package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.TextureView;

public interface RenderTarget3D {
    int width();

    int height();

    TextureView colorAttachment(int index);

    TextureView depthAttachment();

    int colorAttachmentCount();
}
