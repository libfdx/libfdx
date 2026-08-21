package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBuiltinUsage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolation;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling;
import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterDomain;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceUse;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVariable;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVisibility;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.shader.reflection.ShaderUpdateFrequency;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;

/**
 * G3D-owned semantic handles and reusable values for the built-in PBR shader.
 *
 * <p>This class owns the explicit, immutable ABI shared by the built-in WGSL and the parameter
 * writer. It is assembled once during class initialization and reused by every PBR shader; Tint
 * is only used by a build-time verification task to detect source/interface drift.</p>
 */
final class PbrShaderParameters {
    static final int MAX_POINT_LIGHTS = 4;
    static final int MAX_SPOT_LIGHTS = 4;
    static final int MAX_SHADOW_CASCADES = 4;
    static final int MAX_BONES = 64;

    private static final long STATIC_UNIFORM_SIZE = 1_248;
    private static final long SKINNED_UNIFORM_SIZE = 5_360;
    private static final ShaderValueType FLOAT2 =
            ShaderValueType.vector(ShaderScalarType.F32, 2);
    private static final ShaderValueType FLOAT3 =
            ShaderValueType.vector(ShaderScalarType.F32, 3);
    private static final ShaderValueType FLOAT4 =
            ShaderValueType.vector(ShaderScalarType.F32, 4);
    private static final ShaderValueType NAMED_FLOAT4 =
            FLOAT4.named("vec4<f32>");
    private static final ShaderValueType MATRIX4 =
            ShaderValueType.matrix(ShaderScalarType.F32, 4, 4, 16)
                    .named("mat4x4<f32>");
    private static final ShaderValueType FLOAT4_ARRAY =
            ShaderValueType.array(NAMED_FLOAT4, 4, 16)
                    .named("array<vec4<f32>, 4>");
    private static final ShaderValueType BONE_MATRIX_ARRAY =
            ShaderValueType.array(MATRIX4, MAX_BONES, 64)
                    .named("array<mat4x4<f32>, 64>");
    private static final ShaderParameter[] BASE_PARAMETERS = baseParameters();
    private static final ShaderParameterLayout STATIC_UNIFORM_LAYOUT =
            ShaderParameterLayout.of(STATIC_UNIFORM_SIZE, 16, BASE_PARAMETERS);
    private static final ShaderParameterLayout SKINNED_UNIFORM_LAYOUT =
            skinnedUniformLayout();
    private static final ShaderBinding[] RESOURCE_BINDINGS = resourceBindings();
    private static final ShaderReflection STATIC_REFLECTION = reflection(false);
    private static final ShaderReflection SKINNED_REFLECTION = reflection(true);

    private final ShaderParameterBlock block;
    private final ShaderBinding uniformBinding;
    private final ShaderBinding[] textures;
    private final ShaderBinding[] samplers;

    final ShaderParameterHandle MODEL;
    final ShaderParameterHandle VIEW_PROJECTION;
    final ShaderParameterHandle CAMERA_POSITION;
    final ShaderParameterHandle CAMERA_DIRECTION;
    final ShaderParameterHandle AMBIENT_COLOR;
    final ShaderParameterHandle LIGHT_DIRECTION;
    final ShaderParameterHandle LIGHT_COLOR_INTENSITY;
    final ShaderParameterHandle FILL_LIGHT_DIRECTION;
    final ShaderParameterHandle FILL_LIGHT_COLOR_INTENSITY;
    final ShaderParameterHandle POST_PROCESSING;
    final ShaderParameterHandle TEXTURE_FLAGS;
    final ShaderParameterHandle EMISSIVE_FLAGS;
    final ShaderParameterHandle MATERIAL_PARAMS;
    final ShaderParameterHandle FOG_COLOR;
    final ShaderParameterHandle FOG_PARAMS;
    final ShaderParameterHandle SKY_ZENITH_COLOR;
    final ShaderParameterHandle SKY_HORIZON_COLOR;
    final ShaderParameterHandle SKY_NADIR_COLOR;
    final ShaderParameterHandle SKY_SUN_COLOR;
    final ShaderParameterHandle SKY_SUN_DIRECTION;
    final ShaderParameterHandle SKY_PARAMS;
    final ShaderParameterHandle POINT_LIGHT_COUNT;
    final ShaderParameterHandle POINT_LIGHT_POSITIONS;
    final ShaderParameterHandle POINT_LIGHT_COLORS;
    final ShaderParameterHandle SPOT_LIGHT_COUNT;
    final ShaderParameterHandle SPOT_LIGHT_POSITIONS;
    final ShaderParameterHandle SPOT_LIGHT_DIRECTIONS;
    final ShaderParameterHandle SPOT_LIGHT_COLORS;
    final ShaderParameterHandle SPOT_LIGHT_CONES;
    final ShaderParameterHandle SHADOW_VIEW_PROJECTION_0;
    final ShaderParameterHandle SHADOW_VIEW_PROJECTION_1;
    final ShaderParameterHandle SHADOW_VIEW_PROJECTION_2;
    final ShaderParameterHandle SHADOW_VIEW_PROJECTION_3;
    final ShaderParameterHandle SHADOW_PARAMS;
    final ShaderParameterHandle SHADOW_CASCADE_SPLITS;
    final ShaderParameterHandle SHADOW_BIASES;
    final ShaderParameterHandle SHADOW_CAMERA_POSITION;
    final ShaderParameterHandle SHADOW_CAMERA_DIRECTION;
    final ShaderParameterHandle SHADOW_CAMERA_UP;
    final ShaderParameterHandle SHADOW_CAMERA_PARAMS;
    final ShaderParameterHandle SHADOW_FILTER_PARAMS;
    final ShaderParameterHandle SHADOW_FILTER_SCALES;
    final ShaderParameterHandle SKINNING_PARAMS;
    final ShaderParameterHandle BONE_MATRICES;

    final ShaderParameterHandle HAS_BASE_COLOR_TEXTURE;
    final ShaderParameterHandle HAS_METALLIC_ROUGHNESS_TEXTURE;
    final ShaderParameterHandle HAS_NORMAL_TEXTURE;
    final ShaderParameterHandle HAS_OCCLUSION_TEXTURE;
    final ShaderParameterHandle HAS_EMISSIVE_TEXTURE;
    final ShaderParameterHandle ALPHA_CUTOFF;
    final ShaderParameterHandle LIGHTING_INFLUENCE;
    final ShaderParameterHandle SHADOW_INFLUENCE;
    final ShaderParameterHandle TEXTURE_OFFSET_U;
    final ShaderParameterHandle TEXTURE_OFFSET_V;
    final ShaderParameterHandle LIGHT_INTENSITY;
    final ShaderParameterHandle FILL_LIGHT_INTENSITY;
    final ShaderParameterHandle POINT_LIGHT_COUNT_VALUE;
    final ShaderParameterHandle SPOT_LIGHT_COUNT_VALUE;

    private final ShaderParameterHandle[] pointLightPositions;
    private final ShaderParameterHandle[] pointLightColors;
    private final ShaderParameterHandle[] spotLightPositions;
    private final ShaderParameterHandle[] spotLightDirections;
    private final ShaderParameterHandle[] spotLightColors;
    private final ShaderParameterHandle[] spotLightCones;
    private final ShaderParameterHandle[] shadowViewProjections;
    private final ShaderParameterHandle[] boneMatrices;

    static ShaderReflection staticReflection() {
        return STATIC_REFLECTION;
    }

    static ShaderReflection skinnedReflection() {
        return SKINNED_REFLECTION;
    }

    private static ShaderParameter[] baseParameters() {
        return new ShaderParameter[] {
                parameter("model", MATRIX4, 0, 64,
                        ShaderParameterDomain.OBJECT_DRAW, ShaderUpdateFrequency.DRAW),
                parameter("viewProjection", MATRIX4, 64, 64,
                        ShaderParameterDomain.FRAME_VIEW, ShaderUpdateFrequency.FRAME),
                parameter("cameraPosition", NAMED_FLOAT4, 128, 16,
                        ShaderParameterDomain.FRAME_VIEW, ShaderUpdateFrequency.FRAME),
                parameter("cameraDirection", NAMED_FLOAT4, 144, 16,
                        ShaderParameterDomain.FRAME_VIEW, ShaderUpdateFrequency.FRAME),
                environmentParameter("ambientColor", NAMED_FLOAT4, 160, 16),
                environmentParameter("lightDirection", NAMED_FLOAT4, 176, 16),
                environmentParameter("lightColorIntensity", NAMED_FLOAT4, 192, 16),
                environmentParameter("fillLightDirection", NAMED_FLOAT4, 208, 16),
                environmentParameter("fillLightColorIntensity", NAMED_FLOAT4, 224, 16),
                environmentParameter("postProcessing", NAMED_FLOAT4, 240, 16),
                materialParameter("textureFlags", NAMED_FLOAT4, 256, 16),
                materialParameter("emissiveFlags", NAMED_FLOAT4, 272, 16),
                materialParameter("materialParams", NAMED_FLOAT4, 288, 16),
                environmentParameter("fogColor", NAMED_FLOAT4, 304, 16),
                environmentParameter("fogParams", NAMED_FLOAT4, 320, 16),
                environmentParameter("skyZenithColor", NAMED_FLOAT4, 336, 16),
                environmentParameter("skyHorizonColor", NAMED_FLOAT4, 352, 16),
                environmentParameter("skyNadirColor", NAMED_FLOAT4, 368, 16),
                environmentParameter("skySunColor", NAMED_FLOAT4, 384, 16),
                environmentParameter("skySunDirection", NAMED_FLOAT4, 400, 16),
                environmentParameter("skyParams", NAMED_FLOAT4, 416, 16),
                environmentParameter("pointLightCount", NAMED_FLOAT4, 432, 16),
                environmentParameter("pointLightPositions", FLOAT4_ARRAY, 448, 64),
                environmentParameter("pointLightColors", FLOAT4_ARRAY, 512, 64),
                environmentParameter("spotLightCount", NAMED_FLOAT4, 576, 16),
                environmentParameter("spotLightPositions", FLOAT4_ARRAY, 592, 64),
                environmentParameter("spotLightDirections", FLOAT4_ARRAY, 656, 64),
                environmentParameter("spotLightColors", FLOAT4_ARRAY, 720, 64),
                environmentParameter("spotLightCones", FLOAT4_ARRAY, 784, 64),
                environmentParameter("shadowViewProjection0", MATRIX4, 848, 64),
                environmentParameter("shadowViewProjection1", MATRIX4, 912, 64),
                environmentParameter("shadowViewProjection2", MATRIX4, 976, 64),
                environmentParameter("shadowViewProjection3", MATRIX4, 1_040, 64),
                environmentParameter("shadowParams", NAMED_FLOAT4, 1_104, 16),
                environmentParameter("shadowCascadeSplits", NAMED_FLOAT4, 1_120, 16),
                environmentParameter("shadowBiases", NAMED_FLOAT4, 1_136, 16),
                environmentParameter("shadowCameraPosition", NAMED_FLOAT4, 1_152, 16),
                environmentParameter("shadowCameraDirection", NAMED_FLOAT4, 1_168, 16),
                environmentParameter("shadowCameraUp", NAMED_FLOAT4, 1_184, 16),
                environmentParameter("shadowCameraParams", NAMED_FLOAT4, 1_200, 16),
                environmentParameter("shadowFilterParams", NAMED_FLOAT4, 1_216, 16),
                environmentParameter("shadowFilterScales", NAMED_FLOAT4, 1_232, 16)
        };
    }

    private static ShaderParameterLayout skinnedUniformLayout() {
        ShaderParameter[] parameters = new ShaderParameter[BASE_PARAMETERS.length + 2];
        System.arraycopy(BASE_PARAMETERS, 0, parameters, 0, BASE_PARAMETERS.length);
        parameters[BASE_PARAMETERS.length] = parameter("skinningParams", NAMED_FLOAT4,
                STATIC_UNIFORM_SIZE, 16,
                ShaderParameterDomain.OBJECT_DRAW, ShaderUpdateFrequency.DRAW);
        parameters[BASE_PARAMETERS.length + 1] = parameter("boneMatrices", BONE_MATRIX_ARRAY,
                STATIC_UNIFORM_SIZE + 16, MAX_BONES * 64L,
                ShaderParameterDomain.OBJECT_DRAW, ShaderUpdateFrequency.DRAW);
        return ShaderParameterLayout.of(SKINNED_UNIFORM_SIZE, 16, parameters);
    }

    private static ShaderParameter environmentParameter(String name, ShaderValueType type,
            long offset, long size) {
        return parameter(name, type, offset, size,
                ShaderParameterDomain.ENVIRONMENT_PASS, ShaderUpdateFrequency.PASS);
    }

    private static ShaderParameter materialParameter(String name, ShaderValueType type,
            long offset, long size) {
        return parameter(name, type, offset, size,
                ShaderParameterDomain.MATERIAL, ShaderUpdateFrequency.ON_CHANGE);
    }

    private static ShaderParameter parameter(String name, ShaderValueType type, long offset,
            long size, ShaderParameterDomain domain, ShaderUpdateFrequency frequency) {
        return ShaderParameter.builder("pbr." + name, name, type, offset, size, 16)
                .semantics(domain, frequency)
                .build();
    }

    private static ShaderBinding[] resourceBindings() {
        return new ShaderBinding[] {
                materialTexture(0, "baseColorTexture"),
                materialSampler(1, "baseColorSampler"),
                materialTexture(2, "metallicRoughnessTexture"),
                materialSampler(3, "metallicRoughnessSampler"),
                materialTexture(4, "normalTexture"),
                materialSampler(5, "normalSampler"),
                materialTexture(6, "occlusionTexture"),
                materialSampler(7, "occlusionSampler"),
                materialTexture(8, "emissiveTexture"),
                materialSampler(9, "emissiveSampler"),
                environmentTexture(10, "shadowTexture0"),
                environmentSampler(11, "shadowSampler0"),
                environmentTexture(12, "shadowTexture1"),
                environmentSampler(13, "shadowSampler1"),
                environmentTexture(14, "shadowTexture2"),
                environmentSampler(15, "shadowSampler2"),
                environmentTexture(16, "shadowTexture3"),
                environmentSampler(17, "shadowSampler3")
        };
    }

    private static ShaderBinding materialTexture(int binding, String name) {
        return textureBinding(binding, name, "pbr.material." + name,
                ShaderParameterDomain.MATERIAL, ShaderUpdateFrequency.ON_CHANGE);
    }

    private static ShaderBinding materialSampler(int binding, String name) {
        return samplerBinding(binding, name, "pbr.material." + name,
                ShaderParameterDomain.MATERIAL, ShaderUpdateFrequency.ON_CHANGE);
    }

    private static ShaderBinding environmentTexture(int binding, String name) {
        return textureBinding(binding, name, "pbr.environment." + name,
                ShaderParameterDomain.ENVIRONMENT_PASS, ShaderUpdateFrequency.PASS);
    }

    private static ShaderBinding environmentSampler(int binding, String name) {
        return samplerBinding(binding, name, "pbr.environment." + name,
                ShaderParameterDomain.ENVIRONMENT_PASS, ShaderUpdateFrequency.PASS);
    }

    private static ShaderBinding textureBinding(int binding, String name, String stableId,
            ShaderParameterDomain domain, ShaderUpdateFrequency frequency) {
        return ShaderBinding.builder(0, binding, name, ShaderResourceKind.SAMPLED_TEXTURE)
                .stableId(stableId)
                .visibility(ShaderStageVisibility.FRAGMENT)
                .access(ShaderResourceAccess.READ)
                .texture(ShaderTextureDimension.D2, ShaderTextureSampleType.UNKNOWN_FILTERABLE)
                .semantics(domain, frequency)
                .build();
    }

    private static ShaderBinding samplerBinding(int binding, String name, String stableId,
            ShaderParameterDomain domain, ShaderUpdateFrequency frequency) {
        return ShaderBinding.builder(0, binding, name, ShaderResourceKind.SAMPLER)
                .stableId(stableId)
                .visibility(ShaderStageVisibility.FRAGMENT)
                .access(ShaderResourceAccess.NONE)
                .samplerKind(ShaderSamplerKind.UNKNOWN_FILTERING)
                .semantics(domain, frequency)
                .build();
    }

    private static ShaderReflection reflection(boolean skinned) {
        ShaderParameterLayout layout = skinned ? SKINNED_UNIFORM_LAYOUT : STATIC_UNIFORM_LAYOUT;
        long uniformSize = layout.minimumBindingSize();
        ShaderBinding uniform = ShaderBinding.builder(1, 0, "uniforms",
                        ShaderResourceKind.UNIFORM_BUFFER)
                .stableId("pbr.uniforms")
                .visibility(ShaderStageVisibility.of(ShaderStage.VERTEX, ShaderStage.FRAGMENT))
                .access(ShaderResourceAccess.READ)
                .buffer(uniformSize, uniformSize, layout.alignment(), layout)
                .semantics(ShaderParameterDomain.MIXED, ShaderUpdateFrequency.MIXED)
                .build();
        ShaderBinding[] bindings = new ShaderBinding[RESOURCE_BINDINGS.length + 1];
        System.arraycopy(RESOURCE_BINDINGS, 0, bindings, 0, RESOURCE_BINDINGS.length);
        bindings[RESOURCE_BINDINGS.length] = uniform;

        ShaderStageVariable[] vertexInputs = skinned
                ? new ShaderStageVariable[] {
                        input("position", 0, FLOAT3), input("normal", 1, FLOAT3),
                        input("uv", 2, FLOAT2), input("color", 3, FLOAT4),
                        input("pbr", 4, FLOAT3), input("emissive", 5, FLOAT3),
                        input("joints", 6, FLOAT4), input("weights", 7, FLOAT4)
                }
                : new ShaderStageVariable[] {
                        input("position", 0, FLOAT3), input("normal", 1, FLOAT3),
                        input("uv", 2, FLOAT2), input("color", 3, FLOAT4),
                        input("pbr", 4, FLOAT3), input("emissive", 5, FLOAT3)
                };
        ShaderStageVariable[] vertexOutputs = new ShaderStageVariable[] {
                output("worldPosition", 0, FLOAT3), output("normal", 1, FLOAT3),
                output("uv", 2, FLOAT2), output("color", 3, FLOAT4),
                output("pbr", 4, FLOAT3), output("emissive", 5, FLOAT3)
        };
        ShaderStageVariable[] fragmentInputs = new ShaderStageVariable[] {
                input("worldPosition", 0, FLOAT3), input("normal", 1, FLOAT3),
                input("uv", 2, FLOAT2), input("color", 3, FLOAT4),
                input("pbr", 4, FLOAT3), input("emissive", 5, FLOAT3)
        };
        ShaderResourceUse[] fragmentResources = new ShaderResourceUse[bindings.length];
        for (int i = 0; i < RESOURCE_BINDINGS.length; i++) {
            ShaderBinding binding = RESOURCE_BINDINGS[i];
            fragmentResources[i] = ShaderResourceUse.of(binding.group(), binding.binding(), 0);
        }
        fragmentResources[RESOURCE_BINDINGS.length] = ShaderResourceUse.of(1, 0, uniformSize);

        ShaderEntryPoint vertex = ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX)
                .builtins(ShaderBuiltinUsage.POSITION, -1)
                .inputs(vertexInputs)
                .outputs(vertexOutputs)
                .resources(ShaderResourceUse.of(1, 0, uniformSize))
                .build();
        ShaderEntryPoint fragment = ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT)
                .builtins(ShaderBuiltinUsage.POSITION, -1)
                .inputs(fragmentInputs)
                .outputs(stageVariable("<retval>", "", 0, FLOAT4))
                .resources(fragmentResources)
                .build();
        return ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] { vertex, fragment }, bindings, new String[0]);
    }

    private static ShaderStageVariable input(String name, int location, ShaderValueType type) {
        return stageVariable("input." + name, name, location, type);
    }

    private static ShaderStageVariable output(String name, int location, ShaderValueType type) {
        return stageVariable("<retval>." + name, name, location, type);
    }

    private static ShaderStageVariable stageVariable(String name, String variableName,
            int location, ShaderValueType type) {
        return ShaderStageVariable.of(name, variableName, location, -1, -1, type,
                ShaderInterpolation.PERSPECTIVE, ShaderInterpolationSampling.CENTER);
    }

    PbrShaderParameters(ShaderReflection reflection) {
        if (reflection == null || !reflection.complete()) {
            throw new FdxException("PBR parameters require complete shader reflection");
        }
        uniformBinding = reflection.requireBinding(1, 0);
        if (uniformBinding.resourceKind()
                != ShaderResourceKind.UNIFORM_BUFFER) {
            throw new FdxException(
                    "PBR renderer parameters require a uniform buffer at 1:0");
        }
        ShaderParameterLayout layout = uniformBinding.bufferLayout();
        block = ShaderParameterBlock.allocate(layout);
        textures = all(reflection, ShaderResourceKind.SAMPLED_TEXTURE);
        samplers = all(reflection, ShaderResourceKind.SAMPLER);
        if (textures.length != samplers.length) {
            throw new FdxException("PBR shader interface requires paired textures and samplers");
        }

        MODEL = layout.requireHandle("model");
        VIEW_PROJECTION = layout.requireHandle("viewProjection");
        CAMERA_POSITION = layout.requireHandle("cameraPosition");
        CAMERA_DIRECTION = layout.requireHandle("cameraDirection");
        AMBIENT_COLOR = layout.requireHandle("ambientColor");
        LIGHT_DIRECTION = layout.requireHandle("lightDirection");
        LIGHT_COLOR_INTENSITY = layout.requireHandle("lightColorIntensity");
        FILL_LIGHT_DIRECTION = layout.requireHandle("fillLightDirection");
        FILL_LIGHT_COLOR_INTENSITY = layout.requireHandle("fillLightColorIntensity");
        POST_PROCESSING = layout.requireHandle("postProcessing");
        TEXTURE_FLAGS = layout.requireHandle("textureFlags");
        EMISSIVE_FLAGS = layout.requireHandle("emissiveFlags");
        MATERIAL_PARAMS = layout.requireHandle("materialParams");
        FOG_COLOR = layout.requireHandle("fogColor");
        FOG_PARAMS = layout.requireHandle("fogParams");
        SKY_ZENITH_COLOR = layout.requireHandle("skyZenithColor");
        SKY_HORIZON_COLOR = layout.requireHandle("skyHorizonColor");
        SKY_NADIR_COLOR = layout.requireHandle("skyNadirColor");
        SKY_SUN_COLOR = layout.requireHandle("skySunColor");
        SKY_SUN_DIRECTION = layout.requireHandle("skySunDirection");
        SKY_PARAMS = layout.requireHandle("skyParams");
        POINT_LIGHT_COUNT = layout.requireHandle("pointLightCount");
        POINT_LIGHT_POSITIONS = layout.requireHandle("pointLightPositions");
        POINT_LIGHT_COLORS = layout.requireHandle("pointLightColors");
        SPOT_LIGHT_COUNT = layout.requireHandle("spotLightCount");
        SPOT_LIGHT_POSITIONS = layout.requireHandle("spotLightPositions");
        SPOT_LIGHT_DIRECTIONS = layout.requireHandle("spotLightDirections");
        SPOT_LIGHT_COLORS = layout.requireHandle("spotLightColors");
        SPOT_LIGHT_CONES = layout.requireHandle("spotLightCones");
        SHADOW_VIEW_PROJECTION_0 = layout.requireHandle("shadowViewProjection0");
        SHADOW_VIEW_PROJECTION_1 = layout.requireHandle("shadowViewProjection1");
        SHADOW_VIEW_PROJECTION_2 = layout.requireHandle("shadowViewProjection2");
        SHADOW_VIEW_PROJECTION_3 = layout.requireHandle("shadowViewProjection3");
        SHADOW_PARAMS = layout.requireHandle("shadowParams");
        SHADOW_CASCADE_SPLITS = layout.requireHandle("shadowCascadeSplits");
        SHADOW_BIASES = layout.requireHandle("shadowBiases");
        SHADOW_CAMERA_POSITION = layout.requireHandle("shadowCameraPosition");
        SHADOW_CAMERA_DIRECTION = layout.requireHandle("shadowCameraDirection");
        SHADOW_CAMERA_UP = layout.requireHandle("shadowCameraUp");
        SHADOW_CAMERA_PARAMS = layout.requireHandle("shadowCameraParams");
        SHADOW_FILTER_PARAMS = layout.requireHandle("shadowFilterParams");
        SHADOW_FILTER_SCALES = layout.requireHandle("shadowFilterScales");
        SKINNING_PARAMS = layout.findHandle("skinningParams");
        BONE_MATRICES = layout.findHandle("boneMatrices");

        HAS_BASE_COLOR_TEXTURE = TEXTURE_FLAGS.component(0);
        HAS_METALLIC_ROUGHNESS_TEXTURE = TEXTURE_FLAGS.component(1);
        HAS_NORMAL_TEXTURE = TEXTURE_FLAGS.component(2);
        HAS_OCCLUSION_TEXTURE = TEXTURE_FLAGS.component(3);
        HAS_EMISSIVE_TEXTURE = EMISSIVE_FLAGS.component(0);
        ALPHA_CUTOFF = EMISSIVE_FLAGS.component(1);
        LIGHTING_INFLUENCE = MATERIAL_PARAMS.component(0);
        SHADOW_INFLUENCE = MATERIAL_PARAMS.component(1);
        TEXTURE_OFFSET_U = MATERIAL_PARAMS.component(2);
        TEXTURE_OFFSET_V = MATERIAL_PARAMS.component(3);
        LIGHT_INTENSITY = LIGHT_COLOR_INTENSITY.component(3);
        FILL_LIGHT_INTENSITY = FILL_LIGHT_COLOR_INTENSITY.component(3);
        POINT_LIGHT_COUNT_VALUE = POINT_LIGHT_COUNT.component(0);
        SPOT_LIGHT_COUNT_VALUE = SPOT_LIGHT_COUNT.component(0);

        pointLightPositions = elements(layout, POINT_LIGHT_POSITIONS);
        pointLightColors = elements(layout, POINT_LIGHT_COLORS);
        spotLightPositions = elements(layout, SPOT_LIGHT_POSITIONS);
        spotLightDirections = elements(layout, SPOT_LIGHT_DIRECTIONS);
        spotLightColors = elements(layout, SPOT_LIGHT_COLORS);
        spotLightCones = elements(layout, SPOT_LIGHT_CONES);
        shadowViewProjections = new ShaderParameterHandle[] {
                SHADOW_VIEW_PROJECTION_0, SHADOW_VIEW_PROJECTION_1,
                SHADOW_VIEW_PROJECTION_2, SHADOW_VIEW_PROJECTION_3
        };
        boneMatrices = BONE_MATRICES != null ? elements(layout, BONE_MATRICES)
                : new ShaderParameterHandle[0];
    }

    ShaderParameterBlock block() {
        return block;
    }

    int group() {
        return uniformBinding.group();
    }

    int binding() {
        return uniformBinding.binding();
    }

    int maxPointLights() {
        return pointLightPositions.length;
    }

    int maxSpotLights() {
        return spotLightPositions.length;
    }

    int maxShadowCascades() {
        return shadowViewProjections.length;
    }

    int maxBones() {
        return boneMatrices.length;
    }

    ShaderParameterHandle pointLightPosition(int index) {
        return element(pointLightPositions, index, "point light position");
    }

    ShaderParameterHandle pointLightColor(int index) {
        return element(pointLightColors, index, "point light color");
    }

    ShaderParameterHandle spotLightPosition(int index) {
        return element(spotLightPositions, index, "spot light position");
    }

    ShaderParameterHandle spotLightDirection(int index) {
        return element(spotLightDirections, index, "spot light direction");
    }

    ShaderParameterHandle spotLightColor(int index) {
        return element(spotLightColors, index, "spot light color");
    }

    ShaderParameterHandle spotLightCone(int index) {
        return element(spotLightCones, index, "spot light cone");
    }

    ShaderParameterHandle shadowViewProjection(int index) {
        return element(shadowViewProjections, index, "shadow view projection");
    }

    ShaderParameterHandle boneMatrix(int index) {
        return element(boneMatrices, index, "bone matrix");
    }

    void setUniform1i(ShaderParameterHandle handle, int value) {
        ShaderScalarType scalar = handle.valueType().scalarType();
        if (scalar == ShaderScalarType.F32) {
            block.setFloat(handle, value);
        }
        else if (scalar == ShaderScalarType.I32) {
            block.setInt(handle, value);
        }
        else if (scalar == ShaderScalarType.U32) {
            block.setUnsignedInt(handle, value);
        }
        else if (scalar == ShaderScalarType.BOOL) {
            block.setBoolean(handle, value != 0);
        }
        else {
            throw wrongType(handle);
        }
    }

    void setUniform1f(ShaderParameterHandle handle, float value) {
        block.setFloat(handle, value);
    }

    void setUniform3f(ShaderParameterHandle handle, float x, float y, float z) {
        if (handle.valueType().kind() != ShaderValueKind.VECTOR
                || handle.valueType().scalarType() != ShaderScalarType.F32
                || handle.valueType().rows() < 3) {
            throw wrongType(handle);
        }
        block.setFloat(handle.component(0), x);
        block.setFloat(handle.component(1), y);
        block.setFloat(handle.component(2), z);
    }

    void setUniform4f(ShaderParameterHandle handle, float x, float y, float z, float w) {
        block.setFloat4(handle, x, y, z, w);
    }

    void setUniformMatrix4(ShaderParameterHandle handle, float[] values) {
        block.setFloatMatrix(handle, values, 0);
    }

    void bind(RenderPass pass) {
        pass.setParameterBlock(group(), binding(), block);
    }

    void bindTexture(RenderPass pass, int slot, Texture texture) {
        if (slot < 0 || slot >= textures.length) {
            throw new FdxException("PBR sampled texture slot is out of range: " + slot);
        }
        ShaderBinding textureBinding = textures[slot];
        ShaderBinding samplerBinding = samplers[slot];
        pass.setTextureBinding(textureBinding.group(), textureBinding.binding(), texture);
        pass.setTextureSamplerBinding(samplerBinding.group(), samplerBinding.binding(), texture);
    }

    private static ShaderBinding[] all(ShaderReflection reflection, ShaderResourceKind kind) {
        int count = 0;
        for (ShaderBinding binding : reflection.bindings()) {
            if (binding.resourceKind() == kind) {
                count++;
            }
        }
        ShaderBinding[] result = new ShaderBinding[count];
        int cursor = 0;
        for (ShaderBinding binding : reflection.bindings()) {
            if (binding.resourceKind() == kind) {
                result[cursor++] = binding;
            }
        }
        return result;
    }

    private static ShaderParameterHandle[] elements(ShaderParameterLayout layout,
            ShaderParameterHandle array) {
        if (array.valueType().kind() != ShaderValueKind.ARRAY
                || array.valueType().arrayCount() > Integer.MAX_VALUE) {
            throw new FdxException("PBR parameter is not a fixed Java-sized array: " + array.path());
        }
        int count = Math.toIntExact(array.valueType().arrayCount());
        ShaderParameterHandle[] result = new ShaderParameterHandle[count];
        for (int i = 0; i < count; i++) {
            result[i] = layout.requireArrayElementHandle(array.path(), i);
        }
        return result;
    }

    private static ShaderParameterHandle element(ShaderParameterHandle[] values, int index, String label) {
        if (index < 0 || index >= values.length) {
            throw new FdxException("PBR " + label + " index is out of range: " + index);
        }
        return values[index];
    }

    private static FdxException wrongType(ShaderParameterHandle handle) {
        return new FdxException("PBR parameter has an incompatible value type: " + handle.path());
    }
}
