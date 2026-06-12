package io.github.libfdx.backend.web;

import org.teavm.tooling.TeaVMTargetType;

/**
 * Lists the supported web target values.
 *
 * @author xpenatan
 */
public enum WebTarget {
    JAVASCRIPT("app.js", false, TeaVMTargetType.JAVASCRIPT),
    WASM("app.wasm", true, TeaVMTargetType.WEBASSEMBLY_GC);

    private final String defaultTargetFileName;
    private final boolean wasm;
    private final TeaVMTargetType teaVMTargetType;

    WebTarget(String defaultTargetFileName, boolean wasm, TeaVMTargetType teaVMTargetType) {
        this.defaultTargetFileName = defaultTargetFileName;
        this.wasm = wasm;
        this.teaVMTargetType = teaVMTargetType;
    }

    /**
     * Returns the default target file name.
     *
     * @return the get default target file name
     */
    public String getDefaultTargetFileName() {
        return defaultTargetFileName;
    }

    /**
     * Returns whether Wasm is enabled or true.
     *
     * @return true if Wasm is enabled or true; false otherwise
     */
    public boolean isWasm() {
        return wasm;
    }

    TeaVMTargetType teaVMTargetType() {
        return teaVMTargetType;
    }
}
