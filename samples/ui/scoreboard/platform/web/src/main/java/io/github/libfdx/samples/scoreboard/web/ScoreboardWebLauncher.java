package io.github.libfdx.samples.scoreboard.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.samples.scoreboard.ScoreboardApplication;

public final class ScoreboardWebLauncher {
    private ScoreboardWebLauncher() {
    }

    public static void main(String[] args) {
        WebApplicationConfig config = new WebApplicationConfig()
                .title("Scoreboard").size(0, 0).canvasId("libfdx-canvas")
                .graphics(new WebGLProvider());
        new WebApplicationBackend().start(config, new ScoreboardApplication(0));
    }

}
