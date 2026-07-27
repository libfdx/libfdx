#include "fdx_shaderc.h"

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <limits>
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>
#include <tuple>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <variant>
#include <vector>

#include "src/tint/api/common/substitute_overrides_config.h"
#include "src/tint/api/helpers/generate_bindings.h"
#include "src/tint/api/tint.h"
#include "src/tint/lang/core/ir/referenced_module_vars.h"
#include "src/tint/lang/core/ir/transform/resource_table_helper.h"
#include "src/tint/lang/core/ir/var.h"
#include "src/tint/lang/core/type/array.h"
#include "src/tint/lang/core/type/atomic.h"
#include "src/tint/lang/core/type/binding_array.h"
#include "src/tint/lang/core/type/bool.h"
#include "src/tint/lang/core/type/buffer.h"
#include "src/tint/lang/core/type/f16.h"
#include "src/tint/lang/core/type/f32.h"
#include "src/tint/lang/core/type/i32.h"
#include "src/tint/lang/core/type/i8.h"
#include "src/tint/lang/core/type/matrix.h"
#include "src/tint/lang/core/type/memory_view.h"
#include "src/tint/lang/core/type/pointer.h"
#include "src/tint/lang/core/type/reference.h"
#include "src/tint/lang/core/type/struct.h"
#include "src/tint/lang/core/type/u32.h"
#include "src/tint/lang/core/type/u8.h"
#include "src/tint/lang/core/type/vector.h"
#include "src/tint/lang/glsl/writer/helpers/generate_bindings.h"
#include "src/tint/lang/glsl/writer/writer.h"
#include "src/tint/lang/hlsl/writer/writer.h"
#include "src/tint/lang/msl/writer/writer.h"
#include "src/tint/lang/spirv/writer/writer.h"
#include "src/tint/lang/wgsl/ast/builtin_attribute.h"
#include "src/tint/lang/wgsl/ast/identifier.h"
#include "src/tint/lang/wgsl/ast/module.h"
#include "src/tint/lang/wgsl/inspector/inspector.h"
#include "src/tint/lang/wgsl/reader/reader.h"
#include "src/tint/lang/wgsl/sem/function.h"
#include "src/tint/lang/wgsl/sem/variable.h"

namespace {

struct ResultHandle {
    int32_t status = 1;
    int32_t output_kind = FDX_SHADERC_OUTPUT_NONE;
    std::vector<uint8_t> output;
    std::string diagnostics;
    std::vector<uint8_t> reflection;
    std::vector<uint8_t> target_interface;
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

constexpr uint32_t kReflectionSchemaVersion = 1;
constexpr uint32_t kAbsentU32 = std::numeric_limits<uint32_t>::max();

enum ReflectionStage : uint32_t {
    kReflectionStageVertex = 1,
    kReflectionStageFragment = 2,
    kReflectionStageCompute = 3,
};

enum ReflectionResourceKind : uint32_t {
    kReflectionUniformBuffer = 1,
    kReflectionStorageBuffer = 2,
    kReflectionSampler = 3,
    kReflectionSampledTexture = 4,
    kReflectionMultisampledTexture = 5,
    kReflectionStorageTexture = 6,
    kReflectionDepthTexture = 7,
    kReflectionDepthMultisampledTexture = 8,
    kReflectionExternalTexture = 9,
    kReflectionTexelBuffer = 10,
    kReflectionInputAttachment = 11,
};

enum ReflectionAccess : uint32_t {
    kReflectionAccessNone = 0,
    kReflectionAccessRead = 1,
    kReflectionAccessWrite = 2,
    kReflectionAccessReadWrite = 3,
};

enum ReflectionValueKind : uint32_t {
    kReflectionValueUnknown = 0,
    kReflectionValueScalar = 1,
    kReflectionValueVector = 2,
    kReflectionValueMatrix = 3,
    kReflectionValueArray = 4,
    kReflectionValueStruct = 5,
    kReflectionValueAtomic = 6,
    kReflectionValueBuffer = 7,
};

enum ReflectionScalarKind : uint32_t {
    kReflectionScalarNone = 0,
    kReflectionScalarBool = 1,
    kReflectionScalarF16 = 2,
    kReflectionScalarF32 = 3,
    kReflectionScalarI32 = 4,
    kReflectionScalarU32 = 5,
    kReflectionScalarI8 = 6,
    kReflectionScalarU8 = 7,
};

class BinaryWriter {
  public:
    void WriteMagic(const std::array<uint8_t, 4>& magic) {
        bytes_.insert(bytes_.end(), magic.begin(), magic.end());
    }

    void WriteU32(uint32_t value) {
        bytes_.push_back(static_cast<uint8_t>(value & 0xffu));
        bytes_.push_back(static_cast<uint8_t>((value >> 8u) & 0xffu));
        bytes_.push_back(static_cast<uint8_t>((value >> 16u) & 0xffu));
        bytes_.push_back(static_cast<uint8_t>((value >> 24u) & 0xffu));
    }

    void WriteU64(uint64_t value) {
        WriteU32(static_cast<uint32_t>(value & 0xffffffffull));
        WriteU32(static_cast<uint32_t>((value >> 32u) & 0xffffffffull));
    }

    void WriteCount(size_t count, std::string_view label) {
        if (count > std::numeric_limits<uint32_t>::max()) {
            throw std::runtime_error(std::string(label) + " exceeds the FDXI u32 limit");
        }
        WriteU32(static_cast<uint32_t>(count));
    }

    void WriteString(std::string_view value) {
        WriteCount(value.size(), "FDXI string length");
        bytes_.insert(bytes_.end(), value.begin(), value.end());
    }

    std::vector<uint8_t> Take() {
        return std::move(bytes_);
    }

  private:
    std::vector<uint8_t> bytes_;
};

constexpr uint32_t kTargetInterfaceSchemaVersion = 1;

enum TargetBindingRemapKind : uint32_t {
    kTargetBindingDirect = 0,
    kTargetBindingCombinedTexture = 1,
    kTargetBindingCombinedSampler = 2,
};

struct TargetBindingSlot {
    std::string resource_namespace;
    tint::BindingPoint target;
    std::string role;
    std::string name;
};

struct TargetBindingRemap {
    tint::BindingPoint source;
    uint32_t kind = kTargetBindingDirect;
    std::vector<TargetBindingSlot> targets;
};

bool BindingPointLess(const tint::BindingPoint& left, const tint::BindingPoint& right) {
    return std::tie(left.group, left.binding) < std::tie(right.group, right.binding);
}

void AppendTargetBindingMap(std::vector<TargetBindingRemap>* remaps,
                            const tint::BindingMap& bindings,
                            std::string_view resource_namespace,
                            const std::unordered_set<tint::BindingPoint>* combined_sources,
                            const std::unordered_set<tint::BindingPoint>* combined_targets,
                            uint32_t combined_kind) {
    for (const auto& [source, target] : bindings) {
        uint32_t kind = kTargetBindingDirect;
        if ((combined_sources != nullptr && combined_sources->count(source) != 0) ||
            (combined_targets != nullptr && combined_targets->count(target) != 0)) {
            kind = combined_kind;
        }
        remaps->push_back(TargetBindingRemap{
            .source = source,
            .kind = kind,
            .targets = {TargetBindingSlot{
                .resource_namespace = std::string(resource_namespace),
                .target = target,
                .role = "resource",
                .name = "",
            }},
        });
    }
}

void AppendExternalTargetBindings(std::vector<TargetBindingRemap>* remaps,
                                  const tint::ExternalTextureBindings& bindings) {
    for (const auto& [source, target] : bindings) {
        TargetBindingRemap remap;
        remap.source = source;
        if (const auto* multiplanar = std::get_if<tint::ExternalMultiplanarTexture>(&target)) {
            remap.targets = {
                TargetBindingSlot{"buffer", multiplanar->metadata, "metadata", ""},
                TargetBindingSlot{"texture", multiplanar->plane0, "plane0", ""},
                TargetBindingSlot{"texture", multiplanar->plane1, "plane1", ""},
            };
        } else if (const auto* ycbcr = std::get_if<tint::ExternalYCBCRTexture>(&target)) {
            remap.targets = {
                TargetBindingSlot{"buffer", ycbcr->metadata, "metadata", ""},
                TargetBindingSlot{"texture", ycbcr->texture, "texture", ""},
                TargetBindingSlot{"sampler", ycbcr->sampler, "sampler", ""},
            };
        } else {
            throw std::runtime_error("Tint returned an unknown external-texture binding mapping");
        }
        remaps->push_back(std::move(remap));
    }
}

std::vector<uint8_t> BuildTargetInterface(
        const tint::Bindings& bindings,
        int32_t stage,
        const std::string& entry_point,
        const std::unordered_set<tint::BindingPoint>& combined_textures = {},
        const std::unordered_set<tint::BindingPoint>& combined_slots = {}) {
    std::vector<TargetBindingRemap> remaps;
    AppendTargetBindingMap(&remaps, bindings.uniform, "buffer", nullptr, nullptr,
                           kTargetBindingDirect);
    AppendTargetBindingMap(&remaps, bindings.storage, "buffer", nullptr, nullptr,
                           kTargetBindingDirect);
    AppendTargetBindingMap(&remaps, bindings.texture, "texture", &combined_textures, nullptr,
                           kTargetBindingCombinedTexture);
    AppendTargetBindingMap(&remaps, bindings.storage_texture, "storage-texture", nullptr, nullptr,
                           kTargetBindingDirect);
    AppendTargetBindingMap(&remaps, bindings.texel_buffer, "texel-buffer", nullptr, nullptr,
                           kTargetBindingDirect);
    AppendTargetBindingMap(&remaps, bindings.sampler, "sampler", nullptr, &combined_slots,
                           kTargetBindingCombinedSampler);
    AppendExternalTargetBindings(&remaps, bindings.external_texture);
    AppendTargetBindingMap(&remaps, bindings.input_attachment, "input-attachment", nullptr, nullptr,
                           kTargetBindingDirect);

    std::sort(remaps.begin(), remaps.end(), [](const auto& left, const auto& right) {
        return BindingPointLess(left.source, right.source);
    });
    for (size_t i = 1; i < remaps.size(); i++) {
        if (remaps[i - 1].source == remaps[i].source) {
            throw std::runtime_error("Tint target bindings contain a duplicate source binding");
        }
    }
    for (auto& remap : remaps) {
        std::sort(remap.targets.begin(), remap.targets.end(), [](const auto& left, const auto& right) {
            return std::tie(left.resource_namespace, left.target.group, left.target.binding, left.role) <
                   std::tie(right.resource_namespace, right.target.group, right.target.binding, right.role);
        });
    }

    BinaryWriter writer;
    writer.WriteMagic({'F', 'D', 'X', 'T'});
    writer.WriteU32(kTargetInterfaceSchemaVersion);
    writer.WriteU32(1);
    writer.WriteU32(static_cast<uint32_t>(stage));
    writer.WriteString(entry_point);
    writer.WriteString(entry_point);
    writer.WriteCount(remaps.size(), "FDXT binding-remap count");
    for (const auto& remap : remaps) {
        writer.WriteU32(remap.source.group);
        writer.WriteU32(remap.source.binding);
        writer.WriteU32(remap.kind);
        writer.WriteCount(remap.targets.size(), "FDXT target binding count");
        for (const auto& target : remap.targets) {
            writer.WriteString(target.resource_namespace);
            writer.WriteU32(target.target.group);
            writer.WriteU32(target.target.binding);
            writer.WriteString(target.role);
            writer.WriteString(target.name);
        }
    }
    return writer.Take();
}

uint32_t StageTag(tint::inspector::PipelineStage stage) {
    switch (stage) {
        case tint::inspector::PipelineStage::kVertex:
            return kReflectionStageVertex;
        case tint::inspector::PipelineStage::kFragment:
            return kReflectionStageFragment;
        case tint::inspector::PipelineStage::kCompute:
            return kReflectionStageCompute;
    }
    throw std::runtime_error("Tint returned an unknown pipeline stage");
}

uint32_t StageVisibility(uint32_t stage) {
    switch (stage) {
        case kReflectionStageVertex:
            return 1u;
        case kReflectionStageFragment:
            return 2u;
        case kReflectionStageCompute:
            return 4u;
        default:
            throw std::runtime_error("Cannot create visibility for an unknown shader stage");
    }
}

uint32_t ComponentTypeTag(tint::inspector::ComponentType type) {
    switch (type) {
        case tint::inspector::ComponentType::kUnknown:
            return 0;
        case tint::inspector::ComponentType::kF32:
            return 1;
        case tint::inspector::ComponentType::kU32:
            return 2;
        case tint::inspector::ComponentType::kI32:
            return 3;
        case tint::inspector::ComponentType::kF16:
            return 4;
    }
    throw std::runtime_error("Tint returned an unknown stage IO component type");
}

uint32_t CompositionTypeTag(tint::inspector::CompositionType type) {
    switch (type) {
        case tint::inspector::CompositionType::kUnknown:
            return 0;
        case tint::inspector::CompositionType::kScalar:
            return 1;
        case tint::inspector::CompositionType::kVec2:
            return 2;
        case tint::inspector::CompositionType::kVec3:
            return 3;
        case tint::inspector::CompositionType::kVec4:
            return 4;
    }
    throw std::runtime_error("Tint returned an unknown stage IO composition type");
}

uint32_t InterpolationTypeTag(tint::inspector::InterpolationType type) {
    switch (type) {
        case tint::inspector::InterpolationType::kUnknown:
            return 0;
        case tint::inspector::InterpolationType::kPerspective:
            return 1;
        case tint::inspector::InterpolationType::kLinear:
            return 2;
        case tint::inspector::InterpolationType::kFlat:
            return 3;
    }
    throw std::runtime_error("Tint returned an unknown interpolation type");
}

uint32_t InterpolationSamplingTag(tint::inspector::InterpolationSampling sampling) {
    switch (sampling) {
        case tint::inspector::InterpolationSampling::kUnknown:
            return 0;
        case tint::inspector::InterpolationSampling::kNone:
            return 1;
        case tint::inspector::InterpolationSampling::kCenter:
            return 2;
        case tint::inspector::InterpolationSampling::kCentroid:
            return 3;
        case tint::inspector::InterpolationSampling::kSample:
            return 4;
        case tint::inspector::InterpolationSampling::kFirst:
            return 5;
        case tint::inspector::InterpolationSampling::kEither:
            return 6;
    }
    throw std::runtime_error("Tint returned an unknown interpolation sampling mode");
}

uint32_t OverrideTypeTag(tint::inspector::Override::Type type) {
    switch (type) {
        case tint::inspector::Override::Type::kBool:
            return 1;
        case tint::inspector::Override::Type::kFloat32:
            return 2;
        case tint::inspector::Override::Type::kUint32:
            return 3;
        case tint::inspector::Override::Type::kInt32:
            return 4;
        case tint::inspector::Override::Type::kFloat16:
            return 5;
    }
    throw std::runtime_error("Tint returned an unknown override type");
}

uint32_t ResourceKindTag(tint::inspector::ResourceBinding::ResourceType type) {
    using Type = tint::inspector::ResourceBinding::ResourceType;
    switch (type) {
        case Type::kUniformBuffer:
            return kReflectionUniformBuffer;
        case Type::kStorageBuffer:
        case Type::kReadOnlyStorageBuffer:
            return kReflectionStorageBuffer;
        case Type::kSampler:
            return kReflectionSampler;
        case Type::kSampledTexture:
            return kReflectionSampledTexture;
        case Type::kMultisampledTexture:
            return kReflectionMultisampledTexture;
        case Type::kWriteOnlyStorageTexture:
        case Type::kReadOnlyStorageTexture:
        case Type::kReadWriteStorageTexture:
            return kReflectionStorageTexture;
        case Type::kDepthTexture:
            return kReflectionDepthTexture;
        case Type::kDepthMultisampledTexture:
            return kReflectionDepthMultisampledTexture;
        case Type::kExternalTexture:
            return kReflectionExternalTexture;
        case Type::kReadOnlyTexelBuffer:
        case Type::kReadWriteTexelBuffer:
            return kReflectionTexelBuffer;
        case Type::kInputAttachment:
            return kReflectionInputAttachment;
    }
    throw std::runtime_error("Tint returned an unknown resource type");
}

uint32_t ResourceAccessTag(tint::inspector::ResourceBinding::ResourceType type) {
    using Type = tint::inspector::ResourceBinding::ResourceType;
    switch (type) {
        case Type::kSampler:
            return kReflectionAccessNone;
        case Type::kStorageBuffer:
        case Type::kReadWriteStorageTexture:
        case Type::kReadWriteTexelBuffer:
            return kReflectionAccessReadWrite;
        case Type::kWriteOnlyStorageTexture:
            return kReflectionAccessWrite;
        case Type::kUniformBuffer:
        case Type::kReadOnlyStorageBuffer:
        case Type::kSampledTexture:
        case Type::kMultisampledTexture:
        case Type::kReadOnlyStorageTexture:
        case Type::kDepthTexture:
        case Type::kDepthMultisampledTexture:
        case Type::kExternalTexture:
        case Type::kReadOnlyTexelBuffer:
        case Type::kInputAttachment:
            return kReflectionAccessRead;
    }
    throw std::runtime_error("Tint returned an unknown resource access type");
}

uint32_t TextureDimensionTag(tint::inspector::ResourceBinding::TextureDimension dimension) {
    using Dimension = tint::inspector::ResourceBinding::TextureDimension;
    switch (dimension) {
        case Dimension::kNone:
            return 0;
        case Dimension::k1d:
            return 1;
        case Dimension::k2d:
            return 2;
        case Dimension::k2dArray:
            return 3;
        case Dimension::k3d:
            return 4;
        case Dimension::kCube:
            return 5;
        case Dimension::kCubeArray:
            return 6;
    }
    throw std::runtime_error("Tint returned an unknown texture dimension");
}

uint32_t SampledKindTag(tint::inspector::ResourceBinding::SampledKind kind) {
    using Kind = tint::inspector::ResourceBinding::SampledKind;
    switch (kind) {
        case Kind::kFloat:
            return 1;
        case Kind::kUInt:
            return 2;
        case Kind::kSInt:
            return 3;
        case Kind::kFilterable:
            return 4;
        case Kind::kUnfilterable:
            return 5;
        case Kind::kUnknownFilterable:
            return 6;
    }
    throw std::runtime_error("Tint returned an unknown sampled texture kind");
}

uint32_t SamplerKindTag(tint::inspector::ResourceBinding::SamplerType type) {
    using Type = tint::inspector::ResourceBinding::SamplerType;
    switch (type) {
        case Type::kComparison:
            return 1;
        case Type::kFiltering:
            return 2;
        case Type::kNonFiltering:
            return 3;
        case Type::kUnknownFiltering:
            return 4;
    }
    throw std::runtime_error("Tint returned an unknown sampler kind");
}

uint32_t StorageFormatTag(tint::inspector::ResourceBinding::TexelFormat format) {
    using Format = tint::inspector::ResourceBinding::TexelFormat;
    switch (format) {
        case Format::kNone:
            return 0;
        case Format::kR8Snorm:
            return 1;
        case Format::kR8Uint:
            return 2;
        case Format::kR8Sint:
            return 3;
        case Format::kRg8Unorm:
            return 4;
        case Format::kRg8Snorm:
            return 5;
        case Format::kRg8Uint:
            return 6;
        case Format::kRg8Sint:
            return 7;
        case Format::kR16Unorm:
            return 8;
        case Format::kR16Snorm:
            return 9;
        case Format::kR16Uint:
            return 10;
        case Format::kR16Sint:
            return 11;
        case Format::kR16Float:
            return 12;
        case Format::kRg16Unorm:
            return 13;
        case Format::kRg16Snorm:
            return 14;
        case Format::kRg16Uint:
            return 15;
        case Format::kRg16Sint:
            return 16;
        case Format::kRg16Float:
            return 17;
        case Format::kBgra8Unorm:
            return 18;
        case Format::kRgba8Unorm:
            return 19;
        case Format::kRgba8Snorm:
            return 20;
        case Format::kRgba8Uint:
            return 21;
        case Format::kRgba8Sint:
            return 22;
        case Format::kRgba16Unorm:
            return 23;
        case Format::kRgba16Snorm:
            return 24;
        case Format::kRgba16Uint:
            return 25;
        case Format::kRgba16Sint:
            return 26;
        case Format::kRgba16Float:
            return 27;
        case Format::kR32Uint:
            return 28;
        case Format::kR32Sint:
            return 29;
        case Format::kR32Float:
            return 30;
        case Format::kRg32Uint:
            return 31;
        case Format::kRg32Sint:
            return 32;
        case Format::kRg32Float:
            return 33;
        case Format::kRgba32Uint:
            return 34;
        case Format::kRgba32Sint:
            return 35;
        case Format::kRgba32Float:
            return 36;
        case Format::kR8Unorm:
            return 37;
        case Format::kRgb10A2Uint:
            return 38;
        case Format::kRgb10A2Unorm:
            return 39;
        case Format::kRg11B10Ufloat:
            return 40;
    }
    throw std::runtime_error("Tint returned an unknown storage texture format");
}

bool HasTextureDimension(uint32_t kind) {
    return kind == kReflectionSampledTexture || kind == kReflectionMultisampledTexture ||
           kind == kReflectionStorageTexture || kind == kReflectionDepthTexture ||
           kind == kReflectionDepthMultisampledTexture || kind == kReflectionExternalTexture ||
           kind == kReflectionTexelBuffer || kind == kReflectionInputAttachment;
}

bool HasSampledKind(uint32_t kind) {
    return kind == kReflectionSampledTexture || kind == kReflectionMultisampledTexture ||
           kind == kReflectionTexelBuffer || kind == kReflectionInputAttachment;
}

bool IsBufferResource(uint32_t kind) {
    return kind == kReflectionUniformBuffer || kind == kReflectionStorageBuffer;
}

uint32_t ScalarKind(const tint::core::type::Type* type) {
    using namespace tint::core::type;
    if (type == nullptr) {
        return kReflectionScalarNone;
    }
    if (type->Is<Bool>()) {
        return kReflectionScalarBool;
    }
    if (type->Is<F16>()) {
        return kReflectionScalarF16;
    }
    if (type->Is<F32>()) {
        return kReflectionScalarF32;
    }
    if (type->Is<I32>()) {
        return kReflectionScalarI32;
    }
    if (type->Is<U32>()) {
        return kReflectionScalarU32;
    }
    if (type->Is<I8>()) {
        return kReflectionScalarI8;
    }
    if (type->Is<U8>()) {
        return kReflectionScalarU8;
    }
    return kReflectionScalarNone;
}

struct ValueTypeRecord {
    uint32_t value_kind = kReflectionValueUnknown;
    uint32_t scalar_kind = kReflectionScalarNone;
    uint32_t rows = 0;
    uint32_t columns = 0;
    uint32_t array_count = 0;
    uint64_t array_stride = 0;
    uint64_t matrix_stride = 0;
    std::string type_name;
    std::vector<ValueTypeRecord> children;
};

struct MemberRecord {
    std::string path;
    uint32_t parent_index = kAbsentU32;
    uint64_t absolute_offset = 0;
    uint64_t size = 0;
    uint64_t alignment = 0;
    uint64_t minimum_size = 0;
    ValueTypeRecord value_type;
};

ValueTypeRecord DescribeValueType(const tint::core::type::Type* type,
                                  std::optional<uint64_t> matrix_stride_override = std::nullopt) {
    using namespace tint::core::type;
    ValueTypeRecord record;
    if (type == nullptr) {
        return record;
    }

    record.type_name = type->FriendlyName();
    if (auto* array = type->As<Array>()) {
        record.value_kind = kReflectionValueArray;
        record.array_count = array->ConstantCount().value_or(kAbsentU32);
        record.array_stride = array->ImplicitStride();
        record.children.push_back(DescribeValueType(array->ElemType(), matrix_stride_override));
        return record;
    }
    if (auto* matrix = type->As<Matrix>()) {
        record.value_kind = kReflectionValueMatrix;
        record.scalar_kind = ScalarKind(matrix->Type());
        record.rows = matrix->Rows();
        record.columns = matrix->Columns();
        record.matrix_stride = matrix_stride_override.value_or(matrix->ColumnStride());
        return record;
    }
    if (auto* vector = type->As<Vector>()) {
        record.value_kind = kReflectionValueVector;
        record.scalar_kind = ScalarKind(vector->Type());
        record.rows = 1;
        record.columns = vector->Width();
        return record;
    }
    if (auto* atomic = type->As<Atomic>()) {
        record.value_kind = kReflectionValueAtomic;
        record.scalar_kind = ScalarKind(atomic->Type());
        record.rows = 1;
        record.columns = 1;
        return record;
    }
    if (type->Is<Struct>()) {
        record.value_kind = kReflectionValueStruct;
        return record;
    }
    if (type->Is<Buffer>()) {
        record.value_kind = kReflectionValueBuffer;
        return record;
    }

    record.scalar_kind = ScalarKind(type);
    if (record.scalar_kind != kReflectionScalarNone) {
        record.value_kind = kReflectionValueScalar;
        record.rows = 1;
        record.columns = 1;
    }
    return record;
}

const tint::core::type::Struct* NestedStructType(const tint::core::type::Type* type,
                                                 std::string* path_suffix) {
    using namespace tint::core::type;
    const Type* current = type;
    while (auto* array = current->As<Array>()) {
        path_suffix->append("[]");
        current = array->ElemType();
    }
    return current->As<Struct>();
}

void AppendStructMembers(const tint::core::type::Struct* type,
                         const std::string& prefix,
                         uint64_t base_offset,
                         uint32_t parent_index,
                         std::vector<MemberRecord>* records) {
    if (type == nullptr) {
        return;
    }
    for (const auto* member : type->Members()) {
        if (records->size() >= kAbsentU32) {
            throw std::runtime_error("FDXI buffer member count exceeds the u32 limit");
        }
        std::string member_name(member->Name().NameView());
        std::string path = prefix.empty() ? member_name : prefix + "." + member_name;
        MemberRecord record;
        record.path = path;
        record.parent_index = parent_index;
        record.absolute_offset = base_offset + member->Offset();
        record.size = member->Size();
        record.alignment = member->Align();
        record.minimum_size = member->MinimumRequiredSize();
        record.value_type =
            DescribeValueType(member->Type(), member->HasMatrixStride()
                                                   ? std::optional<uint64_t>(member->MatrixStride())
                                                   : std::nullopt);
        uint32_t member_index = static_cast<uint32_t>(records->size());
        records->push_back(std::move(record));

        std::string suffix;
        if (const auto* child_struct = NestedStructType(member->Type(), &suffix)) {
            AppendStructMembers(child_struct, path + suffix, base_offset + member->Offset(),
                                member_index, records);
        }
    }
}

const tint::core::type::Type* UnwrapResourceType(const tint::core::type::Type* type) {
    using namespace tint::core::type;
    const Type* result = type;
    if (auto* view = result != nullptr ? result->As<MemoryView>() : nullptr) {
        result = view->StoreType();
    }
    if (auto* binding_array = result != nullptr ? result->As<BindingArray>() : nullptr) {
        result = binding_array->ElemType();
    }
    return result;
}

uint64_t BindingKey(uint32_t group, uint32_t binding) {
    return (static_cast<uint64_t>(group) << 32u) | binding;
}

std::map<uint64_t, const tint::core::type::Type*> BoundResourceTypes(
    const tint::Program& program) {
    std::map<uint64_t, const tint::core::type::Type*> types;
    for (const auto* variable : program.AST().GlobalVariables()) {
        const auto* semantic = program.Sem().Get(variable);
        const auto* global = semantic != nullptr ? semantic->As<tint::sem::GlobalVariable>() : nullptr;
        if (global == nullptr || !global->Attributes().binding_point.has_value()) {
            continue;
        }
        const auto point = *global->Attributes().binding_point;
        const auto* type = UnwrapResourceType(global->Type());
        auto inserted = types.emplace(BindingKey(point.group, point.binding), type);
        if (!inserted.second && inserted.first->second != type) {
            throw std::runtime_error("WGSL has conflicting semantic resource binding types");
        }
    }
    return types;
}

struct IoRecord {
    std::string name;
    std::string variable_name;
    uint32_t location = kAbsentU32;
    uint32_t color = kAbsentU32;
    uint32_t blend_src = kAbsentU32;
    uint32_t component_type = 0;
    uint32_t composition_type = 0;
    uint32_t interpolation_type = 0;
    uint32_t interpolation_sampling = 0;
};

IoRecord MakeIoRecord(const tint::inspector::StageVariable& variable) {
    IoRecord result;
    result.name = variable.name;
    result.variable_name = variable.variable_name;
    result.location = variable.attributes.location.value_or(kAbsentU32);
    result.color = variable.attributes.color.value_or(kAbsentU32);
    result.blend_src = variable.attributes.blend_src.value_or(kAbsentU32);
    result.component_type = ComponentTypeTag(variable.component_type);
    result.composition_type = CompositionTypeTag(variable.composition_type);
    result.interpolation_type = InterpolationTypeTag(variable.interpolation_type);
    result.interpolation_sampling = InterpolationSamplingTag(variable.interpolation_sampling);
    return result;
}

bool IoLess(const IoRecord& left, const IoRecord& right) {
    return std::tie(left.location, left.color, left.blend_src, left.name, left.variable_name) <
           std::tie(right.location, right.color, right.blend_src, right.name, right.variable_name);
}

struct OverrideRecord {
    std::string name;
    uint32_t id = 0;
    uint32_t type = 0;
    uint32_t initialized = 0;
    uint32_t explicit_id = 0;
};

struct EntryBindingReference {
    uint32_t group = 0;
    uint32_t binding = 0;
    uint64_t minimum_size = 0;
};

struct EntryRecord {
    std::string name;
    uint32_t stage = 0;
    uint32_t workgroup_kind = 0;
    uint32_t workgroup_x = 0;
    uint32_t workgroup_y = 0;
    uint32_t workgroup_z = 0;
    uint64_t builtin_mask = 0;
    uint32_t clip_distances_size = kAbsentU32;
    std::vector<IoRecord> inputs;
    std::vector<IoRecord> outputs;
    std::vector<OverrideRecord> overrides;
    std::vector<EntryBindingReference> bindings;
};

uint64_t BuiltinBit(tint::core::BuiltinValue builtin, bool input) {
    using Builtin = tint::core::BuiltinValue;
    switch (builtin) {
        case Builtin::kUndefined:
            return 0;
        case Builtin::kCullDistance:
            return 1ull << 0u;
        case Builtin::kPointSize:
            return 1ull << 1u;
        case Builtin::kBarycentricCoord:
            return 1ull << 2u;
        case Builtin::kClipDistances:
            return 1ull << 3u;
        case Builtin::kFragDepth:
            return 1ull << 4u;
        case Builtin::kFrontFacing:
            return 1ull << 5u;
        case Builtin::kGlobalInvocationId:
            return 1ull << 6u;
        case Builtin::kGlobalInvocationIndex:
            return 1ull << 7u;
        case Builtin::kInstanceIndex:
            return 1ull << 8u;
        case Builtin::kLocalInvocationId:
            return 1ull << 9u;
        case Builtin::kLocalInvocationIndex:
            return 1ull << 10u;
        case Builtin::kNumSubgroups:
            return 1ull << 11u;
        case Builtin::kNumWorkgroups:
            return 1ull << 12u;
        case Builtin::kPosition:
            return 1ull << 13u;
        case Builtin::kPrimitiveIndex:
            return 1ull << 14u;
        case Builtin::kSampleIndex:
            return 1ull << 15u;
        case Builtin::kSampleMask:
            return input ? 1ull << 16u : 1ull << 17u;
        case Builtin::kSubgroupId:
            return 1ull << 18u;
        case Builtin::kSubgroupInvocationId:
            return 1ull << 19u;
        case Builtin::kSubgroupSize:
            return 1ull << 20u;
        case Builtin::kVertexIndex:
            return 1ull << 21u;
        case Builtin::kWorkgroupId:
            return 1ull << 22u;
        case Builtin::kWorkgroupIndex:
            return 1ull << 23u;
    }
    throw std::runtime_error("Tint returned an unknown builtin shader IO value");
}

uint64_t TypeBuiltinMask(const tint::core::type::Type* type, bool input) {
    const auto* structure = type != nullptr ? type->As<tint::core::type::Struct>() : nullptr;
    if (structure == nullptr) {
        return 0;
    }
    uint64_t result = 0;
    for (const auto* member : structure->Members()) {
        if (member->Attributes().builtin.has_value()) {
            result |= BuiltinBit(*member->Attributes().builtin, input);
        }
        result |= TypeBuiltinMask(member->Type(), input);
    }
    return result;
}

uint64_t SemanticBuiltinMask(const tint::Program& program, std::string_view entry_name) {
    for (const auto* function : program.AST().Functions()) {
        if (function->name->symbol.NameView() != entry_name) {
            continue;
        }
        uint64_t result = 0;
        for (const auto* parameter : function->params) {
            if (const auto* builtin =
                    tint::ast::GetAttribute<tint::ast::BuiltinAttribute>(parameter->attributes)) {
                result |= BuiltinBit(builtin->builtin, true);
            }
            const auto* semantic_parameter = program.Sem().Get(parameter);
            if (semantic_parameter != nullptr) {
                result |= TypeBuiltinMask(semantic_parameter->Type(), true);
            }
        }
        if (const auto* builtin = tint::ast::GetAttribute<tint::ast::BuiltinAttribute>(
                function->return_type_attributes)) {
            result |= BuiltinBit(builtin->builtin, false);
        }
        const auto* semantic_function = program.Sem().Get(function);
        if (semantic_function != nullptr) {
            result |= TypeBuiltinMask(semantic_function->ReturnType(), false);
        }
        return result;
    }
    throw std::runtime_error("Tint entry point AST declaration was not found");
}

EntryRecord MakeEntryRecord(const tint::Program& program,
                            const tint::inspector::EntryPoint& entry) {
    EntryRecord result;
    result.name = entry.name;
    result.stage = StageTag(entry.stage);
    if (entry.stage == tint::inspector::PipelineStage::kCompute) {
        if (entry.workgroup_size.has_value()) {
            result.workgroup_kind = 1;
            result.workgroup_x = entry.workgroup_size->x;
            result.workgroup_y = entry.workgroup_size->y;
            result.workgroup_z = entry.workgroup_size->z;
        } else {
            result.workgroup_kind = 2;
        }
    }
    result.builtin_mask = SemanticBuiltinMask(program, entry.name);
    result.clip_distances_size = entry.clip_distances_size.value_or(kAbsentU32);
    result.inputs.reserve(entry.input_variables.size());
    for (const auto& input : entry.input_variables) {
        result.inputs.push_back(MakeIoRecord(input));
    }
    std::sort(result.inputs.begin(), result.inputs.end(), IoLess);
    result.outputs.reserve(entry.output_variables.size());
    for (const auto& output : entry.output_variables) {
        result.outputs.push_back(MakeIoRecord(output));
    }
    std::sort(result.outputs.begin(), result.outputs.end(), IoLess);
    result.overrides.reserve(entry.overrides.size());
    for (const auto& override_value : entry.overrides) {
        result.overrides.push_back({
            .name = override_value.name,
            .id = override_value.id.value,
            .type = OverrideTypeTag(override_value.type),
            .initialized = override_value.is_initialized ? 1u : 0u,
            .explicit_id = override_value.is_id_specified ? 1u : 0u,
        });
    }
    std::sort(result.overrides.begin(), result.overrides.end(),
              [](const OverrideRecord& left, const OverrideRecord& right) {
                  return std::tie(left.id, left.name) < std::tie(right.id, right.name);
              });
    return result;
}

struct ResourceRecord {
    uint32_t group = 0;
    uint32_t binding = 0;
    std::string name;
    tint::inspector::ResourceBinding::ResourceType tint_type =
        tint::inspector::ResourceBinding::ResourceType::kUniformBuffer;
    uint32_t kind = 0;
    uint32_t access = 0;
    uint32_t visibility = 0;
    uint32_t binding_array_count = kAbsentU32;
    uint64_t minimum_size = 0;
    uint64_t size_no_padding = 0;
    uint64_t alignment = 0;
    uint32_t texture_dimension = 0;
    uint32_t sampled_kind = 0;
    uint32_t sampler_kind = 0;
    uint32_t storage_format = 0;
    uint32_t input_attachment_index = kAbsentU32;
    std::vector<MemberRecord> members;
};

bool SameResourceMetadata(const ResourceRecord& current,
                          const tint::inspector::ResourceBinding& incoming) {
    return current.name == incoming.variable_name && current.tint_type == incoming.resource_type &&
           current.binding_array_count == incoming.array_size.value_or(kAbsentU32) &&
           current.size_no_padding == incoming.size_no_padding &&
           current.texture_dimension ==
               (HasTextureDimension(current.kind) ? TextureDimensionTag(incoming.dim) : 0) &&
           current.sampled_kind ==
               (HasSampledKind(current.kind) ? SampledKindTag(incoming.sampled_kind) : 0) &&
           current.sampler_kind ==
               (current.kind == kReflectionSampler ? SamplerKindTag(incoming.sampler_type) : 0) &&
           current.storage_format == StorageFormatTag(incoming.image_format) &&
           current.input_attachment_index ==
               (current.kind == kReflectionInputAttachment ? incoming.input_attachment_index
                                                            : kAbsentU32);
}

ResourceRecord MakeResourceRecord(const tint::inspector::ResourceBinding& binding,
                                  uint32_t visibility) {
    ResourceRecord result;
    result.group = binding.bind_group;
    result.binding = binding.binding;
    result.name = binding.variable_name;
    result.tint_type = binding.resource_type;
    result.kind = ResourceKindTag(binding.resource_type);
    result.access = ResourceAccessTag(binding.resource_type);
    result.visibility = visibility;
    result.binding_array_count = binding.array_size.value_or(kAbsentU32);
    result.minimum_size = binding.size;
    result.size_no_padding = binding.size_no_padding;
    result.texture_dimension =
        HasTextureDimension(result.kind) ? TextureDimensionTag(binding.dim) : 0;
    result.sampled_kind =
        HasSampledKind(result.kind) ? SampledKindTag(binding.sampled_kind) : 0;
    result.sampler_kind =
        result.kind == kReflectionSampler ? SamplerKindTag(binding.sampler_type) : 0;
    result.storage_format = StorageFormatTag(binding.image_format);
    result.input_attachment_index =
        result.kind == kReflectionInputAttachment ? binding.input_attachment_index : kAbsentU32;
    return result;
}

void WriteIo(BinaryWriter* writer, const IoRecord& io) {
    writer->WriteString(io.name);
    writer->WriteString(io.variable_name);
    writer->WriteU32(io.location);
    writer->WriteU32(io.color);
    writer->WriteU32(io.blend_src);
    writer->WriteU32(io.component_type);
    writer->WriteU32(io.composition_type);
    writer->WriteU32(io.interpolation_type);
    writer->WriteU32(io.interpolation_sampling);
}

void WriteEntry(BinaryWriter* writer, const EntryRecord& entry) {
    writer->WriteString(entry.name);
    writer->WriteU32(entry.stage);
    writer->WriteU32(entry.workgroup_kind);
    writer->WriteU32(entry.workgroup_x);
    writer->WriteU32(entry.workgroup_y);
    writer->WriteU32(entry.workgroup_z);
    writer->WriteU64(entry.builtin_mask);
    writer->WriteU32(entry.clip_distances_size);
    writer->WriteCount(entry.inputs.size(), "FDXI entry input count");
    for (const auto& input : entry.inputs) {
        WriteIo(writer, input);
    }
    writer->WriteCount(entry.outputs.size(), "FDXI entry output count");
    for (const auto& output : entry.outputs) {
        WriteIo(writer, output);
    }
    writer->WriteCount(entry.overrides.size(), "FDXI entry override count");
    for (const auto& override_value : entry.overrides) {
        writer->WriteString(override_value.name);
        writer->WriteU32(override_value.id);
        writer->WriteU32(override_value.type);
        writer->WriteU32(override_value.initialized);
        writer->WriteU32(override_value.explicit_id);
    }
    writer->WriteCount(entry.bindings.size(), "FDXI entry binding count");
    for (const auto& binding : entry.bindings) {
        writer->WriteU32(binding.group);
        writer->WriteU32(binding.binding);
        writer->WriteU64(binding.minimum_size);
    }
}

void WriteValueType(BinaryWriter* writer, const ValueTypeRecord& value_type) {
    writer->WriteU32(value_type.value_kind);
    writer->WriteU32(value_type.scalar_kind);
    writer->WriteU32(value_type.rows);
    writer->WriteU32(value_type.columns);
    writer->WriteU32(value_type.array_count);
    writer->WriteU64(value_type.array_stride);
    writer->WriteU64(value_type.matrix_stride);
    writer->WriteString(value_type.type_name);
    writer->WriteCount(value_type.children.size(), "FDXI value type child count");
    for (const auto& child : value_type.children) {
        WriteValueType(writer, child);
    }
}

void WriteMember(BinaryWriter* writer, const MemberRecord& member) {
    writer->WriteString(member.path);
    writer->WriteU32(member.parent_index);
    writer->WriteU64(member.absolute_offset);
    writer->WriteU64(member.size);
    writer->WriteU64(member.alignment);
    writer->WriteU64(member.minimum_size);
    WriteValueType(writer, member.value_type);
}

void WriteResource(BinaryWriter* writer, const ResourceRecord& resource) {
    writer->WriteU32(resource.group);
    writer->WriteU32(resource.binding);
    writer->WriteString(resource.name);
    writer->WriteU32(resource.kind);
    writer->WriteU32(resource.access);
    writer->WriteU32(resource.visibility);
    writer->WriteU32(resource.binding_array_count);
    writer->WriteU64(resource.minimum_size);
    writer->WriteU64(resource.size_no_padding);
    writer->WriteU64(resource.alignment);
    writer->WriteU32(resource.texture_dimension);
    writer->WriteU32(resource.sampled_kind);
    writer->WriteU32(resource.sampler_kind);
    writer->WriteU32(resource.storage_format);
    writer->WriteU32(resource.input_attachment_index);
    writer->WriteCount(resource.members.size(), "FDXI resource member count");
    for (const auto& member : resource.members) {
        WriteMember(writer, member);
    }
}

bool BuildReflection(const tint::Program& program,
                     tint::inspector::Inspector* inspector,
                     ResultHandle* result) {
    auto tint_entries = inspector->GetEntryPoints();
    if (inspector->has_error()) {
        result->diagnostics = inspector->error();
        return false;
    }

    std::vector<EntryRecord> entries;
    entries.reserve(tint_entries.size());
    std::map<uint64_t, ResourceRecord> resources;
    for (const auto& tint_entry : tint_entries) {
        EntryRecord entry = MakeEntryRecord(program, tint_entry);
        auto tint_resources = inspector->GetResourceBindings(tint_entry.name);
        if (inspector->has_error()) {
            result->diagnostics = inspector->error();
            return false;
        }
        entry.bindings.reserve(tint_resources.size());
        for (const auto& binding : tint_resources) {
            entry.bindings.push_back({
                .group = binding.bind_group,
                .binding = binding.binding,
                .minimum_size = binding.size,
            });
            uint64_t key = BindingKey(binding.bind_group, binding.binding);
            auto found = resources.find(key);
            if (found == resources.end()) {
                resources.emplace(key, MakeResourceRecord(binding, StageVisibility(entry.stage)));
            } else {
                if (!SameResourceMetadata(found->second, binding)) {
                    throw std::runtime_error("Tint returned inconsistent metadata for @group(" +
                                             std::to_string(binding.bind_group) + ") @binding(" +
                                             std::to_string(binding.binding) + ")");
                }
                found->second.visibility |= StageVisibility(entry.stage);
                found->second.minimum_size =
                    std::max(found->second.minimum_size, binding.size);
            }
        }
        std::sort(entry.bindings.begin(), entry.bindings.end(),
                  [](const EntryBindingReference& left, const EntryBindingReference& right) {
                      return std::tie(left.group, left.binding) <
                             std::tie(right.group, right.binding);
                  });
        entries.push_back(std::move(entry));
    }
    std::sort(entries.begin(), entries.end(), [](const EntryRecord& left, const EntryRecord& right) {
        return std::tie(left.stage, left.name) < std::tie(right.stage, right.name);
    });

    auto semantic_types = BoundResourceTypes(program);
    for (auto& pair : resources) {
        ResourceRecord& resource = pair.second;
        if (!IsBufferResource(resource.kind)) {
            continue;
        }
        auto type_it = semantic_types.find(pair.first);
        if (type_it == semantic_types.end() || type_it->second == nullptr) {
            throw std::runtime_error("Tint did not expose the semantic type for reflected buffer @" +
                                     std::to_string(resource.group) + ":" +
                                     std::to_string(resource.binding));
        }
        const auto* type = type_it->second;
        resource.alignment = type->Align();
        if (const auto* structure = type->As<tint::core::type::Struct>()) {
            AppendStructMembers(structure, "", 0, kAbsentU32, &resource.members);
        } else if (!type->Is<tint::core::type::Buffer>()) {
            MemberRecord root;
            root.path = "$";
            root.absolute_offset = 0;
            root.size = type->Size();
            root.alignment = type->Align();
            root.minimum_size = resource.minimum_size;
            root.value_type = DescribeValueType(type);
            resource.members.push_back(std::move(root));
        }
    }

    auto extensions = inspector->GetUsedExtensionNames();
    if (inspector->has_error()) {
        result->diagnostics = inspector->error();
        return false;
    }
    std::sort(extensions.begin(), extensions.end());
    extensions.erase(std::unique(extensions.begin(), extensions.end()), extensions.end());

    BinaryWriter writer;
    writer.WriteMagic({'F', 'D', 'X', 'I'});
    writer.WriteU32(kReflectionSchemaVersion);
    writer.WriteCount(entries.size(), "FDXI entry count");
    for (const auto& entry : entries) {
        WriteEntry(&writer, entry);
    }
    writer.WriteCount(resources.size(), "FDXI resource count");
    for (const auto& resource : resources) {
        WriteResource(&writer, resource.second);
    }
    writer.WriteCount(extensions.size(), "FDXI extension count");
    for (const auto& extension : extensions) {
        writer.WriteString(extension);
    }
    result->reflection = writer.Take();
    return true;
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
    switch (options.stage) {
        case FDX_SHADERC_STAGE_FRAGMENT:
            return "fs_main";
        case FDX_SHADERC_STAGE_COMPUTE:
            return "";
        case FDX_SHADERC_STAGE_MODULE:
        case FDX_SHADERC_STAGE_VERTEX:
            return "vs_main";
        default:
            return "";
    }
}

bool IsWgslTarget(int32_t target) {
    return target == FDX_SHADERC_TARGET_WEBGPU_WGSL ||
           target == FDX_SHADERC_TARGET_WGPU_WGSL;
}

bool ValidateRequestedEntryPoint(tint::inspector::Inspector* inspector,
                                 const fdx_shaderc_options& options,
                                 ResultHandle* result) {
    if (options.stage < FDX_SHADERC_STAGE_MODULE ||
        options.stage > FDX_SHADERC_STAGE_COMPUTE) {
        result->diagnostics =
            "Unsupported libFDX shader compiler stage: " + std::to_string(options.stage);
        return false;
    }

    const std::string requested = EntryPoint(options);
    const bool requires_entry = !IsWgslTarget(options.target) ||
                                options.stage != FDX_SHADERC_STAGE_MODULE ||
                                !SafeView(options.entry_point).empty();
    if (!requires_entry) {
        return true;
    }
    if (requested.empty()) {
        result->diagnostics =
            "Compute runtime shader compilation requires an explicit entry point";
        return false;
    }

    auto entries = inspector->GetEntryPoints();
    if (inspector->has_error()) {
        result->diagnostics = inspector->error();
        return false;
    }
    auto found = std::find_if(entries.begin(), entries.end(),
                              [&requested](const tint::inspector::EntryPoint& entry) {
                                  return entry.name == requested;
                              });
    if (found == entries.end()) {
        result->diagnostics = "WGSL entry point '" + requested + "' was not found";
        return false;
    }

    std::optional<tint::inspector::PipelineStage> expected_stage;
    switch (options.stage) {
        case FDX_SHADERC_STAGE_MODULE:
            break;
        case FDX_SHADERC_STAGE_VERTEX:
            expected_stage = tint::inspector::PipelineStage::kVertex;
            break;
        case FDX_SHADERC_STAGE_FRAGMENT:
            expected_stage = tint::inspector::PipelineStage::kFragment;
            break;
        case FDX_SHADERC_STAGE_COMPUTE:
            expected_stage = tint::inspector::PipelineStage::kCompute;
            break;
        default:
            break;
    }
    if (expected_stage.has_value() && found->stage != *expected_stage) {
        result->diagnostics = "WGSL entry point '" + requested +
                              "' does not match the requested shader stage";
        return false;
    }
    return true;
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

bool ConfigureVulkanCombinedSamplerBindings(tint::spirv::writer::Options& gen_options,
                                            tint::inspector::Inspector& inspector,
                                            const std::string& entry_point,
                                            ResultHandle* result) {
    auto slot_key = [](const tint::BindingPoint& point) {
        return (static_cast<uint64_t>(point.group) << 32u) | point.binding;
    };
    std::unordered_set<uint64_t> occupied_slots;
    auto reserve_bindings = [&occupied_slots, &slot_key](const tint::BindingMap& bindings) {
        for (const auto& [source, target] : bindings) {
            static_cast<void>(source);
            occupied_slots.insert(slot_key(target));
        }
    };
    reserve_bindings(gen_options.bindings.uniform);
    reserve_bindings(gen_options.bindings.storage);
    reserve_bindings(gen_options.bindings.texture);
    reserve_bindings(gen_options.bindings.storage_texture);
    reserve_bindings(gen_options.bindings.texel_buffer);
    reserve_bindings(gen_options.bindings.sampler);
    reserve_bindings(gen_options.bindings.input_attachment);
    for (const auto& [source, target] : gen_options.bindings.external_texture) {
        static_cast<void>(source);
        if (const auto* multiplanar = std::get_if<tint::ExternalMultiplanarTexture>(&target)) {
            occupied_slots.insert(slot_key(multiplanar->metadata));
            occupied_slots.insert(slot_key(multiplanar->plane0));
            occupied_slots.insert(slot_key(multiplanar->plane1));
        } else if (const auto* ycbcr = std::get_if<tint::ExternalYCBCRTexture>(&target)) {
            occupied_slots.insert(slot_key(ycbcr->metadata));
            occupied_slots.insert(slot_key(ycbcr->texture));
            occupied_slots.insert(slot_key(ycbcr->sampler));
        }
    }

    std::unordered_map<tint::BindingPoint, tint::BindingPoint> sampler_slots;
    std::unordered_map<tint::BindingPoint, tint::BindingPoint> texture_samplers;
    auto pairs = inspector.GetSamplerTextureUses(entry_point);
    std::sort(pairs.begin(), pairs.end(), [](const auto& left, const auto& right) {
        if (left.texture_binding_point != right.texture_binding_point) {
            return BindingPointLess(left.texture_binding_point, right.texture_binding_point);
        }
        return BindingPointLess(left.sampler_binding_point, right.sampler_binding_point);
    });
    pairs.erase(std::unique(pairs.begin(), pairs.end(), [](const auto& left, const auto& right) {
        return left.texture_binding_point == right.texture_binding_point &&
               left.sampler_binding_point == right.sampler_binding_point;
    }), pairs.end());
    for (const auto& pair : pairs) {
        auto texture = gen_options.bindings.texture.find(pair.texture_binding_point);
        auto sampler = gen_options.bindings.sampler.find(pair.sampler_binding_point);
        if (texture == gen_options.bindings.texture.end() ||
            sampler == gen_options.bindings.sampler.end()) {
            continue;
        }
        occupied_slots.erase(slot_key(texture->second));
        occupied_slots.erase(slot_key(sampler->second));
    }

    std::unordered_map<uint32_t, uint32_t> next_binding_by_group;
    for (const auto& pair : pairs) {
        auto texture = gen_options.bindings.texture.find(pair.texture_binding_point);
        auto sampler = gen_options.bindings.sampler.find(pair.sampler_binding_point);
        if (texture == gen_options.bindings.texture.end() ||
            sampler == gen_options.bindings.sampler.end()) {
            continue;
        }

        auto existing_sampler_slot = sampler_slots.find(pair.sampler_binding_point);
        if (existing_sampler_slot != sampler_slots.end()) {
            result->diagnostics =
                "Vulkan combined sampler binding generation cannot map one WGSL sampler to "
                "multiple texture slots. Use one sampler binding per sampled texture for this "
                "backend.";
            return false;
        }
        auto existing_texture_sampler = texture_samplers.find(pair.texture_binding_point);
        if (existing_texture_sampler != texture_samplers.end()) {
            result->diagnostics =
                "Vulkan combined sampler binding generation cannot map one WGSL texture to "
                "multiple sampler slots. Use one sampler binding per sampled texture for this "
                "backend.";
            return false;
        }

        uint32_t group = texture->second.group;
        uint32_t& next_binding = next_binding_by_group[group];
        tint::BindingPoint slot{.group = group, .binding = next_binding};
        while (occupied_slots.count(slot_key(slot)) != 0) {
            slot.binding++;
        }
        next_binding = slot.binding + 1u;
        occupied_slots.insert(slot_key(slot));

        sampler_slots.emplace(pair.sampler_binding_point, slot);
        texture_samplers.emplace(pair.texture_binding_point,
                                 pair.sampler_binding_point);
        texture->second = slot;
        sampler->second = slot;
        gen_options.statically_paired_texture_binding_points.insert(pair.texture_binding_point);
    }

    return true;
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
        result->target_interface =
            BuildTargetInterface(gen_options.bindings, options.stage, entry_point);
        return true;
    }

    if (options.target == FDX_SHADERC_TARGET_VULKAN_SPIRV) {
        tint::spirv::writer::Options gen_options;
        gen_options.entry_point_name = entry_point;
        gen_options.bindings = tint::GenerateBindings(ir.Get(), entry_point, false, false);
        if (!ConfigureVulkanCombinedSamplerBindings(gen_options, inspector, entry_point, result)) {
            return false;
        }
        gen_options.resource_table =
            tint::core::ir::transform::GenerateResourceTableConfig(ir.Get(), false);
        auto output = tint::spirv::writer::Generate(ir.Get(), gen_options);
        if (output != tint::Success) {
            result->diagnostics = "Failed to generate SPIR-V: " + output.Failure().reason;
            return false;
        }
        result->output_kind = FDX_SHADERC_OUTPUT_SPIRV;
        result->output = SpirvBytes(output->spirv);
        std::unordered_set<tint::BindingPoint> combined_textures(
            gen_options.statically_paired_texture_binding_points.begin(),
            gen_options.statically_paired_texture_binding_points.end());
        std::unordered_set<tint::BindingPoint> combined_slots;
        for (const auto& source : combined_textures) {
            auto target = gen_options.bindings.texture.find(source);
            if (target != gen_options.bindings.texture.end()) {
                combined_slots.insert(target->second);
            }
        }
        result->target_interface = BuildTargetInterface(
            gen_options.bindings, options.stage, entry_point, combined_textures, combined_slots);
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
        result->target_interface =
            BuildTargetInterface(gen_options.bindings, options.stage, entry_point);
        return true;
    }

    if (options.target == FDX_SHADERC_TARGET_DIRECTX_HLSL) {
        tint::hlsl::writer::Options gen_options;
        // libFDX's current D3D12 consumer compiles shader model 5.1 with FXC.
        // Selecting Tint's matching dialect lowers HLSL-2021-only constructs
        // such as select() to FXC-compatible expressions before the artifact
        // reaches the provider.
        gen_options.compiler =
            tint::hlsl::writer::Options::Compiler::kFXC;
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
        result->target_interface =
            BuildTargetInterface(gen_options.bindings, options.stage, entry_point);
        return true;
    }

    result->diagnostics = "Unsupported libFDX shader compiler target: " +
                          std::to_string(options.target);
    return false;
}

ResultHandle* CompileHandle(const char* source,
                            int32_t source_size,
                            const fdx_shaderc_options& options) {
    auto* result = new (std::nothrow) ResultHandle();
    if (result == nullptr) {
        return nullptr;
    }
    try {
        EnsureTintInitialized();
        if (source == nullptr || source_size <= 0) {
            result->diagnostics = "WGSL source cannot be empty";
            return result;
        }

        std::string wgsl(source, source + source_size);
        tint::Source::File file("libfdx-shader.wgsl", wgsl);
        tint::wgsl::reader::Options reader_options;
#if defined(LIBFDX_SHADERC_REFLECT_ALL_FEATURES)
        reader_options.allowed_features = tint::wgsl::AllowedFeatures::Everything();
#endif
        tint::Program program = tint::wgsl::reader::Parse(&file, reader_options);
        if (!program.IsValid()) {
            result->diagnostics = program.Diagnostics().Str();
            return result;
        }

        tint::inspector::Inspector inspector(program);
        if (inspector.has_error()) {
            result->diagnostics = inspector.error();
            return result;
        }
        if (!BuildReflection(program, &inspector, result) ||
            !ValidateRequestedEntryPoint(&inspector, options, result)) {
            result->reflection.clear();
            return result;
        }

        if (IsWgslTarget(options.target)) {
            result->output_kind = FDX_SHADERC_OUTPUT_TEXT;
            result->output.assign(source, source + source_size);
        } else if (!CompileParsedProgram(program, inspector, options, result)) {
            result->reflection.clear();
            result->target_interface.clear();
            return result;
        }
        if (result->output.size() > static_cast<size_t>(std::numeric_limits<int32_t>::max()) ||
            result->reflection.size() >
                static_cast<size_t>(std::numeric_limits<int32_t>::max()) ||
            result->target_interface.size() >
                static_cast<size_t>(std::numeric_limits<int32_t>::max())) {
            throw std::runtime_error("Runtime shader compiler result exceeds the C ABI size limit");
        }
        result->status = 0;
    } catch (const std::exception& error) {
        result->status = 1;
        result->output_kind = FDX_SHADERC_OUTPUT_NONE;
        result->output.clear();
        result->reflection.clear();
        result->target_interface.clear();
        result->diagnostics = "Runtime shader compiler failure: " + std::string(error.what());
    } catch (...) {
        result->status = 1;
        result->output_kind = FDX_SHADERC_OUTPUT_NONE;
        result->output.clear();
        result->reflection.clear();
        result->target_interface.clear();
        result->diagnostics = "Runtime shader compiler failed with an unknown native exception";
    }
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
    if (handle == nullptr) {
        result->status = 1;
        result->diagnostics = CopyString("Runtime shader compiler allocation failed");
        return result->status;
    }
    result->status = handle->status;
    result->output_kind = handle->output_kind;
    result->output_size = static_cast<int32_t>(handle->output.size());
    result->output = CopyOutput(handle->output);
    result->diagnostics = CopyString(handle->diagnostics);
    if ((!handle->output.empty() && result->output == nullptr) ||
        result->diagnostics == nullptr) {
        fdx_shaderc_free_result(result);
        result->status = 1;
        result->diagnostics = CopyString("Runtime shader compiler result allocation failed");
    }
    int32_t status = handle->status;
    if (result->status != 0) {
        status = result->status;
    }
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

FDX_SHADERC_API const uint8_t* fdx_shaderc_result_reflection(void* handle) {
    if (handle == nullptr) {
        return nullptr;
    }
    const auto& reflection = static_cast<ResultHandle*>(handle)->reflection;
    return reflection.empty() ? nullptr : reflection.data();
}

FDX_SHADERC_API int32_t fdx_shaderc_result_reflection_size(void* handle) {
    if (handle == nullptr) {
        return 0;
    }
    return static_cast<int32_t>(static_cast<ResultHandle*>(handle)->reflection.size());
}

FDX_SHADERC_API const uint8_t* fdx_shaderc_result_target_interface(void* handle) {
    if (handle == nullptr) {
        return nullptr;
    }
    const auto& target_interface = static_cast<ResultHandle*>(handle)->target_interface;
    return target_interface.empty() ? nullptr : target_interface.data();
}

FDX_SHADERC_API int32_t fdx_shaderc_result_target_interface_size(void* handle) {
    if (handle == nullptr) {
        return 0;
    }
    return static_cast<int32_t>(static_cast<ResultHandle*>(handle)->target_interface.size());
}

FDX_SHADERC_API void fdx_shaderc_result_free(void* handle) {
    delete static_cast<ResultHandle*>(handle);
}
