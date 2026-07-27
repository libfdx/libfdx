package io.github.libfdx.tests.graphics;

import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassColorAttachment;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDepthStencilAttachment;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompiler;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderProgram;

/**
 * Compiles complete graph-owned stages, resolves them through the common
 * ShaderProvider contract, and renders an MRT/depth pass without a PBR or
 * renderer shader template.
 */
public final class ShaderGraphProgramTest extends GraphicsParityTest {
    private static final int TARGET_WIDTH = 96;
    private static final int TARGET_HEIGHT = 64;

    private ShaderGraphProvider provider;
    private ResolvedShaderPass resolved;
    private Texture color0;
    private Texture color1;
    private Texture depth;
    private RenderPassDescriptor offscreenPass;
    private boolean multipleTargets;
    private boolean explicitDepth;

    public ShaderGraphProgramTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "ShaderGraphProgramTest");
        multipleTargets = graphics.device().capabilities().supports(
                GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS);
        explicitDepth = graphics.device().capabilities().supports(
                GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS);

        ShaderGraphProgramCompileResult compilation =
                new ShaderGraphProgramCompiler().compile(
                        program(multipleTargets, explicitDepth),
                        ShaderGraphCompileOptions.builder()
                                .profile(ShaderProfile.PORTABLE_WEBGPU)
                                .capabilities(graphics.device().capabilities())
                                .build());
        if (!compilation.success()) {
            throw new FdxException("Complete shader graph program did not compile");
        }
        ShaderModuleDescriptor module = ShaderModuleDescriptor.wgsl(
                        "complete shader graph MRT", compilation.wgsl())
                .entryPoints(compilation.vertexEntryPoint(),
                        compilation.fragmentEntryPoint());
        provider = new ShaderGraphProvider(graphics,
                ShaderGraphRenderProgram.builder(ShaderPassId.FORWARD, module)
                        .entryPoints(compilation.vertexEntryPoint(),
                                compilation.fragmentEntryPoint())
                        .label("complete shader graph MRT")
                        .build());

        TextureFormat[] colorFormats = multipleTargets
                ? new TextureFormat[] {
                        TextureFormat.RGBA8_UNORM,
                        TextureFormat.RGBA8_UNORM
                }
                : new TextureFormat[] { TextureFormat.RGBA8_UNORM };
        RenderTargetLayout targetLayout = RenderTargetLayout.of(colorFormats,
                explicitDepth ? TextureFormat.DEPTH32_FLOAT
                        : TextureFormat.UNKNOWN, 1);
        resolved = provider.resolve(ShaderRequest.builder(ShaderPassId.FORWARD)
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .renderPass(RenderPassCompatibility.of(targetLayout,
                        TARGET_WIDTH, TARGET_HEIGHT))
                .build());

        color0 = texture("graph MRT color 0", TextureFormat.RGBA8_UNORM);
        if (multipleTargets) {
            color1 = texture("graph MRT color 1", TextureFormat.RGBA8_UNORM);
        }
        if (explicitDepth) {
            depth = texture("graph MRT depth", TextureFormat.DEPTH32_FLOAT);
        }
        offscreenPass = new RenderPassDescriptor()
                .label("complete graph render pass");
        if (explicitDepth) {
            offscreenPass.depthStencilAttachment(
                    RenderPassDepthStencilAttachment.of(
                            depth.view(), LoadOp.clear(1, 0, 0, 0),
                            StoreOp.store(), LoadOp.load(), StoreOp.store()));
        }
        if (multipleTargets) {
            offscreenPass.colorAttachments(
                    RenderPassColorAttachment.of(color0.view(),
                            LoadOp.clear(0, 0, 0, 1), StoreOp.store()),
                    RenderPassColorAttachment.of(color1.view(),
                            LoadOp.clear(0, 0, 0, 1), StoreOp.store()));
        } else {
            offscreenPass.colorAttachments(RenderPassColorAttachment.of(
                    color0.view(), LoadOp.clear(0, 0, 0, 1),
                    StoreOp.store()));
        }
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(offscreenPass);
        if (!pass.compatibility().isCompatible(
                resolved.pipeline().targetLayout())) {
            throw new FdxException(
                    "Graph MRT pipeline did not match the active render pass");
        }
        pass.setPipeline(resolved.pipeline());
        pass.draw(3, 1, 0, 0);
        pass.end();

        RenderPass status = frame.commandEncoder().beginRenderPass(
                RenderPassDescriptor.color(frame.colorAttachment(),
                        LoadOp.clear(0.10f, 0.34f, 0.72f, 1),
                        StoreOp.store()).label("graph program status"));
        status.end();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(depth);
        dispose(color1);
        dispose(color0);
        dispose(provider);
        verifyDisposed();
    }

    private Texture texture(String label, TextureFormat format) {
        return graphics.device().createTexture(new TextureDescriptor()
                .label(label)
                .size(TARGET_WIDTH, TARGET_HEIGHT)
                .format(format)
                .usage(TextureUsage.RENDER_ATTACHMENT));
    }

    private static ShaderGraphProgram program(boolean multipleTargets,
            boolean fragmentDepth) {
        ShaderGraphType f32 = ShaderGraphType.scalar(ShaderScalarType.F32);
        ShaderGraphType u32 = ShaderGraphType.scalar(ShaderScalarType.U32);
        ShaderGraphType vec4 = ShaderGraphType.vector(
                ShaderScalarType.F32, 4);

        ShaderGraphBuilder vertex = new ShaderGraphBuilder(
                "runtime_graph_vertex", ShaderGraphKind.VERTEX);
        vertex.parameter(ShaderGraphParameter.semantic("vertex_index", u32,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.VERTEX_INDEX));
        ShaderExpression index = vertex.parameter("index_node", "vertex_index");
        ShaderExpression x = vertex.customWgsl("x_position", f32,
                "select(-0.8, 0.8, $0 == 1u)", index);
        ShaderExpression y = vertex.customWgsl("y_position", f32,
                "select(-0.7, 0.8, $0 == 2u)", index);
        vertex.output("position", ShaderGraphStageSemantic.POSITION,
                vertex.construct("clip_position", vec4, x, y,
                        vertex.floatValue(0.25f), vertex.floatValue(1)));

        ShaderGraphBuilder fragment = new ShaderGraphBuilder(
                "runtime_graph_fragment", ShaderGraphKind.FRAGMENT);
        fragment.output("color0", ShaderGraphStageSemantic.location(0),
                fragment.construct("blue", vec4,
                        fragment.floatValue(0.10f),
                        fragment.floatValue(0.34f),
                        fragment.floatValue(0.72f),
                        fragment.floatValue(1)));
        if (multipleTargets) {
            fragment.output("color1", ShaderGraphStageSemantic.location(1),
                    fragment.construct("orange", vec4,
                            fragment.floatValue(0.92f),
                            fragment.floatValue(0.36f),
                            fragment.floatValue(0.08f),
                            fragment.floatValue(1)));
        }
        if (fragmentDepth) {
            fragment.output("depth", ShaderGraphStageSemantic.FRAGMENT_DEPTH,
                    fragment.floatValue(0.25f));
        }

        ShaderGraph vertexGraph = vertex.build();
        ShaderGraph fragmentGraph = fragment.build();
        return ShaderGraphProgram.builder("runtime_complete_program",
                        vertexGraph, fragmentGraph)
                .entryPoints("graphVertex", "graphFragment")
                .build();
    }
}
