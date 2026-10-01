#ifndef LIBFDX_DESKTOP_BRIDGE_H
#define LIBFDX_DESKTOP_BRIDGE_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
int32_t fdx_c_gl_parallel_shader_compilation(int32_t workers);
int32_t fdx_c_glewInit();
int32_t fdx_c_glCreateProgram();
int32_t fdx_c_glCreateShader(int32_t type);
void fdx_c_glShaderSource(int32_t shader, void* source_data, int32_t source_length);
void fdx_c_glCompileShader(int32_t shader);
void fdx_c_glGetShaderiv(int32_t shader, int32_t name, void* values_data, int32_t values_length);
void fdx_c_glGetShaderInfoLog(int32_t shader, int32_t maxLength, void* length_data, int32_t length_length, void* infoLog_data, int32_t infoLog_length);
void fdx_c_glDeleteShader(int32_t shader);
void fdx_c_glAttachShader(int32_t program, int32_t shader);
void fdx_c_glLinkProgram(int32_t program);
void fdx_c_glGetProgramiv(int32_t program, int32_t name, void* values_data, int32_t values_length);
void fdx_c_glGetProgramInfoLog(int32_t program, int32_t maxLength, void* length_data, int32_t length_length, void* infoLog_data, int32_t infoLog_length);
void fdx_c_glDeleteProgram(int32_t program);
void fdx_c_glUseProgram(int32_t program);
void fdx_c_glGenVertexArrays(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glBindVertexArray(int32_t vertexArray);
void fdx_c_glDeleteVertexArrays(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glGenBuffers(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glBindBuffer(int32_t target, int32_t buffer);
void fdx_c_glBufferData(int32_t target, int32_t size, int64_t data, int32_t usage);
void fdx_c_glBindBufferBase(int32_t target, int32_t index, int32_t buffer);
void fdx_c_glDeleteBuffers(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glGenTextures(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glBindTexture(int32_t target, int32_t texture);
void fdx_c_glTexParameteri(int32_t target, int32_t name, int32_t value);
void fdx_c_glTexImage2D(int32_t target, int32_t level, int32_t internalFormat, int32_t width, int32_t height, int32_t border, int32_t format, int32_t type, int64_t data);
void fdx_c_glDeleteTextures(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glGenFramebuffers(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glBindFramebuffer(int32_t target, int32_t framebuffer);
void fdx_c_glFramebufferTexture2D(int32_t target, int32_t attachment, int32_t textureTarget, int32_t texture, int32_t level);
int32_t fdx_c_glCheckFramebufferStatus(int32_t target);
void fdx_c_glDeleteFramebuffers(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glGenRenderbuffers(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glBindRenderbuffer(int32_t target, int32_t renderbuffer);
void fdx_c_glRenderbufferStorage(int32_t target, int32_t internalFormat, int32_t width, int32_t height);
void fdx_c_glFramebufferRenderbuffer(int32_t target, int32_t attachment, int32_t renderbufferTarget, int32_t renderbuffer);
void fdx_c_glDeleteRenderbuffers(int32_t count, void* values_data, int32_t values_length);
void fdx_c_glActiveTexture(int32_t texture);
int32_t fdx_c_glGetUniformLocation(int32_t program, void* name_data, int32_t name_length);
void fdx_c_glUniform1i(int32_t location, int32_t value);
int32_t fdx_c_glGetUniformBlockIndex(int32_t program, void* uniformBlockName_data, int32_t uniformBlockName_length);
void fdx_c_glUniformBlockBinding(int32_t program, int32_t uniformBlockIndex, int32_t uniformBlockBinding);
void fdx_c_glUniform1f(int32_t location, float value);
void fdx_c_glUniform3f(int32_t location, float x, float y, float z);
void fdx_c_glUniform4f(int32_t location, float x, float y, float z, float w);
void fdx_c_glUniformMatrix4fv(int32_t location, int32_t count, int32_t transpose, void* values_data, int32_t values_length);
void fdx_c_glEnable(int32_t value);
void fdx_c_glDisable(int32_t value);
void fdx_c_glDepthMask(int32_t enabled);
void fdx_c_glDepthFunc(int32_t func);
void fdx_c_glBlendFuncSeparate(int32_t sourceRgb, int32_t destinationRgb, int32_t sourceAlpha, int32_t destinationAlpha);
void fdx_c_glEnableVertexAttribArray(int32_t index);
void fdx_c_glDisableVertexAttribArray(int32_t index);
void fdx_c_glVertexAttribPointer(int32_t index, int32_t size, int32_t type, int32_t normalized, int32_t stride, int64_t offset);
void fdx_c_glVertexAttribDivisor(int32_t index, int32_t divisor);
void fdx_c_glViewport(int32_t x, int32_t y, int32_t width, int32_t height);
void fdx_c_glScissor(int32_t x, int32_t y, int32_t width, int32_t height);
void fdx_c_glClearColor(float red, float green, float blue, float alpha);
void fdx_c_glClearDepth(double depth);
void fdx_c_glClear(int32_t mask);
void fdx_c_glPixelStorei(int32_t name, int32_t value);
void fdx_c_glReadBuffer(int32_t source);
void fdx_c_glDrawArrays(int32_t mode, int32_t first, int32_t count);
void fdx_c_glDrawArraysInstanced(int32_t mode, int32_t first, int32_t count, int32_t instanceCount);
void fdx_c_glDrawElements(int32_t mode, int32_t count, int32_t type, int64_t indices);
void fdx_c_glDrawElementsBaseVertex(int32_t mode, int32_t count, int32_t type, int64_t indices, int32_t baseVertex);
void fdx_c_glDrawElementsInstanced(int32_t mode, int32_t count, int32_t type, int64_t indices, int32_t instanceCount);
void fdx_c_glDrawElementsInstancedBaseVertex(int32_t mode, int32_t count, int32_t type, int64_t indices, int32_t instanceCount, int32_t baseVertex);
void fdx_c_glBufferSubData(int32_t target, int32_t offset, int32_t size, void* data_data, int32_t data_length);
void fdx_c_glTexSubImage2D(int32_t target, int32_t level, int32_t x, int32_t y,
        int32_t width, int32_t height, int32_t format, int32_t type, void* data_data, int32_t data_length);
void fdx_c_glReadPixels(int32_t x, int32_t y, int32_t width, int32_t height,
        int32_t format, int32_t type, void* pixels_data, int32_t pixels_length);
int32_t fdx_c_gl_features_supported(int32_t feature);
int32_t fdx_c_gl_compute_program(void* sourceHandle_data, int32_t sourceHandle_length);
void fdx_c_gl_compute_buffer(int32_t slot, int32_t buffer, int32_t offset, int32_t size, int32_t uniform);
void fdx_c_gl_storage_image(int32_t slot, int32_t texture, int32_t format);
void fdx_c_gl_dispatch_compute(int32_t x, int32_t y, int32_t z);
void fdx_c_gl_compute_barrier();
void fdx_c_gl_copy_buffer(int32_t source, int32_t sourceOffset, int32_t destination, int32_t destinationOffset, int32_t size);
void fdx_c_gl_read_buffer(int32_t source, int32_t offset, void* output_data, int32_t output_length, int32_t size);
void fdx_c_gl_texture_multisample(int32_t texture, int32_t format, int32_t width, int32_t height, int32_t samples);
void fdx_c_gl_framebuffer_texture(int32_t index, int32_t texture, int32_t level, int32_t samples);
void fdx_c_gl_draw_buffers(int32_t count);
void fdx_c_gl_clear_attachment(int32_t index, float red, float green, float blue, float alpha);
void fdx_c_gl_depth_multisample(int32_t width, int32_t height, int32_t samples);
void fdx_c_gl_resolve_framebuffer(int32_t source, int32_t index, int32_t destination, int32_t width, int32_t height);
void fdx_c_gl_reset_masks();
void fdx_c_gl_pipeline_state(void* packed_data, int32_t packed_length);
void fdx_c_gl_color_targets(void* packed_data, int32_t packed_length, int32_t count);
int64_t fdx_c_vulkan_create_compute(int64_t context, void* words_data, int32_t words_length, void* entry_data, int32_t entry_length, void* bindings_data, int32_t bindings_length);
void fdx_c_vulkan_dispatch_compute(int64_t context, int64_t pipeline, void* resources_data, int32_t resources_length,
        int32_t count, int32_t x, int32_t y, int32_t z);
void fdx_c_vulkan_read_buffer(int64_t source, int32_t offset, void* output_data, int32_t output_length, int32_t size);
int32_t fdx_c_fdx_desktop_vulkan_probe_instance();
int64_t fdx_c_fdx_desktop_vulkan_create(int64_t windowHandle, int32_t width, int32_t height, int32_t vSync, int32_t preferMailboxPresentMode, int32_t framesInFlight);
void fdx_c_fdx_desktop_vulkan_resize(int64_t context, int32_t width, int32_t height);
int32_t fdx_c_fdx_desktop_vulkan_begin_frame(int64_t context);
void fdx_c_fdx_desktop_vulkan_end_frame(int64_t context);
void fdx_c_fdx_desktop_vulkan_clear(int64_t context, float red, float green, float blue, float alpha);
int64_t fdx_c_fdx_desktop_vulkan_create_buffer(int64_t context, int32_t size, int32_t usage);
int64_t fdx_c_fdx_desktop_vulkan_create_texture(int64_t context, int32_t width, int32_t height, int32_t format, int32_t wrapS, int32_t wrapT, int32_t filter,
        int32_t magFilter, int32_t mipFilter, int32_t mipLevels, int32_t samples, int32_t usage);
int64_t fdx_c_fdx_desktop_vulkan_create_shader_module(int64_t context, void* vertexWords_data, int32_t vertexWords_length, int32_t vertexWordCount, void* fragmentWords_data, int32_t fragmentWords_length, int32_t fragmentWordCount, void* vertexEntry_data, int32_t vertexEntry_length, void* fragmentEntry_data, int32_t fragmentEntry_length);
int64_t fdx_c_fdx_desktop_vulkan_create_render_pipeline(int64_t context, int64_t shaderModule, int32_t primitiveTopology, void* vertexStrides_data, int32_t vertexStrides_length, void* vertexStepModes_data, int32_t vertexStepModes_length, int32_t vertexLayoutCount, void* attributeBindings_data, int32_t attributeBindings_length, void* attributeLocations_data, int32_t attributeLocations_length, void* attributeFormats_data, int32_t attributeFormats_length, void* attributeOffsets_data, int32_t attributeOffsets_length, int32_t attributeCount, int32_t sampledTextureCount, int32_t uniformBufferEnabled, void* packedState_data, int32_t packedState_length);
int32_t fdx_c_fdx_desktop_vulkan_begin_render_pass(int64_t context, void* packedState_data, int32_t packedState_length, void* textures_data, int32_t textures_length, void* clears_data, int32_t clears_length);
void fdx_c_fdx_desktop_vulkan_set_pipeline(int64_t context, int64_t pipeline);
void fdx_c_fdx_desktop_vulkan_set_vertex_buffer(int64_t context, int32_t slot, int64_t buffer);
void fdx_c_fdx_desktop_vulkan_set_index_buffer(int64_t context, int64_t buffer);
void fdx_c_fdx_desktop_vulkan_set_scissor(int64_t context, int32_t x, int32_t y, int32_t width, int32_t height);
void fdx_c_fdx_desktop_vulkan_set_viewport(int64_t context, int32_t x, int32_t y, int32_t width, int32_t height);
void fdx_c_fdx_desktop_vulkan_bind_textures(int64_t context, int64_t pipeline, void* textures_data, int32_t textures_length, int32_t count);
void fdx_c_fdx_desktop_vulkan_draw(int64_t context, int32_t vertexCount, int32_t instanceCount, int32_t firstVertex, int32_t firstInstance);
void fdx_c_fdx_desktop_vulkan_draw_indexed(int64_t context, int32_t indexCount, int32_t instanceCount, int32_t firstIndex, int32_t baseVertex, int32_t firstInstance);
void fdx_c_fdx_desktop_vulkan_end_render_pass(int64_t context);
int32_t fdx_c_fdx_desktop_vulkan_surface_format(int64_t context);
void fdx_c_fdx_desktop_vulkan_destroy_shader_module(int64_t shaderModule);
void fdx_c_fdx_desktop_vulkan_destroy_render_pipeline(int64_t pipeline, int32_t published);
void fdx_c_fdx_desktop_vulkan_destroy_buffer(int64_t buffer);
void fdx_c_fdx_desktop_vulkan_destroy_texture(int64_t texture);
void fdx_c_fdx_desktop_vulkan_destroy(int64_t context);
void fdx_c_fdx_desktop_vulkan_retain(int64_t context);
void fdx_c_fdx_desktop_vulkan_read_pixels_rgba8(int64_t context, void* data_data, int32_t data_length, int32_t count);
void fdx_c_fdx_desktop_vulkan_write_buffer(int64_t buffer, void* data_data, int32_t data_length, int32_t count);
int32_t fdx_c_fdx_desktop_vulkan_write_texture(int64_t texture, int32_t level, void* data_data, int32_t data_length, int32_t count);
int32_t fdx_c_fdx_desktop_vulkan_bind_uniforms(int64_t context, int64_t pipeline, void* data_data, int32_t data_length, int32_t count);
int32_t fdx_c_glfwVulkanSupported(void);
void fdx_desktop_vulkan_wait_preparations(int64_t context);
void fdx_desktop_vulkan_copy_buffer(int64_t context, int64_t source, int32_t sourceOffset,
        int64_t destination, int32_t destinationOffset, int32_t size);
int32_t fdx_c_glfwGetRequiredInstanceExtensions(void);
#ifdef __cplusplus
}
#endif
#endif
