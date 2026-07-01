#ifndef LIBFDX_DESKTOP_VULKAN_H
#define LIBFDX_DESKTOP_VULKAN_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t fdx_desktop_vulkan_probe_instance(void);
int64_t fdx_desktop_vulkan_create(int64_t windowHandle, int32_t width, int32_t height,
        int32_t vSync, int32_t preferMailboxPresentMode, int32_t framesInFlight);
void fdx_desktop_vulkan_resize(int64_t contextHandle, int32_t width, int32_t height);
int32_t fdx_desktop_vulkan_begin_frame(int64_t contextHandle);
void fdx_desktop_vulkan_end_frame(int64_t contextHandle);
void fdx_desktop_vulkan_read_pixels_rgba8(int64_t contextHandle, void* target, int32_t size);
void fdx_desktop_vulkan_clear(int64_t contextHandle, float red, float green, float blue, float alpha);
int64_t fdx_desktop_vulkan_create_buffer(int64_t contextHandle, int32_t size, int32_t usage);
void fdx_desktop_vulkan_write_buffer(int64_t bufferHandle, void* data, int32_t size);
int64_t fdx_desktop_vulkan_create_texture(int64_t contextHandle, int32_t width, int32_t height,
        int32_t format, int32_t wrapS, int32_t wrapT, int32_t filter);
void fdx_desktop_vulkan_write_texture(int64_t textureHandle, void* data, int32_t size);
int64_t fdx_desktop_vulkan_create_shader_module(int64_t contextHandle,
        const int32_t* vertexWords, int32_t vertexWordCount,
        const int32_t* fragmentWords, int32_t fragmentWordCount);
int64_t fdx_desktop_vulkan_create_render_pipeline(int64_t contextHandle, int64_t shaderModuleHandle,
        int32_t primitiveTopology, const int32_t* vertexStridesData, const int32_t* vertexStepModesData,
        int32_t vertexLayoutCount, const int32_t* attributeBindingsData,
        const int32_t* attributeLocationsData, const int32_t* attributeFormatsData,
        const int32_t* attributeOffsetsData, int32_t attributeCount, int32_t sampledTextureCountValue,
        int32_t pbrUniformsEnabled, int32_t depthTestEnabled, int32_t depthWriteEnabled);
void fdx_desktop_vulkan_begin_render_pass(int64_t contextHandle, int32_t clear,
        float red, float green, float blue, float alpha, int32_t store, int32_t depthClear,
        float depthClearValue);
void fdx_desktop_vulkan_set_pipeline(int64_t contextHandle, int64_t pipelineHandle);
void fdx_desktop_vulkan_set_vertex_buffer(int64_t contextHandle, int32_t slot, int64_t bufferHandle);
void fdx_desktop_vulkan_set_index_buffer(int64_t contextHandle, int64_t bufferHandle);
void fdx_desktop_vulkan_set_scissor(int64_t contextHandle, int32_t x, int32_t y,
        int32_t width, int32_t height);
void fdx_desktop_vulkan_bind_textures(int64_t contextHandle, int64_t pipelineHandle,
        const int64_t* textureHandles, int32_t count);
void fdx_desktop_vulkan_bind_uniforms(int64_t contextHandle, int64_t pipelineHandle, void* data, int32_t size);
void fdx_desktop_vulkan_draw(int64_t contextHandle, int32_t vertexCount,
        int32_t instanceCount, int32_t firstVertex, int32_t firstInstance);
void fdx_desktop_vulkan_draw_indexed(int64_t contextHandle, int32_t indexCount,
        int32_t instanceCount, int32_t firstIndex, int32_t baseVertex, int32_t firstInstance);
void fdx_desktop_vulkan_end_render_pass(int64_t contextHandle);
int32_t fdx_desktop_vulkan_surface_format(int64_t contextHandle);
void fdx_desktop_vulkan_destroy_shader_module(int64_t shaderModuleHandle);
void fdx_desktop_vulkan_destroy_render_pipeline(int64_t pipelineHandle);
void fdx_desktop_vulkan_destroy_buffer(int64_t bufferHandle);
void fdx_desktop_vulkan_destroy_texture(int64_t textureHandle);
void fdx_desktop_vulkan_destroy(int64_t contextHandle);

#ifdef __cplusplus
}
#endif

#endif
