cmake_minimum_required(VERSION 3.22.1)

project(fdx_android_vulkan)

add_library(fdx_android_vulkan SHARED
        libfdx_android_vulkan.cpp)

option(LIBFDX_VULKAN_LOSS_TESTS "Enable test-only Vulkan failure injection" OFF)
if(LIBFDX_VULKAN_LOSS_TESTS)
    if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
        message(FATAL_ERROR "Vulkan failure injection is only supported in debug test builds")
    endif()
    target_compile_definitions(fdx_android_vulkan PRIVATE LIBFDX_VULKAN_LOSS_TESTS)
endif()

find_library(android-lib android)
find_library(log-lib log)
find_library(vulkan-lib vulkan)

target_link_libraries(fdx_android_vulkan
        ${android-lib}
        ${log-lib}
        ${vulkan-lib})

target_link_options(fdx_android_vulkan PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")
