#ifndef LIBFDX_IOS_METAL_H
#define LIBFDX_IOS_METAL_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void libfdx_ios_metal_set_view(void* view);
int32_t libfdx_ios_metal_supported(void);
int64_t libfdx_ios_metal_create(int32_t width, int32_t height);
void libfdx_ios_metal_resize(int64_t context, int32_t width, int32_t height);
int32_t libfdx_ios_metal_begin_frame(int64_t context);
void libfdx_ios_metal_end_frame(int64_t context);
void libfdx_ios_metal_read_pixels_rgba8(int64_t context, void* target, int32_t byte_count);
void libfdx_ios_metal_clear(int64_t context, float red, float green, float blue, float alpha);
int64_t libfdx_ios_metal_create_buffer(int64_t context, int32_t size, int32_t usage);
void libfdx_ios_metal_write_buffer(int64_t buffer, const void* data, int32_t byte_count);
int64_t libfdx_ios_metal_create_texture(
        int64_t context, int32_t width, int32_t height, int32_t wrap_s, int32_t wrap_t, int32_t filter);
void libfdx_ios_metal_write_texture(int64_t texture, const void* data, int32_t byte_count);
int64_t libfdx_ios_metal_create_shader_module(
        int64_t context, int32_t source_length, const int32_t* source_data);
int64_t libfdx_ios_metal_create_render_pipeline(
        int64_t context,
        int64_t shader_module,
        int32_t primitive_topology,
        const int32_t* vertex_strides,
        const int32_t* vertex_step_modes,
        int32_t vertex_layout_count,
        const int32_t* attribute_bindings,
        const int32_t* attribute_locations,
        const int32_t* attribute_formats,
        const int32_t* attribute_offsets,
        int32_t attribute_count,
        int32_t sampled_texture_count,
        int32_t pbr_uniforms_enabled,
        int32_t depth_test_enabled,
        int32_t depth_write_enabled);
void libfdx_ios_metal_begin_render_pass(
        int64_t context,
        int32_t clear,
        float red,
        float green,
        float blue,
        float alpha,
        int32_t store,
        int32_t depth_enabled,
        int32_t depth_clear,
        float depth_clear_value);
void libfdx_ios_metal_set_pipeline(int64_t context, int64_t pipeline);
void libfdx_ios_metal_set_vertex_buffer(int64_t context, int32_t slot, int64_t buffer);
void libfdx_ios_metal_set_index_buffer(int64_t context, int64_t buffer);
void libfdx_ios_metal_set_scissor(
        int64_t context, int32_t x, int32_t y, int32_t width, int32_t height);
void libfdx_ios_metal_set_viewport(
        int64_t context, int32_t x, int32_t y, int32_t width, int32_t height);
void libfdx_ios_metal_set_texture(int64_t context, int32_t texture_slot, int32_t sampler_slot, int64_t texture);
void libfdx_ios_metal_set_uniform_buffer(int64_t context, const void* data, int32_t byte_count);
void libfdx_ios_metal_draw(
        int64_t context, int32_t vertex_count, int32_t instance_count, int32_t first_vertex, int32_t first_instance);
void libfdx_ios_metal_draw_indexed(
        int64_t context,
        int32_t index_count,
        int32_t instance_count,
        int32_t first_index,
        int32_t base_vertex,
        int32_t first_instance);
void libfdx_ios_metal_end_render_pass(int64_t context);
void libfdx_ios_metal_destroy_shader_module(int64_t shader_module);
void libfdx_ios_metal_destroy_render_pipeline(int64_t pipeline);
void libfdx_ios_metal_destroy_buffer(int64_t buffer);
void libfdx_ios_metal_destroy_texture(int64_t texture);
void libfdx_ios_metal_destroy(int64_t context);

#ifdef __cplusplus
}
#endif

#endif
