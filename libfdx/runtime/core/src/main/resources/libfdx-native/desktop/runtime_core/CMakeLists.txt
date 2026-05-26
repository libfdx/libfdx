cmake_minimum_required(VERSION 3.14)
project(libfdx_runtime_core_desktop LANGUAGES C CXX)

if(NOT DEFINED LIBFDX_DESKTOP_OUTPUT_DIR)
    message(FATAL_ERROR "LIBFDX_DESKTOP_OUTPUT_DIR is required")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LIBFDX_JNI_INCLUDE_DIRS "")
if(DEFINED LIBFDX_JAVA_HOME)
    file(TO_CMAKE_PATH "${LIBFDX_JAVA_HOME}" LIBFDX_JAVA_HOME_NORMALIZED)
    if(EXISTS "${LIBFDX_JAVA_HOME_NORMALIZED}/include/jni.h")
        list(APPEND LIBFDX_JNI_INCLUDE_DIRS "${LIBFDX_JAVA_HOME_NORMALIZED}/include")
        if(WIN32)
            list(APPEND LIBFDX_JNI_INCLUDE_DIRS "${LIBFDX_JAVA_HOME_NORMALIZED}/include/win32")
        elseif(APPLE)
            list(APPEND LIBFDX_JNI_INCLUDE_DIRS "${LIBFDX_JAVA_HOME_NORMALIZED}/include/darwin")
        else()
            list(APPEND LIBFDX_JNI_INCLUDE_DIRS "${LIBFDX_JAVA_HOME_NORMALIZED}/include/linux")
        endif()
    endif()
endif()
if(LIBFDX_JNI_INCLUDE_DIRS)
    message(STATUS "Using JNI headers from ${LIBFDX_JAVA_HOME_NORMALIZED}")
else()
    find_package(JNI REQUIRED)
    list(APPEND LIBFDX_JNI_INCLUDE_DIRS ${JNI_INCLUDE_DIRS})
endif()

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${LIBFDX_DESKTOP_OUTPUT_DIR}")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${LIBFDX_DESKTOP_OUTPUT_DIR}")
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${LIBFDX_DESKTOP_OUTPUT_DIR}")
foreach(CONFIG DEBUG RELEASE RELWITHDEBINFO MINSIZEREL)
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_${CONFIG} "${LIBFDX_DESKTOP_OUTPUT_DIR}")
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_${CONFIG} "${LIBFDX_DESKTOP_OUTPUT_DIR}")
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY_${CONFIG} "${LIBFDX_DESKTOP_OUTPUT_DIR}")
endforeach()

set(LIBFDX_RUNTIME_CORE_SOURCES
    libfdx_math.cpp
    libfdx_math_jni.cpp)

string(TOLOWER "${CMAKE_SYSTEM_PROCESSOR}" LIBFDX_SYSTEM_PROCESSOR)
if(LIBFDX_SYSTEM_PROCESSOR MATCHES "^(x86_64|amd64|x64|i[3-6]86|x86)$")
    list(APPEND LIBFDX_RUNTIME_CORE_SOURCES libfdx_math_avx2_fma.cpp)
endif()

add_library(fdx SHARED ${LIBFDX_RUNTIME_CORE_SOURCES})

if(LIBFDX_SYSTEM_PROCESSOR MATCHES "^(x86_64|amd64|x64|i[3-6]86|x86)$")
    target_compile_definitions(fdx PRIVATE LIBFDX_MATH_HAS_AVX2_FMA=1)
    if(MSVC)
        set_source_files_properties(libfdx_math_avx2_fma.cpp PROPERTIES COMPILE_OPTIONS "/arch:AVX2")
    else()
        set_source_files_properties(libfdx_math_avx2_fma.cpp PROPERTIES COMPILE_OPTIONS "-mavx2;-mfma")
    endif()
endif()

target_include_directories(fdx PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    ${LIBFDX_JNI_INCLUDE_DIRS})

if(MSVC)
    target_compile_options(fdx PRIVATE /O2)
else()
    target_compile_options(fdx PRIVATE -O3)
endif()
