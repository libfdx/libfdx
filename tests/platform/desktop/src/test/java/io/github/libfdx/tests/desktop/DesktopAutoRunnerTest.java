package io.github.libfdx.tests.desktop;

import io.github.libfdx.testsupport.desktop.AutoChildFixture;
import io.github.libfdx.testsupport.runner.GraphicsMatrixRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DesktopAutoRunnerTest {
    @TempDir
    Path directory;

    @Test
    void matrixContinuesAfterCrashTimeoutAndEarlyExitAndWritesEveryResult() throws Exception {
        verifyMatrix(false);
    }

    @Test
    void shutdownHangIsKilledBeforeWholeProcessDeadlineAndMatrixContinues() throws Exception {
        verifyMatrix(true);
    }

    private void verifyMatrix(boolean shutdownHang) throws Exception {
        Map<String, String> previous = new HashMap<>();
        Map<String, String> overrides = Map.of(
                "libfdx.test.autoTests", "CircleTest,TriangleTest",
                "libfdx.test.autoGraphics", "gl,vulkan,d3d12,wgpu",
                "libfdx.test.autoTimeoutSeconds", shutdownHang ? "60" : "2",
                "libfdx.test.autoShutdownTimeoutSeconds", "1",
                "libfdx.test.autoReportDirectory", directory.toString(),
                "java.class.path", Path.of(AutoChildFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        overrides.forEach((key, value) -> previous.put(key, System.setProperty(key, value)));
        try {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            long started = System.nanoTime();
            assertEquals(1, GraphicsMatrixRunner.run(List.of(java, "-Dfixture.shutdownHang=" + shutdownHang), AutoChildFixture.class.getName()));
            if (shutdownHang) assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(30));
            Path run;
            try (var paths = Files.list(directory)) { run = paths.findFirst().orElseThrow(); }
            String report = Files.readString(run.resolve("checklist.md"));
            assertTrue(report.contains("## 1. CircleTest"));
            assertTrue(report.contains("**PASS: 2/8 test/API checks** | Completed: 8/8"));
            assertTrue(report.contains("**Tests passing every selected API: 0/2**"));
            assertTrue(report.contains("## 2. TriangleTest"));
            assertEquals(2, report.split("\\*\\*TIMEOUT\\*\\*", -1).length - 1);
            assertEquals(2, report.split("\\*\\*PASS\\*\\*", -1).length - 1);
            assertTrue(report.contains("Exit 7"));
            assertTrue(report.contains("Exited without completing"));
            assertFalse(report.contains("**PENDING**"));
            assertFalse(report.contains("**RUNNING**"));
            if (shutdownHang) assertTrue(report.contains("Shutdown exceeded 1s after observation completed"));
        } finally {
            previous.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); });
        }
    }

    @Test
    void launchErrorIsRecordedAndStaleCompletionIsRemoved() throws Exception {
        Path marker = directory.resolve("stale.complete");
        Files.writeString(marker, "old success");
        var result = GraphicsMatrixRunner.runProcess(List.of(directory.resolve("missing-java").toString()),
                directory.resolve("launch.log"), marker, 2);
        assertEquals("LAUNCH ERROR", result.status());
        assertFalse(Files.exists(marker));
        assertTrue(Files.size(directory.resolve("launch.log")) > 0);
    }
}
