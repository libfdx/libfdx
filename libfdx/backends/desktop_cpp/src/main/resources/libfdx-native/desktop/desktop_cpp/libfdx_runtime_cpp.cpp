#include "libfdx_jnative.hpp"
#include "libfdx_freetype.h"
#include "libfdx_native_image.h"
#include "libfdx_desktop_shaderc.h"
#include <cstring>

using namespace libfdx_cpp;

namespace {
void* pointer(int64_t value) { return reinterpret_cast<void*>(static_cast<intptr_t>(value)); }
int64_t address(const void* value) { return static_cast<int64_t>(reinterpret_cast<intptr_t>(value)); }
}

extern "C" int32_t fdx_cpp_image_dimensions(jn_handle data, jn_handle dimensions) {
    NativeArray<int8_t> bytes(data);
    NativeArray<int32_t> size(dimensions);
    size.require(2);
    int32_t result = fdx_native_image_dimensions(bytes.data(), bytes.size(), size.data());
    size.write();
    return result;
}

extern "C" int32_t fdx_cpp_image_decode(jn_handle data, jn_handle target, int32_t size) {
    NativeArray<int8_t> bytes(data);
    return fdx_native_image_decode_rgba8(bytes.data(), bytes.size(), direct_bytes(target, size, true), size);
}

extern "C" int32_t fdx_cpp_freetype_rasterize(jn_handle font, int32_t font_size,
        jn_handle points, int32_t point_count, float pixel_size, int32_t padding,
        int32_t atlas_width, jn_handle metric_ints, jn_handle metric_floats,
        jn_handle rgba, int32_t rgba_size, jn_handle glyph_ints, int32_t glyph_int_count,
        jn_handle glyph_floats, int32_t glyph_float_count, jn_handle kernings, int32_t kerning_count) {
    NativeArray<int8_t> font_data(font);
    NativeArray<int32_t> code_points(points), metrics(metric_ints), glyphs(glyph_ints), kerning(kernings);
    NativeArray<float> measures(metric_floats), positions(glyph_floats);
    font_data.require(font_size);
    code_points.require(point_count);
    metrics.require(4);
    measures.require(3);
    glyphs.require(glyph_int_count);
    positions.require(glyph_float_count);
    kerning.require(kerning_count);
    int32_t result = fdx_freetype_rasterize(font_data.data(), font_size, code_points.data(),
            point_count, pixel_size, padding, atlas_width, metrics.data(), measures.data(),
            direct_bytes(rgba, rgba_size, true), rgba_size, glyphs.data(), glyph_int_count,
            positions.data(), glyph_float_count, kerning.data(), kerning_count);
    metrics.write();
    measures.write();
    glyphs.write();
    positions.write();
    kerning.write();
    return result;
}

extern "C" void fdx_cpp_copy_bytes(int64_t source, jn_handle target) {
    NativeArray<int8_t> bytes(target);
    if (bytes.size() != 0) {
        if (!source) throw std::invalid_argument("Null native byte source");
        std::memcpy(bytes.data(), pointer(source), bytes.size());
        bytes.write();
    }
}

extern "C" jn_handle fdx_cpp_c_string(int64_t source, int32_t maximum) {
    if (!source) return string_handle("");
    if (maximum <= 0) throw std::invalid_argument("Invalid native string limit");
    const char* text = static_cast<const char*>(pointer(source));
    int32_t size = 0;
    while (size < maximum && text[size]) ++size;
    if (size == maximum) throw std::invalid_argument("Native string exceeded its limit");
    return string_handle(text);
}

extern "C" int32_t fdx_cpp_shaderc_available() { return fdx_desktop_shaderc_available(); }
extern "C" int64_t fdx_cpp_shaderc_failure_message() { return address(fdx_desktop_shaderc_failure_message()); }
extern "C" int64_t fdx_cpp_shaderc_compile(jn_handle source, int32_t target, int32_t stage,
        jn_handle entry, jn_handle profile, jn_handle es_profile) {
    auto text = java_text(source);
    auto entry_text = java_text(entry);
    auto profile_text = java_text(profile);
    auto es_text = java_text(es_profile);
    return address(fdx_desktop_shaderc_compile(text.c_str(), target, stage,
            entry_text.c_str(), profile_text.c_str(), es_text.c_str()));
}
extern "C" int32_t fdx_cpp_shaderc_result_status(int64_t value) { return fdx_desktop_shaderc_result_status(pointer(value)); }
extern "C" int32_t fdx_cpp_shaderc_result_output_kind(int64_t value) { return fdx_desktop_shaderc_result_output_kind(pointer(value)); }
extern "C" int32_t fdx_cpp_shaderc_result_output_size(int64_t value) { return fdx_desktop_shaderc_result_output_size(pointer(value)); }
extern "C" int32_t fdx_cpp_shaderc_result_reflection_size(int64_t value) { return fdx_desktop_shaderc_result_reflection_size(pointer(value)); }
extern "C" int32_t fdx_cpp_shaderc_result_target_interface_size(int64_t value) { return fdx_desktop_shaderc_result_target_interface_size(pointer(value)); }
extern "C" int64_t fdx_cpp_shaderc_result_output(int64_t value) { return address(fdx_desktop_shaderc_result_output(pointer(value))); }
extern "C" int64_t fdx_cpp_shaderc_result_reflection(int64_t value) { return address(fdx_desktop_shaderc_result_reflection(pointer(value))); }
extern "C" int64_t fdx_cpp_shaderc_result_target_interface(int64_t value) { return address(fdx_desktop_shaderc_result_target_interface(pointer(value))); }
extern "C" int64_t fdx_cpp_shaderc_result_diagnostics(int64_t value) { return address(fdx_desktop_shaderc_result_diagnostics(pointer(value))); }
extern "C" void fdx_cpp_shaderc_result_free(int64_t value) { fdx_desktop_shaderc_result_free(pointer(value)); }
