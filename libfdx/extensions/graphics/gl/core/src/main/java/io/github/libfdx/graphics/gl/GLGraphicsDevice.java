package io.github.libfdx.graphics.gl;

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
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
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
import io.github.libfdx.graphics.shader.target.ShaderBindingRemap;
import io.github.libfdx.graphics.shader.target.ShaderCompilerRegistry;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetBinding;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureOrigin;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.runtime.core.RuntimeCore;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Represents a GL graphics device.
 *
 * @author xpenatan
 */
final class GLGraphicsDevice implements GraphicsDevice {
    private final ShaderArtifactCache shaderCache;
    private static GraphicsCapabilities capabilities(boolean depthTextures, boolean mipTextures, boolean halfTextures,
            boolean completePipelineState, boolean multipleTargets, boolean compute) {
        GraphicsCapabilities.Builder builder = GraphicsCapabilities.builder()
            .profile(ShaderProfile.PORTABLE_WEBGL2)
            .profile(ShaderProfile.PORTABLE_WEBGPU)
            .feature(GraphicsFeature.INDEXED_DRAW)
            .feature(GraphicsFeature.INSTANCED_DRAW)
            .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
            .feature(GraphicsFeature.ALPHA_BLEND_CONTROL)
            .colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB)
            .depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
            // Desktop GL, GLES and WebGL all clip depth to -w..w.
            .clipDepthRange(ClipDepthRange.NEGATIVE_ONE_TO_ONE)
            .renderedTextureOrigin(TextureOrigin.BOTTOM_LEFT)
            .sampleCounts(multipleTargets ? new int[] {1, 4} : new int[] {1})
            .limits(GraphicsLimits.builder()
                    .maxBindGroups(2)
                    .maxBindingsPerGroup(32)
                    .maxUniformBuffersPerStage(1)
                    .maxStorageBuffersPerStage(compute ? 8 : 0)
                    .maxStorageTexturesPerStage(compute ? 8 : 0)
                    .maxStorageBufferBindingSize(compute ? 128L * 1024 * 1024 : 0)
                    .maxComputeWorkgroupsPerDimension(compute ? 65535 : 0)
                    .maxComputeWorkgroupSize(compute ? 1024 : 0, compute ? 1024 : 0, compute ? 64 : 0)
                    .maxComputeInvocationsPerWorkgroup(compute ? 1024 : 0)
                    .maxComputeWorkgroupStorageSize(compute ? 32768 : 0)
                    .maxSampledTexturesPerStage(16)
                    .maxSamplersPerStage(16)
                    .maxColorAttachments(multipleTargets ? 8 : 1)
                    .maxVertexBuffers(4)
                    .maxVertexAttributes(16)
                    .maxUniformBufferBindingSize(64L * 1024L)
                    .build());
        if (depthTextures) builder.feature(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS);
        if (compute) builder.feature(GraphicsFeature.COMPUTE).feature(GraphicsFeature.STORAGE_BUFFERS)
                .feature(GraphicsFeature.STORAGE_TEXTURES).feature(GraphicsFeature.ATOMICS);
        if (completePipelineState) builder.feature(GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE);
        if (mipTextures) builder.feature(GraphicsFeature.TEXTURE_MIP_LEVELS).feature(GraphicsFeature.TEXTURE_MIN_MAG_FILTERS);
        if (halfTextures) builder.colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT)
                .filterableColorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                        TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT)
                .blendableColorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                        TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT);
        if (multipleTargets) {
            builder.feature(GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS)
                    .feature(GraphicsFeature.MULTISAMPLE).feature(GraphicsFeature.RESOLVE_ATTACHMENTS)
                    .resolveFormats(halfTextures
                            ? new TextureFormat[] {TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT, TextureFormat.R32_FLOAT}
                            : new TextureFormat[] {TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.R32_FLOAT})
                    .colorFormats(halfTextures
                            ? new TextureFormat[] {TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.RGBA16_FLOAT, TextureFormat.R32_FLOAT}
                            : new TextureFormat[] {TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB, TextureFormat.R32_FLOAT});
        }
        return builder.build();
    }
    private final GraphicsCapabilities capabilities;
    private final ProviderId providerId;
    private final GLApi gl;
    private final GLResourceDomain resourceDomain;
    private final GLGraphicsAttachment attachment;
    private final Object closedPreparationDomain = new Object();
    private final ArrayList<GLPreparationOperation> preparations = new ArrayList<>();
    private ShaderPreparationCapabilities preparationCapabilities;
    private GLProgramCache programCache;
    private ShaderCompilerRegistry preparationCompilers;
    private ShaderCompilerRegistry refreshPreparationCompilers;
    private boolean preparationClosed;

    @Override public Object resourceDomain() {
        attachment.detectContextLoss();
        return preparationClosed || resourceDomain.isLost() ? closedPreparationDomain : resourceDomain;
    }

    @Override public ShaderPreparationCapabilities shaderPreparationCapabilities() {
        if (preparationCapabilities == null) {
            attachment.makeCurrent();
            boolean parallel = gl.supportsParallelShaderCompilation();
            int workers = gl.shaderPreparationWorkers();
            boolean artifacts = shaderCache != null && shaderCache.enabled();
            String binaryIdentity = artifacts ? gl.programBinaryIdentity() : null;
            if (binaryIdentity != null) programCache = new GLProgramCache(shaderCache, binaryIdentity, gl);
            preparationCapabilities = new ShaderPreparationCapabilities(
                    workers > 0 ? ShaderPreparationCapabilities.Execution.WORKERS : ShaderPreparationCapabilities.Execution.OWNER_THREAD,
                    parallel ? ShaderPreparationCapabilities.Execution.DRIVER_POLLING : ShaderPreparationCapabilities.Execution.OWNER_THREAD,
                    workers > 0 && parallel, workers, artifacts, programCache != null);
        }
        return preparationCapabilities;
    }

    @Override public ShaderPreparationOperation prepareRenderPipeline(ShaderPipelineRequest request) {
        attachment.detectContextLoss();
        if (preparationClosed) throw new FdxException("GL shader preparation is closed");
        resourceDomain.requireUsable();
        ShaderPreparationCapabilities execution = shaderPreparationCapabilities();
        if (request == null) throw new FdxException("Shader pipeline request cannot be null");
        if (preparationCompilers == null) {
            var compiler = RuntimeCore.shaderCompiler();
            preparationCompilers = ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(compiler,
                            RuntimeShaderTargetCompiler.VERSION, execution.artifactCache() ? shaderCache : null))
                    .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
            refreshPreparationCompilers = !execution.artifactCache() ? preparationCompilers
                    : ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(compiler,
                            RuntimeShaderTargetCompiler.VERSION, shaderCache.refreshing()))
                            .verifier(new RuntimeWgslTargetVerifier(compiler)).build();
        }
        GLPreparationOperation operation = new GLPreparationOperation(this, attachment, gl, providerId,
                resourceDomain, request, preparationCompilers, refreshPreparationCompilers, programCache);
        preparations.add(operation);
        try {
            if (execution.cpuExecution() == ShaderPreparationCapabilities.Execution.WORKERS) operation.prepareAsync();
            else operation.prepareLoading();
        }
        catch (RuntimeException | Error failure) { preparations.remove(operation); throw failure; }
        return operation;
    }

    void preparationFinished(GLPreparationOperation operation) { preparations.remove(operation); }
    void closePreparation(boolean contextLost) {
        if (preparationClosed) return;
        preparationClosed = true;
        for (int i = 0; i < preparations.size(); i++) preparations.get(i).close(contextLost);
        preparations.clear();
        gl.closeShaderPreparation();
    }

    GLGraphicsDevice(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain,
            GLGraphicsAttachment attachment, ShaderArtifactCache shaderCache) {
        this.providerId = providerId;
        this.shaderCache = shaderCache;
        this.gl = gl;
        capabilities = capabilities(gl.supportsDepthTextures(), gl.supportsMipTextures(), gl.supportsRgba16FloatTextures(),
                gl.supportsCompletePipelineState(), gl.supportsMultipleTargets(), gl.supportsCompute());
        this.resourceDomain = resourceDomain;
        this.attachment = attachment;
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
        if (descriptor.usage() != BufferUsage.VERTEX && descriptor.usage() != BufferUsage.INDEX
                && !capabilities.supports(GraphicsFeature.COMPUTE)) {
            throw new FdxException("GL currently supports vertex and index buffers only");
        }
        attachment.makeCurrent();
        int buffer = gl.genBuffer();
        try {
            if (descriptor.usage() == BufferUsage.INDEX) {
                gl.bindElementArrayBuffer(buffer);
                gl.elementBufferData(descriptor.size());
                gl.bindElementArrayBuffer(0);
            }
            else {
                gl.bindArrayBuffer(buffer);
                gl.bufferData(descriptor.size());
                gl.bindArrayBuffer(0);
            }
            return new GLBufferHandle(providerId, gl, resourceDomain, buffer, descriptor.size(), descriptor.usage());
        }
        catch (RuntimeException | Error failure) {
            rollbackBuffer(buffer, descriptor.usage(), failure);
            throw failure;
        }
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
        GLBufferHandle glBuffer = GLResources.requireBuffer(buffer, resourceDomain, "Buffer");
        if (data.remaining() > glBuffer.size()) {
            throw new FdxException("Buffer data is larger than the destination buffer");
        }
        attachment.makeCurrent();
        if (glBuffer.usage() == BufferUsage.INDEX) {
            gl.bindElementArrayBuffer(glBuffer.buffer());
            gl.elementBufferSubData(data);
            gl.bindElementArrayBuffer(0);
        } else {
            gl.bindArrayBuffer(glBuffer.buffer());
            gl.bufferSubData(data);
            gl.bindArrayBuffer(0);
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
        descriptor.validate(capabilities());
        if (!descriptor.usage().sampled() && !descriptor.usage().renderAttachment() && !descriptor.usage().storage()) {
            throw new FdxException("GL texture usage must allow sampling or render attachment binding");
        }
        boolean depth = descriptor.format() == TextureFormat.DEPTH32_FLOAT;
        if (descriptor.usage().storage() && descriptor.format().isSrgb()) {
            throw new FdxException("GL storage images require a linear format");
        }
        if (depth) {
            capabilities.require(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS);
            if (descriptor.usage() != TextureUsage.RENDER_ATTACHMENT) {
                throw new FdxException("GL depth textures currently support render attachment usage only");
            }
        } else if (descriptor.format() != TextureFormat.RGBA8_UNORM && descriptor.format() != TextureFormat.RGBA8_UNORM_SRGB
                && descriptor.format() != TextureFormat.RGBA16_FLOAT && descriptor.format() != TextureFormat.R32_FLOAT) {
            throw new FdxException("GL supports RGBA8, optional RGBA16_FLOAT and explicit DEPTH32_FLOAT textures");
        }
        attachment.makeCurrent();
        int texture = gl.genTexture();
        try {
            if (descriptor.sampleCount() > 1) {
                gl.texImageMultisample(texture, descriptor.format(), descriptor.width(), descriptor.height(), descriptor.sampleCount());
                return new GLTextureHandle(providerId, gl, resourceDomain, texture, descriptor.width(), descriptor.height(),
                        descriptor.format(), descriptor.usage(), 1, descriptor.sampleCount());
            }
            gl.bindTexture2D(texture);
            if (depth) gl.texImageDepth32F(descriptor.width(), descriptor.height());
            else for (int level = 0; level < descriptor.mipLevelCount(); level++) {
                gl.texImage2D(descriptor.format(), level, Math.max(1, descriptor.width() >> level),
                        Math.max(1, descriptor.height() >> level), null);
            }
            gl.textureMipRange2D(descriptor.mipLevelCount());
            gl.textureFilters2D(descriptor.minFilter(), descriptor.magFilter(), descriptor.mipmapFilter());
            gl.textureWrap2D(descriptor.wrapS(), descriptor.wrapT());
            gl.bindTexture2D(0);
            return new GLTextureHandle(providerId, gl, resourceDomain, texture, descriptor.width(),
                    descriptor.height(), descriptor.format(), descriptor.usage(), descriptor.mipLevelCount());
        }
        catch (RuntimeException | Error failure) {
            rollbackTexture(texture, failure);
            throw failure;
        }
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
        GLTextureHandle glTexture = GLResources.requireTexture(texture, resourceDomain, "Texture");
        TextureUploads.validateSingle(glTexture, data);
        if (glTexture.format().isDepthStencil()) throw new FdxException("GL depth uploads are not supported");
        if (glTexture.mipLevelCount() != 1) throw new FdxException("Use writeTextureMipLevels to replace the complete mip chain");
        int expected = TextureUploads.byteCount(glTexture, 0);
        if (data.remaining() < expected) {
            throw new FdxException("Texture data is smaller than the destination texture");
        }
        attachment.makeCurrent();
        gl.bindTexture2D(glTexture.texture());
        try {
            gl.texSubImage2D(glTexture.format(), 0, glTexture.width(), glTexture.height(), transferPixels(glTexture.format(), data));
        } finally { gl.bindTexture2D(0); }
    }

    @Override
    public void writeTextureMipLevels(Texture texture, ByteBuffer... levels) {
        GLTextureHandle target = GLResources.requireTexture(texture, resourceDomain, "Texture");
        TextureUploads.validate(target, levels);
        attachment.makeCurrent();
        gl.bindTexture2D(target.texture());
        try {
            for (int level = 0; level < levels.length; level++) {
                gl.texSubImage2D(target.format(), level, target.mipWidth(level), target.mipHeight(level), transferPixels(target.format(), levels[level]));
            }
        } finally { gl.bindTexture2D(0); }
    }

    private static ByteBuffer transferPixels(TextureFormat format, ByteBuffer data) {
        if (format != TextureFormat.RGBA16_FLOAT || ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) return data;
        // Common uploads are little endian, while native GL consumes native-order half components.
        ByteBuffer nativePixels = ByteBuffer.allocateDirect(data.remaining()).order(ByteOrder.nativeOrder());
        for (int i = data.position(); i + 1 < data.limit(); i += 2) {
            nativePixels.putShort((short) ((data.get(i) & 255) | ((data.get(i + 1) & 255) << 8)));
        }
        return nativePixels.flip();
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
        if (descriptor.hasSource(ShaderLanguage.WGSL) && !descriptor.reflection().complete()
                && capabilities.supports(GraphicsFeature.COMPUTE)) {
            descriptor = ShaderModuleDescriptors.requireTarget(descriptor, ShaderTarget.WGPU_WGSL, "GL reflection")
                    .entryPoints(descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint());
        }
        if (ShaderModuleDescriptors.computeOnly(descriptor.reflection())) {
            capabilities.require(GraphicsFeature.COMPUTE);
            attachment.makeCurrent();
            return new GLComputeModule(providerId, gl, resourceDomain, descriptor);
        }
        descriptor = ShaderModuleDescriptors.requireTarget(descriptor, ShaderTarget.forProvider(providerId), "GL");
        if (descriptor.targetArtifact() != null) {
            shaderTargetSupport().require(descriptor.targetArtifact());
        }
        if (!descriptor.hasSource(ShaderLanguage.GLSL)) {
            throw new FdxException("GL currently supports GLSL shader modules only");
        }
        attachment.makeCurrent();
        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileShader(GLShaderType.VERTEX, descriptor.glslVertexSource(),
                    descriptor.label() + " vertex");
            fragmentShader = compileShader(GLShaderType.FRAGMENT, descriptor.glslFragmentSource(),
                    descriptor.label() + " fragment");
            program = linkProgram(vertexShader, fragmentShader, descriptor.label());
            GLShaderModuleHandle handle = new GLShaderModuleHandle(providerId, gl, resourceDomain, program,
                    descriptor.reflection(), descriptor.targetArtifact() != null
                    ? descriptor.targetArtifact().translatedInterface() : null,
                    descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint());
            gl.deleteShader(vertexShader);
            vertexShader = 0;
            gl.deleteShader(fragmentShader);
            fragmentShader = 0;
            return handle;
        }
        catch (RuntimeException | Error failure) {
            rollbackProgram(program, vertexShader, fragmentShader, failure);
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
        GLShaderModuleHandle shaderModule = GLResources.requireShaderModule(descriptor.shaderModule(), resourceDomain,
                "Render pipeline shader module");
        shaderModule.requireEntryPoints(descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint());
        descriptor.validate(capabilities());
        if (descriptor.renderTargetLayout().colorAttachmentCount() != 1 && !gl.supportsMultipleTargets()) {
            throw new FdxException("GL currently requires exactly one color attachment");
        }
        ShaderRenderBindings resourceBindings = ShaderRenderBindings.from(descriptor);
        attachment.makeCurrent();
        bindUniformBlock(shaderModule, resourceBindings);
        int uniformBuffer = createUniformBuffer(resourceBindings);
        try {
            GLRenderPipelineHandle pipeline = new GLRenderPipelineHandle(providerId, gl, resourceDomain, shaderModule,
                    descriptor.primitiveTopology(), descriptor.vertexLayouts(), descriptor.sampledTextureCount(),
                    descriptor.depthTestEnabled(), descriptor.depthWriteEnabled(),
                    descriptor.colorTargets()[0].blend() != null, uniformBuffer,
                    resourceBindings, descriptor.renderTargetLayout());
            pipeline.captureState(descriptor);
            return pipeline;
        }
        catch (RuntimeException | Error failure) {
            rollbackGeneratedBuffer(uniformBuffer, failure);
            throw failure;
        }
    }

    @Override public ComputePipeline createComputePipeline(
            ComputePipelineDescriptor descriptor) {
        resourceDomain.requireUsable();
        if (descriptor == null) throw new FdxException("Compute pipeline descriptor cannot be null");
        if (!(descriptor.shaderModule() instanceof GLComputeModule module) || module.domain != resourceDomain) {
            throw new FdxException("GL compute shader belongs to another device or is not a compute module");
        }
        descriptor.validate(capabilities);
        return new GLComputePipeline(resourceDomain, module, descriptor);
    }

    @Override public ByteBuffer readBuffer(Buffer buffer, int offset, int size) {
        capabilities.require(GraphicsFeature.COMPUTE);
        GLBufferHandle source = GLResources.requireBuffer(buffer, resourceDomain, "Readback buffer");
        if (source.usage() != BufferUsage.READBACK || offset < 0 || size < 0 || offset > source.size() - size) {
            throw new FdxException("GL readback requires a valid range in a readback buffer");
        }
        attachment.makeCurrent();
        ByteBuffer result = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        gl.readBuffer(source.buffer(), offset, result);
        return result;
    }

    int compileShader(GLShaderType type, String source, String label) {
        int shader = gl.createShader(type);
        try {
            gl.shaderSource(shader, normalizeGlslSource(source));
            gl.compileShader(shader);
            if (!gl.shaderCompileStatus(shader)) {
                String log = gl.shaderInfoLog(shader);
                throw new FdxException("Could not compile GL shader " + label + ": " + log);
            }
            return shader;
        }
        catch (RuntimeException | Error failure) {
            rollbackShader(shader, failure);
            throw failure;
        }
    }

    int linkProgram(int vertexShader, int fragmentShader, String label) {
        int program = gl.createProgram();
        try {
            gl.attachShader(program, vertexShader);
            gl.attachShader(program, fragmentShader);
            gl.linkProgram(program);
            if (!gl.programLinkStatus(program)) {
                String log = gl.programInfoLog(program);
                throw new FdxException("Could not link GL shader module " + label + ": " + log);
            }
            return program;
        }
        catch (RuntimeException | Error failure) {
            rollbackProgram(program, 0, 0, failure);
            throw failure;
        }
    }

    private int createUniformBuffer(ShaderRenderBindings bindings) {
        if (!bindings.hasUniformBuffer()) {
            return 0;
        }
        int buffer = gl.genBuffer();
        try {
            gl.bindUniformBuffer(buffer);
            gl.uniformBufferData(bindings.uniformByteCount());
            gl.bindUniformBuffer(0);
            return buffer;
        }
        catch (RuntimeException | Error failure) {
            tryCleanupUniformBinding(failure);
            rollbackGeneratedBuffer(buffer, failure);
            throw failure;
        }
    }

    private void rollbackBuffer(int buffer, BufferUsage usage, Throwable failure) {
        try {
            if (usage == BufferUsage.INDEX) {
                gl.bindElementArrayBuffer(0);
            }
            else {
                gl.bindArrayBuffer(0);
            }
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        rollbackGeneratedBuffer(buffer, failure);
    }

    private void rollbackTexture(int texture, Throwable failure) {
        try {
            gl.bindTexture2D(0);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        if (texture == 0) {
            return;
        }
        try {
            gl.deleteTexture(texture);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void rollbackProgram(int program, int vertexShader, int fragmentShader, Throwable failure) {
        if (program != 0) {
            try {
                gl.deleteProgram(program);
            }
            catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        rollbackShader(vertexShader, failure);
        rollbackShader(fragmentShader, failure);
    }

    private void rollbackShader(int shader, Throwable failure) {
        if (shader == 0) {
            return;
        }
        try {
            gl.deleteShader(shader);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void tryCleanupUniformBinding(Throwable failure) {
        try {
            gl.bindUniformBuffer(0);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void rollbackGeneratedBuffer(int buffer, Throwable failure) {
        if (buffer == 0) {
            return;
        }
        try {
            gl.deleteBuffer(buffer);
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void bindUniformBlock(int program, String name, int binding) {
        int blockIndex = gl.uniformBlockIndex(program, name);
        if (blockIndex >= 0) {
            gl.uniformBlockBinding(program, blockIndex, binding);
        }
    }

    private void bindUniformBlock(GLShaderModuleHandle module, ShaderRenderBindings bindings) {
        if (!bindings.hasUniformBuffer()) {
            return;
        }
        ShaderBinding uniform = bindings.uniformBuffer();
        boolean mapped = false;
        if (module.translatedInterface() != null) {
            for (ShaderBindingRemap remap : module.translatedInterface().bindings()) {
                if (remap.sourceGroup() != uniform.group()
                        || remap.sourceBinding() != uniform.binding()) {
                    continue;
                }
                for (ShaderTargetBinding target : remap.targets()) {
                    if (!target.name().isEmpty()) {
                        bindUniformBlock(module.program(), target.name(), 0);
                        mapped = true;
                    }
                }
            }
        }
        if (!mapped) {
            bindUniformBlock(module.program(), uniform.name(), 0);
            bindUniformBlock(module.program(), "v_" + uniform.name() + "_block_ubo", 0);
            bindUniformBlock(module.program(), "f_" + uniform.name() + "_block_ubo", 0);
        }
    }

    @Override
    public GraphicsCapabilities capabilities() {
        return capabilities;
    }

    static String normalizeGlslSource(String source) {
        String actualSource = source != null ? source : "";
        actualSource = actualSource.replaceAll("layout\\(binding\\s*=\\s*[0-9]+\\s*,\\s*std140\\)", "layout(std140)");
        // Cameras already produce GL clip coordinates. Undo Tint's WebGPU coordinate
        // conversion regardless of the user's name for the position builtin field.
        return actualSource.replaceAll(
                "gl_Position\\s*=\\s*vec4\\(\\s*([A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*)\\.x\\s*,\\s*-\\s*\\(\\s*\\1\\.y\\s*\\)\\s*,\\s*\\(\\(2\\.0f?\\s*\\*\\s*\\1\\.z\\s*\\)\\s*-\\s*\\1\\.w\\s*\\)\\s*,\\s*\\1\\.w\\s*\\)\\s*;",
                "gl_Position = $1;");
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return providerId;
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
        return (T) this;
    }
}
