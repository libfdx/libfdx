package io.github.libfdx.graphics.internal;

import io.github.libfdx.graphics.shader.reflection.ShaderAttribute;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBindingType;
import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterDomain;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderUpdateFrequency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuiltInPbrShaderManifestTest {
    @Test
    void checkedInManifestsAreCompleteAndCarryTheCanonicalLayouts() {
        ShaderReflection staticReflection = BuiltInPbrShaderManifest.staticReflection();
        ShaderReflection skinnedReflection = BuiltInPbrShaderManifest.skinnedReflection();

        assertTrue(staticReflection.complete());
        assertTrue(skinnedReflection.complete());
        assertEquals(2, staticReflection.entryPointCount());
        assertEquals(2, skinnedReflection.entryPointCount());
        assertEquals(19, staticReflection.bindingCount());
        assertEquals(19, skinnedReflection.bindingCount());
        assertEquals(1216, staticReflection.requireBinding(1, 0).minimumBindingSize());
        assertEquals(5328, skinnedReflection.requireBinding(1, 0).minimumBindingSize());
        assertEquals(40, staticReflection.requireBinding(1, 0).bufferLayout().parameterCount());
        assertEquals(42, skinnedReflection.requireBinding(1, 0).bufferLayout().parameterCount());
        assertEquals(0, staticReflection.requireBinding(1, 0).bufferLayout()
                .requireHandle("model").byteOffset());
        assertEquals(1232, skinnedReflection.requireBinding(1, 0).bufferLayout()
                .requireHandle("boneMatrices").byteOffset());
        assertNotEquals(staticReflection.physicalHash(), skinnedReflection.physicalHash());
        assertEquals(64, BuiltInPbrShaderManifest.staticSourceHash().length());
        assertEquals(64, BuiltInPbrShaderManifest.skinnedSourceHash().length());
    }

    @Test
    void semanticOverlayPreservesTypedOwnershipAndFrequency() {
        ShaderBinding materialTexture = BuiltInPbrShaderManifest.staticReflection()
                .requireBinding(0, 0);
        ShaderBinding shadowTexture = BuiltInPbrShaderManifest.staticReflection()
                .requireBinding(0, 10);
        ShaderBinding uniforms = BuiltInPbrShaderManifest.staticReflection()
                .requireBinding(1, 0);

        assertEquals(ShaderParameterDomain.MATERIAL, materialTexture.domain());
        assertEquals(ShaderUpdateFrequency.ON_CHANGE, materialTexture.updateFrequency());
        assertEquals(ShaderParameterDomain.ENVIRONMENT_PASS, shadowTexture.domain());
        assertEquals(ShaderUpdateFrequency.PASS, shadowTexture.updateFrequency());
        assertEquals(ShaderParameterDomain.MIXED, uniforms.domain());
        assertEquals(ShaderUpdateFrequency.MIXED, uniforms.updateFrequency());

        assertSemantics(parameter("model"), ShaderParameterDomain.OBJECT_DRAW,
                ShaderUpdateFrequency.DRAW);
        assertSemantics(parameter("viewProjection"), ShaderParameterDomain.FRAME_VIEW,
                ShaderUpdateFrequency.FRAME);
        assertSemantics(parameter("textureFlags"), ShaderParameterDomain.MATERIAL,
                ShaderUpdateFrequency.ON_CHANGE);
        assertSemantics(parameter("shadowParams"), ShaderParameterDomain.ENVIRONMENT_PASS,
                ShaderUpdateFrequency.PASS);
    }

    @Test
    void authoritativeDetectionRejectsCoarseOrUnrelatedReflections() {
        ShaderReflection coarse = ShaderReflection.of(new ShaderBinding[] {
                ShaderBinding.of(1, 0, "uniforms", ShaderBindingType.UNIFORM_BUFFER)
        }, new ShaderAttribute[0]);

        assertTrue(BuiltInPbrShaderManifest.matches(
                BuiltInPbrShaderManifest.staticReflection()));
        assertTrue(BuiltInPbrShaderManifest.matches(
                BuiltInPbrShaderManifest.skinnedReflection()));
        assertFalse(BuiltInPbrShaderManifest.matches(coarse));
        assertFalse(BuiltInPbrShaderManifest.matches(ShaderReflection.empty()));
        assertFalse(BuiltInPbrShaderManifest.matches(null));
    }

    private static ShaderParameter parameter(String name) {
        for (ShaderParameter parameter : BuiltInPbrShaderManifest.staticReflection()
                .requireBinding(1, 0).bufferLayout().parameters()) {
            if (parameter.name().equals(name)) {
                return parameter;
            }
        }
        throw new AssertionError("Missing PBR parameter: " + name);
    }

    private static void assertSemantics(ShaderParameter parameter,
            ShaderParameterDomain domain, ShaderUpdateFrequency frequency) {
        assertEquals(domain, parameter.domain());
        assertEquals(frequency, parameter.updateFrequency());
    }
}
