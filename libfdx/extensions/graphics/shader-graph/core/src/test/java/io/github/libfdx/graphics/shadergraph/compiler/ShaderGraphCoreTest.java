package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.ir.ShaderIrFunction;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrInstruction;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrOpcode;
import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLibrary;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeDefinition;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeRegistry;
import io.github.libfdx.graphics.shadergraph.standard.StandardShaderNodes;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.TextureFormat;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphCoreTest {
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType I32 =
            ShaderGraphType.scalar(ShaderScalarType.I32);
    private static final ShaderGraphType BOOL =
            ShaderGraphType.scalar(ShaderScalarType.BOOL);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC3 =
            ShaderGraphType.vector(ShaderScalarType.F32, 3);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    @Test
    void semanticCodecAndHashAreIndependentOfConstructionAndEditorOrder() {
        ShaderNode first = constantNode("a", ShaderGraphLiteral.f32(1));
        ShaderNode second = constantNode("b", ShaderGraphLiteral.f32(2));
        ShaderNode add = ShaderNode.of("sum", StandardShaderNodes.ADD, 1,
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("in000000", F32),
                        ShaderGraphPort.required("in000001", F32)
                }, new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", F32)
                });
        ShaderEdge left = ShaderEdge.of(ShaderEndpoint.of("a", "value"),
                ShaderEndpoint.of("sum", "in000000"));
        ShaderEdge right = ShaderEdge.of(ShaderEndpoint.of("b", "value"),
                ShaderEndpoint.of("sum", "in000001"));
        ShaderGraphOutput output = ShaderGraphOutput.of("value", F32,
                ShaderEndpoint.of("sum", "value"));

        ShaderGraph graphA = ShaderGraph.builder("ordered", ShaderGraphKind.FUNCTION)
                .nodes(first, second, add)
                .edges(left, right)
                .outputs(output)
                .build();
        ShaderGraph graphB = ShaderGraph.builder("ordered", ShaderGraphKind.FUNCTION)
                .nodes(add, second, first)
                .edges(right, left)
                .outputs(output)
                .build();

        assertEquals(ShaderGraphCodec.write(graphA), ShaderGraphCodec.write(graphB));
        assertEquals(graphA.semanticHash(), graphB.semanticHash());
        assertEquals(graphA, ShaderGraphCodec.read(ShaderGraphCodec.write(graphA)));

        ShaderGraphEditorData layoutA = ShaderGraphEditorData.of("ordered",
                new ShaderGraphEditorNode[] {
                        ShaderGraphEditorNode.of("a", 10, 20, 100, 40, false)
                }, 0, 0, 1);
        ShaderGraphEditorData layoutB = ShaderGraphEditorData.of("ordered",
                new ShaderGraphEditorNode[] {
                        ShaderGraphEditorNode.of("a", 900, -40, 200, 80, true)
                }, 80, 40, 2);
        assertFalse(ShaderGraphEditorCodec.write(layoutA)
                .equals(ShaderGraphEditorCodec.write(layoutB)));
        assertEquals(graphA.semanticHash(), graphB.semanticHash());
        ShaderGraphEditorData decoded = ShaderGraphEditorCodec.read(
                ShaderGraphEditorCodec.write(layoutA));
        assertEquals(10, decoded.nodes()[0].x());
    }

    @Test
    void lowersFunctionsSurfaceMathStructuresAndControlFlowToTypedIr() {
        ShaderGraphBuilder function = new ShaderGraphBuilder(
                "increase", ShaderGraphKind.FUNCTION);
        function.parameter(ShaderGraphParameter.of("input", F32,
                ShaderGraphParameterKind.FUNCTION_INPUT, ShaderGraphLiteral.f32(0)));
        ShaderExpression input = function.parameter("input_node", "input");
        ShaderExpression one = function.constant("one", ShaderGraphLiteral.f32(1));
        function.output("value", function.add("sum", input, one));
        ShaderGraph increase = function.build();

        ShaderGraphBuilder surface = new ShaderGraphBuilder(
                "surface_control", ShaderGraphKind.SURFACE);
        surface.parameter(ShaderGraphParameter.semantic("uv", VEC2,
                ShaderGraphParameterKind.STAGE_INPUT,
                ShaderGraphLiteral.composite(VEC2,
                        ShaderGraphLiteral.f32(0), ShaderGraphLiteral.f32(0)),
                "uv0"));
        surface.parameter(ShaderGraphParameter.of("tint", VEC3,
                ShaderGraphParameterKind.MATERIAL,
                ShaderGraphLiteral.composite(VEC3,
                        ShaderGraphLiteral.f32(1), ShaderGraphLiteral.f32(1),
                        ShaderGraphLiteral.f32(1))));
        ShaderExpression tint = surface.parameter("tint_node", "tint");
        ShaderExpression half = surface.constant("half", ShaderGraphLiteral.f32(0.5f));
        ShaderExpression alpha = surface.call("increase_call", increase, half);
        ShaderExpression condition = surface.constant("condition",
                ShaderGraphLiteral.bool(true));
        ShaderExpression zero = surface.constant("zero", ShaderGraphLiteral.f32(0));
        ShaderExpression branch = surface.branch("branch", condition, alpha, zero);
        ShaderExpression step = surface.constant("step", ShaderGraphLiteral.f32(0.1f));
        ShaderExpression loop = surface.loop("loop", branch, step, 2);
        ShaderExpression selector = surface.constant("selector",
                ShaderGraphLiteral.i32(1));
        ShaderExpression switched = surface.switchValue("switch", selector,
                zero, new long[] { 1 }, loop);
        ShaderExpression color = surface.multiply("color", tint,
                surface.construct("factor", VEC3, switched, switched, switched));
        surface.output("base_color", "baseColor", color);
        surface.output("alpha", "alpha", switched);
        ShaderGraph graph = surface.build();

        ShaderGraphCompileResult result = new ShaderGraphCompiler().compile(graph,
                ShaderGraphCompileOptions.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGPU)
                        .stage(ShaderStage.FRAGMENT)
                        .library(ShaderGraphLibrary.of(increase))
                        .build());

        assertTrue(result.success(), diagnostics(result));
        assertEquals(2, result.module().functions().length);
        assertTrue(result.wgsl().contains("fn fdx_graph_increase"));
        assertTrue(result.wgsl().contains("if ("));
        assertTrue(result.wgsl().contains("switch ("));
        assertTrue(result.wgsl().contains("for ("));
        assertTrue(result.wgsl().contains("@vertex"));
        assertTrue(result.wgsl().contains("@fragment"));
        assertTrue(result.sourceMap().length > 0);
        for (ShaderIrFunction irFunction : result.module().functions()) {
            for (ShaderIrInstruction instruction : irFunction.instructions()) {
                assertNotNull(instruction.opcode());
                assertNotNull(instruction.result().type());
            }
        }
    }

    @Test
    void textureSamplingLowersThroughTypedResources() {
        ShaderGraphType texture = ShaderGraphType.texture(
                ShaderTextureDimension.D2,
                ShaderTextureSampleType.FILTERABLE_FLOAT, false);
        ShaderGraphType sampler = ShaderGraphType.sampler(
                ShaderSamplerKind.FILTERING);
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "sample_surface", ShaderGraphKind.SURFACE);
        builder.parameter(ShaderGraphParameter.semantic("uv", VEC2,
                ShaderGraphParameterKind.STAGE_INPUT,
                ShaderGraphLiteral.composite(VEC2,
                        ShaderGraphLiteral.f32(0), ShaderGraphLiteral.f32(0)),
                "uv0"));
        builder.resource(ShaderGraphResource.of("albedo", texture, 3, 0));
        builder.resource(ShaderGraphResource.of("linear_sampler", sampler, 3, 1));
        ShaderExpression sampled = builder.sample2D("sample",
                builder.resource("texture", "albedo"),
                builder.resource("sampler", "linear_sampler"),
                builder.parameter("uv_node", "uv"));
        builder.output("color", sampled);

        ShaderGraphCompileResult result = new ShaderGraphCompiler().compile(
                builder.build(), ShaderGraphCompileOptions.builder().build());

        assertTrue(result.success(), diagnostics(result));
        assertTrue(result.wgsl().contains("@group(3) @binding(0)"));
        assertTrue(result.wgsl().contains("textureSample("));
        assertTrue(Arrays.stream(result.module().root().instructions())
                .anyMatch(value -> value.opcode() == ShaderIrOpcode.TEXTURE_SAMPLE));
    }

    @Test
    void reportsCyclesTypesVersionsProfilesCapabilitiesAndRequirements() {
        ShaderNode left = ShaderNode.of("left", StandardShaderNodes.ADD, 1,
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("in000000", F32),
                        ShaderGraphPort.optional("in000001", F32,
                                ShaderGraphLiteral.f32(1))
                }, new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", F32)
                });
        ShaderNode right = ShaderNode.of("right", StandardShaderNodes.ADD, 1,
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("in000000", F32),
                        ShaderGraphPort.optional("in000001", F32,
                                ShaderGraphLiteral.f32(1))
                }, new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", F32)
                });
        ShaderGraph cycle = ShaderGraph.builder("cycle", ShaderGraphKind.FUNCTION)
                .nodes(left, right)
                .edges(
                        ShaderEdge.of(ShaderEndpoint.of("left", "value"),
                                ShaderEndpoint.of("right", "in000000")),
                        ShaderEdge.of(ShaderEndpoint.of("right", "value"),
                                ShaderEndpoint.of("left", "in000000")))
                .outputs(ShaderGraphOutput.of("value", F32,
                        ShaderEndpoint.of("left", "value")))
                .build();
        assertCode(new ShaderGraphCompiler().compile(cycle, null), "FDXG_CYCLE");

        ShaderNode unknown = ShaderNode.of("unknown", StandardShaderNodes.CONSTANT,
                999, new ShaderGraphPort[0],
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", F32)
                }, ShaderNodeProperty.literal("literal", ShaderGraphLiteral.f32(1)));
        ShaderGraph version = ShaderGraph.builder("version", ShaderGraphKind.FUNCTION)
                .nodes(unknown)
                .outputs(ShaderGraphOutput.of("value", F32,
                        ShaderEndpoint.of("unknown", "value")))
                .build();
        assertCode(new ShaderGraphCompiler().compile(version, null),
                "FDXG_NODE_VERSION");

        ShaderNode custom = ShaderNode.of("custom",
                StandardShaderNodes.CUSTOM_FUNCTION, 1,
                new ShaderGraphPort[0],
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", F32)
                }, ShaderNodeProperty.string("body", "return 1.0;"));
        ShaderGraph webgl = ShaderGraph.builder("webgl", ShaderGraphKind.FUNCTION)
                .nodes(custom)
                .outputs(ShaderGraphOutput.of("value", F32,
                        ShaderEndpoint.of("custom", "value")))
                .build();
        assertCode(new ShaderGraphCompiler().compile(webgl,
                ShaderGraphCompileOptions.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGL2).build()),
                "FDXG_NODE_PROFILE");

        GraphicsCapabilities noAtomics = GraphicsCapabilities.builder()
                .profile(ShaderProfile.NATIVE)
                .feature(GraphicsFeature.COMPUTE)
                .feature(GraphicsFeature.STORAGE_BUFFERS)
                .colorFormats(TextureFormat.RGBA8_UNORM)
                .sampleCounts(1)
                .limits(GraphicsLimits.builder()
                        .maxBindGroups(4).maxBindingsPerGroup(8)
                        .maxVertexAttributes(8).maxVertexBuffers(4)
                        .maxColorAttachments(1)
                        .maxUniformBufferBindingSize(65536)
                        .maxStorageBufferBindingSize(65536)
                        .maxComputeWorkgroupsPerDimension(64)
                        .maxComputeWorkgroupSize(64, 64, 64)
                        .maxComputeInvocationsPerWorkgroup(64)
                        .maxComputeWorkgroupStorageSize(16384)
                        .build())
                .build();
        ShaderNode atomic = ShaderNode.of("atomic",
                StandardShaderNodes.ATOMIC_ADD, 1,
                new ShaderGraphPort[0],
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", I32)
                });
        ShaderGraph compute = ShaderGraph.builder("compute", ShaderGraphKind.COMPUTE)
                .nodes(atomic)
                .outputs(ShaderGraphOutput.of("value", I32,
                        ShaderEndpoint.of("atomic", "value")))
                .build();
        assertCode(new ShaderGraphCompiler().compile(compute,
                ShaderGraphCompileOptions.builder()
                        .profile(ShaderProfile.NATIVE)
                        .capabilities(noAtomics).build()),
                "FDXG_NODE_CAPABILITY");

        ShaderGraphBuilder derivative = new ShaderGraphBuilder(
                "derivative", ShaderGraphKind.FUNCTION);
        ShaderExpression value = derivative.constant("value",
                ShaderGraphLiteral.f32(1));
        derivative.output("value", derivative.derivativeX("dx", value));
        assertCode(new ShaderGraphCompiler().compile(derivative.build(),
                ShaderGraphCompileOptions.builder()
                        .stage(ShaderStage.VERTEX).build()),
                "FDXG_NODE_STAGE");
    }

    @Test
    void registryContainsOnlyTypedOpcodeDefinitions() {
        ShaderNodeDefinition[] definitions =
                ShaderNodeRegistry.standard().definitions();
        assertTrue(definitions.length >= 25);
        for (ShaderNodeDefinition definition : definitions) {
            assertNotNull(definition.opcode());
            assertTrue(definition.version() > 0);
        }
        assertArrayEquals(definitions, ShaderNodeRegistry.standard().definitions());
    }

    private static ShaderNode constantNode(String id, ShaderGraphLiteral value) {
        return ShaderNode.of(id, StandardShaderNodes.CONSTANT, 1,
                new ShaderGraphPort[0],
                new ShaderGraphPort[] {
                        ShaderGraphPort.required("value", value.type())
                }, ShaderNodeProperty.literal("literal", value));
    }

    private static void assertCode(ShaderGraphCompileResult result, String code) {
        assertFalse(result.success());
        assertTrue(Arrays.stream(result.diagnostics())
                .anyMatch(value -> value.code().equals(code)), diagnostics(result));
    }

    private static String diagnostics(ShaderGraphCompileResult result) {
        StringBuilder message = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic : result.diagnostics()) {
            message.append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return message.toString();
    }
}
