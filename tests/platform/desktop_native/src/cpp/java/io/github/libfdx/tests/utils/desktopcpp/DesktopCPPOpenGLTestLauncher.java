package io.github.libfdx.tests.utils.desktopcpp;

import io.github.libfdx.backend.desktopcpp.DesktopCppOpenGLProvider;

/** Launches the shared OpenGL tests with jNative. */
public final class DesktopCPPOpenGLTestLauncher {
    private DesktopCPPOpenGLTestLauncher() {}

    public static void main(String[] args) {
        DesktopCppTestLauncherSupport.launch("gl", args, DesktopCppOpenGLProvider::new);
    }
}
