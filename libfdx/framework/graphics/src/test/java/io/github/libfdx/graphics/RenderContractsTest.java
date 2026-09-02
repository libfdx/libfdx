package io.github.libfdx.graphics;

import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RenderContractsTest {
    private static final ProviderId TEST_PROVIDER = ProviderId.of("render-contract-test");

    @Test
    void renderTargetKeysDistinguishFormatsAttachmentsDepthAndSamples() {
        RenderTargetLayout rgba = RenderTargetLayout.color(TextureFormat.RGBA8_UNORM);
        RenderTargetLayout bgra = RenderTargetLayout.color(TextureFormat.BGRA8_UNORM);
        RenderTargetLayout mrt = RenderTargetLayout.of(
                new TextureFormat[] { TextureFormat.RGBA8_UNORM, TextureFormat.R32_FLOAT },
                TextureFormat.UNKNOWN, 1);
        RenderTargetLayout depth = RenderTargetLayout.of(
                new TextureFormat[] { TextureFormat.RGBA8_UNORM },
                TextureFormat.DEPTH32_FLOAT, 1);
        RenderTargetLayout multisampled = RenderTargetLayout.of(
                new TextureFormat[] { TextureFormat.RGBA8_UNORM },
                TextureFormat.DEPTH32_FLOAT, 4);

        assertNotEquals(rgba.structuralKey(), bgra.structuralKey());
        assertNotEquals(rgba.structuralKey(), mrt.structuralKey());
        assertNotEquals(rgba.structuralKey(), depth.structuralKey());
        assertNotEquals(depth.structuralKey(), multisampled.structuralKey());
    }

    @Test
    void renderPassDerivesExactMrtDepthResolveCompatibility() {
        TextureView color0 = new FakeView(TextureFormat.RGBA8_UNORM, 320, 180, 4);
        TextureView color1 = new FakeView(TextureFormat.R32_FLOAT, 320, 180, 4);
        TextureView resolve0 = new FakeView(TextureFormat.RGBA8_UNORM, 320, 180, 1);
        TextureView depth = new FakeView(TextureFormat.DEPTH32_FLOAT, 320, 180, 4);
        RenderPassDescriptor descriptor = new RenderPassDescriptor()
                .colorAttachments(
                        RenderPassColorAttachment.resolve(color0, resolve0,
                                LoadOp.clear(0, 0, 0, 1), StoreOp.store()),
                        RenderPassColorAttachment.of(color1, LoadOp.load(), StoreOp.discard()))
                .depthStencilAttachment(RenderPassDepthStencilAttachment.of(
                        depth, LoadOp.clear(1, 0, 0, 0), StoreOp.store(),
                        LoadOp.load(), StoreOp.store()));

        RenderPassCompatibility compatibility = descriptor.validate(advancedCapabilities());

        assertEquals(320, compatibility.width());
        assertEquals(180, compatibility.height());
        assertEquals(2, compatibility.targetLayout().colorAttachmentCount());
        assertEquals(TextureFormat.DEPTH32_FLOAT,
                compatibility.targetLayout().depthStencilFormat());
        assertEquals(4, compatibility.targetLayout().sampleCount());
    }

    @Test
    void attachmentDimensionAndExplicitMetadataMismatchesFail() {
        TextureView color0 = new FakeView(TextureFormat.RGBA8_UNORM, 320, 180, 1);
        TextureView color1 = new FakeView(TextureFormat.R32_FLOAT, 640, 360, 1);
        assertThrows(FdxException.class, () -> new RenderPassDescriptor()
                .colorAttachments(
                        RenderPassColorAttachment.of(color0, LoadOp.load(), StoreOp.store()),
                        RenderPassColorAttachment.of(color1, LoadOp.load(), StoreOp.store()))
                .compatibility());

        assertThrows(FdxException.class, () -> RenderPassDescriptor
                .color(color0, LoadOp.load(), StoreOp.store())
                .compatibility(RenderPassCompatibility.of(
                        RenderTargetLayout.color(TextureFormat.RGBA8_UNORM), 1, 1))
                .compatibility());
    }

    @Test
    void conservativeCapabilitiesRejectAdvancedContractsBeforeProviderCalls() {
        TextureView color = new FakeView(TextureFormat.RGBA8_UNORM, 64, 64, 1);
        TextureView depth = new FakeView(TextureFormat.DEPTH32_FLOAT, 64, 64, 1);
        RenderPassDescriptor explicitDepth = RenderPassDescriptor
                .color(color, LoadOp.load(), StoreOp.store())
                .depthStencilAttachment(RenderPassDepthStencilAttachment.of(
                        depth, LoadOp.load(), StoreOp.store(),
                        LoadOp.load(), StoreOp.store()));
        assertThrows(FdxException.class, () ->
                explicitDepth.validate(GraphicsCapabilities.conservativeRender()));

        RenderPipelineDescriptor customState = RenderPipelineDescriptor
                .shader(new FakeShaderModule(), TextureFormat.RGBA8_UNORM)
                .colorTargets(ColorTargetState.opaque(TextureFormat.RGBA8_UNORM));
        assertThrows(FdxException.class, () ->
                customState.validate(GraphicsCapabilities.conservativeRender()));

        RenderTargetLayout mrt = RenderTargetLayout.of(
                new TextureFormat[] { TextureFormat.RGBA8_UNORM, TextureFormat.BGRA8_UNORM },
                TextureFormat.UNKNOWN, 1);
        assertThrows(FdxException.class, () ->
                mrt.validate(GraphicsCapabilities.conservativeRender()));

        RenderPassDescriptor unsupportedResolve = new RenderPassDescriptor()
                .colorAttachments(RenderPassColorAttachment.resolve(
                        new FakeView(TextureFormat.R32_FLOAT, 64, 64, 4),
                        new FakeView(TextureFormat.R32_FLOAT, 64, 64, 1),
                        LoadOp.load(), StoreOp.store()));
        assertThrows(FdxException.class, () ->
                unsupportedResolve.validate(advancedCapabilities()));
    }

    @Test
    void alphaBlendControlAllowsOpaqueColorTargetsWithoutCompleteState() {
        GraphicsCapabilities capabilities = GraphicsCapabilities.builder()
                .clipDepthRange(ClipDepthRange.ZERO_TO_ONE)
                .profile(ShaderProfile.PORTABLE_WEBGL2)
                .feature(GraphicsFeature.ALPHA_BLEND_CONTROL)
                .colorFormats(TextureFormat.RGBA8_UNORM)
                .sampleCounts(1)
                .limits(GraphicsLimits.conservativeRender())
                .build();
        RenderPipelineDescriptor opaque = RenderPipelineDescriptor
                .shader(new FakeShaderModule(), TextureFormat.RGBA8_UNORM)
                .colorTargets(ColorTargetState.opaque(
                        TextureFormat.RGBA8_UNORM));

        assertDoesNotThrow(() -> opaque.validate(capabilities));
    }

    @Test
    void structuralVertexLayoutsCompareByContent() {
        VertexLayout first = VertexLayout.of(20,
                VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT32X2, 12));
        VertexLayout equivalent = VertexLayout.of(20,
                VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT32X2, 12));
        VertexLayout incompatible = VertexLayout.of(24,
                VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT32X2, 16));

        assertEquals(first, equivalent);
        assertEquals(first.structuralKey(), equivalent.structuralKey());
        assertNotEquals(first, incompatible);
        assertNotEquals(first.structuralKey(), incompatible.structuralKey());
    }

    @Test
    void defaultDeviceRejectsComputeThroughTheCommonContract() {
        GraphicsDevice device = new NonComputeDevice();
        assertThrows(FdxException.class, () -> device.createComputePipeline(
                ComputePipelineDescriptor.shader(new FakeShaderModule())
                        .entryPoint("computeMain")));
    }

    private static GraphicsCapabilities advancedCapabilities() {
        return GraphicsCapabilities.builder()
                .clipDepthRange(ClipDepthRange.ZERO_TO_ONE)
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .feature(GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS)
                .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
                .feature(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS)
                .feature(GraphicsFeature.MULTISAMPLE)
                .feature(GraphicsFeature.RESOLVE_ATTACHMENTS)
                .feature(GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE)
                .colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.R32_FLOAT)
                .depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
                .resolveFormats(TextureFormat.RGBA8_UNORM)
                .sampleCounts(1, 4)
                .limits(GraphicsLimits.builder()
                        .maxBindGroups(4)
                        .maxBindingsPerGroup(32)
                        .maxUniformBuffersPerStage(4)
                        .maxSampledTexturesPerStage(8)
                        .maxSamplersPerStage(8)
                        .maxColorAttachments(4)
                        .maxVertexBuffers(4)
                        .maxVertexAttributes(16)
                        .maxUniformBufferBindingSize(64 * 1024)
                        .build())
                .build();
    }

    private record FakeView(TextureFormat format, int width, int height,
            int sampleCount) implements TextureView {
        @Override
        public ProviderId providerId() {
            return TEST_PROVIDER;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    private static final class FakeShaderModule implements ShaderModule {
        @Override
        public ShaderLanguage language() {
            return ShaderLanguage.WGSL;
        }

        @Override
        public ProviderId providerId() {
            return TEST_PROVIDER;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }

    private static final class NonComputeDevice implements GraphicsDevice {
        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProviderId providerId() {
            return TEST_PROVIDER;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }

    /**
     * A device must state which depth range it clips against. Guessing is what
     * made the original defect invisible: an OpenGL-convention projection fed
     * to a zero-to-one clipper discards everything nearer than about twice the
     * near plane, with no error anywhere.
     */
    @Test
    void capabilitiesRequireAnExplicitClipDepthRange() {
        FdxException failure = assertThrows(FdxException.class,
                () -> GraphicsCapabilities.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGPU)
                        .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
                        .colorFormats(TextureFormat.RGBA8_UNORM)
                        .depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
                        .sampleCounts(1)
                        .build());
        assertTrue(failure.getMessage().contains("clip depth range"),
                "expected a clip depth range message, got: " + failure.getMessage());
    }

    @Test
    void conservativeCapabilitiesUseTheOpenGlConvention() {
        // The conservative profile is WebGL2, which clips -w..w.
        assertEquals(ClipDepthRange.NEGATIVE_ONE_TO_ONE,
                GraphicsCapabilities.conservativeRender().clipDepthRange());
    }
}
