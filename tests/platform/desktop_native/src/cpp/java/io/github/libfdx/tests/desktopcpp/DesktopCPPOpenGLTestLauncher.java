package io.github.libfdx.tests.desktopcpp;

import io.github.libfdx.backend.desktopcpp.DesktopCppOpenGLProvider;
import io.github.libfdx.testsupport.desktopcpp.DesktopCppTestLauncherSupport;

/** Launches the shared OpenGL tests with jNative. */
public final class DesktopCPPOpenGLTestLauncher {
    private DesktopCPPOpenGLTestLauncher() {}

    public static void main(String[] args) {
        DesktopCppTestLauncherSupport.launch("gl", args, DesktopCppOpenGLProvider::new);
    }
}
