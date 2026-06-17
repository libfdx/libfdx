#include "fdx_shaderc.h"

#include <cstdint>
#include <fstream>
#include <iostream>
#include <string>
#include <string_view>
#include <vector>

namespace {

constexpr char kBase64[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

void WriteInt(std::vector<uint8_t>& bytes, int32_t value) {
    bytes.push_back(static_cast<uint8_t>(value & 0xff));
    bytes.push_back(static_cast<uint8_t>((value >> 8) & 0xff));
    bytes.push_back(static_cast<uint8_t>((value >> 16) & 0xff));
    bytes.push_back(static_cast<uint8_t>((value >> 24) & 0xff));
}

std::string Base64Encode(const std::vector<uint8_t>& bytes) {
    std::string out;
    out.reserve(((bytes.size() + 2) / 3) * 4);
    for (size_t i = 0; i < bytes.size(); i += 3) {
        uint32_t value = static_cast<uint32_t>(bytes[i]) << 16;
        bool has_second = i + 1 < bytes.size();
        bool has_third = i + 2 < bytes.size();
        if (has_second) {
            value |= static_cast<uint32_t>(bytes[i + 1]) << 8;
        }
        if (has_third) {
            value |= bytes[i + 2];
        }
        out.push_back(kBase64[(value >> 18) & 0x3f]);
        out.push_back(kBase64[(value >> 12) & 0x3f]);
        out.push_back(has_second ? kBase64[(value >> 6) & 0x3f] : '=');
        out.push_back(has_third ? kBase64[value & 0x3f] : '=');
    }
    return out;
}

std::string EncodeResult(const fdx_shaderc_result& result) {
    const char* diagnostics = result.diagnostics != nullptr ? result.diagnostics : "";
    int32_t diagnostic_size = static_cast<int32_t>(std::char_traits<char>::length(diagnostics));
    std::vector<uint8_t> bytes;
    bytes.reserve(16 + static_cast<size_t>(result.output_size) + static_cast<size_t>(diagnostic_size));
    WriteInt(bytes, result.status);
    WriteInt(bytes, result.output_kind);
    WriteInt(bytes, result.output_size);
    WriteInt(bytes, diagnostic_size);
    if (result.output != nullptr && result.output_size > 0) {
        bytes.insert(bytes.end(), result.output, result.output + result.output_size);
    }
    bytes.insert(bytes.end(), diagnostics, diagnostics + diagnostic_size);
    return Base64Encode(bytes);
}

int32_t Target(std::string_view value) {
    if (value == "webgpu-wgsl") {
        return FDX_SHADERC_TARGET_WEBGPU_WGSL;
    }
    if (value == "wgpu-wgsl") {
        return FDX_SHADERC_TARGET_WGPU_WGSL;
    }
    if (value == "webgl-glsl-es") {
        return FDX_SHADERC_TARGET_WEBGL_GLSL_ES;
    }
    if (value == "gles-glsl-es") {
        return FDX_SHADERC_TARGET_GLES_GLSL_ES;
    }
    if (value == "opengl-glsl") {
        return FDX_SHADERC_TARGET_OPENGL_GLSL;
    }
    if (value == "vulkan-spirv") {
        return FDX_SHADERC_TARGET_VULKAN_SPIRV;
    }
    if (value == "metal-msl") {
        return FDX_SHADERC_TARGET_METAL_MSL;
    }
    if (value == "directx-hlsl") {
        return FDX_SHADERC_TARGET_DIRECTX_HLSL;
    }
    return FDX_SHADERC_TARGET_WEBGPU_WGSL;
}

int32_t Stage(std::string_view value) {
    if (value == "vertex") {
        return FDX_SHADERC_STAGE_VERTEX;
    }
    if (value == "fragment") {
        return FDX_SHADERC_STAGE_FRAGMENT;
    }
    return FDX_SHADERC_STAGE_MODULE;
}

std::string ReadFile(const std::string& path) {
    std::ifstream input(path, std::ios::binary);
    return std::string(std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>());
}

}  // namespace

int main(int argc, char** argv) {
    std::string input_path;
    std::string target = "webgpu-wgsl";
    std::string stage = "module";
    std::string entry;
    std::string glsl_profile = "330";
    std::string glsl_es_profile = "300";

    for (int i = 1; i < argc; i++) {
        std::string_view arg(argv[i]);
        auto next = [&]() -> std::string {
            if (i + 1 >= argc) {
                return "";
            }
            return argv[++i];
        };
        if (arg == "--input") {
            input_path = next();
        } else if (arg == "--target") {
            target = next();
        } else if (arg == "--stage") {
            stage = next();
        } else if (arg == "--entry") {
            entry = next();
        } else if (arg == "--glsl-profile") {
            glsl_profile = next();
        } else if (arg == "--glsl-es-profile") {
            glsl_es_profile = next();
        }
    }

    if (input_path.empty()) {
        std::cerr << "Missing --input" << std::endl;
        return 2;
    }

    std::string source = ReadFile(input_path);
    fdx_shaderc_options options = {};
    options.target = Target(target);
    options.stage = Stage(stage);
    options.entry_point = entry.c_str();
    options.glsl_profile = glsl_profile.c_str();
    options.glsl_es_profile = glsl_es_profile.c_str();

    fdx_shaderc_result result = {};
    fdx_shaderc_compile_wgsl(source.c_str(), static_cast<int32_t>(source.size()), &options, &result);
    std::cout << EncodeResult(result) << std::endl;
    int status = result.status == 0 ? 0 : 1;
    fdx_shaderc_free_result(&result);
    return status;
}
