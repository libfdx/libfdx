package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Worker-only cached PSOs. Native creation still validates the complete description on every hit. */
final class D3D12PipelineCache {
    record Entry(ShaderArtifactCache cache, ShaderCacheKey key, byte[] bytes) { }

    private D3D12PipelineCache() { }

    static FdxFuture<Entry> read(ShaderArtifactCache cache, ShaderCacheKey key) {
        if (key == null) return FdxFuture.completed(null);
        FdxFuture<Entry> result = FdxFuture.pending();
        cache.readAsync(key).onSuccess(bytes -> result.complete(new Entry(cache, key, bytes)));
        return result;
    }

    static ShaderCacheKey key(String adapter, ShaderCacheKey vertex, ShaderCacheKey fragment,
            RenderPipelineDescriptor state, D3D12Device.VertexInputs inputs, D3D12Device.PipelineBindings bindings) {
        if (adapter == null || RuntimeIdentity.VALUE == null) return null;
        return key(adapter, RuntimeIdentity.VALUE, vertex, fragment, state, inputs, bindings);
    }

    /** Version this schema with changes to native root signatures, descriptor defaults or bindings. */
    static ShaderCacheKey key(String adapter, String runtime, ShaderCacheKey vertex, ShaderCacheKey fragment,
            RenderPipelineDescriptor state, D3D12Device.VertexInputs inputs, D3D12Device.PipelineBindings bindings) {
        String fixedState;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment descriptor = arena.allocate(D3D12Ffm.SIZE_GRAPHICS_PIPELINE_DESC, 8);
            D3D12PipelineState.write(descriptor, state);
            fixedState = PortableSha256.hash(descriptor.toArray(D3D12Ffm.BYTE));
        }
        StringBuilder targets = new StringBuilder();
        for (var color : state.colorTargets()) targets.append(color.format().name()).append(';');
        return ShaderCacheKey.of(ShaderCacheLayer.DRIVER_PIPELINE, "d3d12-pso:1", adapter, runtime,
                vertex.digest(), fragment.digest(), fixedState, targets.toString(),
                state.renderTargetLayout().depthStencilFormat().name(),
                state.primitiveTopology().name(), Boolean.toString(state.depthTestEnabled()),
                Boolean.toString(state.depthWriteEnabled()), Integer.toString(state.multisampleState().count()),
                Arrays.toString(inputs.layoutStrides), Arrays.toString(inputs.layoutStepModes),
                Arrays.toString(inputs.locations), Arrays.toString(inputs.formats),
                Arrays.toString(inputs.offsets), Arrays.toString(inputs.slots),
                Integer.toString(bindings.uniformGroup), Integer.toString(bindings.uniformBinding),
                Arrays.toString(bindings.textureGroups), Arrays.toString(bindings.textureBindings),
                Arrays.toString(bindings.samplerGroups), Arrays.toString(bindings.samplerBindings));
    }

    static MemorySegment create(MemorySegment device, MemorySegment descriptor, Arena arena, Entry entry) {
        byte[] bytes = entry == null ? null : entry.bytes();
        if (bytes != null) {
            MemorySegment nativeBytes = arena.allocateFrom(D3D12Ffm.BYTE, bytes);
            descriptor.set(D3D12Ffm.ADDRESS, D3D12Ffm.OFF_PIPELINE_CACHED_PSO, nativeBytes);
            descriptor.set(D3D12Ffm.LONG, D3D12Ffm.OFF_PIPELINE_CACHED_PSO + 8L, bytes.length);
        }
        MemorySegment output = arena.allocate(D3D12Ffm.ADDRESS);
        if (entry != null) entry.cache().pipelineInvoked(entry.key(), bytes != null);
        int result = create(device, descriptor, output);
        if (D3D12Ffm.failed(result) && bytes != null) {
            // A stale/malformed PSO must not force DXC or poison the otherwise valid shader stages.
            D3D12Ffm.release(D3D12Ffm.pointer(output));
            output.set(D3D12Ffm.ADDRESS, 0, D3D12Ffm.NULL);
            entry.cache().rejected(entry.key());
            descriptor.set(D3D12Ffm.ADDRESS, D3D12Ffm.OFF_PIPELINE_CACHED_PSO, D3D12Ffm.NULL);
            descriptor.set(D3D12Ffm.LONG, D3D12Ffm.OFF_PIPELINE_CACHED_PSO + 8L, 0);
            entry.cache().pipelineInvoked(entry.key(), false);
            result = create(device, descriptor, output);
            bytes = null;
        }
        if (D3D12Ffm.failed(result)) {
            D3D12Ffm.release(D3D12Ffm.pointer(output));
            D3D12Ffm.check(result, "Could not create a Direct3D 12 graphics pipeline");
        }
        MemorySegment pipeline = D3D12Ffm.pointer(output);
        try {
            if (entry != null && bytes == null) save(pipeline, arena, entry);
        } catch (Error failure) { D3D12Ffm.release(pipeline); throw failure; }
        return pipeline;
    }

    private static int create(MemorySegment device, MemorySegment descriptor, MemorySegment output) {
        return D3D12Ffm.comIntAAAA(device, D3D12Ffm.SLOT_DEVICE_CREATE_GRAPHICS_PIPELINE_STATE,
                descriptor, D3D12Ffm.IID_ID3D12_PIPELINE_STATE, output);
    }

    private static void save(MemorySegment pipeline, Arena arena, Entry entry) {
        MemorySegment output = arena.allocate(D3D12Ffm.ADDRESS);
        MemorySegment blob = D3D12Ffm.NULL;
        try {
            int result = D3D12Ffm.comIntAA(pipeline, D3D12Ffm.SLOT_PIPELINE_GET_CACHED_BLOB, output);
            blob = D3D12Ffm.pointer(output);
            if (D3D12Ffm.failed(result) || D3D12Ffm.isNull(blob)) return;
            long size = D3D12Ffm.comLongA(blob, D3D12Ffm.SLOT_BLOB_GET_BUFFER_SIZE);
            if (size <= 0 || size > ShaderArtifactCache.MAX_PAYLOAD_BYTES) return;
            byte[] bytes = D3D12Ffm.comAddressA(blob, D3D12Ffm.SLOT_BLOB_GET_BUFFER_POINTER)
                    .reinterpret(size).toArray(D3D12Ffm.BYTE);
            // The store copies before returning and owns its queued I/O. Pipeline publication need not wait.
            entry.cache().writeAsync(entry.key(), bytes);
        } catch (RuntimeException ignored) {
            // Optional cache export cannot invalidate a successfully created pipeline.
        } finally { D3D12Ffm.release(blob); }
    }

    /** Loaded runtime binaries, not guessed installation paths. Initialized only by a worker. */
    private static final class RuntimeIdentity {
        static final String VALUE = compute();

        private static String compute() {
            try {
                SymbolLookup symbols = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
                Linker linker = Linker.nativeLinker();
                MethodHandle module = linker.downcallHandle(symbols.find("GetModuleHandleW").orElseThrow(),
                        FunctionDescriptor.of(D3D12Ffm.ADDRESS, D3D12Ffm.ADDRESS));
                MethodHandle filename = linker.downcallHandle(symbols.find("GetModuleFileNameW").orElseThrow(),
                        FunctionDescriptor.of(D3D12Ffm.INT, D3D12Ffm.ADDRESS, D3D12Ffm.ADDRESS, D3D12Ffm.INT));
                StringBuilder identity = new StringBuilder();
                for (String name : new String[]{"d3d12.dll", "d3d12core.dll", "dxgi.dll"}) {
                    Path path = modulePath(module, filename, name);
                    if (path == null) {
                        if (!name.equals("d3d12core.dll")) return null;
                        identity.append(name).append(":absent;");
                        continue;
                    }
                    MessageDigest hash = MessageDigest.getInstance("SHA-256");
                    try (InputStream input = Files.newInputStream(path)) {
                        byte[] buffer = new byte[65536];
                        for (int count; (count = input.read(buffer)) >= 0;) hash.update(buffer, 0, count);
                    }
                    identity.append(name).append(':').append(HexFormat.of().formatHex(hash.digest())).append(';');
                }
                return identity.toString();
            } catch (IOException | RuntimeException failure) { return null; }
            catch (NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
        }

        private static Path modulePath(MethodHandle module, MethodHandle filename, String name) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment text = arena.allocate((name.length() + 1L) * 2, 2);
                for (int i = 0; i < name.length(); i++) text.set(D3D12Ffm.SHORT, i * 2L, (short)name.charAt(i));
                MemorySegment handle = (MemorySegment)module.invokeExact(text);
                if (D3D12Ffm.isNull(handle)) return null;
                MemorySegment output = arena.allocate(32768L * 2, 2);
                int size = (int)filename.invokeExact(handle, output, 32768);
                if (size <= 0 || size >= 32768) throw new FdxException("Cannot identify loaded D3D12 runtime");
                StringBuilder path = new StringBuilder(size);
                for (int i = 0; i < size; i++) path.append((char)(output.get(D3D12Ffm.SHORT, i * 2L) & 65535));
                return Path.of(path.toString());
            } catch (RuntimeException | Error failure) { throw failure; }
            catch (Throwable failure) { throw new FdxException("Cannot identify loaded D3D12 runtime", failure); }
        }
    }
}
