package io.github.libfdx.graphics.shadergraph.node;

import io.github.libfdx.graphics.shader.target.ShaderBindingRemap;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shader.target.ShaderArtifactFormat;
import io.github.libfdx.graphics.shader.target.ShaderArtifactFormats;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.target.ShaderCompilerRegistry;
import io.github.libfdx.graphics.shader.target.ShaderEntryPointRemap;
import io.github.libfdx.graphics.shader.target.ShaderEntryPointSelection;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetCacheKeys;
import io.github.libfdx.graphics.shader.target.ShaderTargetCompileRequest;
import io.github.libfdx.graphics.shader.target.ShaderTargetCompileResult;
import io.github.libfdx.graphics.shader.target.ShaderTargetCompiler;
import io.github.libfdx.graphics.shader.target.ShaderTargetEnvironment;
import io.github.libfdx.graphics.shader.target.ShaderTargetId;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;
import io.github.libfdx.graphics.shader.target.ShaderVerificationRequirement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphRegistryIntegrationTest {
    private static final ShaderTargetId TARGET =
            ShaderTargetId.of("shader-graph-test");
    private static final ShaderArtifactFormat FORMAT =
            ShaderArtifactFormats.WGSL_TEXT;
    private static final ShaderTargetEnvironment ENVIRONMENT =
            ShaderTargetEnvironment.builder("shader-graph-test-v1", TARGET, FORMAT)
                    .consumer("test", "1")
                    .build();

    @Test
    void graphAndHandwrittenWgslUseTheSamePhase2RegistryContract() {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "registry_surface", ShaderGraphKind.SURFACE);
        ShaderGraphType vec4 =
                ShaderGraphType.vector(ShaderScalarType.F32, 4);
        builder.output("color", builder.constant("color",
                ShaderGraphLiteral.composite(vec4,
                        ShaderGraphLiteral.f32(1), ShaderGraphLiteral.f32(0),
                        ShaderGraphLiteral.f32(0), ShaderGraphLiteral.f32(1))));
        ShaderGraphCompileResult graph = new ShaderGraphCompiler().compile(
                builder.build(), null);
        assertTrue(graph.success());

        String handwritten = """
                @vertex fn fdx_graph_vertex(@builtin(vertex_index) index: u32)
                        -> @builtin(position) vec4<f32> {
                    return vec4<f32>(f32(index), 0.0, 0.0, 1.0);
                }
                @fragment fn fdx_graph_fragment() -> @location(0) vec4<f32> {
                    return vec4<f32>(1.0);
                }
                """;
        PassThroughCompiler compiler = new PassThroughCompiler();
        ShaderCompilerRegistry registry = ShaderCompilerRegistry.builder()
                .compiler(compiler)
                .build();

        ShaderTargetCompileResult generated = registry.compile(
                request("generated", graph.wgsl()));
        ShaderTargetCompileResult coded = registry.compile(
                request("handwritten", handwritten));

        assertTrue(generated.success());
        assertTrue(coded.success());
        assertEquals(graph.wgsl(), generated.artifact()
                .find(ShaderArtifactStage.MODULE, "").text());
        assertEquals(handwritten, coded.artifact()
                .find(ShaderArtifactStage.MODULE, "").text());
        assertEquals(2, compiler.compileCount);
    }

    private static ShaderTargetCompileRequest request(String label, String wgsl) {
        return ShaderTargetCompileRequest.builder(label, wgsl,
                        TARGET, FORMAT, ENVIRONMENT)
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .entryPoints(
                        ShaderEntryPointSelection.of(ShaderArtifactStage.VERTEX,
                                "fdx_graph_vertex"),
                        ShaderEntryPointSelection.of(ShaderArtifactStage.FRAGMENT,
                                "fdx_graph_fragment"))
                .verification(ShaderVerificationRequirement.PROVIDER_PIPELINE)
                .build();
    }

    private static final class PassThroughCompiler
            implements ShaderTargetCompiler {
        private static final ShaderCompilerId ID =
                ShaderCompilerId.of("shader-graph-pass-through");
        private int compileCount;

        @Override
        public ShaderCompilerId id() {
            return ID;
        }

        @Override
        public String version() {
            return "1";
        }

        @Override
        public ShaderTargetId[] targets() {
            return new ShaderTargetId[] { TARGET };
        }

        @Override
        public boolean supports(ShaderTargetCompileRequest request) {
            return TARGET.equals(request.target())
                    && FORMAT.equals(request.format());
        }

        @Override
        public ShaderTargetCompileResult compile(
                ShaderTargetCompileRequest request) {
            compileCount++;
            ShaderEntryPointRemap[] entries = {
                    ShaderEntryPointRemap.of(ShaderArtifactStage.VERTEX,
                            "fdx_graph_vertex", "fdx_graph_vertex"),
                    ShaderEntryPointRemap.of(ShaderArtifactStage.FRAGMENT,
                            "fdx_graph_fragment", "fdx_graph_fragment")
            };
            ShaderReflection reflection = ShaderReflection.empty();
            ShaderTranslatedInterface translated = ShaderTranslatedInterface.of(
                    reflection, reflection, entries,
                    new io.github.libfdx.graphics.shader.target.ShaderBindingRemap[0]);
            ShaderTargetArtifact artifact = ShaderTargetArtifact.compiled(
                    request.target(), request.format(), request.environment(),
                    new ShaderStageArtifact[] {
                            ShaderStageArtifact.text(ShaderArtifactStage.MODULE,
                                    "", FORMAT, request.wgsl())
                    }, translated, id(), version(),
                    ShaderTargetCacheKeys.compilation(request, id(), version()));
            return ShaderTargetCompileResult.success(artifact);
        }
    }
}
