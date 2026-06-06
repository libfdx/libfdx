package io.github.libfdx.backend.desktopnative;

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

public final class NativeProjectWriter {
    private static final List<String> NATIVE_RESOURCE_PREFIXES = List.of(
            "libfdx-native/shared/",
            "libfdx-native/desktop/");

    private NativeProjectWriter() {
    }

    public static Set<Path> write(NativeProject project) throws IOException {
        Objects.requireNonNull(project, "project");
        LinkedHashSet<Path> written = new LinkedHashSet<>();
        Path root = project.getBuildRoot();
        Path sources = project.getGeneratedSourcesDirectory();
        Path release = project.getReleaseDirectory();
        Files.createDirectories(root);
        Files.createDirectories(sources);
        Files.createDirectories(release);
        copyNativeResources(project.getNativeResourceClasspath(), root.resolve("c/external_cpp"));
        Path include = sources.resolve("app_include.c");
        Files.writeString(include, appInclude(), StandardCharsets.UTF_8);
        written.add(include.toAbsolutePath().normalize());
        Path cmake = root.resolve("CMakeLists.txt");
        Files.writeString(cmake, cmake(project), StandardCharsets.UTF_8);
        written.add(cmake.toAbsolutePath().normalize());
        written.add(writeBuildScript(project, "app_debug", "Debug"));
        written.add(writeBuildScript(project, "app_release", "Release"));
        return Set.copyOf(written);
    }

    private static String appInclude() {
        return """
                #if defined(__has_include)
                #  if __has_include(<GL/glew.h>)
                #    include <GL/glew.h>
                #  endif
                #  if __has_include("teavm_optimizations.h")
                #    include "teavm_optimizations.h"
                #  endif
                #else
                #  include <GL/glew.h>
                #endif

                #include "all.c"
                """;
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

    private static void copyNativeResourcesFromDirectory(Path classpathRoot, Path outputRoot) throws IOException {
        for (String prefix : NATIVE_RESOURCE_PREFIXES) {
            Path nativeRoot = classpathRoot.resolve(prefix).normalize();
            if (Files.isDirectory(nativeRoot)) {
                copyDirectory(nativeRoot, outputRoot);
            }
        }
    }

    private static void copyDirectory(Path sourceRoot, Path outputRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                Path relative = sourceRoot.relativize(source);
                Path output = outputRoot.resolve(relative).normalize();
                if (!output.startsWith(outputRoot)) {
                    throw new IOException("Refusing to copy native resource outside output directory: " + source);
                }
                Files.createDirectories(output.getParent());
                Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
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

    private static String cmake(NativeProject project) {
        String releasePath = slash(project.getReleaseDirectory());
        String rootPath = slash(project.getBuildRoot());
        String projectName = project.getProjectName();
        String showConsole = project.isShowConsole() ? "ON" : "OFF";
        return """
                cmake_minimum_required(VERSION 3.14)
                project(%1$s C)
                set(CMAKE_C_STANDARD 11)
                set(LIBFDX_DESKTOP_NATIVE_SHOW_CONSOLE %2$s CACHE BOOL "Build desktop_native Windows executables with a console subsystem")

                if(CMAKE_CONFIGURATION_TYPES)
                  foreach(config ${CMAKE_CONFIGURATION_TYPES})
                    string(TOUPPER ${config} config_upper)
                    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_${config_upper} "%3$s")
                  endforeach()
                else()
                  set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "%3$s")
                endif()

                if(NOT CMAKE_BUILD_TYPE)
                  set(CMAKE_BUILD_TYPE %4$s)
                endif()

                if(WIN32)
                  if(LIBFDX_DESKTOP_NATIVE_SHOW_CONSOLE)
                    message(STATUS "desktop_native console: enabled")
                  else()
                    message(STATUS "desktop_native console: disabled")
                  endif()
                endif()

                if(UNIX)
                  find_package(glfw3 CONFIG REQUIRED)
                  find_package(OpenGL QUIET)
                  find_package(GLEW QUIET)
                endif()

                if(WIN32)
                  include_directories("%5$s/c/external_cpp/glfw/include")
                  link_directories("%5$s/c/external_cpp/glfw/lib-vc2022")
                  set(LIBFDX_HAS_GLEW_RESOURCES OFF)
                  if(EXISTS "%5$s/c/external_cpp/glew-2.3.0/include/GL/glew.h")
                    add_definitions(-DGLEW_STATIC)
                    include_directories("%5$s/c/external_cpp/glew-2.3.0/include")
                    link_directories("%5$s/c/external_cpp/glew-2.3.0/lib/Release/x64")
                    set(LIBFDX_HAS_GLEW_RESOURCES ON)
                  endif()
                endif()

                include_directories("%5$s/c/external_cpp/native_optimizations")
                include_directories("%5$s/c/external_cpp/teavm_optimizations/teavm")
                include_directories("%5$s/c/external_cpp/teavm_stats")
                include_directories("%5$s/c/external_cpp/desktop_native")
                include_directories("%5$s/c/external_cpp/desktop_vulkan")
                include_directories("%5$s/c/external_cpp/runtime_fdx")

                set(SOURCES "%5$s/c/src/app_include.c")
                set(TEAVM_FASTMATH_SOURCE "%5$s/c/external_cpp/teavm_optimizations/teavm/teavm_fastmath.c")
                set(TEAVM_MATRIX4_SOURCE "%5$s/c/external_cpp/teavm_optimizations/teavm/teavm_matrix4.c")
                set(TEAVM_MEMORY_STATS_SOURCE "%5$s/c/external_cpp/teavm_stats/teavm_memory_stats.c")
                foreach(optional_source ${TEAVM_FASTMATH_SOURCE} ${TEAVM_MATRIX4_SOURCE} ${TEAVM_MEMORY_STATS_SOURCE})
                  if(EXISTS "${optional_source}")
                    list(APPEND SOURCES "${optional_source}")
                  endif()
                endforeach()
                set(TEAVM_SPRITEBATCH_SOURCE "%5$s/c/external_cpp/teavm_optimizations/teavm/teavm_spritebatch.c")
                set(GDX_SPRITEBATCH_HEADER "%5$s/c/src/classes/com/badlogic/gdx/graphics/g2d/SpriteBatch.h")
                if(EXISTS "${TEAVM_SPRITEBATCH_SOURCE}" AND EXISTS "${GDX_SPRITEBATCH_HEADER}")
                  list(APPEND SOURCES "${TEAVM_SPRITEBATCH_SOURCE}")
                endif()
                set(LIBFDX_NATIVE_IMAGE_SOURCE "%5$s/c/external_cpp/desktop_native/libfdx_native_image.cpp")
                set(LIBFDX_DESKTOP_VULKAN_SOURCE "%5$s/c/external_cpp/desktop_vulkan/libfdx_desktop_vulkan.cpp")
                set(LIBFDX_RUNTIME_FDX_FREETYPE_SOURCE "%5$s/c/external_cpp/runtime_fdx/libfdx_freetype.cpp")
                set(LIBFDX_HAS_CXX_SOURCES OFF)
                if(EXISTS "${LIBFDX_NATIVE_IMAGE_SOURCE}")
                  set(LIBFDX_HAS_CXX_SOURCES ON)
                endif()
                if(EXISTS "${LIBFDX_DESKTOP_VULKAN_SOURCE}")
                  set(LIBFDX_HAS_CXX_SOURCES ON)
                endif()
                if(EXISTS "${LIBFDX_RUNTIME_FDX_FREETYPE_SOURCE}")
                  set(LIBFDX_HAS_CXX_SOURCES ON)
                endif()
                if(LIBFDX_HAS_CXX_SOURCES)
                  enable_language(CXX)
                  set(CMAKE_CXX_STANDARD 17)
                  set(CMAKE_CXX_STANDARD_REQUIRED ON)
                endif()
                if(EXISTS "${LIBFDX_NATIVE_IMAGE_SOURCE}")
                  list(APPEND SOURCES "${LIBFDX_NATIVE_IMAGE_SOURCE}")
                endif()
                set(LIBFDX_HAS_DESKTOP_VULKAN OFF)
                set(LIBFDX_DESKTOP_VULKAN_USES_SDK OFF)
                if(EXISTS "${LIBFDX_DESKTOP_VULKAN_SOURCE}")
                  set(LIBFDX_HAS_DESKTOP_VULKAN ON)
                  list(APPEND SOURCES "${LIBFDX_DESKTOP_VULKAN_SOURCE}")
                  find_package(Vulkan QUIET)
                  if(TARGET Vulkan::Vulkan)
                    set(LIBFDX_DESKTOP_VULKAN_USES_SDK ON)
                    message(STATUS "desktop_native Vulkan: using Vulkan SDK from CMake Vulkan::Vulkan")
                  else()
                    message(STATUS "desktop_native Vulkan: Vulkan SDK not found; using local ABI shim and runtime loader")
                  endif()
                endif()
                set(LIBFDX_HAS_RUNTIME_FDX_FREETYPE OFF)
                if(EXISTS "${LIBFDX_RUNTIME_FDX_FREETYPE_SOURCE}")
                  set(LIBFDX_HAS_RUNTIME_FDX_FREETYPE ON)
                  include(FetchContent)
                  set(FT_DISABLE_ZLIB ON CACHE BOOL "" FORCE)
                  set(FT_DISABLE_BZIP2 ON CACHE BOOL "" FORCE)
                  set(FT_DISABLE_PNG ON CACHE BOOL "" FORCE)
                  set(FT_DISABLE_HARFBUZZ ON CACHE BOOL "" FORCE)
                  set(FT_DISABLE_BROTLI ON CACHE BOOL "" FORCE)
                  FetchContent_Declare(libfdx_freetype
                    URL https://download.savannah.gnu.org/releases/freetype/freetype-2.14.3.tar.xz
                    DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
                  FetchContent_MakeAvailable(libfdx_freetype)
                  list(APPEND SOURCES "${LIBFDX_RUNTIME_FDX_FREETYPE_SOURCE}")
                endif()

                add_executable(%1$s ${SOURCES})

                if(WIN32 AND NOT LIBFDX_DESKTOP_NATIVE_SHOW_CONSOLE)
                  if(MSVC)
                    set_property(TARGET %1$s APPEND_STRING PROPERTY
                      LINK_FLAGS " /SUBSYSTEM:WINDOWS /ENTRY:mainCRTStartup")
                  elseif(MINGW)
                    set_property(TARGET %1$s APPEND_STRING PROPERTY
                      LINK_FLAGS " -mwindows")
                  endif()
                endif()

                if(MSVC)
                  target_compile_options(%1$s PRIVATE
                    $<$<COMPILE_LANGUAGE:CXX>:/EHsc>
                    $<$<CONFIG:Debug>:/Od /Zi>
                    $<$<CONFIG:Release>:/O2 /Ob2 /Oi /Ot>)
                elseif(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang")
                  target_compile_options(%1$s PRIVATE
                    $<$<CONFIG:Debug>:-g>
                    $<$<CONFIG:Release>:-O3>)
                endif()

                set_target_properties(%1$s PROPERTIES
                  OUTPUT_NAME "%1$s_$<IF:$<CONFIG:Debug>,debug,release>")

                if(LIBFDX_HAS_DESKTOP_VULKAN AND LIBFDX_DESKTOP_VULKAN_USES_SDK)
                  target_compile_definitions(%1$s PRIVATE LIBFDX_USE_SYSTEM_VULKAN_SDK=1)
                  target_link_libraries(%1$s PRIVATE Vulkan::Vulkan)
                endif()
                if(LIBFDX_HAS_RUNTIME_FDX_FREETYPE)
                  target_link_libraries(%1$s PRIVATE freetype)
                endif()

                if(WIN32)
                  set_target_properties(%1$s PROPERTIES
                    VS_DEBUGGER_WORKING_DIRECTORY "%3$s")
                  target_link_libraries(%1$s PRIVATE glfw3)
                  if(LIBFDX_HAS_GLEW_RESOURCES)
                    target_link_libraries(%1$s PRIVATE opengl32 glew32s)
                  endif()
                  if(EXISTS "${LIBFDX_NATIVE_IMAGE_SOURCE}")
                    target_link_libraries(%1$s PRIVATE windowscodecs ole32)
                  endif()
                  set_property(DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR} PROPERTY VS_STARTUP_PROJECT %1$s)
                endif()
                if(UNIX)
                  target_link_libraries(%1$s PRIVATE glfw m)
                  if(TARGET OpenGL::GL)
                    target_link_libraries(%1$s PRIVATE OpenGL::GL)
                  endif()
                  if(TARGET GLEW::GLEW)
                    target_link_libraries(%1$s PRIVATE GLEW::GLEW)
                  endif()
                  if(LIBFDX_HAS_DESKTOP_VULKAN AND NOT LIBFDX_DESKTOP_VULKAN_USES_SDK AND CMAKE_DL_LIBS)
                    target_link_libraries(%1$s PRIVATE ${CMAKE_DL_LIBS})
                  endif()
                endif()
                """.formatted(projectName, showConsole, releasePath, project.getBuildType(), rootPath);
    }

    private static Path writeBuildScript(NativeProject project, String baseName, String config) throws IOException {
        String showConsoleCmake = project.isShowConsole() ? "ON" : "OFF";
        Path root = project.getBuildRoot();
        if (isWindows()) {
            Path script = root.resolve(baseName + ".bat");
            Files.writeString(script, """
                    @echo off
                    setlocal
                    cd /d "%%~dp0"
                    cmake -S . -B build\\cmake -DLIBFDX_DESKTOP_NATIVE_SHOW_CONSOLE=%s
                    if errorlevel 1 exit /b 1
                    cmake --build build\\cmake --config %s
                    if errorlevel 1 exit /b 1
                    endlocal
                    """.formatted(showConsoleCmake, config), StandardCharsets.UTF_8);
            return script.toAbsolutePath().normalize();
        }
        Path script = root.resolve(baseName + ".sh");
        Files.writeString(script, """
                #!/usr/bin/env bash
                set -euo pipefail
                cd "$(dirname "$0")"
                cmake -S . -B build/cmake -DCMAKE_BUILD_TYPE="%s" -DLIBFDX_DESKTOP_NATIVE_SHOW_CONSOLE="%s"
                cmake --build build/cmake --config "%s"
                """.formatted(config, showConsoleCmake, config), StandardCharsets.UTF_8);
        script.toFile().setExecutable(true, false);
        return script.toAbsolutePath().normalize();
    }

    private static String slash(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows");
    }
}
