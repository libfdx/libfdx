cmake_minimum_required(VERSION 3.14)
project(libfdx_sprite_packing LANGUAGES CXX)

find_package(JNI REQUIRED)
add_library(libfdx_sprite_packing SHARED sprite_packing.cpp)
target_compile_features(libfdx_sprite_packing PRIVATE cxx_std_11)
target_include_directories(libfdx_sprite_packing PRIVATE ${JNI_INCLUDE_DIRS})

# The Java reference rounds each multiplication and addition separately.
if(MSVC)
    target_compile_options(libfdx_sprite_packing PRIVATE /fp:strict)
else()
    target_compile_options(libfdx_sprite_packing PRIVATE -ffp-contract=off)
endif()

if(WIN32 AND MSVC AND CMAKE_SIZEOF_VOID_P EQUAL 8)
    # Reuse the GLFW and GLEW libraries shipped with the Windows desktop C backend.
    set(repo "${CMAKE_CURRENT_LIST_DIR}/../../../../../..")
    set(glfw "${repo}/libfdx/backends/c_shared/src/main/resources/libfdx-native/desktop/glfw")
    set(glew "${repo}/libfdx/extensions/graphics/gl/platform/desktop_c/src/main/resources/libfdx-native/desktop/glew-2.3.0")
    set(stb "${repo}/libfdx/framework/fdx/platform/shared/src/main/cpp/common/stb/include")

    add_library(libfdx_sprite_native SHARED EXCLUDE_FROM_ALL native_sprite_benchmark.cpp)
    target_compile_features(libfdx_sprite_native PRIVATE cxx_std_17)
    target_compile_definitions(libfdx_sprite_native PRIVATE GLEW_STATIC)
    target_compile_options(libfdx_sprite_native PRIVATE /fp:strict)
    # The vendored C-only GLEW library names LIBCMT. Resolve its CRT calls with GLFW's DLL CRT.
    target_link_options(libfdx_sprite_native PRIVATE /NODEFAULTLIB:LIBCMT)
    set_property(TARGET libfdx_sprite_native PROPERTY INTERPROCEDURAL_OPTIMIZATION_RELEASE TRUE)
    target_include_directories(libfdx_sprite_native PRIVATE ${JNI_INCLUDE_DIRS} "${glfw}/include" "${glew}/include" "${stb}")
    target_link_libraries(libfdx_sprite_native PRIVATE "${glfw}/lib-vc2022/glfw3.lib"
        "${glew}/lib/Release/x64/glew32s.lib" opengl32)
endif()
