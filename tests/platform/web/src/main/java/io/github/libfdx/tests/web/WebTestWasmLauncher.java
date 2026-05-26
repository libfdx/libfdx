package io.github.libfdx.tests.web;

public final class WebTestWasmLauncher {
    private WebTestWasmLauncher() {
    }

    public static void main(String[] args) {
        WebTestLauncherSupport.start("Wasm", args);
    }
}
