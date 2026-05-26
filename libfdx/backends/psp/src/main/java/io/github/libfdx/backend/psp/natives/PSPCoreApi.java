package io.github.libfdx.backend.psp.natives;

import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

@Include("PSPCoreApi.h")
public class PSPCoreApi {

    @Import(name = "isRunning")
    public static native boolean isRunning();

    @Import(name = "setupCallbacks")
    public static native int setupCallbacks();

    @Import(name = "libfdx_psp_delay_micros")
    public static native void delayMicros(int micros);
}
