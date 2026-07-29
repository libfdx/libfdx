package io.github.libfdx.samples.g2d.spritemovement.iosc;

import io.github.libfdx.backend.iosc.IosCApplicationBackend;
import io.github.libfdx.backend.iosc.IosCApplicationConfig;
import io.github.libfdx.backend.iosc.IosCOpenGLESProvider;
import io.github.libfdx.ecs.EcsApplication;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject;

/**
 * Launches the 2D Sprite Movement iOS C entry point.
 *
 * @author xpenatan
 */
public final class SpriteMovementIosCLauncher {
    private SpriteMovementIosCLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        IosCApplicationConfig config = new IosCApplicationConfig()
                .title("libfdx 2D Sprite Movement - GLES iOS C")
                .size(640, 480)
                .graphics(new IosCOpenGLESProvider());

        new IosCApplicationBackend().start(
                config, new EcsApplication(new SpriteMovementProject(exitAfterFrames(args))));
    }

    private static long exitAfterFrames(String[] args) {
        if (args != null) {
            for (String arg : args) {
                long parsed = parseLong(arg);
                if (parsed > 0L) {
                    return parsed;
                }
            }
        }
        String value = System.getProperty("libfdx.sample.exitAfterFrames");
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        return parseLong(value.trim());
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
