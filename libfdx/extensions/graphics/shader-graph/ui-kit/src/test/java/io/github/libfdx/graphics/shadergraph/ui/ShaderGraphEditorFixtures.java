package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphPipelineState;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;

final class ShaderGraphEditorFixtures {
    static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    private ShaderGraphEditorFixtures() {
    }

    static ShaderGraph editableFunction(String id) {
        ShaderNode left = constant("a", 1.0f);
        ShaderNode right = constant("b", 2.0f);
        ShaderNode add = ShaderNode.of("sum", StandardShaderNodes.ADD, 1,
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("in000000", F32),
                        ShaderGraphPort.required("in000001", F32)
                }, new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", F32)
                });
        return ShaderGraph.builder(id, ShaderGraphKind.FUNCTION)
                .nodes(left, right, add)
                .edges(
                        io.github.libfdx.graphics.shadergraph.model.ShaderEdge.of(
                                ShaderEndpoint.of("a", "value"),
                                ShaderEndpoint.of("sum", "in000000")),
                        io.github.libfdx.graphics.shadergraph.model.ShaderEdge.of(
                                ShaderEndpoint.of("b", "value"),
                                ShaderEndpoint.of("sum", "in000001")))
                .outputs(ShaderGraphOutput.of("value", F32,
                        ShaderEndpoint.of("sum", "value")))
                .build();
    }

    static ShaderGraph graph(String id, ShaderGraphKind kind) {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(id, kind);
        if (kind == ShaderGraphKind.VERTEX) {
            ShaderExpression zero = builder.constant("zero",
                    ShaderGraphLiteral.f32(0.0f));
            ShaderExpression one = builder.constant("one",
                    ShaderGraphLiteral.f32(1.0f));
            builder.output("position", ShaderGraphStageSemantic.POSITION,
                    builder.construct("position_value", VEC4,
                            zero, zero, zero, one));
        } else if (kind == ShaderGraphKind.FRAGMENT) {
            builder.output("color", ShaderGraphStageSemantic.location(0),
                    color(builder, "fragment_color", 0.3f, 0.6f, 1.0f));
        } else if (kind == ShaderGraphKind.SURFACE) {
            builder.output("base_color", "baseColor",
                    color(builder, "surface_color", 0.3f, 0.6f, 1.0f));
        } else {
            builder.output("value",
                    builder.constant("value",
                            ShaderGraphLiteral.f32(0.5f)));
        }
        return builder.build();
    }

    static ShaderGraphProgram program(String id) {
        return ShaderGraphProgram.builder(id,
                        graph(id + "_vertex", ShaderGraphKind.VERTEX),
                        graph(id + "_fragment",
                                ShaderGraphKind.FRAGMENT))
                .entryPoints("vertexMain", "fragmentMain")
                .materialBinding(2, 3)
                .build();
    }

    static ShaderGraphComputeProgram computeProgram(String id) {
        return ShaderGraphComputeProgram.builder(id,
                        graph(id + "_compute", ShaderGraphKind.COMPUTE))
                .entryPoint("computeMain")
                .workgroupSize(4, 2, 1)
                .build();
    }

    static ShaderGraphTechnique technique(String id) {
        ShaderGraphVariant normal = ShaderGraphVariant.builder("",
                program(id + "_program")).build();
        ShaderGraphVariant nativeVariant = ShaderGraphVariant
                .builder("native", program(id + "_native_program"))
                .profiles(ShaderProfile.NATIVE)
                .fallback("")
                .build();
        ShaderGraphTechniquePass pass = ShaderGraphTechniquePass
                .builder(ShaderPassId.FORWARD,
                        ShaderGraphPipelineState.builder()
                                .colorTargets(ColorTargetState.opaque(
                                        TextureFormat.RGBA8_UNORM))
                                .build())
                .variants(normal, nativeVariant)
                .defaultVariant("")
                .build();
        return ShaderGraphTechnique.builder(id)
                .passes(pass).maxVariants(4).build();
    }

    static ShaderGraphComputeTechnique computeTechnique(String id) {
        ShaderGraphComputeProgram program =
                computeProgram(id + "_program");
        ShaderGraphComputeVariant normal = ShaderGraphComputeVariant
                .builder("", program).build();
        return ShaderGraphComputeTechnique.builder(id)
                .passes(ShaderGraphComputeTechniquePass
                        .builder(ShaderPassId.of("simulate"))
                        .variants(normal).defaultVariant("").build())
                .maxVariants(2).build();
    }

    static GraphicsCapabilities capabilities() {
        return GraphicsCapabilities.builder()
                .profile(ShaderProfile.PORTABLE_WEBGL2)
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .profile(ShaderProfile.NATIVE)
                .feature(GraphicsFeature.COMPUTE)
                .feature(GraphicsFeature.STORAGE_BUFFERS)
                .feature(GraphicsFeature.STORAGE_TEXTURES)
                .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
                .colorFormats(TextureFormat.RGBA8_UNORM)
                .depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
                .sampleCounts(1)
                .limits(GraphicsLimits.builder()
                        .maxBindGroups(4)
                        .maxBindingsPerGroup(16)
                        .maxStorageBuffersPerStage(8)
                        .maxStorageTexturesPerStage(4)
                        .maxColorAttachments(4)
                        .maxVertexBuffers(4)
                        .maxVertexAttributes(16)
                        .maxComputeWorkgroupsPerDimension(65535)
                        .maxComputeWorkgroupSize(256, 256, 64)
                        .maxComputeInvocationsPerWorkgroup(256)
                        .maxComputeWorkgroupStorageSize(16384)
                        .maxUniformBufferBindingSize(65536)
                        .maxStorageBufferBindingSize(1 << 20)
                        .build())
                .build();
    }

    static ShaderGraph invalidGraph(String id) {
        ShaderNode invalid = ShaderNode.of("invalid",
                StandardShaderNodes.CONSTANT, 999,
                new ShaderGraphPort[0],
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", F32)
                },
                ShaderNodeProperty.literal("literal",
                        ShaderGraphLiteral.f32(1.0f)));
        return ShaderGraph.builder(id, ShaderGraphKind.FUNCTION)
                .nodes(invalid)
                .outputs(ShaderGraphOutput.of("value", F32,
                        ShaderEndpoint.of("invalid", "value")))
                .build();
    }

    private static ShaderNode constant(String id, float value) {
        return ShaderNode.of(id, StandardShaderNodes.CONSTANT, 1,
                new ShaderGraphPort[0],
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", F32)
                },
                ShaderNodeProperty.literal("literal",
                        ShaderGraphLiteral.f32(value)));
    }

    private static ShaderExpression color(ShaderGraphBuilder builder,
            String id, float red, float green, float blue) {
        return builder.construct(id, VEC4,
                builder.constant(id + "_r",
                        ShaderGraphLiteral.f32(red)),
                builder.constant(id + "_g",
                        ShaderGraphLiteral.f32(green)),
                builder.constant(id + "_b",
                        ShaderGraphLiteral.f32(blue)),
                builder.constant(id + "_a",
                        ShaderGraphLiteral.f32(1.0f)));
    }
}
