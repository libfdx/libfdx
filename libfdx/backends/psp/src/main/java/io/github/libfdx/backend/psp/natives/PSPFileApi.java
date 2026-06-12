package io.github.libfdx.backend.psp.natives;

import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/**
 * Exposes API access for PSP file.
 *
 * @author xpenatan
 */
@Include("PSPFileSystem.h")
public final class PSPFileApi {
    private PSPFileApi() {
    }

    /**
     * Calls the libfdx PSP asset size native function.
     *
     * @param path the asset or file path
     * @param pathLength the path length
     * @return the asset size
     */
    @Import(name = "libfdx_psp_asset_size")
    public static native int assetSize(char[] path, int pathLength);

    /**
     * Calls the libfdx PSP asset read native function.
     *
     * @param path the asset or file path
     * @param pathLength the path length
     * @param target the target value
     * @param targetLength the target length
     * @return the asset read
     */
    @Import(name = "libfdx_psp_asset_read")
    public static native int assetRead(char[] path, int pathLength, byte[] target, int targetLength);

    /**
     * Calls the libfdx PSP debug log native function.
     *
     * @param message the message
     * @param messageLength the message length
     * @return the debug log
     */
    @Import(name = "libfdx_psp_debug_log")
    public static native int debugLog(char[] message, int messageLength);

    /**
     * Calls the libfdx PSP debug log clear native function.
     *
     * @return the clear debug log
     */
    @Import(name = "libfdx_psp_debug_log_clear")
    public static native int clearDebugLog();
}
