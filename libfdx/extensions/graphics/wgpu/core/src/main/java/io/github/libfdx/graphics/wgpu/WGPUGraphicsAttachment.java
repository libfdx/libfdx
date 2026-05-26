package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentReadiness;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureFormat;

final class WGPUGraphicsAttachment implements GraphicsAttachment, GraphicsAttachmentReadiness {
    private final WGPUContext context;

    WGPUGraphicsAttachment(WGPUContext context) {
        this.context = context;
    }

    @Override
    public void resize(int framebufferWidth, int framebufferHeight) {
        context.resize(framebufferWidth, framebufferHeight);
    }

    @Override
    public void processEvents() {
        context.processEvents();
    }

    @Override
    public boolean beginFrame() {
        return context.beginFrame();
    }

    public boolean isReady() {
        return context.isReady();
    }

    @Override
    public void endFrame() {
        context.endFrame();
    }

    @Override
    public GraphicsDevice device() {
        return context.device();
    }

    @Override
    public TextureFormat surfaceFormat() {
        return context.surfaceFormat();
    }

    @Override
    public GraphicsFrame currentFrame() {
        return context.currentFrame();
    }

    @Override
    public void clear(float red, float green, float blue, float alpha) {
        context.clear(red, green, blue, alpha);
    }

    @Override
    public ProviderId providerId() {
        return context.providerId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) context;
    }

    @Override
    public void dispose() {
        context.dispose();
    }

    @Override
    public boolean isDisposed() {
        return context.isDisposed();
    }
}
