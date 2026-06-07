package io.github.libfdx.tools.project.generator;

public final class ProjectGenerationSettings {
    public static final String DEFAULT_PROJECT_NAME = "libfdx-game";
    public static final String DEFAULT_PACKAGE_NAME = "com.example.game";
    public static final String DEFAULT_APPLICATION_CLASS_NAME = "GameApplication";
    public static final String DEFAULT_DESKTOP_LAUNCHER_CLASS_NAME = "DesktopLauncher";
    public static final String DEFAULT_LIBFDX_VERSION = "-SNAPSHOT";

    private final String projectName;
    private final String packageName;
    private final String applicationClassName;
    private final String desktopLauncherClassName;
    private final String libfdxVersion;
    private final boolean desktopPlatform;

    private ProjectGenerationSettings(Builder builder) {
        projectName = clean(builder.projectName, DEFAULT_PROJECT_NAME);
        packageName = clean(builder.packageName, DEFAULT_PACKAGE_NAME);
        applicationClassName = clean(builder.applicationClassName, DEFAULT_APPLICATION_CLASS_NAME);
        desktopLauncherClassName = clean(builder.desktopLauncherClassName, DEFAULT_DESKTOP_LAUNCHER_CLASS_NAME);
        libfdxVersion = clean(builder.libfdxVersion, DEFAULT_LIBFDX_VERSION);
        desktopPlatform = builder.desktopPlatform;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String projectName() {
        return projectName;
    }

    public String packageName() {
        return packageName;
    }

    public String packagePath() {
        return packageName.replace('.', '/');
    }

    public String applicationClassName() {
        return applicationClassName;
    }

    public String desktopPackageName() {
        return packageName + ".desktop";
    }

    public String desktopPackagePath() {
        return desktopPackageName().replace('.', '/');
    }

    public String desktopLauncherClassName() {
        return desktopLauncherClassName;
    }

    public String libfdxVersion() {
        return libfdxVersion;
    }

    public boolean desktopPlatform() {
        return desktopPlatform;
    }

    private static String clean(String value, String fallback) {
        String trimmed = value != null ? value.trim() : "";
        return trimmed.length() > 0 ? trimmed : fallback;
    }

    public static final class Builder {
        private String projectName = DEFAULT_PROJECT_NAME;
        private String packageName = DEFAULT_PACKAGE_NAME;
        private String applicationClassName = DEFAULT_APPLICATION_CLASS_NAME;
        private String desktopLauncherClassName = DEFAULT_DESKTOP_LAUNCHER_CLASS_NAME;
        private String libfdxVersion = DEFAULT_LIBFDX_VERSION;
        private boolean desktopPlatform = true;

        private Builder() {
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder applicationClassName(String applicationClassName) {
            this.applicationClassName = applicationClassName;
            return this;
        }

        public Builder desktopLauncherClassName(String desktopLauncherClassName) {
            this.desktopLauncherClassName = desktopLauncherClassName;
            return this;
        }

        public Builder libfdxVersion(String libfdxVersion) {
            this.libfdxVersion = libfdxVersion;
            return this;
        }

        public Builder desktopPlatform(boolean desktopPlatform) {
            this.desktopPlatform = desktopPlatform;
            return this;
        }

        public ProjectGenerationSettings build() {
            return new ProjectGenerationSettings(this);
        }
    }
}
