package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

public interface GraphicsFrame extends ProviderHandle {
    CommandEncoder commandEncoder();

    FrameBuffer frameBuffer();

    TextureView colorAttachment();

    int width();

    int height();
}
