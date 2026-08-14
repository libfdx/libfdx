package io.github.libfdx.backend.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.ShapeRenderer;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

final class WebBootstrapShaderArtifactsTest {
    @Test
    void artifactsMatchTheCanonicalUiKitRendererSources() throws Exception {
        String[] sources = {
                source(ShapeRenderer.class, "SHADER"),
                source(SpriteBatch.class, "SPRITE_SHADER_SOURCE"),
                source(SpriteBatch.class, "WHITE_SPRITE_SHADER_SOURCE"),
                source(SpriteBatch.class, "INSTANCED_SPRITE_SHADER_SOURCE"),
                source(SpriteBatch.class, "COMPACT_INSTANCED_SPRITE_SHADER_SOURCE")
        };

        for (String source : sources) {
            assertTrue(WebBootstrapShaderArtifacts.contains(source));
            assertArtifact(source, RuntimeShaderCompileStage.VERTEX, "vertexMain");
            assertArtifact(source, RuntimeShaderCompileStage.FRAGMENT, "fragmentMain");
        }
    }

    @Test
    void unrelatedShadersStillRequireTheNativeRuntimeCompiler() {
        String source = "@compute @workgroup_size(1) fn computeMain() {}";

        assertFalse(WebBootstrapShaderArtifacts.contains(source));
        assertNull(WebBootstrapShaderArtifacts.compile(RuntimeShaderCompileRequest
                .builder(source, RuntimeShaderCompileTarget.WEBGL_GLSL_ES)
                .stage(RuntimeShaderCompileStage.COMPUTE)
                .entryPoint("computeMain")
                .glslEsProfile("300")
                .build()));
    }

    @Test
    void bootstrapCompilerIsOnlyAdvertisedToWebGl() {
        assertTrue(WebRuntimeCoreProvider.supportsBootstrapCompiler(ProviderId.of("webgl")));
        assertFalse(WebRuntimeCoreProvider.supportsBootstrapCompiler(ProviderId.of("wgpu")));
        assertFalse(WebRuntimeCoreProvider.supportsBootstrapCompiler(ProviderId.of("webgpu")));
    }

    private static void assertArtifact(String source, RuntimeShaderCompileStage stage,
            String entryPoint) {
        RuntimeShaderCompileResult result = WebBootstrapShaderArtifacts.compile(RuntimeShaderCompileRequest
                .builder(source, RuntimeShaderCompileTarget.WEBGL_GLSL_ES)
                .stage(stage)
                .entryPoint(entryPoint)
                .glslEsProfile("300")
                .build());

        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.outputText().startsWith("#version 300 es"));
        assertTrue(result.hasReflection());
        assertTrue(result.hasTargetInterface());
        assertEquals(1, result.targetInterface().entryPoints().length);
        assertEquals(entryPoint, result.targetInterface().entryPoints()[0].sourceName());
    }

    private static String source(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
