cmake_minimum_required(VERSION 3.14)
project(libfdx_runtime_fdx_web LANGUAGES C CXX)

if(NOT DEFINED LIBFDX_RUNTIME_FDX_NATIVE_DIR)
    message(FATAL_ERROR "LIBFDX_RUNTIME_FDX_NATIVE_DIR is required")
endif()
if(NOT DEFINED LIBFDX_WEB_OUTPUT_DIR)
    message(FATAL_ERROR "LIBFDX_WEB_OUTPUT_DIR is required")
endif()
if(NOT DEFINED LIBFDX_NATIVE_DEPS_DIR)
    message(FATAL_ERROR "LIBFDX_NATIVE_DEPS_DIR is required")
endif()
file(TO_CMAKE_PATH "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}" LIBFDX_RUNTIME_FDX_NATIVE_DIR)
file(TO_CMAKE_PATH "${LIBFDX_WEB_OUTPUT_DIR}" LIBFDX_WEB_OUTPUT_DIR)
file(TO_CMAKE_PATH "${LIBFDX_NATIVE_DEPS_DIR}" LIBFDX_NATIVE_DEPS_DIR)
include("${LIBFDX_NATIVE_DEPS_DIR}/cmake/fdx-natives-targets.cmake")

option(LIBFDX_ENABLE_SHADER_COMPILER "Enable Tint-backed WGSL runtime shader compilation" OFF)

if(LIBFDX_ENABLE_SHADER_COMPILER)
    set(CMAKE_CXX_STANDARD 20)
else()
    set(CMAKE_CXX_STANDARD 17)
endif()
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_SCAN_FOR_MODULES OFF)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${LIBFDX_WEB_OUTPUT_DIR}")

if(LIBFDX_ENABLE_SHADER_COMPILER AND EMSCRIPTEN)
    # Tint's Emscripten path is built conservatively; optimized Wasm has been unstable.
    set(CMAKE_C_FLAGS_RELEASE "-O0 -DNDEBUG" CACHE STRING "" FORCE)
    set(CMAKE_CXX_FLAGS_RELEASE "-O0 -DNDEBUG" CACHE STRING "" FORCE)
endif()

set(LIBFDX_RUNTIME_FDX_WEB_SOURCES
    "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}/libfdx_freetype.cpp")

if(EMSCRIPTEN)
    list(APPEND LIBFDX_RUNTIME_FDX_WEB_SOURCES
        libfdx_emscripten_libcpp_compat.cpp)
endif()

set(LIBFDX_WEB_EXPORTED_FUNCTIONS "'_malloc','_free','_fdx_freetype_rasterize'")
set(LIBFDX_WEB_EXPORTED_RUNTIME_METHODS "'HEAP8','HEAPU8','HEAP32','HEAPF32'")

if(LIBFDX_ENABLE_SHADER_COMPILER)
    if(NOT DEFINED LIBFDX_SHADERC_SOURCE_DIR)
        message(FATAL_ERROR "LIBFDX_SHADERC_SOURCE_DIR is required when LIBFDX_ENABLE_SHADER_COMPILER=ON")
    endif()
    file(TO_CMAKE_PATH "${LIBFDX_SHADERC_SOURCE_DIR}" LIBFDX_SHADERC_SOURCE_DIR)
    list(APPEND LIBFDX_RUNTIME_FDX_WEB_SOURCES
        "${LIBFDX_SHADERC_SOURCE_DIR}/libfdx_shaderc.cpp")

    set(LIBFDX_WEB_EXPORTED_FUNCTIONS
        "${LIBFDX_WEB_EXPORTED_FUNCTIONS},'_fdx_shaderc_compile_wgsl_handle','_fdx_shaderc_result_status','_fdx_shaderc_result_output_kind','_fdx_shaderc_result_output','_fdx_shaderc_result_output_size','_fdx_shaderc_result_diagnostics','_fdx_shaderc_result_reflection','_fdx_shaderc_result_reflection_size','_fdx_shaderc_result_target_interface','_fdx_shaderc_result_target_interface_size','_fdx_shaderc_result_free'")
    set(LIBFDX_WEB_EXPORTED_RUNTIME_METHODS
        "${LIBFDX_WEB_EXPORTED_RUNTIME_METHODS},'cwrap','lengthBytesUTF8','stringToUTF8','UTF8ToString'")
endif()

add_executable(fdx ${LIBFDX_RUNTIME_FDX_WEB_SOURCES})

target_include_directories(fdx PRIVATE
    "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}")

target_link_libraries(fdx PRIVATE fdx_natives::freetype)

if(LIBFDX_ENABLE_SHADER_COMPILER)
    target_include_directories(fdx PRIVATE "${LIBFDX_SHADERC_SOURCE_DIR}")
    target_link_libraries(fdx PRIVATE
        fdx_natives::tint
        fdx_natives::tint_api_helpers
        fdx_natives::tint_lang_core_ir_transform
        fdx_natives::tint_lang_glsl_writer_helpers)
    target_link_options(fdx PRIVATE
        "-sSTACK_SIZE=1048576")
endif()

target_link_options(fdx PRIVATE
    "-sMODULARIZE=1"
    "-sEXPORT_NAME=FdxModule"
    "-sEXPORTED_FUNCTIONS=[${LIBFDX_WEB_EXPORTED_FUNCTIONS}]"
    "-sEXPORTED_RUNTIME_METHODS=[${LIBFDX_WEB_EXPORTED_RUNTIME_METHODS}]"
    "-sALLOW_MEMORY_GROWTH=1"
    "-sENVIRONMENT=web"
    "--no-entry")
