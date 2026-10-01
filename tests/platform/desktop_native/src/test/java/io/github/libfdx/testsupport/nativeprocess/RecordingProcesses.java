package io.github.libfdx.testsupport.nativeprocess;

import java.util.ArrayList;

/** JVM fixture for the native process boundary. */
public final class RecordingProcesses implements NativeTestLaunchHandler.Processes {
    public final ArrayList<byte[]> launches = new ArrayList<>();
    public int polls, disposals, result;
    public long create() { return 42; }
    public int launch(long handle, byte[] arguments) {
        check(handle);
        launches.add(arguments);
        return result;
    }
    public int poll(long handle) { check(handle); polls++; return launches.isEmpty() ? 0 : 1; }
    public void dispose(long handle) { check(handle); disposals++; }
    private void check(long handle) { if (handle != 42) throw new AssertionError("Wrong native handle"); }
}
