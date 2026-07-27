package io.github.libfdx.tests.graphics;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.CullMode;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.FrontFace;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassColorAttachment;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDepthStencilAttachment;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphPipelineState;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompiler;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;

/**
 * Executes an explicitly scheduled depth/forward/outline graph technique,
 * capability fallback, bounded caching, and atomic whole-technique reload.
 */
public final class ShaderGraphTechniqueTest extends GraphicsParityTest {
    private static final int WIDTH = 96;
    private static final int HEIGHT = 64;
    private static final ShaderPassId OUTLINE =
            ShaderPassId.of("outline");

    private ShaderGraphProvider provider;
    private ResolvedShaderPass depthPass;
    private ResolvedShaderPass forwardPass;
    private ResolvedShaderPass outlinePass;
    private Texture color;
    private Texture depth;
    private RenderPassCompatibility compatibility;
    private boolean firstFrame = true;

    public ShaderGraphTechniqueTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "ShaderGraphTechniqueTest");
        graphics.device().capabilities().require(
                GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS);
        graphics.device().capabilities().require(
                GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE);

        ShaderGraphTechniqueCompiler compiler =
                new ShaderGraphTechniqueCompiler();
        ShaderGraphCompileOptions options =
                ShaderGraphCompileOptions.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGPU)
                        .capabilities(graphics.device().capabilities())
                        .build();
        ShaderGraphTechniqueCompileResult initial =
                compiler.compile(technique("initial", 0.12f), options);
        provider = new ShaderGraphProvider(graphics, initial, 4);

        color = texture("technique color", TextureFormat.RGBA8_UNORM);
        depth = texture("technique depth", TextureFormat.DEPTH32_FLOAT);
        compatibility = RenderPassCompatibility.of(
                initial.technique().pass(ShaderPassId.FORWARD)
                        .pipelineState().targetLayout(),
                WIDTH, HEIGHT);

        forwardPass = resolve(ShaderPassId.FORWARD, "native-only");
        long revision = provider.revision();
        ShaderGraphTechniqueCompileResult unsupported =
                compiler.compile(techniqueWithInvalidResource(),
                        ShaderGraphCompileOptions.builder()
                                .profile(ShaderProfile.PORTABLE_WEBGPU)
                                .build());
        try {
            provider.replace(unsupported);
            throw new FdxException(
                    "Invalid replacement technique unexpectedly succeeded");
        } catch (FdxException expected) {
            if (provider.revision() != revision
                    || provider.resolve(request(ShaderPassId.FORWARD,
                            "native-only")).pipeline()
                            != forwardPass.pipeline()) {
                throw new FdxException(
                        "Failed replacement changed the active technique",
                        expected);
            }
        }

        ShaderGraphTechniqueCompileResult replacement =
                compiler.compile(technique("replacement", 0.72f), options);
        provider.replace(replacement);
        if (provider.revision() != revision + 1
                || provider.passCount() != 3) {
            throw new FdxException(
                    "Whole-technique replacement was not atomic");
        }
        depthPass = resolve(ShaderPassId.DEPTH, "");
        forwardPass = resolve(ShaderPassId.FORWARD, "native-only");
        outlinePass = resolve(OUTLINE, "");
        if (depthPass.providerRevision() != forwardPass.providerRevision()
                || forwardPass.providerRevision()
                        != outlinePass.providerRevision()
                || provider.cachedPipelineCount() > 4) {
            throw new FdxException(
                    "Technique passes exposed mixed revisions or an unbounded cache");
        }
        markCreated();
    }

    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        draw(frame, depthPass, firstFrame);
        draw(frame, forwardPass, false);
        draw(frame, outlinePass, false);
        firstFrame = false;

        RenderPass status = frame.commandEncoder().beginRenderPass(
                RenderPassDescriptor.color(frame.colorAttachment(),
                        LoadOp.clear(0.22f, 0.10f, 0.48f, 1),
                        StoreOp.store()).label("technique status"));
        status.end();
        finishFrame();
    }

    private void draw(GraphicsFrame frame, ResolvedShaderPass resolved,
            boolean clear) {
        RenderPassDescriptor descriptor = new RenderPassDescriptor()
                .label("technique " + resolved.passId())
                .colorAttachments(RenderPassColorAttachment.of(
                        color.view(),
                        clear ? LoadOp.clear(0, 0, 0, 1)
                                : LoadOp.load(),
                        StoreOp.store()))
                .depthStencilAttachment(
                        RenderPassDepthStencilAttachment.of(depth.view(),
                                clear ? LoadOp.clear(1, 0, 0, 0)
                                        : LoadOp.load(),
                                StoreOp.store(), LoadOp.load(),
                                StoreOp.store()));
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor);
        pass.setPipeline(resolved.pipeline());
        pass.draw(3, 1, 0, 0);
        pass.end();
    }

    @Override
    public void dispose() {
        dispose(depth);
        dispose(color);
        dispose(provider);
        verifyDisposed();
    }

    private ResolvedShaderPass resolve(ShaderPassId pass, String variant) {
        return provider.resolve(request(pass, variant));
    }

    private ShaderRequest request(ShaderPassId pass, String variant) {
        return ShaderRequest.builder(pass)
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .renderPass(compatibility)
                .topology(PrimitiveTopology.TRIANGLE_LIST)
                .variantKey(variant)
                .build();
    }

    private Texture texture(String label, TextureFormat format) {
        return graphics.device().createTexture(new TextureDescriptor()
                .label(label).size(WIDTH, HEIGHT).format(format)
                .usage(TextureUsage.RENDER_ATTACHMENT));
    }

    private static ShaderGraphTechnique technique(String id,
            float colorBias) {
        ShaderGraphProgram depthProgram = program(id + "_depth",
                colorBias, 0.08f, 0.12f, -1);
        ShaderGraphProgram forwardProgram = program(id + "_forward",
                0.10f, colorBias, 0.24f, -1);
        ShaderGraphProgram outlineProgram = program(id + "_outline",
                colorBias, 0.18f, 0.04f, -1);

        ShaderGraphVariant normal = ShaderGraphVariant.builder(
                "", forwardProgram).build();
        ShaderGraphVariant nativeOnly = ShaderGraphVariant.builder(
                        "native-only", forwardProgram)
                .profiles(ShaderProfile.NATIVE)
                .fallback("")
                .build();
        return ShaderGraphTechnique.builder(id)
                .passes(
                        ShaderGraphTechniquePass.builder(ShaderPassId.DEPTH,
                                        state(CullMode.BACK,
                                                CompareFunction.LESS,
                                                true, false))
                                .variants(ShaderGraphVariant.builder("",
                                        depthProgram).build())
                                .build(),
                        ShaderGraphTechniquePass.builder(
                                        ShaderPassId.FORWARD,
                                        state(CullMode.BACK,
                                                CompareFunction.LESS_EQUAL,
                                                true, false))
                                .variants(nativeOnly, normal)
                                .build(),
                        ShaderGraphTechniquePass.builder(OUTLINE,
                                        state(CullMode.FRONT,
                                                CompareFunction.ALWAYS,
                                                false, true))
                                .variants(ShaderGraphVariant.builder("",
                                        outlineProgram).build())
                                .build())
                .maxVariants(8)
                .build();
    }

    private static ShaderGraphTechnique techniqueWithInvalidResource() {
        ShaderGraphProgram program = program("invalid_resource",
                1, 0, 1, 99);
        return ShaderGraphTechnique.builder("invalid-replacement")
                .passes(ShaderGraphTechniquePass.builder(
                                ShaderPassId.FORWARD,
                                state(CullMode.NONE,
                                        CompareFunction.ALWAYS,
                                        false, false))
                        .variants(ShaderGraphVariant.builder("",
                                program).build())
                        .build())
                .build();
    }

    private static ShaderGraphPipelineState state(CullMode cull,
            CompareFunction compare, boolean depthWrite,
            boolean alphaBlend) {
        return ShaderGraphPipelineState.builder()
                .primitive(PrimitiveState.of(
                        PrimitiveTopology.TRIANGLE_LIST,
                        FrontFace.COUNTER_CLOCKWISE, cull))
                .colorTargets(alphaBlend
                        ? ColorTargetState.alpha(TextureFormat.RGBA8_UNORM)
                        : ColorTargetState.opaque(TextureFormat.RGBA8_UNORM))
                .depthStencil(DepthStencilState
                        .builder(TextureFormat.DEPTH32_FLOAT)
                        .depthWriteEnabled(depthWrite)
                        .depthCompare(compare)
                        .build())
                .build();
    }

    private static ShaderGraphProgram program(String id, float red,
            float green, float blue, int resourceGroup) {
        ShaderGraphType f32 =
                ShaderGraphType.scalar(ShaderScalarType.F32);
        ShaderGraphType u32 =
                ShaderGraphType.scalar(ShaderScalarType.U32);
        ShaderGraphType vec2 =
                ShaderGraphType.vector(ShaderScalarType.F32, 2);
        ShaderGraphType vec4 =
                ShaderGraphType.vector(ShaderScalarType.F32, 4);
        ShaderGraphBuilder vertex = new ShaderGraphBuilder(
                id + "_vertex", ShaderGraphKind.VERTEX);
        vertex.parameter(ShaderGraphParameter.semantic("vertex_index", u32,
                        ShaderGraphParameterKind.STAGE_INPUT,
                        null, ShaderGraphStageSemantic.VERTEX_INDEX));
        ShaderExpression index = vertex.parameter("index", "vertex_index");
        ShaderExpression x = vertex.customWgsl("x", f32,
                "select(-0.72, 0.72, $0 == 1u)", index);
        ShaderExpression y = vertex.customWgsl("y", f32,
                "select(-0.62, 0.72, $0 == 2u)", index);
        vertex.output("position", ShaderGraphStageSemantic.POSITION,
                vertex.construct("clip", vec4, x, y,
                        vertex.floatValue(0.25f),
                        vertex.floatValue(1)));

        ShaderGraphBuilder fragment = new ShaderGraphBuilder(
                id + "_fragment", ShaderGraphKind.FRAGMENT);
        ShaderExpression color;
        if (resourceGroup >= 0) {
            ShaderGraphType texture = ShaderGraphType.texture(
                    ShaderTextureDimension.D2,
                    ShaderTextureSampleType.FILTERABLE_FLOAT, false);
            ShaderGraphType sampler = ShaderGraphType.sampler(
                    ShaderSamplerKind.FILTERING);
            fragment.resource(ShaderGraphResource.of(
                    "invalid_texture", texture, resourceGroup, 0));
            fragment.resource(ShaderGraphResource.of(
                    "invalid_sampler", sampler, resourceGroup, 1));
            ShaderExpression uv = fragment.construct("uv", vec2,
                    fragment.floatValue(0.5f),
                    fragment.floatValue(0.5f));
            color = fragment.sample2D("sample",
                    fragment.resource("texture", "invalid_texture"),
                    fragment.resource("sampler", "invalid_sampler"), uv);
        } else {
            color = fragment.construct("color", vec4,
                    fragment.floatValue(red),
                    fragment.floatValue(green),
                    fragment.floatValue(blue),
                    fragment.floatValue(1));
        }
        fragment.output("color", ShaderGraphStageSemantic.location(0),
                color);
        return ShaderGraphProgram.builder(id, vertex.build(),
                fragment.build()).build();
    }
}
