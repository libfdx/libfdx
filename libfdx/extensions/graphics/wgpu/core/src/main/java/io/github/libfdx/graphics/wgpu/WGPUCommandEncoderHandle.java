package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPULoadOp;
import com.github.xpenatan.webgpu.WGPUQuerySet;
import com.github.xpenatan.webgpu.WGPURenderPassColorAttachment;
import com.github.xpenatan.webgpu.WGPURenderPassDepthStencilAttachment;
import com.github.xpenatan.webgpu.WGPURenderPassDescriptor;
import com.github.xpenatan.webgpu.WGPURenderPassEncoder;
import com.github.xpenatan.webgpu.WGPUStoreOp;
import com.github.xpenatan.webgpu.WGPUTextureView;
import com.github.xpenatan.webgpu.WGPUVectorRenderPassColorAttachment;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;

/**
 * Represents a WGPU command encoder handle.
 *
 * @author xpenatan
 */
final class WGPUCommandEncoderHandle implements CommandEncoder {
    private final WGPUContext context;
    private WGPURenderPass[] renderPasses = new WGPURenderPass[4];
    private int renderPassCount;

    WGPUCommandEncoderHandle(WGPUContext context) {
        this.context = context;
    }

    /**
     * Begins render pass.
     *
     * @param descriptor the descriptor
     * @return the begin render pass
     */
    @Override
    public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPassDescriptor cannot be null");
        }
        if (!context.isFrameStarted()) {
            throw new FdxException("Cannot begin render pass outside a frame");
        }
        ensurePreviousPassEnded();
        WGPUTextureViewHandle attachment = WGPUResources.requireTextureView(descriptor.colorAttachment(), context,
                "Color attachment");
        WGPUTextureHandle renderTarget = attachment.textureHandle();
        if (renderTarget != null) {
            if (!renderTarget.usage().renderAttachment()) {
                throw new FdxException("Color attachment texture was not created for render attachment usage");
            }
            context.markRecordedResource(renderTarget.allocation());
        }
        int attachmentHeight = attachment.height();
        int renderTargetHeight = attachmentHeight > 0 ? attachmentHeight : context.height();

        WGPURenderPassDescriptor passDescriptor = WGPURenderPassDescriptor.obtain();
        passDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        passDescriptor.setLabel(descriptor.label());
        passDescriptor.setOcclusionQuerySet(WGPUQuerySet.NULL);

        WGPURenderPassColorAttachment colorAttachment = WGPURenderPassColorAttachment.obtain();
        colorAttachment.setNextInChain(WGPUChainedStruct.NULL);
        colorAttachment.setView(attachment.nativeView());
        colorAttachment.setResolveTarget(WGPUTextureView.NULL);
        colorAttachment.setLoadOp(descriptor.colorLoadOp().isClear() ? WGPULoadOp.Clear : WGPULoadOp.Load);
        colorAttachment.setStoreOp(descriptor.colorStoreOp().isStore() ? WGPUStoreOp.Store : WGPUStoreOp.Discard);
        colorAttachment.setDepthSlice(-1);
        if (descriptor.colorLoadOp().isClear()) {
            colorAttachment.getClearValue().setR(descriptor.colorLoadOp().red());
            colorAttachment.getClearValue().setG(descriptor.colorLoadOp().green());
            colorAttachment.getClearValue().setB(descriptor.colorLoadOp().blue());
            colorAttachment.getClearValue().setA(descriptor.colorLoadOp().alpha());
        }

        WGPUVectorRenderPassColorAttachment colorAttachments = WGPUVectorRenderPassColorAttachment.obtain();
        colorAttachments.push_back(colorAttachment);
        passDescriptor.setColorAttachments(colorAttachments);
        if (descriptor.depthEnabled()) {
            WGPURenderPassDepthStencilAttachment depthAttachment = WGPURenderPassDepthStencilAttachment.obtain();
            depthAttachment.setView(context.depthTextureView(attachment.width(), attachment.height()));
            depthAttachment.setDepthLoadOp(descriptor.depthClearEnabled() ? WGPULoadOp.Clear : WGPULoadOp.Load);
            depthAttachment.setDepthStoreOp(WGPUStoreOp.Store);
            depthAttachment.setDepthClearValue(descriptor.depthClearValue());
            depthAttachment.setDepthReadOnly(false);
            depthAttachment.setStencilLoadOp(WGPULoadOp.Undefined);
            depthAttachment.setStencilStoreOp(WGPUStoreOp.Undefined);
            depthAttachment.setStencilClearValue(0);
            depthAttachment.setStencilReadOnly(true);
            passDescriptor.setDepthStencilAttachment(depthAttachment);
        }

        WGPURenderPass renderPass = nextRenderPass();
        context.frameEncoder().beginRenderPass(passDescriptor, renderPass.nativePass());
        renderPass.begin(renderTargetHeight, renderTarget);
        renderPassCount++;
        return renderPass;
    }

    void beginFrame() {
        ensurePassesEnded();
        renderPassCount = 0;
    }

    void ensureNoOpenPass() {
        ensurePreviousPassEnded();
    }

    void ensurePassesEnded() {
        for (int i = 0; i < renderPassCount; i++) {
            if (!renderPasses[i].isEnded()) {
                throw new FdxException("WGPU render pass must be ended before ending the frame");
            }
        }
    }

    void dispose() {
        for (int i = 0; i < renderPasses.length; i++) {
            if (renderPasses[i] != null) {
                renderPasses[i].dispose();
                renderPasses[i] = null;
            }
        }
        renderPassCount = 0;
    }

    private void ensurePreviousPassEnded() {
        if (renderPassCount > 0 && !renderPasses[renderPassCount - 1].isEnded()) {
            throw new FdxException("Previous WGPU render pass must be ended before beginning another pass");
        }
    }

    private WGPURenderPass nextRenderPass() {
        if (renderPassCount == renderPasses.length) {
            WGPURenderPass[] grown = new WGPURenderPass[renderPasses.length * 2];
            System.arraycopy(renderPasses, 0, grown, 0, renderPasses.length);
            renderPasses = grown;
        }
        WGPURenderPass renderPass = renderPasses[renderPassCount];
        if (renderPass == null) {
            renderPass = new WGPURenderPass(context);
            renderPasses[renderPassCount] = renderPass;
        }
        return renderPass;
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) context.frameEncoder();
    }
}
