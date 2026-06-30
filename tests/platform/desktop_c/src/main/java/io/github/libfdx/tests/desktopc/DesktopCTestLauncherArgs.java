package io.github.libfdx.tests.desktopc;

import io.github.libfdx.tests.TestSelector;

final class DesktopCTestLauncherArgs {
    private static final String SYSTEM_PROPERTY_PREFIX = "-D";

    private final String[] positional;

    private DesktopCTestLauncherArgs(String[] positional) {
        this.positional = positional;
    }

    static DesktopCTestLauncherArgs apply(String[] args) {
        String[] positional = args != null ? new String[args.length] : new String[0];
        int positionalCount = 0;
        if (args != null) {
            for (String arg : args) {
                String value = trim(arg);
                if (value == null) {
                    continue;
                }
                if (applySystemProperty(value) || applyNamedOption(value)) {
                    continue;
                }
                positional[positionalCount++] = value;
            }
        }
        String[] compactPositional = compact(positional, positionalCount);
        DesktopCTestLauncherArgs launcherArgs = new DesktopCTestLauncherArgs(compactPositional);
        launcherArgs.applyPositionalCapture();
        return launcherArgs;
    }

    String testName() {
        String property = property("libfdx.test.name");
        if (property != null) {
            return property;
        }
        return positional.length > 0 ? positional[0] : TestSelector.DEFAULT_TEST_NAME;
    }

    long frames() {
        String property = property("libfdx.test.frames");
        if (property != null) {
            return parseLong(property, 0L);
        }
        return positional.length > 1 ? parseLong(positional[1], 0L) : 0L;
    }

    int width(String testName) {
        return intProperty("libfdx.test.width", TestSelector.defaultWidth(testName));
    }

    int height(String testName) {
        return intProperty("libfdx.test.height", TestSelector.defaultHeight(testName));
    }

    boolean maximized(boolean explicitSize) {
        return booleanProperty("libfdx.test.maximized", !explicitSize);
    }

    boolean hasProperty(String name) {
        return property(name) != null;
    }

    String property(String name) {
        return trim(System.getProperty(name));
    }

    private void applyPositionalCapture() {
        if (property("libfdx.test.capture") == null && positional.length > 2) {
            System.setProperty("libfdx.test.capture", positional[2]);
        }
    }

    private int intProperty(String name, int fallback) {
        String value = property(name);
        return value != null ? Integer.parseInt(value) : fallback;
    }

    private boolean booleanProperty(String name, boolean fallback) {
        String value = property(name);
        return value != null ? Boolean.parseBoolean(value) : fallback;
    }

    private static boolean applySystemProperty(String arg) {
        if (!arg.startsWith(SYSTEM_PROPERTY_PREFIX)) {
            return false;
        }
        int equals = arg.indexOf('=');
        if (equals <= SYSTEM_PROPERTY_PREFIX.length()) {
            return true;
        }
        System.setProperty(arg.substring(SYSTEM_PROPERTY_PREFIX.length(), equals), arg.substring(equals + 1));
        return true;
    }

    private static boolean applyNamedOption(String arg) {
        if (applyNamedOption(arg, "--test=", "libfdx.test.name")) {
            return true;
        }
        if (applyNamedOption(arg, "--frames=", "libfdx.test.frames")) {
            return true;
        }
        if (applyNamedOption(arg, "--width=", "libfdx.test.width")) {
            return true;
        }
        if (applyNamedOption(arg, "--height=", "libfdx.test.height")) {
            return true;
        }
        if (applyNamedOption(arg, "--maximized=", "libfdx.test.maximized")) {
            return true;
        }
        if (applyNamedOption(arg, "--capture=", "libfdx.test.capture")) {
            return true;
        }
        if (applyNamedOption(arg, "--captureFrame=", "libfdx.test.captureFrame")) {
            return true;
        }
        return applyNamedOption(arg, "--spriteCount=", "libfdx.test.spriteCount");
    }

    private static boolean applyNamedOption(String arg, String prefix, String propertyName) {
        if (!arg.startsWith(prefix)) {
            return false;
        }
        System.setProperty(propertyName, arg.substring(prefix.length()));
        return true;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String[] compact(String[] values, int count) {
        if (count == values.length) {
            return values;
        }
        String[] compact = new String[count];
        for (int i = 0; i < count; i++) {
            compact[i] = values[i];
        }
        return compact;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 0 ? trimmed : null;
    }
}
