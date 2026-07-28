package io.github.libfdx.tools.project.generator;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Selects the starting point, package, and platforms used for generation.
 *
 * @author xpenatan
 */
public final class ProjectGenerationSettings {
    public static final String DEFAULT_PROJECT_NAME = "libfdx-game";
    public static final String DEFAULT_PACKAGE_NAME = "com.example.game";
    public static final String DEFAULT_SAMPLE_ID = "base/starter-project";

    private final String projectName;
    private final String packageName;
    private final String sampleId;
    private final Set<ProjectPlatform> platforms;

    private ProjectGenerationSettings(Builder builder) {
        projectName = clean(builder.projectName, DEFAULT_PROJECT_NAME);
        packageName = clean(builder.packageName, DEFAULT_PACKAGE_NAME);
        sampleId = clean(builder.sampleId, DEFAULT_SAMPLE_ID);
        EnumSet<ProjectPlatform> selected = EnumSet.noneOf(ProjectPlatform.class);
        selected.addAll(builder.platforms);
        platforms = Collections.unmodifiableSet(selected);
    }

    /**
     * Returns a new settings builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the generated Gradle root-project name.
     *
     * @return the project name
     */
    public String projectName() {
        return projectName;
    }

    /**
     * Returns the Java package used by customizable starting points.
     *
     * @return the package name
     */
    public String packageName() {
        return packageName;
    }

    /**
     * Returns the stable identifier of the bundled sample to copy.
     *
     * @return the sample identifier
     */
    public String sampleId() {
        return sampleId;
    }

    /**
     * Returns the platform modules to include.
     *
     * @return the selected platforms
     */
    public Set<ProjectPlatform> platforms() {
        return platforms;
    }

    /**
     * Returns whether a platform is selected.
     *
     * @param platform the platform
     * @return true when selected
     */
    public boolean includes(ProjectPlatform platform) {
        return platforms.contains(platform);
    }

    private static String clean(String value, String fallback) {
        String trimmed = value != null ? value.trim() : "";
        return trimmed.length() > 0 ? trimmed : fallback;
    }

    /**
     * Builds project-generation settings.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private String projectName = DEFAULT_PROJECT_NAME;
        private String packageName = DEFAULT_PACKAGE_NAME;
        private String sampleId = DEFAULT_SAMPLE_ID;
        private final EnumSet<ProjectPlatform> platforms = EnumSet.of(ProjectPlatform.DESKTOP);

        private Builder() {
        }

        /**
         * Sets the generated Gradle root-project name.
         *
         * @param projectName the project name
         * @return this builder for chaining
         */
        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        /**
         * Sets the Java package for customizable starting points.
         *
         * @param packageName the package name
         * @return this builder for chaining
         */
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        /**
         * Selects the bundled repository sample to copy.
         *
         * @param sampleId the stable sample identifier
         * @return this builder for chaining
         */
        public Builder sampleId(String sampleId) {
            this.sampleId = sampleId;
            return this;
        }

        /**
         * Replaces the selected platforms.
         *
         * @param selectedPlatforms the platforms to include
         * @return this builder for chaining
         */
        public Builder platforms(ProjectPlatform... selectedPlatforms) {
            platforms.clear();
            if (selectedPlatforms != null) {
                for (int i = 0; i < selectedPlatforms.length; i++) {
                    if (selectedPlatforms[i] != null) {
                        platforms.add(selectedPlatforms[i]);
                    }
                }
            }
            return this;
        }

        /**
         * Selects or clears one platform.
         *
         * @param platform the platform
         * @param selected whether it is selected
         * @return this builder for chaining
         */
        public Builder platform(ProjectPlatform platform, boolean selected) {
            if (platform != null) {
                if (selected) {
                    platforms.add(platform);
                } else {
                    platforms.remove(platform);
                }
            }
            return this;
        }

        /**
         * Creates the immutable settings.
         *
         * @return the settings
         */
        public ProjectGenerationSettings build() {
            return new ProjectGenerationSettings(this);
        }
    }
}
