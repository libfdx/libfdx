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
import io.github.libfdx.core.FdxException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reuses dynamically offset uniform buffers and bind groups across submitted
 * frames, partitioned by reflected binding size.
 */
final class WGPUUniformArena {
    private static final int SLOTS_PER_CHUNK = 64;

    private final WGPUContext context;
    private final int alignment;
    private final ArrayList<SizeClass> sizes = new ArrayList<SizeClass>();
    private final WGPUVectorInt dynamicOffsets = new WGPUVectorInt();
    private boolean disposed;

    WGPUUniformArena(WGPUContext context) {
        this.context = context;
        WGPULimits limits = WGPULimits.obtain();
        context.nativeDevice().getLimits(limits);
        alignment = Math.max(256, limits.getMinUniformBufferOffsetAlignment());
    }

    void beginFrame() {
        for (int i = 0; i < sizes.size(); i++) {
            sizes.get(i).allocationCursor = 0;
        }
    }

    int bind(WGPURenderPassEncoder pass, WGPURenderPipelineHandle pipeline,
            int uniformIndex, ByteBuffer data, int allocationIndex) {
        int byteCount = pipeline.resourceBindings().uniformByteCount(uniformIndex);
        if (byteCount <= 0 || data == null || data.capacity() < byteCount) {
            throw new FdxException("WGPU uniform upload does not match the reflected binding size");
        }
        SizeClass size = sizeClass(byteCount);
        boolean upload = allocationIndex < 0;
        if (upload) {
            allocationIndex = size.allocationCursor++;
        }
        int chunkIndex = allocationIndex / SLOTS_PER_CHUNK;
        int slot = allocationIndex % SLOTS_PER_CHUNK;
        Chunk chunk = size.chunk(chunkIndex);
        int offset = slot * size.stride;
        if (upload) {
            data.position(0);
            data.limit(byteCount);
            context.nativeQueue().writeBuffer(chunk.buffer, offset, data, byteCount);
        }
        WGPUBindGroup bindGroup = chunk.bindGroup(pipeline, uniformIndex);
        dynamicOffsets.clear();
        dynamicOffsets.push_back(offset);
        pass.setBindGroup(pipeline.uniformBindGroupIndex(uniformIndex),
                bindGroup, dynamicOffsets);
        return allocationIndex;
    }

    void releaseLayout(WGPUBindGroupLayout layout) {
        if (layout == null || disposed) {
            return;
        }
        WGPUCleanup cleanup = new WGPUCleanup();
        for (int i = 0; i < sizes.size(); i++) {
            SizeClass size = sizes.get(i);
            for (int j = 0; j < size.chunks.size(); j++) {
                WGPUBindGroup bindGroup = size.chunks.get(j).bindGroups.remove(layout);
                releaseBindGroup(bindGroup, cleanup);
            }
        }
        cleanup.throwIfFailed();
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        WGPUCleanup cleanup = new WGPUCleanup();
        for (int i = 0; i < sizes.size(); i++) {
            sizes.get(i).dispose(cleanup);
        }
        sizes.clear();
        cleanup.run(dynamicOffsets::dispose);
        cleanup.throwIfFailed();
    }

    private SizeClass sizeClass(int byteCount) {
        for (int i = 0; i < sizes.size(); i++) {
            SizeClass size = sizes.get(i);
            if (size.byteCount == byteCount) {
                return size;
            }
        }
        SizeClass created = new SizeClass(byteCount);
        sizes.add(created);
        return created;
    }

    private int align(int value) {
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

    private final class SizeClass {
        final int byteCount;
        final int stride;
        final ArrayList<Chunk> chunks = new ArrayList<Chunk>();
        int allocationCursor;

        SizeClass(int byteCount) {
            this.byteCount = byteCount;
            stride = align(byteCount);
        }

        Chunk chunk(int index) {
            while (chunks.size() <= index) {
                chunks.add(new Chunk(this, chunks.size()));
            }
            return chunks.get(index);
        }

        void dispose(WGPUCleanup cleanup) {
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).dispose(cleanup);
            }
            chunks.clear();
        }
    }

    private final class Chunk {
        private final SizeClass size;
        private final WGPUBuffer buffer;
        private final Map<WGPUBindGroupLayout, WGPUBindGroup> bindGroups =
                new IdentityHashMap<WGPUBindGroupLayout, WGPUBindGroup>();

        Chunk(SizeClass size, int index) {
            this.size = size;
            WGPUBufferDescriptor descriptor = WGPUBufferDescriptor.obtain();
            descriptor.setNextInChain(WGPUChainedStruct.NULL);
            descriptor.setLabel("libfdx uniform arena " + size.byteCount + " bytes " + index);
            descriptor.setSize(size.stride * SLOTS_PER_CHUNK);
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

        WGPUBindGroup bindGroup(WGPURenderPipelineHandle pipeline,
                int uniformIndex) {
            WGPUBindGroupLayout layout =
                    pipeline.uniformBindGroupLayout(uniformIndex);
            WGPUBindGroup bindGroup = bindGroups.get(layout);
            if (bindGroup != null) {
                return bindGroup;
            }
            WGPUVectorBindGroupEntry entries = WGPUVectorBindGroupEntry.obtain();
            WGPUBindGroupEntry uniformEntry = WGPUBindGroupEntry.obtain();
            uniformEntry.setNextInChain(WGPUChainedStruct.NULL);
            uniformEntry.setBinding(pipeline.resourceBindings()
                    .uniformBuffer(uniformIndex).binding());
            uniformEntry.setBuffer(buffer);
            uniformEntry.setOffset(0);
            uniformEntry.setSize(size.byteCount);
            entries.push_back(uniformEntry);

            WGPUBindGroupDescriptor descriptor = WGPUBindGroupDescriptor.obtain();
            descriptor.setNextInChain(WGPUChainedStruct.NULL);
            descriptor.setLabel("libfdx reflected uniform bind group");
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
