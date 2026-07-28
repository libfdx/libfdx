package io.github.libfdx.tools.project.generator.desktop;

import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;

/**
 * Launches the project generator desktop entry point.
 *
 * @author xpenatan
 */
public final class ProjectGeneratorDesktopLauncher {
    private ProjectGeneratorDesktopLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Project Generator")
                .size(980, 760)
                .visible(visible(args))
                .vSync(true)
                .foregroundFps(60)
                .graphics(new DesktopOpenGLProvider());

        new DesktopApplicationBackend().start(config, new ProjectGeneratorDesktopApplication(
                new DesktopProjectExportTarget(outputDirectory(args)), exitAfterFrames(args)));
    }

    private static String outputDirectory(String[] args) {
        return option(args, "--output=", System.getProperty("libfdx.projectGenerator.output",
                "build/generated/project-generator/libfdx-game"));
    }

    private static long exitAfterFrames(String[] args) {
        String value = option(args, "--exit-after-frames=",
                System.getProperty("libfdx.projectGenerator.exitAfterFrames", "0"));
        return Long.parseLong(value);
    }

    private static boolean visible(String[] args) {
        String value = option(args, "--visible=", System.getProperty("libfdx.projectGenerator.visible", "true"));
        return Boolean.parseBoolean(value);
    }

    private static String option(String[] args, String prefix, String fallback) {
        if (args == null) {
            return fallback;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return fallback;
    }
}
