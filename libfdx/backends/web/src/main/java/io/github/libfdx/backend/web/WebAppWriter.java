package io.github.libfdx.backend.web;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
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
    private static final String LEGACY_LOADER_PATH = "fdx-loader.js";
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
        Files.writeString(root.resolve("index.html"), indexHtml(app, assets, shaderCompilerIdentity(root.resolve("scripts"))), StandardCharsets.UTF_8);
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

    private static String indexHtml(WebApp app, List<WebAsset> assets, String compilerIdentity) {
        String escapedTitle = html(app.getTitle());
        String escapedCanvas = html(app.getCanvasId());
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
                    <script id="libfdx-bootstrap-data" type="application/json">%s</script>
                    %s
                </body>
                </html>
                """.formatted(escapedTitle, canvasSizeCss, escapedCanvas, canvasWidth, canvasHeight, fillWindowAttribute,
                        bootstrapData(assets, compilerIdentity), applicationScripts(app))
                .trim() + "\n";
    }

    private static String applicationScripts(WebApp app) {
        String target = app.getTargetFileName();
        String entry = "[\"" + inlineJs(app.getEntryPointName()) + "\"]";
        String args = "[" + app.getMainClassArgs().replace("</", "<\\/") + "]";
        String start = app.isWasm()
                ? "return TeaVM.wasmGC.load(\"" + inlineJs(target) + "\").then(function(app) { return app.exports"
                        + entry + "(" + args + "); });"
                : "return window" + entry + "(" + args + ");";
        return """
                <script>
                function libfdxStartupError(error) {
                    var message = error && error.stack || String(error);
                    console.error(message);
                    var output = document.getElementById('libfdx-error');
                    if (output) return;
                    output = document.createElement('pre');
                    output.id = 'libfdx-error';
                    document.body.appendChild(output);
                    output.textContent = 'libfdx startup/runtime failed\\n' + message;
                }
                window.addEventListener('error', function(event) { libfdxStartupError(event.error || event.message); });
                window.addEventListener('unhandledrejection', function(event) { libfdxStartupError(event.reason); });
                window.addEventListener('load', function() {
                    Promise.resolve().then(function() { %s }).catch(libfdxStartupError);
                });
                </script>
                <script defer src="%s" onerror="libfdxStartupError('Application script failed to load')"></script>
                """.formatted(start, html(target + (app.isWasm() ? "-runtime.js" : ""))).trim();
    }

    private static String bootstrapData(List<WebAsset> assets, String compilerIdentity) {
        StringBuilder data = new StringBuilder("{\"runtimeBase\":\"scripts/\",\"shaderCompilerIdentity\":\"")
                .append(inlineJs(compilerIdentity)).append("\",\"assets\":[");
        for (int index = 0; index < assets.size(); index++) {
            if (index > 0) data.append(',');
            WebAsset asset = assets.get(index);
            data.append("{\"path\":\"").append(inlineJs(asset.getPath())).append("\",\"size\":")
                    .append(asset.getSize()).append('}');
        }
        return data.append("]}").toString();
    }

    private static String inlineJs(String value) {
        return js(value).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    private static String shaderCompilerIdentity(Path scriptsRoot) throws IOException {
        if (!Files.isRegularFile(scriptsRoot.resolve("fdx.js"))
                || !Files.isRegularFile(scriptsRoot.resolve("fdx.wasm"))) return "";
        try {
            StringBuilder identity = new StringBuilder("fdx-web-fdxr2-v1");
            for (String name : List.of("fdx.js", "fdx.wasm")) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = Files.newInputStream(scriptsRoot.resolve(name))) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) >= 0) if (count > 0) digest.update(buffer, 0, count);
                }
                identity.append(':').append(HexFormat.of().formatHex(digest.digest()));
            }
            return identity.toString();
        } catch (NoSuchAlgorithmException error) { throw new IOException("SHA-256 unavailable", error); }
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
            boolean webRuntimeJar = containsPublishableWebAssembly(jarPath, runtimeEntries);

            Set<String> archivePaths = new HashSet<>();
            for (ZipEntry entry : runtimeEntries) {
                if (!webRuntimeJar) {
                    continue;
                }
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
                    && !resourcePath.equalsIgnoreCase(LEGACY_LOADER_PATH)) {
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
        String loaderKey = LEGACY_LOADER_PATH.toLowerCase(Locale.ROOT);
        if (portableKey.equals(loaderKey)) {
            return;
        }
        if (portableKey.startsWith(loaderKey + "/")) {
            throw new IOException("Runtime script path conflicts with legacy loader '" + LEGACY_LOADER_PATH
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
                || resourcePath.equalsIgnoreCase(WebWorkerSource.RESOURCE)
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
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                escaped.append('\\').append(c);
            } else if (c < 32 || c == 0x2028 || c == 0x2029) {
                escaped.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4) {
                    escaped.append("0123456789abcdef".charAt((c >> shift) & 15));
                }
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
