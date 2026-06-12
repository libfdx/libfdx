package io.github.libfdx.tools.project.generator.web;

/**
 * Launches the project generator web js entry point.
 *
 * @author xpenatan
 */
public final class ProjectGeneratorWebJsLauncher {
    private ProjectGeneratorWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        ProjectGeneratorWebLauncherSupport.start("JS", args);
    }
}
