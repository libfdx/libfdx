package io.github.libfdx.tools.project.generator.web;

public final class ProjectGeneratorWebWasmLauncher {
    private ProjectGeneratorWebWasmLauncher() {
    }

    public static void main(String[] args) {
        ProjectGeneratorWebLauncherSupport.start("Wasm", args);
    }
}
