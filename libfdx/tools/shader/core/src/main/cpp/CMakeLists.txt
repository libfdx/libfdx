cmake_minimum_required(VERSION 3.22)
project(libfdx_shaderc LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_SCAN_FOR_MODULES OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT FDX_DAWN_SOURCE_DIR)
  message(FATAL_ERROR "FDX_DAWN_SOURCE_DIR is required")
endif()

if(NOT FDX_SHADERC_OUTPUT_DIR)
  set(FDX_SHADERC_OUTPUT_DIR "${CMAKE_BINARY_DIR}/libfdx_shaderc")
endif()

function(fdx_shaderc_set_output_dirs target)
  set_target_properties(${target} PROPERTIES
      RUNTIME_OUTPUT_DIRECTORY "${FDX_SHADERC_OUTPUT_DIR}"
      LIBRARY_OUTPUT_DIRECTORY "${FDX_SHADERC_OUTPUT_DIR}"
      ARCHIVE_OUTPUT_DIRECTORY "${FDX_SHADERC_OUTPUT_DIR}")
  foreach(config DEBUG RELEASE RELWITHDEBINFO MINSIZEREL)
    set_target_properties(${target} PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY_${config} "${FDX_SHADERC_OUTPUT_DIR}"
        LIBRARY_OUTPUT_DIRECTORY_${config} "${FDX_SHADERC_OUTPUT_DIR}"
        ARCHIVE_OUTPUT_DIRECTORY_${config} "${FDX_SHADERC_OUTPUT_DIR}")
  endforeach()
endfunction()

option(FDX_SHADERC_WEB_DIAGNOSTICS "Enable Emscripten diagnostics for libfdx shaderc web builds" OFF)

set(DAWN_BUILD_SAMPLES OFF CACHE BOOL "" FORCE)
set(DAWN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(DAWN_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)
set(DAWN_BUILD_NODE_BINDINGS OFF CACHE BOOL "" FORCE)
set(DAWN_BUILD_FUZZERS OFF CACHE BOOL "" FORCE)
set(DAWN_BUILD_MONOLITHIC_LIBRARY OFF CACHE STRING "" FORCE)
set(DAWN_BUILD_PROTOBUF OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_RTTI OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_D3D11 OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_D3D12 OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_DESKTOP_GL OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_METAL OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_NULL OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_OPENGLES OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_SPIRV_VALIDATION OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_SWIFTSHADER OFF CACHE BOOL "" FORCE)
set(DAWN_ENABLE_VULKAN OFF CACHE BOOL "" FORCE)
set(DAWN_USE_GLFW OFF CACHE BOOL "" FORCE)
set(DAWN_USE_WAYLAND OFF CACHE BOOL "" FORCE)
set(DAWN_USE_WINDOWS_UI OFF CACHE BOOL "" FORCE)
set(DAWN_USE_X11 OFF CACHE BOOL "" FORCE)
set(DAWN_FETCH_DEPENDENCIES ON CACHE BOOL "" FORCE)
set(DAWN_SUPPORTS_CXX_MODULES OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_CMD_TOOLS OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_FUZZERS OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_GLSL_VALIDATOR OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_GLSL_WRITER ON CACHE BOOL "" FORCE)
set(TINT_BUILD_HLSL_WRITER ON CACHE BOOL "" FORCE)
set(TINT_BUILD_IR_BINARY OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_MSL_WRITER ON CACHE BOOL "" FORCE)
set(TINT_BUILD_NULL_WRITER OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_SPV_READER OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_SPV_WRITER ON CACHE BOOL "" FORCE)
set(TINT_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_TINTD OFF CACHE BOOL "" FORCE)
set(TINT_BUILD_WGSL_READER ON CACHE BOOL "" FORCE)
set(TINT_BUILD_WGSL_WRITER ON CACHE BOOL "" FORCE)
set(TINT_ENABLE_IR_DUMPING OFF CACHE BOOL "" FORCE)
set(TINT_ENABLE_IR_VALIDATION_ASSERTS OFF CACHE BOOL "" FORCE)

add_subdirectory("${FDX_DAWN_SOURCE_DIR}" dawn EXCLUDE_FROM_ALL)

# Dawn skips this target for Emscripten, but Tint's SPIR-V writer still links it.
if(EMSCRIPTEN AND NOT TARGET SPIRV-Headers)
  set(SPIRV_HEADERS_SKIP_EXAMPLES ON CACHE BOOL "" FORCE)
  set(SPIRV_HEADERS_SKIP_INSTALL ON CACHE BOOL "" FORCE)
  add_subdirectory("${FDX_DAWN_SOURCE_DIR}/third_party/spirv-headers/src"
      "${CMAKE_CURRENT_BINARY_DIR}/libfdx_spirv_headers" EXCLUDE_FROM_ALL)
endif()

set(LIBFDX_SHADERC_CORE_SOURCES libfdx_shaderc.cpp)
set(LIBFDX_SHADERC_TINT_LIBS
    libtint
    tint_api_helpers
    tint_lang_core_ir_transform
    tint_lang_glsl_writer_helpers)

if(EMSCRIPTEN)
  add_executable(fdx_shaderc ${LIBFDX_SHADERC_CORE_SOURCES})
  set_target_properties(fdx_shaderc PROPERTIES
      OUTPUT_NAME "libfdx_shaderc"
      SUFFIX ".js")
  fdx_shaderc_set_output_dirs(fdx_shaderc)
  target_link_options(fdx_shaderc PRIVATE
      "SHELL:-s MODULARIZE=1"
      "SHELL:-s EXPORT_NAME=LibFdxShadercModule"
      "SHELL:-s ENVIRONMENT=web,node"
      "SHELL:-s EXPORTED_RUNTIME_METHODS=['cwrap','lengthBytesUTF8','stringToUTF8','UTF8ToString','HEAPU8']"
      "SHELL:-s EXPORTED_FUNCTIONS=['_fdx_shaderc_compile_wgsl_handle','_fdx_shaderc_result_status','_fdx_shaderc_result_output_kind','_fdx_shaderc_result_output','_fdx_shaderc_result_output_size','_fdx_shaderc_result_diagnostics','_fdx_shaderc_result_free','_malloc','_free']"
      "SHELL:-s STACK_SIZE=1048576"
      "SHELL:-s ALLOW_MEMORY_GROWTH=1")
  if(FDX_SHADERC_WEB_DIAGNOSTICS)
    target_link_options(fdx_shaderc PRIVATE
        "SHELL:-s ASSERTIONS=2"
        "SHELL:--profiling-funcs")
  endif()
  target_link_libraries(fdx_shaderc PRIVATE ${LIBFDX_SHADERC_TINT_LIBS})
else()
  if(ANDROID)
    list(APPEND LIBFDX_SHADERC_CORE_SOURCES libfdx_shaderc_jni.cpp)
  endif()
  add_library(fdx_shaderc SHARED ${LIBFDX_SHADERC_CORE_SOURCES})
  fdx_shaderc_set_output_dirs(fdx_shaderc)
  target_link_libraries(fdx_shaderc PRIVATE ${LIBFDX_SHADERC_TINT_LIBS})

  if(NOT ANDROID)
    add_executable(libfdx_shaderc_cli libfdx_shaderc_cli.cpp)
    fdx_shaderc_set_output_dirs(libfdx_shaderc_cli)
    target_link_libraries(libfdx_shaderc_cli PRIVATE fdx_shaderc)
  endif()
endif()
