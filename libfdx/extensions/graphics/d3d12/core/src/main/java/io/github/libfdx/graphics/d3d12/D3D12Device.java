package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.ComputePipelineDescriptor;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;
import io.github.libfdx.graphics.internal.TextureUploads;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.target.RuntimeShaderTargetCompiler;
import io.github.libfdx.graphics.shader.target.RuntimeWgslTargetVerifier;
import io.github.libfdx.graphics.shader.target.ShaderCompilerRegistry;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureOrigin;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.runtime.core.RuntimeCore;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;

final class D3D12Device implements GraphicsDevice {
    private static GraphicsCapabilities capabilities(boolean hdr, boolean multisample) {
        GraphicsCapabilities.Builder builder = GraphicsCapabilities.builder()
            .profile(ShaderProfile.PORTABLE_WEBGL2)
            .profile(ShaderProfile.PORTABLE_WEBGPU)
            .profile(ShaderProfile.NATIVE)
            .feature(GraphicsFeature.INDEXED_DRAW)
            .feature(GraphicsFeature.COMPUTE)
            .feature(GraphicsFeature.STORAGE_BUFFERS)
            .feature(GraphicsFeature.STORAGE_TEXTURES)
            .feature(GraphicsFeature.ATOMICS)
            .feature(GraphicsFeature.INSTANCED_DRAW)
            .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
            .feature(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS)
            .feature(GraphicsFeature.ALPHA_BLEND_CONTROL)
            .feature(GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE)
            .feature(GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS)
            .feature(GraphicsFeature.TEXTURE_MIP_LEVELS)
            .feature(GraphicsFeature.TEXTURE_MIN_MAG_FILTERS)
            .colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.R32_FLOAT)
            .depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
            // Direct3D 12 clips depth to 0..w.
            .clipDepthRange(ClipDepthRange.ZERO_TO_ONE)
            .renderedTextureOrigin(TextureOrigin.TOP_LEFT)
            .sampleCounts(multisample ? new int[] {1, 4} : new int[] {1})
            .limits(GraphicsLimits.builder()
                    .maxBindGroups(2)
                    .maxStorageBuffersPerStage(8)
                    .maxStorageTexturesPerStage(8)
                    .maxStorageBufferBindingSize(128L * 1024 * 1024)
                    .maxComputeWorkgroupsPerDimension(65535)
                    .maxComputeWorkgroupSize(1024, 1024, 64)
                    .maxComputeInvocationsPerWorkgroup(1024)
                    .maxComputeWorkgroupStorageSize(32768)
                    .maxBindingsPerGroup(32)
                    .maxUniformBuffersPerStage(1)
                    .maxSampledTexturesPerStage(16)
                    .maxSamplersPerStage(16)
                    .maxColorAttachments(8)
                    .maxVertexBuffers(4)
                    .maxVertexAttributes(16)
                    .maxUniformBufferBindingSize(64L * 1024L)
                    .build());
        if (hdr) builder.colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT, TextureFormat.R32_FLOAT)
                .filterableColorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                        TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT)
                .blendableColorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                        TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT);
        if (multisample) builder.feature(GraphicsFeature.MULTISAMPLE).feature(GraphicsFeature.RESOLVE_ATTACHMENTS)
                .resolveFormats(hdr
                        ? new TextureFormat[] {TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                                TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT, TextureFormat.R32_FLOAT}
                        : new TextureFormat[] {TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                                TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.R32_FLOAT});
        return builder.build();
    }
    private final D3D12Context context;
    private GraphicsCapabilities capabilities;
    private final int preparationWorkers;
    private final boolean validation, optimize;
    private final ShaderArtifactCache shaderCache;
    private ShaderPreparationCapabilities preparationCapabilities;
    private D3D12PreparationQueue preparation;
    private Object resourceDomain = new Object();
    private boolean preparationClosed;

    D3D12Device(D3D12Context context, D3D12Configuration configuration) {
        this.context = context;
        preparationWorkers = configuration.shaderPreparationWorkers();
        validation = configuration.validation();
        optimize = configuration.optimizeShaders();
        shaderCache = configuration.shaderCache();
        var workers = ShaderPreparationCapabilities.Execution.WORKERS;
        preparationCapabilities = new ShaderPreparationCapabilities(
                workers, workers, true, preparationWorkers, shaderCache != null && shaderCache.enabled(), false);
    }

    @Override public Object resourceDomain() { context.detectDeviceLoss(); return resourceDomain; }

    void initializedPipelineCache(boolean identifiedAdapter) {
        var workers = ShaderPreparationCapabilities.Execution.WORKERS;
        boolean artifacts = shaderCache != null && shaderCache.enabled();
        preparationCapabilities = new ShaderPreparationCapabilities(workers, workers, true, preparationWorkers,
                artifacts, artifacts && identifiedAdapter);
    }

    @Override public ShaderPreparationCapabilities shaderPreparationCapabilities() {
        return preparationCapabilities;
    }

    @Override public ShaderPreparationOperation prepareRenderPipeline(
            ShaderPipelineRequest request) {
        Objects.requireNonNull(request, "request");
        context.requireDeviceAvailable("prepare a render pipeline");
        if (preparation == null) {
            var compiler = RuntimeCore.shaderCompiler();
            var registry = ShaderCompilerRegistry.builder()
                    .compiler(new RuntimeShaderTargetCompiler(compiler, RuntimeShaderTargetCompiler.VERSION, shaderCache))
                    .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
            var refreshRegistry = shaderCache == null ? registry : ShaderCompilerRegistry.builder()
                    .compiler(new RuntimeShaderTargetCompiler(compiler, RuntimeShaderTargetCompiler.VERSION, shaderCache.refreshing()))
                    .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
            preparation = new D3D12PreparationQueue(context, capabilities(), registry, refreshRegistry,
                    preparationWorkers, validation, optimize, shaderCache);
        }
        return preparation.submit(request);
    }

    void closePreparation() {
        if (preparationClosed) return;
        preparationClosed = true;
        resourceDomain = new Object();
        if (preparation != null) preparation.close();
    }

    @Override
    public Buffer createBuffer(BufferDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("BufferDescriptor cannot be null");
        }
        context.requireUsable("create a buffer");
        long handle = D3D12Native.createBuffer(context.nativeHandle(), descriptor.size(), descriptor.usage().ordinal());
        return new D3D12Buffer(context, handle, descriptor.size(), descriptor.usage());
    }

    @Override
    public void writeBuffer(Buffer buffer, ByteBuffer data) {
        if (data == null) {
            throw new FdxException("Buffer data cannot be null");
        }
        D3D12Buffer target = context.requireBuffer(buffer, "Buffer");
        if (data.remaining() > target.size()) {
            throw new FdxException("Buffer write exceeds the destination size");
        }
        int size = data.remaining();
        MemorySegment source = target.uploadSource(data, size);
        D3D12Native.writeBuffer(context.nativeHandle(), target.nativeHandle(), source, size);
    }

    @Override public ByteBuffer readBuffer(Buffer buffer, int offset, int size) {
        var target = context.requireBuffer(buffer, "Readback buffer");
        if (target.usage() != BufferUsage.READBACK || offset < 0 || size < 0 || offset > target.size() - size) throw new FdxException("Invalid D3D12 readback buffer or range");
        return D3D12Native.readBuffer(context.nativeHandle(), target.nativeHandle(), offset, size);
    }

    @Override public ComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor) {
        context.requireUsable("create a compute pipeline");
        descriptor.validate(capabilities());
        if (!(descriptor.shaderModule() instanceof D3D12Compute.Module module) || module.context != context || module.isDisposed()) throw new FdxException("Invalid D3D12 compute module or device");
        return new D3D12Compute.Pipeline(context, module, descriptor);
    }

    @Override
    public Texture createTexture(TextureDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("TextureDescriptor cannot be null");
        }
        descriptor.validate(capabilities());
        if (descriptor.format().isDepthStencil() && descriptor.usage() != TextureUsage.RENDER_ATTACHMENT) {
            throw new FdxException("Direct3D 12 depth textures currently support attachment usage only");
        }
        context.requireUsable("create a texture");
        long handle = D3D12Native.createTexture(context.nativeHandle(), descriptor.width(), descriptor.height(),
                descriptor.format().ordinal(), descriptor.usage().ordinal(), descriptor.filter().ordinal(),
                descriptor.wrapS().ordinal(), descriptor.wrapT().ordinal(), descriptor.magFilter().ordinal(),
                descriptor.mipmapFilter().ordinal(), descriptor.mipLevelCount(), descriptor.sampleCount());
        return new D3D12Texture(context, handle, descriptor.width(), descriptor.height(), descriptor.format(),
                descriptor.usage(), descriptor.filter(), descriptor.wrapS(), descriptor.wrapT(), descriptor.mipLevelCount(), descriptor.sampleCount());
    }

    @Override
    public void writeTexture(Texture texture, ByteBuffer data) {
        if (data == null) {
            throw new FdxException("Texture data cannot be null");
        }
        D3D12Texture target = context.requireTexture(texture, "Texture");
        TextureUploads.validateSingle(target, data);
        int requiredBytes = TextureUploads.byteCount(target, 0);
        MemorySegment source = target.uploadSource(data, requiredBytes);
        D3D12Native.writeTexture(context.nativeHandle(), target.nativeHandle(), source, requiredBytes);
    }

    @Override public void writeTextureMipLevels(Texture texture, ByteBuffer... levels) {
        D3D12Texture target = context.requireTexture(texture, "Texture");
        TextureUploads.validate(target, levels);
        MemorySegment source = target.uploadMipSource(levels);
        D3D12Native.writeTexture(context.nativeHandle(), target.nativeHandle(), source, Math.toIntExact(source.byteSize()));
    }

    @Override
    public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("ShaderModuleDescriptor cannot be null");
        }
        context.requireUsable("create a shader module");
        if (descriptor.targetArtifact() != null
                && descriptor.targetArtifact().target().equals(ShaderTarget.DIRECTX_HLSL.id())
                && !shaderTargetSupport().accepts(descriptor.targetArtifact().environment())) {
            throw new FdxException("Direct3D 12 requires d3d12-dxc-sm-6.0 shader artifacts. "
                    + "Regenerate this HLSL artifact from its WGSL source for DXC: " + descriptor.label());
        }
        if (descriptor.hasSource(ShaderLanguage.WGSL) && !descriptor.reflection().complete()) {
            descriptor = ShaderModuleDescriptors.requireTarget(descriptor, ShaderTarget.WGPU_WGSL, "D3D12 reflection")
                    .entryPoints(descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint());
        }
        ShaderModuleDescriptor ready = ShaderModuleDescriptors.requireTarget(descriptor,
                ShaderTarget.DIRECTX_HLSL, "Direct3D 12");
        if (ready.targetArtifact() != null) {
            shaderTargetSupport().require(ready.targetArtifact());
        }
        if (ShaderModuleDescriptors.computeOnly(ready.reflection())) return new D3D12Compute.Module(context, ready);
        if (!ready.hasSource(ShaderLanguage.HLSL)) {
            throw new FdxException("Direct3D 12 requires HLSL shader modules");
        }
        long handle = D3D12Native.createShader(context.nativeHandle(), ready.hlslVertexSource(),
                ready.hlslFragmentSource(), ready.vertexEntryPoint(), ready.fragmentEntryPoint(), ready.label());
        return new D3D12Shader(context, handle, ready.reflection());
    }

    @Override
    public RenderPipeline[] createRenderPipelines(RenderPipelineDescriptor... descriptors) {
        Objects.requireNonNull(descriptors, "descriptors");
        context.requireUsable("create render pipelines");
        long[] shaders = new long[descriptors.length];
        for (int i = 0; i < descriptors.length; i++) {
            var descriptor = Objects.requireNonNull(descriptors[i], "descriptor");
            descriptor.validate(capabilities());
            shaders[i] = context.requireShader(descriptor.shaderModule(), "Render pipeline shader module").nativeHandle();
        }
        D3D12Native.prepareShaders(context.nativeHandle(), shaders);
        return GraphicsDevice.super.createRenderPipelines(descriptors);
    }

    @Override
    public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPipelineDescriptor cannot be null");
        }
        context.requireUsable("create a render pipeline");
        D3D12Shader shader = context.requireShader(descriptor.shaderModule(), "Render pipeline shader module");
        descriptor.validate(capabilities());
        ShaderRenderBindings resources = ShaderRenderBindings.from(descriptor);
        PipelineBindings bindings = PipelineBindings.from(resources);
        VertexInputs inputs = VertexInputs.from(descriptor.vertexLayouts());
        long handle = D3D12Native.createPipeline(context.nativeHandle(), shader.nativeHandle(),
                descriptor.colorFormat().ordinal(), descriptor.primitiveTopology().ordinal(),
                descriptor.depthTestEnabled(), descriptor.depthWriteEnabled(), descriptor.colorTargets()[0].blend() != null,
                descriptor.sampledTextureCount(),
                bindings.uniformGroup, bindings.uniformBinding,
                inputs.layoutStrides, inputs.layoutStepModes,
                inputs.locations, inputs.formats, inputs.offsets, inputs.slots,
                bindings.textureGroups, bindings.textureBindings,
                bindings.samplerGroups, bindings.samplerBindings, descriptor);
        return new D3D12Pipeline(context, handle, descriptor.sampledTextureCount(), resources,
                descriptor.renderTargetLayout());
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    public GraphicsCapabilities capabilities() {
        if (capabilities == null) capabilities = capabilities(D3D12Native.supportsHdr(context.nativeHandle()),
                D3D12Native.supportsMultisample(context.nativeHandle()));
        return capabilities;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)this;
    }

    static final class VertexInputs {
        final int[] layoutStrides;
        final int[] layoutStepModes;
        final int[] locations;
        final int[] formats;
        final int[] offsets;
        final int[] slots;

        private VertexInputs(int[] layoutStrides, int[] layoutStepModes, int[] locations,
                int[] formats, int[] offsets, int[] slots) {
            this.layoutStrides = layoutStrides;
            this.layoutStepModes = layoutStepModes;
            this.locations = locations;
            this.formats = formats;
            this.offsets = offsets;
            this.slots = slots;
        }

        static VertexInputs from(VertexLayout[] layouts) {
            int attributeCount = 0;
            for (int i = 0; i < layouts.length; i++) {
                attributeCount += layouts[i].attributeCount();
            }
            int[] strides = new int[layouts.length];
            int[] stepModes = new int[layouts.length];
            int[] locations = new int[attributeCount];
            int[] formats = new int[attributeCount];
            int[] offsets = new int[attributeCount];
            int[] slots = new int[attributeCount];
            int index = 0;
            for (int slot = 0; slot < layouts.length; slot++) {
                VertexLayout layout = layouts[slot];
                strides[slot] = layout.arrayStride();
                stepModes[slot] = layout.stepMode().ordinal();
                for (int attributeIndex = 0; attributeIndex < layout.attributeCount(); attributeIndex++) {
                    VertexAttribute attribute = layout.attribute(attributeIndex);
                    locations[index] = attribute.location();
                    formats[index] = attribute.format().ordinal();
                    offsets[index] = attribute.offset();
                    slots[index] = slot;
                    index++;
                }
            }
            return new VertexInputs(strides, stepModes, locations, formats, offsets, slots);
        }
    }

    static final class PipelineBindings {
        final int uniformGroup;
        final int uniformBinding;
        final int[] textureGroups;
        final int[] textureBindings;
        final int[] samplerGroups;
        final int[] samplerBindings;

        private PipelineBindings(int uniformGroup, int uniformBinding, int[] textureGroups, int[] textureBindings,
                int[] samplerGroups, int[] samplerBindings) {
            this.uniformGroup = uniformGroup;
            this.uniformBinding = uniformBinding;
            this.textureGroups = textureGroups;
            this.textureBindings = textureBindings;
            this.samplerGroups = samplerGroups;
            this.samplerBindings = samplerBindings;
        }

        static PipelineBindings from(ShaderRenderBindings resources) {
            int textureCount = resources.sampledTextureCount();
            int samplerCount = resources.reflected() ? resources.samplerCount() : textureCount;
            int[] textureGroups = new int[textureCount];
            int[] textureBindings = new int[textureCount];
            int[] samplerGroups = new int[samplerCount];
            int[] samplerBindings = new int[samplerCount];
            if (resources.reflected()) {
                for (int slot = 0; slot < textureCount; slot++) {
                    textureGroups[slot] = resources.texture(slot).group();
                    textureBindings[slot] = resources.texture(slot).binding();
                }
                for (int slot = 0; slot < samplerCount; slot++) {
                    samplerGroups[slot] = resources.sampler(slot).group();
                    samplerBindings[slot] = resources.sampler(slot).binding();
                }
            }
            else {
                for (int slot = 0; slot < textureCount; slot++) {
                    textureGroups[slot] = 0;
                    textureBindings[slot] = slot * 2;
                    samplerGroups[slot] = 0;
                    samplerBindings[slot] = slot * 2 + 1;
                }
            }
            return new PipelineBindings(resources.uniformGroup(), resources.uniformBinding(),
                    textureGroups, textureBindings, samplerGroups, samplerBindings);
        }
    }
}
