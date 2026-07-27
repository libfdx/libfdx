package io.github.libfdx.graphics.g2d;

import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardSpriteTechniqueTest {
    @Test
    void coversEveryStableSpriteAbiWithTintValidWgsl() {
        ShaderGraphTechnique technique = StandardSpriteTechnique.create(
                TextureFormat.RGBA8_UNORM, 1);
        assertEquals(SpriteShaderAbi.values().length,
                technique.passes().length);
        for (SpriteShaderAbi abi : SpriteShaderAbi.values()) {
            assertNotNull(technique.pass(abi.passId()));
        }
        ShaderGraphTechniqueCompileResult compiled =
                new ShaderGraphTechniqueCompiler().compile(
                        technique,
                        ShaderGraphCompileOptions.builder().build());
        assertTrue(compiled.success(), diagnostics(compiled));
        for (var pass : compiled.passes()) {
            assertTrue(pass.variants()[0].compilation().wgsl()
                    .contains("@group(0) @binding(0)"));
            assertEquals(pass.pass().passId(),
                    ShaderPassId.of(pass.pass().passId().value()));
        }
    }

    private static String diagnostics(
            ShaderGraphTechniqueCompileResult result) {
        StringBuilder message = new StringBuilder();
        for (var diagnostic : result.diagnostics()) {
            message.append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return message.toString();
    }
}
