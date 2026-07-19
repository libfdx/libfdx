package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

final class D3D12CommandEncoder implements CommandEncoder {
    private final D3D12Context context;
    private final D3D12RenderPass renderPass;
    private boolean passActive;

    D3D12CommandEncoder(D3D12Context context) {
        this.context = context;
        renderPass = new D3D12RenderPass(context, this);
    }

    void beginFrame() {
        requireEnded();
        passActive = false;
    }

    void requireEnded() {
        if (passActive) {
            throw new FdxException("Direct3D 12 render pass must be ended before ending the frame");
        }
    }

    void ended() {
        passActive = false;
    }

    @Override
    public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("RenderPassDescriptor cannot be null");
        }
        context.requireFrame("begin a render pass");
        if (passActive) {
            throw new FdxException("Previous Direct3D 12 render pass must be ended first");
        }
        D3D12TextureView attachment = context.requireTextureView(descriptor.colorAttachment(), "Color attachment");
        LoadOp load = descriptor.colorLoadOp();
        StoreOp store = descriptor.colorStoreOp();
        D3D12Native.beginRenderPass(context.nativeHandle(), attachment.nativeHandle(), load.isClear(),
                load.red(), load.green(), load.blue(), load.alpha(), store.isStore(), descriptor.depthEnabled(),
                descriptor.depthClearEnabled(), descriptor.depthClearValue());
        passActive = true;
        renderPass.begin(attachment);
        return renderPass;
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)Long.valueOf(context.nativeHandle());
    }
}

final class D3D12RenderPass implements RenderPass {
    private static final int MATRIX_FLOAT_COUNT = 16;
    private static final int MODEL_OFFSET = 0;
    private static final int VIEW_PROJECTION_OFFSET = 16;
    private static final int CAMERA_POSITION_OFFSET = 32;
    private static final int CAMERA_DIRECTION_OFFSET = 36;
    private static final int AMBIENT_COLOR_OFFSET = 40;
    private static final int LIGHT_DIRECTION_OFFSET = 44;
    private static final int LIGHT_COLOR_INTENSITY_OFFSET = 48;
    private static final int TEXTURE_FLAGS_OFFSET = 52;
    private static final int EMISSIVE_FLAGS_OFFSET = 56;
    private static final int FOG_COLOR_OFFSET = 60;
    private static final int FOG_PARAMS_OFFSET = 64;
    private static final int SKY_ZENITH_COLOR_OFFSET = 68;
    private static final int SKY_HORIZON_COLOR_OFFSET = 72;
    private static final int SKY_NADIR_COLOR_OFFSET = 76;
    private static final int SKY_SUN_COLOR_OFFSET = 80;
    private static final int SKY_SUN_DIRECTION_OFFSET = 84;
    private static final int SKY_PARAMS_OFFSET = 88;
    private static final int MAX_POINT_LIGHTS = 4;
    private static final int POINT_LIGHT_COUNT_OFFSET = 92;
    private static final int POINT_LIGHT_POSITIONS_OFFSET = POINT_LIGHT_COUNT_OFFSET + 4;
    private static final int POINT_LIGHT_COLORS_OFFSET = POINT_LIGHT_POSITIONS_OFFSET + MAX_POINT_LIGHTS * 4;
    private static final int MAX_SPOT_LIGHTS = 4;
    private static final int SPOT_LIGHT_COUNT_OFFSET = POINT_LIGHT_COLORS_OFFSET + MAX_POINT_LIGHTS * 4;
    private static final int SPOT_LIGHT_POSITIONS_OFFSET = SPOT_LIGHT_COUNT_OFFSET + 4;
    private static final int SPOT_LIGHT_DIRECTIONS_OFFSET = SPOT_LIGHT_POSITIONS_OFFSET + MAX_SPOT_LIGHTS * 4;
    private static final int SPOT_LIGHT_COLORS_OFFSET = SPOT_LIGHT_DIRECTIONS_OFFSET + MAX_SPOT_LIGHTS * 4;
    private static final int SPOT_LIGHT_CONES_OFFSET = SPOT_LIGHT_COLORS_OFFSET + MAX_SPOT_LIGHTS * 4;
    private static final int MAX_SHADOW_CASCADES = 4;
    private static final int SHADOW_VIEW_PROJECTIONS_OFFSET = SPOT_LIGHT_CONES_OFFSET + MAX_SPOT_LIGHTS * 4;
    private static final int SHADOW_PARAMS_OFFSET = SHADOW_VIEW_PROJECTIONS_OFFSET
            + MAX_SHADOW_CASCADES * MATRIX_FLOAT_COUNT;
    private static final int SHADOW_CASCADE_SPLITS_OFFSET = SHADOW_PARAMS_OFFSET + 4;
    private static final int SHADOW_BIASES_OFFSET = SHADOW_CASCADE_SPLITS_OFFSET + 4;
    private static final int SHADOW_CAMERA_POSITION_OFFSET = SHADOW_BIASES_OFFSET + 4;
    private static final int SHADOW_CAMERA_DIRECTION_OFFSET = SHADOW_CAMERA_POSITION_OFFSET + 4;
    private static final int SHADOW_CAMERA_UP_OFFSET = SHADOW_CAMERA_DIRECTION_OFFSET + 4;
    private static final int SHADOW_CAMERA_PARAMS_OFFSET = SHADOW_CAMERA_UP_OFFSET + 4;
    private static final int SKINNING_PARAMS_OFFSET = SHADOW_CAMERA_PARAMS_OFFSET + 4;
    private static final int MAX_BONES = 64;
    private static final int BONE_MATRICES_OFFSET = SKINNING_PARAMS_OFFSET + 4;
    private static final int PBR_UNIFORM_BYTE_COUNT = 5232;

    private final D3D12Context context;
    private final D3D12CommandEncoder encoder;
    private final ByteBuffer uniformBytes = ByteBuffer.allocateDirect(PBR_UNIFORM_BYTE_COUNT)
            .order(ByteOrder.nativeOrder());
    private final MemorySegment uniformMemory = MemorySegment.ofBuffer(uniformBytes);
    private final FloatBuffer uniformFloats = uniformBytes.asFloatBuffer();
    private D3D12TextureView colorAttachment;
    private D3D12Pipeline pipeline;
    private D3D12Buffer indexBuffer;
    private D3D12Buffer[] vertexBuffers = new D3D12Buffer[4];
    private D3D12Texture[] textures = new D3D12Texture[0];
    private boolean hasUniformData;
    private boolean ended = true;

    D3D12RenderPass(D3D12Context context, D3D12CommandEncoder encoder) {
        this.context = context;
        this.encoder = encoder;
    }

    void begin(D3D12TextureView colorAttachment) {
        if (!ended) {
            throw new FdxException("Cannot reuse an active Direct3D 12 render pass");
        }
        this.colorAttachment = colorAttachment;
        pipeline = null;
        indexBuffer = null;
        Arrays.fill(vertexBuffers, null);
        Arrays.fill(textures, null);
        hasUniformData = false;
        resetUniformData();
        ended = false;
    }

    @Override
    public void setPipeline(RenderPipeline pipeline) {
        ensureOpen();
        this.pipeline = context.requirePipeline(pipeline, "Render pipeline");
        prepareTextureSlots(this.pipeline.sampledTextureCount());
        D3D12Native.setPipeline(context.nativeHandle(), this.pipeline.nativeHandle());
    }

    @Override
    public void setVertexBuffer(Buffer buffer) {
        setVertexBuffer(0, buffer);
    }

    @Override
    public void setVertexBuffer(int slot, Buffer buffer) {
        ensureOpen();
        if (slot < 0) {
            throw new FdxException("Vertex buffer slot cannot be negative");
        }
        D3D12Buffer target = context.requireBuffer(buffer, "Vertex buffer");
        if (target.usage() != BufferUsage.VERTEX) {
            throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
        }
        if (slot >= vertexBuffers.length) {
            vertexBuffers = Arrays.copyOf(vertexBuffers, Math.max(slot + 1, vertexBuffers.length * 2));
        }
        vertexBuffers[slot] = target;
        D3D12Native.setVertexBuffer(context.nativeHandle(), slot, target.nativeHandle());
    }

    @Override
    public void setIndexBuffer(Buffer buffer) {
        ensureOpen();
        indexBuffer = context.requireBuffer(buffer, "Index buffer");
        if (indexBuffer.usage() != BufferUsage.INDEX) {
            throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
        }
        D3D12Native.setIndexBuffer(context.nativeHandle(), indexBuffer.nativeHandle());
    }

    @Override
    public void setTexture(int slot, Texture texture) {
        ensureOpen();
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before binding a texture");
        }
        if (slot < 0 || slot >= pipeline.sampledTextureCount()) {
            throw new FdxException("Texture slot is not declared by the active Direct3D 12 pipeline: " + slot);
        }
        textures[slot] = context.requireTexture(texture, "Texture");
    }

    @Override
    public void setScissor(int x, int y, int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new FdxException("Scissor size must be greater than zero");
        }
        D3D12Native.setScissor(context.nativeHandle(), x, y, width, height);
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new FdxException("Viewport size must be greater than zero");
        }
        D3D12Native.setViewport(context.nativeHandle(), x, y, width, height);
    }

    @Override
    public void setUniform1i(String name, int value) {
        if ("u_hasBaseColorTexture".equals(name)) {
            setUniformFloat(TEXTURE_FLAGS_OFFSET, value);
        }
        else if ("u_hasMetallicRoughnessTexture".equals(name)) {
            setUniformFloat(TEXTURE_FLAGS_OFFSET + 1, value);
        }
        else if ("u_hasNormalTexture".equals(name)) {
            setUniformFloat(TEXTURE_FLAGS_OFFSET + 2, value);
        }
        else if ("u_hasOcclusionTexture".equals(name)) {
            setUniformFloat(TEXTURE_FLAGS_OFFSET + 3, value);
        }
        else if ("u_hasEmissiveTexture".equals(name)) {
            setUniformFloat(EMISSIVE_FLAGS_OFFSET, value);
        }
    }

    @Override
    public void setUniform1f(String name, float value) {
        if ("u_lightIntensity".equals(name)) {
            setUniformFloat(LIGHT_COLOR_INTENSITY_OFFSET + 3, value);
        }
        else if ("u_pointLightCount".equals(name)) {
            setUniformFloat(POINT_LIGHT_COUNT_OFFSET, value);
        }
        else if ("u_spotLightCount".equals(name)) {
            setUniformFloat(SPOT_LIGHT_COUNT_OFFSET, value);
        }
    }

    @Override
    public void setUniform3f(String name, float x, float y, float z) {
        if ("u_cameraPosition".equals(name)) {
            setUniform4f(CAMERA_POSITION_OFFSET, x, y, z, 1.0f);
        }
        else if ("u_cameraDirection".equals(name)) {
            setUniform4f(CAMERA_DIRECTION_OFFSET, x, y, z, 0.0f);
        }
        else if ("u_ambientColor".equals(name)) {
            setUniform4f(AMBIENT_COLOR_OFFSET, x, y, z, 1.0f);
        }
        else if ("u_lightDirection".equals(name)) {
            setUniform4f(LIGHT_DIRECTION_OFFSET, x, y, z, 0.0f);
        }
        else if ("u_lightColor".equals(name)) {
            setUniform4f(LIGHT_COLOR_INTENSITY_OFFSET, x, y, z,
                    uniformFloats.get(LIGHT_COLOR_INTENSITY_OFFSET + 3));
        }
        else if ("u_fogColor".equals(name)) {
            setUniform4f(FOG_COLOR_OFFSET, x, y, z, 1.0f);
        }
        else if ("u_skyZenithColor".equals(name)) {
            setUniform4f(SKY_ZENITH_COLOR_OFFSET, x, y, z, 1.0f);
        }
        else if ("u_skyHorizonColor".equals(name)) {
            setUniform4f(SKY_HORIZON_COLOR_OFFSET, x, y, z, 1.0f);
        }
        else if ("u_skyNadirColor".equals(name)) {
            setUniform4f(SKY_NADIR_COLOR_OFFSET, x, y, z, 1.0f);
        }
        else if ("u_skySunColor".equals(name)) {
            setUniform4f(SKY_SUN_COLOR_OFFSET, x, y, z, uniformFloats.get(SKY_SUN_COLOR_OFFSET + 3));
        }
        else if ("u_skySunDirection".equals(name)) {
            setUniform4f(SKY_SUN_DIRECTION_OFFSET, x, y, z, 0.0f);
        }
    }

    @Override
    public void setUniform4f(String name, float x, float y, float z, float w) {
        if ("u_cameraPosition".equals(name)) {
            setUniform4f(CAMERA_POSITION_OFFSET, x, y, z, w);
        }
        else if ("u_cameraDirection".equals(name)) {
            setUniform4f(CAMERA_DIRECTION_OFFSET, x, y, z, w);
        }
        else if ("u_ambientColor".equals(name)) {
            setUniform4f(AMBIENT_COLOR_OFFSET, x, y, z, w);
        }
        else if ("u_lightDirection".equals(name)) {
            setUniform4f(LIGHT_DIRECTION_OFFSET, x, y, z, w);
        }
        else if ("u_lightColor".equals(name)) {
            setUniform4f(LIGHT_COLOR_INTENSITY_OFFSET, x, y, z, w);
        }
        else if ("u_fogColor".equals(name)) {
            setUniform4f(FOG_COLOR_OFFSET, x, y, z, w);
        }
        else if ("u_fogParams".equals(name)) {
            setUniform4f(FOG_PARAMS_OFFSET, x, y, z, w);
        }
        else if ("u_skyZenithColor".equals(name)) {
            setUniform4f(SKY_ZENITH_COLOR_OFFSET, x, y, z, w);
        }
        else if ("u_skyHorizonColor".equals(name)) {
            setUniform4f(SKY_HORIZON_COLOR_OFFSET, x, y, z, w);
        }
        else if ("u_skyNadirColor".equals(name)) {
            setUniform4f(SKY_NADIR_COLOR_OFFSET, x, y, z, w);
        }
        else if ("u_skySunColor".equals(name)) {
            setUniform4f(SKY_SUN_COLOR_OFFSET, x, y, z, w);
        }
        else if ("u_skySunDirection".equals(name)) {
            setUniform4f(SKY_SUN_DIRECTION_OFFSET, x, y, z, w);
        }
        else if ("u_skyParams".equals(name)) {
            setUniform4f(SKY_PARAMS_OFFSET, x, y, z, w);
        }
        else {
            int index = lightIndex(name, "u_pointLight", "PositionRange", MAX_POINT_LIGHTS);
            if (index >= 0) {
                setUniform4f(POINT_LIGHT_POSITIONS_OFFSET + index * 4, x, y, z, w);
                return;
            }
            index = lightIndex(name, "u_pointLight", "ColorIntensity", MAX_POINT_LIGHTS);
            if (index >= 0) {
                setUniform4f(POINT_LIGHT_COLORS_OFFSET + index * 4, x, y, z, w);
                return;
            }
            index = lightIndex(name, "u_spotLight", "PositionRange", MAX_SPOT_LIGHTS);
            if (index >= 0) {
                setUniform4f(SPOT_LIGHT_POSITIONS_OFFSET + index * 4, x, y, z, w);
                return;
            }
            index = lightIndex(name, "u_spotLight", "DirectionInner", MAX_SPOT_LIGHTS);
            if (index >= 0) {
                setUniform4f(SPOT_LIGHT_DIRECTIONS_OFFSET + index * 4, x, y, z, w);
                return;
            }
            index = lightIndex(name, "u_spotLight", "ColorIntensity", MAX_SPOT_LIGHTS);
            if (index >= 0) {
                setUniform4f(SPOT_LIGHT_COLORS_OFFSET + index * 4, x, y, z, w);
                return;
            }
            index = lightIndex(name, "u_spotLight", "Cone", MAX_SPOT_LIGHTS);
            if (index >= 0) {
                setUniform4f(SPOT_LIGHT_CONES_OFFSET + index * 4, x, y, z, w);
                return;
            }
            if ("u_shadowParams".equals(name)) {
                setUniform4f(SHADOW_PARAMS_OFFSET, x, y, z, w);
            }
            else if ("u_shadowCascadeSplits".equals(name)) {
                setUniform4f(SHADOW_CASCADE_SPLITS_OFFSET, x, y, z, w);
            }
            else if ("u_shadowBiases".equals(name)) {
                setUniform4f(SHADOW_BIASES_OFFSET, x, y, z, w);
            }
            else if ("u_shadowCameraPosition".equals(name)) {
                setUniform4f(SHADOW_CAMERA_POSITION_OFFSET, x, y, z, w);
            }
            else if ("u_shadowCameraDirection".equals(name)) {
                setUniform4f(SHADOW_CAMERA_DIRECTION_OFFSET, x, y, z, w);
            }
            else if ("u_shadowCameraUp".equals(name)) {
                setUniform4f(SHADOW_CAMERA_UP_OFFSET, x, y, z, w);
            }
            else if ("u_shadowCameraParams".equals(name)) {
                setUniform4f(SHADOW_CAMERA_PARAMS_OFFSET, x, y, z, w);
            }
            else if ("u_skinningParams".equals(name)) {
                setUniform4f(SKINNING_PARAMS_OFFSET, x, y, z, w);
            }
        }
    }

    @Override
    public void setUniformMatrix4(String name, float[] values) {
        ensureOpen();
        if (values == null || values.length < MATRIX_FLOAT_COUNT) {
            throw new FdxException("Matrix uniform requires 16 float values");
        }
        if ("u_model".equals(name)) {
            setUniformMatrix(MODEL_OFFSET, values);
        }
        else if ("u_viewProjection".equals(name)) {
            setUniformMatrix(VIEW_PROJECTION_OFFSET, values);
        }
        else {
            int index = shadowViewProjectionIndex(name);
            if (index >= 0) {
                setUniformMatrix(SHADOW_VIEW_PROJECTIONS_OFFSET + index * MATRIX_FLOAT_COUNT, values);
                return;
            }
            index = boneMatrixIndex(name);
            if (index >= 0) {
                setUniformMatrix(BONE_MATRICES_OFFSET + index * MATRIX_FLOAT_COUNT, values);
            }
        }
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        ensureOpen();
        validateBoundResources(false);
        bindTextures();
        bindUniforms();
        D3D12Native.draw(context.nativeHandle(), vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
        ensureOpen();
        validateBoundResources(true);
        bindTextures();
        bindUniforms();
        D3D12Native.drawIndexed(context.nativeHandle(), indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    @Override
    public void end() {
        if (ended) {
            return;
        }
        D3D12Native.endRenderPass(context.nativeHandle());
        ended = true;
        encoder.ended();
        pipeline = null;
        indexBuffer = null;
        colorAttachment = null;
        Arrays.fill(vertexBuffers, null);
        Arrays.fill(textures, null);
    }

    private void validateBoundResources(boolean indexed) {
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before drawing");
        }
        context.requirePipeline(pipeline, "Render pipeline");
        if (indexed && indexBuffer == null) {
            throw new FdxException("Index buffer must be set before drawIndexed");
        }
    }

    private void bindTextures() {
        if (pipeline.sampledTextureCount() == 0) {
            return;
        }
        for (int i = 0; i < pipeline.sampledTextureCount(); i++) {
            D3D12Texture texture = textures[i];
            if (texture == null || texture.isDisposed()) {
                throw new FdxException("Texture slot " + i + " must be set before drawing with Direct3D 12");
            }
            D3D12Native.setTexture(context.nativeHandle(), i, texture.nativeHandle());
        }
    }

    private void bindUniforms() {
        if (!pipeline.uniformBufferEnabled()) {
            return;
        }
        if (!hasUniformData) {
            throw new FdxException("Direct3D 12 uniforms must be set before drawing");
        }
        uniformBytes.position(0);
        uniformBytes.limit(PBR_UNIFORM_BYTE_COUNT);
        D3D12Native.bindUniforms(context.nativeHandle(), uniformMemory, PBR_UNIFORM_BYTE_COUNT);
    }

    private void prepareTextureSlots(int count) {
        if (textures.length < count) {
            textures = new D3D12Texture[count];
        }
        Arrays.fill(textures, null);
    }

    private void ensureOpen() {
        if (ended) {
            throw new FdxException("Render pass has already ended");
        }
        context.requireFrame("use a Direct3D 12 render pass");
        if (colorAttachment != null && colorAttachment.texture() != null && colorAttachment.texture().isDisposed()) {
            throw new FdxException("Render target texture has been disposed");
        }
    }

    private void setUniformMatrix(int offset, float[] values) {
        ensureOpen();
        for (int i = 0; i < MATRIX_FLOAT_COUNT; i++) {
            uniformFloats.put(offset + i, values[i]);
        }
        markUniformData();
    }

    private void setUniform4f(int offset, float x, float y, float z, float w) {
        ensureOpen();
        uniformFloats.put(offset, x);
        uniformFloats.put(offset + 1, y);
        uniformFloats.put(offset + 2, z);
        uniformFloats.put(offset + 3, w);
        markUniformData();
    }

    private void setUniformFloat(int offset, float value) {
        ensureOpen();
        uniformFloats.put(offset, value);
        markUniformData();
    }

    private void markUniformData() {
        hasUniformData = true;
    }

    private int lightIndex(String name, String prefix, String suffix, int maxLights) {
        if (name == null || !name.startsWith(prefix) || !name.endsWith(suffix)) {
            return -1;
        }
        int digitOffset = prefix.length();
        int digitEnd = name.length() - suffix.length();
        if (digitEnd != digitOffset + 1) {
            return -1;
        }
        int index = name.charAt(digitOffset) - '0';
        return index >= 0 && index < maxLights ? index : -1;
    }

    private int boneMatrixIndex(String name) {
        if (name == null || !name.startsWith("u_bone")) {
            return -1;
        }
        int index = 0;
        for (int i = 6; i < name.length(); i++) {
            char value = name.charAt(i);
            if (value < '0' || value > '9') {
                return -1;
            }
            index = index * 10 + value - '0';
        }
        return index >= 0 && index < MAX_BONES ? index : -1;
    }

    private int shadowViewProjectionIndex(String name) {
        if ("u_shadowViewProjection".equals(name)) {
            return 0;
        }
        String prefix = "u_shadowViewProjection";
        if (name == null || !name.startsWith(prefix) || name.length() != prefix.length() + 1) {
            return -1;
        }
        int index = name.charAt(prefix.length()) - '0';
        return index >= 0 && index < MAX_SHADOW_CASCADES ? index : -1;
    }

    private void resetUniformData() {
        for (int i = 0; i < PBR_UNIFORM_BYTE_COUNT / 4; i++) {
            uniformFloats.put(i, 0.0f);
        }
        uniformFloats.put(MODEL_OFFSET, 1.0f);
        uniformFloats.put(MODEL_OFFSET + 5, 1.0f);
        uniformFloats.put(MODEL_OFFSET + 10, 1.0f);
        uniformFloats.put(MODEL_OFFSET + 15, 1.0f);
        uniformFloats.put(VIEW_PROJECTION_OFFSET, 1.0f);
        uniformFloats.put(VIEW_PROJECTION_OFFSET + 5, 1.0f);
        uniformFloats.put(VIEW_PROJECTION_OFFSET + 10, 1.0f);
        uniformFloats.put(VIEW_PROJECTION_OFFSET + 15, 1.0f);
    }

    @Override
    public ProviderId providerId() {
        return D3D12Provider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T)this;
    }
}
