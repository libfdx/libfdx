package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.FdxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class WGPUCreationErrorsTest {
    @Test
    void reportsFirstCreationErrorAndClearsScopeAfterFailure() {
        WGPUCreationErrors errors = new WGPUCreationErrors();
        assertFalse(errors.capture("outside creation"));
        try (WGPUCreationErrors.Scope scope = errors.begin("shader module")) {
            assertTrue(errors.capture("first failure"));
            assertTrue(errors.capture("secondary failure"));
            FdxException failure = assertThrows(FdxException.class, () -> WGPUCreationErrors.check(scope));
            assertTrue(failure.getMessage().contains("first failure"));
            assertFalse(failure.getMessage().contains("secondary failure"));
        }
        assertFalse(errors.capture("delayed callback"));
        try (WGPUCreationErrors.Scope next = errors.begin("valid pipeline")) {
            assertDoesNotThrow(() -> WGPUCreationErrors.check(next));
        }
    }

    @Test
    void doesNotCaptureAnotherDevicesError() {
        WGPUCreationErrors first = new WGPUCreationErrors(), second = new WGPUCreationErrors();
        try (WGPUCreationErrors.Scope scope = first.begin("pipeline")) {
            assertFalse(second.capture("another device"));
            assertDoesNotThrow(() -> WGPUCreationErrors.check(scope));
            assertTrue(first.capture("allocation failed"));
            assertThrows(FdxException.class, () -> WGPUCreationErrors.check(scope));
        }
    }

    @Test
    void concurrentCallingThreadsNeverConsumeEachOthersErrors() throws Exception {
        WGPUCreationErrors errors = new WGPUCreationErrors();
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
                WGPUCreationErrors.Scope owner = errors.begin("owner")) {
            executor.submit(() -> {
                assertFalse(errors.capture("unscoped worker callback"));
                try (WGPUCreationErrors.Scope worker = errors.begin("worker")) {
                    assertTrue(errors.capture("worker failure"));
                    assertTrue(assertThrows(FdxException.class, () -> WGPUCreationErrors.check(worker))
                            .getMessage().contains("worker failure"));
                }
            }).get(5, TimeUnit.SECONDS);
            assertDoesNotThrow(() -> WGPUCreationErrors.check(owner));
            assertTrue(errors.capture("owner failure"));
            assertTrue(assertThrows(FdxException.class, () -> WGPUCreationErrors.check(owner))
                    .getMessage().contains("owner failure"));
        }
    }
}
