package io.github.libfdx.testsupport.desktopcpp;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import io.github.libfdx.testsupport.nativeprocess.NativeTestLaunchHandler;

/** jNative adapter for the test module's native child-process tracker. */
@NativeInclude("libfdx_test_process_cpp.h")
public final class DesktopCppTestProcesses implements NativeTestLaunchHandler.Processes {
    @Override public long create() { return nativeCreate(); }
    @Override public int launch(long handle, byte[] arguments) { return nativeLaunch(handle, arguments, arguments.length); }
    @Override public int poll(long handle) { return nativePoll(handle); }
    @Override public void dispose(long handle) { nativeDispose(handle); }

    @NativeImport("fdx_cpp_test_process_create") private static native long nativeCreate();
    @NativeImport("fdx_cpp_test_process_launch") private static native int nativeLaunch(long state, byte[] arguments, int length);
    @NativeImport(value = "fdx_cpp_test_process_poll", leaf = true) private static native int nativePoll(long state);
    @NativeImport("fdx_cpp_test_process_dispose") private static native void nativeDispose(long state);
}
