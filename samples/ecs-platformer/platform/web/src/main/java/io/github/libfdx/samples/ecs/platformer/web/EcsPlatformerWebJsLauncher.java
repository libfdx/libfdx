package io.github.libfdx.samples.ecs.platformer.web;

/**
 * Launches the ECS platformer JavaScript web entry point.
 *
 * @author xpenatan
 */
public final class EcsPlatformerWebJsLauncher {
    private EcsPlatformerWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        EcsPlatformerWebLauncherSupport.start("JS", args);
    }
}
