package io.github.libfdx.samples.basic.web;

/**
 * Launches the basic web js entry point.
 *
 * @author xpenatan
 */
public final class BasicWebJsLauncher {
    private BasicWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        BasicWebLauncherSupport.start("JS", args);
    }
}
