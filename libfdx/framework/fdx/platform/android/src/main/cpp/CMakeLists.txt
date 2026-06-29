cmake_minimum_required(VERSION 3.14)
project(fdx_android LANGUAGES C CXX)

if(NOT DEFINED LIBFDX_RUNTIME_FDX_NATIVE_DIR)
    message(FATAL_ERROR "LIBFDX_RUNTIME_FDX_NATIVE_DIR is required")
endif()
if(NOT DEFINED LIBFDX_NATIVE_DEPS_DIR)
    message(FATAL_ERROR "LIBFDX_NATIVE_DEPS_DIR is required")
endif()
file(TO_CMAKE_PATH "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}" LIBFDX_RUNTIME_FDX_NATIVE_DIR)
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
set(LIBFDX_ANDROID_PAGE_SIZE_LINK_OPTIONS
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")
string(JOIN " " LIBFDX_ANDROID_PAGE_SIZE_LINK_FLAGS ${LIBFDX_ANDROID_PAGE_SIZE_LINK_OPTIONS})
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${LIBFDX_ANDROID_PAGE_SIZE_LINK_FLAGS}")
set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} ${LIBFDX_ANDROID_PAGE_SIZE_LINK_FLAGS}")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${LIBFDX_ANDROID_PAGE_SIZE_LINK_FLAGS}")

set(LIBFDX_RUNTIME_FDX_ANDROID_SOURCES
    libfdx_android_freetype_jni.cpp
    libfdx_android_math_jni.cpp
    "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}/libfdx_freetype.cpp"
    "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}/libfdx_math.cpp")

if(LIBFDX_ENABLE_SHADER_COMPILER)
    if(NOT DEFINED LIBFDX_SHADERC_SOURCE_DIR)
        message(FATAL_ERROR "LIBFDX_SHADERC_SOURCE_DIR is required when LIBFDX_ENABLE_SHADER_COMPILER=ON")
    endif()
    file(TO_CMAKE_PATH "${LIBFDX_SHADERC_SOURCE_DIR}" LIBFDX_SHADERC_SOURCE_DIR)
    list(APPEND LIBFDX_RUNTIME_FDX_ANDROID_SOURCES
        "${LIBFDX_SHADERC_SOURCE_DIR}/libfdx_shaderc.cpp"
        "${LIBFDX_SHADERC_SOURCE_DIR}/libfdx_shaderc_jni.cpp")
endif()

add_library(fdx SHARED ${LIBFDX_RUNTIME_FDX_ANDROID_SOURCES})

target_include_directories(fdx PRIVATE
    "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}")

if(LIBFDX_ENABLE_SHADER_COMPILER)
    target_include_directories(fdx PRIVATE "${LIBFDX_SHADERC_SOURCE_DIR}")
endif()

target_link_options(fdx PRIVATE ${LIBFDX_ANDROID_PAGE_SIZE_LINK_OPTIONS})
target_link_libraries(fdx PRIVATE fdx_natives::freetype)

if(LIBFDX_ENABLE_SHADER_COMPILER)
    target_link_libraries(fdx PRIVATE
        fdx_natives::tint
        fdx_natives::tint_api_helpers
        fdx_natives::tint_lang_core_ir_transform
        fdx_natives::tint_lang_glsl_writer_helpers)
endif()
