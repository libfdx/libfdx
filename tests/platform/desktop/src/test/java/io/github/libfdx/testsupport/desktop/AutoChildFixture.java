package io.github.libfdx.testsupport.desktop;

import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone child used to exercise failure containment without a graphics device. */
public final class AutoChildFixture {
    public static void main(String[] args) throws Exception {
        Path marker = Path.of(System.getProperty("libfdx.test.autoCompletionFile"));
        switch (System.getProperty("libfdx.test.graphics")) {
            case "gl" -> { Files.writeString(marker, "premature marker"); System.exit(7); }
            case "vulkan" -> {
                if (Boolean.getBoolean("fixture.shutdownHang")) {
                    Files.writeString(Path.of(marker + ".stopping"), "stopping");
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try { Thread.sleep(60_000); } catch (InterruptedException ignored) { }
                    }));
                    System.exit(0);
                }
                Thread.sleep(60_000);
            }
            case "d3d12" -> { /* Early clean exit without completion. */ }
            case "wgpu" -> Files.writeString(marker, "completed");
            default -> throw new AssertionError("Unknown fixture mode");
        }
    }
}
