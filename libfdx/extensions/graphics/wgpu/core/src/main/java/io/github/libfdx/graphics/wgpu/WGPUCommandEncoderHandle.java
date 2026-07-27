package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPUComputePassDescriptor;
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
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.ComputePass;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassColorAttachment;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDepthStencilAttachment;
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
    private WGPUComputePass[] computePasses = new WGPUComputePass[2];
    private int computePassCount;

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
        RenderPassCompatibility declared = descriptor.validate(
                context.device().capabilities());
        RenderPassColorAttachment[] attachments = descriptor.colorAttachments();
        WGPUTextureHandle[] retainedTargets =
                new WGPUTextureHandle[attachments.length * 2 + 1];
        int retainedTargetCount = 0;
        int renderTargetWidth = 0;
        int renderTargetHeight = 0;

        WGPURenderPassDescriptor passDescriptor = WGPURenderPassDescriptor.obtain();
        passDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        passDescriptor.setLabel(descriptor.label());
        passDescriptor.setOcclusionQuerySet(WGPUQuerySet.NULL);

        WGPUVectorRenderPassColorAttachment colorAttachments = WGPUVectorRenderPassColorAttachment.obtain();
        for (int i = 0; i < attachments.length; i++) {
            RenderPassColorAttachment attachment = attachments[i];
            WGPUTextureViewHandle view = WGPUResources.requireTextureView(
                    attachment.view(), context, "Color attachment " + i);
            int width = view.width() > 0 ? view.width() : context.width();
            int height = view.height() > 0 ? view.height() : context.height();
            if (renderTargetWidth == 0) {
                renderTargetWidth = width;
                renderTargetHeight = height;
            } else if (width != renderTargetWidth || height != renderTargetHeight) {
                throw new FdxException("WGPU render-pass attachments have different dimensions");
            }
            retainedTargetCount = retainAttachment(view, retainedTargets,
                    retainedTargetCount, "Color attachment " + i);

            WGPURenderPassColorAttachment nativeAttachment =
                    WGPURenderPassColorAttachment.obtain();
            nativeAttachment.setNextInChain(WGPUChainedStruct.NULL);
            nativeAttachment.setView(view.nativeView());
            nativeAttachment.setResolveTarget(WGPUTextureView.NULL);
            if (attachment.resolveView() != null) {
                WGPUTextureViewHandle resolve = WGPUResources.requireTextureView(
                        attachment.resolveView(), context,
                        "Resolve attachment " + i);
                int resolveWidth = resolve.width() > 0
                        ? resolve.width() : context.width();
                int resolveHeight = resolve.height() > 0
                        ? resolve.height() : context.height();
                if (resolveWidth != width || resolveHeight != height) {
                    throw new FdxException("WGPU resolve attachment dimensions do not match");
                }
                retainedTargetCount = retainAttachment(resolve, retainedTargets,
                        retainedTargetCount, "Resolve attachment " + i);
                nativeAttachment.setResolveTarget(resolve.nativeView());
            }
            nativeAttachment.setLoadOp(attachment.loadOp().isClear()
                    ? WGPULoadOp.Clear : WGPULoadOp.Load);
            nativeAttachment.setStoreOp(attachment.storeOp().isStore()
                    ? WGPUStoreOp.Store : WGPUStoreOp.Discard);
            nativeAttachment.setDepthSlice(-1);
            if (attachment.loadOp().isClear()) {
                nativeAttachment.getClearValue().setR(attachment.loadOp().red());
                nativeAttachment.getClearValue().setG(attachment.loadOp().green());
                nativeAttachment.getClearValue().setB(attachment.loadOp().blue());
                nativeAttachment.getClearValue().setA(attachment.loadOp().alpha());
            }
            colorAttachments.push_back(nativeAttachment);
        }
        passDescriptor.setColorAttachments(colorAttachments);

        RenderPassDepthStencilAttachment explicitDepth =
                descriptor.depthStencilAttachment();
        if (explicitDepth != null) {
            WGPUTextureViewHandle view = WGPUResources.requireTextureView(
                    explicitDepth.view(), context, "Depth/stencil attachment");
            int width = view.width() > 0 ? view.width() : context.width();
            int height = view.height() > 0 ? view.height() : context.height();
            if (renderTargetWidth == 0) {
                renderTargetWidth = width;
                renderTargetHeight = height;
            } else if (width != renderTargetWidth || height != renderTargetHeight) {
                throw new FdxException("WGPU depth/stencil attachment dimensions do not match");
            }
            retainedTargetCount = retainAttachment(view, retainedTargets,
                    retainedTargetCount, "Depth/stencil attachment");
            WGPURenderPassDepthStencilAttachment depthAttachment = WGPURenderPassDepthStencilAttachment.obtain();
            depthAttachment.setView(view.nativeView());
            depthAttachment.setDepthLoadOp(explicitDepth.depthLoadOp().isClear()
                    ? WGPULoadOp.Clear : WGPULoadOp.Load);
            depthAttachment.setDepthStoreOp(explicitDepth.depthStoreOp().isStore()
                    ? WGPUStoreOp.Store : WGPUStoreOp.Discard);
            depthAttachment.setDepthClearValue(descriptor.depthClearValue());
            depthAttachment.setDepthReadOnly(false);
            if (view.format().hasStencil()) {
                depthAttachment.setStencilLoadOp(explicitDepth.stencilLoadOp().isClear()
                        ? WGPULoadOp.Clear : WGPULoadOp.Load);
                depthAttachment.setStencilStoreOp(explicitDepth.stencilStoreOp().isStore()
                        ? WGPUStoreOp.Store : WGPUStoreOp.Discard);
                depthAttachment.setStencilClearValue(0);
                depthAttachment.setStencilReadOnly(false);
            } else {
                depthAttachment.setStencilLoadOp(WGPULoadOp.Undefined);
                depthAttachment.setStencilStoreOp(WGPUStoreOp.Undefined);
                depthAttachment.setStencilClearValue(0);
                depthAttachment.setStencilReadOnly(true);
            }
            passDescriptor.setDepthStencilAttachment(depthAttachment);
        } else if (descriptor.depthEnabled()) {
            WGPURenderPassDepthStencilAttachment depthAttachment =
                    WGPURenderPassDepthStencilAttachment.obtain();
            depthAttachment.setView(context.depthTextureView(renderTargetWidth,
                    renderTargetHeight, declared.targetLayout().sampleCount()));
            depthAttachment.setDepthLoadOp(descriptor.depthClearEnabled()
                    ? WGPULoadOp.Clear : WGPULoadOp.Load);
            depthAttachment.setDepthStoreOp(WGPUStoreOp.Store);
            depthAttachment.setDepthClearValue(descriptor.depthClearValue());
            depthAttachment.setDepthReadOnly(false);
            depthAttachment.setStencilLoadOp(WGPULoadOp.Undefined);
            depthAttachment.setStencilStoreOp(WGPUStoreOp.Undefined);
            depthAttachment.setStencilClearValue(0);
            depthAttachment.setStencilReadOnly(true);
            passDescriptor.setDepthStencilAttachment(depthAttachment);
        }

        if (renderTargetWidth <= 0 || renderTargetHeight <= 0) {
            throw new FdxException("WGPU render-pass dimensions could not be determined");
        }
        if (declared.hasDimensions()
                && (declared.width() != renderTargetWidth
                || declared.height() != renderTargetHeight)) {
            throw new FdxException("WGPU render-pass compatibility dimensions do not match its attachments");
        }
        RenderPassCompatibility compatibility = RenderPassCompatibility.of(
                declared.targetLayout(), renderTargetWidth, renderTargetHeight);

        WGPURenderPass renderPass = nextRenderPass();
        context.frameEncoder().beginRenderPass(passDescriptor, renderPass.nativePass());
        renderPass.begin(compatibility, retainedTargets, retainedTargetCount);
        renderPassCount++;
        return renderPass;
    }

    private int retainAttachment(WGPUTextureViewHandle view,
            WGPUTextureHandle[] retainedTargets, int count, String label) {
        WGPUTextureHandle target = view.textureHandle();
        if (target == null) {
            return count;
        }
        if (!target.usage().renderAttachment()) {
            throw new FdxException(label
                    + " texture was not created for render attachment usage");
        }
        context.markRecordedResource(target.allocation());
        retainedTargets[count] = target;
        return count + 1;
    }

    @Override
    public ComputePass beginComputePass(
            io.github.libfdx.graphics.ComputePassDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("ComputePassDescriptor cannot be null");
        }
        if (!context.isFrameStarted()) {
            throw new FdxException("Cannot begin compute pass outside a frame");
        }
        context.device().capabilities().require(GraphicsFeature.COMPUTE);
        ensurePreviousPassEnded();
        WGPUComputePassDescriptor nativeDescriptor =
                WGPUComputePassDescriptor.obtain();
        nativeDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        nativeDescriptor.setLabel(descriptor.label());
        WGPUComputePass computePass = nextComputePass();
        context.frameEncoder().beginComputePass(nativeDescriptor,
                computePass.nativePass());
        computePass.begin();
        computePassCount++;
        return computePass;
    }

    @Override
    public void copyBufferToBuffer(Buffer source, int sourceOffset,
            Buffer destination, int destinationOffset, int size) {
        if (!context.isFrameStarted()) {
            throw new FdxException("Cannot copy buffers outside a frame");
        }
        ensurePreviousPassEnded();
        WGPUBufferHandle sourceBuffer = WGPUResources.requireBuffer(
                source, context.resourceDomain(), "Copy source buffer");
        WGPUBufferHandle destinationBuffer = WGPUResources.requireBuffer(
                destination, context.resourceDomain(), "Copy destination buffer");
        if (sourceBuffer == destinationBuffer) {
            throw new FdxException("WGPU buffer copy source and destination must be distinct");
        }
        if (sourceBuffer.usage() != BufferUsage.STORAGE) {
            throw new FdxException("WGPU copy source must be a STORAGE buffer");
        }
        if (destinationBuffer.usage() != BufferUsage.STORAGE
                && destinationBuffer.usage() != BufferUsage.READBACK) {
            throw new FdxException("WGPU copy destination must be a STORAGE or READBACK buffer");
        }
        if (sourceOffset < 0 || destinationOffset < 0 || size <= 0
                || sourceOffset > sourceBuffer.size() - size
                || destinationOffset > destinationBuffer.size() - size) {
            throw new FdxException("WGPU buffer copy range is invalid");
        }
        if (((sourceOffset | destinationOffset | size) & 3) != 0) {
            throw new FdxException("WGPU buffer copy offsets and size must be aligned to four bytes");
        }
        context.markRecordedResource(sourceBuffer.allocation());
        context.markRecordedResource(destinationBuffer.allocation());
        context.frameEncoder().copyBufferToBuffer(sourceBuffer.nativeBuffer(),
                sourceOffset, destinationBuffer.nativeBuffer(), destinationOffset, size);
    }

    void beginFrame() {
        ensurePassesEnded();
        renderPassCount = 0;
        computePassCount = 0;
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
        for (int i = 0; i < computePassCount; i++) {
            if (!computePasses[i].isEnded()) {
                throw new FdxException("WGPU compute pass must be ended before ending the frame");
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
        for (int i = 0; i < computePasses.length; i++) {
            if (computePasses[i] != null) {
                computePasses[i].dispose();
                computePasses[i] = null;
            }
        }
        renderPassCount = 0;
        computePassCount = 0;
    }

    private void ensurePreviousPassEnded() {
        if (renderPassCount > 0 && !renderPasses[renderPassCount - 1].isEnded()) {
            throw new FdxException("Previous WGPU render pass must be ended before beginning another pass");
        }
        if (computePassCount > 0 && !computePasses[computePassCount - 1].isEnded()) {
            throw new FdxException("Previous WGPU compute pass must be ended before recording another command");
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

    private WGPUComputePass nextComputePass() {
        if (computePassCount == computePasses.length) {
            WGPUComputePass[] grown = new WGPUComputePass[computePasses.length * 2];
            System.arraycopy(computePasses, 0, grown, 0, computePasses.length);
            computePasses = grown;
        }
        WGPUComputePass computePass = computePasses[computePassCount];
        if (computePass == null) {
            computePass = new WGPUComputePass(context);
            computePasses[computePassCount] = computePass;
        }
        return computePass;
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
