package io.github.libfdx.backend.psp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PspProjectWriter {
    private static final List<String> NATIVE_RESOURCE_PREFIXES = List.of(
            "libfdx-native/shared/",
            "libfdx-native/psp/");

    private PspProjectWriter() {
    }

    public static Set<Path> write(PspProject project) throws IOException {
        Objects.requireNonNull(project, "project");
        LinkedHashSet<Path> written = new LinkedHashSet<>();
        Path root = project.getBuildRoot();
        Path sources = project.getGeneratedSourcesDirectory();
        Path release = project.getReleaseDirectory();
        Files.createDirectories(root);
        Files.createDirectories(sources);
        Files.createDirectories(release);
        copyNativeResources(project.getNativeResourceClasspath(), root.resolve("c/external_cpp"));
        written.addAll(copyAssets(project.getAssets(), root.resolve("assets")));
        Path include = sources.resolve("app_include.c");
        Files.writeString(include, appInclude(project.getProjectName()), StandardCharsets.UTF_8);
        written.add(include.toAbsolutePath().normalize());
        Path cmake = root.resolve("CMakeLists.txt");
        Files.writeString(cmake, cmake(project), StandardCharsets.UTF_8);
        written.add(cmake.toAbsolutePath().normalize());
        written.add(writeBuildBat(project.getBuildRoot()));
        written.add(writeBuildSh(project.getBuildRoot(), project.getProjectName()));
        return Set.copyOf(written);
    }

    private static String appInclude(String projectName) {
        return """
                #include "PSPInclude.h"
                #include "PSPDebugApi.h"
                #include "PSPGraphicsApi.h"
                #include "PSPCoreApi.h"

                #if defined(__has_include)
                #  if __has_include("teavm_optimizations.h")
                #    include "teavm_optimizations.h"
                #  endif
                #else
                #  include "teavm_optimizations.h"
                #endif

                #if defined(__has_include)
                #  if __has_include("stb_image.h")
                #    define STB_IMAGE_IMPLEMENTATION
                #    include "stb_image.h"
                #  endif
                #else
                #  define STB_IMAGE_IMPLEMENTATION
                #  include "stb_image.h"
                #endif

                #if defined(__has_include)
                #  if __has_include("libfdx_native_image.h")
                #    include "libfdx_native_image.h"
                #  endif
                #else
                #  include "libfdx_native_image.h"
                #endif

                #define LIBFDX_PSP_PROJECT_NAME "%s"
                PSP_MODULE_INFO("%s", 0, 1, 1);
                PSP_MAIN_THREAD_ATTR(THREAD_ATTR_USER);
                PSP_MAIN_THREAD_STACK_SIZE_KB(1024);

                #include "all.c"
                #include "PSPFileSystem.h"
                """.formatted(cString(projectName), cString(projectName));
    }

    private static void copyNativeResources(Iterable<Path> nativeResourceClasspath, Path outputRoot)
            throws IOException {
        deleteDirectory(outputRoot);
        Files.createDirectories(outputRoot);
        Path normalizedOutputRoot = outputRoot.toAbsolutePath().normalize();
        for (Path entry : nativeResourceClasspath) {
            Path normalized = entry.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                copyNativeResourcesFromDirectory(normalized, normalizedOutputRoot);
            } else if (Files.isRegularFile(normalized) && normalized.getFileName().toString().endsWith(".jar")) {
                copyNativeResourcesFromJar(normalized, normalizedOutputRoot);
            }
        }
    }

    private static Set<Path> copyAssets(Iterable<Path> assets, Path outputRoot) throws IOException {
        LinkedHashSet<Path> written = new LinkedHashSet<>();
        deleteDirectory(outputRoot);
        Files.createDirectories(outputRoot);
        Path normalizedOutputRoot = outputRoot.toAbsolutePath().normalize();
        for (Path asset : assets) {
            Path normalizedAsset = asset.toAbsolutePath().normalize();
            if (Files.isDirectory(normalizedAsset)) {
                written.addAll(copyDirectory(normalizedAsset, normalizedOutputRoot));
            } else if (Files.isRegularFile(normalizedAsset)) {
                Path output = normalizedOutputRoot.resolve(normalizedAsset.getFileName()).normalize();
                if (!output.startsWith(normalizedOutputRoot)) {
                    throw new IOException("Refusing to copy asset outside output directory: " + normalizedAsset);
                }
                Files.createDirectories(output.getParent());
                Files.copy(normalizedAsset, output, StandardCopyOption.REPLACE_EXISTING);
                written.add(output.toAbsolutePath().normalize());
            }
        }
        return Set.copyOf(written);
    }

    private static void copyNativeResourcesFromDirectory(Path classpathRoot, Path outputRoot) throws IOException {
        for (String prefix : NATIVE_RESOURCE_PREFIXES) {
            Path nativeRoot = classpathRoot.resolve(prefix).normalize();
            if (Files.isDirectory(nativeRoot)) {
                copyDirectory(nativeRoot, outputRoot);
            }
        }
    }

    private static Set<Path> copyDirectory(Path sourceRoot, Path outputRoot) throws IOException {
        LinkedHashSet<Path> written = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                Path relative = sourceRoot.relativize(source);
                Path output = outputRoot.resolve(relative).normalize();
                if (!output.startsWith(outputRoot)) {
                    throw new IOException("Refusing to copy native resource outside output directory: " + source);
                }
                Files.createDirectories(output.getParent());
                Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
                written.add(output.toAbsolutePath().normalize());
            }
        }
        return Set.copyOf(written);
    }

    private static void copyNativeResourcesFromJar(Path jar, Path outputRoot) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                if (entry.isDirectory()) {
                    continue;
                }
                String relativePath = relativeNativeResourcePath(entry.getName());
                if (relativePath == null || relativePath.isBlank()) {
                    continue;
                }
                Path output = outputRoot.resolve(relativePath).normalize();
                if (!output.startsWith(outputRoot)) {
                    throw new IOException("Refusing to extract native resource outside output directory: "
                            + entry.getName());
                }
                Files.createDirectories(output.getParent());
                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String relativeNativeResourcePath(String entryName) {
        for (String prefix : NATIVE_RESOURCE_PREFIXES) {
            if (entryName.startsWith(prefix)) {
                return entryName.substring(prefix.length());
            }
        }
        return null;
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path current : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static String cmake(PspProject project) {
        String rootPath = "${CMAKE_CURRENT_SOURCE_DIR}";
        String debugMemory = project.isDebugMemory() ? "add_definitions(-DPSP_DEBUG_MEMORY)" : "";
        return """
                cmake_minimum_required(VERSION 3.11)
                project(@PROJECT_NAME@ C)

                include_directories("@ROOT@/c/external_cpp/psp")
                include_directories("@ROOT@/c/external_cpp/teavm_optimizations/pure")
                include_directories("@ROOT@/c/external_cpp/teavm_optimizations/teavm")
                include_directories("@ROOT@/c/external_cpp/teavm_stats")
                include_directories("@ROOT@/c/external_cpp/stb/include")
                @DEBUG_MEMORY@

                set(SOURCES "@ROOT@/c/src/app_include.c")

                set(TEAVM_FASTMATH_SOURCE "@ROOT@/c/external_cpp/teavm_optimizations/teavm/teavm_fastmath.c")
                set(TEAVM_MATRIX4_SOURCE "@ROOT@/c/external_cpp/teavm_optimizations/teavm/teavm_matrix4.c")
                set(TEAVM_MEMORY_STATS_SOURCE "@ROOT@/c/external_cpp/teavm_stats/teavm_memory_stats.c")
                foreach(optional_source ${TEAVM_FASTMATH_SOURCE} ${TEAVM_MATRIX4_SOURCE} ${TEAVM_MEMORY_STATS_SOURCE})
                  if(EXISTS "${optional_source}")
                    list(APPEND SOURCES "${optional_source}")
                  endif()
                endforeach()

                add_executable(@PROJECT_NAME@ ${SOURCES})

                target_link_libraries(@PROJECT_NAME@ PRIVATE
                    pspgu
                    pspge
                    pspgum
                    pspvram
                    pspdisplay
                    pspctrl
                    pspsdk
                    pspdebug
                )

                create_pbp_file(
                    TARGET @PROJECT_NAME@
                    ICON_PATH NULL
                    BACKGROUND_PATH NULL
                    PREVIEW_PATH NULL
                    TITLE @PROJECT_NAME@
                    VERSION 01.00
                )
                """
                .replace("@PROJECT_NAME@", project.getProjectName())
                .replace("@ROOT@", rootPath)
                .replace("@DEBUG_MEMORY@", debugMemory);
    }

    private static Path writeBuildBat(Path root) throws IOException {
        Path script = root.resolve("build.bat");
        Files.writeString(script, """
                @echo off
                setlocal EnableDelayedExpansion
                cd /d "%~dp0"

                where wsl >nul 2>nul
                if errorlevel 1 (
                    echo ERROR: WSL was not found. Install WSL with a PSPDEV/psp-cmake toolchain, or run build.sh on a POSIX shell with PSPDEV configured.
                    exit /b 1
                )

                for /f "tokens=*" %%i in ('wsl wslpath -u "%CD%"') do set "WSLPATH=%%i"
                if defined PSPDEV (
                    for /f "tokens=*" %%i in ('wsl wslpath -u "%PSPDEV%"') do set "WSL_PSPDEV=%%i"
                    if not defined WSL_PSPDEV (
                        echo ERROR: Could not convert Windows PSPDEV path for WSL: !PSPDEV!
                        exit /b 1
                    )
                    wsl bash -lc "cd '!WSLPATH!' && PSPDEV='!WSL_PSPDEV!' bash build.sh"
                ) else (
                    wsl bash -lc "cd '!WSLPATH!' && bash build.sh"
                )
                if errorlevel 1 exit /b 1
                endlocal
                """, StandardCharsets.UTF_8);
        return script.toAbsolutePath().normalize();
    }

    private static Path writeBuildSh(Path root, String projectName) throws IOException {
        Path script = root.resolve("build.sh");
        Files.writeString(script, """
                #!/usr/bin/env bash
                set -euo pipefail
                cd "$(dirname "$0")"

                if [ -n "${PSPDEV:-}" ]; then
                  export PATH="$PSPDEV/bin:$PATH"
                fi
                if ! command -v psp-cmake >/dev/null 2>&1; then
                  echo "ERROR: psp-cmake was not found. Set PSPDEV or add the PSP toolchain to PATH." >&2
                  exit 1
                fi

                mkdir -p build
                cd build
                psp-cmake -DBUILD_PRX=1 ..
                make

                mkdir -p ../c/release
                cp EBOOT.PBP ../c/release/
                if [ -f "@PROJECT_NAME@.prx" ]; then
                  cp "@PROJECT_NAME@.prx" ../c/release/
                fi
                rm -rf ../c/release/assets
                if [ -d ../assets ]; then
                  cp -R ../assets ../c/release/
                fi
                """
                .replace("@PROJECT_NAME@", projectName), StandardCharsets.UTF_8);
        script.toFile().setExecutable(true, false);
        return script.toAbsolutePath().normalize();
    }

    private static String cString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
