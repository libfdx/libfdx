package io.github.libfdx.tests.graphics;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.ComputePass;
import io.github.libfdx.graphics.ComputePassDescriptor;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceSet;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderStorageTextureFormat;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBarrierScope;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeTechniqueCompiler;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompiler;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphComputeProvider;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderProgram;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphResolvedComputePass;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Executes graph-generated buffer, workgroup/barrier, atomic, storage-texture,
 * and compute-to-render programs through the real WGPU runtime.
 */
public final class ShaderGraphComputeTest extends GraphicsParityTest {
    private static final int VALUE_COUNT = 4;
    private static final int DATA_BYTES = VALUE_COUNT * 4;
    private static final int READBACK_BYTES = DATA_BYTES + 4;
    private static final int TEXTURE_SIZE = 32;
    private static final int[] EXPECTED = { 5, 7, 9, 3 };
    private static final ShaderPassId TRANSFORM =
            ShaderPassId.of("transform");
    private static final ShaderPassId WRITE_TEXTURE =
            ShaderPassId.of("write-texture");

    private static final ShaderGraphType U32 =
            ShaderGraphType.scalar(ShaderScalarType.U32);
    private static final ShaderGraphType U32X3 =
            ShaderGraphType.vector(ShaderScalarType.U32, 3);
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType F32X2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType F32X4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    private ShaderGraphComputeProvider computeProvider;
    private ShaderGraphProvider renderProvider;
    private ShaderGraphResolvedComputePass transformPass;
    private ShaderGraphResolvedComputePass texturePass;
    private ResolvedShaderPass renderPass;
    private ShaderResourceSet transformResources;
    private ShaderResourceSet textureResources;
    private ShaderResourceSet renderResources;
    private Buffer data;
    private Buffer counter;
    private Buffer readback;
    private Texture texture;
    private boolean commandsRecorded;
    private boolean verified;

    public ShaderGraphComputeTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "ShaderGraphComputeTest");
        graphics.device().capabilities().require(GraphicsFeature.COMPUTE);
        graphics.device().capabilities().require(
                GraphicsFeature.STORAGE_BUFFERS);
        graphics.device().capabilities().require(
                GraphicsFeature.STORAGE_TEXTURES);
        graphics.device().capabilities().require(GraphicsFeature.ATOMICS);

        ShaderGraphCompileOptions options =
                ShaderGraphCompileOptions.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGPU)
                        .capabilities(graphics.device().capabilities())
                        .build();
        ShaderGraphComputeTechniqueCompileResult compiled =
                new ShaderGraphComputeTechniqueCompiler().compile(
                        computeTechnique(0.18f), options);
        computeProvider = new ShaderGraphComputeProvider(
                graphics, compiled);
        long initialRevision = computeProvider.revision();
        computeProvider.replace(
                new ShaderGraphComputeTechniqueCompiler().compile(
                        computeTechnique(0.72f), options));
        if (computeProvider.revision() != initialRevision + 1
                || computeProvider.passCount() != 2) {
            throw new FdxException(
                    "Compute technique replacement was not atomic");
        }
        transformPass = computeProvider.resolve(
                TRANSFORM, "native-only",
                ShaderProfile.PORTABLE_WEBGPU);
        texturePass = computeProvider.resolve(
                WRITE_TEXTURE, "", ShaderProfile.PORTABLE_WEBGPU);
        if (!transformPass.variantKey().isEmpty()
                || transformPass.providerRevision()
                        != texturePass.providerRevision()) {
            throw new FdxException(
                    "Compute capability fallback or revision selection failed");
        }

        data = graphics.device().createBuffer(new BufferDescriptor()
                .label("graph compute data").size(DATA_BYTES)
                .usage(BufferUsage.STORAGE));
        counter = graphics.device().createBuffer(new BufferDescriptor()
                .label("graph compute counter").size(4)
                .usage(BufferUsage.STORAGE));
        readback = graphics.device().createBuffer(new BufferDescriptor()
                .label("graph compute readback").size(READBACK_BYTES)
                .usage(BufferUsage.READBACK));
        ByteBuffer initial = ByteBuffer.allocateDirect(DATA_BYTES)
                .order(ByteOrder.nativeOrder());
        for (int value = 1; value <= VALUE_COUNT; value++) {
            initial.putInt(value);
        }
        initial.flip();
        graphics.device().writeBuffer(data, initial);
        graphics.device().writeBuffer(counter,
                ByteBuffer.allocateDirect(4)
                        .order(ByteOrder.nativeOrder()).putInt(0).flip());

        texture = graphics.device().createTexture(new TextureDescriptor()
                .label("graph compute storage texture")
                .size(TEXTURE_SIZE, TEXTURE_SIZE)
                .format(TextureFormat.RGBA8_UNORM)
                .usage(TextureUsage.SAMPLED_STORAGE));

        transformResources = ShaderResourceSet.builder(
                        transformPass.resourceLayout(), 0)
                .buffer(0, data).buffer(1, counter).build();
        textureResources = ShaderResourceSet.builder(
                        texturePass.resourceLayout(), 0)
                .texture(0, texture).build();

        ShaderGraphProgramCompileResult renderCompilation =
                new ShaderGraphProgramCompiler().compile(
                        renderProgram(), options);
        if (!renderCompilation.success()) {
            throw new FdxException(
                    "Compute handoff render graph did not compile");
        }
        renderProvider = new ShaderGraphProvider(graphics,
                ShaderGraphRenderProgram.builder(ShaderPassId.FORWARD,
                                ShaderModuleDescriptor.wgsl(
                                                "graph compute handoff",
                                                renderCompilation.wgsl())
                                        .entryPoints(
                                                renderCompilation
                                                        .vertexEntryPoint(),
                                                renderCompilation
                                                        .fragmentEntryPoint()))
                        .entryPoints(renderCompilation.vertexEntryPoint(),
                                renderCompilation.fragmentEntryPoint())
                        .depth(false, CompareFunction.ALWAYS)
                        .build());
        RenderPassCompatibility compatibility =
                RenderPassCompatibility.of(
                        RenderTargetLayout.color(
                                graphics.surfaceFormat()),
                        framebufferWidth(), framebufferHeight());
        renderPass = renderProvider.resolve(
                ShaderRequest.builder(ShaderPassId.FORWARD)
                        .profile(ShaderProfile.PORTABLE_WEBGPU)
                        .renderPass(compatibility)
                        .topology(PrimitiveTopology.TRIANGLE_LIST)
                        .build());
        renderResources = ShaderResourceSet.builder(
                        renderPass.resourceLayout(), 0)
                .texture(0, texture).build();
        markCreated();
    }

    @Override
    public void render() {
        if (commandsRecorded && !verified) {
            verifyReadback();
        }
        GraphicsFrame frame = graphics.currentFrame();
        if (!commandsRecorded) {
            dispatch(frame, transformPass, transformResources,
                    transformPass.workgroupCountX(VALUE_COUNT), 1, 1);
            dispatch(frame, texturePass, textureResources,
                    texturePass.workgroupCountX(TEXTURE_SIZE),
                    texturePass.workgroupCountY(TEXTURE_SIZE), 1);
            frame.commandEncoder().copyBufferToBuffer(
                    data, 0, readback, 0, DATA_BYTES);
            frame.commandEncoder().copyBufferToBuffer(
                    counter, 0, readback, DATA_BYTES, 4);
            commandsRecorded = true;
        }

        RenderPass pass = frame.commandEncoder().beginRenderPass(
                RenderPassDescriptor.color(frame.colorAttachment(),
                        LoadOp.clear(0.02f, 0.03f, 0.06f, 1),
                        StoreOp.store())
                        .label("graph compute-to-render handoff"));
        pass.setPipeline(renderPass.pipeline());
        pass.setResourceSet(renderResources);
        pass.draw(3, 1, 0, 0);
        pass.end();
        finishFrame();
    }

    private void dispatch(GraphicsFrame frame,
            ShaderGraphResolvedComputePass resolved,
            ShaderResourceSet resources, int x, int y, int z) {
        ComputePass pass = frame.commandEncoder().beginComputePass(
                ComputePassDescriptor.create(
                        "graph compute " + resolved.passId()));
        pass.setPipeline(resolved.pipeline());
        pass.setResourceSet(resources);
        pass.dispatch(x, y, z);
        pass.end();
    }

    private void verifyReadback() {
        ByteBuffer result = graphics.device().readBuffer(
                        readback, 0, READBACK_BYTES)
                .order(ByteOrder.nativeOrder());
        for (int i = 0; i < EXPECTED.length; i++) {
            int actual = result.getInt(i * 4);
            if (actual != EXPECTED[i]) {
                throw new FdxException(
                        "Graph compute result mismatch at " + i
                                + ": expected " + EXPECTED[i]
                                + ", got " + actual);
            }
        }
        int atomicCount = result.getInt(DATA_BYTES);
        if (atomicCount != VALUE_COUNT) {
            throw new FdxException(
                    "Graph compute atomic count expected "
                            + VALUE_COUNT + ", got " + atomicCount);
        }
        verified = true;
        logger.info(
                "ShaderGraphComputeTest verified buffer, workgroup barrier, atomics, storage texture, and render handoff");
    }

    @Override
    public void dispose() {
        dispose(texture);
        dispose(readback);
        dispose(counter);
        dispose(data);
        dispose(renderProvider);
        dispose(computeProvider);
        if (!verified) {
            throw new FdxException(
                    "ShaderGraphComputeTest exited before readback verification");
        }
        verifyDisposed();
    }

    private static ShaderGraphComputeTechnique computeTechnique(
            float blue) {
        ShaderGraphComputeProgram transform =
                ShaderGraphComputeProgram.builder(
                                "graph_compute_transform",
                                transformGraph())
                        .workgroupSize(VALUE_COUNT, 1, 1)
                        .build();
        ShaderGraphComputeProgram texture =
                ShaderGraphComputeProgram.builder(
                                "graph_compute_texture",
                                textureGraph(blue))
                        .workgroupSize(8, 8, 1)
                        .build();
        ShaderGraphComputeVariant transformDefault =
                ShaderGraphComputeVariant.builder("", transform).build();
        ShaderGraphComputeVariant nativeOnly =
                ShaderGraphComputeVariant.builder(
                                "native-only", transform)
                        .profiles(ShaderProfile.NATIVE)
                        .fallback("")
                        .build();
        return ShaderGraphComputeTechnique.builder(
                        "graph_compute_acceptance")
                .passes(
                        ShaderGraphComputeTechniquePass.builder(TRANSFORM)
                                .variants(transformDefault, nativeOnly)
                                .build(),
                        ShaderGraphComputeTechniquePass.builder(
                                        WRITE_TEXTURE)
                                .variants(ShaderGraphComputeVariant.builder(
                                                "", texture)
                                        .build())
                                .build())
                .build();
    }

    private static io.github.libfdx.graphics.shadergraph.model.ShaderGraph
            transformGraph() {
        ShaderGraphBuilder builder = computeBuilder(
                "graph_compute_transform_stage", true);
        ShaderGraphType buffer = ShaderGraphType.storageBuffer(
                U32, ShaderResourceAccess.READ_WRITE);
        ShaderGraphType atomic = ShaderGraphType.value(
                ShaderValueType.atomic(ShaderScalarType.U32));
        builder.resource(ShaderGraphResource.of(
                "data", buffer, 0, 0));
        builder.resource(ShaderGraphResource.of(
                "counter", ShaderGraphType.storageBuffer(
                        atomic, ShaderResourceAccess.READ_WRITE),
                0, 1));
        builder.resource(ShaderGraphResource.workgroup(
                "shared",
                ShaderGraphType.workgroupArray(U32, VALUE_COUNT)));
        ShaderExpression data = builder.resource(
                "data_resource", "data");
        ShaderExpression counter = builder.resource(
                "counter_resource", "counter");
        ShaderExpression shared = builder.resource(
                "shared_resource", "shared");
        ShaderExpression global = builder.member("global_index",
                builder.parameter("global_id_node", "global_id"),
                "x", U32);
        ShaderExpression local = builder.parameter(
                "local_index_node", "local_index");
        ShaderExpression loaded = builder.storageLoad(
                "load_data", data, global);
        ShaderExpression staged = builder.storageStore(
                "store_shared", shared, local, loaded);
        ShaderExpression barrier = builder.barrier(
                "workgroup_barrier",
                ShaderGraphBarrierScope.WORKGROUP, staged);
        ShaderExpression neighbor = builder.customWgsl(
                "neighbor_index", U32, "($0 + 1u) & 3u", local);
        ShaderExpression sharedValue = builder.storageLoad(
                "load_neighbor", shared, neighbor, barrier);
        ShaderExpression result = builder.add("add_one",
                builder.multiply("multiply_two", sharedValue,
                        builder.constant("two",
                                ShaderGraphLiteral.u32(2))),
                builder.constant("one", ShaderGraphLiteral.u32(1)));
        ShaderExpression atomicPrior = builder.atomicAdd(
                "count_invocation", counter,
                builder.constant("counter_index",
                        ShaderGraphLiteral.u32(0)),
                builder.constant("counter_increment",
                        ShaderGraphLiteral.u32(1)));
        builder.output("stored", builder.storageStore(
                "store_result", data, global, result, atomicPrior));
        return builder.build();
    }

    private static io.github.libfdx.graphics.shadergraph.model.ShaderGraph
            textureGraph(float blue) {
        ShaderGraphBuilder builder = computeBuilder(
                "graph_compute_texture_stage", false);
        ShaderGraphType i32x2 = ShaderGraphType.vector(
                ShaderScalarType.I32, 2);
        builder.resource(ShaderGraphResource.of(
                "output",
                ShaderGraphType.storageTexture2D(
                        ShaderStorageTextureFormat.RGBA8_UNORM,
                        ShaderResourceAccess.WRITE),
                0, 0));
        ShaderExpression global = builder.parameter(
                "global_id_node", "global_id");
        ShaderExpression coordinates = builder.customWgsl(
                "coordinates", i32x2, "vec2<i32>($0.xy)", global);
        ShaderExpression color = builder.customWgsl(
                "gradient", F32X4,
                "vec4<f32>(f32($0.x) / 31.0, f32($0.y) / 31.0, "
                        + Float.toString(blue) + ", 1.0)",
                global);
        builder.output("stored", builder.storageStore(
                "store_texture",
                builder.resource("output_resource", "output"),
                coordinates, color));
        return builder.build();
    }

    private static ShaderGraphBuilder computeBuilder(
            String id, boolean localIndex) {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                id, ShaderGraphKind.COMPUTE);
        builder.parameter(ShaderGraphParameter.semantic(
                "global_id", U32X3,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.GLOBAL_INVOCATION_ID));
        if (localIndex) {
            builder.parameter(ShaderGraphParameter.semantic(
                    "local_index", U32,
                    ShaderGraphParameterKind.STAGE_INPUT, null,
                    ShaderGraphStageSemantic.LOCAL_INVOCATION_INDEX));
        }
        return builder;
    }

    private static ShaderGraphProgram renderProgram() {
        ShaderGraphBuilder vertex = new ShaderGraphBuilder(
                "graph_compute_handoff_vertex",
                ShaderGraphKind.VERTEX);
        vertex.parameter(ShaderGraphParameter.semantic(
                "vertex_index", U32,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.VERTEX_INDEX));
        ShaderExpression index = vertex.parameter(
                "vertex_index_node", "vertex_index");
        ShaderExpression x = vertex.customWgsl(
                "position_x", F32,
                "select(-1.0, 3.0, $0 == 1u)", index);
        ShaderExpression y = vertex.customWgsl(
                "position_y", F32,
                "select(-1.0, 3.0, $0 == 2u)", index);
        vertex.output("position", ShaderGraphStageSemantic.POSITION,
                vertex.construct("clip_position", F32X4,
                        x, y, vertex.floatValue(0),
                        vertex.floatValue(1)));
        vertex.output("uv", ShaderGraphStageSemantic.location(0),
                vertex.construct("uv_value", F32X2,
                        vertex.customWgsl("uv_x", F32,
                                "select(0.0, 2.0, $0 == 1u)", index),
                        vertex.customWgsl("uv_y", F32,
                                "select(0.0, 2.0, $0 == 2u)", index)));

        ShaderGraphBuilder fragment = new ShaderGraphBuilder(
                "graph_compute_handoff_fragment",
                ShaderGraphKind.FRAGMENT);
        fragment.parameter(ShaderGraphParameter.semantic(
                "uv", F32X2,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.location(0)));
        ShaderGraphType sampled = ShaderGraphType.texture(
                ShaderTextureDimension.D2,
                ShaderTextureSampleType.FLOAT, false);
        fragment.resource(ShaderGraphResource.of(
                "computed_texture", sampled, 0, 0));
        ShaderExpression sampledTexture = fragment.resource(
                "computed_texture_node", "computed_texture");
        ShaderExpression uv = fragment.parameter("uv_node", "uv");
        ShaderExpression color = fragment.customWgsl(
                "sample_computed_texture", F32X4,
                "textureLoad($0, vec2<i32>(clamp($1 * 31.0, "
                        + "vec2<f32>(0.0), vec2<f32>(31.0))), 0)",
                sampledTexture, uv);
        fragment.output("color",
                ShaderGraphStageSemantic.location(0), color);
        return ShaderGraphProgram.builder(
                        "graph_compute_handoff_program",
                        vertex.build(), fragment.build())
                .entryPoints("handoffVertex", "handoffFragment")
                .build();
    }
}
