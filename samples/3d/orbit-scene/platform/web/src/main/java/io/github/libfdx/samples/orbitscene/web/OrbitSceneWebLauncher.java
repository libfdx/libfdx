package io.github.libfdx.samples.orbitscene.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.samples.orbitscene.OrbitSceneApplication;

public final class OrbitSceneWebLauncher {
    private OrbitSceneWebLauncher() {
    }

    public static void main(String[] args) {
        WebApplicationConfig config = new WebApplicationConfig()
                .title("Orbit Scene").size(0, 0).canvasId("libfdx-canvas")
                .graphics(new WebGLProvider());
        new WebApplicationBackend().start(config, new OrbitSceneApplication(0));
    }

}
