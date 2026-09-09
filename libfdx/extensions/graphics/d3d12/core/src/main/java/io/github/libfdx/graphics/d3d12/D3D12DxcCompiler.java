package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.lang.foreign.ValueLayout.*;

/** An owning-thread IDxcCompiler3 session. Returned IDxcBlob references belong to the caller. */
final class D3D12DxcCompiler implements AutoCloseable {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final FunctionDescriptor RESULT_METHOD = FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS);
    private static final FunctionDescriptor COMPILE_METHOD = FunctionDescriptor.of(JAVA_INT,
            ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS);
    private static final ThreadLocal<D3D12DxcCompiler> WORKER = new ThreadLocal<>();
    private final Thread owner = Thread.currentThread();
    private MemorySegment compiler = MemorySegment.NULL;
    private MemorySegment utils = MemorySegment.NULL;

    private static final class Library {
        static final D3D12DxcLibrary DISTRIBUTION = new D3D12DxcLibrary();
        static final MethodHandle CREATE = LINKER.downcallHandle(
                DISTRIBUTION.symbols.find("DxcCreateInstance").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    }

    D3D12DxcCompiler() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(ADDRESS);
            int result = (int)Library.CREATE.invokeExact(
                    guid("73e22d93-e6ce-47f3-b5bf-f0664f39c1b0", arena),
                    guid("228b4687-5a6a-4730-900c-9702b2203f54", arena), output);
            compiler = output.get(ADDRESS, 0);
            check(result, "create IDxcCompiler3");
            if (compiler.address() == 0) throw new FdxException("DXC returned no IDxcCompiler3");
        } catch (Throwable error) {
            release(compiler);
            compiler = MemorySegment.NULL;
            throw new FdxException("Could not initialize the packaged DXC runtime compiler", error);
        }
    }

    static String version() { return Library.DISTRIBUTION.version; }
    static String cacheIdentity() { return Library.DISTRIBUTION.compilerIdentity; }

    /** Copies previously validated, key/checksum-checked DXIL into an owned IDxcBlob. No Compile call. */
    MemorySegment loadBytecode(byte[] bytes) {
        requireOwner();
        if (compiler.address() == 0) throw new FdxException("DXC compiler is closed");
        MemorySegment blob = MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = arena.allocate(ADDRESS);
            if (utils.address() == 0) {
                int result = (int)Library.CREATE.invokeExact(
                        guid("6245d6af-66e0-48fd-80b4-4d271796748c", arena),
                        guid("4605c4cb-2019-492a-ada4-65f20bb7d67f", arena), output);
                utils = output.get(ADDRESS, 0);
                check(result, "create IDxcUtils");
                if (utils.address() == 0) throw new FdxException("DXC returned no IDxcUtils");
            }
            output.set(ADDRESS, 0, MemorySegment.NULL);
            // dxcapi.h IDxcUtils: IUnknown slots 0..2; CreateBlob (copy, raw binary) is slot 6.
            int result = (int)method(utils, 6, FunctionDescriptor.of(JAVA_INT,
                    ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS)).invokeExact(utils,
                    arena.allocateFrom(JAVA_BYTE, bytes), bytes.length, 0, output);
            blob = output.get(ADDRESS, 0);
            check(result, "copy cached DXIL");
            if (blob.address() == 0) throw new FdxException("DXC returned no cached bytecode blob");
            MemorySegment owned = blob;
            blob = MemorySegment.NULL;
            return owned;
        } catch (Throwable failure) {
            throw new FdxException("Could not load cached DXIL", failure);
        } finally { release(blob); }
    }

    static byte[] bytecode(MemorySegment blob) throws Throwable {
        long size = size(blob);
        if (size < 1 || size > 16 * 1024 * 1024) throw new FdxException("DXIL cache payload size is out of bounds");
        MemorySegment data = (MemorySegment)method(blob, 3, FunctionDescriptor.of(ADDRESS, ADDRESS)).invokeExact(blob);
        return data.reinterpret(size).toArray(JAVA_BYTE);
    }

    static List<String> arguments(String entry, String profile, boolean validation, boolean optimize) {
        if (!List.of("vs_6_0", "ps_6_0", "cs_6_0").contains(profile)) {
            throw new FdxException("D3D12 requires a Shader Model 6.0 stage profile: " + profile);
        }
        var arguments = new ArrayList<>(List.of("-E", entry, "-T", profile,
                validation || !optimize ? "-Od" : "-O3", "-HV", "2018", "-Zpc",
                "-Wno-parentheses-equality"));
        if (validation) arguments.addAll(List.of("-Zi", "-Qembed_debug"));
        return List.copyOf(arguments);
    }

    MemorySegment compile(String source, String entry, String profile, String label,
            boolean validation, boolean optimize) {
        requireOwner();
        if (compiler.address() == 0) throw new FdxException("DXC compiler is closed");
        String context = "DXC " + version() + " shader '" + label + "' (" + profile + ", entry " + entry + ")";
        MemorySegment result = MemorySegment.NULL;
        MemorySegment errors = MemorySegment.NULL;
        MemorySegment blob = MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            byte[] utf8 = source.getBytes(StandardCharsets.UTF_8);
            MemorySegment buffer = arena.allocate(24, 8); // DxcBuffer: pointer, SIZE_T, UINT32 encoding.
            buffer.set(ADDRESS, 0, arena.allocateFrom(JAVA_BYTE, utf8));
            buffer.set(JAVA_LONG, 8, utf8.length);
            buffer.set(JAVA_INT, 16, 65001);
            List<String> options = arguments(entry, profile, validation, optimize);
            MemorySegment args = arena.allocate(options.size() * ADDRESS.byteSize(), ADDRESS.byteAlignment());
            for (int index = 0; index < options.size(); index++) {
                args.setAtIndex(ADDRESS, index, arena.allocateFrom(options.get(index), StandardCharsets.UTF_16LE));
            }
            MemorySegment output = arena.allocate(ADDRESS);
            int invocation = (int)method(compiler, 3, COMPILE_METHOD).invokeExact(compiler, buffer, args,
                    options.size(), MemorySegment.NULL, guid("58346cda-dde7-4497-9461-6f87af5e0659", arena), output);
            result = output.get(ADDRESS, 0);
            check(invocation, "invoke IDxcCompiler3.Compile");
            if (result.address() == 0) throw new FdxException("DXC returned no compilation result");
            MemorySegment status = arena.allocate(JAVA_INT);
            check((int)method(result, 3, RESULT_METHOD).invokeExact(result, status), "get compilation status");
            output.set(ADDRESS, 0, MemorySegment.NULL);
            int diagnosticResult = (int)method(result, 5, RESULT_METHOD).invokeExact(result, output);
            errors = output.get(ADDRESS, 0);
            check(diagnosticResult, "get compiler diagnostics");
            String diagnostics = text(errors).strip();
            if (status.get(JAVA_INT, 0) < 0) {
                throw new FdxException(diagnostics.isEmpty()
                        ? "Compilation failed: HRESULT 0x" + Integer.toHexString(status.get(JAVA_INT, 0)) : diagnostics);
            }
            if (!diagnostics.isEmpty()) System.err.println("[libfdx-d3d12] " + context + ": " + diagnostics);
            output.set(ADDRESS, 0, MemorySegment.NULL);
            int objectResult = (int)method(result, 4, RESULT_METHOD).invokeExact(result, output);
            blob = output.get(ADDRESS, 0);
            check(objectResult, "get validated DXIL object");
            if (blob.address() == 0 || size(blob) == 0) throw new FdxException("DXC returned empty DXIL bytecode");
            MemorySegment owned = blob;
            blob = MemorySegment.NULL;
            return owned;
        } catch (Throwable error) {
            throw new FdxException("Could not compile " + context + ": " + error.getMessage(), error);
        } finally {
            release(blob);
            release(errors);
            release(result);
        }
    }

    static D3D12DxcCompiler workerCompiler() {
        D3D12DxcCompiler compiler = WORKER.get();
        if (compiler == null) { compiler = new D3D12DxcCompiler(); WORKER.set(compiler); }
        return compiler;
    }

    static ThreadFactory workerThreads() {
        ThreadFactory threads = Executors.defaultThreadFactory();
        return work -> threads.newThread(() -> {
            try { work.run(); }
            finally {
                D3D12DxcCompiler compiler = WORKER.get();
                WORKER.remove();
                if (compiler != null) compiler.close();
            }
        });
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new FdxException("DXC compiler must be used and closed on its owning thread");
    }

    @Override public void close() {
        requireOwner();
        release(utils);
        utils = MemorySegment.NULL;
        release(compiler);
        compiler = MemorySegment.NULL;
    }

    static void release(MemorySegment object) {
        if (object.address() == 0) return;
        try { int ignored = (int)method(object, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS)).invokeExact(object); }
        catch (Throwable error) { throw new FdxException("Could not release DXC COM reference", error); }
    }

    static long size(MemorySegment blob) throws Throwable {
        return (long)method(blob, 4, FunctionDescriptor.of(JAVA_LONG, ADDRESS)).invokeExact(blob);
    }

    private static String text(MemorySegment blob) throws Throwable {
        if (blob.address() == 0) return "";
        long size = size(blob);
        if (size == 0) return "";
        MemorySegment data = (MemorySegment)method(blob, 3, FunctionDescriptor.of(ADDRESS, ADDRESS)).invokeExact(blob);
        byte[] bytes = data.reinterpret(size).toArray(JAVA_BYTE);
        int length = bytes.length;
        while (length > 0 && bytes[length - 1] == 0) length--;
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }

    private static MethodHandle method(MemorySegment object, int slot, FunctionDescriptor descriptor) {
        MemorySegment table = object.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
        return LINKER.downcallHandle(table.reinterpret((slot + 1L) * ADDRESS.byteSize())
                .getAtIndex(ADDRESS, slot), descriptor);
    }

    private static void check(int result, String operation) {
        if (result < 0) throw new FdxException("DXC could not " + operation + ": HRESULT 0x" + Integer.toHexString(result));
    }

    private static MemorySegment guid(String value, Arena arena) {
        UUID id = UUID.fromString(value);
        MemorySegment result = arena.allocate(16, 4);
        var bytes = result.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt((int)(id.getMostSignificantBits() >>> 32));
        bytes.putShort((short)(id.getMostSignificantBits() >>> 16));
        bytes.putShort((short)id.getMostSignificantBits());
        bytes.order(ByteOrder.BIG_ENDIAN).putLong(id.getLeastSignificantBits());
        return result;
    }
}
