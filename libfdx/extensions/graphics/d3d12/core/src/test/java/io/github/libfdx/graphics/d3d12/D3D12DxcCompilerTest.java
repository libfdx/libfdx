package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.foreign.MemorySegment;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
final class D3D12DxcCompilerTest {
    @Test
    void copiedCachedDxilHasIndependentNativeOwnership() throws Throwable {
        byte[] bytes;
        try (var compiler = new D3D12DxcCompiler()) {
            MemorySegment compiled = compiler.compile("float4 main():SV_Target{return 1;}", "main", "ps_6_0", "cache-test", false, true);
            try { bytes = D3D12DxcCompiler.bytecode(compiled); }
            finally { D3D12DxcCompiler.release(compiled); }
        }
        MemorySegment cached;
        try (var compiler = new D3D12DxcCompiler()) { cached = compiler.loadBytecode(bytes); }
        try { assertArrayEquals(bytes, D3D12DxcCompiler.bytecode(cached)); }
        finally { D3D12DxcCompiler.release(cached); }
    }
    @Test
    void packagedCompilerProducesGraphicsAndComputeObjects() throws Throwable {
        try (var compiler = new D3D12DxcCompiler()) {
            compile(compiler, "float4 main(float4 p:POSITION):SV_Position{return p;}", "vs_6_0", false, true);
            compile(compiler, "float4 main():SV_Target{return float4(1,0,0,1);}", "ps_6_0", false, true);
            compile(compiler, "RWStructuredBuffer<uint> data:register(u0); [numthreads(1,1,1)] void main(uint3 id:SV_DispatchThreadID){data[id.x]=42;}", "cs_6_0", false, true);
            compile(compiler, "float4 main():SV_Target{return float4(1,0,0,1);}", "ps_6_0", true, true);
            compile(compiler, "float4 main():SV_Target{return float4(1,0,0,1);}", "ps_6_0", false, false);
        }
    }

    @Test
    void invalidSourceReportsStageEntryAndLabelAndSessionRemainsUsable() throws Throwable {
        try (var compiler = new D3D12DxcCompiler()) {
            var error = assertThrows(FdxException.class,
                    () -> compiler.compile("this is invalid HLSL", "main", "ps_6_0", "broken-material", false, true));
            assertTrue(error.getMessage().contains("broken-material"));
            assertTrue(error.getMessage().contains("ps_6_0, entry main"));
            assertTrue(error.getMessage().contains("error:"));
            compile(compiler, "float4 main():SV_Target{return 1;}", "ps_6_0", false, true);
        }
    }

    @Test
    void sessionsRejectOtherThreadsAndUseAfterClose() throws InterruptedException {
        var compiler = new D3D12DxcCompiler();
        try {
            var result = new AtomicReference<Throwable>();
            Thread thread = new Thread(() -> {
                try { compiler.compile("", "main", "ps_6_0", "wrong-thread", false, true); }
                catch (Throwable error) { result.set(error); }
            });
            thread.start();
            thread.join();
            assertInstanceOf(FdxException.class, result.get());
            assertTrue(result.get().getMessage().contains("owning thread"));
        } finally { compiler.close(); }
        assertThrows(FdxException.class, () -> compiler.compile("", "main", "ps_6_0", "closed", false, true));
    }

    @Test
    void workerOwnedCompilersProduceAndReleaseBatchObjects() {
        Callable<MemorySegment> job = () -> D3D12DxcCompiler.workerCompiler()
                .compile("float4 main():SV_Target{return 1;}", "main", "ps_6_0", "batch", false, true);
        var blobs = D3D12ShaderCompilationBatch.compile(List.of(job, job, job),
                D3D12DxcCompiler::release, D3D12DxcCompiler.workerThreads());
        assertEquals(3, blobs.size());
        blobs.forEach(D3D12DxcCompiler::release);
    }

    @Test
    void missingResourceHasActionableDiagnostic() {
        var error = assertThrows(FdxException.class, () -> D3D12DxcLibrary.resource("missing.dll"));
        assertTrue(error.getMessage().contains("Missing packaged DXC resource"));
    }

    @Test
    void warningsAreVisibleWithoutDiscardingSuccessfulBytecode() throws Throwable {
        var captured = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        try (var compiler = new D3D12DxcCompiler(); var output = new PrintStream(captured)) {
            System.setErr(output);
            compile(compiler, "#warning native diagnostic test\nfloat4 main():SV_Target{return 1;}",
                    "ps_6_0", false, true);
        } finally { System.setErr(previous); }
        assertTrue(captured.toString().contains("native diagnostic test"));
        assertTrue(captured.toString().contains("native-test"));
    }

    @Test
    void nativeBatchFailureJoinsWorkersAndReleasesSuccessfulObjects() {
        var releases = new AtomicInteger();
        Callable<MemorySegment> valid = () -> D3D12DxcCompiler.workerCompiler()
                .compile("float4 main():SV_Target{return 1;}", "main", "ps_6_0", "valid", false, true);
        Callable<MemorySegment> invalid = () -> D3D12DxcCompiler.workerCompiler()
                .compile("invalid HLSL", "main", "ps_6_0", "invalid-batch", false, true);
        var error = assertThrows(FdxException.class, () -> D3D12ShaderCompilationBatch.compile(
                List.of(valid, invalid, valid), blob -> {
                    D3D12DxcCompiler.release(blob);
                    releases.incrementAndGet();
                }, D3D12DxcCompiler.workerThreads()));
        assertTrue(error.getMessage().contains("invalid-batch"));
        assertEquals(2, releases.get());
    }

    private static void compile(D3D12DxcCompiler compiler, String source, String profile,
            boolean validation, boolean optimize) throws Throwable {
        MemorySegment blob = compiler.compile(source, "main", profile, "native-test", validation, optimize);
        try { assertTrue(D3D12DxcCompiler.size(blob) > 32); }
        finally { D3D12DxcCompiler.release(blob); }
    }
}
