package io.github.libfdx.testsupport.nativeprocess;

import io.github.libfdx.testsupport.TestLaunchHandler;

/** Tracks child processes without waiting in the chooser's application loop. */
public final class NativeTestLaunchHandler implements TestLaunchHandler {
    public interface Processes {
        long create();
        int launch(long handle, byte[] arguments);
        int poll(long handle);
        void dispose(long handle);
    }

    private final String graphics;
    private final Processes processes;
    private long handle;

    public NativeTestLaunchHandler(String graphics, Processes processes) {
        this.graphics = graphics;
        this.processes = processes;
        handle = processes.create();
        if (handle == 0) throw new IllegalStateException("Could not allocate native test process tracker");
    }

    @Override public boolean launch(String testName, String graphicsName) {
        if (handle == 0 || !graphics.equals(graphicsName)) return false;
        int error = processes.launch(handle, NativeTestLaunchArguments.encode(NativeTestLaunchArguments.forTest(testName)));
        if (error != 0) {
            System.err.println("Could not launch " + testName + ": native process error " + error);
            return false;
        }
        return true;
    }

    @Override public boolean hasActiveLaunch() {
        if (handle == 0) return false;
        int result = processes.poll(handle);
        if (result < 0) System.err.println("Could not poll native test process: " + -result);
        return result != 0;
    }

    /** Releases tracking resources. Selected tests continue when the chooser closes, as on JVM. */
    public void dispose() {
        long closingHandle = handle;
        handle = 0;
        if (closingHandle != 0) processes.dispose(closingHandle);
    }
}
