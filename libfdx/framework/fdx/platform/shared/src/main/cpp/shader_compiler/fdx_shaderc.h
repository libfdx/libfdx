#ifndef LIBFDX_RUNTIME_SHADER_COMPILER_FDX_SHADERC_H_
#define LIBFDX_RUNTIME_SHADER_COMPILER_FDX_SHADERC_H_

#include <stdint.h>

#ifdef _WIN32
#define FDX_SHADERC_API extern "C" __declspec(dllexport)
#else
#define FDX_SHADERC_API extern "C" __attribute__((visibility("default")))
#endif

enum fdx_shaderc_target {
    FDX_SHADERC_TARGET_WEBGPU_WGSL = 0,
    FDX_SHADERC_TARGET_WGPU_WGSL = 1,
    FDX_SHADERC_TARGET_WEBGL_GLSL_ES = 2,
    FDX_SHADERC_TARGET_GLES_GLSL_ES = 3,
    FDX_SHADERC_TARGET_OPENGL_GLSL = 4,
    FDX_SHADERC_TARGET_VULKAN_SPIRV = 5,
    FDX_SHADERC_TARGET_METAL_MSL = 6,
    FDX_SHADERC_TARGET_DIRECTX_HLSL = 7
};

enum fdx_shaderc_stage {
    FDX_SHADERC_STAGE_MODULE = 0,
    FDX_SHADERC_STAGE_VERTEX = 1,
    FDX_SHADERC_STAGE_FRAGMENT = 2,
    FDX_SHADERC_STAGE_COMPUTE = 3
};

enum fdx_shaderc_output_kind {
    FDX_SHADERC_OUTPUT_NONE = 0,
    FDX_SHADERC_OUTPUT_TEXT = 1,
    FDX_SHADERC_OUTPUT_SPIRV = 2
};

struct fdx_shaderc_options {
    int32_t target;
    int32_t stage;
    const char* entry_point;
    const char* glsl_profile;
    const char* glsl_es_profile;
};

struct fdx_shaderc_result {
    int32_t status;
    int32_t output_kind;
    uint8_t* output;
    int32_t output_size;
    char* diagnostics;
};

FDX_SHADERC_API int32_t fdx_shaderc_compile_wgsl(const char* source,
                                                 int32_t source_size,
                                                 const fdx_shaderc_options* options,
                                                 fdx_shaderc_result* result);

FDX_SHADERC_API void fdx_shaderc_free_result(fdx_shaderc_result* result);

FDX_SHADERC_API void* fdx_shaderc_compile_wgsl_handle(const char* source,
                                                       int32_t source_size,
                                                       int32_t target,
                                                       int32_t stage,
                                                       const char* entry_point,
                                                       const char* glsl_profile,
                                                       const char* glsl_es_profile);

FDX_SHADERC_API int32_t fdx_shaderc_result_status(void* handle);

FDX_SHADERC_API int32_t fdx_shaderc_result_output_kind(void* handle);

FDX_SHADERC_API const uint8_t* fdx_shaderc_result_output(void* handle);

FDX_SHADERC_API int32_t fdx_shaderc_result_output_size(void* handle);

FDX_SHADERC_API const char* fdx_shaderc_result_diagnostics(void* handle);

FDX_SHADERC_API const uint8_t* fdx_shaderc_result_reflection(void* handle);

FDX_SHADERC_API int32_t fdx_shaderc_result_reflection_size(void* handle);

FDX_SHADERC_API const uint8_t* fdx_shaderc_result_target_interface(void* handle);

FDX_SHADERC_API int32_t fdx_shaderc_result_target_interface_size(void* handle);

FDX_SHADERC_API void fdx_shaderc_result_free(void* handle);

#endif  // LIBFDX_RUNTIME_SHADER_COMPILER_FDX_SHADERC_H_
