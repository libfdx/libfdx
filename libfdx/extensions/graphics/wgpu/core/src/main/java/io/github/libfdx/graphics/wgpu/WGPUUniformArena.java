package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUBindGroup;
import com.github.xpenatan.webgpu.WGPUBindGroupDescriptor;
import com.github.xpenatan.webgpu.WGPUBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import com.github.xpenatan.webgpu.WGPUBuffer;
import com.github.xpenatan.webgpu.WGPUBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPULimits;
import com.github.xpenatan.webgpu.WGPURenderPassEncoder;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUVectorInt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reuses dynamically offset uniform buffers and bind groups across submitted frames.
 */
final class WGPUUniformArena {
    private static final int SLOTS_PER_CHUNK = 64;

    private final WGPUContext context;
    private final int stride;
    private final ArrayList<Chunk> chunks = new ArrayList<Chunk>();
    private final WGPUVectorInt dynamicOffsets = new WGPUVectorInt();
    private int allocationCursor;
    private boolean disposed;

    WGPUUniformArena(WGPUContext context) {
        this.context = context;
        WGPULimits limits = WGPULimits.obtain();
        context.nativeDevice().getLimits(limits);
        int alignment = Math.max(256, limits.getMinUniformBufferOffsetAlignment());
        stride = align(WGPURenderPass.PBR_UNIFORM_BYTE_COUNT, alignment);
    }

    void beginFrame() {
        allocationCursor = 0;
    }

    int bind(WGPURenderPassEncoder pass, WGPURenderPipelineHandle pipeline, ByteBuffer data,
            int allocationIndex) {
        boolean upload = allocationIndex < 0;
        if (upload) {
            allocationIndex = allocationCursor++;
        }
        int chunkIndex = allocationIndex / SLOTS_PER_CHUNK;
        int slot = allocationIndex % SLOTS_PER_CHUNK;
        Chunk chunk = chunk(chunkIndex);
        int offset = slot * stride;
        if (upload) {
            data.position(0);
            data.limit(WGPURenderPass.PBR_UNIFORM_BYTE_COUNT);
            context.nativeQueue().writeBuffer(chunk.buffer, offset, data, WGPURenderPass.PBR_UNIFORM_BYTE_COUNT);
        }
        WGPUBindGroup bindGroup = chunk.bindGroup(pipeline.uniformBindGroupLayout());
        dynamicOffsets.clear();
        dynamicOffsets.push_back(offset);
        pass.setBindGroup(pipeline.uniformBindGroupIndex(), bindGroup, dynamicOffsets);
        return allocationIndex;
    }

    void releaseLayout(WGPUBindGroupLayout layout) {
        if (layout == null || disposed) {
            return;
        }
        WGPUCleanup cleanup = new WGPUCleanup();
        for (int i = 0; i < chunks.size(); i++) {
            WGPUBindGroup bindGroup = chunks.get(i).bindGroups.remove(layout);
            releaseBindGroup(bindGroup, cleanup);
        }
        cleanup.throwIfFailed();
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        WGPUCleanup cleanup = new WGPUCleanup();
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).dispose(cleanup);
        }
        chunks.clear();
        cleanup.run(dynamicOffsets::dispose);
        cleanup.throwIfFailed();
    }

    private Chunk chunk(int index) {
        while (chunks.size() <= index) {
            chunks.add(new Chunk(chunks.size()));
        }
        return chunks.get(index);
    }

    private int align(int value, int alignment) {
        return ((value + alignment - 1) / alignment) * alignment;
    }

    private void releaseBindGroup(WGPUBindGroup bindGroup, WGPUCleanup cleanup) {
        if (bindGroup == null) {
            return;
        }
        cleanup.run(() -> {
            if (bindGroup.isValid()) {
                bindGroup.release();
            }
        });
        cleanup.run(bindGroup::dispose);
    }

    private final class Chunk {
        private final WGPUBuffer buffer;
        private final Map<WGPUBindGroupLayout, WGPUBindGroup> bindGroups =
                new IdentityHashMap<WGPUBindGroupLayout, WGPUBindGroup>();

        Chunk(int index) {
            WGPUBufferDescriptor descriptor = WGPUBufferDescriptor.obtain();
            descriptor.setNextInChain(WGPUChainedStruct.NULL);
            descriptor.setLabel("libfdx uniform arena " + index);
            descriptor.setSize(stride * SLOTS_PER_CHUNK);
            descriptor.setUsage(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));
            descriptor.setMappedAtCreation(false);
            WGPUBuffer created = new WGPUBuffer();
            try {
                context.nativeDevice().createBuffer(descriptor, created);
                created.native_setAddress(created.native_getAddressLong());
                buffer = created;
            }
            catch (RuntimeException | Error failure) {
                try {
                    created.dispose();
                }
                catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        WGPUBindGroup bindGroup(WGPUBindGroupLayout layout) {
            WGPUBindGroup bindGroup = bindGroups.get(layout);
            if (bindGroup != null) {
                return bindGroup;
            }
            WGPUVectorBindGroupEntry entries = WGPUVectorBindGroupEntry.obtain();
            WGPUBindGroupEntry uniformEntry = WGPUBindGroupEntry.obtain();
            uniformEntry.setNextInChain(WGPUChainedStruct.NULL);
            uniformEntry.setBinding(0);
            uniformEntry.setBuffer(buffer);
            uniformEntry.setOffset(0);
            uniformEntry.setSize(WGPURenderPass.PBR_UNIFORM_BYTE_COUNT);
            entries.push_back(uniformEntry);

            WGPUBindGroupDescriptor descriptor = WGPUBindGroupDescriptor.obtain();
            descriptor.setNextInChain(WGPUChainedStruct.NULL);
            descriptor.setLabel("libfdx uniform arena bind group");
            descriptor.setLayout(layout);
            descriptor.setEntries(entries);
            WGPUBindGroup created = new WGPUBindGroup();
            try {
                context.nativeDevice().createBindGroup(descriptor, created);
                bindGroups.put(layout, created);
                return created;
            }
            catch (RuntimeException | Error failure) {
                try {
                    created.dispose();
                }
                catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        void dispose(WGPUCleanup cleanup) {
            for (WGPUBindGroup bindGroup : bindGroups.values()) {
                releaseBindGroup(bindGroup, cleanup);
            }
            bindGroups.clear();
            cleanup.run(() -> {
                if (buffer.isValid()) {
                    buffer.destroy();
                }
            });
            cleanup.run(() -> {
                if (buffer.isValid()) {
                    buffer.release();
                }
            });
            cleanup.run(buffer::dispose);
        }
    }
}
