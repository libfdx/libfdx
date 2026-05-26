cmake_minimum_required(VERSION 3.14)
project(libfdx_runtime_core_android LANGUAGES C CXX)

if(NOT DEFINED LIBFDX_FREETYPE_SOURCE_DIR)
    message(FATAL_ERROR "LIBFDX_FREETYPE_SOURCE_DIR is required")
endif()
if(NOT DEFINED LIBFDX_RUNTIME_CORE_NATIVE_DIR)
    message(FATAL_ERROR "LIBFDX_RUNTIME_CORE_NATIVE_DIR is required")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(FT_DISABLE_ZLIB ON CACHE BOOL "" FORCE)
set(FT_DISABLE_BZIP2 ON CACHE BOOL "" FORCE)
set(FT_DISABLE_PNG ON CACHE BOOL "" FORCE)
set(FT_DISABLE_HARFBUZZ ON CACHE BOOL "" FORCE)
set(FT_DISABLE_BROTLI ON CACHE BOOL "" FORCE)

add_subdirectory("${LIBFDX_FREETYPE_SOURCE_DIR}" freetype-build)

add_library(fdx SHARED
    libfdx_android_freetype_jni.cpp
    libfdx_android_math_jni.cpp
    "${LIBFDX_RUNTIME_CORE_NATIVE_DIR}/libfdx_freetype.cpp"
    "${LIBFDX_RUNTIME_CORE_NATIVE_DIR}/libfdx_math.cpp")

target_include_directories(fdx PRIVATE
    "${LIBFDX_RUNTIME_CORE_NATIVE_DIR}")

target_link_options(fdx PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")

target_link_libraries(fdx PRIVATE freetype)
