package io.github.libfdx.tools.project.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describes a repository sample bundled into the project generator.
 *
 * @author xpenatan
 */
public final class ProjectSample {
    private final String id;
    private final String displayName;
    private final String description;
    private final List<ProjectPlatform> platforms;

    /**
     * Creates a bundled sample description.
     *
     * @param id the stable repository-relative sample identifier
     * @param displayName the user-facing sample name
     * @param description the sample summary
     * @param platforms the platforms supplied by the sample
     */
    public ProjectSample(String id, String displayName, String description, ProjectPlatform[] platforms) {
        this.id = id != null ? id : "";
        this.displayName = displayName != null ? displayName : this.id;
        this.description = description != null ? description : "";
        ArrayList<ProjectPlatform> available = new ArrayList<ProjectPlatform>();
        if (platforms != null) {
            for (int i = 0; i < platforms.length; i++) {
                ProjectPlatform platform = platforms[i];
                if (platform != null && !available.contains(platform)) {
                    available.add(platform);
                }
            }
        }
        this.platforms = Collections.unmodifiableList(available);
    }

    /**
     * Returns the stable repository-relative sample identifier.
     *
     * @return the sample identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the user-facing sample name.
     *
     * @return the display name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the sample summary.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the platform modules supplied by this sample.
     *
     * @return the available platforms
     */
    public List<ProjectPlatform> platforms() {
        return platforms;
    }

    /**
     * Returns whether this sample supplies the requested platform.
     *
     * @param platform the platform
     * @return true when the platform is available
     */
    public boolean supports(ProjectPlatform platform) {
        return platform != null && platforms.contains(platform);
    }
}
