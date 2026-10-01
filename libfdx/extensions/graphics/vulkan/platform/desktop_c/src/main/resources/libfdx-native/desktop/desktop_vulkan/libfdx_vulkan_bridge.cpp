#include "libfdx_desktop_values.hpp"
#include "libfdx_desktop_vulkan.h"
#include <algorithm>
#include <cstring>

using namespace libfdx_c;

extern "C" int64_t fdx_c_vulkan_create_compute(int64_t context, void* words_data, int32_t words_length, void* entry_data, int32_t entry_length, void* bindings_data, int32_t bindings_length) {
    FdxCSpan words{words_data, words_length};
    FdxCSpan entry{entry_data, entry_length};
    FdxCSpan bindings{bindings_data, bindings_length};
    NativeArray<int32_t> code(words), layout(bindings);
    if (layout.size() % 3) throw std::invalid_argument("Invalid compute binding table");
    return fdx_desktop_vulkan_create_compute(context, reinterpret_cast<const uint32_t*>(code.data()),
            code.size(), java_text(entry).c_str(), layout.data(), layout.size() / 3);
}

extern "C" void fdx_c_vulkan_dispatch_compute(int64_t context, int64_t pipeline, void* resources_data, int32_t resources_length,
        int32_t count, int32_t x, int32_t y, int32_t z) {
    FdxCSpan resources{resources_data, resources_length};
    NativeArray<int64_t, 192> values(resources);
    values.require(static_cast<int64_t>(count) * 3);
    fdx_desktop_vulkan_dispatch_compute(context, pipeline, values.data(), count, x, y, z);
}

extern "C" void fdx_c_vulkan_read_buffer(int64_t source, int32_t offset, void* output_data, int32_t output_length, int32_t size) {
    FdxCSpan output{output_data, output_length};
    fdx_desktop_vulkan_read_buffer(source, offset, direct_bytes(output, size, true), size);
}

extern "C" int32_t fdx_c_fdx_desktop_vulkan_probe_instance() {

    return fdx_desktop_vulkan_probe_instance();
}

extern "C" int64_t fdx_c_fdx_desktop_vulkan_create(int64_t windowHandle, int32_t width, int32_t height, int32_t vSync, int32_t preferMailboxPresentMode, int32_t framesInFlight) {

    return fdx_desktop_vulkan_create(windowHandle, width, height, vSync, preferMailboxPresentMode, framesInFlight);
}

extern "C" void fdx_c_fdx_desktop_vulkan_resize(int64_t context, int32_t width, int32_t height) {

    fdx_desktop_vulkan_resize(context, width, height);
}

extern "C" int32_t fdx_c_fdx_desktop_vulkan_begin_frame(int64_t context) {

    return fdx_desktop_vulkan_begin_frame(context);
}

extern "C" void fdx_c_fdx_desktop_vulkan_end_frame(int64_t context) {

    fdx_desktop_vulkan_end_frame(context);
}

extern "C" void fdx_c_fdx_desktop_vulkan_clear(int64_t context, float red, float green, float blue, float alpha) {

    fdx_desktop_vulkan_clear(context, red, green, blue, alpha);
}

extern "C" int64_t fdx_c_fdx_desktop_vulkan_create_buffer(int64_t context, int32_t size, int32_t usage) {

    return fdx_desktop_vulkan_create_buffer(context, size, usage);
}

extern "C" int64_t fdx_c_fdx_desktop_vulkan_create_texture(int64_t context, int32_t width, int32_t height, int32_t format, int32_t wrapS, int32_t wrapT, int32_t filter,
        int32_t magFilter, int32_t mipFilter, int32_t mipLevels, int32_t samples, int32_t usage) {

    return fdx_desktop_vulkan_create_texture_full(context, width, height, format, wrapS, wrapT, filter,
            magFilter, mipFilter, mipLevels, samples, usage);
}

extern "C" int64_t fdx_c_fdx_desktop_vulkan_create_shader_module(int64_t context, void* vertexWords_data, int32_t vertexWords_length, int32_t vertexWordCount, void* fragmentWords_data, int32_t fragmentWords_length, int32_t fragmentWordCount, void* vertexEntry_data, int32_t vertexEntry_length, void* fragmentEntry_data, int32_t fragmentEntry_length) {
    FdxCSpan vertexWords{vertexWords_data, vertexWords_length};
    FdxCSpan fragmentWords{fragmentWords_data, fragmentWords_length};
    FdxCSpan vertexEntry{vertexEntry_data, vertexEntry_length};
    FdxCSpan fragmentEntry{fragmentEntry_data, fragmentEntry_length};
    NativeArray<int32_t> vertexWords_array(vertexWords);
    NativeArray<int32_t> fragmentWords_array(fragmentWords);
    vertexWords_array.require(vertexWordCount);
    fragmentWords_array.require(fragmentWordCount);
    return fdx_desktop_vulkan_create_shader_module_with_entries(context, vertexWords_array.data(), vertexWordCount, fragmentWords_array.data(), fragmentWordCount, java_text(vertexEntry).c_str(), java_text(fragmentEntry).c_str());
}

extern "C" int64_t fdx_c_fdx_desktop_vulkan_create_render_pipeline(int64_t context, int64_t shaderModule, int32_t primitiveTopology, void* vertexStrides_data, int32_t vertexStrides_length, void* vertexStepModes_data, int32_t vertexStepModes_length, int32_t vertexLayoutCount, void* attributeBindings_data, int32_t attributeBindings_length, void* attributeLocations_data, int32_t attributeLocations_length, void* attributeFormats_data, int32_t attributeFormats_length, void* attributeOffsets_data, int32_t attributeOffsets_length, int32_t attributeCount, int32_t sampledTextureCount, int32_t uniformBufferEnabled, void* packedState_data, int32_t packedState_length) {
    FdxCSpan vertexStrides{vertexStrides_data, vertexStrides_length};
    FdxCSpan vertexStepModes{vertexStepModes_data, vertexStepModes_length};
    FdxCSpan attributeBindings{attributeBindings_data, attributeBindings_length};
    FdxCSpan attributeLocations{attributeLocations_data, attributeLocations_length};
    FdxCSpan attributeFormats{attributeFormats_data, attributeFormats_length};
    FdxCSpan attributeOffsets{attributeOffsets_data, attributeOffsets_length};
    FdxCSpan packedState{packedState_data, packedState_length};
    NativeArray<int32_t> vertexStrides_array(vertexStrides);
    NativeArray<int32_t> vertexStepModes_array(vertexStepModes);
    NativeArray<int32_t> attributeBindings_array(attributeBindings);
    NativeArray<int32_t> attributeLocations_array(attributeLocations);
    NativeArray<int32_t> attributeFormats_array(attributeFormats);
    NativeArray<int32_t> attributeOffsets_array(attributeOffsets);
    vertexStrides_array.require(vertexLayoutCount);
    vertexStepModes_array.require(vertexLayoutCount);
    attributeBindings_array.require(attributeCount);
    attributeLocations_array.require(attributeCount);
    attributeFormats_array.require(attributeCount);
    attributeOffsets_array.require(attributeCount);
    NativeArray<int32_t> values(packedState);
    values.require(23);
    const int32_t* p = values.data();
    FdxVulkanPipelineState state{};
    state.targets.colorCount = p[0];
    if (p[0] < 0 || p[0] > 8) throw std::invalid_argument("Vulkan color count exceeds eight");
    values.require(23 + p[0] * 9);
    state.targets.depth = p[1];
    state.targets.samples = p[2];
    state.cullMode = p[3];
    state.frontFace = p[4];
    state.sampleMask = p[5];
    state.alphaToCoverage = p[6];
    state.depthTest = p[7];
    state.depthWrite = p[8];
    state.depthCompare = p[9];
    state.depthBias = p[10];
    std::memcpy(&state.depthBiasSlope, p + 11, sizeof(float));
    std::memcpy(&state.depthBiasClamp, p + 12, sizeof(float));
    std::copy(p + 13, p + 23, state.stencil);
    for (int i = 0; i < p[0]; ++i) {
        state.targets.colors[i] = p[23 + i * 9];
        std::copy(p + 24 + i * 9, p + 32 + i * 9, state.colors[i]);
    }
    return fdx_desktop_vulkan_create_pipeline_full(context, shaderModule, primitiveTopology, vertexStrides_array.data(), vertexStepModes_array.data(), vertexLayoutCount, attributeBindings_array.data(), attributeLocations_array.data(), attributeFormats_array.data(), attributeOffsets_array.data(), attributeCount, sampledTextureCount, uniformBufferEnabled, &state);
}

extern "C" int32_t fdx_c_fdx_desktop_vulkan_begin_render_pass(int64_t context, void* packedState_data, int32_t packedState_length, void* textures_data, int32_t textures_length, void* clears_data, int32_t clears_length) {
    FdxCSpan packedState{packedState_data, packedState_length};
    FdxCSpan textures{textures_data, textures_length};
    FdxCSpan clears{clears_data, clears_length};
    NativeArray<int32_t, 64> state(packedState);
    NativeArray<int64_t> handles(textures);
    NativeArray<float, 64> colors(clears);
    state.require(51);
    handles.require(17);
    colors.require(33);
    const int32_t* p = state.data();
    FdxVulkanPass pass{};
    pass.targets.colorCount = p[0];
    if (p[0] < 0 || p[0] > 8) throw std::invalid_argument("Vulkan color count exceeds eight");
    pass.targets.depth = p[1];
    pass.targets.samples = p[2];
    pass.width = p[3];
    pass.height = p[4];
    pass.depthMip = p[5];
    pass.depthLoad = p[6];
    pass.depthStore = p[7];
    pass.stencilLoad = p[8];
    pass.stencilStore = p[9];
    pass.stencilClear = p[10];
    pass.depth = handles.data()[16];
    pass.depthClear = colors.data()[32];
    for (int i = 0; i < p[0]; ++i) {
        pass.targets.colors[i] = p[11 + i * 5];
        pass.colorMips[i] = p[12 + i * 5];
        pass.resolveMips[i] = p[13 + i * 5];
        pass.load[i] = p[14 + i * 5];
        pass.store[i] = p[15 + i * 5];
        pass.colors[i] = handles.data()[i];
        pass.resolves[i] = handles.data()[8 + i];
        std::copy(colors.data() + i * 4, colors.data() + i * 4 + 4, pass.clear[i]);
    }
    return fdx_desktop_vulkan_begin_pass_full(context, &pass);
}

extern "C" void fdx_c_fdx_desktop_vulkan_set_pipeline(int64_t context, int64_t pipeline) {

    fdx_desktop_vulkan_set_pipeline(context, pipeline);
}

extern "C" void fdx_c_fdx_desktop_vulkan_set_vertex_buffer(int64_t context, int32_t slot, int64_t buffer) {

    fdx_desktop_vulkan_set_vertex_buffer(context, slot, buffer);
}

extern "C" void fdx_c_fdx_desktop_vulkan_set_index_buffer(int64_t context, int64_t buffer) {

    fdx_desktop_vulkan_set_index_buffer(context, buffer);
}

extern "C" void fdx_c_fdx_desktop_vulkan_set_scissor(int64_t context, int32_t x, int32_t y, int32_t width, int32_t height) {

    fdx_desktop_vulkan_set_scissor(context, x, y, width, height);
}

extern "C" void fdx_c_fdx_desktop_vulkan_set_viewport(int64_t context, int32_t x, int32_t y, int32_t width, int32_t height) {

    fdx_desktop_vulkan_set_viewport(context, x, y, width, height);
}

extern "C" void fdx_c_fdx_desktop_vulkan_bind_textures(int64_t context, int64_t pipeline, void* textures_data, int32_t textures_length, int32_t count) {
    FdxCSpan textures{textures_data, textures_length};
    NativeArray<int64_t> textures_array(textures);
    textures_array.require(count);
    fdx_desktop_vulkan_bind_textures(context, pipeline, textures_array.data(), count);
}

extern "C" void fdx_c_fdx_desktop_vulkan_draw(int64_t context, int32_t vertexCount, int32_t instanceCount, int32_t firstVertex, int32_t firstInstance) {

    fdx_desktop_vulkan_draw(context, vertexCount, instanceCount, firstVertex, firstInstance);
}

extern "C" void fdx_c_fdx_desktop_vulkan_draw_indexed(int64_t context, int32_t indexCount, int32_t instanceCount, int32_t firstIndex, int32_t baseVertex, int32_t firstInstance) {

    fdx_desktop_vulkan_draw_indexed(context, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
}

extern "C" void fdx_c_fdx_desktop_vulkan_end_render_pass(int64_t context) {

    fdx_desktop_vulkan_end_render_pass(context);
}

extern "C" int32_t fdx_c_fdx_desktop_vulkan_surface_format(int64_t context) {

    return fdx_desktop_vulkan_surface_format(context);
}

extern "C" void fdx_c_fdx_desktop_vulkan_destroy_shader_module(int64_t shaderModule) {

    fdx_desktop_vulkan_destroy_shader_module(shaderModule);
}

extern "C" void fdx_c_fdx_desktop_vulkan_destroy_render_pipeline(int64_t pipeline, int32_t published) {

    fdx_desktop_vulkan_destroy_render_pipeline(pipeline, published);
}

extern "C" void fdx_c_fdx_desktop_vulkan_destroy_buffer(int64_t buffer) {

    fdx_desktop_vulkan_destroy_buffer(buffer);
}

extern "C" void fdx_c_fdx_desktop_vulkan_destroy_texture(int64_t texture) {

    fdx_desktop_vulkan_destroy_texture(texture);
}

extern "C" void fdx_c_fdx_desktop_vulkan_destroy(int64_t context) {

    fdx_desktop_vulkan_destroy(context);
}

extern "C" void fdx_c_fdx_desktop_vulkan_retain(int64_t context) {

    fdx_desktop_vulkan_retain(context);
}

extern "C" void fdx_c_fdx_desktop_vulkan_read_pixels_rgba8(int64_t context, void* data_data, int32_t data_length, int32_t count) {
    FdxCSpan data{data_data, data_length};
    fdx_desktop_vulkan_read_pixels_rgba8(context, direct_bytes(data, count, true), count);
}

extern "C" void fdx_c_fdx_desktop_vulkan_write_buffer(int64_t buffer, void* data_data, int32_t data_length, int32_t count) {
    FdxCSpan data{data_data, data_length};
    fdx_desktop_vulkan_write_buffer(buffer, direct_bytes(data, count, false), count);
}

extern "C" int32_t fdx_c_fdx_desktop_vulkan_write_texture(int64_t texture, int32_t level, void* data_data, int32_t data_length, int32_t count) {
    FdxCSpan data{data_data, data_length};
    return fdx_desktop_vulkan_write_texture_level(texture, level, direct_bytes(data, count, false), count);
}

extern "C" int32_t fdx_c_fdx_desktop_vulkan_bind_uniforms(int64_t context, int64_t pipeline, void* data_data, int32_t data_length, int32_t count) {
    FdxCSpan data{data_data, data_length};
    return fdx_desktop_vulkan_bind_uniforms(context, pipeline, direct_bytes(data, count, false), count);
}

#include <GLFW/glfw3.h>
extern "C" int32_t fdx_c_glfwVulkanSupported() { return glfwVulkanSupported(); }
extern "C" int32_t fdx_c_glfwGetRequiredInstanceExtensions() {
    uint32_t count = 0;
    glfwGetRequiredInstanceExtensions(&count);
    return static_cast<int32_t>(count);
}
