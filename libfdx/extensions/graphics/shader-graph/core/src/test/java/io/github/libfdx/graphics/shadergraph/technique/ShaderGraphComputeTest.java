package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledComputeVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeProgramCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeTechniqueCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBarrierScope;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderStorageTextureFormat;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.graphics.TextureFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphComputeTest {
    private static final ShaderGraphType U32 =
            ShaderGraphType.scalar(ShaderScalarType.U32);
    private static final ShaderGraphType I32 =
            ShaderGraphType.scalar(ShaderScalarType.I32);
    private static final ShaderGraphType U32X3 =
            ShaderGraphType.vector(ShaderScalarType.U32, 3);
    private static final ShaderGraphType I32X2 =
            ShaderGraphType.vector(ShaderScalarType.I32, 2);
    private static final ShaderGraphType F32X4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    @Test
    void compilesTypedBufferWorkgroupTextureAndAtomicPrograms() {
        ShaderGraphComputeCompileResult buffer = compile(
                program("buffer", bufferGraph(), 4, 1, 1));
        assertTrue(buffer.success(), diagnostics(buffer));
        assertTrue(buffer.wgsl().contains(
                "var<storage, read_write> fdx_resource_0_0: array<u32>;"));
        assertTrue(buffer.wgsl().contains(
                "@builtin(global_invocation_id)"));
        assertTrue(buffer.wgsl().contains(
                "@compute @workgroup_size(4, 1, 1)"));

        ShaderGraphComputeCompileResult workgroup = compile(
                program("workgroup", workgroupGraph(), 4, 1, 1));
        assertTrue(workgroup.success(), diagnostics(workgroup));
        assertTrue(workgroup.wgsl().contains(
                "var<workgroup> fdx_workgroup_shared: array<u32, 4>;"));
        assertTrue(workgroup.wgsl().contains("workgroupBarrier();"));

        ShaderGraphComputeCompileResult texture = compile(
                program("texture", storageTextureGraph(), 8, 8, 1));
        assertTrue(texture.success(), diagnostics(texture));
        assertTrue(texture.wgsl().contains(
                "texture_storage_2d<rgba8unorm, write>"));
        assertTrue(texture.wgsl().contains("textureStore("));

        ShaderGraphComputeCompileResult atomic = compile(
                program("atomic", atomicGraph(), 1, 1, 1));
        assertTrue(atomic.success(), diagnostics(atomic));
        assertTrue(atomic.wgsl().contains("array<atomic<u32>>"));
        assertTrue(atomic.wgsl().contains("atomicAdd(&"));
    }

    @Test
    void serializesComputeResourcesAndRejectsProfilesAndLimits() {
        ShaderGraph source = storageTextureGraph();
        assertEquals(source, ShaderGraphCodec.read(
                ShaderGraphCodec.write(source)));

        ShaderGraphComputeCompileResult webGl =
                new ShaderGraphComputeProgramCompiler().compile(
                        program("webgl", bufferGraph(), 1, 1, 1),
                        ShaderGraphCompileOptions.builder()
                                .profile(ShaderProfile.PORTABLE_WEBGL2)
                                .build());
        assertFalse(webGl.success());
        assertCode(webGl, "FDXG_COMPUTE_PROFILE");

        ShaderGraphComputeCompileResult dimension =
                new ShaderGraphComputeProgramCompiler().compile(
                        program("too_wide", bufferGraph(), 257, 1, 1),
                        options());
        assertCode(dimension,
                "FDXG_COMPUTE_WORKGROUP_DIMENSION");

        ShaderGraphBuilder storage = computeBuilder("too_much_storage");
        storage.resource(ShaderGraphResource.workgroup("huge",
                ShaderGraphType.workgroupArray(U32, 5000)));
        ShaderExpression resource = storage.resource(
                "huge_resource", "huge");
        ShaderExpression index = storage.constant("index",
                ShaderGraphLiteral.u32(0));
        storage.output("value", storage.storageLoad(
                "load", resource, index));
        ShaderGraphComputeCompileResult storageLimit =
                new ShaderGraphComputeProgramCompiler().compile(
                        program("too_much_storage", storage.build(),
                                1, 1, 1),
                        options());
        assertCode(storageLimit, "FDXG_WORKGROUP_STORAGE_LIMIT");
    }

    @Test
    void compilesBoundedComputeTechniqueWithExplicitFallbackAndDedup() {
        ShaderGraphComputeProgram program =
                program("technique_program", bufferGraph(), 4, 1, 1);
        ShaderGraphComputeVariant normal =
                ShaderGraphComputeVariant.builder("", program).build();
        ShaderGraphComputeVariant nativeOnly =
                ShaderGraphComputeVariant.builder("native", program)
                        .profiles(ShaderProfile.NATIVE)
                        .features(GraphicsFeature.ATOMICS)
                        .fallback("")
                        .build();
        ShaderGraphComputeTechnique technique =
                ShaderGraphComputeTechnique.builder("compute_technique")
                        .passes(ShaderGraphComputeTechniquePass.builder(
                                        ShaderPassId.of("transform"))
                                .variants(nativeOnly, normal)
                                .build())
                        .maxVariants(2)
                        .build();
        ShaderGraphComputeTechniqueCompileResult result =
                new ShaderGraphComputeTechniqueCompiler().compile(
                        technique, options());
        assertTrue(result.success());
        ShaderGraphCompiledComputeVariant[] variants =
                result.passes()[0].variants();
        assertSame(variants[0].compilation(),
                variants[1].compilation());
        assertEquals(2, technique.variantCount());
        assertEquals(program.semanticHash(),
                ShaderGraphComputeProgramCodec.read(
                        ShaderGraphComputeProgramCodec.write(
                                program)).semanticHash());
        assertEquals(technique.semanticHash(),
                ShaderGraphComputeTechniqueCodec.read(
                        ShaderGraphComputeTechniqueCodec.write(
                                technique)).semanticHash());
    }

    private static ShaderGraph bufferGraph() {
        ShaderGraphBuilder builder = computeBuilder("buffer_graph");
        ShaderGraphType buffer = ShaderGraphType.storageBuffer(
                U32, ShaderResourceAccess.READ_WRITE);
        builder.resource(ShaderGraphResource.of(
                "data", buffer, 0, 0));
        ShaderExpression resource = builder.resource(
                "data_resource", "data");
        ShaderExpression invocation = builder.parameter(
                "global_id", "global_id");
        ShaderExpression index = builder.member(
                "index", invocation, "x", U32);
        ShaderExpression loaded = builder.storageLoad(
                "load", resource, index);
        ShaderExpression doubled = builder.multiply(
                "double", loaded,
                builder.constant("two", ShaderGraphLiteral.u32(2)));
        ShaderExpression result = builder.add(
                "increment", doubled,
                builder.constant("one", ShaderGraphLiteral.u32(1)));
        builder.output("stored", builder.storageStore(
                "store", resource, index, result));
        return builder.build();
    }

    private static ShaderGraph workgroupGraph() {
        ShaderGraphBuilder builder = computeBuilder("workgroup_graph");
        ShaderGraphType buffer = ShaderGraphType.storageBuffer(
                U32, ShaderResourceAccess.READ_WRITE);
        ShaderGraphType workgroup =
                ShaderGraphType.workgroupArray(U32, 4);
        builder.resource(ShaderGraphResource.of(
                "data", buffer, 0, 0));
        builder.resource(ShaderGraphResource.workgroup(
                "shared", workgroup));
        ShaderExpression data = builder.resource(
                "data_resource", "data");
        ShaderExpression shared = builder.resource(
                "shared_resource", "shared");
        ShaderExpression global = builder.member("global_index",
                builder.parameter("global_id", "global_id"), "x", U32);
        ShaderExpression local = builder.parameter(
                "local_index", "local_index");
        ShaderExpression value = builder.storageLoad(
                "load_data", data, global);
        ShaderExpression staged = builder.storageStore(
                "store_shared", shared, local, value);
        ShaderExpression barrier = builder.barrier(
                "shared_barrier", ShaderGraphBarrierScope.WORKGROUP,
                staged);
        ShaderExpression loaded = builder.storageLoad(
                "load_shared", shared, local, barrier);
        builder.output("stored", builder.storageStore(
                "store_data", data, global, loaded, barrier));
        return builder.build();
    }

    private static ShaderGraph storageTextureGraph() {
        ShaderGraphBuilder builder = computeBuilder("texture_graph");
        ShaderGraphType texture = ShaderGraphType.storageTexture2D(
                ShaderStorageTextureFormat.RGBA8_UNORM,
                ShaderResourceAccess.WRITE);
        builder.resource(ShaderGraphResource.of(
                "output", texture, 0, 0));
        ShaderExpression resource = builder.resource(
                "output_resource", "output");
        ShaderExpression invocation = builder.parameter(
                "global_id", "global_id");
        ShaderExpression coordinates = builder.customWgsl(
                "coordinates", I32X2, "vec2<i32>($0.xy)",
                invocation);
        ShaderExpression color = builder.constant("color",
                ShaderGraphLiteral.composite(F32X4,
                        ShaderGraphLiteral.f32(0.1f),
                        ShaderGraphLiteral.f32(0.8f),
                        ShaderGraphLiteral.f32(0.2f),
                        ShaderGraphLiteral.f32(1)));
        builder.output("stored", builder.storageStore(
                "store", resource, coordinates, color));
        return builder.build();
    }

    private static ShaderGraph atomicGraph() {
        ShaderGraphBuilder builder = computeBuilder("atomic_graph");
        ShaderGraphType atomic = ShaderGraphType.value(
                ShaderValueType.atomic(ShaderScalarType.U32));
        ShaderGraphType buffer = ShaderGraphType.storageBuffer(
                atomic, ShaderResourceAccess.READ_WRITE);
        builder.resource(ShaderGraphResource.of(
                "counter", buffer, 0, 0));
        ShaderExpression resource = builder.resource(
                "counter_resource", "counter");
        builder.output("prior", builder.atomicAdd("add",
                resource,
                builder.constant("index", ShaderGraphLiteral.u32(0)),
                builder.constant("one", ShaderGraphLiteral.u32(1))));
        return builder.build();
    }

    private static ShaderGraphBuilder computeBuilder(String id) {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                id, ShaderGraphKind.COMPUTE);
        builder.parameter(ShaderGraphParameter.semantic(
                "global_id", U32X3,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.GLOBAL_INVOCATION_ID));
        if (id.equals("workgroup_graph")) {
            builder.parameter(ShaderGraphParameter.semantic(
                    "local_index", U32,
                    ShaderGraphParameterKind.STAGE_INPUT, null,
                    ShaderGraphStageSemantic.LOCAL_INVOCATION_INDEX));
        }
        return builder;
    }

    private static ShaderGraphComputeProgram program(String id,
            ShaderGraph graph, int x, int y, int z) {
        return ShaderGraphComputeProgram.builder(id, graph)
                .workgroupSize(x, y, z).build();
    }

    private static ShaderGraphComputeCompileResult compile(
            ShaderGraphComputeProgram program) {
        return new ShaderGraphComputeProgramCompiler().compile(
                program, options());
    }

    private static ShaderGraphCompileOptions options() {
        return ShaderGraphCompileOptions.builder()
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .capabilities(capabilities()).build();
    }

    private static GraphicsCapabilities capabilities() {
        return GraphicsCapabilities.builder()
                .profile(ShaderProfile.PORTABLE_WEBGL2)
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .profile(ShaderProfile.NATIVE)
                .feature(GraphicsFeature.COMPUTE)
                .feature(GraphicsFeature.STORAGE_BUFFERS)
                .feature(GraphicsFeature.STORAGE_TEXTURES)
                .feature(GraphicsFeature.ATOMICS)
                .colorFormats(TextureFormat.RGBA8_UNORM)
                .sampleCounts(1)
                .limits(GraphicsLimits.builder()
                        .maxBindGroups(4)
                        .maxBindingsPerGroup(16)
                        .maxStorageBuffersPerStage(8)
                        .maxStorageTexturesPerStage(4)
                        .maxColorAttachments(1)
                        .maxVertexBuffers(4)
                        .maxVertexAttributes(8)
                        .maxComputeWorkgroupsPerDimension(65535)
                        .maxComputeWorkgroupSize(256, 256, 64)
                        .maxComputeInvocationsPerWorkgroup(256)
                        .maxComputeWorkgroupStorageSize(16384)
                        .maxUniformBufferBindingSize(65536)
                        .maxStorageBufferBindingSize(1 << 20)
                        .build())
                .build();
    }

    private static void assertCode(
            ShaderGraphComputeCompileResult result, String code) {
        assertFalse(result.success());
        for (ShaderGraphDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.code().equals(code)) {
                return;
            }
        }
        throw new AssertionError(
                "Missing diagnostic " + code + ": "
                        + diagnostics(result));
    }

    private static String diagnostics(
            ShaderGraphComputeCompileResult result) {
        StringBuilder value = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic : result.diagnostics()) {
            value.append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return value.toString();
    }
}
