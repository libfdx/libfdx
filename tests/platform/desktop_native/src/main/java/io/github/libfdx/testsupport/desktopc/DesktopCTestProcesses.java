package io.github.libfdx.testsupport.desktopc;

import io.github.libfdx.testsupport.nativeprocess.NativeTestLaunchHandler;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/** TeaVM-C adapter for the test module's native child-process tracker. */
@Include("libfdx_test_process.h")
public final class DesktopCTestProcesses implements NativeTestLaunchHandler.Processes {
    @Override public long create() { return nativeCreate().toLong(); }
    @Override public int launch(long handle, byte[] arguments) {
        return nativeLaunch(Address.fromLong(handle), Address.ofData(arguments), arguments.length);
    }
    @Override public int poll(long handle) { return nativePoll(Address.fromLong(handle)); }
    @Override public void dispose(long handle) { nativeDispose(Address.fromLong(handle)); }

    @Import(name = "fdxTestProcessCreate") private static native Address nativeCreate();
    @Import(name = "fdxTestProcessLaunch") private static native int nativeLaunch(Address state, Address arguments, int length);
    @Import(name = "fdxTestProcessPoll") private static native int nativePoll(Address state);
    @Import(name = "fdxTestProcessDispose") private static native void nativeDispose(Address state);
}
