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

final class WGPUCommandEncoderHandle implements CommandEncoder {
    private final WGPUContext context;

    WGPUCommandEncoderHandle(WGPUContext context) {
        this.context = context;
    }

    @Override
    public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPassDescriptor cannot be null");
        }
        if (!context.isFrameStarted()) {
            throw new FdxException("Cannot begin render pass outside a frame");
        }
        WGPUTextureViewHandle attachment = descriptor.colorAttachment().as();

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
            depthAttachment.setView(context.depthTextureView());
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

        WGPURenderPassEncoder passEncoder = new WGPURenderPassEncoder();
        context.frameEncoder().beginRenderPass(passDescriptor, passEncoder);
        return new WGPURenderPass(context, passEncoder);
    }

    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) context.frameEncoder();
    }
}
