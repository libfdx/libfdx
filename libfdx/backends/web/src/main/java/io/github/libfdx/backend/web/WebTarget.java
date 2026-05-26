package io.github.libfdx.backend.web;

import org.teavm.tooling.TeaVMTargetType;

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

    public String getDefaultTargetFileName() {
        return defaultTargetFileName;
    }

    public boolean isWasm() {
        return wasm;
    }

    TeaVMTargetType teaVMTargetType() {
        return teaVMTargetType;
    }
}
