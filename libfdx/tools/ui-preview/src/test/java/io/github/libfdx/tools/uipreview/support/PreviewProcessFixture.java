package io.github.libfdx.tools.uipreview.support;

import java.nio.file.Files;
import java.nio.file.Path;

/** Ordinary precompiled worker/build executables used by supervisor tests, with no native window. */
public final class PreviewProcessFixture {
    public static void main(String[] args) throws Exception {
        Path control = Path.of(System.getProperty("fixture.control"));
        if (args[0].equals("build")) {
            Files.writeString(control.resolve("build-started"), "started");
            while (Files.exists(control.resolve("build-gate"))) Thread.sleep(20);
            if (Files.exists(control.resolve("build-fail"))) System.exit(3);
            Files.copy(control.resolve("next.txt"), control.resolve("classes/content.txt"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (Files.exists(control.resolve("runtime-fail"))) System.exit(7);
        if (!Files.exists(control.resolve("no-ready"))) {
            try (var content = PreviewProcessFixture.class.getResourceAsStream("/content.txt")) {
                if (content == null) throw new IllegalStateException("Snapshot content missing");
                Files.write(Path.of(System.getProperty("libfdx.uiPreview.readyFile")).resolveSibling("observed.txt"), content.readAllBytes());
            }
            Path ready = Path.of(System.getProperty("libfdx.uiPreview.readyFile"));
            Files.writeString(ready.resolveSibling("observed-option.txt"), System.getProperty("fixture.selection", ""));
            Files.writeString(Path.of(System.getProperty("libfdx.uiPreview.targetFile")),
                    System.getProperty("libfdx.uiPreview.restoredTarget", ""));
            while (Files.exists(control.resolve("ready-gate"))) Thread.sleep(20);
            Files.writeString(ready, "ready");
        }
        while (!Files.exists(Path.of(System.getProperty("libfdx.uiPreview.stopFile")))) {
            if (Files.exists(control.resolve("close-window"))) return;
            Thread.sleep(20);
        }
    }

}
