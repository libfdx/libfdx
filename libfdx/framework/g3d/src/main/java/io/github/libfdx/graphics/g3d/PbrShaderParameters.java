package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.Texture;

/**
 * G3D-owned semantic handles and reusable values for the built-in PBR shader.
 *
 * <p>Every physical offset, stride, binding, and array length comes from the
 * checked-in shader interface manifest. Graphics providers see only the
 * generic reflected resource layout and never identify this block as PBR.</p>
 */
final class PbrShaderParameters {
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
    final ShaderParameterHandle SKINNING_PARAMS;
    final ShaderParameterHandle BONE_MATRICES;

    final ShaderParameterHandle HAS_BASE_COLOR_TEXTURE;
    final ShaderParameterHandle HAS_METALLIC_ROUGHNESS_TEXTURE;
    final ShaderParameterHandle HAS_NORMAL_TEXTURE;
    final ShaderParameterHandle HAS_OCCLUSION_TEXTURE;
    final ShaderParameterHandle HAS_EMISSIVE_TEXTURE;
    final ShaderParameterHandle LIGHTING_INFLUENCE;
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

    static int manifestMaxPointLights() {
        return arrayLength(io.github.libfdx.graphics.internal.BuiltInPbrShaderManifest
                .staticReflection(), "pointLightPositions");
    }

    static int manifestMaxSpotLights() {
        return arrayLength(io.github.libfdx.graphics.internal.BuiltInPbrShaderManifest
                .staticReflection(), "spotLightPositions");
    }

    static int manifestMaxBones() {
        return arrayLength(io.github.libfdx.graphics.internal.BuiltInPbrShaderManifest
                .skinnedReflection(), "boneMatrices");
    }

    static int manifestMaxShadowCascades() {
        ShaderParameterLayout layout = requireSingle(
                io.github.libfdx.graphics.internal.BuiltInPbrShaderManifest.staticReflection(),
                ShaderResourceKind.UNIFORM_BUFFER).bufferLayout();
        int count = 0;
        while (layout.findHandle("shadowViewProjection" + count) != null) {
            count++;
        }
        return count;
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
        SKINNING_PARAMS = layout.findHandle("skinningParams");
        BONE_MATRICES = layout.findHandle("boneMatrices");

        HAS_BASE_COLOR_TEXTURE = TEXTURE_FLAGS.component(0);
        HAS_METALLIC_ROUGHNESS_TEXTURE = TEXTURE_FLAGS.component(1);
        HAS_NORMAL_TEXTURE = TEXTURE_FLAGS.component(2);
        HAS_OCCLUSION_TEXTURE = TEXTURE_FLAGS.component(3);
        HAS_EMISSIVE_TEXTURE = EMISSIVE_FLAGS.component(0);
        LIGHTING_INFLUENCE = MATERIAL_PARAMS.component(0);
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

    private static ShaderBinding requireSingle(ShaderReflection reflection, ShaderResourceKind kind) {
        ShaderBinding result = null;
        for (ShaderBinding binding : reflection.bindings()) {
            if (binding.resourceKind() == kind) {
                if (result != null) {
                    throw new FdxException("PBR shader interface contains multiple " + kind + " bindings");
                }
                result = binding;
            }
        }
        if (result == null) {
            throw new FdxException("PBR shader interface has no " + kind + " binding");
        }
        return result;
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

    private static int arrayLength(ShaderReflection reflection, String path) {
        ShaderParameterHandle handle = requireSingle(reflection,
                ShaderResourceKind.UNIFORM_BUFFER).bufferLayout().requireHandle(path);
        if (handle.valueType().kind() != ShaderValueKind.ARRAY
                || handle.valueType().arrayCount() > Integer.MAX_VALUE) {
            throw new FdxException("PBR manifest value is not a fixed Java-sized array: " + path);
        }
        return Math.toIntExact(handle.valueType().arrayCount());
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
