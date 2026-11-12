package io.github.libfdx.tools.uipreview;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiRoot;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

final class PreviewJavaCompilerTest {
    @Test
    void bodyAndPrivateStructureEditsCompileInIsolationAndFreshClassesShareSdkTypes() throws Exception {
        try (Fixture fixture = new Fixture()) {
            byte[] gradleOutput = Files.readAllBytes(fixture.output.resolve("example/Panel.class"));
            fixture.edit("private int added = 7;", "updated " + "\" + added + \"");
            var result = fixture.compile();
            assertEquals(PreviewJavaCompiler.State.COMPILED, result.state(), result.diagnostics());
            assertArrayEquals(gradleOutput, Files.readAllBytes(fixture.output.resolve("example/Panel.class")));
            fixture.outputs.accept(result);
            fixture.compiler.accept(result);
            try (var loader = new PreviewCompositionLoader(
                    List.of(result.outputs().values().iterator().next().toString(), fixture.output.toString()),
                    getClass().getClassLoader())) {
                assertSame(UiContent.class, loader.loadClass(UiContent.class.getName()));
                Object content = loader.loadClass("example.Panel").getConstructor().newInstance();
                assertInstanceOf(UiContent.class, content);
                assertEquals("updated 7", content.getClass().getMethod("value").invoke(content));
                UiRoot root = new io.github.libfdx.ui.UiToolkit(null).root(null, null).allowFontFallback(true);
                try {
                    root.setContent((UiContent)content);
                    root.update(0);
                    assertEquals("updated 7", root.rootNode().children().get(0).text());
                } finally {
                    root.dispose();
                }
            }
        }
    }

    @Test
    void compilerErrorsKeepPreviousOutputAndTheWarmCompilerRecovers() throws Exception {
        try (Fixture fixture = new Fixture()) {
            byte[] original = Files.readAllBytes(fixture.output.resolve("example/Panel.class"));
            Files.writeString(fixture.source, "package example; public class Panel { syntax error }");
            var failed = fixture.compile();
            assertEquals(PreviewJavaCompiler.State.FAILED, failed.state());
            assertTrue(failed.diagnostics().contains("Panel.java"));
            assertArrayEquals(original, Files.readAllBytes(fixture.output.resolve("example/Panel.class")));
            fixture.edit("", "recovered");
            assertEquals(PreviewJavaCompiler.State.COMPILED, fixture.compile().state());
        }
    }

    @Test
    void visibleConstantsAndSignaturesRequireDependentRecompilation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.edit("public static final int LIMIT = 6;", "constant changed");
            var constant = fixture.compile();
            assertEquals(PreviewJavaCompiler.State.GRADLE, constant.state());
            assertTrue(constant.diagnostics().contains("constants"));
            fixture.edit("", "method changed");
            Files.writeString(fixture.source, Files.readString(fixture.source)
                    .replace("public String value()", "public Object value()"));
            assertEquals(PreviewJavaCompiler.State.GRADLE, fixture.compile().state());
        }
    }

    @Test
    void removingANestedClassUsesGradleInsteadOfKeepingAStaleClassFromThePreviousJar() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.edit("private static class Extra {}", "nested");
            var added = fixture.compile();
            assertEquals(PreviewJavaCompiler.State.COMPILED, added.state(), added.diagnostics());
            fixture.compiler.accept(added);
            fixture.outputs.accept(added);
            fixture.edit("", "removed");
            var removed = fixture.compile();
            assertEquals(PreviewJavaCompiler.State.GRADLE, removed.state());
            assertTrue(removed.diagnostics().contains("outputs were removed"));
        }
    }

    @Test
    void newlyAddedDeletedAndNonJavaInputsAreNotSilentlyCompiled() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path added = fixture.source.resolveSibling("Added.java");
            Files.writeString(added, "package example; class Added {}");
            assertFalse(fixture.compiler.supports(Set.of(added)));
            assertFalse(fixture.compiler.supports(Set.of(fixture.source.resolveSibling("skin.png"))));
            assertTrue(fixture.compiler.supports(Set.of(fixture.source)));
            Files.delete(fixture.source);
            assertFalse(fixture.compiler.supports(Set.of(fixture.source)));
        }
    }

    @Test
    void processorContractsAndExplicitOptOutRetainTheGradlePath() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.model.setProperty("java.0.direct", "false");
            try (var compiler = fixture.newCompiler()) {
                assertFalse(compiler.supports(Set.of(fixture.source)));
            }
            fixture.model.setProperty("java.0.direct", "true");
            fixture.model.setProperty("fastReload", "false");
            try (var compiler = fixture.newCompiler()) {
                assertFalse(compiler.supports(Set.of(fixture.source)));
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Path directory;
        final Path source;
        final Path output;
        final Properties model = new Properties();
        final PreviewJavaCompiler compiler;
        final PreviewJavaOutputs outputs;
        int revision;

        Fixture() throws Exception {
            Path parent = Path.of("build/tmp/preview-java").toAbsolutePath();
            Files.createDirectories(parent);
            directory = Files.createTempDirectory(parent, "case-");
            source = directory.resolve("src/example/Panel.java");
            output = directory.resolve("classes");
            Files.createDirectories(source.getParent());
            Files.createDirectories(output);
            edit("", "original");
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-d", output.toString(),
                    "-classpath", System.getProperty("java.class.path"), source.toString()));
            model.setProperty("formatVersion", "1");
            model.setProperty("javaExecutable", ProcessHandle.current().info().command().orElseThrow());
            model.setProperty("sessionDirectory", directory.toString());
            model.setProperty("projectDirectory", directory.toString());
            model.setProperty("workingDirectory", directory.toString());
            model.setProperty("fastReload", "true");
            model.setProperty("java.count", "1");
            model.setProperty("java.0.direct", "true");
            model.setProperty("java.0.reloadable", "true");
            model.setProperty("java.0.output", output.toString());
            list("classpath", List.of(output.toString()));
            list("java.0.sources", List.of(source.toString()));
            list("java.0.classpath", List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
            list("java.0.options", List.of("--release", "25", "-encoding", "UTF-8"));
            compiler = newCompiler();
            compiler.warm();
            outputs = new PreviewJavaOutputs(directory.resolve("java-output"));
        }

        void edit(String members, String value) throws Exception {
            String constants = members.contains("LIMIT") ? "" : "public static final int LIMIT = 4;";
            Files.writeString(source, "package example; public final class Panel implements io.github.libfdx.ui.UiContent {"
                    + constants + members + " public String value() { return \"" + value + "\"; }"
                    + " public void build(io.github.libfdx.ui.UiScope ui) { ui.text(String.valueOf(value())); } }");
        }

        PreviewJavaCompiler newCompiler() throws Exception {
            Path file = directory.resolve("launch.properties");
            try (var writer = Files.newBufferedWriter(file)) { model.store(writer, null); }
            return new PreviewJavaCompiler(new PreviewLaunch(file));
        }

        PreviewJavaCompiler.Result compile() throws Exception {
            List<String> classpath = new java.util.ArrayList<>(outputs.classpath());
            classpath.add(output.toString());
            return compiler.compile(Set.of(source), classpath, directory.resolve("compile-" + (++revision)));
        }

        void list(String key, List<String> values) {
            model.setProperty(key + ".count", Integer.toString(values.size()));
            for (int i = 0; i < values.size(); i++) model.setProperty(key + "." + i, values.get(i));
        }

        @Override
        public void close() throws Exception {
            compiler.close();
            PreviewSnapshot.deleteOwned(directory.getParent(), directory);
        }

    }

}
