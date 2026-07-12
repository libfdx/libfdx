package io.github.libfdx.tools.project.generator;

/**
 * Represents a project generation settings.
 *
 * @author xpenatan
 */
public final class ProjectGenerationSettings {
    public static final String DEFAULT_PROJECT_NAME = "libfdx-game";
    public static final String DEFAULT_PACKAGE_NAME = "com.example.game";
    public static final String DEFAULT_APPLICATION_CLASS_NAME = "GameApplication";
    public static final String DEFAULT_DESKTOP_LAUNCHER_CLASS_NAME = "DesktopLauncher";
    public static final String DEFAULT_LIBFDX_VERSION = "0.0.2-SNAPSHOT";

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

    /**
     * Returns the builder.
     *
     * @return the created value
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the project name.
     *
     * @return the project name
     */
    public String projectName() {
        return projectName;
    }

    /**
     * Returns the package name.
     *
     * @return the package name
     */
    public String packageName() {
        return packageName;
    }

    /**
     * Returns the package path.
     *
     * @return the package path
     */
    public String packagePath() {
        return packageName.replace('.', '/');
    }

    /**
     * Returns the application class name.
     *
     * @return the application class name
     */
    public String applicationClassName() {
        return applicationClassName;
    }

    /**
     * Returns the desktop package name.
     *
     * @return the desktop package name
     */
    public String desktopPackageName() {
        return packageName + ".desktop";
    }

    /**
     * Returns the desktop package path.
     *
     * @return the desktop package path
     */
    public String desktopPackagePath() {
        return desktopPackageName().replace('.', '/');
    }

    /**
     * Returns the desktop launcher class name.
     *
     * @return the desktop launcher class name
     */
    public String desktopLauncherClassName() {
        return desktopLauncherClassName;
    }

    /**
     * Returns the libfdx version.
     *
     * @return the libfdx version
     */
    public String libfdxVersion() {
        return libfdxVersion;
    }

    /**
     * Returns the desktop platform.
     *
     * @return true if desktop platform succeeds or is active; false otherwise
     */
    public boolean desktopPlatform() {
        return desktopPlatform;
    }

    private static String clean(String value, String fallback) {
        String trimmed = value != null ? value.trim() : "";
        return trimmed.length() > 0 ? trimmed : fallback;
    }

    /**
     * Builds value instances and related output.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private String projectName = DEFAULT_PROJECT_NAME;
        private String packageName = DEFAULT_PACKAGE_NAME;
        private String applicationClassName = DEFAULT_APPLICATION_CLASS_NAME;
        private String desktopLauncherClassName = DEFAULT_DESKTOP_LAUNCHER_CLASS_NAME;
        private String libfdxVersion = DEFAULT_LIBFDX_VERSION;
        private boolean desktopPlatform = true;

        private Builder() {
        }

        /**
         * Sets the project name and returns this builder.
         *
         * @param projectName the project name
         * @return this builder for chaining
         */
        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        /**
         * Sets the package name and returns this builder.
         *
         * @param packageName the package name
         * @return this builder for chaining
         */
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        /**
         * Sets the application class name and returns this builder.
         *
         * @param applicationClassName the application class name
         * @return this builder for chaining
         */
        public Builder applicationClassName(String applicationClassName) {
            this.applicationClassName = applicationClassName;
            return this;
        }

        /**
         * Sets the desktop launcher class name and returns this builder.
         *
         * @param desktopLauncherClassName the desktop launcher class name
         * @return this builder for chaining
         */
        public Builder desktopLauncherClassName(String desktopLauncherClassName) {
            this.desktopLauncherClassName = desktopLauncherClassName;
            return this;
        }

        /**
         * Sets the libfdx version and returns this builder.
         *
         * @param libfdxVersion the libfdx version
         * @return this builder for chaining
         */
        public Builder libfdxVersion(String libfdxVersion) {
            this.libfdxVersion = libfdxVersion;
            return this;
        }

        /**
         * Sets the desktop platform and returns this builder.
         *
         * @param desktopPlatform the desktop platform
         * @return this builder for chaining
         */
        public Builder desktopPlatform(boolean desktopPlatform) {
            this.desktopPlatform = desktopPlatform;
            return this;
        }

        /**
         * Returns the build.
         *
         * @return the created value
         */
        public ProjectGenerationSettings build() {
            return new ProjectGenerationSettings(this);
        }
    }
}
