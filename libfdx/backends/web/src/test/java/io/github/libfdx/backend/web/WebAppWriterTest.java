package io.github.libfdx.backend.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebAppWriterTest {
    private static final String WORKER_RESOURCE = "io/github/libfdx/backend/web/internal/worker.js";
    private static final byte[] WORKER_PAYLOAD = "/* bundled worker fixture */".getBytes(StandardCharsets.UTF_8);
    @TempDir
    Path temporaryDirectory;

    @Test
    void indexStartsConfiguredApplicationForBothTargets() throws Exception {
        for (boolean wasm : new boolean[]{false, true}) {
            Path webapp = temporaryDirectory.resolve(wasm ? "wasm" : "js");
            String target = wasm ? "game.wasm" : "game.js";
            WebAppWriter.write(WebApp.builder().webappDirectory(webapp).wasm(wasm)
                    .targetFileName(target).entryPointName("launch").mainClassArgs("\"one\",\"two\"").build());
            String html = Files.readString(webapp.resolve("index.html"));
            assertTrue(html.contains("[\"launch\"]([\"one\",\"two\"]);"));
            assertTrue(html.contains("src=\"" + target + (wasm ? "-runtime.js" : "") + "\""));
            assertEquals(wasm, html.contains("TeaVM.wasmGC.load(\"game.wasm\")"));
            assertFalse(html.contains("libfdxStartupError"));
            assertFalse(html.contains("libfdx-error"));
            assertFalse(html.contains("addEventListener"));
            assertFalse(html.contains("onerror="));
            assertTrue(html.indexOf("src=\"") < html.indexOf("[\"launch\"]"));
            assertDirectStartup(webapp);
        }
    }

    @Test
    void assetsStayOutOfHtmlAndEntryPointCannotCloseItsScriptElement() throws Exception {
        Path assets = temporaryDirectory.resolve("assets");
        write(assets.resolve("a&b.txt"), new byte[]{1});
        Path webapp = temporaryDirectory.resolve("safe-html");
        WebAppWriter.write(WebApp.builder().webappDirectory(webapp).asset(assets)
                .entryPointName("</script><script>bad()</script>").build());
        String html = Files.readString(webapp.resolve("index.html"));
        assertFalse(html.contains("a\\u0026b.txt"));
        assertFalse(html.contains("a&b.txt"));
        assertTrue(html.contains("\\u003c/script\\u003e"));
        assertFalse(html.contains("<script>bad()"));
    }

    @Test
    void compilerCacheIdentityTracksBothPublishedBinariesAndDisablesMissingCompiler() throws Exception {
        Path runtime = Files.createDirectories(temporaryDirectory.resolve("compiler"));
        Path webapp = temporaryDirectory.resolve("compiler-webapp");
        writeWebApp(webapp, runtime);
        String missing = compilerIdentity(webapp, runtime);
        assertEquals("", missing);
        write(runtime.resolve("fdx.js"), new byte[]{1, 2});
        write(runtime.resolve("fdx.wasm"), new byte[]{3, 4});
        writeWebApp(webapp, runtime);
        String original = compilerIdentity(webapp, runtime);
        assertTrue(original.matches("fdx-web-fdxr2-v1:[0-9a-f]{64}:[0-9a-f]{64}"));
        writeWebApp(webapp, runtime);
        assertEquals(original, compilerIdentity(webapp, runtime));
        write(runtime.resolve("fdx.js"), new byte[]{1, 5});
        writeWebApp(webapp, runtime);
        String scriptChanged = compilerIdentity(webapp, runtime);
        assertNotEquals(original, scriptChanged);
        write(runtime.resolve("fdx.wasm"), new byte[]{3, 6});
        writeWebApp(webapp, runtime);
        assertNotEquals(scriptChanged, compilerIdentity(webapp, runtime));
        Path jar = createJar("compiler.jar", Map.of("fdx.js", new byte[]{1, 5}, "fdx.wasm", new byte[]{3, 6}));
        assertEquals(compilerIdentity(webapp, runtime), compilerIdentity(webapp, jar));
    }

    private static String compilerIdentity(Path webapp, Path runtime) {
        Properties properties = new Properties();
        TeaVMAssetProperties.putRuntimeClasspath(properties, List.of(runtime), webapp);
        return TeaVMAssetProperties.compilerIdentity(properties);
    }

    @Test
    void escapesAssetNamesForThePublishedJavaScriptInventory() {
        org.junit.jupiter.api.Assertions.assertEquals("a\\\"b\\\\c\\u000a\\u000d\\u0009\\u2028\\u2029",
                WebAppWriter.js("a\"b\\c\n\r\t\u2028\u2029"));
    }

    @Test
    void publishesSharedAssetsFromClasspathDirectory() throws Exception {
        Path runtime = Files.createDirectories(temporaryDirectory.resolve("runtime"));
        byte[] font = new byte[] { 0, 1, 0, 0, 7 };
        write(runtime.resolve("libfdx-assets/ui/font/default.ttf"), font);
        Path webapp = temporaryDirectory.resolve("webapp");

        writeWebApp(webapp, runtime);

        assertArrayEquals(font,
                Files.readAllBytes(webapp.resolve("assets/libfdx-assets/ui/font/default.ttf")));
        assertCompiledAsset(runtime, "libfdx-assets/ui/font/default.ttf", 5);
    }

    @Test
    void publishesSharedAssetsFromClasspathJar() throws Exception {
        byte[] license = "shared-license".getBytes(StandardCharsets.UTF_8);
        Path jar = createJar("shared-assets.jar",
                Map.of("libfdx-assets/ui/font/OFL.txt", license));
        Path webapp = temporaryDirectory.resolve("webapp");

        writeWebApp(webapp, jar);

        assertArrayEquals(license,
                Files.readAllBytes(webapp.resolve("assets/libfdx-assets/ui/font/OFL.txt")));
        assertCompiledAsset(jar, "libfdx-assets/ui/font/OFL.txt", license.length);
    }

    private void assertCompiledAsset(Path runtime, String path, int size) {
        Properties properties = new Properties();
        TeaVMAssetProperties.putRuntimeClasspath(properties, List.of(runtime), temporaryDirectory.resolve("webapp"));
        var assets = new WebAssetMetadataGenerator().generateMetadata(properties);
        var shared = assets.values.stream().filter(asset -> asset.path.equals(path)).toList();
        assertEquals(1, shared.size());
        assertEquals(size, shared.getFirst().size);
        // Application overrides must match what packaging will actually publish.
        TeaVMAssetProperties.putInto(properties, List.of(new WebAsset(path, size + 1, null)));
        assets = new WebAssetMetadataGenerator().generateMetadata(properties);
        shared = assets.values.stream().filter(asset -> asset.path.equals(path)).toList();
        assertEquals(1, shared.size());
        assertEquals(size + 1, shared.getFirst().size);
    }

    @Test
    void discoversRuntimeScriptsFromClasspathDirectoryWithoutFilenameRegistration() throws Exception {
        Path runtime = Files.createDirectories(temporaryDirectory.resolve("runtime"));
        byte[] javascript = "custom-runtime".getBytes(StandardCharsets.UTF_8);
        byte[] webAssembly = new byte[] { 0, 97, 115, 109 };
        write(runtime.resolve("custom-plugin-bootstrap.js"), javascript);
        write(runtime.resolve("vendor/custom-plugin.wasm"), webAssembly);
        write(runtime.resolve("source.js.map"), javascript);
        write(runtime.resolve("notes.txt"), javascript);
        write(runtime.resolve("META-INF/private.js"), javascript);
        write(runtime.resolve("WEB-INF/private.wasm"), webAssembly);
        write(runtime.resolve("fdx-loader.js"), "dependency-loader".getBytes(StandardCharsets.UTF_8));

        Path webapp = runtime.resolve("generated-webapp");
        write(webapp.resolve("app.js"), javascript);
        write(webapp.resolve("scripts/stale.js"), javascript);

        writeWebApp(webapp, runtime);

        assertArrayEquals(javascript, Files.readAllBytes(webapp.resolve("scripts/custom-plugin-bootstrap.js")));
        assertArrayEquals(webAssembly, Files.readAllBytes(webapp.resolve("scripts/vendor/custom-plugin.wasm")));
        assertFalse(Files.exists(webapp.resolve("scripts/source.js.map")));
        assertFalse(Files.exists(webapp.resolve("scripts/notes.txt")));
        assertFalse(Files.exists(webapp.resolve("scripts/META-INF/private.js")));
        assertFalse(Files.exists(webapp.resolve("scripts/WEB-INF/private.wasm")));
        assertFalse(Files.exists(webapp.resolve("scripts/app.js")));
        assertFalse(Files.exists(webapp.resolve("scripts/stale.js")));
        assertFalse(Files.exists(webapp.resolve("scripts/fdx-loader.js")));
    }

    @Test
    void discoversRuntimeScriptsFromJarAndPreservesResourcePaths() throws Exception {
        byte[] javascript = "jar-runtime".getBytes(StandardCharsets.UTF_8);
        byte[] webAssembly = new byte[] { 1, 2, 3, 4 };
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin/bootstrap.js", javascript);
        entries.put("plugin/native/runtime.wasm", webAssembly);
        entries.put("plugin/bootstrap.js.map", javascript);
        entries.put("META-INF/build.js", javascript);
        entries.put("WEB-INF/server.wasm", webAssembly);
        entries.put("org/teavm/backend/javascript/runtime.js", javascript);
        Path jar = createJar("runtime.jar", entries);
        Path webapp = temporaryDirectory.resolve("webapp");

        writeWebApp(webapp, jar);

        assertArrayEquals(javascript, Files.readAllBytes(webapp.resolve("scripts/plugin/bootstrap.js")));
        assertArrayEquals(webAssembly, Files.readAllBytes(webapp.resolve("scripts/plugin/native/runtime.wasm")));
        assertFalse(Files.exists(webapp.resolve("scripts/plugin/bootstrap.js.map")));
        assertFalse(Files.exists(webapp.resolve("scripts/META-INF/build.js")));
        assertFalse(Files.exists(webapp.resolve("scripts/WEB-INF/server.wasm")));
        assertFalse(Files.exists(webapp.resolve("scripts/org/teavm/backend/javascript/runtime.js")));
    }

    @Test
    void keepsWorkerCompilerInputOutOfThePageAndDeployment() throws Exception {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(WORKER_RESOURCE, WORKER_PAYLOAD);
        entries.put("embedded/admin-tool.js", "internal".getBytes(StandardCharsets.UTF_8));
        Path jar = createJar("javascript-only.jar", entries);
        Path webapp = temporaryDirectory.resolve("webapp");

        WebAppWriter.write(WebApp.builder().webappDirectory(webapp).runtimeClasspath(jar).build());

        assertDirectStartup(webapp);
        assertFalse(Files.exists(webapp.resolve("scripts/embedded/admin-tool.js")));
    }

    @Test
    void excludesDirectoryWorkerAndRemovesStaleDeploymentScripts() throws Exception {
        Path runtime = temporaryDirectory.resolve("runtime");
        Path webapp = temporaryDirectory.resolve("webapp");
        write(runtime.resolve(WORKER_RESOURCE), WORKER_PAYLOAD);
        write(webapp.resolve("scripts/fdx-loader.js"), WORKER_PAYLOAD);
        write(webapp.resolve("scripts/libfdx-asset-worker.js"), WORKER_PAYLOAD);
        write(webapp.resolve("scripts/libfdx-pbr-worker.js"), WORKER_PAYLOAD);
        write(webapp.resolve("scripts/libfdx-shader-worker.js"), WORKER_PAYLOAD);
        WebAppWriter.write(WebApp.builder().webappDirectory(webapp).runtimeClasspath(runtime).build());
        assertDirectStartup(webapp);
    }

    @Test
    void startupPackagingDoesNotRequireWorkerCompilerInputs() throws Exception {
        Path webapp = temporaryDirectory.resolve("webapp");
        WebAppWriter.write(WebApp.builder().webappDirectory(webapp).build());
        assertDirectStartup(webapp);
    }
    private static void assertDirectStartup(Path webapp) throws IOException {
        String html = Files.readString(webapp.resolve("index.html"));
        assertFalse(Files.exists(webapp.resolve("scripts/fdx-loader.js")));
        assertFalse(html.contains("fdx-loader.js"));
        assertFalse(html.contains("bundled worker fixture"));
        assertFalse(html.contains("FdxModule"));
        assertFalse(html.contains("application/json"));
        assertFalse(html.contains("libfdx-bootstrap-data"));
        assertFalse(html.contains("runtimeBase"));
        assertFalse(html.contains("scripts/"));
        assertFalse(html.contains("shaderCompilerIdentity"));
        assertEquals(2, html.split("<script", -1).length - 1);
        if (Files.exists(webapp.resolve("scripts"))) {
            try (var files = Files.walk(webapp.resolve("scripts"))) {
                assertEquals(0, files.filter(Files::isRegularFile).count());
            }
        }
    }
    @Test
    void doesNotPublishJavaScriptFromAnUnrelatedJar() throws Exception {
        Path jar = createJar("unrelated.jar",
                Map.of("embedded/admin-tool.js", "internal".getBytes(StandardCharsets.UTF_8)));
        Path webapp = temporaryDirectory.resolve("webapp");

        writeWebApp(webapp, jar);

        assertFalse(Files.exists(webapp.resolve("scripts/embedded/admin-tool.js")));
    }

    @Test
    void excludedWebAssemblyDoesNotMakeAnUnrelatedJarPublishable() throws Exception {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/private.wasm", new byte[] { 0, 97, 115, 109 });
        entries.put("embedded/admin-tool.js", "internal".getBytes(StandardCharsets.UTF_8));
        Path jar = createJar("unrelated-with-excluded-wasm.jar", entries);
        Path webapp = temporaryDirectory.resolve("webapp");

        writeWebApp(webapp, jar);

        assertFalse(Files.exists(webapp.resolve("scripts/embedded/admin-tool.js")));
        assertFalse(Files.exists(webapp.resolve("scripts/META-INF/private.wasm")));
    }

    @Test
    void acceptsByteIdenticalDuplicateRuntimeScripts() throws Exception {
        byte[] content = "same-runtime".getBytes(StandardCharsets.UTF_8);
        Path runtime = Files.createDirectories(temporaryDirectory.resolve("runtime"));
        write(runtime.resolve("shared/runtime.js"), content);
        Path jar = createJar("duplicate.jar", Map.of("shared/runtime.js", content));
        Path webapp = temporaryDirectory.resolve("webapp");

        writeWebApp(webapp, runtime, jar);

        assertArrayEquals(content, Files.readAllBytes(webapp.resolve("scripts/shared/runtime.js")));
    }

    @Test
    void rejectsDifferentRuntimeScriptsAtTheSamePathBeforeReplacingExistingOutput() throws Exception {
        Path first = Files.createDirectories(temporaryDirectory.resolve("first"));
        Path second = Files.createDirectories(temporaryDirectory.resolve("second"));
        write(first.resolve("conflict.js"), "left".getBytes(StandardCharsets.UTF_8));
        write(second.resolve("conflict.js"), "rite".getBytes(StandardCharsets.UTF_8));
        Path webapp = temporaryDirectory.resolve("webapp");
        Path sentinel = webapp.resolve("scripts/sentinel.txt");
        write(sentinel, new byte[] { 9 });

        IOException error = assertThrows(IOException.class, () -> writeWebApp(webapp, first, second));

        assertTrue(error.getMessage().contains("Conflicting runtime script 'conflict.js'"));
        assertTrue(error.getMessage().contains(first.toString()));
        assertTrue(error.getMessage().contains(second.toString()));
        assertTrue(Files.exists(sentinel));
    }

    @Test
    void rejectsRuntimeScriptPathsThatDifferOnlyByCase() throws Exception {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("runtime.wasm", new byte[] { 0, 97, 115, 109 });
        entries.put("Vendor/Runtime.js", new byte[] { 1 });
        entries.put("vendor/runtime.js", new byte[] { 1 });
        Path jar = createJar("case-collision.jar", entries);

        IOException error = assertThrows(IOException.class,
                () -> writeWebApp(temporaryDirectory.resolve("webapp"), jar));

        assertTrue(error.getMessage().contains("differ only by case"));
        assertTrue(error.getMessage().contains("Vendor/Runtime.js"));
        assertTrue(error.getMessage().contains("vendor/runtime.js"));
    }

    @Test
    void rejectsFileDirectoryRuntimePathCollisionsBeforeReplacingExistingOutput() throws Exception {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("vendor.js", new byte[] { 1 });
        entries.put("vendor.js/worker.wasm", new byte[] { 2 });
        Path jar = createJar("file-directory-collision.jar", entries);
        Path webapp = temporaryDirectory.resolve("webapp");
        Path sentinel = webapp.resolve("scripts/sentinel.txt");
        write(sentinel, new byte[] { 9 });

        IOException error = assertThrows(IOException.class, () -> writeWebApp(webapp, jar));

        assertTrue(error.getMessage().contains("file/directory path"));
        assertTrue(error.getMessage().contains("vendor.js"));
        assertTrue(error.getMessage().contains("vendor.js/worker.wasm"));
        assertTrue(Files.exists(sentinel));
    }

    @Test
    void rejectsRuntimePathsBelowTheGeneratedLoaderBeforeReplacingExistingOutput() throws Exception {
        Path jar = createJar("loader-collision.jar", Map.of(
                "fdx-loader.js/worker.wasm", new byte[] { 0, 97, 115, 109 }));
        Path webapp = temporaryDirectory.resolve("webapp");
        Path sentinel = webapp.resolve("scripts/sentinel.txt");
        write(sentinel, new byte[] { 9 });

        IOException error = assertThrows(IOException.class, () -> writeWebApp(webapp, jar));

        assertTrue(error.getMessage().contains("conflicts with legacy loader"));
        assertTrue(error.getMessage().contains("fdx-loader.js/worker.wasm"));
        assertTrue(Files.exists(sentinel));
    }

    @Test
    void rejectsArchivePathsThatCouldEscapeTheScriptsDirectory() throws Exception {
        Path jar = createJar("invalid.jar", Map.of(
                "runtime.wasm", new byte[] { 0, 97, 115, 109 },
                "../escape.js", new byte[] { 1 }));
        Path webapp = temporaryDirectory.resolve("webapp");

        IOException error = assertThrows(IOException.class, () -> writeWebApp(webapp, jar));

        assertTrue(error.getMessage().contains("Invalid runtime script path '../escape.js'"));
        assertFalse(Files.exists(webapp.resolve("escape.js")));
    }

    @Test
    void rejectsNonPortableArchivePathsBeforeReplacingExistingOutput() throws Exception {
        Path jar = createJar("invalid-portable-path.jar", Map.of(
                "runtime.wasm", new byte[] { 0, 97, 115, 109 },
                "bad?.js", new byte[] { 1 }));
        Path webapp = temporaryDirectory.resolve("webapp");
        Path sentinel = webapp.resolve("scripts/sentinel.txt");
        write(sentinel, new byte[] { 9 });

        IOException error = assertThrows(IOException.class, () -> writeWebApp(webapp, jar));

        assertTrue(error.getMessage().contains("Invalid runtime script path 'bad?.js'"));
        assertTrue(Files.exists(sentinel));
    }

    private void writeWebApp(Path webapp, Path... runtimeClasspath) throws IOException {
        WebApp.Builder builder = WebApp.builder().webappDirectory(webapp);
        for (Path entry : runtimeClasspath) {
            builder.runtimeClasspath(entry);
        }
        WebAppWriter.write(builder.build());
    }

    private static void write(Path path, byte[] content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content);
    }

    private Path createJar(String name, Map<String, byte[]> entries) throws IOException {
        Path jar = temporaryDirectory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }
}
