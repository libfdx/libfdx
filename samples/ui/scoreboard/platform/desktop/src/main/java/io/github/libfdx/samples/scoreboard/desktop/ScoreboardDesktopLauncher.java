package io.github.libfdx.samples.scoreboard.desktop;

import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.samples.scoreboard.ScoreboardApplication;

public final class ScoreboardDesktopLauncher {
    private ScoreboardDesktopLauncher() {
    }

    public static void main(String[] args) {
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("Scoreboard").size(640, 400).vSync(true)
                .graphics(new DesktopOpenGLProvider());
        long frames = Long.getLong("libfdx.sample.exitAfterFrames", 0L);
        new DesktopApplicationBackend().start(config, new ScoreboardApplication(frames));
    }

}
