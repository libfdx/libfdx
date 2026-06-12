package io.github.libfdx.backend.psp.natives;

import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/**
 * Exposes API access for PSP debug.
 *
 * @author xpenatan
 */
@Include("PSPDebugApi.h")
public class PSPDebugApi {

    /**
     * Calls the PSP debug screen init native function.
     */
    @Import(name = "pspDebugScreenInit")
    public static native void pspDebugScreenInit();

    /**
     * Calls the PSP debug screen printf native function.
     *
     * @param text the text
     */
    @Import(name = "pspDebugScreenPrintf")
    public static native void pspDebugScreenPrintf(String text);

    /**
     * Calls the PSP debug screen set XY native function.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    @Import(name = "pspDebugScreenSetXY")
    public static native void pspDebugScreenSetXY(int x, int y);

    /**
     * @return The amount of user memory allocated in bytes.
     */
    @Import(name = "getAllocatedMemory")
    private static native int getAllocatedMemory();

    /**
     * Calls the libfdx PSP debug heap log native function.
     *
     * @param frame the frame index
     * @return the debug heap log
     */
    @Import(name = "libfdx_psp_debug_heap_log")
    public static native int debugHeapLog(int frame);

    /**
     * Calls the libfdx PSP debug loop log native function.
     *
     * @param frame the frame index
     * @param stage the stage identifier
     * @param javaRunning whether Java-side execution is running
     * @param nativeRunning whether native execution is running
     * @param closeRequested whether close was requested
     * @return the debug loop log
     */
    @Import(name = "libfdx_psp_debug_loop_log")
    public static native int debugLoopLog(int frame, int stage, int javaRunning, int nativeRunning, int closeRequested);

    /**
     * Returns the allocated memory in megabytes.
     *
     * @return the allocated memory in megabytes
     */
    public static float getAllocatedMemoryMB() {
        double mb = getAllocatedMemory() / (1024.0 * 1024.0);
        return (float)(Math.round(mb * 1000) / 1000.0);
    }

    private static long lastMemoryLogTime;

    /**
     * Logs used memory after the configured delay has elapsed.
     *
     * @param logMemoryDelayMilli the delay between memory log messages in milliseconds
     */
    public static void logUsedMemory(int logMemoryDelayMilli) {
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastMemoryLogTime >= logMemoryDelayMilli) {
            float usedMemory= getAllocatedMemoryMB();
            System.out.println("Used memory: " + String.format("%.3f", usedMemory) + " MB");
            lastMemoryLogTime = currentTime;
        }
    }
}
