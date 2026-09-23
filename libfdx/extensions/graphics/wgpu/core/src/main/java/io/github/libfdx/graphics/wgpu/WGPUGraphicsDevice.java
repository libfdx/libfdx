package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.graphics.TextureOrigin;
import io.github.libfdx.graphics.TextureMipmapFilter;
import io.github.libfdx.graphics.StencilOperation;
import io.github.libfdx.graphics.internal.TextureUploads;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.BlendState;
import io.github.libfdx.graphics.BlendFactor;
import com.github.xpenatan.webgpu.WGPUStencilFaceState;
import com.github.xpenatan.webgpu.WGPUBlendComponent;
import io.github.libfdx.math.ClipDepthRange;
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
import com.github.xpenatan.webgpu.WGPUComputePipeline;
import com.github.xpenatan.webgpu.WGPUComputePipelineDescriptor;
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
import com.github.xpenatan.webgpu.WGPUStorageTextureAccess;
import com.github.xpenatan.webgpu.WGPUStorageTextureBindingLayout;
import com.github.xpenatan.webgpu.WGPUStencilOperation;
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
import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUPlatformType;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.BlendComponent;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.ColorWriteMask;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.ComputePipelineDescriptor;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.StencilFaceState;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.SamplerDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVisibility;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetEnvironments;
import io.github.libfdx.graphics.shader.target.ShaderTargetSupport;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
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
import io.github.libfdx.graphics.internal.ShaderRenderBindings;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import com.github.xpenatan.jParser.api.NativeObject;
import com.github.xpenatan.webgpu.WGPUDevice;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;

/**
 * Represents a WGPU graphics device.
 *
 * @author xpenatan
 */
final class WGPUGraphicsDevice implements GraphicsDevice {
    private static final int COPY_BUFFER_WRITE_ALIGNMENT = 4;
    private static final int COPY_BYTES_PER_ROW_ALIGNMENT = 256;
    private static final GraphicsCapabilities CAPABILITIES = GraphicsCapabilities.builder()
            .profile(ShaderProfile.PORTABLE_WEBGL2)
            .profile(ShaderProfile.PORTABLE_WEBGPU)
            .profile(ShaderProfile.NATIVE)
            .feature(GraphicsFeature.INDEXED_DRAW)
            .feature(GraphicsFeature.INSTANCED_DRAW)
            .feature(GraphicsFeature.SEPARATE_SAMPLERS)
            .feature(GraphicsFeature.TEXTURE_MIP_LEVELS)
            .feature(GraphicsFeature.TEXTURE_MIN_MAG_FILTERS)
            .feature(GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS)
            .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
            .feature(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS)
            .feature(GraphicsFeature.MULTISAMPLE)
            .feature(GraphicsFeature.RESOLVE_ATTACHMENTS)
            .feature(GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE)
            .feature(GraphicsFeature.STORAGE_BUFFERS)
            .feature(GraphicsFeature.STORAGE_TEXTURES)
            .feature(GraphicsFeature.COMPUTE)
            .feature(GraphicsFeature.ATOMICS)
            .colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB,
                    TextureFormat.RGBA16_FLOAT, TextureFormat.R32_FLOAT)
            .filterableColorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT)
            .blendableColorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT)
            .depthStencilFormats(TextureFormat.DEPTH24_STENCIL8, TextureFormat.DEPTH32_FLOAT)
            // WebGPU clips depth to 0..w.
            .clipDepthRange(ClipDepthRange.ZERO_TO_ONE)
            .renderedTextureOrigin(TextureOrigin.TOP_LEFT)
            .resolveFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB,
                    TextureFormat.RGBA16_FLOAT)
            .sampleCounts(1, 4)
            .limits(GraphicsLimits.builder()
                    .maxBindGroups(4)
                    .maxBindingsPerGroup(1000)
                    .maxUniformBuffersPerStage(12)
                    .maxStorageBuffersPerStage(8)
                    .maxSampledTexturesPerStage(16)
                    .maxSamplersPerStage(16)
                    .maxStorageTexturesPerStage(4)
                    .maxColorAttachments(8)
                    .maxVertexBuffers(8)
                    .maxVertexAttributes(16)
                    .maxComputeWorkgroupsPerDimension(65535)
                    .maxComputeWorkgroupSize(256, 256, 64)
                    .maxComputeInvocationsPerWorkgroup(256)
                    .maxComputeWorkgroupStorageSize(16 * 1024)
                    .maxUniformBufferBindingSize(64L * 1024L)
                    .maxStorageBufferBindingSize(128L * 1024L * 1024L)
                    .build())
            .build();
    private final WGPUContext context;
    private final Object closedDomain = new Object();
    private ByteBuffer paddedBufferUpload;
    private ByteBuffer paddedTextureUpload;

    WGPUGraphicsDevice(WGPUContext context) {
        this.context = context;
    }

    @Override public Object resourceDomain() {
        return context.isDisposed() || context.resourceDomain().isClosed() ? closedDomain : context.resourceDomain();
    }

    @Override public ShaderPreparationCapabilities shaderPreparationCapabilities() {
        WGPUPreparation preparation = context.preparation();
        return preparation == null ? ShaderPreparationCapabilities.UNAVAILABLE : preparation.capabilities();
    }

    @Override public ShaderPreparationOperation prepareRenderPipeline(ShaderPipelineRequest request) {
        context.requireDeviceUsable("prepare a render pipeline");
        if (request == null) throw new FdxException("Shader pipeline request cannot be null");
        WGPUPreparation preparation = context.preparation();
        if (preparation == null) throw new FdxException("Asynchronous WGPU preparation is unavailable for this binding");
        return preparation.submit(request);
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
        if (descriptor.usage() == BufferUsage.STORAGE) {
            capabilities().require(GraphicsFeature.STORAGE_BUFFERS);
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
        if (wgpuBuffer.usage() == BufferUsage.READBACK) {
            throw new FdxException("WGPU readback buffers cannot be written through the graphics queue");
        }
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

    @Override public boolean supportsBufferRangeInitialization() { return true; }

    @Override public void initializeBufferRange(Buffer buffer, int offset, ByteBuffer data) {
        context.requireDeviceUsable("initialize a buffer");
        WGPUBufferHandle target = WGPUResources.requireBuffer(buffer, context.resourceDomain(), "Buffer");
        io.github.libfdx.graphics.BufferInitialization.validate(target, offset, data);
        if (target.allocation().hasRecordingReferences())
            throw new FdxException("Cannot initialize a buffer referenced by recorded commands");
        context.nativeQueue().writeBuffer(target.nativeBuffer(), offset,
                bufferUploadData(data, data.remaining(), data.remaining()), data.remaining());
    }

    @Override
    public ByteBuffer readBuffer(Buffer buffer, int offset, int size) {
        context.requireDeviceUsable("read a buffer");
        WGPUBufferHandle wgpuBuffer = WGPUResources.requireBuffer(
                buffer, context.resourceDomain(), "Readback buffer");
        if (wgpuBuffer.usage() != BufferUsage.READBACK) {
            throw new FdxException("WGPU readBuffer requires a READBACK buffer");
        }
        if (offset < 0 || size <= 0 || offset > wgpuBuffer.size() - size) {
            throw new FdxException("WGPU readback range is invalid");
        }
        if ((offset & (COPY_BUFFER_WRITE_ALIGNMENT - 1)) != 0
                || (size & (COPY_BUFFER_WRITE_ALIGNMENT - 1)) != 0) {
            throw new FdxException("WGPU readback offset and size must be aligned to four bytes");
        }
        if (wgpuBuffer.allocation().hasRecordingReferences()) {
            throw new FdxException("WGPU readback buffer is still referenced by an unsubmitted recording");
        }
        return context.mapReadbackBuffer(wgpuBuffer.nativeBuffer(), offset, size);
    }

    private ByteBuffer bufferUploadData(ByteBuffer data, int byteCount, int uploadByteCount) {
        if (byteCount == uploadByteCount && data.position() == 0 && data.isDirect()) {
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
        WGPUBufferUsage nativeUsage = switch (usage) {
            case VERTEX -> WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Vertex);
            case INDEX -> WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index);
            case UNIFORM -> WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform);
            case STORAGE -> WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.CopySrc).or(WGPUBufferUsage.Storage);
            case READBACK -> WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.MapRead);
        };
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
        descriptor.validate(capabilities());
        if (!descriptor.usage().sampled() && !descriptor.usage().renderAttachment()
                && !descriptor.usage().storage()) {
            throw new FdxException("WGPU texture usage does not expose a GPU binding");
        }
        int mipLevelCount = descriptor.mipLevelCount();
        WGPUTextureAllocation allocation = createTextureAllocation(descriptor.label(), descriptor.width(),
                descriptor.height(), mipLevelCount, descriptor.sampleCount(), descriptor.format(),
                descriptor.usage(), descriptor.minFilter(), descriptor.magFilter(), descriptor.mipmapFilter(), descriptor.wrapS(), descriptor.wrapT());
        return new WGPUTextureHandle(context.resourceDomain(), allocation, descriptor.label(), descriptor.width(),
                descriptor.height(), mipLevelCount, descriptor.sampleCount(), descriptor.format(),
                descriptor.usage(), descriptor.minFilter(), descriptor.magFilter(), descriptor.mipmapFilter(), descriptor.wrapS(), descriptor.wrapT());
    }

    private WGPUTextureAllocation createTextureAllocation(String label, int width, int height, int mipLevelCount,
            int sampleCount, TextureFormat format, TextureUsage usage, TextureFilter filter,
            TextureFilter magFilter, TextureMipmapFilter mipmapFilter, TextureWrap wrapS, TextureWrap wrapT) {
        WGPUTextureUsage nativeUsage = sampleCount == 1 ? WGPUTextureUsage.CopyDst : WGPUTextureUsage.None;
        if (usage.sampled()) {
            nativeUsage = nativeUsage.or(WGPUTextureUsage.TextureBinding);
        }
        if (usage.renderAttachment()) {
            nativeUsage = nativeUsage.or(WGPUTextureUsage.RenderAttachment);
        }
        if (usage.storage()) {
            nativeUsage = nativeUsage.or(WGPUTextureUsage.StorageBinding);
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
        textureDescriptor.setSampleCount(sampleCount);
        textureDescriptor.setViewFormats(WGPUVectorTextureFormat.NULL);

        WGPUTexture texture = new WGPUTexture();
        WGPUTextureView view = null;
        WGPUTextureView storageView = null;
        WGPUSampler sampler = null;
        WGPUTextureView[] attachmentViews = usage.renderAttachment() && mipLevelCount > 1 ? new WGPUTextureView[mipLevelCount] : null;
        try {
            context.nativeDevice().createTexture(textureDescriptor, texture);

            WGPUTextureUsage viewUsage;
            if (usage.sampled()) {
                viewUsage = usage.renderAttachment()
                        ? WGPUTextureUsage.TextureBinding.or(
                                WGPUTextureUsage.RenderAttachment)
                        : WGPUTextureUsage.TextureBinding;
            } else if (usage.renderAttachment()) {
                viewUsage = WGPUTextureUsage.RenderAttachment;
            } else {
                viewUsage = WGPUTextureUsage.StorageBinding;
            }
            view = createTextureView(texture, label + " view", format,
                    usage.sampled() ? mipLevelCount : 1, viewUsage);
            if (attachmentViews != null) for (int level = 0; level < mipLevelCount; level++) {
                attachmentViews[level] = createTextureView(texture, label + " mip " + level, format, level, 1,
                        WGPUTextureUsage.RenderAttachment);
            }
            if (usage.storage()
                    && (usage.sampled() || usage.renderAttachment())) {
                storageView = createTextureView(texture,
                        label + " storage view", format, 1,
                        WGPUTextureUsage.StorageBinding);
            }

            if (usage.sampled() && sampleCount == 1) {
                WGPUSamplerDescriptor samplerDescriptor = WGPUSamplerDescriptor.obtain();
                samplerDescriptor.setNextInChain(WGPUChainedStruct.NULL);
                samplerDescriptor.setLabel(label + " sampler");
                samplerDescriptor.setAddressModeU(toNative(wrapS));
                samplerDescriptor.setAddressModeV(toNative(wrapT));
                samplerDescriptor.setAddressModeW(WGPUAddressMode.ClampToEdge);
                samplerDescriptor.setMagFilter(toNative(magFilter));
                samplerDescriptor.setMinFilter(toNative(filter));
                samplerDescriptor.setMipmapFilter(mipmapFilter == TextureMipmapFilter.LINEAR
                        ? WGPUMipmapFilterMode.Linear : WGPUMipmapFilterMode.Nearest);
                samplerDescriptor.setLodMinClamp(0.0f);
                // A zero clamp forces magnification filtering on some native APIs. Keep positive LODs
                // below the nearest-mip boundary so NONE still selects level zero with the min filter.
                samplerDescriptor.setLodMaxClamp(mipmapFilter == TextureMipmapFilter.NONE
                        ? .499f : Math.max(.499f, mipLevelCount - 1.0f));
                samplerDescriptor.setCompare(WGPUCompareFunction.Undefined);
                samplerDescriptor.setMaxAnisotropy(1);
                sampler = new WGPUSampler();
                context.nativeDevice().createSampler(samplerDescriptor, sampler);
            }

            return new WGPUTextureAllocation(context.resourceDomain(),
                    texture, view, storageView, sampler, attachmentViews);
        }
        catch (RuntimeException | Error failure) {
            rollbackTexture(texture, view, storageView, sampler, attachmentViews, failure);
            throw failure;
        }
    }

    private WGPUTextureView createTextureView(WGPUTexture texture,
            String label, TextureFormat format, int mipLevelCount,
            WGPUTextureUsage usage) {
        return createTextureView(texture, label, format, 0, mipLevelCount, usage);
    }

    private WGPUTextureView createTextureView(WGPUTexture texture,
            String label, TextureFormat format, int baseMipLevel, int mipLevelCount, WGPUTextureUsage usage) {
        WGPUTextureViewDescriptor descriptor =
                WGPUTextureViewDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel(label);
        descriptor.setFormat(WGPUTextureFormats.toNative(format));
        descriptor.setDimension(WGPUTextureViewDimension._2D);
        descriptor.setBaseMipLevel(baseMipLevel);
        descriptor.setMipLevelCount(mipLevelCount);
        descriptor.setBaseArrayLayer(0);
        descriptor.setArrayLayerCount(1);
        descriptor.setAspect(WGPUTextureAspect.All);
        descriptor.setUsage(usage);
        WGPUTextureView result = new WGPUTextureView();
        texture.createView(descriptor, result);
        return result;
    }

    @Override
    public Sampler createSampler(SamplerDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("SamplerDescriptor cannot be null");
        }
        context.requireDeviceUsable("create a sampler");
        capabilities().require(GraphicsFeature.SEPARATE_SAMPLERS);
        WGPUSamplerDescriptor nativeDescriptor = WGPUSamplerDescriptor.obtain();
        nativeDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        nativeDescriptor.setLabel(descriptor.label());
        nativeDescriptor.setAddressModeU(toNative(descriptor.wrapU()));
        nativeDescriptor.setAddressModeV(toNative(descriptor.wrapV()));
        nativeDescriptor.setAddressModeW(toNative(descriptor.wrapW()));
        nativeDescriptor.setMagFilter(toNative(descriptor.magFilter()));
        nativeDescriptor.setMinFilter(toNative(descriptor.minFilter()));
        nativeDescriptor.setMipmapFilter(toNativeMipmap(descriptor.mipmapFilter()));
        nativeDescriptor.setLodMinClamp(0.0f);
        nativeDescriptor.setLodMaxClamp(32.0f);
        nativeDescriptor.setCompare(descriptor.kind() == ShaderSamplerKind.COMPARISON
                ? toNativeCompare(descriptor.compareFunction())
                : WGPUCompareFunction.Undefined);
        nativeDescriptor.setMaxAnisotropy(1);
        WGPUSampler nativeSampler = new WGPUSampler();
        try {
            context.nativeDevice().createSampler(nativeDescriptor, nativeSampler);
            WGPUSamplerAllocation allocation = new WGPUSamplerAllocation(
                    context.resourceDomain(), nativeSampler);
            return new WGPUSamplerHandle(context.resourceDomain(), allocation, descriptor.kind());
        }
        catch (RuntimeException | Error failure) {
            suppressRollback(failure, () -> new WGPUSamplerAllocation(
                    context.resourceDomain(), nativeSampler).retire());
            throw failure;
        }
    }

    private static WGPUAddressMode toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return WGPUAddressMode.Repeat;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return WGPUAddressMode.MirrorRepeat;
        }
        return WGPUAddressMode.ClampToEdge;
    }

    private static WGPUFilterMode toNative(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? WGPUFilterMode.Nearest : WGPUFilterMode.Linear;
    }

    private static WGPUMipmapFilterMode toNativeMipmap(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? WGPUMipmapFilterMode.Nearest : WGPUMipmapFilterMode.Linear;
    }

    private static WGPUCompareFunction toNativeCompare(CompareFunction function) {
        return switch (function) {
            case NEVER -> WGPUCompareFunction.Never;
            case LESS -> WGPUCompareFunction.Less;
            case EQUAL -> WGPUCompareFunction.Equal;
            case LESS_EQUAL -> WGPUCompareFunction.LessEqual;
            case GREATER -> WGPUCompareFunction.Greater;
            case NOT_EQUAL -> WGPUCompareFunction.NotEqual;
            case GREATER_EQUAL -> WGPUCompareFunction.GreaterEqual;
            case ALWAYS -> WGPUCompareFunction.Always;
        };
    }

    /**
     * Runs the write texture step.
     *
     * @param texture the texture
     * @param data the data
     */
    @Override
    public void writeTexture(Texture texture, ByteBuffer data) {
        context.requireDeviceUsable("write a texture");
        WGPUTextureHandle target = WGPUResources.requireTexture(texture, context.resourceDomain(), "Texture");
        TextureUploads.validateSingle(target, data);
        uploadTexture(target, data, null);
    }

    @Override
    public void writeTextureMipLevels(Texture texture, ByteBuffer... levels) {
        context.requireDeviceUsable("write a mip chain");
        WGPUTextureHandle target = WGPUResources.requireTexture(texture, context.resourceDomain(), "Texture");
        TextureUploads.validate(target, levels);
        uploadTexture(target, null, levels);
    }

    private void uploadTexture(WGPUTextureHandle target, ByteBuffer single, ByteBuffer[] levels) {
        paddedRowBytes(target.width(), target.height(), target.format().bytesPerPixel());
        WGPUTextureAllocation original = target.allocation();
        WGPUTextureAllocation destination = original.hasRecordingReferences()
                ? createTextureAllocation(target.label(), target.width(), target.height(), target.mipLevelCount(),
                        target.sampleCount(), target.format(), target.usage(), target.filter(), target.magFilter(),
                        target.mipmapFilter(), target.wrapS(), target.wrapT())
                : original;
        try {
            for (int level = 0; level < target.mipLevelCount(); level++) {
                writeTextureLevel(destination, target.format(), level, target.mipWidth(level), target.mipHeight(level),
                        levels == null ? single : levels[level], TextureUploads.byteCount(target, level));
            }
        } catch (RuntimeException | Error failure) {
            if (destination != original) suppressRollback(failure, destination::retire);
            throw failure;
        }
        // Publish only after every level has been submitted to the native queue.
        if (destination != original) target.replaceAllocation(destination);
    }

    private void writeTextureLevel(WGPUTextureAllocation texture, TextureFormat format, int mipLevel,
            int width, int height, ByteBuffer data, int byteCount) {
        int rowBytes = width * format.bytesPerPixel();
        int bytesPerRow = paddedRowBytes(width, height, format.bytesPerPixel());
        long paddedBytes = (long)bytesPerRow * height;
        if (paddedBytes > Integer.MAX_VALUE) throw new FdxException("Padded texture upload exceeds buffer size limit");
        ByteBuffer uploadData = data;
        int uploadByteCount = byteCount;
        // The native bridge reads the buffer's base address. Normalize active ranges through
        // reusable staging even when the row is aligned; forwarding a nonzero position reads its prefix.
        if (bytesPerRow != rowBytes || data.position() != 0 || !data.isDirect()) {
            uploadData = packTextureRows(data, height, rowBytes, bytesPerRow);
            uploadByteCount = (int)paddedBytes;
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

    private int paddedRowBytes(int width, int height, int texelBytes) {
        long row = (long)width * texelBytes;
        long padded = (row + COPY_BYTES_PER_ROW_ALIGNMENT-1) / COPY_BYTES_PER_ROW_ALIGNMENT * COPY_BYTES_PER_ROW_ALIGNMENT;
        if (padded > Integer.MAX_VALUE || padded*height > Integer.MAX_VALUE) {
            throw new FdxException("Padded texture upload exceeds buffer size limit");
        }
        return (int)padded;
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

    /**
     * Creates a shader module.
     * Native wgpu-native validation failures throw from this call and release the rejected
     * module; they do not poison subsequent context event processing. This call is synchronous.
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
        descriptor = ShaderModuleDescriptors.requireTarget(descriptor,
                shaderTarget(), "WGPU");
        if (descriptor.targetArtifact() != null) {
            shaderTargetSupport().require(descriptor.targetArtifact());
        }
        if (!descriptor.hasSource(ShaderLanguage.WGSL)) {
            throw new FdxException("WGPU currently supports WGSL shader modules only");
        }

        return createNativeShader(context.nativeDevice(), context.resourceDomain(),
                nativeCreationErrors(), descriptor);
    }

    private WGPUCreationErrors nativeCreationErrors() {
        return context.configuration().loaderBackend() == WGPULoaderBackend.WGPU
                && WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web ? context.creationErrors() : null;
    }

    static WGPUShaderModuleHandle createNativeShader(WGPUDevice device, WGPUResourceDomain domain,
            WGPUCreationErrors creationErrors, ShaderModuleDescriptor descriptor) {
        try (NativeInputs inputs = new NativeInputs()) {
            WGPUShaderModuleDescriptor shaderDescriptor = inputs.own(new WGPUShaderModuleDescriptor());
            shaderDescriptor.setLabel(descriptor.label());

            WGPUShaderSourceWGSL source = inputs.own(new WGPUShaderSourceWGSL());
            source.getChain().setNext(WGPUChainedStruct.NULL);
            source.getChain().setSType(WGPUSType.ShaderSourceWGSL);
            source.setCode(descriptor.source());
            shaderDescriptor.setNextInChain(source.getChain());

            WGPUShaderModule shaderModule = new WGPUShaderModule();
            try (WGPUCreationErrors.Scope errors = creationErrors != null ? creationErrors.begin("shader module") : null) {
                device.createShaderModule(shaderDescriptor, shaderModule);
                WGPUCreationErrors.check(errors);
                return new WGPUShaderModuleHandle(domain, shaderModule, ShaderLanguage.WGSL,
                        descriptor.reflection());
            }
            catch (RuntimeException | Error failure) {
                rollbackShaderModule(domain, shaderModule, failure);
                throw failure;
            }
        }
    }

    /**
     * Creates a render pipeline.
     * Native wgpu-native validation failures throw from this call and release partial pipeline
     * resources; later valid creation remains available. This call is synchronous.
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
        return createNativePipeline(context.nativeDevice(), context.resourceDomain(), nativeCreationErrors(),
                descriptor, shaderModule).publish();
    }

    static WGPURenderPipelineHandle createNativePipeline(WGPUDevice device, WGPUResourceDomain domain,
            WGPUCreationErrors creationErrors, RenderPipelineDescriptor descriptor, WGPUShaderModuleHandle shaderModule) {
        try (RenderPipelineInputs inputs = createRenderPipelineInputs(device, domain, creationErrors, descriptor, shaderModule);
                WGPUCreationErrors.Scope errors = creationErrors != null ? creationErrors.begin("render pipeline") : null) {
            WGPURenderPipeline pipeline = new WGPURenderPipeline();
            try {
                device.createRenderPipeline(inputs.descriptor, pipeline);
                WGPUCreationErrors.check(errors);
                return inputs.takePipeline(pipeline);
            } catch (RuntimeException | Error failure) {
                suppressRollback(failure, () -> { if (pipeline.isValid()) pipeline.release(); });
                suppressRollback(failure, pipeline::dispose);
                throw failure;
            }
        }
    }

    /** Owned descriptor graph shared by synchronous creation and browser async callbacks. */
    static final class RenderPipelineInputs implements AutoCloseable {
        final WGPURenderPipelineDescriptor descriptor;
        private final NativeInputs inputs;
        private final WGPUResourceDomain domain;
        private final RenderPipelineDescriptor state;
        private final WGPUPipelineLayout layout;
        private final WGPUBindGroupLayout textures;
        private final WGPUBindGroupLayout[] uniforms;
        private final ShaderRenderBindings bindings;
        private boolean transferred;
        private boolean closed;

        RenderPipelineInputs(NativeInputs inputs, WGPURenderPipelineDescriptor descriptor,
                WGPUResourceDomain domain, RenderPipelineDescriptor state, WGPUPipelineLayout layout,
                WGPUBindGroupLayout textures, WGPUBindGroupLayout[] uniforms, ShaderRenderBindings bindings) {
            this.inputs = inputs;
            this.descriptor = descriptor;
            this.domain = domain;
            this.state = state;
            this.layout = layout;
            this.textures = textures;
            this.uniforms = uniforms;
            this.bindings = bindings;
        }

        WGPURenderPipelineHandle takePipeline(WGPURenderPipeline pipeline) {
            if (closed || transferred) throw new FdxException("WGPU pipeline inputs already consumed");
            WGPURenderPipelineHandle result = new WGPURenderPipelineHandle(domain, pipeline, layout,
                    textures, uniforms, state.sampledTextureCount(), bindings.textureSetIndex(),
                    state.vertexLayouts().length, bindings, state.renderTargetLayout());
            transferred = true;
            return result;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            WGPUCleanup cleanup = new WGPUCleanup();
            cleanup.run(inputs::close);
            if (!transferred) cleanup.run(() -> new WGPURenderPipelineHandle(domain, null, layout,
                    textures, uniforms, 0, -1, 0, bindings, null).dispose());
            cleanup.throwIfFailed();
        }
    }

    static RenderPipelineInputs createRenderPipelineInputs(WGPUDevice device, WGPUResourceDomain domain,
            WGPUCreationErrors creationErrors, RenderPipelineDescriptor descriptor, WGPUShaderModuleHandle shaderModule) {
        descriptor.validate(CAPABILITIES);
        NativeInputs inputs = new NativeInputs();
        try {
            ShaderRenderBindings resourceBindings = ShaderRenderBindings.from(descriptor);
            WGPUVectorColorTargetState colorTargets = inputs.own(new WGPUVectorColorTargetState());
            for (ColorTargetState target : descriptor.colorTargets()) {
                WGPUColorTargetState nativeTarget = inputs.own(new WGPUColorTargetState());
                nativeTarget.setNextInChain(WGPUChainedStruct.NULL);
                nativeTarget.setFormat(WGPUTextureFormats.toNative(target.format()));
                nativeTarget.setBlend(target.blend() != null
                        ? createBlendState(inputs, target.blend()) : WGPUBlendState.NULL);
                synchronized (WGPUColorWriteMask.class) {
                    nativeTarget.setWriteMask(toNativeColorWriteMask(target.writeMask()));
                }
                colorTargets.push_back(nativeTarget);
            }

            WGPUFragmentState fragmentState = inputs.own(new WGPUFragmentState());
            fragmentState.setNextInChain(WGPUChainedStruct.NULL);
            fragmentState.setModule(shaderModule.nativeModule());
            fragmentState.setEntryPoint(descriptor.fragmentEntryPoint());
            fragmentState.setConstants(WGPUVectorConstantEntry.NULL);
            fragmentState.setTargets(colorTargets);

            WGPUBindGroupLayout textureBindGroupLayout = null;
            WGPUBindGroupLayout[] uniformBindGroupLayouts =
                    new WGPUBindGroupLayout[resourceBindings.uniformBufferCount()];
            WGPUPipelineLayout pipelineLayout = null;
            try (WGPUCreationErrors.Scope errors = creationErrors != null ? creationErrors.begin("render pipeline") : null) {
                textureBindGroupLayout = createTextureBindGroupLayout(device, inputs, resourceBindings,
                        descriptor.label());
                WGPUCreationErrors.check(errors);
                for (int i = 0; i < uniformBindGroupLayouts.length; i++) {
                    uniformBindGroupLayouts[i] = createUniformBindGroupLayout(device, inputs,
                            resourceBindings, i, descriptor.label());
                    WGPUCreationErrors.check(errors);
                }
                WGPUVectorBindGroupLayout bindGroupLayouts = inputs.own(new WGPUVectorBindGroupLayout());
                for (int group = 0; group < resourceBindings.bindGroupCount(); group++) {
                    if (resourceBindings.textureSetIndex() == group) {
                        bindGroupLayouts.push_back(textureBindGroupLayout);
                    }
                    int uniformIndex = resourceBindings.uniformBufferIndex(group);
                    if (uniformIndex >= 0) {
                        bindGroupLayouts.push_back(uniformBindGroupLayouts[uniformIndex]);
                    }
                }

                WGPUPipelineLayoutDescriptor layoutDescriptor = inputs.own(new WGPUPipelineLayoutDescriptor());
                layoutDescriptor.setNextInChain(WGPUChainedStruct.NULL);
                layoutDescriptor.setLabel(descriptor.label() + " layout");
                layoutDescriptor.setBindGroupLayouts(bindGroupLayouts);

                pipelineLayout = new WGPUPipelineLayout();
                device.createPipelineLayout(layoutDescriptor, pipelineLayout);
                WGPUCreationErrors.check(errors);

                WGPURenderPipelineDescriptor pipelineDescriptor = inputs.own(new WGPURenderPipelineDescriptor());
                pipelineDescriptor.setNextInChain(WGPUChainedStruct.NULL);
                pipelineDescriptor.setLabel(descriptor.label());
                pipelineDescriptor.getVertex().setModule(shaderModule.nativeModule());
                pipelineDescriptor.getVertex().setEntryPoint(descriptor.vertexEntryPoint());
                pipelineDescriptor.getVertex().setConstants(WGPUVectorConstantEntry.NULL);
                pipelineDescriptor.getVertex().setBuffers(createVertexBuffers(inputs, descriptor.vertexLayouts()));
                PrimitiveState primitive = descriptor.primitiveState();
                pipelineDescriptor.getPrimitive().setNextInChain(WGPUChainedStruct.NULL);
                pipelineDescriptor.getPrimitive().setTopology(toNative(primitive.topology()));
                pipelineDescriptor.getPrimitive().setStripIndexFormat(WGPUIndexFormat.Undefined);
                pipelineDescriptor.getPrimitive().setFrontFace(switch (primitive.frontFace()) {
                    case COUNTER_CLOCKWISE -> WGPUFrontFace.CCW;
                    case CLOCKWISE -> WGPUFrontFace.CW;
                });
                pipelineDescriptor.getPrimitive().setCullMode(switch (primitive.cullMode()) {
                    case NONE -> WGPUCullMode.None;
                    case FRONT -> WGPUCullMode.Front;
                    case BACK -> WGPUCullMode.Back;
                });
                pipelineDescriptor.setFragment(fragmentState);
                pipelineDescriptor.setDepthStencil(createDepthStencilState(inputs, descriptor));
                MultisampleState multisample = descriptor.multisampleState();
                pipelineDescriptor.getMultisample().setNextInChain(WGPUChainedStruct.NULL);
                pipelineDescriptor.getMultisample().setCount(multisample.count());
                pipelineDescriptor.getMultisample().setMask(multisample.mask());
                pipelineDescriptor.getMultisample().setAlphaToCoverageEnabled(
                        multisample.alphaToCoverageEnabled());
                pipelineDescriptor.setLayout(pipelineLayout);

                return new RenderPipelineInputs(inputs, pipelineDescriptor, domain, descriptor,
                        pipelineLayout, textureBindGroupLayout, uniformBindGroupLayouts, resourceBindings);
            }
            catch (RuntimeException | Error failure) {
                rollbackPipeline(domain, null, pipelineLayout, textureBindGroupLayout,
                        uniformBindGroupLayouts,
                        resourceBindings, failure);
                throw failure;
            }
        } catch (RuntimeException | Error failure) {
            suppressRollback(failure, inputs::close);
            throw failure;
        }
    }

    @Override
    public ComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("ComputePipelineDescriptor cannot be null");
        }
        context.requireDeviceUsable("create a compute pipeline");
        descriptor.validate(capabilities());
        WGPUShaderModuleHandle shaderModule = WGPUResources.requireShaderModule(
                descriptor.shaderModule(), context.resourceDomain(),
                "Compute pipeline shader module");
        ShaderResourceLayout resourceLayout = descriptor.resourceLayout();

        NativeResourceLayouts nativeResources = null;
        WGPUPipelineLayout pipelineLayout = null;
        WGPUComputePipeline pipeline = null;
        try {
            nativeResources = createResourceLayouts(resourceLayout, descriptor.label());
            WGPUVectorBindGroupLayout groupLayouts = WGPUVectorBindGroupLayout.obtain();
            for (WGPUBindGroupLayout groupLayout : nativeResources.layouts) {
                groupLayouts.push_back(groupLayout);
            }

            WGPUPipelineLayoutDescriptor layoutDescriptor = WGPUPipelineLayoutDescriptor.obtain();
            layoutDescriptor.setNextInChain(WGPUChainedStruct.NULL);
            layoutDescriptor.setLabel(descriptor.label() + " layout");
            layoutDescriptor.setBindGroupLayouts(groupLayouts);
            pipelineLayout = new WGPUPipelineLayout();
            context.nativeDevice().createPipelineLayout(layoutDescriptor, pipelineLayout);

            WGPUComputePipelineDescriptor nativeDescriptor =
                    WGPUComputePipelineDescriptor.obtain();
            nativeDescriptor.setNextInChain(WGPUChainedStruct.NULL);
            nativeDescriptor.setLabel(descriptor.label());
            nativeDescriptor.setLayout(pipelineLayout);
            nativeDescriptor.getCompute().setNextInChain(WGPUChainedStruct.NULL);
            nativeDescriptor.getCompute().setModule(shaderModule.nativeModule());
            nativeDescriptor.getCompute().setEntryPoint(descriptor.entryPoint());
            nativeDescriptor.getCompute().setConstants(WGPUVectorConstantEntry.NULL);

            pipeline = new WGPUComputePipeline();
            context.nativeDevice().createComputePipeline(nativeDescriptor, pipeline);
            if (!pipeline.isValid()) {
                throw new FdxException("Could not create WGPU compute pipeline");
            }
            return new WGPUComputePipelineHandle(context.resourceDomain(), pipeline,
                    pipelineLayout, nativeResources.layouts, nativeResources.usedGroups,
                    resourceLayout);
        } catch (RuntimeException | Error failure) {
            rollbackComputePipeline(pipeline, pipelineLayout, nativeResources,
                    resourceLayout, failure);
            throw failure;
        }
    }

    private NativeResourceLayouts createResourceLayouts(
            ShaderResourceLayout layout, String label) {
        int groupCount = 0;
        for (int i = 0; i < layout.bindingCount(); i++) {
            groupCount = Math.max(groupCount, layout.binding(i).group() + 1);
        }
        WGPUBindGroupLayout[] layouts = new WGPUBindGroupLayout[groupCount];
        boolean[] usedGroups = new boolean[groupCount];
        try {
            for (int group = 0; group < groupCount; group++) {
                WGPUVectorBindGroupLayoutEntry entries =
                        WGPUVectorBindGroupLayoutEntry.obtain();
                for (int i = 0; i < layout.bindingCount(); i++) {
                    ShaderBinding binding = layout.binding(i);
                    if (binding.group() != group) {
                        continue;
                    }
                    usedGroups[group] = true;
                    entries.push_back(createResourceLayoutEntry(binding));
                }
                WGPUBindGroupLayoutDescriptor descriptor =
                        WGPUBindGroupLayoutDescriptor.obtain();
                descriptor.setNextInChain(WGPUChainedStruct.NULL);
                descriptor.setLabel(label + " resource group " + group);
                descriptor.setEntries(entries);
                WGPUBindGroupLayout nativeLayout = new WGPUBindGroupLayout();
                context.nativeDevice().createBindGroupLayout(descriptor, nativeLayout);
                if (!nativeLayout.isValid()) {
                    throw new FdxException("Could not create WGPU bind group layout " + group);
                }
                layouts[group] = nativeLayout;
            }
            return new NativeResourceLayouts(layouts, usedGroups);
        } catch (RuntimeException | Error failure) {
            for (WGPUBindGroupLayout created : layouts) {
                if (created != null) {
                    rollbackBindGroupLayout(created, failure);
                }
            }
            throw failure;
        }
    }

    private WGPUBindGroupLayoutEntry createResourceLayoutEntry(ShaderBinding binding) {
        if (binding.bindingArrayCount() != ShaderBinding.ABSENT) {
            throw new FdxException("WGPU resource binding arrays are not exposed yet: "
                    + binding.name());
        }
        WGPUBindGroupLayoutEntry entry = WGPUBindGroupLayoutEntry.obtain();
        entry.setNextInChain(WGPUChainedStruct.NULL);
        entry.setBinding(binding.binding());
        synchronized (WGPUShaderStage.class) {
            entry.setVisibility(toNative(binding.visibility()));
        }
        switch (binding.resourceKind()) {
            case UNIFORM_BUFFER, STORAGE_BUFFER -> {
                WGPUBufferBindingLayout buffer = WGPUBufferBindingLayout.obtain();
                buffer.setNextInChain(WGPUChainedStruct.NULL);
                buffer.setType(binding.resourceKind() == ShaderResourceKind.UNIFORM_BUFFER
                        ? WGPUBufferBindingType.Uniform
                        : binding.access() == ShaderResourceAccess.READ
                                ? WGPUBufferBindingType.ReadOnlyStorage
                                : WGPUBufferBindingType.Storage);
                buffer.setHasDynamicOffset(0);
                buffer.setMinBindingSize(Math.toIntExact(binding.minimumBindingSize()));
                entry.setBuffer(buffer);
            }
            case SAMPLER -> {
                WGPUSamplerBindingLayout sampler = WGPUSamplerBindingLayout.obtain();
                sampler.setNextInChain(WGPUChainedStruct.NULL);
                sampler.setType(toNative(binding.samplerKind()));
                entry.setSampler(sampler);
            }
            case SAMPLED_TEXTURE, MULTISAMPLED_TEXTURE,
                    DEPTH_TEXTURE, DEPTH_MULTISAMPLED_TEXTURE -> {
                WGPUTextureBindingLayout texture = WGPUTextureBindingLayout.obtain();
                texture.setNextInChain(WGPUChainedStruct.NULL);
                texture.setSampleType(binding.resourceKind() == ShaderResourceKind.DEPTH_TEXTURE
                        || binding.resourceKind() == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE
                                ? WGPUTextureSampleType.Depth
                                : toNative(binding.textureSampleType()));
                texture.setViewDimension(toNative(binding.textureDimension()));
                texture.setMultisampled(binding.resourceKind()
                        == ShaderResourceKind.MULTISAMPLED_TEXTURE
                        || binding.resourceKind()
                        == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE ? 1 : 0);
                entry.setTexture(texture);
            }
            case STORAGE_TEXTURE -> {
                WGPUStorageTextureBindingLayout texture =
                        WGPUStorageTextureBindingLayout.obtain();
                texture.setNextInChain(WGPUChainedStruct.NULL);
                texture.setAccess(toNative(binding.access()));
                texture.setFormat(WGPUTextureFormats.toNative(
                        WGPUTextureFormats.toCommon(binding.storageFormat())));
                texture.setViewDimension(toNative(binding.textureDimension()));
                entry.setStorageTexture(texture);
            }
            case EXTERNAL_TEXTURE -> throw new FdxException(
                    "WGPU external textures are not exposed by the common resource API");
            case TEXEL_BUFFER, INPUT_ATTACHMENT, UNKNOWN -> throw new FdxException(
                    "WGPU does not support reflected resource kind "
                            + binding.resourceKind());
        }
        return entry;
    }

    private static WGPUBindGroupLayout createTextureBindGroupLayout(WGPUDevice device, NativeInputs inputs,
            ShaderRenderBindings bindings, String label) {
        if (bindings.sampledTextureCount() <= 0 && bindings.samplerCount() <= 0) {
            return null;
        }
        WGPUVectorBindGroupLayoutEntry entries = inputs.own(new WGPUVectorBindGroupLayoutEntry());

        for (int slot = 0; slot < bindings.sampledTextureCount(); slot++) {
            WGPUBindGroupLayoutEntry textureEntry = inputs.own(new WGPUBindGroupLayoutEntry());
            textureEntry.setNextInChain(WGPUChainedStruct.NULL);
            textureEntry.setBinding(bindings.reflected()
                    ? bindings.texture(slot).binding() : slot * 2);
            synchronized (WGPUShaderStage.class) {
                textureEntry.setVisibility(bindings.reflected()
                        ? toNative(bindings.texture(slot).visibility()) : WGPUShaderStage.Fragment);
            }
            // Default nested-layout constructors use shared native storage; borrow this entry's field.
            WGPUTextureBindingLayout textureLayout = textureEntry.getTexture();
            textureLayout.setNextInChain(WGPUChainedStruct.NULL);
            textureLayout.setSampleType(bindings.reflected()
                    ? toNative(bindings.texture(slot).textureSampleType())
                    : WGPUTextureSampleType.Float);
            textureLayout.setViewDimension(bindings.reflected()
                    ? toNative(bindings.texture(slot).textureDimension())
                    : WGPUTextureViewDimension._2D);
            textureLayout.setMultisampled(bindings.reflected()
                    && (bindings.texture(slot).resourceKind()
                    == ShaderResourceKind.MULTISAMPLED_TEXTURE
                    || bindings.texture(slot).resourceKind()
                    == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE) ? 1 : 0);
            entries.push_back(textureEntry);

        }
        for (int slot = 0; slot < bindings.samplerCount(); slot++) {
            WGPUBindGroupLayoutEntry samplerEntry = inputs.own(new WGPUBindGroupLayoutEntry());
            samplerEntry.setNextInChain(WGPUChainedStruct.NULL);
            samplerEntry.setBinding(bindings.reflected()
                    ? bindings.sampler(slot).binding() : slot * 2 + 1);
            synchronized (WGPUShaderStage.class) {
                samplerEntry.setVisibility(bindings.reflected()
                        ? toNative(bindings.sampler(slot).visibility()) : WGPUShaderStage.Fragment);
            }
            WGPUSamplerBindingLayout samplerLayout = samplerEntry.getSampler();
            samplerLayout.setNextInChain(WGPUChainedStruct.NULL);
            samplerLayout.setType(bindings.reflected()
                    ? toNative(bindings.sampler(slot).samplerKind())
                    : WGPUSamplerBindingType.Filtering);
            entries.push_back(samplerEntry);
        }

        WGPUBindGroupLayoutDescriptor descriptor = inputs.own(new WGPUBindGroupLayoutDescriptor());
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel(label + " texture bind group layout");
        descriptor.setEntries(entries);
        WGPUBindGroupLayout bindGroupLayout = new WGPUBindGroupLayout();
        try {
            device.createBindGroupLayout(descriptor, bindGroupLayout);
            return bindGroupLayout;
        }
        catch (RuntimeException | Error failure) {
            rollbackBindGroupLayout(bindGroupLayout, failure);
            throw failure;
        }
    }

    private static WGPUBindGroupLayout createUniformBindGroupLayout(WGPUDevice device, NativeInputs inputs,
            ShaderRenderBindings bindings, int uniformIndex, String label) {
        WGPUVectorBindGroupLayoutEntry entries = inputs.own(new WGPUVectorBindGroupLayoutEntry());

        ShaderBinding binding = bindings.uniformBuffer(uniformIndex);
        WGPUBindGroupLayoutEntry uniformEntry = inputs.own(new WGPUBindGroupLayoutEntry());
        uniformEntry.setNextInChain(WGPUChainedStruct.NULL);
        uniformEntry.setBinding(binding.binding());
        synchronized (WGPUShaderStage.class) {
            uniformEntry.setVisibility(toNative(binding.visibility()));
        }
        WGPUBufferBindingLayout uniformLayout = uniformEntry.getBuffer();
        uniformLayout.setNextInChain(WGPUChainedStruct.NULL);
        uniformLayout.setType(WGPUBufferBindingType.Uniform);
        uniformLayout.setHasDynamicOffset(1);
        uniformLayout.setMinBindingSize(bindings.uniformByteCount(uniformIndex));
        entries.push_back(uniformEntry);

        WGPUBindGroupLayoutDescriptor descriptor = inputs.own(new WGPUBindGroupLayoutDescriptor());
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel(label + " uniform bind group layout "
                + binding.group());
        descriptor.setEntries(entries);
        WGPUBindGroupLayout bindGroupLayout = new WGPUBindGroupLayout();
        try {
            device.createBindGroupLayout(descriptor, bindGroupLayout);
            return bindGroupLayout;
        }
        catch (RuntimeException | Error failure) {
            rollbackBindGroupLayout(bindGroupLayout, failure);
            throw failure;
        }
    }

    private static WGPUDepthStencilState createDepthStencilState(NativeInputs inputs,
            RenderPipelineDescriptor descriptor) {
        DepthStencilState state = descriptor.depthStencilState();
        if (state == null) {
            return WGPUDepthStencilState.NULL;
        }
        WGPUDepthStencilState depthStencilState = inputs.own(new WGPUDepthStencilState());
        depthStencilState.setNextInChain(WGPUChainedStruct.NULL);
        depthStencilState.setFormat(WGPUTextureFormats.toNative(state.format()));
        depthStencilState.setDepthWriteEnabled(state.depthWriteEnabled()
                ? WGPUOptionalBool.True
                : WGPUOptionalBool.False);
        depthStencilState.setDepthCompare(toNativeCompare(state.depthCompare()));
        setStencilFace(depthStencilState.getStencilFront(), state.stencilFront());
        setStencilFace(depthStencilState.getStencilBack(), state.stencilBack());
        depthStencilState.setStencilReadMask(state.stencilReadMask());
        depthStencilState.setStencilWriteMask(state.stencilWriteMask());
        depthStencilState.setDepthBias(state.depthBias());
        depthStencilState.setDepthBiasSlopeScale(state.depthBiasSlopeScale());
        depthStencilState.setDepthBiasClamp(state.depthBiasClamp());
        return depthStencilState;
    }

    private static WGPUBlendState createBlendState(NativeInputs inputs, BlendState state) {
        WGPUBlendState blend = inputs.own(new WGPUBlendState());
        setBlendComponent(blend.getColor(), state.color());
        setBlendComponent(blend.getAlpha(), state.alpha());
        return blend;
    }

    private static void setBlendComponent(WGPUBlendComponent target,
            BlendComponent source) {
        target.setOperation(switch (source.operation()) {
            case ADD -> WGPUBlendOperation.Add;
            case SUBTRACT -> WGPUBlendOperation.Subtract;
            case REVERSE_SUBTRACT -> WGPUBlendOperation.ReverseSubtract;
            case MIN -> WGPUBlendOperation.Min;
            case MAX -> WGPUBlendOperation.Max;
        });
        target.setSrcFactor(toNative(source.sourceFactor()));
        target.setDstFactor(toNative(source.destinationFactor()));
    }

    private static WGPUBlendFactor toNative(BlendFactor factor) {
        return switch (factor) {
            case ZERO -> WGPUBlendFactor.Zero;
            case ONE -> WGPUBlendFactor.One;
            case SOURCE -> WGPUBlendFactor.Src;
            case ONE_MINUS_SOURCE -> WGPUBlendFactor.OneMinusSrc;
            case SOURCE_ALPHA -> WGPUBlendFactor.SrcAlpha;
            case ONE_MINUS_SOURCE_ALPHA -> WGPUBlendFactor.OneMinusSrcAlpha;
            case DESTINATION -> WGPUBlendFactor.Dst;
            case ONE_MINUS_DESTINATION -> WGPUBlendFactor.OneMinusDst;
            case DESTINATION_ALPHA -> WGPUBlendFactor.DstAlpha;
            case ONE_MINUS_DESTINATION_ALPHA -> WGPUBlendFactor.OneMinusDstAlpha;
            case SOURCE_ALPHA_SATURATED -> WGPUBlendFactor.SrcAlphaSaturated;
            case CONSTANT -> WGPUBlendFactor.Constant;
            case ONE_MINUS_CONSTANT -> WGPUBlendFactor.OneMinusConstant;
        };
    }

    // Generated bitmask enums reuse CUSTOM; callers guard both combination and the consuming setter.
    private static WGPUColorWriteMask toNativeColorWriteMask(int mask) {
        WGPUColorWriteMask result = WGPUColorWriteMask.None;
        if ((mask & ColorWriteMask.RED) != 0) {
            result = result.or(WGPUColorWriteMask.Red);
        }
        if ((mask & ColorWriteMask.GREEN) != 0) {
            result = result.or(WGPUColorWriteMask.Green);
        }
        if ((mask & ColorWriteMask.BLUE) != 0) {
            result = result.or(WGPUColorWriteMask.Blue);
        }
        if ((mask & ColorWriteMask.ALPHA) != 0) {
            result = result.or(WGPUColorWriteMask.Alpha);
        }
        return result;
    }

    private static void setStencilFace(
            WGPUStencilFaceState target,
            StencilFaceState source) {
        target.setCompare(toNativeCompare(source.compare()));
        target.setFailOp(toNative(source.fail()));
        target.setDepthFailOp(toNative(source.depthFail()));
        target.setPassOp(toNative(source.pass()));
    }

    private static WGPUStencilOperation toNative(
            StencilOperation operation) {
        return switch (operation) {
            case KEEP -> WGPUStencilOperation.Keep;
            case ZERO -> WGPUStencilOperation.Zero;
            case REPLACE -> WGPUStencilOperation.Replace;
            case INVERT -> WGPUStencilOperation.Invert;
            case INCREMENT_CLAMP -> WGPUStencilOperation.IncrementClamp;
            case DECREMENT_CLAMP -> WGPUStencilOperation.DecrementClamp;
            case INCREMENT_WRAP -> WGPUStencilOperation.IncrementWrap;
            case DECREMENT_WRAP -> WGPUStencilOperation.DecrementWrap;
        };
    }

    private static WGPUVectorVertexBufferLayout createVertexBuffers(NativeInputs inputs, VertexLayout[] layouts) {
        WGPUVectorVertexBufferLayout vertexBuffers = inputs.own(new WGPUVectorVertexBufferLayout());
        vertexBuffers.clear();
        if (layouts == null || layouts.length == 0) {
            return vertexBuffers;
        }

        for (int layoutIndex = 0; layoutIndex < layouts.length; layoutIndex++) {
            VertexLayout layout = layouts[layoutIndex];
            WGPUVectorVertexAttribute nativeAttributes = inputs.own(new WGPUVectorVertexAttribute());
            nativeAttributes.clear();
            for (int i = 0; i < layout.attributeCount(); i++) {
                VertexAttribute attribute = layout.attribute(i);
                WGPUVertexAttribute nativeAttribute = inputs.own(new WGPUVertexAttribute());
                nativeAttribute.setShaderLocation(attribute.location());
                nativeAttribute.setOffset(attribute.offset());
                nativeAttribute.setFormat(toNative(attribute.format()));
                nativeAttributes.push_back(nativeAttribute);
            }

            WGPUVertexBufferLayout nativeLayout = inputs.own(new WGPUVertexBufferLayout());
            nativeLayout.setArrayStride(layout.arrayStride());
            nativeLayout.setStepMode(layout.stepMode() == VertexStepMode.INSTANCE
                    ? WGPUVertexStepMode.Instance
                    : WGPUVertexStepMode.Vertex);
            nativeLayout.setAttributes(nativeAttributes);
            vertexBuffers.push_back(nativeLayout);
        }
        return vertexBuffers;
    }

    private static WGPUVertexFormat toNative(VertexFormat format) {
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

    private static WGPUPrimitiveTopology toNative(PrimitiveTopology primitiveTopology) {
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

    private static WGPUShaderStage toNative(ShaderStageVisibility visibility) {
        WGPUShaderStage result = WGPUShaderStage.None;
        if (visibility.contains(ShaderStage.VERTEX)) {
            result = result.or(WGPUShaderStage.Vertex);
        }
        if (visibility.contains(ShaderStage.FRAGMENT)) {
            result = result.or(WGPUShaderStage.Fragment);
        }
        if (visibility.contains(ShaderStage.COMPUTE)) {
            result = result.or(WGPUShaderStage.Compute);
        }
        return result;
    }

    private static WGPUTextureSampleType toNative(ShaderTextureSampleType type) {
        return switch (type) {
            case UNFILTERABLE_FLOAT -> WGPUTextureSampleType.UnfilterableFloat;
            case DEPTH -> WGPUTextureSampleType.Depth;
            case SINT -> WGPUTextureSampleType.Sint;
            case UINT -> WGPUTextureSampleType.Uint;
            case FLOAT, FILTERABLE_FLOAT, UNKNOWN_FILTERABLE ->
                    WGPUTextureSampleType.Float;
            case NONE, UNKNOWN -> throw new FdxException(
                    "WGPU sampled texture has an unknown sample type");
        };
    }

    private static WGPUTextureViewDimension toNative(ShaderTextureDimension dimension) {
        return switch (dimension) {
            case D1 -> WGPUTextureViewDimension._1D;
            case D2 -> WGPUTextureViewDimension._2D;
            case D2_ARRAY -> WGPUTextureViewDimension._2DArray;
            case CUBE -> WGPUTextureViewDimension.Cube;
            case CUBE_ARRAY -> WGPUTextureViewDimension.CubeArray;
            case D3 -> WGPUTextureViewDimension._3D;
            case NONE, UNKNOWN -> throw new FdxException(
                    "WGPU sampled texture has an unknown dimension");
        };
    }

    private static WGPUSamplerBindingType toNative(ShaderSamplerKind kind) {
        return switch (kind) {
            case FILTERING, UNKNOWN_FILTERING -> WGPUSamplerBindingType.Filtering;
            case NON_FILTERING -> WGPUSamplerBindingType.NonFiltering;
            case COMPARISON -> WGPUSamplerBindingType.Comparison;
            case NONE, UNKNOWN -> throw new FdxException(
                    "WGPU sampler has an unknown binding type");
        };
    }

    private static WGPUStorageTextureAccess toNative(ShaderResourceAccess access) {
        return switch (access) {
            case READ -> WGPUStorageTextureAccess.ReadOnly;
            case WRITE -> WGPUStorageTextureAccess.WriteOnly;
            case READ_WRITE -> WGPUStorageTextureAccess.ReadWrite;
            case NONE, UNKNOWN -> throw new FdxException(
                    "WGPU storage texture has no explicit access mode");
        };
    }

    private void rollbackBuffer(WGPUBuffer buffer, Throwable failure) {
        suppressRollback(failure, () -> new WGPUBufferAllocation(context.resourceDomain(), buffer).retire());
    }

    private void rollbackTexture(WGPUTexture texture,
            WGPUTextureView view, WGPUTextureView storageView,
            WGPUSampler sampler, WGPUTextureView[] attachmentViews, Throwable failure) {
        suppressRollback(failure,
                () -> new WGPUTextureAllocation(context.resourceDomain(),
                        texture, view, storageView, sampler, attachmentViews).retire());
    }

    private static void rollbackShaderModule(WGPUResourceDomain domain, WGPUShaderModule shaderModule, Throwable failure) {
        suppressRollback(failure, () -> new WGPUShaderModuleHandle(domain, shaderModule,
                ShaderLanguage.WGSL).dispose());
    }

    private static void rollbackPipeline(WGPUResourceDomain domain, WGPURenderPipeline pipeline, WGPUPipelineLayout pipelineLayout,
            WGPUBindGroupLayout textureBindGroupLayout,
            WGPUBindGroupLayout[] uniformBindGroupLayouts,
            ShaderRenderBindings bindings, Throwable failure) {
        suppressRollback(failure, () -> new WGPURenderPipelineHandle(domain, pipeline,
                pipelineLayout, textureBindGroupLayout, uniformBindGroupLayouts, 0,
                -1, 0, bindings, null).dispose());
    }

    private void rollbackComputePipeline(WGPUComputePipeline pipeline,
            WGPUPipelineLayout pipelineLayout, NativeResourceLayouts nativeResources,
            ShaderResourceLayout resourceLayout, Throwable failure) {
        WGPUBindGroupLayout[] layouts = nativeResources != null
                ? nativeResources.layouts : new WGPUBindGroupLayout[0];
        boolean[] usedGroups = nativeResources != null
                ? nativeResources.usedGroups : new boolean[0];
        suppressRollback(failure, () -> new WGPUComputePipelineHandle(
                context.resourceDomain(), pipeline, pipelineLayout, layouts,
                usedGroups, resourceLayout).dispose());
    }

    private static void rollbackBindGroupLayout(WGPUBindGroupLayout layout, Throwable failure) {
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

    private static void suppressRollback(Throwable failure, Runnable rollback) {
        try {
            rollback.run();
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static final class NativeInputs implements AutoCloseable {
        // Vectors and pointed-to descriptors remain alive through native creation, then close together.
        private final ArrayList<NativeObject> owned = new ArrayList<>();

        <T extends NativeObject> T own(T value) {
            owned.add(value);
            return value;
        }

        @Override public void close() {
            WGPUCleanup cleanup = new WGPUCleanup();
            for (int i = owned.size() - 1; i >= 0; i--) cleanup.run(owned.get(i)::dispose);
            owned.clear();
            cleanup.throwIfFailed();
        }
    }

    private static final class NativeResourceLayouts {
        private final WGPUBindGroupLayout[] layouts;
        private final boolean[] usedGroups;

        private NativeResourceLayouts(WGPUBindGroupLayout[] layouts,
                boolean[] usedGroups) {
            this.layouts = layouts;
            this.usedGroups = usedGroups;
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

    @Override
    public GraphicsCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ShaderTargetSupport shaderTargetSupport() {
        return WGPU.getPlatformType() == WGPUPlatformType.WGPU_Web
                ? ShaderTargetSupport.of(ShaderTargetEnvironments.WEBGPU_WGSL_1)
                : ShaderTargetSupport.of(ShaderTargetEnvironments.WGPU_WGSL_1);
    }

    private static ShaderTarget shaderTarget() {
        return WGPU.getPlatformType() == WGPUPlatformType.WGPU_Web
                ? ShaderTarget.WEBGPU_WGSL : ShaderTarget.WGPU_WGSL;
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
