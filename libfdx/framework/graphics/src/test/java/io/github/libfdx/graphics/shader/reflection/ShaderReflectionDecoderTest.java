package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.target.ShaderSemanticOverlay;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ShaderReflectionDecoderTest {
    /*
     * Native Tint output for phase1-reflection-fixture.wgsl. Its SHA-256 is
     * c92b2c39fba7d7c1613f91e248255e1ddee163412fab17ec7deb0f0c36c39e19.
     */
    static final String FDXI_BASE64 = """
            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
            """;
    /*
     * Native Tint output for fdxi-all-resource-kinds-smoke.wgsl. Its SHA-256 is
     * 46916001044dd027c2a286ef07228f72efbc22b4b95dd6c6dd31829cbaec524a.
     */
    private static final String ALL_RESOURCE_KINDS_FDXI_BASE64 = """
            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
            """;

    @Test
    void decodesNativeProvenCompleteFixtureWithoutLosingNestedTypeFacts() {
        ShaderReflection reflection = decode(FDXI_BASE64);
        ShaderReflection expected = expectedCompleteReflection();

        assertEquals(expected, reflection);
        assertTrue(expected.physicallyEquivalent(reflection));
        assertEquals(expected.physicalHash(), reflection.physicalHash());
        assertEquals(expected.fullHash(), reflection.fullHash());
        assertEquals("0e3c434f6f5b17a333ad67daac351d703afda61136e60d62a6806115c3335a46",
                reflection.physicalHash());
        assertEquals("2051692eba327be643e4a826a13a3c6856c1dc7c845191d96493a52b12eac1ac",
                reflection.fullHash());
        assertTrue(reflection.complete());
        assertEquals(3, reflection.entryPointCount());
        assertEquals(4, reflection.bindingCount());
        assertEquals(1, reflection.sampledTextureCount("vs_main", "fs_main"));

        ShaderEntryPoint vertex = reflection.requireEntryPoint(ShaderStage.VERTEX, "vs_main");
        assertEquals(ShaderBuiltinUsage.POSITION | ShaderBuiltinUsage.INSTANCE_INDEX, vertex.builtinMask());
        assertEquals(304, vertex.resource(0).minimumBindingSize());
        ShaderEntryPoint fragment = reflection.requireEntryPoint(ShaderStage.FRAGMENT, "fs_main");
        assertEquals(ShaderBuiltinUsage.POSITION, fragment.builtinMask());
        ShaderEntryPoint compute = reflection.requireEntryPoint(ShaderStage.COMPUTE, "cs_main");
        assertEquals(ShaderBuiltinUsage.GLOBAL_INVOCATION_ID, compute.builtinMask());
        assertEquals(8, compute.workgroupX());
        assertEquals(4, compute.workgroupY());
        assertEquals(2, compute.workgroupZ());

        ShaderBinding uniform = reflection.requireBinding(0, 0);
        assertEquals(ShaderResourceKind.UNIFORM_BUFFER, uniform.resourceKind());
        assertEquals(304, uniform.minimumBindingSize());
        ShaderParameterLayout layout = uniform.bufferLayout();
        assertEquals(304, layout.minimumBindingSize());
        assertEquals("1bd1b14b10125ad154241942daac320b01194df66e3f09050145cfe33c45b48b",
                layout.physicalHash());
        assertTrue(expectedUniformLayout().physicallyEquivalent(layout));
        assertEquals(0, layout.requireHandle("transform").byteOffset());
        assertEquals(16, layout.requireHandle("transform").matrixStride());
        assertEquals(64, layout.requireHandle("tint").byteOffset());
        assertEquals(80, layout.requireHandle("weights").byteOffset());
        assertEquals(16, layout.requireHandle("weights").arrayStride());

        ShaderParameterHandle nested = layout.requireHandle("nested");
        ShaderValueType outer = nested.valueType();
        assertEquals(ShaderValueKind.ARRAY, outer.kind());
        assertEquals(2, outer.arrayCount());
        assertEquals(96, outer.arrayStride());
        ShaderValueType inner = outer.elementType();
        assertEquals(ShaderValueKind.ARRAY, inner.kind());
        assertEquals(3, inner.arrayCount());
        assertEquals(32, inner.arrayStride());
        ShaderValueType matrix = inner.elementType();
        assertEquals(ShaderValueKind.MATRIX, matrix.kind());
        assertEquals(2, matrix.columns());
        assertEquals(3, matrix.rows());
        assertEquals(16, matrix.matrixStride());
        ShaderParameterHandle outerElement = nested.element(1);
        ShaderParameterHandle innerElement = outerElement.element(1);
        assertSame(outerElement, nested.element(1));
        assertSame(innerElement, outerElement.element(1));
        assertEquals(240, innerElement.byteOffset());

        ShaderBinding storage = reflection.requireBinding(0, 1);
        ShaderValueType runtimeArray = storage.bufferLayout().requireHandle("values").valueType();
        assertEquals(-1, runtimeArray.arrayCount());
        assertEquals(4, runtimeArray.arrayStride());
    }

    @Test
    void decodesEveryNativeResourceKindWithItsKindSpecificFacts() {
        ShaderReflection reflection = decode(ALL_RESOURCE_KINDS_FDXI_BASE64);

        assertEquals(11, reflection.bindingCount());
        assertArrayEquals(new String[]{"chromium_internal_input_attachments"},
                reflection.requiredCapabilities());
        ShaderResourceKind[] expectedKinds = {
                ShaderResourceKind.UNIFORM_BUFFER,
                ShaderResourceKind.STORAGE_BUFFER,
                ShaderResourceKind.SAMPLER,
                ShaderResourceKind.SAMPLED_TEXTURE,
                ShaderResourceKind.MULTISAMPLED_TEXTURE,
                ShaderResourceKind.STORAGE_TEXTURE,
                ShaderResourceKind.DEPTH_TEXTURE,
                ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE,
                ShaderResourceKind.EXTERNAL_TEXTURE,
                ShaderResourceKind.TEXEL_BUFFER,
                ShaderResourceKind.INPUT_ATTACHMENT
        };
        for (int binding = 0; binding < 11; binding++) {
            ShaderBinding resource = reflection.requireBinding(0, binding);
            assertEquals(expectedKinds[binding], resource.resourceKind());
            assertEquals(ShaderStageVisibility.FRAGMENT, resource.visibility());
        }
        ShaderEntryPoint fragment = reflection.requireEntryPoint(ShaderStage.FRAGMENT, "fs_main");
        assertEquals(11, fragment.resourceCount());

        ShaderBinding uniform = reflection.requireBinding(0, 0);
        assertEquals(ShaderResourceAccess.READ, uniform.access());
        assertEquals(16, uniform.minimumBindingSize());
        assertEquals(ShaderValueType.vector(ShaderScalarType.F32, 4).named("vec4<f32>"),
                uniform.bufferLayout().requireHandle("value").valueType());

        ShaderBinding storage = reflection.requireBinding(0, 1);
        assertEquals(ShaderResourceAccess.READ_WRITE, storage.access());
        assertEquals(-1, storage.bufferLayout().requireHandle("values").valueType().arrayCount());

        ShaderBinding sampler = reflection.requireBinding(0, 2);
        assertEquals(ShaderResourceAccess.NONE, sampler.access());
        assertEquals(ShaderSamplerKind.UNKNOWN_FILTERING, sampler.samplerKind());

        ShaderBinding sampled = reflection.requireBinding(0, 3);
        assertEquals(ShaderResourceAccess.READ, sampled.access());
        assertEquals(ShaderTextureDimension.D2, sampled.textureDimension());
        assertEquals(ShaderTextureSampleType.UNKNOWN_FILTERABLE, sampled.textureSampleType());

        ShaderBinding multisampled = reflection.requireBinding(0, 4);
        assertEquals(ShaderResourceAccess.READ, multisampled.access());
        assertEquals(ShaderTextureDimension.D2, multisampled.textureDimension());
        assertEquals(ShaderTextureSampleType.UNFILTERABLE_FLOAT, multisampled.textureSampleType());

        ShaderBinding storageTexture = reflection.requireBinding(0, 5);
        assertEquals(ShaderResourceAccess.WRITE, storageTexture.access());
        assertEquals(ShaderTextureDimension.D2, storageTexture.textureDimension());
        assertEquals(ShaderTextureSampleType.NONE, storageTexture.textureSampleType());
        assertEquals(ShaderStorageTextureFormat.RGBA8_UNORM, storageTexture.imageFormat());

        ShaderBinding depth = reflection.requireBinding(0, 6);
        assertEquals(ShaderResourceAccess.READ, depth.access());
        assertEquals(ShaderTextureDimension.D2, depth.textureDimension());
        assertEquals(ShaderTextureSampleType.NONE, depth.textureSampleType());
        ShaderBinding depthMultisampled = reflection.requireBinding(0, 7);
        assertEquals(ShaderResourceAccess.READ, depthMultisampled.access());
        assertEquals(ShaderTextureDimension.D2, depthMultisampled.textureDimension());
        assertEquals(ShaderTextureSampleType.NONE, depthMultisampled.textureSampleType());

        ShaderBinding external = reflection.requireBinding(0, 8);
        assertEquals(ShaderResourceKind.EXTERNAL_TEXTURE, external.resourceKind());
        assertEquals(ShaderResourceAccess.READ, external.access());
        assertEquals(ShaderStageVisibility.FRAGMENT, external.visibility());
        assertEquals(ShaderTextureDimension.D2, external.textureDimension());
        assertEquals(ShaderTextureSampleType.NONE, external.textureSampleType());
        assertEquals(ShaderStorageTextureFormat.NONE, external.imageFormat());

        ShaderBinding texel = reflection.requireBinding(0, 9);
        assertEquals(ShaderResourceKind.TEXEL_BUFFER, texel.resourceKind());
        assertEquals(ShaderResourceAccess.READ, texel.access());
        assertEquals(ShaderTextureDimension.D1, texel.textureDimension());
        assertEquals(ShaderTextureSampleType.UINT, texel.textureSampleType());
        assertEquals(ShaderStorageTextureFormat.RGBA8_UINT, texel.imageFormat());

        ShaderBinding input = reflection.requireBinding(0, 10);
        assertEquals(ShaderResourceKind.INPUT_ATTACHMENT, input.resourceKind());
        assertEquals(ShaderResourceAccess.READ, input.access());
        assertEquals(ShaderTextureDimension.D2, input.textureDimension());
        assertEquals(ShaderTextureSampleType.FLOAT, input.textureSampleType());
        assertEquals(0, input.inputAttachmentIndex());
    }

    @Test
    void decodingIsDeterministicAndRejectsTrailingOrTruncatedPayloads() {
        ShaderReflection first = decode(FDXI_BASE64);
        ShaderReflection second = decode(FDXI_BASE64);

        assertEquals(first, second);
        assertEquals(first.physicalHash(), second.physicalHash());
        assertEquals(first.fullHash(), second.fullHash());

        byte[] bytes = Base64.getMimeDecoder().decode(FDXI_BASE64);
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
        assertThrows(FdxException.class, () ->
                ShaderReflection.fromRuntime(RuntimeShaderReflection.fromBytes(trailing)));
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 1);
        assertThrows(FdxException.class, () ->
                ShaderReflection.fromRuntime(RuntimeShaderReflection.fromBytes(truncated)));
    }

    @Test
    void semanticOverlayPreservesPhysicalHashButChangesFullHash() {
        ShaderReflection physical = decode(FDXI_BASE64);
        ShaderReflection semantic = physical.withSemanticOverlay(ShaderSemanticOverlay.of(
                ShaderBindingSemantic.builder(0, 0, "frameUniforms")
                        .semantics(ShaderParameterDomain.MIXED, ShaderUpdateFrequency.MIXED)
                        .parameters(
                                ShaderParameterSemantic.of("transform", "objectTransform",
                                        ShaderParameterDomain.OBJECT_DRAW, ShaderUpdateFrequency.DRAW),
                                ShaderParameterSemantic.of("tint", "materialTint",
                                        ShaderParameterDomain.MATERIAL, ShaderUpdateFrequency.ON_CHANGE))
                        .build()));

        assertTrue(physical.physicallyEquivalent(semantic));
        assertEquals(physical.physicalHash(), semantic.physicalHash());
        assertNotEquals(physical.fullHash(), semantic.fullHash());
        assertEquals("frameUniforms", semantic.requireBinding(0, 0).stableId());
        assertEquals("tint", semantic.requireBinding(0, 0).bufferLayout()
                .requireHandle("materialTint").path());
        assertEquals("materialTint", semantic.requireBinding(0, 0).bufferLayout().parameter(1).stableId());
        assertFalse(physical.equals(semantic));
    }

    private static ShaderReflection decode(String encoded) {
        return ShaderReflection.fromRuntime(runtimeFixture(encoded));
    }

    public static RuntimeShaderReflection runtimeFixture() {
        return runtimeFixture(FDXI_BASE64);
    }

    private static RuntimeShaderReflection runtimeFixture(String encoded) {
        return RuntimeShaderReflection.fromBytes(Base64.getMimeDecoder().decode(encoded));
    }

    private static ShaderParameterLayout expectedUniformLayout() {
        ShaderValueType vec3 = ShaderValueType.vector(ShaderScalarType.F32, 3).named("vec3<f32>");
        ShaderValueType vec4 = ShaderValueType.vector(ShaderScalarType.F32, 4).named("vec4<f32>");
        ShaderValueType mat4 = ShaderValueType.matrix(ShaderScalarType.F32, 4, 4, 16).named("mat4x4<f32>");
        ShaderValueType mat2x3 = ShaderValueType.matrix(ShaderScalarType.F32, 2, 3, 16).named("mat2x3<f32>");
        ShaderValueType weights = ShaderValueType.array(vec4, 2, 16).named("array<vec4<f32>, 2>");
        ShaderValueType inner = ShaderValueType.array(mat2x3, 3, 32)
                .named("array<mat2x3<f32>, 3>");
        ShaderValueType nested = ShaderValueType.array(inner, 2, 96)
                .named("array<array<mat2x3<f32>, 3>, 2>");
        return ShaderParameterLayout.of(304, 16,
                ShaderParameter.builder("transform", "transform", mat4, 0, 64, 16)
                        .minimumRequiredSize(64)
                        .matrixStride(16)
                        .build(),
                ShaderParameter.builder("tint", "tint", vec3, 64, 12, 16)
                        .minimumRequiredSize(12)
                        .build(),
                ShaderParameter.builder("weights", "weights", weights, 80, 32, 16)
                        .minimumRequiredSize(32)
                        .arrayStride(16)
                        .build(),
                ShaderParameter.builder("nested", "nested", nested, 112, 192, 16)
                        .minimumRequiredSize(192)
                        .arrayStride(96)
                        .matrixStride(16)
                        .build());
    }

    private static ShaderReflection expectedCompleteReflection() {
        ShaderValueType vec2 = ShaderValueType.vector(ShaderScalarType.F32, 2);
        ShaderValueType vec3 = ShaderValueType.vector(ShaderScalarType.F32, 3);
        ShaderValueType vec4 = ShaderValueType.vector(ShaderScalarType.F32, 4);
        ShaderStageVariable vertexPosition = ShaderStageVariable.of("position", "position", 0, -1, -1,
                vec3, ShaderInterpolation.PERSPECTIVE, ShaderInterpolationSampling.CENTER);
        ShaderStageVariable vertexUv = ShaderStageVariable.of("<retval>.uv", "uv", 0, -1, -1,
                vec2, ShaderInterpolation.PERSPECTIVE, ShaderInterpolationSampling.CENTER);
        ShaderStageVariable fragmentUv = ShaderStageVariable.of("input.uv", "uv", 0, -1, -1,
                vec2, ShaderInterpolation.PERSPECTIVE, ShaderInterpolationSampling.CENTER);
        ShaderStageVariable fragmentColor = ShaderStageVariable.of("<retval>", "", 0, -1, -1,
                vec4, ShaderInterpolation.PERSPECTIVE, ShaderInterpolationSampling.CENTER);

        ShaderEntryPoint vertex = ShaderEntryPoint.builder("vs_main", ShaderStage.VERTEX)
                .builtins(ShaderBuiltinUsage.POSITION | ShaderBuiltinUsage.INSTANCE_INDEX, -1)
                .inputs(vertexPosition)
                .outputs(vertexUv)
                .resources(ShaderResourceUse.of(0, 0, 304))
                .build();
        ShaderEntryPoint fragment = ShaderEntryPoint.builder("fs_main", ShaderStage.FRAGMENT)
                .builtins(ShaderBuiltinUsage.POSITION, -1)
                .inputs(fragmentUv)
                .outputs(fragmentColor)
                .resources(ShaderResourceUse.of(1, 0, 0), ShaderResourceUse.of(1, 1, 0))
                .build();
        ShaderEntryPoint compute = ShaderEntryPoint.builder("cs_main", ShaderStage.COMPUTE)
                .fixedWorkgroupSize(8, 4, 2)
                .builtins(ShaderBuiltinUsage.GLOBAL_INVOCATION_ID, -1)
                .resources(ShaderResourceUse.of(0, 1, 4))
                .build();

        ShaderParameterLayout uniformLayout = expectedUniformLayout();
        ShaderBinding uniform = ShaderBinding.builder(0, 0, "uniforms", ShaderResourceKind.UNIFORM_BUFFER)
                .visibility(ShaderStageVisibility.VERTEX)
                .access(ShaderResourceAccess.READ)
                .buffer(304, 304, 16, uniformLayout)
                .build();
        ShaderValueType scalar = ShaderValueType.scalar(ShaderScalarType.F32).named("f32");
        ShaderValueType runtimeValues = ShaderValueType.runtimeArray(scalar, 4).named("array<f32>");
        ShaderParameterLayout storageLayout = ShaderParameterLayout.of(4, 4,
                ShaderParameter.builder("values", "values", runtimeValues, 0, 4, 4)
                        .minimumRequiredSize(4)
                        .arrayStride(4)
                        .build());
        ShaderBinding storage = ShaderBinding.builder(0, 1, "storage_data", ShaderResourceKind.STORAGE_BUFFER)
                .visibility(ShaderStageVisibility.COMPUTE)
                .access(ShaderResourceAccess.READ_WRITE)
                .buffer(4, 4, 4, storageLayout)
                .build();
        ShaderBinding texture = ShaderBinding.builder(1, 0, "color_texture",
                        ShaderResourceKind.SAMPLED_TEXTURE)
                .visibility(ShaderStageVisibility.FRAGMENT)
                .access(ShaderResourceAccess.READ)
                .texture(ShaderTextureDimension.D2, ShaderTextureSampleType.UNKNOWN_FILTERABLE)
                .build();
        ShaderBinding sampler = ShaderBinding.builder(1, 1, "color_sampler", ShaderResourceKind.SAMPLER)
                .visibility(ShaderStageVisibility.FRAGMENT)
                .access(ShaderResourceAccess.NONE)
                .samplerKind(ShaderSamplerKind.UNKNOWN_FILTERING)
                .build();

        return ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU)
                .entryPoints(vertex, fragment, compute)
                .bindings(uniform, storage, texture, sampler)
                .requiredCapabilities()
                .build();
    }
}
