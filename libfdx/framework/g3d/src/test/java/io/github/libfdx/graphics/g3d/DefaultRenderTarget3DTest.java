package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DefaultRenderTarget3DTest {
    private static final ProviderId PROVIDER_ID = ProviderId.of("test-render-target");
    private static final TextureView COLOR = textureView(TextureFormat.RGBA8_UNORM);
    private static final TextureView SECOND_COLOR = textureView(TextureFormat.BGRA8_UNORM);
    private static final TextureView DEPTH = textureView(TextureFormat.UNKNOWN);

    @Test
    void supportsTheSingleColorAttachmentRepresentedByRenderPassDescriptor() {
        DefaultRenderTarget3D target = new DefaultRenderTarget3D(64, 32, COLOR);

        assertEquals(64, target.width());
        assertEquals(32, target.height());
        assertEquals(1, target.colorAttachmentCount());
        assertSame(COLOR, target.colorAttachment(0));
    }

    @Test
    void rejectsMultipleColorAttachmentsInsteadOfIgnoringThem() {
        assertThrows(FdxException.class,
                () -> new DefaultRenderTarget3D(64, 32, new TextureView[] { COLOR, SECOND_COLOR }, null));
    }

    @Test
    void rejectsExplicitDepthAttachmentInsteadOfIgnoringIt() {
        assertThrows(FdxException.class,
                () -> new DefaultRenderTarget3D(64, 32, new TextureView[] { COLOR }, DEPTH));
    }

    private static TextureView textureView(TextureFormat format) {
        return new TextureView() {
            @Override
            public TextureFormat format() {
                return format;
            }

            @Override
            public ProviderId providerId() {
                return PROVIDER_ID;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T as() {
                return (T) this;
            }
        };
    }
}
