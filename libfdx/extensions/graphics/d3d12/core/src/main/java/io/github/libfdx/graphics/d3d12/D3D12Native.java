package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class D3D12Native {
    private static final Object CONTEXT_LOCK = new Object();
    private static volatile ContextSlot[] contexts = new ContextSlot[8];
    private static int[] freeContextSlots = new int[8];
    private static int freeContextCount;
    private static int nextContextSlot = 1;

    private D3D12Native() {
    }

    static long createContext(long windowHandle, int width, int height, boolean vSync,
            boolean validation, int framesInFlight) {
        D3D12FfmContext context = new D3D12FfmContext(
                windowHandle, width, height, vSync, validation, framesInFlight);
        context.initialize();
        synchronized (CONTEXT_LOCK) {
            int index;
            ContextSlot previous;
            if (freeContextCount > 0) {
                index = freeContextSlots[--freeContextCount];
                previous = contexts[index];
            } else {
                index = nextContextSlot++;
                if (index >= contexts.length) {
                    contexts = Arrays.copyOf(contexts, contexts.length * 2);
                }
                previous = contexts[index];
            }
            int generation = previous != null ? previous.generation : 1;
            ContextSlot[] updated = Arrays.copyOf(contexts, contexts.length);
            updated[index] = new ContextSlot(generation, context);
            contexts = updated;
            return handle(index, generation);
        }
    }

    static void resizeContext(long context, int width, int height) {
        context(context).resize(width, height);
    }

    static boolean beginFrame(long context) {
        return context(context).beginFrame();
    }

    static void endFrame(long context) {
        context(context).endFrame();
    }

    static void readPixels(long context, ByteBuffer destination) {
        context(context).readPixels(destination);
    }

    static void destroyContext(long context) {
        D3D12FfmContext removed;
        synchronized (CONTEXT_LOCK) {
            ContextSlot[] snapshot = contexts;
            int index = (int)context;
            int generation = (int)(context >>> 32);
            if (index <= 0 || index >= snapshot.length) {
                throw new FdxException("Direct3D 12 context handle is invalid");
            }
            ContextSlot slot = snapshot[index];
            if (slot == null || slot.generation != generation || slot.context == null) {
                throw new FdxException("Direct3D 12 context handle is invalid");
            }
            removed = slot.context;
            ContextSlot[] updated = Arrays.copyOf(snapshot, snapshot.length);
            updated[index] = new ContextSlot(nextGeneration(generation), null);
            contexts = updated;
            if (freeContextCount == freeContextSlots.length) {
                freeContextSlots = Arrays.copyOf(freeContextSlots, freeContextSlots.length * 2);
            }
            freeContextSlots[freeContextCount++] = index;
        }
        removed.close();
    }

    static String adapterName(long context) {
        return context(context).adapterName();
    }

    static long createBuffer(long context, int size, int usage) {
        return context(context).createBuffer(size, usage);
    }

    static void writeBuffer(long context, long buffer, MemorySegment source, int size) {
        context(context).writeBuffer(buffer, source, size);
    }

    static void destroyBuffer(long context, long buffer) {
        context(context).destroyBuffer(buffer);
    }

    static long createTexture(long context, int width, int height, int format, int usage,
            int filter, int wrapS, int wrapT) {
        return context(context).createTexture(width, height, format, usage, filter, wrapS, wrapT);
    }

    static void writeTexture(long context, long texture, MemorySegment source, int size) {
        context(context).writeTexture(texture, source, size);
    }

    static void destroyTexture(long context, long texture) {
        context(context).destroyTexture(texture);
    }

    static long createShader(long context, String vertexSource, String fragmentSource,
            String vertexEntryPoint, String fragmentEntryPoint, String label) {
        return context(context).createShader(vertexSource, fragmentSource,
                vertexEntryPoint, fragmentEntryPoint, label);
    }

    static void destroyShader(long context, long shader) {
        context(context).destroyShader(shader);
    }

    static long createPipeline(long context, long shader, int colorFormat, int topology,
            boolean depthTest, boolean depthWrite, int sampledTextureCount,
            int uniformGroup, int uniformBinding,
            int[] layoutStrides, int[] layoutStepModes,
            int[] attributeLocations, int[] attributeFormats, int[] attributeOffsets, int[] attributeSlots,
            int[] textureGroups, int[] textureBindings, int[] samplerGroups, int[] samplerBindings) {
        return context(context).createPipeline(shader, colorFormat, topology,
                depthTest, depthWrite, sampledTextureCount, uniformGroup, uniformBinding,
                layoutStrides, layoutStepModes, attributeLocations, attributeFormats,
                attributeOffsets, attributeSlots, textureGroups, textureBindings,
                samplerGroups, samplerBindings);
    }

    static void destroyPipeline(long context, long pipeline) {
        context(context).destroyPipeline(pipeline);
    }

    static void beginRenderPass(long context, long texture, boolean clear,
            float red, float green, float blue, float alpha, boolean store,
            boolean depthEnabled, boolean depthClear, float depthClearValue) {
        context(context).beginPass(texture, clear, red, green, blue, alpha,
                store, depthEnabled, depthClear, depthClearValue);
    }

    static void endRenderPass(long context) {
        context(context).endPass();
    }

    static void setPipeline(long context, long pipeline) {
        context(context).setPipeline(pipeline);
    }

    static void setVertexBuffer(long context, int slot, long buffer) {
        context(context).setVertexBuffer(slot, buffer);
    }

    static void setIndexBuffer(long context, long buffer) {
        context(context).setIndexBuffer(buffer);
    }

    static void setTexture(long context, int slot, long texture) {
        context(context).setTexture(slot, texture);
    }

    static void setScissor(long context, int x, int y, int width, int height) {
        context(context).setScissor(x, y, width, height);
    }

    static void setViewport(long context, int x, int y, int width, int height) {
        context(context).setViewport(x, y, width, height);
    }

    static void bindUniforms(long context, MemorySegment source, int size) {
        context(context).bindUniforms(source, size);
    }

    static void draw(long context, int vertexCount, int instanceCount,
            int firstVertex, int firstInstance) {
        context(context).draw(vertexCount, instanceCount, firstVertex, firstInstance);
    }

    static void drawIndexed(long context, int indexCount, int instanceCount, int firstIndex,
            int baseVertex, int firstInstance) {
        context(context).drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    private static D3D12FfmContext context(long handle) {
        ContextSlot[] snapshot = contexts;
        int index = (int)handle;
        int generation = (int)(handle >>> 32);
        if (index <= 0 || index >= snapshot.length) {
            throw new FdxException("Direct3D 12 context handle is invalid");
        }
        ContextSlot slot = snapshot[index];
        if (slot == null || slot.generation != generation || slot.context == null) {
            throw new FdxException("Direct3D 12 context handle is invalid");
        }
        return slot.context;
    }

    private static long handle(int index, int generation) {
        return ((long)generation << 32) | (index & 0xffffffffL);
    }

    private static int nextGeneration(int generation) {
        int next = generation + 1;
        return next != 0 ? next : 1;
    }

    private static final class ContextSlot {
        private final int generation;
        private final D3D12FfmContext context;

        private ContextSlot(int generation, D3D12FfmContext context) {
            this.generation = generation;
            this.context = context;
        }
    }
}
