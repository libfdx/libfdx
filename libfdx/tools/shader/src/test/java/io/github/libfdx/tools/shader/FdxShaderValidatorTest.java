package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.ShaderBundle;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.ShaderProfile;
import io.github.libfdx.graphics.ShaderProfileValidator;
import io.github.libfdx.graphics.ShaderTarget;
import io.github.libfdx.graphics.ShaderValidationDiagnostic;
import io.github.libfdx.graphics.ShaderValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the fdx shader validator test scenario.
 *
 * @author xpenatan
 */
final class FdxShaderValidatorTest {
    private static final String SIMPLE_WGSL = """
            @vertex
            fn vs_main(@builtin(vertex_index) vertexIndex : u32) -> @builtin(position) vec4<f32> {
                let x = f32(i32(vertexIndex) - 1);
                return vec4<f32>(x, 0.0, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4<f32> {
                return vec4<f32>(1.0, 1.0, 1.0, 1.0);
            }
            """;
    private static final String VERTEX_GLSL = """
            #version 330 core
            void main() {
                gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT_GLSL = """
            #version 330 core
            out vec4 color;
            void main() {
                color = vec4(1.0, 1.0, 1.0, 1.0);
            }
            """;

    @Test
    void portableWebgl2RejectsComputeAndStorageBuffers() {
        ShaderValidationResult result = ShaderProfileValidator.validateWgsl(ShaderProfile.PORTABLE_WEBGL2, """
                @group(0) @binding(0) var<storage, read> values : array<f32>;

                @compute @workgroup_size(1)
                fn cs_main() {
                }
                """);

        assertFalse(result.isSuccess());
        assertHasDiagnostic(result, "shader.webgl2.compute");
        assertHasDiagnostic(result, "shader.webgl2.storage-buffer");
    }

    @Test
    void portableWebgpuRejectsBackendSpecificFeatures() {
        ShaderValidationResult result = ShaderProfileValidator.validateWgsl(ShaderProfile.PORTABLE_WEBGPU, """
                enable f16;

                @fragment
                fn fs_main() -> @location(0) vec4<f32> {
                    return vec4<f32>(1.0, 1.0, 1.0, 1.0);
                }
                """);

        assertFalse(result.isSuccess());
        assertHasDiagnostic(result, "shader.webgpu.extension");
    }

    @Test
    void sourceDirectiveOverridesDefaultProfile(@TempDir Path tempDir) throws IOException {
        Path shader = tempDir.resolve("compute.wgsl");
        Files.writeString(shader, """
                // @fdx.profile webgl2
                @compute @workgroup_size(1)
                fn cs_main() {
                }
                """, StandardCharsets.UTF_8);

        FdxShaderValidationReport.Entry entry =
                FdxShaderValidator.validateFile(shader, ShaderProfile.PORTABLE_WEBGPU);

        assertEquals(ShaderProfile.PORTABLE_WEBGL2.id(), entry.profileId());
        assertEquals(1, entry.errorCount());
        assertEquals("shader.webgl2.compute", entry.diagnostics()[0].code());
    }

    @Test
    void bundleReportsGlslFallbackForGlesTargets() {
        ShaderBundle bundle = ShaderBundle.builder("simple")
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .wgsl(SIMPLE_WGSL)
                .glsl(VERTEX_GLSL, FRAGMENT_GLSL)
                .build();

        assertTrue(bundle.hasTarget(ShaderTarget.GLES_GLSL_ES));
        ShaderModuleDescriptor descriptor = bundle.descriptorForTarget(ShaderTarget.GLES_GLSL_ES);
        assertEquals(ShaderLanguage.GLSL, descriptor.language());
        assertEquals(VERTEX_GLSL, descriptor.glslVertexSource());
        assertEquals(FRAGMENT_GLSL, descriptor.glslFragmentSource());
    }

    private static void assertHasDiagnostic(ShaderValidationResult result, String code) {
        for (ShaderValidationDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.code().equals(code)) {
                return;
            }
        }
        throw new AssertionError("Missing shader diagnostic: " + code);
    }
}
