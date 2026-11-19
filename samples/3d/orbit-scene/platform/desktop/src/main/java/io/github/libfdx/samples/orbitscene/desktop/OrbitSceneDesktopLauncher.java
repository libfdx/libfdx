package io.github.libfdx.samples.orbitscene.desktop;

import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.samples.orbitscene.OrbitSceneApplication;

public final class OrbitSceneDesktopLauncher {
    private OrbitSceneDesktopLauncher() {
    }

    public static void main(String[] args) {
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("Orbit Scene").size(640, 400).vSync(true)
                .graphics(new DesktopOpenGLProvider());
        long frames = Long.getLong("libfdx.sample.exitAfterFrames", 0L);
        new DesktopApplicationBackend().start(config, new OrbitSceneApplication(frames));
    }

}
