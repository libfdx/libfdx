package io.github.libfdx.backend.psp.natives;

import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/**
 * Exposes API access for PSP core.
 *
 * @author xpenatan
 */
@Include("PSPCoreApi.h")
public class PSPCoreApi {

    /**
     * Calls the is running native function.
     *
     * @return true if running is enabled or true; false otherwise
     */
    @Import(name = "isRunning")
    public static native boolean isRunning();

    /**
     * Calls the setup callbacks native function.
     *
     * @return the setup callbacks
     */
    @Import(name = "setupCallbacks")
    public static native int setupCallbacks();

    /**
     * Calls the libfdx PSP delay micros native function.
     *
     * @param micros the micros
     */
    @Import(name = "libfdx_psp_delay_micros")
    public static native void delayMicros(int micros);
}
