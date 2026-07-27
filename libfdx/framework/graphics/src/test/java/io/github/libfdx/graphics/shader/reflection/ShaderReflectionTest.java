package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.shader.ShaderOverride;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.target.ShaderSemanticOverlay;
import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderReflectionTest {
    @Test
    void compatibilityFactoryRemainsIncompleteAndDefensivelyCopied() {
        ShaderBinding[] bindings = {
                ShaderBinding.of(0, 0, "uniforms", ShaderBindingType.UNIFORM_BUFFER)
        };
        ShaderAttribute[] attributes = {
                ShaderAttribute.of(0, "position", VertexFormat.FLOAT32X3)
        };
        ShaderReflection reflection = ShaderReflection.of(bindings, attributes);
        bindings[0] = null;
        attributes[0] = null;

        assertFalse(reflection.complete());
        assertEquals("uniforms", reflection.binding(0).name());
        assertEquals("position", reflection.attributes()[0].name());
        assertNotSame(reflection.bindings(), reflection.bindings());
        assertNotSame(reflection.attributes(), reflection.attributes());
        assertThrows(FdxException.class, () ->
                reflection.withSemanticOverlay(ShaderSemanticOverlay.empty()));
    }

    @Test
    void nestedOverlayUsesCanonicalTraversalPathsWithDuplicateLeafNames() {
        ShaderValueType f32 = ShaderValueType.scalar(ShaderScalarType.F32);
        ShaderParameter firstValue = ShaderParameter.of("value", f32, 0, 4, 4);
        ShaderParameter secondValue = ShaderParameter.of("value", f32, 16, 4, 4);
        ShaderParameter first = ShaderParameter.builder("first", "first", ShaderValueType.structure("First"),
                        0, 16, 16)
                .members(firstValue)
                .build();
        ShaderParameter second = ShaderParameter.builder("second", "second", ShaderValueType.structure("Second"),
                        16, 16, 16)
                .members(secondValue)
                .build();
        ShaderParameterLayout layout = ShaderParameterLayout.of(32, 16, first, second);
        ShaderBinding binding = ShaderBinding.builder(0, 0, "uniforms", ShaderResourceKind.UNIFORM_BUFFER)
                .visibility(ShaderStageVisibility.VERTEX)
                .access(ShaderResourceAccess.READ)
                .buffer(32, 32, 16, layout)
                .build();
        ShaderReflection reflection = ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] {
                        ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX)
                                .resources(ShaderResourceUse.of(0, 0, 32))
                                .build()
                }, new ShaderBinding[] { binding }, new String[0]);

        ShaderReflection overlaid = reflection.withSemanticOverlay(ShaderSemanticOverlay.of(
                ShaderBindingSemantic.builder(0, 0, "uniforms")
                        .parameters(ShaderParameterSemantic.of("second.value", "secondValue",
                                ShaderParameterDomain.OBJECT_DRAW, ShaderUpdateFrequency.DRAW))
                        .build()));

        ShaderParameterLayout updated = overlaid.requireBinding(0, 0).bufferLayout();
        assertEquals(0, updated.requireHandle("first.value").byteOffset());
        assertEquals(16, updated.requireHandle("secondValue").byteOffset());
        assertTrue(reflection.physicallyEquivalent(overlaid));
        assertThrows(FdxException.class, () -> reflection.withSemanticOverlay(ShaderSemanticOverlay.of(
                ShaderBindingSemantic.builder(0, 0, "uniforms")
                        .parameters(ShaderParameterSemantic.of("value", "ambiguous",
                                ShaderParameterDomain.MATERIAL, ShaderUpdateFrequency.ON_CHANGE))
                        .build())));
    }

    @Test
    void physicalEquivalenceAndHashUseTheSameAbiFacts() {
        ShaderReflection original = singleOutputManifest("<retval>", "", "overrideName", 0);
        ShaderReflection sourceRenamed = singleOutputManifest("color", "outputColor", "renamedOverride", 0);
        ShaderReflection locationChanged = singleOutputManifest("<retval>", "", "overrideName", 1);

        assertTrue(original.physicallyEquivalent(sourceRenamed));
        assertEquals(original.physicalHash(), sourceRenamed.physicalHash());
        assertNotEquals(original.fullHash(), sourceRenamed.fullHash());
        assertFalse(original.equals(sourceRenamed));

        assertFalse(original.physicallyEquivalent(locationChanged));
        assertNotEquals(original.physicalHash(), locationChanged.physicalHash());
    }

    private static ShaderReflection singleOutputManifest(String logicalName, String variableName,
            String overrideName, int location) {
        ShaderStageVariable output = ShaderStageVariable.of(logicalName, variableName, location, -1, -1,
                ShaderValueType.vector(ShaderScalarType.F32, 4),
                ShaderInterpolation.PERSPECTIVE, ShaderInterpolationSampling.CENTER);
        ShaderEntryPoint fragment = ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT)
                .outputs(output)
                .overrides(ShaderOverride.of(overrideName, 7, ShaderScalarType.F32, true, true))
                .build();
        return ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[]{fragment}, new ShaderBinding[0], new String[0]);
    }
}
