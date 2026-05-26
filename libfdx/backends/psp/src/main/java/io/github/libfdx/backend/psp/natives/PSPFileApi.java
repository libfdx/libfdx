package io.github.libfdx.backend.psp.natives;

import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

@Include("PSPFileSystem.h")
public final class PSPFileApi {
    private PSPFileApi() {
    }

    @Import(name = "libfdx_psp_asset_size")
    public static native int assetSize(char[] path, int pathLength);

    @Import(name = "libfdx_psp_asset_read")
    public static native int assetRead(char[] path, int pathLength, byte[] target, int targetLength);

    @Import(name = "libfdx_psp_debug_log")
    public static native int debugLog(char[] message, int messageLength);

    @Import(name = "libfdx_psp_debug_log_clear")
    public static native int clearDebugLog();
}
