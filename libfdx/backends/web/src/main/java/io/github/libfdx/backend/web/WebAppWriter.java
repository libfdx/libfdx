package io.github.libfdx.backend.web;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Writes web app output.
 *
 * @author xpenatan
 */
public final class WebAppWriter {
    private static final String GENERATED_LOADER_PATH = "fdx-loader.js";
    private static final String WEB_RUNTIME_MARKER_PATH = "META-INF/libfdx-web.properties";
    private static final String TEAVM_INTERNAL_RESOURCE_PREFIX = "org/teavm/";
    private static final String SHARED_ASSET_PREFIX = "libfdx-assets/";

    private WebAppWriter() {
    }

    /**
     * Runs the write step.
     *
     * @param app the app
     * @return the write
     * @throws IOException if the operation cannot be completed
     */
    public static List<WebAsset> write(WebApp app) throws IOException {
        Objects.requireNonNull(app, "app");
        Path root = app.getWebappDirectory();
        Path webInf = root.resolve("WEB-INF");
        Files.createDirectories(root);
        Files.createDirectories(webInf);
        Files.deleteIfExists(root.resolve("libfdx-assets.js"));
        List<WebAsset> assets = new ArrayList<>(WebAssets.copy(app.getAssets(), root.resolve("assets")));
        copySharedAssets(root.resolve("assets"), app.getRuntimeClasspath(), assets);
        copyRuntimeScripts(root, app.getRuntimeClasspath());
        writeFdxLoader(root, app, assets.size());
        Files.writeString(root.resolve("index.html"), indexHtml(app, assets.size()), StandardCharsets.UTF_8);
        Files.writeString(webInf.resolve("web.xml"), "<web-app></web-app>\n", StandardCharsets.UTF_8);
        return assets;
    }

    private static void copySharedAssets(Path assetsRoot, List<Path> runtimeClasspath,
            List<WebAsset> assets) throws IOException {
        Path outputRoot = assetsRoot.toAbsolutePath().normalize();
        LinkedHashMap<String, SharedAsset> discovered = new LinkedHashMap<>();
        for (Path entry : runtimeClasspath) {
            Path normalized = entry.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                discoverSharedAssetsFromDirectory(normalized, discovered);
            } else if (Files.isRegularFile(normalized) && isJar(normalized)) {
                discoverSharedAssetsFromJar(normalized, discovered);
            }
        }

        Set<String> applicationAssets = new HashSet<>();
        for (WebAsset asset : assets) {
            applicationAssets.add(asset.getPath().toLowerCase(Locale.ROOT));
        }
        for (SharedAsset asset : discovered.values().stream()
                .sorted(Comparator.comparing(SharedAsset::resourcePath))
                .toList()) {
            if (applicationAssets.contains(asset.resourcePath().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Path output = sharedAssetOutput(outputRoot, asset.resourcePath(), asset.origin());
            Files.createDirectories(output.getParent());
            try (InputStream input = asset.open()) {
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            }
            assets.add(new WebAsset(asset.resourcePath(), Files.size(output), output));
        }
    }

    private static void discoverSharedAssetsFromDirectory(Path classpathRoot,
            Map<String, SharedAsset> discovered) throws IOException {
        Path sharedRoot = classpathRoot.resolve(SHARED_ASSET_PREFIX).normalize();
        if (!Files.isDirectory(sharedRoot)) {
            return;
        }
        try (var paths = Files.walk(sharedRoot)) {
            for (Path source : paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> sharedRoot.relativize(path).toString()))
                    .toList()) {
                String relative = sharedRoot.relativize(source).toString().replace('\\', '/');
                String resourcePath = normalizeSharedAssetPath(SHARED_ASSET_PREFIX + relative,
                        source.toString());
                registerSharedAsset(SharedAsset.file(resourcePath, source), discovered);
            }
        }
    }

    private static void discoverSharedAssetsFromJar(Path jarPath,
            Map<String, SharedAsset> discovered) throws IOException {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            for (ZipEntry entry : zip.stream()
                    .filter(candidate -> !candidate.isDirectory()
                            && candidate.getName().startsWith(SHARED_ASSET_PREFIX))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList()) {
                String origin = jarPath + "!/" + entry.getName();
                String resourcePath = normalizeSharedAssetPath(entry.getName(), origin);
                registerSharedAsset(SharedAsset.jar(resourcePath, jarPath, entry.getName(), entry.getSize()),
                        discovered);
            }
        }
    }

    private static void registerSharedAsset(SharedAsset candidate,
            Map<String, SharedAsset> discovered) throws IOException {
        String portableKey = candidate.resourcePath().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, SharedAsset> entry : discovered.entrySet()) {
            String existingKey = entry.getKey();
            if (portableKey.startsWith(existingKey + "/") || existingKey.startsWith(portableKey + "/")) {
                throw new IOException("Shared asset path conflicts with a file/directory path: '"
                        + entry.getValue().resourcePath() + "' from " + entry.getValue().origin() + " and '"
                        + candidate.resourcePath() + "' from " + candidate.origin());
            }
        }
        SharedAsset existing = discovered.get(portableKey);
        if (existing != null) {
            if (!existing.resourcePath().equals(candidate.resourcePath())) {
                throw new IOException("Shared asset paths differ only by case: '" + existing.resourcePath()
                        + "' from " + existing.origin() + " and '" + candidate.resourcePath() + "' from "
                        + candidate.origin());
            }
            if (!sameContent(existing, candidate)) {
                throw new IOException("Conflicting shared asset '" + candidate.resourcePath() + "' from "
                        + existing.origin() + " and " + candidate.origin());
            }
            return;
        }
        discovered.put(portableKey, candidate);
    }

    private static boolean sameContent(SharedAsset first, SharedAsset second) throws IOException {
        if (first.size() >= 0 && second.size() >= 0 && first.size() != second.size()) {
            return false;
        }
        try (InputStream firstInput = first.open(); InputStream secondInput = second.open()) {
            byte[] firstBuffer = new byte[8192];
            byte[] secondBuffer = new byte[8192];
            while (true) {
                int firstCount = firstInput.readNBytes(firstBuffer, 0, firstBuffer.length);
                int secondCount = secondInput.readNBytes(secondBuffer, 0, secondBuffer.length);
                if (firstCount != secondCount) {
                    return false;
                }
                if (firstCount == 0) {
                    return true;
                }
                if (Arrays.mismatch(firstBuffer, 0, firstCount, secondBuffer, 0, secondCount) >= 0) {
                    return false;
                }
            }
        }
    }

    /**
     * Runs the copy Wasm runtime step.
     *
     * @param outputFile the output file
     * @param modular the modular
     * @param obfuscated the obfuscated
     * @throws IOException if the operation cannot be completed
     */
    public static void copyWasmRuntime(Path outputFile, boolean modular, boolean obfuscated) throws IOException {
        StringBuilder resource = new StringBuilder("org/teavm/backend/wasm/wasm-gc");
        if (modular) {
            resource.append("-module");
        }
        resource.append("-runtime");
        if (obfuscated) {
            resource.append(".min");
        }
        resource.append(".js");
        try (InputStream input = WebAppWriter.class.getClassLoader().getResourceAsStream(resource.toString())) {
            if (input == null) {
                throw new IOException("TeaVM Wasm runtime resource was not found: " + resource);
            }
            Files.createDirectories(outputFile.toAbsolutePath().normalize().getParent());
            Files.copy(input, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String indexHtml(WebApp app, int assetCount) {
        String escapedTitle = html(app.getTitle());
        String escapedCanvas = js(app.getCanvasId());
        boolean fillWindow = app.getWidth() <= 0 || app.getHeight() <= 0;
        int canvasWidth = fillWindow ? 1 : app.getWidth();
        int canvasHeight = fillWindow ? 1 : app.getHeight();
        String canvasSizeCss = fillWindow
                ? "width: 100vw; height: 100vh;"
                : "width: " + app.getWidth() + "px; height: " + app.getHeight() + "px;";
        String fillWindowAttribute = fillWindow ? " data-libfdx-fill-window=\"true\"" : "";
        return """
                <!doctype html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s</title>
                    <style>
                        html, body { margin: 0; width: 100%%; height: 100%%; overflow: hidden; background: #ffffff; }
                        canvas { display: block; %s }
                        #libfdx-error {
                            position: fixed;
                            inset: 0;
                            z-index: 2147483647;
                            box-sizing: border-box;
                            overflow: auto;
                            padding: 16px;
                            background: rgba(20, 20, 20, 0.94);
                            color: #ff6b6b;
                            font: 13px/1.45 Consolas, Monaco, monospace;
                            white-space: pre-wrap;
                        }
                    </style>
                </head>
                <body>
                    <canvas id="%s" width="%d" height="%d"%s></canvas>
                    <script type="text/javascript" src="scripts/fdx-loader.js"></script>
                </body>
                </html>
                """.formatted(escapedTitle, canvasSizeCss, escapedCanvas, canvasWidth, canvasHeight, fillWindowAttribute)
                .trim() + "\n";
    }

    private static void writeFdxLoader(Path root, WebApp app, int assetCount) throws IOException {
        Path scriptsRoot = root.resolve("scripts").toAbsolutePath().normalize();
        Files.createDirectories(scriptsRoot);
        long runtimeCoreScriptSize = publishedFileSize(scriptsRoot.resolve("fdx.js"));
        long runtimeCoreWasmSize = publishedFileSize(scriptsRoot.resolve("fdx.wasm"));
        Files.writeString(scriptsRoot.resolve("fdx-loader.js"),
                fdxLoaderJs(app, assetCount, runtimeCoreScriptSize, runtimeCoreWasmSize), StandardCharsets.UTF_8);
    }

    private static long publishedFileSize(Path path) throws IOException {
        return Files.isRegularFile(path) ? Files.size(path) : 0L;
    }

    private static String fdxLoaderJs(WebApp app, int assetCount, long runtimeCoreScriptSize,
            long runtimeCoreWasmSize) {
        String source = """
                (function(root) {
                    "use strict";

                    var config = {
                        wasm: __WASM__,
                        targetFileName: "__TARGET_FILE_NAME__",
                        entryPointName: "__ENTRY_POINT_NAME__",
                        mainClassArgs: [__MAIN_CLASS_ARGS__],
                        assetCount: __ASSET_COUNT__,
                        preloadLogoPath: "__PRELOAD_LOGO_PATH__",
                        runtimeCoreScriptSize: __RUNTIME_CORE_SCRIPT_SIZE__,
                        runtimeCoreWasmSize: __RUNTIME_CORE_WASM_SIZE__
                    };
                    var modulePromise = null;
                    var runtimeWasmPromise = null;
                    var loadedScripts = {};
                    var scriptUrl = (document.currentScript && document.currentScript.src) || "scripts/fdx-loader.js";
                    var pageUrl = document.baseURI || window.location.href;
                    var runtimePreload = {
                        script: { size: config.runtimeCoreScriptSize, loadedBytes: 0, complete: false },
                        wasm: { size: config.runtimeCoreWasmSize, loadedBytes: 0, complete: false }
                    };
                    root.libfdxRuntimePreload = runtimePreload;

                    function loaderBaseUrl(path) {
                        return new URL(path, scriptUrl).href;
                    }

                    function pageBaseUrl(path) {
                        return new URL(path, pageUrl).href;
                    }

                    function normalizeAssetPath(path) {
                        path = (path || "").replace(/\\\\/g, "/");
                        while (path.indexOf("./") === 0) path = path.substring(2);
                        while (path.indexOf("/") === 0) path = path.substring(1);
                        if (path.indexOf("assets/") === 0) path = path.substring(7);
                        return path;
                    }

                    function loadImage(buffer) {
                        var blob = new Blob([buffer]);
                        if (root.createImageBitmap) {
                            return root.createImageBitmap(blob);
                        }
                        return new Promise(function(resolve, reject) {
                            var image = new Image();
                            var url = URL.createObjectURL(blob);
                            image.onload = function() {
                                URL.revokeObjectURL(url);
                                resolve(image);
                            };
                            image.onerror = function(error) {
                                URL.revokeObjectURL(url);
                                reject(error);
                            };
                            image.src = url;
                        });
                    }

                    function decodeBootstrapImage(path, buffer) {
                        return loadImage(buffer).then(function(image) {
                            var canvas = document.createElement("canvas");
                            canvas.width = image.width || image.naturalWidth;
                            canvas.height = image.height || image.naturalHeight;
                            var context = canvas.getContext("2d");
                            context.drawImage(image, 0, 0);
                            if (typeof image.close === "function") image.close();
                            var rgba = context.getImageData(0, 0, canvas.width, canvas.height).data;
                            var copy = new Uint8Array(rgba.length);
                            copy.set(rgba);
                            root.libfdxImageData = root.libfdxImageData || Object.create(null);
                            root.libfdxImageData[path] = { width: canvas.width, height: canvas.height, rgba: copy };
                            root.libfdxImageData["assets/" + path] = root.libfdxImageData[path];
                        });
                    }

                    function preloadBootstrapLogo() {
                        var path = normalizeAssetPath(config.preloadLogoPath);
                        if (!path) {
                            return Promise.resolve();
                        }
                        root.libfdxAssets = root.libfdxAssets || Object.create(null);
                        var existing = root.libfdxAssets[path] || root.libfdxAssets["assets/" + path];
                        if (existing && root.libfdxImageData && root.libfdxImageData[path]) {
                            return Promise.resolve();
                        }
                        return fetch(pageBaseUrl("assets/" + path)).then(function(response) {
                            if (!response.ok) {
                                throw new Error("Could not preload bootstrap image " + path + ": " + response.status);
                            }
                            return response.arrayBuffer();
                        }).then(function(buffer) {
                            root.libfdxAssets[path] = buffer;
                            root.libfdxAssets["assets/" + path] = buffer;
                            return decodeBootstrapImage(path, buffer);
                        });
                    }

                    function updateRuntimeBytes(entry, loadedBytes) {
                        var next = Math.max(entry.loadedBytes, Math.min(entry.size, loadedBytes));
                        entry.loadedBytes = next;
                    }

                    function completeRuntimeFile(entry) {
                        updateRuntimeBytes(entry, entry.size);
                        if (entry.complete) {
                            return;
                        }
                        entry.complete = true;
                    }

                    function fetchRuntimeWasm() {
                        var entry = runtimePreload.wasm;
                        var url = loaderBaseUrl("fdx.wasm");
                        return fetch(url).then(function(response) {
                            if (!response.ok) {
                                throw new Error("Could not preload runtime module " + url + ": " + response.status);
                            }
                            if (!response.body || typeof response.body.getReader !== "function") {
                                return response.arrayBuffer().then(function(buffer) {
                                    updateRuntimeBytes(entry, buffer.byteLength);
                                    completeRuntimeFile(entry);
                                    return new Uint8Array(buffer);
                                });
                            }
                            var reader = response.body.getReader();
                            var chunks = [];
                            var received = 0;
                            function read() {
                                return reader.read().then(function(result) {
                                    if (result.done) {
                                        var bytes = new Uint8Array(received);
                                        var offset = 0;
                                        for (var i = 0; i < chunks.length; i++) {
                                            bytes.set(chunks[i], offset);
                                            offset += chunks[i].byteLength;
                                        }
                                        completeRuntimeFile(entry);
                                        return bytes;
                                    }
                                    chunks.push(result.value);
                                    received += result.value.byteLength;
                                    updateRuntimeBytes(entry, received);
                                    return read();
                                });
                            }
                            return read();
                        });
                    }

                    function loadRuntimeWasm() {
                        if (!runtimeWasmPromise) {
                            runtimeWasmPromise = runtimePreload.wasm.size > 0
                                    ? fetchRuntimeWasm()
                                    : Promise.resolve(null);
                        }
                        return runtimeWasmPromise;
                    }

                    function loadScript(url) {
                        if (loadedScripts[url]) {
                            return loadedScripts[url];
                        }
                        loadedScripts[url] = new Promise(function(resolve, reject) {
                            var script = document.createElement("script");
                            script.type = "text/javascript";
                            script.src = url;
                            script.onload = resolve;
                            script.onerror = function() {
                                reject(new Error("Could not load script: " + url));
                            };
                            document.head.appendChild(script);
                        });
                        return loadedScripts[url];
                    }

                    function javaArrayLength(source) {
                        if (!source) return 0;
                        if (typeof source.length === "number") return source.length;
                        if (source.data && typeof source.data.length === "number") return source.data.length;
                        if (source.$data && typeof source.$data.length === "number") return source.$data.length;
                        return 0;
                    }

                    function copyJavaArray(source, ctor) {
                        var length = javaArrayLength(source);
                        var target = new ctor(length);
                        if (!source || length === 0) return target;
                        var data = source.data || source.$data || source;
                        if (ArrayBuffer.isView(data)) {
                            target.set(new ctor(data.buffer, data.byteOffset, Math.min(length, data.length)));
                            return target;
                        }
                        for (var i = 0; i < length; i++) {
                            target[i] = data[i];
                        }
                        return target;
                    }

                    function base64(bytes) {
                        var binary = "";
                        for (var i = 0; i < bytes.length; i++) {
                            binary += String.fromCharCode(bytes[i]);
                        }
                        return btoa(binary);
                    }

                    function writeInt(bytes, value) {
                        bytes.push(value & 255);
                        bytes.push((value >> 8) & 255);
                        bytes.push((value >> 16) & 255);
                        bytes.push((value >> 24) & 255);
                    }

                    function utf8Bytes(text) {
                        return new TextEncoder().encode(text || "");
                    }

                    function wire(status, kind, output, diagnostics, reflection, targetInterface) {
                        var out = output || new Uint8Array(0);
                        var diag = utf8Bytes(diagnostics || "");
                        var reflected = reflection || new Uint8Array(0);
                        var translated = targetInterface || new Uint8Array(0);
                        var bytes = [70, 68, 88, 82];
                        writeInt(bytes, 2);
                        writeInt(bytes, status);
                        writeInt(bytes, kind);
                        writeInt(bytes, out.length);
                        writeInt(bytes, diag.length);
                        writeInt(bytes, reflected.length);
                        writeInt(bytes, translated.length);
                        for (var i = 0; i < out.length; i++) {
                            bytes.push(out[i]);
                        }
                        for (var j = 0; j < diag.length; j++) {
                            bytes.push(diag[j]);
                        }
                        for (var k = 0; k < reflected.length; k++) {
                            bytes.push(reflected[k]);
                        }
                        for (var m = 0; m < translated.length; m++) {
                            bytes.push(translated[m]);
                        }
                        return base64(bytes);
                    }

                    function showError(prefix, error) {
                        var details = prefix + "\\n";
                        if (error) {
                            var message = error.message || String(error);
                            var stack = error.stack || "";
                            details += message;
                            if (stack && stack.indexOf(message) < 0) {
                                details += "\\n" + stack;
                            }
                            else if (stack) {
                                details += "\\n" + stack;
                            }
                        }
                        else {
                            details += "Unknown error";
                        }
                        console.error(details);
                        var existing = document.getElementById("libfdx-error");
                        var output = existing || document.createElement("pre");
                        output.id = "libfdx-error";
                        output.textContent = details;
                        if (!existing) document.body.appendChild(output);
                    }

                    function installShaderCompiler(module) {
                        if (root.libfdxShaderCompileBase64) {
                            return;
                        }
                        if (typeof module.cwrap !== "function"
                                || typeof module.lengthBytesUTF8 !== "function"
                                || typeof module.stringToUTF8 !== "function"
                                || typeof module.UTF8ToString !== "function") {
                            return;
                        }

                        var compile;
                        var status;
                        var kind;
                        var output;
                        var size;
                        var diagnostics;
                        var reflection;
                        var reflectionSize;
                        var targetInterface;
                        var targetInterfaceSize;
                        var freeResult;
                        try {
                            compile = module.cwrap("fdx_shaderc_compile_wgsl_handle", "number",
                                    ["number", "number", "number", "number", "number", "number", "number"]);
                            status = module.cwrap("fdx_shaderc_result_status", "number", ["number"]);
                            kind = module.cwrap("fdx_shaderc_result_output_kind", "number", ["number"]);
                            output = module.cwrap("fdx_shaderc_result_output", "number", ["number"]);
                            size = module.cwrap("fdx_shaderc_result_output_size", "number", ["number"]);
                            diagnostics = module.cwrap("fdx_shaderc_result_diagnostics", "number", ["number"]);
                            reflection = module.cwrap("fdx_shaderc_result_reflection", "number", ["number"]);
                            reflectionSize = module.cwrap("fdx_shaderc_result_reflection_size", "number", ["number"]);
                            targetInterface = module.cwrap(
                                    "fdx_shaderc_result_target_interface", "number", ["number"]);
                            targetInterfaceSize = module.cwrap(
                                    "fdx_shaderc_result_target_interface_size", "number", ["number"]);
                            freeResult = module.cwrap("fdx_shaderc_result_free", null, ["number"]);
                        } catch (ignored) {
                            return;
                        }

                        root.libfdxShaderCompileBase64 = function(source, target, stage, entryPoint, glslProfile, glslEsProfile) {
                            var sourceSize = module.lengthBytesUTF8(source);
                            var entrySize = module.lengthBytesUTF8(entryPoint);
                            var glslSize = module.lengthBytesUTF8(glslProfile);
                            var glslEsSize = module.lengthBytesUTF8(glslEsProfile);
                            var sourcePtr = module._malloc(sourceSize + 1);
                            var entryPtr = module._malloc(entrySize + 1);
                            var glslPtr = module._malloc(glslSize + 1);
                            var glslEsPtr = module._malloc(glslEsSize + 1);
                            var handle = 0;
                            try {
                                module.stringToUTF8(source, sourcePtr, sourceSize + 1);
                                module.stringToUTF8(entryPoint, entryPtr, entrySize + 1);
                                module.stringToUTF8(glslProfile, glslPtr, glslSize + 1);
                                module.stringToUTF8(glslEsProfile, glslEsPtr, glslEsSize + 1);
                                handle = compile(sourcePtr, sourceSize, target, stage, entryPtr, glslPtr, glslEsPtr);
                                if (!handle) {
                                    throw new Error("Native shader compiler returned no result handle");
                                }
                                var resultStatus = status(handle);
                                var resultKind = kind(handle);
                                var resultSize = size(handle);
                                if (resultSize < 0) {
                                    throw new Error("Native shader compiler returned a negative output size");
                                }
                                var resultOutput = new Uint8Array(0);
                                if (resultSize > 0) {
                                    var outputPtr = output(handle);
                                    if (!outputPtr) {
                                        throw new Error("Native shader compiler returned a null output pointer");
                                    }
                                    resultOutput = module.HEAPU8.slice(outputPtr, outputPtr + resultSize);
                                }
                                var diagnosticPtr = diagnostics(handle);
                                var diagnosticText = diagnosticPtr ? module.UTF8ToString(diagnosticPtr) : "";
                                var reflectedSize = reflectionSize(handle);
                                if (reflectedSize < 0) {
                                    throw new Error("Native shader compiler returned a negative reflection size");
                                }
                                var resultReflection = new Uint8Array(0);
                                if (reflectedSize > 0) {
                                    var reflectionPtr = reflection(handle);
                                    if (!reflectionPtr) {
                                        throw new Error("Native shader compiler returned a null reflection pointer");
                                    }
                                    resultReflection = module.HEAPU8.slice(
                                            reflectionPtr, reflectionPtr + reflectedSize);
                                }
                                var translatedSize = targetInterfaceSize(handle);
                                if (translatedSize < 0) {
                                    throw new Error("Native shader compiler returned a negative target-interface size");
                                }
                                var resultTargetInterface = new Uint8Array(0);
                                if (translatedSize > 0) {
                                    var targetInterfacePtr = targetInterface(handle);
                                    if (!targetInterfacePtr) {
                                        throw new Error(
                                                "Native shader compiler returned a null target-interface pointer");
                                    }
                                    resultTargetInterface = module.HEAPU8.slice(
                                            targetInterfacePtr, targetInterfacePtr + translatedSize);
                                }
                                return wire(resultStatus, resultKind, resultOutput, diagnosticText,
                                        resultReflection, resultTargetInterface);
                            } finally {
                                if (handle) {
                                    freeResult(handle);
                                }
                                module._free(sourcePtr);
                                module._free(entryPtr);
                                module._free(glslPtr);
                                module._free(glslEsPtr);
                            }
                        };
                    }

                    function ensureModule() {
                        if (root.libfdxCoreModule) {
                            return Promise.resolve(root.libfdxCoreModule);
                        }
                        if (modulePromise) {
                            return modulePromise;
                        }
                        if (typeof root.FdxModule !== "function") {
                            return Promise.reject(new Error("libfdx core Emscripten module script was not loaded"));
                        }
                        modulePromise = loadRuntimeWasm().then(function(bytes) {
                            if (!bytes) {
                                return root.FdxModule({
                                    locateFile: function(path) {
                                        return path === "fdx.wasm" ? loaderBaseUrl(path) : path;
                                    }
                                });
                            }
                            return new Promise(function(resolve, reject) {
                                var factory;
                                try {
                                    factory = root.FdxModule({
                                        instantiateWasm: function(imports, success) {
                                            WebAssembly.instantiate(bytes, imports).then(function(result) {
                                                try {
                                                    success(result.instance);
                                                } catch (error) {
                                                    reject(error);
                                                }
                                            }, reject);
                                            return {};
                                        }
                                    });
                                } catch (error) {
                                    reject(error);
                                    return;
                                }
                                Promise.resolve(factory).then(resolve, reject);
                            });
                        }).then(function(module) {
                            installShaderCompiler(module);
                            root.libfdxCoreModule = module;
                            return module;
                        });
                        return modulePromise;
                    }

                    function loadRuntimeCore() {
                        var scriptPromise = loadScript(loaderBaseUrl("fdx.js")).then(function() {
                            completeRuntimeFile(runtimePreload.script);
                        });
                        return Promise.all([scriptPromise, loadRuntimeWasm()]).then(ensureModule);
                    }

                    function rasterize(fontBytes, codePoints, pixelSize, padding, atlasWidth) {
                        var module = root.libfdxCoreModule;
                        if (!module) {
                            throw new Error("libfdx FreeType Emscripten module is not ready");
                        }

                        var font = copyJavaArray(fontBytes, Int8Array);
                        var points = copyJavaArray(codePoints, Int32Array);
                        var fontPtr = 0;
                        var codePointPtr = 0;
                        var metricIntsPtr = 0;
                        var metricFloatsPtr = 0;
                        var rgbaPtr = 0;
                        var glyphIntsPtr = 0;
                        var glyphFloatsPtr = 0;
                        var kerningIntsPtr = 0;

                        try {
                            fontPtr = module._malloc(Math.max(1, font.byteLength));
                            codePointPtr = module._malloc(Math.max(1, points.byteLength));
                            metricIntsPtr = module._malloc(16);
                            metricFloatsPtr = module._malloc(12);
                            module.HEAP8.set(font, fontPtr);
                            module.HEAP32.set(points, codePointPtr >> 2);

                            var measured = module._fdx_freetype_rasterize(fontPtr, font.byteLength, codePointPtr, points.length,
                                    pixelSize, padding, atlasWidth, metricIntsPtr, metricFloatsPtr, 0, 0, 0, 0, 0, 0, 0, 0);
                            if (!measured) {
                                throw new Error("FreeType failed to measure native web font");
                            }

                            var metricInts = new Int32Array(module.HEAP32.buffer, metricIntsPtr, 4);
                            var metricFloats = new Float32Array(module.HEAPF32.buffer, metricFloatsPtr, 3);
                            var width = metricInts[0];
                            var height = metricInts[1];
                            var glyphCount = metricInts[2];
                            var kerningCount = metricInts[3];
                            if (width <= 0 || height <= 0 || glyphCount < 0 || kerningCount < 0) {
                                throw new Error("FreeType returned invalid native web font metrics");
                            }

                            var rgbaSize = width * height * 4;
                            rgbaPtr = module._malloc(Math.max(1, rgbaSize));
                            glyphIntsPtr = module._malloc(Math.max(1, glyphCount * 5 * 4));
                            glyphFloatsPtr = module._malloc(Math.max(1, glyphCount * 3 * 4));
                            kerningIntsPtr = module._malloc(Math.max(1, kerningCount * 3 * 4));

                            var rasterized = module._fdx_freetype_rasterize(fontPtr, font.byteLength, codePointPtr, points.length,
                                    pixelSize, padding, atlasWidth, metricIntsPtr, metricFloatsPtr, rgbaPtr, rgbaSize,
                                    glyphIntsPtr, glyphCount * 5, glyphFloatsPtr, glyphCount * 3, kerningIntsPtr, kerningCount * 3);
                            if (!rasterized) {
                                throw new Error("FreeType failed to rasterize native web font");
                            }

                            return {
                                nativeSize: metricFloats[0],
                                lineHeight: metricFloats[1],
                                baseLine: metricFloats[2],
                                atlasWidth: width,
                                atlasHeight: height,
                                glyphCount: glyphCount,
                                kerningCount: kerningCount,
                                rgba: new Int8Array(module.HEAPU8.slice(rgbaPtr, rgbaPtr + rgbaSize).buffer),
                                glyphInts: new Int32Array(module.HEAP32.slice(glyphIntsPtr >> 2, (glyphIntsPtr >> 2) + glyphCount * 5).buffer),
                                glyphFloats: new Float32Array(module.HEAPF32.slice(glyphFloatsPtr >> 2, (glyphFloatsPtr >> 2) + glyphCount * 3).buffer),
                                kerningInts: new Int32Array(module.HEAP32.slice(kerningIntsPtr >> 2, (kerningIntsPtr >> 2) + kerningCount * 3).buffer)
                            };
                        } finally {
                            if (kerningIntsPtr) module._free(kerningIntsPtr);
                            if (glyphFloatsPtr) module._free(glyphFloatsPtr);
                            if (glyphIntsPtr) module._free(glyphIntsPtr);
                            if (rgbaPtr) module._free(rgbaPtr);
                            if (metricFloatsPtr) module._free(metricFloatsPtr);
                            if (metricIntsPtr) module._free(metricIntsPtr);
                            if (codePointPtr) module._free(codePointPtr);
                            if (fontPtr) module._free(fontPtr);
                        }
                    }

                    function prepareTeaVmApp() {
                        if (config.wasm) {
                            return loadScript(pageBaseUrl(config.targetFileName + "-runtime.js")).then(function() {
                                return TeaVM.wasmGC.load(pageBaseUrl(config.targetFileName)).then(function(teavm) {
                                    return function() {
                                        var entry = teavm.exports[config.entryPointName];
                                        if (typeof entry !== "function") {
                                            throw new Error("TeaVM Wasm entry point was not found: "
                                                    + config.entryPointName);
                                        }
                                        return entry(config.mainClassArgs);
                                    };
                                });
                            });
                        }
                        return loadScript(pageBaseUrl(config.targetFileName)).then(function() {
                            return function() {
                                var entry = root[config.entryPointName];
                                if (typeof entry !== "function") {
                                    throw new Error("TeaVM JavaScript entry point was not found: "
                                            + config.entryPointName);
                                }
                                return entry(config.mainClassArgs);
                            };
                        });
                    }

                    function start() {
                        var assetBase = pageBaseUrl("assets/");
                        console.log("%clibfdx assets: " + assetBase + " (" + config.assetCount + " files)", "color:#d50000;font-weight:bold");
                        return Promise.all([preloadBootstrapLogo(), loadRuntimeCore(), prepareTeaVmApp()])
                                .then(function(prepared) {
                                    return prepared[2]();
                                });
                    }

                    root.libfdxPreloadRuntimeCore = loadRuntimeCore;
                    root.libfdxFreeTypeRasterize = rasterize;

                    root.addEventListener("error", function(event) {
                        showError("libfdx runtime error", event.error || event.message);
                    });
                    root.addEventListener("unhandledrejection", function(event) {
                        showError("libfdx promise rejection", event.reason);
                    });
                    root.addEventListener("load", function() {
                        start().catch(function(error) {
                            showError("libfdx startup failed", error);
                            throw error;
                        });
                    });
                })(typeof window !== "undefined" ? window : globalThis);
                """;
        return source
                .replace("__WASM__", Boolean.toString(app.isWasm()))
                .replace("__TARGET_FILE_NAME__", js(app.getTargetFileName()))
                .replace("__ENTRY_POINT_NAME__", js(app.getEntryPointName()))
                .replace("__MAIN_CLASS_ARGS__", app.getMainClassArgs())
                .replace("__ASSET_COUNT__", Integer.toString(assetCount))
                .replace("__PRELOAD_LOGO_PATH__", js(WebAssets.DEFAULT_PRELOAD_LOGO_PATH))
                .replace("__RUNTIME_CORE_SCRIPT_SIZE__", Long.toString(runtimeCoreScriptSize))
                .replace("__RUNTIME_CORE_WASM_SIZE__", Long.toString(runtimeCoreWasmSize))
                .trim() + "\n";
    }

    private static void copyRuntimeScripts(Path root, List<Path> runtimeClasspath) throws IOException {
        Path webappRoot = root.toAbsolutePath().normalize();
        Path scriptsRoot = root.resolve("scripts").toAbsolutePath().normalize();
        LinkedHashMap<String, RuntimeScript> discovered = new LinkedHashMap<>();
        LinkedHashMap<String, String> portablePaths = new LinkedHashMap<>();
        for (Path entry : runtimeClasspath) {
            Path normalized = entry.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                discoverRuntimeScriptsFromDirectory(normalized, webappRoot, discovered, portablePaths);
            } else if (Files.isRegularFile(normalized) && isJar(normalized)) {
                discoverRuntimeScriptsFromJar(normalized, discovered, portablePaths);
            }
        }

        ArrayList<RuntimeScriptCopy> copies = new ArrayList<>(discovered.size());
        for (RuntimeScript script : discovered.values().stream()
                .sorted(Comparator.comparing(RuntimeScript::resourcePath))
                .toList()) {
            Path output = runtimeScriptOutput(scriptsRoot, script.resourcePath(), script.origin());
            copies.add(new RuntimeScriptCopy(script, output));
        }

        WebAssets.deleteDirectory(scriptsRoot);
        for (RuntimeScriptCopy copy : copies) {
            Files.createDirectories(copy.output().getParent());
            try (InputStream input = copy.script().open()) {
                Files.copy(input, copy.output(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void discoverRuntimeScriptsFromDirectory(Path directory, Path webappRoot,
            Map<String, RuntimeScript> discovered, Map<String, String> portablePaths)
            throws IOException {
        if (directory.startsWith(webappRoot)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path source : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toAbsolutePath().normalize().startsWith(webappRoot))
                    .filter(path -> isRuntimeScript(directory.relativize(path).toString()))
                    .sorted(Comparator.comparing(path -> directory.relativize(path).toString()))
                    .toList()) {
                String relativePath = directory.relativize(source).toString();
                String resourcePath = normalizeRuntimeScriptPath(relativePath, source.toString());
                registerRuntimeScript(RuntimeScript.file(resourcePath, source), discovered, portablePaths);
            }
        }
    }

    private static void discoverRuntimeScriptsFromJar(Path jarPath, Map<String, RuntimeScript> discovered,
            Map<String, String> portablePaths)
            throws IOException {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            var runtimeEntries = zip.stream()
                    .filter(candidate -> !candidate.isDirectory() && isRuntimeScript(candidate.getName()))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
            // WASM-bearing JARs identify themselves automatically. A JavaScript-only runtime JAR opts in once at
            // artifact build time; application users never maintain a list of individual runtime filenames.
            boolean webRuntimeJar = zip.getEntry(WEB_RUNTIME_MARKER_PATH) != null
                    || containsPublishableWebAssembly(jarPath, runtimeEntries);
            if (!webRuntimeJar) {
                return;
            }

            Set<String> archivePaths = new HashSet<>();
            for (ZipEntry entry : runtimeEntries) {
                String origin = jarPath + "!/" + entry.getName();
                String resourcePath = normalizeRuntimeScriptPath(entry.getName(), origin);
                if (!archivePaths.add(resourcePath)) {
                    throw new IOException("Duplicate runtime script path '" + resourcePath + "' in " + jarPath);
                }
                registerRuntimeScript(RuntimeScript.jar(resourcePath, jarPath, entry.getName(), entry.getSize()),
                        discovered, portablePaths);
            }
        }
    }

    private static boolean containsPublishableWebAssembly(Path jarPath, List<? extends ZipEntry> entries)
            throws IOException {
        for (ZipEntry entry : entries) {
            if (!isWebAssembly(entry.getName())) {
                continue;
            }
            String origin = jarPath + "!/" + entry.getName();
            String resourcePath = normalizeRuntimeScriptPath(entry.getName(), origin);
            if (!isExcludedRuntimeScript(resourcePath)
                    && !resourcePath.equalsIgnoreCase(GENERATED_LOADER_PATH)) {
                return true;
            }
        }
        return false;
    }

    private static void registerRuntimeScript(RuntimeScript candidate, Map<String, RuntimeScript> discovered,
            Map<String, String> portablePaths) throws IOException {
        String resourcePath = candidate.resourcePath();
        if (isExcludedRuntimeScript(resourcePath)) {
            return;
        }

        String portableKey = resourcePath.toLowerCase(Locale.ROOT);
        String loaderKey = GENERATED_LOADER_PATH.toLowerCase(Locale.ROOT);
        if (portableKey.equals(loaderKey)) {
            return;
        }
        if (portableKey.startsWith(loaderKey + "/")) {
            throw new IOException("Runtime script path conflicts with generated loader '" + GENERATED_LOADER_PATH
                    + "': '" + resourcePath + "' from " + candidate.origin());
        }
        String existingPortablePath = portablePaths.get(portableKey);
        if (existingPortablePath != null && !existingPortablePath.equals(resourcePath)) {
            RuntimeScript existing = discovered.get(existingPortablePath);
            throw new IOException("Runtime script paths differ only by case: '" + existingPortablePath + "' from "
                    + existing.origin() + " and '" + resourcePath + "' from " + candidate.origin());
        }
        for (Map.Entry<String, String> entry : portablePaths.entrySet()) {
            String existingKey = entry.getKey();
            if (portableKey.startsWith(existingKey + "/") || existingKey.startsWith(portableKey + "/")) {
                RuntimeScript existing = discovered.get(entry.getValue());
                throw new IOException("Runtime script path conflicts with a file/directory path: '"
                        + existing.resourcePath() + "' from " + existing.origin() + " and '" + resourcePath
                        + "' from " + candidate.origin());
            }
        }

        RuntimeScript existing = discovered.get(resourcePath);
        if (existing != null) {
            if (!sameContent(existing, candidate)) {
                throw new IOException("Conflicting runtime script '" + resourcePath + "' from " + existing.origin()
                        + " and " + candidate.origin());
            }
            return;
        }

        portablePaths.put(portableKey, resourcePath);
        discovered.put(resourcePath, candidate);
    }

    private static boolean sameContent(RuntimeScript first, RuntimeScript second) throws IOException {
        if (first.size() >= 0 && second.size() >= 0 && first.size() != second.size()) {
            return false;
        }
        try (InputStream firstInput = first.open(); InputStream secondInput = second.open()) {
            byte[] firstBuffer = new byte[8192];
            byte[] secondBuffer = new byte[8192];
            while (true) {
                int firstCount = firstInput.readNBytes(firstBuffer, 0, firstBuffer.length);
                int secondCount = secondInput.readNBytes(secondBuffer, 0, secondBuffer.length);
                if (firstCount != secondCount) {
                    return false;
                }
                if (firstCount == 0) {
                    return true;
                }
                if (Arrays.mismatch(firstBuffer, 0, firstCount, secondBuffer, 0, secondCount) >= 0) {
                    return false;
                }
            }
        }
    }

    private static boolean isJar(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static boolean isRuntimeScript(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.endsWith(".js") || normalized.endsWith(".wasm");
    }

    private static boolean isWebAssembly(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".wasm");
    }

    private static boolean isExcludedRuntimeScript(String resourcePath) {
        int separator = resourcePath.indexOf('/');
        String firstSegment = separator < 0 ? resourcePath : resourcePath.substring(0, separator);
        return firstSegment.equalsIgnoreCase("META-INF")
                || firstSegment.equalsIgnoreCase("WEB-INF")
                // TeaVM packages compiler inputs as JavaScript resources; they are not webapp runtime scripts.
                || resourcePath.regionMatches(true, 0, TEAVM_INTERNAL_RESOURCE_PREFIX, 0,
                        TEAVM_INTERNAL_RESOURCE_PREFIX.length());
    }

    private static String normalizeRuntimeScriptPath(String path, String origin) throws IOException {
        String normalized = path.replace('\\', '/');
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.indexOf('\0') >= 0) {
            throw invalidRuntimeScriptPath(path, origin);
        }
        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (!isPortablePathSegment(segment)) {
                throw invalidRuntimeScriptPath(path, origin);
            }
        }
        return String.join("/", segments);
    }

    private static String normalizeSharedAssetPath(String path, String origin) throws IOException {
        String normalized = path.replace('\\', '/');
        if (!normalized.startsWith(SHARED_ASSET_PREFIX) || normalized.length() <= SHARED_ASSET_PREFIX.length()
                || normalized.startsWith("/") || normalized.indexOf('\0') >= 0) {
            throw invalidSharedAssetPath(path, origin);
        }
        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (!isPortablePathSegment(segment)) {
                throw invalidSharedAssetPath(path, origin);
            }
        }
        return String.join("/", segments);
    }

    private static boolean isPortablePathSegment(String segment) {
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                || segment.endsWith(".") || segment.endsWith(" ")) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char character = segment.charAt(i);
            if (character < 32 || "<>:\"|?*".indexOf(character) >= 0) {
                return false;
            }
        }

        int extension = segment.indexOf('.');
        String baseName = (extension < 0 ? segment : segment.substring(0, extension)).toUpperCase(Locale.ROOT);
        if (baseName.equals("CON") || baseName.equals("PRN") || baseName.equals("AUX") || baseName.equals("NUL")) {
            return false;
        }
        return !(baseName.length() == 4
                && (baseName.startsWith("COM") || baseName.startsWith("LPT"))
                && baseName.charAt(3) >= '1' && baseName.charAt(3) <= '9');
    }

    private static IOException invalidRuntimeScriptPath(String path, String origin) {
        return new IOException("Invalid runtime script path '" + path + "' from " + origin);
    }

    private static IOException invalidSharedAssetPath(String path, String origin) {
        return new IOException("Invalid shared asset path '" + path + "' from " + origin);
    }

    private static Path sharedAssetOutput(Path assetsRoot, String resourcePath, String origin) throws IOException {
        Path output;
        try {
            output = assetsRoot.resolve(resourcePath).toAbsolutePath().normalize();
        } catch (InvalidPathException error) {
            throw new IOException("Invalid shared asset output path from " + origin + ": " + resourcePath, error);
        }
        if (!output.startsWith(assetsRoot)) {
            throw new IOException("Refusing to copy shared asset outside output directory: " + origin);
        }
        return output;
    }

    private static Path runtimeScriptOutput(Path scriptsRoot, String resourcePath, String origin) throws IOException {
        Path output;
        try {
            output = scriptsRoot.resolve(resourcePath).toAbsolutePath().normalize();
        } catch (InvalidPathException error) {
            throw new IOException("Invalid runtime script output path from " + origin + ": " + resourcePath, error);
        }
        if (!output.startsWith(scriptsRoot)) {
            throw new IOException("Refusing to copy runtime script outside output directory: " + origin);
        }
        return output;
    }

    private record RuntimeScriptCopy(RuntimeScript script, Path output) {
    }

    private record SharedAsset(String resourcePath, Path source, String jarEntryName, long size) {
        private static SharedAsset file(String resourcePath, Path source) throws IOException {
            return new SharedAsset(resourcePath, source, null, Files.size(source));
        }

        private static SharedAsset jar(String resourcePath, Path jarPath, String jarEntryName, long size) {
            return new SharedAsset(resourcePath, jarPath, jarEntryName, size);
        }

        private String origin() {
            return jarEntryName == null ? source.toString() : source + "!/" + jarEntryName;
        }

        private InputStream open() throws IOException {
            if (jarEntryName == null) {
                return Files.newInputStream(source);
            }
            ZipFile zip = new ZipFile(source.toFile());
            ZipEntry entry = zip.getEntry(jarEntryName);
            if (entry == null || entry.isDirectory()) {
                zip.close();
                throw new IOException("Shared asset disappeared from JAR: " + origin());
            }
            try {
                InputStream input = zip.getInputStream(entry);
                return new FilterInputStream(input) {
                    @Override
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            zip.close();
                        }
                    }
                };
            } catch (IOException | RuntimeException error) {
                zip.close();
                throw error;
            }
        }
    }

    private record RuntimeScript(String resourcePath, Path source, String jarEntryName, long size) {
        private static RuntimeScript file(String resourcePath, Path source) throws IOException {
            return new RuntimeScript(resourcePath, source, null, Files.size(source));
        }

        private static RuntimeScript jar(String resourcePath, Path jarPath, String jarEntryName, long size) {
            return new RuntimeScript(resourcePath, jarPath, jarEntryName, size);
        }

        private String origin() {
            return jarEntryName == null ? source.toString() : source + "!/" + jarEntryName;
        }

        private InputStream open() throws IOException {
            if (jarEntryName == null) {
                return Files.newInputStream(source);
            }
            ZipFile zip = new ZipFile(source.toFile());
            ZipEntry entry = zip.getEntry(jarEntryName);
            if (entry == null || entry.isDirectory()) {
                zip.close();
                throw new IOException("Runtime script disappeared from JAR: " + origin());
            }
            try {
                InputStream input = zip.getInputStream(entry);
                return new FilterInputStream(input) {
                    @Override
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            zip.close();
                        }
                    }
                };
            } catch (IOException | RuntimeException error) {
                zip.close();
                throw error;
            }
        }
    }

    private static String html(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Runs the JS step.
     *
     * @param value the value
     * @return the JS
     */
    public static String js(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
