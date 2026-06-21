package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroupDescriptor;
import com.github.xpenatan.webgpu.WGPUBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUBindGroup;
import com.github.xpenatan.webgpu.WGPUBuffer;
import com.github.xpenatan.webgpu.WGPUBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPURenderPassEncoder;
import com.github.xpenatan.webgpu.WGPUIndexFormat;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupEntry;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.Texture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Represents a WGPU render pass.
 *
 * @author xpenatan
 */
final class WGPURenderPass implements RenderPass {
    static final int PBR_UNIFORM_BYTE_COUNT = 5232;
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

    private final WGPUContext context;
    private final WGPURenderPassEncoder nativePass;
    private final ByteBuffer uniformBytes = ByteBuffer.allocateDirect(PBR_UNIFORM_BYTE_COUNT)
            .order(ByteOrder.nativeOrder());
    private final FloatBuffer uniformFloats = uniformBytes.asFloatBuffer();
    private WGPURenderPipelineHandle pipeline;
    private WGPUTextureHandle[] textures = new WGPUTextureHandle[0];
    private WGPUBindGroup activeTextureBindGroup;
    private WGPUBuffer uniformBuffer;
    private WGPUBindGroup uniformBindGroup;
    private boolean textureBindGroupDirty;
    private boolean uniformDataDirty = true;
    private boolean uniformBufferUsedByRecordedCommand;
    private boolean hasUniformData;
    private boolean ended;

    WGPURenderPass(WGPUContext context, WGPURenderPassEncoder nativePass) {
        this.context = context;
        this.nativePass = nativePass;
        resetUniformData();
    }

    /**
     * Sets the pipeline.
     *
     * @param pipeline the pipeline
     */
    @Override
    public void setPipeline(RenderPipeline pipeline) {
        ensureOpen();
        this.pipeline = pipeline.as();
        ensureTextureSlots(this.pipeline.sampledTextureCount());
        releaseActiveTextureBindGroup();
        releaseUniformBindGroup();
        textureBindGroupDirty = this.pipeline.sampledTextureCount() > 0;
        nativePass.setPipeline(this.pipeline.nativePipeline());
    }

    /**
     * Sets the vertex buffer.
     *
     * @param buffer the buffer
     */
    @Override
    public void setVertexBuffer(Buffer buffer) {
        setVertexBuffer(0, buffer);
    }

    /**
     * Sets the vertex buffer.
     *
     * @param slot the slot
     * @param buffer the buffer
     */
    @Override
    public void setVertexBuffer(int slot, Buffer buffer) {
        ensureOpen();
        if (buffer == null) {
            throw new FdxException("Vertex buffer cannot be null");
        }
        WGPUBufferHandle wgpuBuffer = buffer.as();
        if (wgpuBuffer.usage() != BufferUsage.VERTEX) {
            throw new FdxException("RenderPass.setVertexBuffer requires a vertex buffer");
        }
        nativePass.setVertexBuffer(slot, wgpuBuffer.nativeBuffer(), 0, wgpuBuffer.size());
        context.markBufferUsedByRecordedCommand(wgpuBuffer);
    }

    /**
     * Sets the index buffer.
     *
     * @param buffer the buffer
     */
    @Override
    public void setIndexBuffer(Buffer buffer) {
        ensureOpen();
        if (buffer == null) {
            throw new FdxException("Index buffer cannot be null");
        }
        WGPUBufferHandle wgpuBuffer = buffer.as();
        if (wgpuBuffer.usage() != BufferUsage.INDEX) {
            throw new FdxException("RenderPass.setIndexBuffer requires an index buffer");
        }
        nativePass.setIndexBuffer(wgpuBuffer.nativeBuffer(), WGPUIndexFormat.Uint16, 0, wgpuBuffer.size());
        context.markBufferUsedByRecordedCommand(wgpuBuffer);
    }

    /**
     * Sets the texture.
     *
     * @param slot the slot
     * @param texture the texture
     */
    @Override
    public void setTexture(int slot, Texture texture) {
        ensureOpen();
        if (pipeline == null || pipeline.textureBindGroupLayout() == null) {
            throw new FdxException("Current WGPU pipeline does not accept textures");
        }
        if (slot < 0 || slot >= pipeline.sampledTextureCount()) {
            throw new FdxException("WGPU texture slot is outside the current pipeline texture range");
        }
        if (texture == null) {
            throw new FdxException("Texture cannot be null");
        }
        WGPUTextureHandle wgpuTexture = texture.as();
        if (!wgpuTexture.usage().sampled()) {
            throw new FdxException("Texture was not created with sampled usage");
        }
        if (textures[slot] != wgpuTexture) {
            textures[slot] = wgpuTexture;
            textureBindGroupDirty = true;
        }
    }

    /**
     * Sets the scissor.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void setScissor(int x, int y, int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new FdxException("Scissor size must be greater than zero");
        }
        nativePass.setScissorRect(x, y, width, height);
    }

    /**
     * Sets the viewport.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void setViewport(int x, int y, int width, int height) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new FdxException("Viewport size must be greater than zero");
        }
        nativePass.setViewport(x, y, width, height, 0.0f, 1.0f);
    }

    /**
     * Sets the uniform1i.
     *
     * @param name the name
     * @param value the value
     */
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

    /**
     * Sets the uniform1f.
     *
     * @param name the name
     * @param value the value
     */
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

    /**
     * Sets the uniform3f.
     *
     * @param name the name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
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

    /**
     * Sets the uniform4f.
     *
     * @param name the name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     */
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
            int positionIndex = pointLightIndex(name, "PositionRange");
            if (positionIndex >= 0) {
                setUniform4f(POINT_LIGHT_POSITIONS_OFFSET + positionIndex * 4, x, y, z, w);
                return;
            }
            int colorIndex = pointLightIndex(name, "ColorIntensity");
            if (colorIndex >= 0) {
                setUniform4f(POINT_LIGHT_COLORS_OFFSET + colorIndex * 4, x, y, z, w);
                return;
            }
            int spotPositionIndex = spotLightIndex(name, "PositionRange");
            if (spotPositionIndex >= 0) {
                setUniform4f(SPOT_LIGHT_POSITIONS_OFFSET + spotPositionIndex * 4, x, y, z, w);
                return;
            }
            int spotDirectionIndex = spotLightIndex(name, "DirectionInner");
            if (spotDirectionIndex >= 0) {
                setUniform4f(SPOT_LIGHT_DIRECTIONS_OFFSET + spotDirectionIndex * 4, x, y, z, w);
                return;
            }
            int spotColorIndex = spotLightIndex(name, "ColorIntensity");
            if (spotColorIndex >= 0) {
                setUniform4f(SPOT_LIGHT_COLORS_OFFSET + spotColorIndex * 4, x, y, z, w);
                return;
            }
            int spotConeIndex = spotLightIndex(name, "Cone");
            if (spotConeIndex >= 0) {
                setUniform4f(SPOT_LIGHT_CONES_OFFSET + spotConeIndex * 4, x, y, z, w);
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

    /**
     * Sets the uniform matrix4.
     *
     * @param name the name
     * @param values the values
     */
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
            int shadowIndex = shadowViewProjectionIndex(name);
            if (shadowIndex >= 0) {
                setUniformMatrix(SHADOW_VIEW_PROJECTIONS_OFFSET + shadowIndex * MATRIX_FLOAT_COUNT, values);
                return;
            }
            int boneIndex = boneMatrixIndex(name);
            if (boneIndex >= 0) {
                setUniformMatrix(BONE_MATRICES_OFFSET + boneIndex * MATRIX_FLOAT_COUNT, values);
            }
        }
    }

    /**
     * Draws the current content.
     *
     * @param vertexCount the vertex count
     * @param instanceCount the instance count
     * @param firstVertex the first vertex
     * @param firstInstance the first instance
     */
    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        ensureOpen();
        applyBindGroups();
        nativePass.draw(vertexCount, instanceCount, firstVertex, firstInstance);
    }

    /**
     * Draws indexed.
     *
     * @param indexCount the index count
     * @param instanceCount the instance count
     * @param firstIndex the first index
     * @param baseVertex the base vertex
     * @param firstInstance the first instance
     */
    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance) {
        ensureOpen();
        applyBindGroups();
        nativePass.drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    /**
     * Ends the operation.
     */
    @Override
    public void end() {
        if (ended) {
            return;
        }
        ended = true;
        releaseActiveTextureBindGroup();
        releaseUniformBindGroup();
        releaseUniformBuffer();
        nativePass.end();
        nativePass.release();
    }

    private void applyBindGroups() {
        if (pipeline == null) {
            throw new FdxException("Render pipeline must be set before draw");
        }
        applyTextureBindGroup();
        applyUniformBindGroup();
    }

    private void applyTextureBindGroup() {
        int textureCount = pipeline.sampledTextureCount();
        if (textureCount <= 0) {
            return;
        }
        for (int i = 0; i < textureCount; i++) {
            if (textures[i] == null) {
                throw new FdxException("WGPU texture slot " + i + " has not been set");
            }
        }
        if (activeTextureBindGroup == null || textureBindGroupDirty) {
            releaseActiveTextureBindGroup();
            activeTextureBindGroup = createTextureBindGroup(textureCount);
            textureBindGroupDirty = false;
        }
        nativePass.setBindGroup(0, activeTextureBindGroup);
    }

    private WGPUBindGroup createTextureBindGroup(int textureCount) {
        WGPUVectorBindGroupEntry entries = WGPUVectorBindGroupEntry.obtain();
        for (int slot = 0; slot < textureCount; slot++) {
            WGPUTextureHandle texture = textures[slot];

            WGPUBindGroupEntry textureEntry = WGPUBindGroupEntry.obtain();
            textureEntry.setNextInChain(WGPUChainedStruct.NULL);
            textureEntry.setBinding(slot * 2);
            textureEntry.setTextureView(texture.nativeView());
            entries.push_back(textureEntry);

            WGPUBindGroupEntry samplerEntry = WGPUBindGroupEntry.obtain();
            samplerEntry.setNextInChain(WGPUChainedStruct.NULL);
            samplerEntry.setBinding(slot * 2 + 1);
            samplerEntry.setSampler(texture.nativeSampler());
            entries.push_back(samplerEntry);
        }

        WGPUBindGroupDescriptor descriptor = WGPUBindGroupDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx texture bind group");
        descriptor.setLayout(pipeline.textureBindGroupLayout());
        descriptor.setEntries(entries);
        WGPUBindGroup bindGroup = new WGPUBindGroup();
        context.nativeDevice().createBindGroup(descriptor, bindGroup);
        return bindGroup;
    }

    private void applyUniformBindGroup() {
        if (!hasUniformData || pipeline.uniformBindGroupLayout() == null) {
            return;
        }
        if (uniformDataDirty) {
            ensureWritableUniformBuffer();
            uniformBytes.position(0);
            uniformBytes.limit(PBR_UNIFORM_BYTE_COUNT);
            context.nativeQueue().writeBuffer(uniformBuffer, 0, uniformBytes, PBR_UNIFORM_BYTE_COUNT);
            uniformDataDirty = false;
        }
        else {
            ensureUniformBuffer();
        }
        if (uniformBindGroup == null) {
            uniformBindGroup = createUniformBindGroup();
        }
        nativePass.setBindGroup(pipeline.uniformBindGroupIndex(), uniformBindGroup);
        uniformBufferUsedByRecordedCommand = true;
    }

    private void ensureUniformBuffer() {
        if (uniformBuffer != null && uniformBuffer.isValid()) {
            return;
        }
        WGPUBufferDescriptor descriptor = WGPUBufferDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx pbr uniforms");
        descriptor.setSize(PBR_UNIFORM_BYTE_COUNT);
        descriptor.setUsage(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));
        descriptor.setMappedAtCreation(false);
        uniformBuffer = new WGPUBuffer();
        context.nativeDevice().createBuffer(descriptor, uniformBuffer);
        uniformBuffer.native_setAddress(uniformBuffer.native_getAddressLong());
        uniformBufferUsedByRecordedCommand = false;
    }

    private void ensureWritableUniformBuffer() {
        ensureUniformBuffer();
        if (!uniformBufferUsedByRecordedCommand) {
            return;
        }
        releaseUniformBindGroup();
        context.destroyAfterSubmit(uniformBuffer);
        uniformBuffer = null;
        ensureUniformBuffer();
    }

    private WGPUBindGroup createUniformBindGroup() {
        WGPUVectorBindGroupEntry entries = WGPUVectorBindGroupEntry.obtain();
        WGPUBindGroupEntry uniformEntry = WGPUBindGroupEntry.obtain();
        uniformEntry.setNextInChain(WGPUChainedStruct.NULL);
        uniformEntry.setBinding(0);
        uniformEntry.setBuffer(uniformBuffer);
        uniformEntry.setOffset(0);
        uniformEntry.setSize(PBR_UNIFORM_BYTE_COUNT);
        entries.push_back(uniformEntry);

        WGPUBindGroupDescriptor descriptor = WGPUBindGroupDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx pbr uniform bind group");
        descriptor.setLayout(pipeline.uniformBindGroupLayout());
        descriptor.setEntries(entries);
        WGPUBindGroup bindGroup = new WGPUBindGroup();
        context.nativeDevice().createBindGroup(descriptor, bindGroup);
        return bindGroup;
    }

    private void setUniformMatrix(int offset, float[] values) {
        ensureOpen();
        for (int i = 0; i < MATRIX_FLOAT_COUNT; i++) {
            uniformFloats.put(offset + i, values[i]);
        }
        markUniformDirty();
    }

    private void setUniform4f(int offset, float x, float y, float z, float w) {
        ensureOpen();
        uniformFloats.put(offset, x);
        uniformFloats.put(offset + 1, y);
        uniformFloats.put(offset + 2, z);
        uniformFloats.put(offset + 3, w);
        markUniformDirty();
    }

    private void setUniformFloat(int offset, float value) {
        ensureOpen();
        uniformFloats.put(offset, value);
        markUniformDirty();
    }

    private int pointLightIndex(String name, String suffix) {
        return lightIndex(name, "u_pointLight", suffix, MAX_POINT_LIGHTS);
    }

    private int spotLightIndex(String name, String suffix) {
        return lightIndex(name, "u_spotLight", suffix, MAX_SPOT_LIGHTS);
    }

    private int boneMatrixIndex(String name) {
        if (name == null || !name.startsWith("u_bone")) {
            return -1;
        }
        int index = 0;
        for (int i = 6; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch < '0' || ch > '9') {
                return -1;
            }
            index = index * 10 + ch - '0';
        }
        return index >= 0 && index < MAX_BONES ? index : -1;
    }

    private int shadowViewProjectionIndex(String name) {
        if ("u_shadowViewProjection".equals(name)) {
            return 0;
        }
        if (name == null || !name.startsWith("u_shadowViewProjection")) {
            return -1;
        }
        int suffixOffset = "u_shadowViewProjection".length();
        if (name.length() != suffixOffset + 1) {
            return -1;
        }
        int index = name.charAt(suffixOffset) - '0';
        return index >= 0 && index < MAX_SHADOW_CASCADES ? index : -1;
    }

    private int lightIndex(String name, String prefix, String suffix, int maxLights) {
        if (name == null || suffix == null || !name.startsWith(prefix) || !name.endsWith(suffix)) {
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

    private void markUniformDirty() {
        hasUniformData = true;
        uniformDataDirty = true;
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

    private void ensureTextureSlots(int textureCount) {
        if (textureCount <= 0) {
            Arrays.fill(textures, null);
            return;
        }
        if (textures.length != textureCount) {
            textures = new WGPUTextureHandle[textureCount];
            return;
        }
        Arrays.fill(textures, null);
    }

    private void releaseActiveTextureBindGroup() {
        if (activeTextureBindGroup != null) {
            context.releaseAfterSubmit(activeTextureBindGroup);
            activeTextureBindGroup = null;
        }
    }

    private void releaseUniformBindGroup() {
        if (uniformBindGroup != null) {
            context.releaseAfterSubmit(uniformBindGroup);
            uniformBindGroup = null;
        }
    }

    private void releaseUniformBuffer() {
        if (uniformBuffer != null) {
            context.destroyAfterSubmit(uniformBuffer);
            uniformBuffer = null;
        }
        uniformBufferUsedByRecordedCommand = false;
    }

    private void ensureOpen() {
        if (ended) {
            throw new FdxException("Render pass has already ended");
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
        return (T) nativePass;
    }
}
