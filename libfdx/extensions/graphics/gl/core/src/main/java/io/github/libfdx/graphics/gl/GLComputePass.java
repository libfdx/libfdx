package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceSet;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceValueKind;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class GLComputePass implements ComputePass {
    private final ProviderId provider;
    private final GLApi gl;
    private final GLResourceDomain domain;
    private final GraphicsLimits limits;
    private final ShaderResourceSet[] sets;
    private GLComputePipeline pipeline;
    private boolean ended = true;
    private int uniformBuffer;
    private ByteBuffer uniformBytes;

    GLComputePass(ProviderId provider, GLApi gl, GLResourceDomain domain, GraphicsLimits limits) {
        this.provider = provider;
        this.gl = gl;
        this.domain = domain;
        this.limits = limits;
        sets = new ShaderResourceSet[limits.maxBindGroups()];
    }

    void begin() { ended = false; pipeline = null; Arrays.fill(sets, null); }
    boolean isEnded() { return ended; }
    private void requireOpen() {
        domain.requireUsable();
        if (ended) throw new FdxException("GL compute pass has ended");
    }

    @Override
    public void setPipeline(ComputePipeline value) {
        requireOpen();
        if (!(value instanceof GLComputePipeline candidate) || candidate.domain != domain) {
            throw new FdxException("GL compute pipeline belongs to another device");
        }
        candidate.requireLive();
        pipeline = candidate;
        Arrays.fill(sets, null);
    }

    @Override
    public void setResourceSet(ShaderResourceSet set) {
        requireOpen();
        if (pipeline == null) throw new FdxException("Set a GL compute pipeline before its resources");
        if (set == null || set.group() >= sets.length || !pipeline.layout.physicalHash().equals(set.layout().physicalHash())) {
            throw new FdxException("GL compute resources do not match the active pipeline");
        }
        sets[set.group()] = set;
    }

    @Override
    public void dispatch(int x, int y, int z) {
        requireOpen();
        validateDispatch(x, y, z, limits);
        if (pipeline == null) throw new FdxException("No GL compute pipeline is bound");
        pipeline.requireLive();
        // Validate every borrowed resource before changing GL state.
        for (int i = 0; i < pipeline.layout.bindingCount(); i++) {
            var binding = pipeline.layout.binding(i);
            var set = sets[binding.group()];
            if (set == null) throw new FdxException("Missing GL compute resource group " + binding.group());
            var value = set.find(binding.binding());
            if (value.kind() == ShaderResourceValueKind.BUFFER) GLResources.requireBuffer(value.buffer(), domain, "Compute buffer");
            else if (value.kind() == ShaderResourceValueKind.TEXTURE) GLResources.requireTexture(value.texture(), domain, "Compute texture");
        }
        gl.useProgram(pipeline.module.program());
        for (int i = 0; i < pipeline.layout.bindingCount(); i++) {
            var binding = pipeline.layout.binding(i);
            var value = sets[binding.group()].find(binding.binding());
            int slot = pipeline.slots[i];
            if (binding.resourceKind() == ShaderResourceKind.STORAGE_TEXTURE) {
                GLTextureHandle texture = GLResources.requireTexture(value.texture(), domain, "Compute texture");
                gl.bindStorageImage(slot, texture.texture(), texture.format());
            } else if (value.kind() == ShaderResourceValueKind.PARAMETER_BLOCK) {
                var block = value.parameterBlock();
                if (uniformBuffer == 0) uniformBuffer = gl.genBuffer();
                gl.bindArrayBuffer(uniformBuffer);
                if (uniformBytes == null || uniformBytes.capacity() < block.byteSize()) {
                    uniformBytes = ByteBuffer.allocateDirect(block.byteSize());
                    gl.bufferData(block.byteSize());
                }
                block.copyTo(uniformBytes, 0);
                uniformBytes.position(0).limit(block.byteSize());
                gl.bufferSubData(uniformBytes);
                gl.bindArrayBuffer(0);
                gl.bindComputeBuffer(slot, uniformBuffer, 0, block.byteSize(), true);
            } else {
                GLBufferHandle buffer = GLResources.requireBuffer(value.buffer(), domain, "Compute buffer");
                gl.bindComputeBuffer(slot, buffer.buffer(), Math.toIntExact(value.offset()), Math.toIntExact(value.size()),
                        binding.resourceKind() == ShaderResourceKind.UNIFORM_BUFFER);
            }
        }
        gl.dispatchCompute(x, y, z);
        // Covers subsequent dispatches, buffer copies/readback and texture sampling.
        gl.computeMemoryBarrier();
    }

    @Override
    public void end() {
        if (ended) return;
        ended = true;
        pipeline = null;
        Arrays.fill(sets, null);
        if (!domain.isLost()) gl.useProgram(0);
    }
    void dispose() {
        if (uniformBuffer != 0 && !domain.isLost()) gl.deleteBuffer(uniformBuffer);
        uniformBuffer = 0;
        uniformBytes = null;
    }
    @Override
    public ProviderId providerId() { return provider; }
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() { return (T) this; }
}
