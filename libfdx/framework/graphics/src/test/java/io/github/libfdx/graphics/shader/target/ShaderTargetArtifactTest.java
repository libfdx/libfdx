package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderReflectionDecoderTest;
import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ShaderTargetArtifactTest {
    private static final ShaderArtifactFormat FORMAT = ShaderArtifactFormats.GLSL_TEXT;
    private static final ShaderStageArtifact[] STAGES = {
            ShaderStageArtifact.text(ShaderArtifactStage.VERTEX, "vs_main", FORMAT, "vertex")
    };

    @Test
    void invalidArrayMembersFailWithFrameworkDiagnosticsBeforeSorting() {
        ShaderReflection reflection = ShaderReflection.fromRuntime(ShaderReflectionDecoderTest.runtimeFixture());

        assertThrows(FdxException.class, () -> ShaderTargetCompileRequest.builder(
                        "invalid", "fn helper() {}", ShaderTargets.OPENGL_GLSL, FORMAT,
                        ShaderTargetEnvironments.OPENGL_33_GLSL_330)
                .entryPoints((ShaderEntryPointSelection) null)
                .build());
        assertThrows(FdxException.class, () -> ShaderTranslatedInterface.of(
                reflection, reflection, new ShaderEntryPointRemap[] { null },
                identityBindings(reflection, "resource")));
        assertThrows(FdxException.class, () -> ShaderTargetArtifact.compiled(
                ShaderTargets.OPENGL_GLSL, FORMAT,
                ShaderTargetEnvironments.OPENGL_33_GLSL_330,
                new ShaderStageArtifact[] { null },
                translated(reflection, "resource"), ShaderCompilerId.of("test.compiler"),
                "1", ""));
    }

    @Test
    void fallbackArtifactHashIncludesTranslatedResourceInterface() {
        ShaderReflection reflection = ShaderReflection.fromRuntime(ShaderReflectionDecoderTest.runtimeFixture());
        ShaderTargetArtifact first = artifact(reflection, "resource-a");
        ShaderTargetArtifact second = artifact(reflection, "resource-b");

        assertNotEquals(first.compileCacheKey(), second.compileCacheKey());
    }

    private static ShaderTargetArtifact artifact(ShaderReflection reflection, String firstTargetName) {
        return ShaderTargetArtifact.compiled(ShaderTargets.OPENGL_GLSL, FORMAT,
                ShaderTargetEnvironments.OPENGL_33_GLSL_330, STAGES,
                translated(reflection, firstTargetName), ShaderCompilerId.of("test.compiler"),
                "1", "");
    }

    private static ShaderTranslatedInterface translated(
            ShaderReflection reflection, String firstTargetName) {
        return ShaderTranslatedInterface.of(reflection, reflection,
                new ShaderEntryPointRemap[] {
                        ShaderEntryPointRemap.of(ShaderArtifactStage.VERTEX,
                                "vs_main", "vs_main")
                }, identityBindings(reflection, firstTargetName));
    }

    private static ShaderBindingRemap[] identityBindings(
            ShaderReflection reflection, String firstTargetName) {
        ShaderBinding[] bindings = reflection.bindings();
        ShaderBindingRemap[] remaps = new ShaderBindingRemap[bindings.length];
        for (int i = 0; i < bindings.length; i++) {
            ShaderBinding binding = bindings[i];
            remaps[i] = ShaderBindingRemap.of(binding.group(), binding.binding(),
                    "group-binding", binding.group(), binding.binding(),
                    i == 0 ? firstTargetName : binding.name(), ShaderBindingRemapKind.DIRECT);
        }
        return remaps;
    }
}
