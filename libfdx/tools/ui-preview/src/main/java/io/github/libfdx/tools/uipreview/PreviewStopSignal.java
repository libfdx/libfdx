package io.github.libfdx.tools.uipreview;

import java.nio.file.Files;
import java.nio.file.Path;

/** Stop requests are observed off the render thread. The render loop reads only a volatile flag. */
final class PreviewStopSignal implements AutoCloseable {
    private volatile boolean closed;
    volatile boolean requested;
    private final Thread thread;

    PreviewStopSignal(Path path) {
        thread = path == null ? null : new Thread(() -> {
            while (!closed) {
                if (Files.exists(path)) { requested = true; return; }
                try { Thread.sleep(100); }
                catch (InterruptedException interrupted) { return; }
            }
        }, "ui-preview-stop");
        if (thread != null) { thread.setDaemon(true); thread.start(); }
    }

    public void close() {
        closed = true;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(1000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }

}
