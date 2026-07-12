package io.github.libfdx.backend.desktopc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativeProjectWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void removesOnlyLineDirectivesInsideParenthesizedExpressions() throws Exception {
        verifyLineDirectivePatch("\n", "lf");
        verifyLineDirectivePatch("\r\n", "crlf");
    }

    @Test
    void patchesTeaVmLoggingWithEitherLineSeparator() throws Exception {
        verifyLogFlushPatch("\n", "lf");
        verifyLogFlushPatch("\r\n", "crlf");
    }

    @Test
    void patchesTeaVmEnumCountNarrowingIdempotently() throws Exception {
        Path root = temporaryDirectory.resolve("core-header");
        Path sources = root.resolve("c/src");
        Files.createDirectories(sources);
        Path core = sources.resolve("core.h");
        Files.writeString(core, "inline int32_t count(void* values) {\n"
                + "    return *(intptr_t*) values;\n"
                + "}\n");

        writeProject(root, sources);
        writeProject(root, sources);

        String patched = Files.readString(core);
        assertTrue(patched.contains("return (int32_t) *(intptr_t*) values;"));
        assertEquals(1, countOccurrences(patched, "(int32_t) *(intptr_t*) values"));
    }

    @Test
    void writesScopedMsvcRuntimeAndWarningSettings() throws Exception {
        Path root = temporaryDirectory.resolve("cmake");
        Path sources = root.resolve("c/src");
        Files.createDirectories(sources);

        writeProject(root, sources);

        String cmake = Files.readString(root.resolve("CMakeLists.txt"));
        assertTrue(cmake.contains("cmake_minimum_required(VERSION 3.15)"));
        assertTrue(cmake.contains("set(CMAKE_MSVC_RUNTIME_LIBRARY \"MultiThreadedDLL\")"));
        assertTrue(cmake.contains("set_source_files_properties(\""
                + root.toAbsolutePath().normalize().toString().replace('\\', '/')
                + "/c/src/app_include.c\" PROPERTIES"));
        assertTrue(cmake.contains("COMPILE_OPTIONS \"/wd4090;/wd4244\""));
        assertTrue(cmake.contains("target_compile_options(freetype PRIVATE /wd4244 /wd4267)"));
        assertTrue(cmake.contains("target_link_options(test PRIVATE /NODEFAULTLIB:LIBCMT /IGNORE:4099)"));
        String aggregate = Files.readString(sources.resolve("app_include.c"));
        assertTrue(aggregate.indexOf("#  include <windows.h>") < aggregate.indexOf("#    include <GL/glew.h>"));
    }

    private void verifyLineDirectivePatch(String newline, String name) throws Exception {
        Path root = temporaryDirectory.resolve("lines-" + name);
        Path sources = root.resolve("c/src");
        Files.createDirectories(sources);
        Path generated = sources.resolve("generated.c");
        String source = String.join(newline,
                "#line 10 \"safe-before.c\"",
                "value = TEAVM_FIELD(",
                "#line 20 \"unsafe-first.c\"",
                "TEAVM_WITH_CALL_SITE_ID(",
                "#line 30 \"unsafe-nested.c\"",
                "call(value))",
                "#line 40 \"unsafe-last.c\"",
                ", cls_Type, fld_value);",
                "#line 50 \"safe-after.c\"",
                "const char* text = \"parentheses ( )\"; // ignored (",
                "/* ignored ( */ int value2 = (1 + 2); /* ignored ) */",
                "#line 60 \"safe-final.c\"",
                "") ;
        Files.writeString(generated, source);

        writeProject(root, sources);

        String patched = Files.readString(generated);
        assertTrue(patched.contains("#line 10 \"safe-before.c\""));
        assertTrue(patched.contains("#line 50 \"safe-after.c\""));
        assertTrue(patched.contains("#line 60 \"safe-final.c\""));
        assertFalse(patched.contains("unsafe-first.c"));
        assertFalse(patched.contains("unsafe-nested.c"));
        assertFalse(patched.contains("unsafe-last.c"));
        assertEquals(source.chars().filter(character -> character == '\n').count(),
                patched.chars().filter(character -> character == '\n').count());
    }

    private void verifyLogFlushPatch(String newline, String name) throws Exception {
        Path root = temporaryDirectory.resolve("log-" + name);
        Path sources = root.resolve("c/src");
        Files.createDirectories(sources);
        Path log = sources.resolve("log.c");
        Files.writeString(log, "void teavm_logCodePoint(int c) {" + newline
                + "            putwchar(c);" + newline
                + "}" + newline);

        writeProject(root, sources);
        NativeProjectWriter.write(NativeProject.builder()
                .buildRoot(root)
                .generatedSourcesDirectory(sources)
                .releaseDirectory(root.resolve("c/release"))
                .projectName("test")
                .build());

        String patched = Files.readString(log);
        assertTrue(patched.contains("fflush(stdout);"));
        assertEquals(1, countOccurrences(patched, "fflush(stdout);"));
        assertTrue(patched.contains(newline + "            if (c == '\\n') {" + newline));
    }

    private static void writeProject(Path root, Path sources) throws Exception {
        NativeProjectWriter.write(NativeProject.builder()
                .buildRoot(root)
                .generatedSourcesDirectory(sources)
                .releaseDirectory(root.resolve("c/release"))
                .projectName("test")
                .build());
    }

    private static int countOccurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
