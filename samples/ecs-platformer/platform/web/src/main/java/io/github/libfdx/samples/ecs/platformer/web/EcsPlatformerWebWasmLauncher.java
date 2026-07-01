package io.github.libfdx.samples.ecs.platformer.web;

/**
 * Launches the ECS platformer Wasm web entry point.
 *
 * @author xpenatan
 */
public final class EcsPlatformerWebWasmLauncher {
    private EcsPlatformerWebWasmLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        EcsPlatformerWebLauncherSupport.start("Wasm", args);
    }
}
