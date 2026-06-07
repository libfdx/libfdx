package io.github.libfdx.samples.basic.web;

public final class BasicWebWasmLauncher {
    private BasicWebWasmLauncher() {
    }

    public static void main(String[] args) {
        BasicWebLauncherSupport.start("Wasm", args);
    }
}
