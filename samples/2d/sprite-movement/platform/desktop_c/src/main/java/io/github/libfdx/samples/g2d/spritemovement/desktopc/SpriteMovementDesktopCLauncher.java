package io.github.libfdx.samples.g2d.spritemovement.desktopc;

import io.github.libfdx.backend.desktopc.DesktopCApplicationBackend;
import io.github.libfdx.backend.desktopc.DesktopCApplicationConfig;
import io.github.libfdx.backend.desktopc.DesktopCOpenGLProvider;
import io.github.libfdx.ecs.tooling.EcsProjectApplication;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject;

/**
 * Launches the 2D Sprite Movement desktop C entry point.
 *
 * @author xpenatan
 */
public final class SpriteMovementDesktopCLauncher {
    private SpriteMovementDesktopCLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean maximized = Boolean.parseBoolean(System.getProperty("libfdx.sample.maximized", "true"));
        DesktopCApplicationConfig config = new DesktopCApplicationConfig()
                .title("libfdx 2D Sprite Movement - GL Desktop C")
                .size(640, 480)
                .maximized(maximized)
                .graphics(new DesktopCOpenGLProvider());

        new DesktopCApplicationBackend().start(
                config, new EcsProjectApplication(new SpriteMovementProject(exitAfterFrames(args))));
    }

    private static long exitAfterFrames(String[] args) {
        if (args != null && args.length > 0) {
            long parsed = parseLong(args[0]);
            if (parsed > 0L) {
                return parsed;
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
