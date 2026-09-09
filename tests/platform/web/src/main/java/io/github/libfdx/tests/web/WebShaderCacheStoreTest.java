package io.github.libfdx.tests.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.web.WebShaderCacheStore;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;

/** Actual IndexedDB transaction/budget/lifetime checks on JS and Wasm, selected by the web launcher. */
public final class WebShaderCacheStoreTest extends ApplicationAdapter {
    private static final String A = "0".repeat(64), B = "1".repeat(64), C = "2".repeat(64);
    private WebShaderCacheStore first, second;
    private FdxFuture<Void> writeA, writeB;
    private FdxFuture<byte[]> readA, readB;
    private Application application;
    private int step;
    private String survivor;
    private long deadline;

    @Override public void create(Fdx fdx) {
        application = fdx.app();
        String name = "libfdx-store-validation-" + System.currentTimeMillis();
        first = new WebShaderCacheStore(name, ShaderArtifactCache.MAX_RECORD_BYTES);
        second = new WebShaderCacheStore(name, ShaderArtifactCache.MAX_RECORD_BYTES);
        byte[] large = new byte[9 * 1024 * 1024];
        large[0] = 7;
        large[large.length - 1] = 11;
        // Separate connections race; the two records together exceed the database byte budget.
        writeA = first.writeAsync(A, large);
        writeB = second.writeAsync(B, large);
        deadline = System.currentTimeMillis() + 15000;
    }

    @Override public void render() {
        check(System.currentTimeMillis() < deadline, "IndexedDB checks timed out");
        switch (step) {
            case 0 -> {
                if (!writeA.isDone() || !writeB.isDone()) return;
                writeA.get(); writeB.get();
                readA = first.readAsync(A); readB = second.readAsync(B); step++;
            }
            case 1 -> {
                if (!readA.isDone() || !readB.isDone()) return;
                byte[] a = readA.get(), b = readB.get();
                check((a == null) != (b == null), "Concurrent writes exceeded the shared budget");
                survivor = a != null ? A : B;
                byte[] data = a != null ? a : b;
                check(data.length == 9 * 1024 * 1024 && data[0] == 7 && data[data.length - 1] == 11,
                        "IndexedDB bytes changed");
                data[0] = 99;
                readA = first.readAsync(survivor); step++;
            }
            case 2 -> {
                if (!readA.isDone()) return;
                check(readA.get()[0] == 7, "A returned array aliases stored data");
                writeA = first.writeAsync(survivor, new byte[]{12, 13}); step++;
            }
            case 3 -> {
                if (!writeA.isDone()) return;
                writeA.get(); readA = second.readAsync(survivor); step++;
            }
            case 4 -> {
                if (!readA.isDone()) return;
                byte[] replacement = readA.get();
                check(replacement.length == 2 && replacement[0] == 12 && replacement[1] == 13,
                        "Atomic replacement was not visible to the other connection");
                writeA = first.writeAsync(C, new byte[]{42});
                readA = first.readAsync(C);
                first.dispose();
                check(first.readAsync(C).isFailed(), "Disposed cache accepted a new request");
                step++;
            }
            case 5 -> {
                if (!writeA.isDone() || !readA.isDone()) return;
                writeA.get(); check(readA.get()[0] == 42, "Disposal dropped accepted I/O");
                writeA = second.removeAsync(C); writeB = second.removeAsync(survivor); step++;
            }
            case 6 -> {
                if (!writeA.isDone() || !writeB.isDone()) return;
                writeA.get(); writeB.get(); readA = second.readAsync(C); readB = second.readAsync(survivor); step++;
            }
            case 7 -> {
                if (!readA.isDone() || !readB.isDone()) return;
                check(readA.get() == null && readB.get() == null, "Removal left a cache entry");
                System.out.println("WEB_SHADER_CACHE_STORE_PASS budget=1 cross_connection=1 copies=1 replacement=1 drain=1 removal=1");
                step++;
                application.requestExit();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @Override public void dispose() {
        if (first != null) first.dispose();
        if (second != null) second.dispose();
    }
}
