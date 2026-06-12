package io.github.libfdx.backend.psp.natives;

import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/**
 * Exposes API access for PSP input.
 *
 * @author xpenatan
 */
@Include("PSPInputApi.h")
public final class PSPInputApi {
    public static final int PSP_CTRL_SELECT = 0x000001;
    public static final int PSP_CTRL_START = 0x000008;
    public static final int PSP_CTRL_UP = 0x000010;
    public static final int PSP_CTRL_RIGHT = 0x000020;
    public static final int PSP_CTRL_DOWN = 0x000040;
    public static final int PSP_CTRL_LEFT = 0x000080;
    public static final int PSP_CTRL_LTRIGGER = 0x000100;
    public static final int PSP_CTRL_RTRIGGER = 0x000200;
    public static final int PSP_CTRL_TRIANGLE = 0x001000;
    public static final int PSP_CTRL_CIRCLE = 0x002000;
    public static final int PSP_CTRL_CROSS = 0x004000;
    public static final int PSP_CTRL_SQUARE = 0x008000;

    private PSPInputApi() {
    }

    /**
     * Calls the init input native function.
     */
    @Import(name = "initInput")
    public static native void initInput();

    /**
     * Calls the poll input native function.
     *
     * @return the poll input
     */
    @Import(name = "pollInput")
    public static native int pollInput();

    /**
     * Calls the analog x native function.
     *
     * @return the analog x
     */
    @Import(name = "analogX")
    public static native int analogX();

    /**
     * Calls the analog y native function.
     *
     * @return the analog y
     */
    @Import(name = "analogY")
    public static native int analogY();
}
