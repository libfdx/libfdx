package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.shader.ShaderOverride;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.target.ShaderSemanticOverlay;
import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void nestedPhysicalLayoutsCompareEveryMemberWithoutDependingOnNames() {
        ShaderParameterLayout first = nestedLayout("first", ShaderScalarType.F32, 4, true);
        assertTrue(first.physicallyEquivalent(first));
        assertFalse(first.physicallyEquivalent(null));
        assertTrue(first.physicallyEquivalent(nestedLayout("renamed", ShaderScalarType.F32, 4, true)));
        assertFalse(first.physicallyEquivalent(nestedLayout("first", ShaderScalarType.I32, 4, true)));
        assertFalse(first.physicallyEquivalent(nestedLayout("first", ShaderScalarType.F32, 8, true)));
        assertFalse(first.physicallyEquivalent(nestedLayout("first", ShaderScalarType.F32, 4, false)));
    }

    private static ShaderParameterLayout nestedLayout(String name, ShaderScalarType scalar,
            int offset, boolean includeSecond) {
        ShaderParameter first = ShaderParameter.of("x", ShaderValueType.scalar(ShaderScalarType.F32), 0, 4, 4);
        ShaderParameter second = ShaderParameter.of("y", ShaderValueType.scalar(scalar), offset, 4, 4);
        ShaderParameter structure = ShaderParameter.builder(name, name, ShaderValueType.structure("Pair"), 0, 16, 4)
                .members(includeSecond ? new ShaderParameter[] {first, second} : new ShaderParameter[] {first})
                .build();
        return ShaderParameterLayout.of(16, 4, structure);
    }

    @Test
    void concurrentEquivalentLayoutsNeverAcceptAnIncompatibleLayout() throws Exception {
        ShaderParameterLayout first = nestedLayout("first", ShaderScalarType.F32, 4, true);
        ShaderParameterLayout second = nestedLayout("second", ShaderScalarType.F32, 4, true);
        ShaderParameterLayout third = nestedLayout("third", ShaderScalarType.F32, 4, true);
        ShaderParameterLayout incompatible = nestedLayout("wrong", ShaderScalarType.I32, 4, true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable work = () -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    assertTrue(first.physicallyEquivalent((i & 1) == 0 ? second : third));
                    assertFalse(first.physicallyEquivalent(incompatible));
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        };
        Thread a = new Thread(work);
        Thread b = new Thread(work);
        a.start();
        b.start();
        a.join();
        b.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
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
