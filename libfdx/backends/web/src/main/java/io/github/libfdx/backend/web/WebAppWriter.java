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
        List<WebAsset> assets = WebAssets.copy(app.getAssets(), root.resolve("assets"));
        copyRuntimeScripts(root, app.getRuntimeClasspath());
        writeFdxLoader(root, app, assets.size());
        Files.writeString(root.resolve("index.html"), indexHtml(app, assets.size()), StandardCharsets.UTF_8);
        Files.writeString(webInf.resolve("web.xml"), "<web-app></web-app>\n", StandardCharsets.UTF_8);
        return assets;
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
        Files.writeString(scriptsRoot.resolve("fdx-loader.js"), fdxLoaderJs(app, assetCount), StandardCharsets.UTF_8);
    }

    private static String fdxLoaderJs(WebApp app, int assetCount) {
        String source = """
                (function(root) {
                    "use strict";

                    var config = {
                        wasm: __WASM__,
                        targetFileName: "__TARGET_FILE_NAME__",
                        entryPointName: "__ENTRY_POINT_NAME__",
                        mainClassArgs: [__MAIN_CLASS_ARGS__],
                        assetCount: __ASSET_COUNT__
                    };
                    var modulePromise = null;
                    var loadedScripts = {};
                    var scriptUrl = (document.currentScript && document.currentScript.src) || "scripts/fdx-loader.js";
                    var pageUrl = document.baseURI || window.location.href;

                    function loaderBaseUrl(path) {
                        return new URL(path, scriptUrl).href;
                    }

                    function pageBaseUrl(path) {
                        return new URL(path, pageUrl).href;
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
                        modulePromise = root.FdxModule({
                            locateFile: function(path) {
                                return path === "fdx.wasm" ? loaderBaseUrl(path) : path;
                            }
                        }).then(function(module) {
                            installShaderCompiler(module);
                            root.libfdxCoreModule = module;
                            return module;
                        });
                        return modulePromise;
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

                    function startTeaVmApp() {
                        if (config.wasm) {
                            return loadScript(pageBaseUrl(config.targetFileName + "-runtime.js")).then(function() {
                                return TeaVM.wasmGC.load(pageBaseUrl(config.targetFileName)).then(function(teavm) {
                                    var entry = teavm.exports[config.entryPointName];
                                    if (typeof entry !== "function") {
                                        throw new Error("TeaVM Wasm entry point was not found: " + config.entryPointName);
                                    }
                                    return entry(config.mainClassArgs);
                                });
                            });
                        }
                        return loadScript(pageBaseUrl(config.targetFileName)).then(function() {
                            var entry = root[config.entryPointName];
                            if (typeof entry !== "function") {
                                throw new Error("TeaVM JavaScript entry point was not found: " + config.entryPointName);
                            }
                            return entry.apply(root, config.mainClassArgs);
                        });
                    }

                    function start() {
                        var assetBase = pageBaseUrl("assets/");
                        console.log("%clibfdx assets: " + assetBase + " (" + config.assetCount + " files)", "color:#d50000;font-weight:bold");
                        return loadScript(loaderBaseUrl("fdx.js"))
                                .then(ensureModule)
                                .then(startTeaVmApp);
                    }

                    root.libfdxPreloadRuntimeCore = ensureModule;
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
            if (!isPortableRuntimeScriptSegment(segment)) {
                throw invalidRuntimeScriptPath(path, origin);
            }
        }
        return String.join("/", segments);
    }

    private static boolean isPortableRuntimeScriptSegment(String segment) {
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
