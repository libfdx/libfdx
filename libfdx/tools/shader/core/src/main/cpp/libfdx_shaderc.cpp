#include "fdx_shaderc.h"

#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <string_view>
#include <unordered_set>
#include <vector>

#include "src/tint/api/common/substitute_overrides_config.h"
#include "src/tint/api/helpers/generate_bindings.h"
#include "src/tint/api/tint.h"
#include "src/tint/lang/core/ir/referenced_module_vars.h"
#include "src/tint/lang/core/ir/transform/resource_table_helper.h"
#include "src/tint/lang/core/ir/var.h"
#include "src/tint/lang/core/type/pointer.h"
#include "src/tint/lang/glsl/writer/helpers/generate_bindings.h"
#include "src/tint/lang/glsl/writer/writer.h"
#include "src/tint/lang/hlsl/writer/writer.h"
#include "src/tint/lang/msl/writer/writer.h"
#include "src/tint/lang/spirv/writer/writer.h"
#include "src/tint/lang/wgsl/inspector/inspector.h"
#include "src/tint/lang/wgsl/reader/reader.h"

namespace {

struct ResultHandle {
    int32_t status = 1;
    int32_t output_kind = FDX_SHADERC_OUTPUT_NONE;
    std::vector<uint8_t> output;
    std::string diagnostics;
};

std::once_flag g_tint_initialize_once;

void EnsureTintInitialized() {
    std::call_once(g_tint_initialize_once, []() {
        tint::Initialize();
    });
}

std::string_view SafeView(const char* value) {
    return value != nullptr ? std::string_view(value) : std::string_view();
}

std::string SafeString(const char* value) {
    return std::string(SafeView(value));
}

std::vector<uint8_t> TextBytes(const std::string& text) {
    return std::vector<uint8_t>(text.begin(), text.end());
}

std::vector<uint8_t> SpirvBytes(const std::vector<uint32_t>& words) {
    std::vector<uint8_t> bytes(words.size() * sizeof(uint32_t));
    for (size_t i = 0; i < words.size(); i++) {
        uint32_t word = words[i];
        bytes[i * 4 + 0] = static_cast<uint8_t>(word & 0xffu);
        bytes[i * 4 + 1] = static_cast<uint8_t>((word >> 8u) & 0xffu);
        bytes[i * 4 + 2] = static_cast<uint8_t>((word >> 16u) & 0xffu);
        bytes[i * 4 + 3] = static_cast<uint8_t>((word >> 24u) & 0xffu);
    }
    return bytes;
}

bool IsGlslEsTarget(int32_t target) {
    return target == FDX_SHADERC_TARGET_WEBGL_GLSL_ES ||
           target == FDX_SHADERC_TARGET_GLES_GLSL_ES;
}

bool IsGlslTarget(int32_t target) {
    return IsGlslEsTarget(target) || target == FDX_SHADERC_TARGET_OPENGL_GLSL;
}

std::string EntryPoint(const fdx_shaderc_options& options) {
    std::string entry_point = SafeString(options.entry_point);
    if (!entry_point.empty()) {
        return entry_point;
    }
    if (options.stage == FDX_SHADERC_STAGE_FRAGMENT) {
        return "fs_main";
    }
    return "vs_main";
}

void ConfigureGlslVersion(tint::glsl::writer::Options& gen_options,
                          int32_t target,
                          std::string_view glsl_profile,
                          std::string_view glsl_es_profile) {
    if (target == FDX_SHADERC_TARGET_OPENGL_GLSL) {
        uint32_t major = 4;
        uint32_t minor = 6;
        if (glsl_profile == "330" || glsl_profile == "glsl330") {
            major = 3;
            minor = 3;
        }
        gen_options.version = tint::glsl::writer::Version(
            tint::glsl::writer::Version::Standard::kDesktop, major, minor);
        return;
    }

    uint32_t major = 3;
    uint32_t minor = 1;
    if (glsl_es_profile == "300" || glsl_es_profile == "es300" ||
        glsl_es_profile == "essl300" || glsl_es_profile == "webgl2") {
        minor = 0;
    }
    gen_options.version = tint::glsl::writer::Version(
        tint::glsl::writer::Version::Standard::kES, major, minor);
}

tint::msl::writer::ArrayLengthOptions GenerateMslArrayLengthFromConstants(
    tint::core::ir::Module& ir,
    const std::string& entry_point) {
    tint::msl::writer::ArrayLengthOptions options{
        .ubo_binding = 30,
    };

    tint::core::ir::Function* entry_function = nullptr;
    for (auto* function : ir.functions) {
        if (function->IsEntryPoint() && ir.NameOf(function).NameView() == entry_point) {
            entry_function = function;
            break;
        }
    }
    if (entry_function == nullptr) {
        return options;
    }

    tint::core::ir::ReferencedModuleVars<const tint::core::ir::Module> referenced_module_vars{ir};
    auto& references = referenced_module_vars.TransitiveReferences(entry_function);
    std::unordered_set<tint::BindingPoint> storage_bindings;
    for (auto* var : references) {
        auto binding_point = var->BindingPoint();
        if (!binding_point.has_value()) {
            continue;
        }

        auto* pointer_type = var->Result()->Type()->As<tint::core::type::Pointer>();
        if (pointer_type && pointer_type->AddressSpace() == tint::core::AddressSpace::kStorage &&
            !pointer_type->HasFixedFootprint()) {
            if (storage_bindings.insert(*binding_point).second) {
                options.bindpoint_to_size_index.emplace(
                    *binding_point, static_cast<uint32_t>(storage_bindings.size() - 1));
            }
        }
    }
    return options;
}

bool CompileParsedProgram(const tint::Program& program,
                          tint::inspector::Inspector& inspector,
                          const fdx_shaderc_options& options,
                          ResultHandle* result) {
    std::string entry_point = EntryPoint(options);
    auto ir = tint::wgsl::reader::ProgramToLoweredIR(program);
    if (ir != tint::Success) {
        result->diagnostics = "Failed to generate Tint IR: " + ir.Failure().reason;
        return false;
    }

    if (IsGlslTarget(options.target)) {
        tint::glsl::writer::Options gen_options;
        gen_options.entry_point_name = entry_point;
        ConfigureGlslVersion(gen_options, options.target, SafeView(options.glsl_profile),
                             SafeView(options.glsl_es_profile));
        gen_options.substitute_overrides_config = tint::SubstituteOverridesConfig{};
        auto entry = inspector.GetEntryPoint(entry_point);
        uint32_t offset = (entry.immediate_data_size + 3u) & ~3u;
        if (entry.instance_index_used) {
            gen_options.first_instance_offset = offset;
            offset += 4;
        }
        if (entry.frag_depth_used) {
            gen_options.depth_range_offsets = {offset + 0, offset + 4};
        }
        auto bindings = tint::glsl::writer::GenerateBindings(ir.Get(), entry_point);
        gen_options.bindings = std::move(bindings.bindings);
        gen_options.texture_builtins_from_uniform =
            std::move(bindings.texture_builtins_from_uniform);
        auto output = tint::glsl::writer::Generate(ir.Get(), gen_options);
        if (output != tint::Success) {
            result->diagnostics = "Failed to generate GLSL: " + output.Failure().reason;
            return false;
        }
        result->output_kind = FDX_SHADERC_OUTPUT_TEXT;
        result->output = TextBytes(output->glsl);
        return true;
    }

    if (options.target == FDX_SHADERC_TARGET_VULKAN_SPIRV) {
        tint::spirv::writer::Options gen_options;
        gen_options.entry_point_name = entry_point;
        gen_options.bindings = tint::GenerateBindings(ir.Get(), entry_point, false, false);
        gen_options.resource_table =
            tint::core::ir::transform::GenerateResourceTableConfig(ir.Get(), false);
        auto output = tint::spirv::writer::Generate(ir.Get(), gen_options);
        if (output != tint::Success) {
            result->diagnostics = "Failed to generate SPIR-V: " + output.Failure().reason;
            return false;
        }
        result->output_kind = FDX_SHADERC_OUTPUT_SPIRV;
        result->output = SpirvBytes(output->spirv);
        return true;
    }

    if (options.target == FDX_SHADERC_TARGET_METAL_MSL) {
        tint::msl::writer::Options gen_options;
        gen_options.entry_point_name = entry_point;
        gen_options.bindings = tint::GenerateBindings(ir.Get(), entry_point, false, false);
        gen_options.immediate_binding_point = tint::BindingPoint{.group = 0u, .binding = 30u};
        gen_options.array_length_from_constants =
            GenerateMslArrayLengthFromConstants(ir.Get(), entry_point);
        gen_options.substitute_overrides_config = tint::SubstituteOverridesConfig{};
        auto output = tint::msl::writer::Generate(ir.Get(), gen_options);
        if (output != tint::Success) {
            result->diagnostics = "Failed to generate MSL: " + output.Failure().reason;
            return false;
        }
        result->output_kind = FDX_SHADERC_OUTPUT_TEXT;
        result->output = TextBytes(output->msl);
        return true;
    }

    if (options.target == FDX_SHADERC_TARGET_DIRECTX_HLSL) {
        tint::hlsl::writer::Options gen_options;
        gen_options.entry_point_name = entry_point;
        gen_options.bindings = tint::GenerateBindings(ir.Get(), entry_point, false, false);
        gen_options.resource_table =
            tint::core::ir::transform::GenerateResourceTableConfig(ir.Get(), false);
        gen_options.substitute_overrides_config = tint::SubstituteOverridesConfig{};
        auto output = tint::hlsl::writer::Generate(ir.Get(), gen_options);
        if (output != tint::Success) {
            result->diagnostics = "Failed to generate HLSL: " + output.Failure().reason;
            return false;
        }
        result->output_kind = FDX_SHADERC_OUTPUT_TEXT;
        result->output = TextBytes(output->hlsl);
        return true;
    }

    result->diagnostics = "Unsupported libFDX shader compiler target: " +
                          std::to_string(options.target);
    return false;
}

ResultHandle* CompileHandle(const char* source,
                            int32_t source_size,
                            const fdx_shaderc_options& options) {
    EnsureTintInitialized();
    auto* result = new ResultHandle();
    if (source == nullptr || source_size <= 0) {
        result->diagnostics = "WGSL source cannot be empty";
        return result;
    }

    if (options.target == FDX_SHADERC_TARGET_WEBGPU_WGSL ||
        options.target == FDX_SHADERC_TARGET_WGPU_WGSL) {
        result->status = 0;
        result->output_kind = FDX_SHADERC_OUTPUT_TEXT;
        result->output.assign(source, source + source_size);
        return result;
    }

    std::string wgsl(source, source + source_size);
    tint::Source::File file("libfdx-shader.wgsl", wgsl);
    tint::Program program = tint::wgsl::reader::Parse(&file);
    if (!program.IsValid()) {
        result->diagnostics = program.Diagnostics().Str();
        return result;
    }

    tint::inspector::Inspector inspector(program);
    if (inspector.has_error()) {
        result->diagnostics = inspector.error();
        return result;
    }

    if (!CompileParsedProgram(program, inspector, options, result)) {
        return result;
    }
    result->status = 0;
    return result;
}

uint8_t* CopyOutput(const std::vector<uint8_t>& bytes) {
    if (bytes.empty()) {
        return nullptr;
    }
    auto* output = static_cast<uint8_t*>(std::malloc(bytes.size()));
    if (output != nullptr) {
        std::memcpy(output, bytes.data(), bytes.size());
    }
    return output;
}

char* CopyString(const std::string& value) {
    auto* output = static_cast<char*>(std::malloc(value.size() + 1));
    if (output != nullptr) {
        std::memcpy(output, value.c_str(), value.size() + 1);
    }
    return output;
}

}  // namespace

FDX_SHADERC_API int32_t fdx_shaderc_compile_wgsl(const char* source,
                                                 int32_t source_size,
                                                 const fdx_shaderc_options* options,
                                                 fdx_shaderc_result* result) {
    if (result == nullptr) {
        return 1;
    }
    fdx_shaderc_free_result(result);
    fdx_shaderc_options actual_options = {};
    if (options != nullptr) {
        actual_options = *options;
    }
    ResultHandle* handle = CompileHandle(source, source_size, actual_options);
    result->status = handle->status;
    result->output_kind = handle->output_kind;
    result->output_size = static_cast<int32_t>(handle->output.size());
    result->output = CopyOutput(handle->output);
    result->diagnostics = CopyString(handle->diagnostics);
    int32_t status = handle->status;
    delete handle;
    return status;
}

FDX_SHADERC_API void fdx_shaderc_free_result(fdx_shaderc_result* result) {
    if (result == nullptr) {
        return;
    }
    std::free(result->output);
    std::free(result->diagnostics);
    result->status = 0;
    result->output_kind = FDX_SHADERC_OUTPUT_NONE;
    result->output = nullptr;
    result->output_size = 0;
    result->diagnostics = nullptr;
}

FDX_SHADERC_API void* fdx_shaderc_compile_wgsl_handle(const char* source,
                                                       int32_t source_size,
                                                       int32_t target,
                                                       int32_t stage,
                                                       const char* entry_point,
                                                       const char* glsl_profile,
                                                       const char* glsl_es_profile) {
    fdx_shaderc_options options = {};
    options.target = target;
    options.stage = stage;
    options.entry_point = entry_point;
    options.glsl_profile = glsl_profile;
    options.glsl_es_profile = glsl_es_profile;
    return CompileHandle(source, source_size, options);
}

FDX_SHADERC_API int32_t fdx_shaderc_result_status(void* handle) {
    return handle != nullptr ? static_cast<ResultHandle*>(handle)->status : 1;
}

FDX_SHADERC_API int32_t fdx_shaderc_result_output_kind(void* handle) {
    return handle != nullptr ? static_cast<ResultHandle*>(handle)->output_kind : FDX_SHADERC_OUTPUT_NONE;
}

FDX_SHADERC_API const uint8_t* fdx_shaderc_result_output(void* handle) {
    if (handle == nullptr) {
        return nullptr;
    }
    const auto& output = static_cast<ResultHandle*>(handle)->output;
    return output.empty() ? nullptr : output.data();
}

FDX_SHADERC_API int32_t fdx_shaderc_result_output_size(void* handle) {
    if (handle == nullptr) {
        return 0;
    }
    return static_cast<int32_t>(static_cast<ResultHandle*>(handle)->output.size());
}

FDX_SHADERC_API const char* fdx_shaderc_result_diagnostics(void* handle) {
    if (handle == nullptr) {
        return "";
    }
    return static_cast<ResultHandle*>(handle)->diagnostics.c_str();
}

FDX_SHADERC_API void fdx_shaderc_result_free(void* handle) {
    delete static_cast<ResultHandle*>(handle);
}
