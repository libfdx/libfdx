package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.TextureView;
import java.util.Arrays;

/** Attachment-owned reusable framebuffer objects for explicit MRT and resolve passes. */
final class GLMultipleTargets {
    private final GLApi gl;
    private final GLResourceDomain domain;
    private final Object owner;
    private final GLTextureViewHandle[] colors = new GLTextureViewHandle[8];
    private final GLTextureViewHandle[] resolves = new GLTextureViewHandle[8];
    private GLTextureViewHandle depth;
    private int framebuffer;
    private int resolveFramebuffer;
    private int depthBuffer;
    private int depthWidth, depthHeight, depthSamples;
    private int count;

    GLMultipleTargets(GLApi gl, GLResourceDomain domain, Object owner) {
        this.gl = gl;
        this.domain = domain;
        this.owner = owner;
    }

    void begin(RenderPassDescriptor descriptor) {
        var attachments = descriptor.colorAttachments();
        count = attachments.length;
        for (int i = 0; i < count; i++) {
            colors[i] = require(attachments[i].view());
            resolves[i] = attachments[i].resolveView() == null ? null : require(attachments[i].resolveView());
        }
        var explicitDepth = descriptor.depthStencilAttachment();
        depth = explicitDepth == null ? null : require(explicitDepth.view());
        if (framebuffer == 0) framebuffer = gl.genFramebuffer();
        gl.bindFramebuffer(framebuffer);
        for (int i = 0; i < colors.length; i++) {
            if (i < count) attach(i, colors[i]);
            else gl.framebufferTexture(i, 0, 0, 1);
        }
        if (depth != null) attach(-1, depth);
        else if (descriptor.depthEnabled()) {
            int width = colors[0].width(), height = colors[0].height(), samples = colors[0].sampleCount();
            if (depthBuffer == 0) depthBuffer = gl.genRenderbuffer();
            gl.bindRenderbuffer(depthBuffer);
            if (depthWidth != width || depthHeight != height || depthSamples != samples) {
                gl.renderbufferStorageDepth(width, height, samples);
                depthWidth = width;
                depthHeight = height;
                depthSamples = samples;
            }
            gl.framebufferRenderbufferDepth(depthBuffer);
            gl.bindRenderbuffer(0);
        } else gl.framebufferTexture(-1, 0, 0, 1);
        gl.drawBuffers(count);
        if (!gl.framebufferComplete()) throw new FdxException("GL multiple-target framebuffer is incomplete");
        gl.viewport(0, 0, colors[0].width(), colors[0].height());
        gl.framebufferSrgb(true);
        gl.enableScissorTest(false);
        gl.resetAttachmentWriteMasks();
        for (int i = 0; i < count; i++) {
            LoadOp clear = attachments[i].loadOp();
            if (clear.isClear()) gl.clearColorAttachment(i, clear.red(), clear.green(), clear.blue(), clear.alpha());
        }
        if (descriptor.depthEnabled()) {
            gl.depthMask(true);
            if (explicitDepth != null ? explicitDepth.depthLoadOp().isClear() : descriptor.depthClearEnabled()) {
                gl.clearDepth(descriptor.depthClearValue());
                gl.clearDepthBuffer();
            }
        }
    }

    private GLTextureViewHandle require(TextureView view) {
        GLTextureViewHandle result = GLResources.requireTextureView(view, domain, owner, "Render attachment");
        if (!result.textureBacked() || !result.textureHandle().usage().renderAttachment()) {
            throw new FdxException("GL multiple targets require offscreen render-attachment textures");
        }
        return result;
    }

    private void attach(int slot, GLTextureViewHandle view) {
        gl.framebufferTexture(slot, view.textureHandle().texture(), view.mipLevel(), view.sampleCount());
    }

    boolean contains(GLTextureHandle texture) {
        if (depth != null && depth.textureHandle() == texture) return true;
        for (int i = 0; i < count; i++) {
            if (colors[i].textureHandle() == texture || resolves[i] != null && resolves[i].textureHandle() == texture) return true;
        }
        return false;
    }

    void requireLive() {
        if (depth != null && depth.textureHandle().isDisposed()) throw new FdxException("GL depth attachment was disposed");
        for (int i = 0; i < count; i++) {
            if (colors[i].textureHandle().isDisposed() || resolves[i] != null && resolves[i].textureHandle().isDisposed()) {
                throw new FdxException("GL color or resolve attachment was disposed");
            }
        }
    }

    void end() {
        if (!domain.isLost()) {
            requireLive();
            gl.enableScissorTest(false);
            for (int i = 0; i < count; i++) {
                if (resolves[i] == null) continue;
                if (resolveFramebuffer == 0) resolveFramebuffer = gl.genFramebuffer();
                gl.bindFramebuffer(resolveFramebuffer);
                attach(0, resolves[i]);
                gl.drawBuffers(1);
                if (!gl.framebufferComplete()) throw new FdxException("GL resolve framebuffer is incomplete");
                gl.resolveColorFramebuffer(framebuffer, i, resolveFramebuffer, colors[i].width(), colors[i].height());
            }
        }
        Arrays.fill(colors, null);
        Arrays.fill(resolves, null);
        depth = null;
        count = 0;
    }

    void dispose() {
        if (!domain.isLost()) {
            if (framebuffer != 0) gl.deleteFramebuffer(framebuffer);
            if (resolveFramebuffer != 0) gl.deleteFramebuffer(resolveFramebuffer);
            if (depthBuffer != 0) gl.deleteRenderbuffer(depthBuffer);
        }
        framebuffer = resolveFramebuffer = depthBuffer = 0;
    }
}
