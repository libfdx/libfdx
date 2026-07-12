package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBuffer;
import com.github.xpenatan.webgpu.WGPUBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPUAddressMode;
import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import com.github.xpenatan.webgpu.WGPUBindGroupLayoutDescriptor;
import com.github.xpenatan.webgpu.WGPUBindGroupLayoutEntry;
import com.github.xpenatan.webgpu.WGPUBlendFactor;
import com.github.xpenatan.webgpu.WGPUBlendOperation;
import com.github.xpenatan.webgpu.WGPUBlendState;
import com.github.xpenatan.webgpu.WGPUBufferBindingLayout;
import com.github.xpenatan.webgpu.WGPUBufferBindingType;
import com.github.xpenatan.webgpu.WGPUColorTargetState;
import com.github.xpenatan.webgpu.WGPUColorWriteMask;
import com.github.xpenatan.webgpu.WGPUCompareFunction;
import com.github.xpenatan.webgpu.WGPUCullMode;
import com.github.xpenatan.webgpu.WGPUDepthStencilState;
import com.github.xpenatan.webgpu.WGPUExtent3D;
import com.github.xpenatan.webgpu.WGPUFilterMode;
import com.github.xpenatan.webgpu.WGPUFragmentState;
import com.github.xpenatan.webgpu.WGPUFrontFace;
import com.github.xpenatan.webgpu.WGPUIndexFormat;
import com.github.xpenatan.webgpu.WGPUMipmapFilterMode;
import com.github.xpenatan.webgpu.WGPUOptionalBool;
import com.github.xpenatan.webgpu.WGPUPipelineLayout;
import com.github.xpenatan.webgpu.WGPUPipelineLayoutDescriptor;
import com.github.xpenatan.webgpu.WGPUPrimitiveTopology;
import com.github.xpenatan.webgpu.WGPURenderPipeline;
import com.github.xpenatan.webgpu.WGPURenderPipelineDescriptor;
import com.github.xpenatan.webgpu.WGPUSampler;
import com.github.xpenatan.webgpu.WGPUSamplerBindingLayout;
import com.github.xpenatan.webgpu.WGPUSamplerBindingType;
import com.github.xpenatan.webgpu.WGPUSamplerDescriptor;
import com.github.xpenatan.webgpu.WGPUSType;
import com.github.xpenatan.webgpu.WGPUShaderModule;
import com.github.xpenatan.webgpu.WGPUShaderModuleDescriptor;
import com.github.xpenatan.webgpu.WGPUShaderSourceWGSL;
import com.github.xpenatan.webgpu.WGPUShaderStage;
import com.github.xpenatan.webgpu.WGPUTexelCopyBufferLayout;
import com.github.xpenatan.webgpu.WGPUTexelCopyTextureInfo;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureAspect;
import com.github.xpenatan.webgpu.WGPUTextureBindingLayout;
import com.github.xpenatan.webgpu.WGPUTextureDescriptor;
import com.github.xpenatan.webgpu.WGPUTextureDimension;
import com.github.xpenatan.webgpu.WGPUTextureSampleType;
import com.github.xpenatan.webgpu.WGPUTextureUsage;
import com.github.xpenatan.webgpu.WGPUTextureView;
import com.github.xpenatan.webgpu.WGPUTextureViewDescriptor;
import com.github.xpenatan.webgpu.WGPUTextureViewDimension;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupLayout;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupLayoutEntry;
import com.github.xpenatan.webgpu.WGPUVectorColorTargetState;
import com.github.xpenatan.webgpu.WGPUVectorConstantEntry;
import com.github.xpenatan.webgpu.WGPUVectorTextureFormat;
import com.github.xpenatan.webgpu.WGPUVectorVertexAttribute;
import com.github.xpenatan.webgpu.WGPUVectorVertexBufferLayout;
import com.github.xpenatan.webgpu.WGPUVertexAttribute;
import com.github.xpenatan.webgpu.WGPUVertexBufferLayout;
import com.github.xpenatan.webgpu.WGPUVertexFormat;
import com.github.xpenatan.webgpu.WGPUVertexStepMode;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;

import java.nio.ByteBuffer;

/**
 * Represents a WGPU graphics device.
 *
 * @author xpenatan
 */
final class WGPUGraphicsDevice implements GraphicsDevice {
    private static final int COPY_BUFFER_WRITE_ALIGNMENT = 4;
    private static final int COPY_BYTES_PER_ROW_ALIGNMENT = 256;
    private final WGPUContext context;
    private ByteBuffer paddedBufferUpload;
    private ByteBuffer paddedTextureUpload;

    WGPUGraphicsDevice(WGPUContext context) {
        this.context = context;
    }

    /**
     * Creates a buffer.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public Buffer createBuffer(BufferDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("BufferDescriptor cannot be null");
        }
        context.requireDeviceUsable("create a buffer");
        if (descriptor.usage() != BufferUsage.VERTEX && descriptor.usage() != BufferUsage.INDEX) {
            throw new FdxException("WGPU currently supports vertex and index buffers only");
        }
        int nativeSize = align(descriptor.size(), COPY_BUFFER_WRITE_ALIGNMENT);
        WGPUBuffer buffer = createNativeBuffer(descriptor.label(), nativeSize, descriptor.usage());
        WGPUBufferAllocation allocation = new WGPUBufferAllocation(context.resourceDomain(), buffer);
        return new WGPUBufferHandle(context.resourceDomain(), allocation, descriptor.label(), nativeSize,
                descriptor.usage());
    }

    /**
     * Runs the write buffer step.
     *
     * @param buffer the buffer
     * @param data the data
     */
    @Override
    public void writeBuffer(Buffer buffer, ByteBuffer data) {
        if (data == null) {
            throw new FdxException("Buffer data cannot be null");
        }
        context.requireDeviceUsable("write a buffer");
        WGPUBufferHandle wgpuBuffer = WGPUResources.requireBuffer(buffer, context.resourceDomain(), "Buffer");
        int byteCount = data.remaining();
        int uploadByteCount = align(byteCount, COPY_BUFFER_WRITE_ALIGNMENT);
        if (uploadByteCount > wgpuBuffer.size()) {
            throw new FdxException("Buffer data is larger than the destination buffer");
        }
        if (wgpuBuffer.allocation().hasRecordingReferences()) {
            WGPUBuffer newBuffer = createNativeBuffer(wgpuBuffer.label(), wgpuBuffer.size(), wgpuBuffer.usage());
            wgpuBuffer.replaceAllocation(new WGPUBufferAllocation(context.resourceDomain(), newBuffer));
        }
        ByteBuffer uploadData = bufferUploadData(data, byteCount, uploadByteCount);
        context.nativeQueue().writeBuffer(wgpuBuffer.nativeBuffer(), 0, uploadData, uploadByteCount);
    }

    private ByteBuffer bufferUploadData(ByteBuffer data, int byteCount, int uploadByteCount) {
        if (byteCount == uploadByteCount) {
            return data;
        }
        paddedBufferUpload = ensureBuffer(paddedBufferUpload, uploadByteCount);
        paddedBufferUpload.clear();
        int sourcePosition = data.position();
        try {
            data.limit(sourcePosition + byteCount);
            paddedBufferUpload.put(data);
        }
        finally {
            data.position(sourcePosition);
        }
        while (paddedBufferUpload.position() < uploadByteCount) {
            paddedBufferUpload.put((byte)0);
        }
        paddedBufferUpload.flip();
        return paddedBufferUpload;
    }

    private WGPUBuffer createNativeBuffer(String label, int size, BufferUsage usage) {
        WGPUBufferDescriptor bufferDescriptor = WGPUBufferDescriptor.obtain();
        bufferDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        bufferDescriptor.setLabel(label);
        bufferDescriptor.setSize(size);
        WGPUBufferUsage nativeUsage = usage == BufferUsage.INDEX
                ? WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index)
                : WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Vertex);
        bufferDescriptor.setUsage(nativeUsage);
        bufferDescriptor.setMappedAtCreation(false);
        WGPUBuffer buffer = new WGPUBuffer();
        try {
            context.nativeDevice().createBuffer(bufferDescriptor, buffer);
            buffer.native_setAddress(buffer.native_getAddressLong());
            return buffer;
        }
        catch (RuntimeException | Error failure) {
            rollbackBuffer(buffer, failure);
            throw failure;
        }
    }

    /**
     * Creates a texture.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public Texture createTexture(TextureDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("TextureDescriptor cannot be null");
        }
        context.requireDeviceUsable("create a texture");
        if (!descriptor.usage().sampled() && !descriptor.usage().renderAttachment()) {
            throw new FdxException("WGPU texture usage must allow sampling or render attachment binding");
        }
        int mipLevelCount = mipLevelCount(descriptor);
        WGPUTextureAllocation allocation = createTextureAllocation(descriptor.label(), descriptor.width(),
                descriptor.height(), mipLevelCount, descriptor.format(), descriptor.usage(), descriptor.filter(),
                descriptor.wrapS(), descriptor.wrapT());
        return new WGPUTextureHandle(context.resourceDomain(), allocation, descriptor.label(), descriptor.width(),
                descriptor.height(), mipLevelCount, descriptor.format(), descriptor.usage(), descriptor.filter(),
                descriptor.wrapS(), descriptor.wrapT());
    }

    private WGPUTextureAllocation createTextureAllocation(String label, int width, int height, int mipLevelCount,
            TextureFormat format, TextureUsage usage, TextureFilter filter, TextureWrap wrapS, TextureWrap wrapT) {
        WGPUTextureUsage nativeUsage = WGPUTextureUsage.CopyDst;
        if (usage.sampled()) {
            nativeUsage = nativeUsage.or(WGPUTextureUsage.TextureBinding);
        }
        if (usage.renderAttachment()) {
            nativeUsage = nativeUsage.or(WGPUTextureUsage.RenderAttachment);
        }
        WGPUTextureUsage viewUsage = usage.sampled()
                ? WGPUTextureUsage.TextureBinding
                : WGPUTextureUsage.RenderAttachment;
        if (usage.sampled() && usage.renderAttachment()) {
            viewUsage = viewUsage.or(WGPUTextureUsage.RenderAttachment);
        }
        WGPUTextureDescriptor textureDescriptor = WGPUTextureDescriptor.obtain();
        textureDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        textureDescriptor.setLabel(label);
        textureDescriptor.setUsage(nativeUsage);
        textureDescriptor.setDimension(WGPUTextureDimension._2D);
        textureDescriptor.getSize().setWidth(width);
        textureDescriptor.getSize().setHeight(height);
        textureDescriptor.getSize().setDepthOrArrayLayers(1);
        textureDescriptor.setFormat(WGPUTextureFormats.toNative(format));
        textureDescriptor.setMipLevelCount(mipLevelCount);
        textureDescriptor.setSampleCount(1);
        textureDescriptor.setViewFormats(WGPUVectorTextureFormat.NULL);

        WGPUTexture texture = new WGPUTexture();
        WGPUTextureView view = null;
        WGPUSampler sampler = null;
        try {
            context.nativeDevice().createTexture(textureDescriptor, texture);

            WGPUTextureViewDescriptor viewDescriptor = WGPUTextureViewDescriptor.obtain();
            viewDescriptor.setNextInChain(WGPUChainedStruct.NULL);
            viewDescriptor.setLabel(label + " view");
            viewDescriptor.setFormat(WGPUTextureFormats.toNative(format));
            viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
            viewDescriptor.setBaseMipLevel(0);
            viewDescriptor.setMipLevelCount(mipLevelCount);
            viewDescriptor.setBaseArrayLayer(0);
            viewDescriptor.setArrayLayerCount(1);
            viewDescriptor.setAspect(WGPUTextureAspect.All);
            viewDescriptor.setUsage(viewUsage);
            view = new WGPUTextureView();
            texture.createView(viewDescriptor, view);

            WGPUSamplerDescriptor samplerDescriptor = WGPUSamplerDescriptor.obtain();
            samplerDescriptor.setNextInChain(WGPUChainedStruct.NULL);
            samplerDescriptor.setLabel(label + " sampler");
            samplerDescriptor.setAddressModeU(toNative(wrapS));
            samplerDescriptor.setAddressModeV(toNative(wrapT));
            samplerDescriptor.setAddressModeW(WGPUAddressMode.ClampToEdge);
            samplerDescriptor.setMagFilter(toNative(filter));
            samplerDescriptor.setMinFilter(toNative(filter));
            samplerDescriptor.setMipmapFilter(mipLevelCount > 1
                    ? toNativeMipmap(filter)
                    : WGPUMipmapFilterMode.Nearest);
            samplerDescriptor.setLodMinClamp(0.0f);
            samplerDescriptor.setLodMaxClamp(mipLevelCount - 1.0f);
            samplerDescriptor.setCompare(WGPUCompareFunction.Undefined);
            samplerDescriptor.setMaxAnisotropy(1);
            sampler = new WGPUSampler();
            context.nativeDevice().createSampler(samplerDescriptor, sampler);

            return new WGPUTextureAllocation(context.resourceDomain(), texture, view, sampler);
        }
        catch (RuntimeException | Error failure) {
            rollbackTexture(texture, view, sampler, failure);
            throw failure;
        }
    }

    private int mipLevelCount(TextureDescriptor descriptor) {
        return 1;
    }

    private WGPUAddressMode toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return WGPUAddressMode.Repeat;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return WGPUAddressMode.MirrorRepeat;
        }
        return WGPUAddressMode.ClampToEdge;
    }

    private WGPUFilterMode toNative(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? WGPUFilterMode.Nearest : WGPUFilterMode.Linear;
    }

    private WGPUMipmapFilterMode toNativeMipmap(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? WGPUMipmapFilterMode.Nearest : WGPUMipmapFilterMode.Linear;
    }

    /**
     * Runs the write texture step.
     *
     * @param texture the texture
     * @param data the data
     */
    @Override
    public void writeTexture(Texture texture, ByteBuffer data) {
        if (data == null) {
            throw new FdxException("Texture data cannot be null");
        }
        context.requireDeviceUsable("write a texture");
        WGPUTextureHandle wgpuTexture = WGPUResources.requireTexture(texture, context.resourceDomain(), "Texture");
        int byteCount = wgpuTexture.width() * wgpuTexture.height() * 4;
        if (data.remaining() < byteCount) {
            throw new FdxException("Texture data is smaller than the destination texture");
        }
        if (wgpuTexture.allocation().hasRecordingReferences()) {
            WGPUTextureAllocation replacement = createTextureAllocation(wgpuTexture.label(), wgpuTexture.width(),
                    wgpuTexture.height(), wgpuTexture.mipLevelCount(), wgpuTexture.format(), wgpuTexture.usage(),
                    wgpuTexture.filter(), wgpuTexture.wrapS(), wgpuTexture.wrapT());
            wgpuTexture.replaceAllocation(replacement);
        }
        writeTextureLevel(wgpuTexture, 0, wgpuTexture.width(), wgpuTexture.height(), data, byteCount);
        writeMipLevels(wgpuTexture, data);
    }

    private void writeTextureLevel(WGPUTextureHandle texture, int mipLevel, int width, int height, ByteBuffer data,
            int byteCount) {
        int rowBytes = width * 4;
        int bytesPerRow = align(rowBytes, COPY_BYTES_PER_ROW_ALIGNMENT);
        ByteBuffer uploadData = data;
        int uploadByteCount = byteCount;
        if (bytesPerRow != rowBytes) {
            uploadData = packTextureRows(data, height, rowBytes, bytesPerRow);
            uploadByteCount = bytesPerRow * height;
        }

        WGPUTexelCopyTextureInfo destination = WGPUTexelCopyTextureInfo.obtain();
        destination.setTexture(texture.nativeTexture());
        destination.setMipLevel(mipLevel);
        destination.getOrigin().setX(0);
        destination.getOrigin().setY(0);
        destination.getOrigin().setZ(0);
        destination.setAspect(WGPUTextureAspect.All);

        WGPUTexelCopyBufferLayout layout = WGPUTexelCopyBufferLayout.obtain();
        layout.setOffset(0);
        layout.setBytesPerRow(bytesPerRow);
        layout.setRowsPerImage(height);

        WGPUExtent3D size = WGPUExtent3D.obtain();
        size.setWidth(width);
        size.setHeight(height);
        size.setDepthOrArrayLayers(1);
        context.nativeQueue().writeTexture(destination, uploadData, uploadByteCount, layout, size);
    }

    private ByteBuffer packTextureRows(ByteBuffer source, int height, int rowBytes, int bytesPerRow) {
        int byteCount = bytesPerRow * height;
        paddedTextureUpload = ensureBuffer(paddedTextureUpload, byteCount);
        paddedTextureUpload.clear();
        int sourcePosition = source.position();
        for (int y = 0; y < height; y++) {
            int sourceOffset = sourcePosition + y * rowBytes;
            for (int x = 0; x < rowBytes; x++) {
                paddedTextureUpload.put(source.get(sourceOffset + x));
            }
            for (int padding = rowBytes; padding < bytesPerRow; padding++) {
                paddedTextureUpload.put((byte)0);
            }
        }
        paddedTextureUpload.flip();
        return paddedTextureUpload;
    }

    private ByteBuffer ensureBuffer(ByteBuffer buffer, int byteCount) {
        if (buffer != null && buffer.capacity() >= byteCount) {
            return buffer;
        }
        return ByteBuffer.allocateDirect(byteCount);
    }

    private int align(int value, int alignment) {
        return ((value + alignment - 1) / alignment) * alignment;
    }

    private void writeMipLevels(WGPUTextureHandle texture, ByteBuffer basePixels) {
        if (texture.mipLevelCount() <= 1 || texture.format() != TextureFormat.RGBA8_UNORM) {
            return;
        }
        ByteBuffer previous = basePixels.duplicate();
        int previousWidth = texture.width();
        int previousHeight = texture.height();
        for (int level = 1; level < texture.mipLevelCount(); level++) {
            int mipWidth = Math.max(1, previousWidth / 2);
            int mipHeight = Math.max(1, previousHeight / 2);
            ByteBuffer mipPixels = generateMipLevel(previous, previousWidth, previousHeight, mipWidth, mipHeight);
            writeTextureLevel(texture, level, mipWidth, mipHeight, mipPixels, mipWidth * mipHeight * 4);
            previous = mipPixels;
            previousWidth = mipWidth;
            previousHeight = mipHeight;
        }
    }

    private ByteBuffer generateMipLevel(ByteBuffer source, int sourceWidth, int sourceHeight, int mipWidth,
            int mipHeight) {
        ByteBuffer mip = ByteBuffer.allocateDirect(mipWidth * mipHeight * 4);
        for (int y = 0; y < mipHeight; y++) {
            for (int x = 0; x < mipWidth; x++) {
                putAveragePixel(source, sourceWidth, sourceHeight, x * 2, y * 2, mip);
            }
        }
        mip.flip();
        return mip;
    }

    private void putAveragePixel(ByteBuffer source, int sourceWidth, int sourceHeight, int sourceX, int sourceY,
            ByteBuffer destination) {
        int maxX = Math.min(sourceX + 1, sourceWidth - 1);
        int maxY = Math.min(sourceY + 1, sourceHeight - 1);
        int red = 0;
        int green = 0;
        int blue = 0;
        int alpha = 0;
        int count = 0;
        for (int y = sourceY; y <= maxY; y++) {
            for (int x = sourceX; x <= maxX; x++) {
                int index = (y * sourceWidth + x) * 4;
                red += source.get(index) & 0xff;
                green += source.get(index + 1) & 0xff;
                blue += source.get(index + 2) & 0xff;
                alpha += source.get(index + 3) & 0xff;
                count++;
            }
        }
        destination.put((byte) (red / count));
        destination.put((byte) (green / count));
        destination.put((byte) (blue / count));
        destination.put((byte) (alpha / count));
    }

    /**
     * Creates a shader module.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("ShaderModuleDescriptor cannot be null");
        }
        context.requireDeviceUsable("create a shader module");
        if (!descriptor.hasSource(ShaderLanguage.WGSL)) {
            throw new FdxException("WGPU currently supports WGSL shader modules only");
        }

        WGPUShaderModuleDescriptor shaderDescriptor = WGPUShaderModuleDescriptor.obtain();
        shaderDescriptor.setLabel(descriptor.label());

        WGPUShaderSourceWGSL source = WGPUShaderSourceWGSL.obtain();
        source.getChain().setNext(WGPUChainedStruct.NULL);
        source.getChain().setSType(WGPUSType.ShaderSourceWGSL);
        source.setCode(descriptor.source());
        shaderDescriptor.setNextInChain(source.getChain());

        WGPUShaderModule shaderModule = new WGPUShaderModule();
        try {
            context.nativeDevice().createShaderModule(shaderDescriptor, shaderModule);
            return new WGPUShaderModuleHandle(context.resourceDomain(), shaderModule, ShaderLanguage.WGSL);
        }
        catch (RuntimeException | Error failure) {
            rollbackShaderModule(shaderModule, failure);
            throw failure;
        }
    }

    /**
     * Creates a render pipeline.
     *
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPipelineDescriptor cannot be null");
        }
        context.requireDeviceUsable("create a render pipeline");
        WGPUShaderModuleHandle shaderModule = WGPUResources.requireShaderModule(descriptor.shaderModule(),
                context.resourceDomain(), "Render pipeline shader module");
        WGPUColorTargetState colorTarget = WGPUColorTargetState.obtain();
        colorTarget.setNextInChain(WGPUChainedStruct.NULL);
        colorTarget.setFormat(WGPUTextureFormats.toNative(descriptor.colorFormat()));
        colorTarget.setBlend(createAlphaBlendState());
        colorTarget.setWriteMask(WGPUColorWriteMask.All);

        WGPUVectorColorTargetState colorTargets = WGPUVectorColorTargetState.obtain();
        colorTargets.push_back(colorTarget);

        WGPUFragmentState fragmentState = WGPUFragmentState.obtain();
        fragmentState.setNextInChain(WGPUChainedStruct.NULL);
        fragmentState.setModule(shaderModule.nativeModule());
        fragmentState.setEntryPoint(descriptor.fragmentEntryPoint());
        fragmentState.setConstants(WGPUVectorConstantEntry.NULL);
        fragmentState.setTargets(colorTargets);

        WGPUBindGroupLayout textureBindGroupLayout = null;
        WGPUBindGroupLayout uniformBindGroupLayout = null;
        WGPUPipelineLayout pipelineLayout = null;
        WGPURenderPipeline pipeline = null;
        try {
            textureBindGroupLayout = createTextureBindGroupLayout(descriptor.sampledTextureCount(),
                    descriptor.label());
            uniformBindGroupLayout = usesPbrUniformBlock(descriptor)
                    ? createUniformBindGroupLayout(descriptor.label())
                    : null;
            WGPUVectorBindGroupLayout bindGroupLayouts = WGPUVectorBindGroupLayout.obtain();
            if (textureBindGroupLayout != null) {
                bindGroupLayouts.push_back(textureBindGroupLayout);
            }
            int uniformBindGroupIndex = textureBindGroupLayout != null ? 1 : 0;
            if (uniformBindGroupLayout != null) {
                bindGroupLayouts.push_back(uniformBindGroupLayout);
            }

            WGPUPipelineLayoutDescriptor layoutDescriptor = WGPUPipelineLayoutDescriptor.obtain();
            layoutDescriptor.setNextInChain(WGPUChainedStruct.NULL);
            layoutDescriptor.setLabel(descriptor.label() + " layout");
            layoutDescriptor.setBindGroupLayouts(bindGroupLayouts);

            pipelineLayout = new WGPUPipelineLayout();
            context.nativeDevice().createPipelineLayout(layoutDescriptor, pipelineLayout);

            WGPURenderPipelineDescriptor pipelineDescriptor = WGPURenderPipelineDescriptor.obtain();
            pipelineDescriptor.setNextInChain(WGPUChainedStruct.NULL);
            pipelineDescriptor.setLabel(descriptor.label());
            pipelineDescriptor.getVertex().setModule(shaderModule.nativeModule());
            pipelineDescriptor.getVertex().setEntryPoint(descriptor.vertexEntryPoint());
            pipelineDescriptor.getVertex().setConstants(WGPUVectorConstantEntry.NULL);
            pipelineDescriptor.getVertex().setBuffers(createVertexBuffers(descriptor.vertexLayouts()));
            pipelineDescriptor.getPrimitive().setTopology(toNative(descriptor.primitiveTopology()));
            pipelineDescriptor.getPrimitive().setStripIndexFormat(WGPUIndexFormat.Undefined);
            pipelineDescriptor.getPrimitive().setFrontFace(WGPUFrontFace.CCW);
            pipelineDescriptor.getPrimitive().setCullMode(WGPUCullMode.None);
            pipelineDescriptor.setFragment(fragmentState);
            pipelineDescriptor.setDepthStencil(createDepthStencilState(descriptor));
            pipelineDescriptor.getMultisample().setCount(1);
            pipelineDescriptor.getMultisample().setMask(-1);
            pipelineDescriptor.getMultisample().setAlphaToCoverageEnabled(false);
            pipelineDescriptor.setLayout(pipelineLayout);

            pipeline = new WGPURenderPipeline();
            context.nativeDevice().createRenderPipeline(pipelineDescriptor, pipeline);
            return new WGPURenderPipelineHandle(context.resourceDomain(), pipeline, pipelineLayout,
                    textureBindGroupLayout, uniformBindGroupLayout, descriptor.sampledTextureCount(),
                    uniformBindGroupIndex, descriptor.vertexLayouts().length);
        }
        catch (RuntimeException | Error failure) {
            rollbackPipeline(pipeline, pipelineLayout, textureBindGroupLayout, uniformBindGroupLayout, failure);
            throw failure;
        }
    }

    private WGPUBindGroupLayout createTextureBindGroupLayout(int sampledTextureCount, String label) {
        if (sampledTextureCount <= 0) {
            return null;
        }
        WGPUVectorBindGroupLayoutEntry entries = WGPUVectorBindGroupLayoutEntry.obtain();

        for (int slot = 0; slot < sampledTextureCount; slot++) {
            WGPUBindGroupLayoutEntry textureEntry = WGPUBindGroupLayoutEntry.obtain();
            textureEntry.setNextInChain(WGPUChainedStruct.NULL);
            textureEntry.setBinding(slot * 2);
            textureEntry.setVisibility(WGPUShaderStage.Fragment);
            WGPUTextureBindingLayout textureLayout = WGPUTextureBindingLayout.obtain();
            textureLayout.setNextInChain(WGPUChainedStruct.NULL);
            textureLayout.setSampleType(WGPUTextureSampleType.Float);
            textureLayout.setViewDimension(WGPUTextureViewDimension._2D);
            textureLayout.setMultisampled(0);
            textureEntry.setTexture(textureLayout);
            entries.push_back(textureEntry);

            WGPUBindGroupLayoutEntry samplerEntry = WGPUBindGroupLayoutEntry.obtain();
            samplerEntry.setNextInChain(WGPUChainedStruct.NULL);
            samplerEntry.setBinding(slot * 2 + 1);
            samplerEntry.setVisibility(WGPUShaderStage.Fragment);
            WGPUSamplerBindingLayout samplerLayout = WGPUSamplerBindingLayout.obtain();
            samplerLayout.setNextInChain(WGPUChainedStruct.NULL);
            samplerLayout.setType(WGPUSamplerBindingType.Filtering);
            samplerEntry.setSampler(samplerLayout);
            entries.push_back(samplerEntry);
        }

        WGPUBindGroupLayoutDescriptor descriptor = WGPUBindGroupLayoutDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel(label + " texture bind group layout");
        descriptor.setEntries(entries);
        WGPUBindGroupLayout bindGroupLayout = new WGPUBindGroupLayout();
        try {
            context.nativeDevice().createBindGroupLayout(descriptor, bindGroupLayout);
            return bindGroupLayout;
        }
        catch (RuntimeException | Error failure) {
            rollbackBindGroupLayout(bindGroupLayout, failure);
            throw failure;
        }
    }

    private WGPUBindGroupLayout createUniformBindGroupLayout(String label) {
        WGPUVectorBindGroupLayoutEntry entries = WGPUVectorBindGroupLayoutEntry.obtain();

        WGPUBindGroupLayoutEntry uniformEntry = WGPUBindGroupLayoutEntry.obtain();
        uniformEntry.setNextInChain(WGPUChainedStruct.NULL);
        uniformEntry.setBinding(0);
        uniformEntry.setVisibility(WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment));
        WGPUBufferBindingLayout uniformLayout = WGPUBufferBindingLayout.obtain();
        uniformLayout.setNextInChain(WGPUChainedStruct.NULL);
        uniformLayout.setType(WGPUBufferBindingType.Uniform);
        uniformLayout.setHasDynamicOffset(1);
        uniformLayout.setMinBindingSize(WGPURenderPass.PBR_UNIFORM_BYTE_COUNT);
        uniformEntry.setBuffer(uniformLayout);
        entries.push_back(uniformEntry);

        WGPUBindGroupLayoutDescriptor descriptor = WGPUBindGroupLayoutDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel(label + " uniform bind group layout");
        descriptor.setEntries(entries);
        WGPUBindGroupLayout bindGroupLayout = new WGPUBindGroupLayout();
        try {
            context.nativeDevice().createBindGroupLayout(descriptor, bindGroupLayout);
            return bindGroupLayout;
        }
        catch (RuntimeException | Error failure) {
            rollbackBindGroupLayout(bindGroupLayout, failure);
            throw failure;
        }
    }

    private WGPUDepthStencilState createDepthStencilState(RenderPipelineDescriptor descriptor) {
        if (!descriptor.depthTestEnabled()) {
            return WGPUDepthStencilState.NULL;
        }
        WGPUDepthStencilState depthStencilState = WGPUDepthStencilState.obtain();
        depthStencilState.setNextInChain(WGPUChainedStruct.NULL);
        depthStencilState.setFormat(WGPUContext.DEPTH_FORMAT);
        depthStencilState.setDepthWriteEnabled(descriptor.depthWriteEnabled()
                ? WGPUOptionalBool.True
                : WGPUOptionalBool.False);
        depthStencilState.setDepthCompare(WGPUCompareFunction.LessEqual);
        depthStencilState.setStencilReadMask(0);
        depthStencilState.setStencilWriteMask(0);
        depthStencilState.setDepthBias(0);
        depthStencilState.setDepthBiasSlopeScale(0.0f);
        depthStencilState.setDepthBiasClamp(0.0f);
        return depthStencilState;
    }

    private static boolean usesPbrUniformBlock(RenderPipelineDescriptor descriptor) {
        ShaderBinding[] bindings = descriptor.shaderReflection().bindings();
        for (int i = 0; i < bindings.length; i++) {
            ShaderBinding binding = bindings[i];
            if ((binding.group() == 0 || binding.group() == 1)
                    && binding.binding() == 0
                    && binding.type() == ShaderBindingType.UNIFORM_BUFFER
                    && "uniforms".equals(binding.name())) {
                return true;
            }
        }
        return false;
    }

    private WGPUBlendState createAlphaBlendState() {
        WGPUBlendState blend = WGPUBlendState.obtain();
        blend.getColor().setOperation(WGPUBlendOperation.Add);
        blend.getColor().setSrcFactor(WGPUBlendFactor.SrcAlpha);
        blend.getColor().setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
        blend.getAlpha().setOperation(WGPUBlendOperation.Add);
        blend.getAlpha().setSrcFactor(WGPUBlendFactor.One);
        blend.getAlpha().setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
        return blend;
    }

    private WGPUBlendState createNoBlendState() {
        WGPUBlendState blend = WGPUBlendState.obtain();
        blend.getColor().setOperation(WGPUBlendOperation.Add);
        blend.getColor().setSrcFactor(WGPUBlendFactor.One);
        blend.getColor().setDstFactor(WGPUBlendFactor.Zero);
        blend.getAlpha().setOperation(WGPUBlendOperation.Add);
        blend.getAlpha().setSrcFactor(WGPUBlendFactor.One);
        blend.getAlpha().setDstFactor(WGPUBlendFactor.Zero);
        return blend;
    }

    private WGPUVectorVertexBufferLayout createVertexBuffers(VertexLayout[] layouts) {
        WGPUVectorVertexBufferLayout vertexBuffers = new WGPUVectorVertexBufferLayout();
        vertexBuffers.clear();
        if (layouts == null || layouts.length == 0) {
            return vertexBuffers;
        }

        for (int layoutIndex = 0; layoutIndex < layouts.length; layoutIndex++) {
            VertexLayout layout = layouts[layoutIndex];
            WGPUVectorVertexAttribute nativeAttributes = new WGPUVectorVertexAttribute();
            nativeAttributes.clear();
            for (int i = 0; i < layout.attributeCount(); i++) {
                VertexAttribute attribute = layout.attribute(i);
                WGPUVertexAttribute nativeAttribute = new WGPUVertexAttribute();
                nativeAttribute.setShaderLocation(attribute.location());
                nativeAttribute.setOffset(attribute.offset());
                nativeAttribute.setFormat(toNative(attribute.format()));
                nativeAttributes.push_back(nativeAttribute);
            }

            WGPUVertexBufferLayout nativeLayout = new WGPUVertexBufferLayout();
            nativeLayout.setArrayStride(layout.arrayStride());
            nativeLayout.setStepMode(layout.stepMode() == VertexStepMode.INSTANCE
                    ? WGPUVertexStepMode.Instance
                    : WGPUVertexStepMode.Vertex);
            nativeLayout.setAttributes(nativeAttributes);
            vertexBuffers.push_back(nativeLayout);
        }
        return vertexBuffers;
    }

    private WGPUVertexFormat toNative(VertexFormat format) {
        switch (format) {
            case FLOAT32:
                return WGPUVertexFormat.Float32;
            case FLOAT32X2:
                return WGPUVertexFormat.Float32x2;
            case FLOAT32X3:
                return WGPUVertexFormat.Float32x3;
            case UNORM8X4:
                return WGPUVertexFormat.Unorm8x4;
            case FLOAT32X4:
            default:
                return WGPUVertexFormat.Float32x4;
        }
    }

    private WGPUPrimitiveTopology toNative(PrimitiveTopology primitiveTopology) {
        switch (primitiveTopology) {
            case LINE_LIST:
                return WGPUPrimitiveTopology.LineList;
            case TRIANGLE_STRIP:
                return WGPUPrimitiveTopology.TriangleStrip;
            case TRIANGLE_LIST:
            default:
                return WGPUPrimitiveTopology.TriangleList;
        }
    }

    private void rollbackBuffer(WGPUBuffer buffer, Throwable failure) {
        suppressRollback(failure, () -> new WGPUBufferAllocation(context.resourceDomain(), buffer).retire());
    }

    private void rollbackTexture(WGPUTexture texture, WGPUTextureView view, WGPUSampler sampler,
            Throwable failure) {
        suppressRollback(failure,
                () -> new WGPUTextureAllocation(context.resourceDomain(), texture, view, sampler).retire());
    }

    private void rollbackShaderModule(WGPUShaderModule shaderModule, Throwable failure) {
        suppressRollback(failure, () -> new WGPUShaderModuleHandle(context.resourceDomain(), shaderModule,
                ShaderLanguage.WGSL).dispose());
    }

    private void rollbackPipeline(WGPURenderPipeline pipeline, WGPUPipelineLayout pipelineLayout,
            WGPUBindGroupLayout textureBindGroupLayout, WGPUBindGroupLayout uniformBindGroupLayout,
            Throwable failure) {
        suppressRollback(failure, () -> new WGPURenderPipelineHandle(context.resourceDomain(), pipeline,
                pipelineLayout, textureBindGroupLayout, uniformBindGroupLayout, 0, 0, 0).dispose());
    }

    private void rollbackBindGroupLayout(WGPUBindGroupLayout layout, Throwable failure) {
        suppressRollback(failure, () -> {
            WGPUCleanup cleanup = new WGPUCleanup();
            cleanup.run(() -> {
                if (layout.isValid()) {
                    layout.release();
                }
            });
            cleanup.run(layout::dispose);
            cleanup.throwIfFailed();
        });
    }

    private void suppressRollback(Throwable failure, Runnable rollback) {
        try {
            rollback.run();
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
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
        context.requireDeviceUsable("access the native device");
        return (T) context.nativeDevice();
    }
}
