package io.github.libfdx.graphics;

import io.github.libfdx.core.Disposable;

public interface GraphicsAttachment extends GraphicsContext, Disposable {
    void resize(int framebufferWidth, int framebufferHeight);

    void processEvents();

    boolean beginFrame();

    void endFrame();
}
