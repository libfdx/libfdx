package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.RuntimeCore;
import io.github.libfdx.runtime.core.RuntimeCoreProvider;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderModuleDescriptorsTest {
    private RuntimeCoreProvider previousProvider;

    @BeforeEach
    void storeRuntimeProvider() {
        previousProvider = RuntimeCore.provider();
    }

    @AfterEach
    void restoreRuntimeProvider() {
        RuntimeCore.registerProvider(previousProvider);
    }

    @Test
    void publicDescriptorApiIsWgslOnly() {
        assertThrows(NoSuchMethodException.class, () ->
                ShaderModuleDescriptor.class.getMethod("glsl", String.class, String.class, String.class));
        assertThrows(NoSuchMethodException.class, () ->
                ShaderModuleDescriptor.class.getMethod("spirv", String.class, int[].class, int[].class));
        assertThrows(NoSuchMethodException.class, () ->
                ShaderModuleDescriptor.class.getMethod("msl", String.class, String.class));
        assertThrows(NoSuchMethodException.class, () ->
                ShaderModuleDescriptor.class.getMethod("glsl", String.class, String.class));
        assertThrows(NoSuchMethodException.class, () ->
                ShaderModuleDescriptor.class.getMethod("spirv", int[].class, int[].class));
        assertThrows(NoSuchMethodException.class, () ->
                ShaderModuleDescriptor.class.getMethod("msl", String.class));
        assertThrows(NoSuchMethodException.class, () ->
                ShaderModuleDescriptor.class.getMethod("language", ShaderLanguage.class));
    }

    @Test
    void requireGlslTargetCompilesWgslOnlyDescriptor() {
        RecordingCompiler compiler = new RecordingCompiler(request -> {
            if (request.stage() == RuntimeShaderCompileStage.VERTEX) {
                return RuntimeShaderCompileResult.text("""
                        #version 300 es
                        layout(location = 0) out vec4 v_color;
                        void main() {
                            float x = 1.0f;
                            gl_Position = vec4(x);
                        }
                        """);
            }
            return RuntimeShaderCompileResult.text("""
                    #version 300 es
                    layout(location = 0) in vec4 v_color;
                    layout(location = 0) out vec4 fragColor;
                    void main() {
                        fragColor = v_color;
                    }
                    """);
        });
        RuntimeCore.registerProvider(new TestRuntimeCoreProvider(compiler));

        ShaderModuleDescriptor ready = ShaderModuleDescriptors.requireTarget(
                ShaderModuleDescriptor.wgsl("wgsl only", "wgsl source")
                        .entryPoints("vs_main", "fs_main"),
                ShaderTarget.GLES_GLSL_ES,
                "GLES");

        assertTrue(ready.hasSource(ShaderLanguage.GLSL));
        assertEquals(2, compiler.requests.size());
        assertRequest(compiler.requests.get(0), RuntimeShaderCompileTarget.GLES_GLSL_ES,
                RuntimeShaderCompileStage.VERTEX, "vs_main");
        assertRequest(compiler.requests.get(1), RuntimeShaderCompileTarget.GLES_GLSL_ES,
                RuntimeShaderCompileStage.FRAGMENT, "fs_main");
        assertFalse(ready.glslVertexSource().contains("layout(location"));
        assertFalse(ready.glslFragmentSource().contains("layout(location"));
        assertTrue(ready.glslVertexSource().contains("float x = 1.0;"));
    }

    @Test
    void requireSpirvTargetCompilesAndConvertsLittleEndianWords() {
        RecordingCompiler compiler = new RecordingCompiler(request -> {
            if (request.stage() == RuntimeShaderCompileStage.VERTEX) {
                return RuntimeShaderCompileResult.spirv(new byte[] { 3, 2, 1, 0 });
            }
            return RuntimeShaderCompileResult.spirv(new byte[] { 7, 6, 5, 4 });
        });
        RuntimeCore.registerProvider(new TestRuntimeCoreProvider(compiler));

        ShaderModuleDescriptor ready = ShaderModuleDescriptors.requireTarget(
                ShaderModuleDescriptor.wgsl("spirv", "wgsl source"),
                ShaderTarget.VULKAN_SPIRV,
                "Vulkan");

        assertTrue(ready.hasSource(ShaderLanguage.SPIRV));
        assertArrayEquals(new int[] { 0x00010203 }, ready.spirvVertexWords());
        assertArrayEquals(new int[] { 0x04050607 }, ready.spirvFragmentWords());
    }

    @Test
    void requireMslTargetCompilesAndCombinesStages() {
        RecordingCompiler compiler = new RecordingCompiler(request -> {
            if (request.stage() == RuntimeShaderCompileStage.VERTEX) {
                return RuntimeShaderCompileResult.text("""
                        #include <metal_stdlib>
                        using namespace metal;

                        struct VertexOutput {
                            float4 position [[position]];
                        };

                        vertex VertexOutput vs_main() {
                            return VertexOutput();
                        }
                        """);
            }
            return RuntimeShaderCompileResult.text("""
                    #include <metal_stdlib>
                    using namespace metal;

                    struct VertexOutput {
                        float4 position [[position]];
                    };

                    fragment float4 fs_main(VertexOutput input [[stage_in]]) {
                        return float4(1.0);
                    }
                    """);
        });
        RuntimeCore.registerProvider(new TestRuntimeCoreProvider(compiler));

        ShaderModuleDescriptor ready = ShaderModuleDescriptors.requireTarget(
                ShaderModuleDescriptor.wgsl("msl", "wgsl source")
                        .entryPoints("vs_main", "fs_main"),
                ShaderTarget.METAL_MSL,
                "Metal");

        assertTrue(ready.hasSource(ShaderLanguage.MSL));
        assertTrue(ready.mslSource().contains("vertex VertexOutput vs_main()"));
        assertTrue(ready.mslSource().contains("fragment float4 fs_main"));
        assertEquals(1, countOccurrences(ready.mslSource(), "struct VertexOutput"));
    }

    @Test
    void requireHlslTargetCompilesBothStages() {
        RecordingCompiler compiler = new RecordingCompiler(request ->
                RuntimeShaderCompileResult.text(request.stage() == RuntimeShaderCompileStage.VERTEX
                        ? "float4 vs_main() : SV_Position { return 0.0f; }"
                        : "float4 fs_main() : SV_Target { return 1.0f; }"));
        RuntimeCore.registerProvider(new TestRuntimeCoreProvider(compiler));

        ShaderModuleDescriptor ready = ShaderModuleDescriptors.requireTarget(
                ShaderModuleDescriptor.wgsl("hlsl", "wgsl source")
                        .entryPoints("vs_main", "fs_main"),
                ShaderTarget.DIRECTX_HLSL,
                "Direct3D 12");

        assertTrue(ready.hasSource(ShaderLanguage.HLSL));
        assertTrue(ready.hlslVertexSource().contains("vs_main"));
        assertTrue(ready.hlslFragmentSource().contains("fs_main"));
        assertEquals(2, compiler.requests.size());
        assertRequest(compiler.requests.get(0), RuntimeShaderCompileTarget.DIRECTX_HLSL,
                RuntimeShaderCompileStage.VERTEX, "vs_main");
        assertRequest(compiler.requests.get(1), RuntimeShaderCompileTarget.DIRECTX_HLSL,
                RuntimeShaderCompileStage.FRAGMENT, "fs_main");
    }

    @Test
    void missingCompilerForWgslOnlyNativeTargetFailsClearly() {
        RuntimeCore.registerProvider(new TestRuntimeCoreProvider(null));

        FdxException exception = assertThrows(FdxException.class, () ->
                ShaderModuleDescriptors.requireTarget(ShaderModuleDescriptor.wgsl("missing", "wgsl source"),
                        ShaderTarget.OPENGL_GLSL, "OpenGL"));

        assertTrue(exception.getMessage().contains("runtime shader compiler is not available"));
        assertTrue(exception.getMessage().contains("only provides WGSL"));
    }

    @Test
    void nativeTargetWithoutWgslFailsEvenIfDescriptorIsGeneratedOutput() {
        RuntimeCore.registerProvider(new TestRuntimeCoreProvider(null));

        FdxException exception = assertThrows(FdxException.class, () ->
                ShaderModuleDescriptors.requireTarget(
                        ShaderModuleDescriptor.generatedGlsl("generated", "vertex", "fragment"),
                        ShaderTarget.OPENGL_GLSL,
                        "OpenGL"));

        assertTrue(exception.getMessage().contains("must provide WGSL"));
    }

    @Test
    void compilerDiagnosticsSurfaceInFailureMessage() {
        RecordingCompiler compiler = new RecordingCompiler(request -> RuntimeShaderCompileResult.failure(
                new RuntimeShaderCompileDiagnostic[] {
                        RuntimeShaderCompileDiagnostic.of("line 4: expected expression")
                }));
        RuntimeCore.registerProvider(new TestRuntimeCoreProvider(compiler));

        FdxException exception = assertThrows(FdxException.class, () ->
                ShaderModuleDescriptors.requireTarget(ShaderModuleDescriptor.wgsl("bad", "wgsl source"),
                        ShaderTarget.VULKAN_SPIRV, "Vulkan"));

        assertTrue(exception.getMessage().contains("Could not compile WGSL shader bad"));
        assertTrue(exception.getMessage().contains("line 4: expected expression"));
    }

    @Test
    void compilerOutputKindMismatchFailsClearly() {
        RecordingCompiler compiler = new RecordingCompiler(request -> RuntimeShaderCompileResult.text("not spirv"));
        RuntimeCore.registerProvider(new TestRuntimeCoreProvider(compiler));

        FdxException exception = assertThrows(FdxException.class, () ->
                ShaderModuleDescriptors.requireTarget(ShaderModuleDescriptor.wgsl("wrong kind", "wgsl source"),
                        ShaderTarget.VULKAN_SPIRV, "Vulkan"));

        assertTrue(exception.getMessage().contains("Runtime shader compiler returned TEXT for SPIR-V target"));
    }

    private static void assertRequest(RuntimeShaderCompileRequest request, RuntimeShaderCompileTarget target,
            RuntimeShaderCompileStage stage, String entryPoint) {
        assertEquals(target, request.target());
        assertEquals(stage, request.stage());
        assertEquals(entryPoint, request.entryPoint());
        assertEquals("330", request.glslProfile());
        assertEquals("300", request.glslEsProfile());
    }

    private static int countOccurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while (offset < text.length()) {
            int index = text.indexOf(value, offset);
            if (index < 0) {
                return count;
            }
            count++;
            offset = index + value.length();
        }
        return count;
    }

    private interface CompileAction {
        RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request);
    }

    private static final class RecordingCompiler implements RuntimeShaderCompiler {
        private final CompileAction action;
        private final List<RuntimeShaderCompileRequest> requests = new ArrayList<>();

        private RecordingCompiler(CompileAction action) {
            this.action = action;
        }

        @Override
        public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
            requests.add(request);
            return action.compile(request);
        }
    }

    private static final class TestRuntimeCoreProvider implements RuntimeCoreProvider {
        private final RuntimeShaderCompiler compiler;

        private TestRuntimeCoreProvider(RuntimeShaderCompiler compiler) {
            this.compiler = compiler;
        }

        @Override
        public FontRasterizer fontRasterizer() {
            return null;
        }

        @Override
        public RuntimeShaderCompiler shaderCompiler() {
            return compiler;
        }

        @Override
        public boolean nativeFontRasterizerAvailable() {
            return false;
        }

        @Override
        public boolean nativeShaderCompilerAvailable() {
            return compiler != null;
        }
    }
}
