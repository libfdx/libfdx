cmake_minimum_required(VERSION 3.14)
project(libfdx_runtime_fdx_web LANGUAGES C CXX)

if(NOT DEFINED LIBFDX_FREETYPE_SOURCE_DIR)
    message(FATAL_ERROR "LIBFDX_FREETYPE_SOURCE_DIR is required")
endif()
if(NOT DEFINED LIBFDX_RUNTIME_FDX_NATIVE_DIR)
    message(FATAL_ERROR "LIBFDX_RUNTIME_FDX_NATIVE_DIR is required")
endif()
if(NOT DEFINED LIBFDX_WEB_OUTPUT_DIR)
    message(FATAL_ERROR "LIBFDX_WEB_OUTPUT_DIR is required")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${LIBFDX_WEB_OUTPUT_DIR}")

set(FT_DISABLE_ZLIB ON CACHE BOOL "" FORCE)
set(FT_DISABLE_BZIP2 ON CACHE BOOL "" FORCE)
set(FT_DISABLE_PNG ON CACHE BOOL "" FORCE)
set(FT_DISABLE_HARFBUZZ ON CACHE BOOL "" FORCE)
set(FT_DISABLE_BROTLI ON CACHE BOOL "" FORCE)

add_subdirectory("${LIBFDX_FREETYPE_SOURCE_DIR}" freetype-build)

add_executable(fdx
    "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}/libfdx_freetype.cpp")

target_include_directories(fdx PRIVATE
    "${LIBFDX_RUNTIME_FDX_NATIVE_DIR}")

target_link_libraries(fdx PRIVATE freetype)

target_link_options(fdx PRIVATE
    "-sMODULARIZE=1"
    "-sEXPORT_NAME=FdxModule"
    "-sEXPORTED_FUNCTIONS=['_malloc','_free','_fdx_freetype_rasterize']"
    "-sEXPORTED_RUNTIME_METHODS=['HEAP8','HEAPU8','HEAP32','HEAPF32']"
    "-sALLOW_MEMORY_GROWTH=1"
    "-sENVIRONMENT=web"
    "--no-entry")
