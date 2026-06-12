package io.github.libfdx.backend.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final Set<String> RUNTIME_SCRIPT_NAMES = Set.of("jWebGPU.js", "jWebGPU.wasm", "fdx.js",
            "fdx-loader.js", "fdx.wasm", "runtime.js", "runtime.wasm");

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
        String args = app.getMainClassArgs();
        String mode = app.isWasm()
                ? """
                return TeaVM.wasmGC.load("%s").then(function(teavm) {
                    teavm.exports.%s([%s]);
                });
                """.formatted(js(app.getTargetFileName()), app.getEntryPointName(), args).trim()
                : """
                %s(%s);
                """.formatted(app.getEntryPointName(), args).trim();
        String script = app.isWasm()
                ? "<script type=\"text/javascript\" src=\"" + html(app.getTargetFileName()) + "-runtime.js\"></script>"
                : "<script type=\"text/javascript\" src=\"" + html(app.getTargetFileName()) + "\"></script>";
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
                    <script type="text/javascript" src="scripts/fdx.js"></script>
                    <script type="text/javascript" src="scripts/fdx-loader.js"></script>
                    %s
                    <script type="text/javascript">
                        function libfdxShowError(prefix, error) {
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
                        window.addEventListener("error", function(event) {
                            libfdxShowError("libfdx runtime error", event.error || event.message);
                        });
                        window.addEventListener("unhandledrejection", function(event) {
                            libfdxShowError("libfdx promise rejection", event.reason);
                        });
                        window.addEventListener("load", function() {
                            var assetBase = new URL("assets/", window.location.href).href;
                            console.log("%%clibfdx assets: " + assetBase + " (%d files)", "color:#d50000;font-weight:bold");
                            var preloadRuntime = window.libfdxPreloadRuntimeCore || function() { return Promise.resolve(); };
                            preloadRuntime().then(function() {
                                %s
                            }).catch(function(error) {
                                libfdxShowError("libfdx asset preload failed", error);
                                throw error;
                            });
                        });
                    </script>
                </body>
                </html>
                """.formatted(escapedTitle, canvasSizeCss, escapedCanvas, canvasWidth, canvasHeight, fillWindowAttribute,
                script, assetCount, mode).trim() + "\n";
    }

    private static void copyRuntimeScripts(Path root, List<Path> runtimeClasspath) throws IOException {
        Path scriptsRoot = root.resolve("scripts").toAbsolutePath().normalize();
        WebAssets.deleteDirectory(scriptsRoot);
        LinkedHashMap<String, Long> copied = new LinkedHashMap<>();
        for (Path entry : runtimeClasspath) {
            Path normalized = entry.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                copyRuntimeScriptsFromDirectory(normalized, scriptsRoot, copied);
            } else if (Files.isRegularFile(normalized) && normalized.getFileName().toString().endsWith(".jar")) {
                copyRuntimeScriptsFromJar(normalized, scriptsRoot, copied);
            }
        }
        if (copied.isEmpty()) {
            WebAssets.deleteDirectory(scriptsRoot);
        }
    }

    private static void copyRuntimeScriptsFromDirectory(Path directory, Path scriptsRoot, Map<String, Long> copied)
            throws IOException {
        for (String name : RUNTIME_SCRIPT_NAMES) {
            Path source = directory.resolve(name);
            if (Files.isRegularFile(source) && shouldCopyRuntimeScript(name, Files.size(source), copied)) {
                copyRuntimeScript(source, scriptsRoot.resolve(name), scriptsRoot);
            }
        }
    }

    private static void copyRuntimeScriptsFromJar(Path jarPath, Path scriptsRoot, Map<String, Long> copied)
            throws IOException {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
                if (entry.isDirectory() || !RUNTIME_SCRIPT_NAMES.contains(name)
                        || !shouldCopyRuntimeScript(name, entry.getSize(), copied)) {
                    continue;
                }
                Path output = scriptsRoot.resolve(name).toAbsolutePath().normalize();
                if (!output.startsWith(scriptsRoot)) {
                    throw new IOException("Refusing to extract runtime script outside output directory: " + entry.getName());
                }
                Files.createDirectories(output.getParent());
                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyRuntimeScript(Path source, Path output, Path scriptsRoot) throws IOException {
        Path target = output.toAbsolutePath().normalize();
        if (!target.startsWith(scriptsRoot)) {
            throw new IOException("Refusing to copy runtime script outside output directory: " + source);
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean shouldCopyRuntimeScript(String name, long size, Map<String, Long> copied) {
        Long existingSize = copied.get(name);
        if (existingSize != null && existingSize >= size) {
            return false;
        }
        copied.put(name, size);
        return true;
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
