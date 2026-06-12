package io.github.libfdx.tests.web;

/**
 * Launches the web test js entry point.
 *
 * @author xpenatan
 */
public final class WebTestJsLauncher {
    private WebTestJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        WebTestLauncherSupport.start("JS", args);
    }
}
