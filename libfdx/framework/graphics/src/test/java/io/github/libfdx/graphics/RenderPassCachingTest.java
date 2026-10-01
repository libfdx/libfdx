package io.github.libfdx.graphics;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.math.ClipDepthRange;

import org.junit.jupiter.api.Test;

class RenderPassCachingTest {
    @Test
    void successfulLayoutValidationDoesNotHideAnotherDevicesRestrictions() {
        RenderTargetLayout layout = RenderTargetLayout.color(TextureFormat.RGBA8_UNORM);
        GraphicsCapabilities supported = GraphicsCapabilities.conservativeRender();
        GraphicsCapabilities unsupported =
                GraphicsCapabilities.builder()
                        .profile(ShaderProfile.NATIVE)
                        .clipDepthRange(ClipDepthRange.NEGATIVE_ONE_TO_ONE)
                        .colorFormats(TextureFormat.BGRA8_UNORM)
                        .build();
        String key = layout.structuralKey();
        layout.validate(supported);
        layout.validate(supported);
        assertThrows(FdxException.class, () -> layout.validate(unsupported));
        assertThrows(FdxException.class, () -> layout.validate(unsupported));
        assertThrows(FdxException.class, () -> layout.validate(null));
        layout.validate(supported);
        assertEquals(key, layout.structuralKey());
        assertEquals(RenderTargetLayout.color(TextureFormat.RGBA8_UNORM), layout);
    }

    @Test
    void cachedLayoutValidationStillChecksChangedAttachments() {
        View view = new View();
        RenderPassDescriptor descriptor =
                RenderPassDescriptor.color(view, LoadOp.load(), StoreOp.store());
        GraphicsCapabilities capabilities = GraphicsCapabilities.conservativeRender();
        descriptor.validate(capabilities);
        descriptor.validate(capabilities);
        view.samples = 4;
        assertThrows(FdxException.class, () -> descriptor.validate(capabilities));
        view.samples = 1;
        descriptor.validate(capabilities);
        descriptor.compatibility(descriptor.compatibility());
        view.width = 100;
        assertThrows(FdxException.class, () -> descriptor.validate(capabilities));
    }

    @Test
    void unchangedAttachmentsReuseMetadataWhileResizeAndFormatChangesRemainVisible() {
        View view = new View();
        RenderPassDescriptor descriptor =
                RenderPassDescriptor.color(view, LoadOp.load(), StoreOp.store());
        RenderPassCompatibility first = descriptor.compatibility();
        assertSame(first, descriptor.compatibility());
        descriptor.colorAttachment(view).colorLoadOp(LoadOp.clear(1, 0, 0, 1));
        assertSame(first, descriptor.compatibility());
        view.width = 100;
        RenderPassCompatibility resized = descriptor.compatibility();
        assertNotSame(first, resized);
        assertSame(first.targetLayout(), resized.targetLayout());
        assertEquals(100, resized.width());
        assertEquals(64, first.width());
        view.format = TextureFormat.RGBA8_UNORM_SRGB;
        RenderPassCompatibility changed = descriptor.compatibility();
        assertEquals(RenderTargetLayout.color(view.format), changed.targetLayout());
        assertEquals(
                RenderTargetLayout.color(view.format).structuralKey(),
                changed.targetLayout().structuralKey());
        descriptor.depthEnabled(true);
        assertEquals(
                TextureFormat.DEPTH32_FLOAT,
                descriptor.compatibility().targetLayout().depthStencilFormat());
        view.samples = 4;
        assertEquals(4, descriptor.compatibility().targetLayout().sampleCount());
        assertSame(descriptor.compatibility(), descriptor.compatibility());
        view.samples = 3;
        assertThrows(FdxException.class, descriptor::compatibility);
    }

    @Test
    void compatibilityCacheDoesNotSuppressAttachmentOrExplicitMetadataValidation() {
        View color = new View();
        View resolve = new View();
        color.samples = 4;
        RenderPassDescriptor descriptor =
                new RenderPassDescriptor()
                        .colorAttachments(
                                RenderPassColorAttachment.resolve(
                                        color, resolve, LoadOp.load(), StoreOp.store()));
        RenderPassCompatibility initial = descriptor.compatibility();
        assertSame(initial, descriptor.compatibility());
        resolve.samples = 2;
        assertThrows(FdxException.class, descriptor::compatibility);
        resolve.samples = 1;
        resolve.width = 63;
        assertThrows(FdxException.class, descriptor::compatibility);
        resolve.width = 64;
        resolve.format = TextureFormat.RGBA8_UNORM_SRGB;
        assertThrows(FdxException.class, descriptor::compatibility);
        resolve.format = color.format;
        descriptor.compatibility(initial);
        color.width = resolve.width = 100;
        assertThrows(FdxException.class, descriptor::compatibility);
        descriptor.compatibility(initial.withDimensions(100, 48));
        assertEquals(100, descriptor.compatibility().width());
        assertThrows(FdxException.class, () -> initial.withDimensions(1, 0));
    }

    @Test
    void publicAttachmentSnapshotsStayDefensiveAndRetainTheirLoadOperation() {
        View view = new View();
        RenderPassDescriptor descriptor =
                RenderPassDescriptor.color(view, LoadOp.load(), StoreOp.store());
        RenderPassColorAttachment[] first = descriptor.colorAttachments();
        RenderPassColorAttachment[] second = descriptor.colorAttachments();
        first[0] = null;
        assertNotNull(descriptor.colorAttachments()[0]);
        LoadOp clear = LoadOp.clear(0, 0, 1, 1);
        descriptor.colorLoadOp(clear);
        assertSame(LoadOp.load(), second[0].loadOp());
        assertSame(clear, descriptor.colorAttachments()[0].loadOp());
        RenderPassColorAttachment[] supplied = descriptor.colorAttachments();
        descriptor.colorAttachments(supplied);
        supplied[0] = null;
        assertSame(view, descriptor.colorAttachment());
        descriptor.colorAttachment(view);
        assertSame(view, descriptor.colorAttachments()[0].view());
    }

    private static final class View implements TextureView {
        int width = 64;
        int height = 48;
        int samples = 1;
        TextureFormat format = TextureFormat.RGBA8_UNORM;

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int sampleCount() {
            return samples;
        }

        public TextureFormat format() {
            return format;
        }

        public ProviderId providerId() {
            return ProviderId.of("test");
        }

        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }
}
