package io.github.libfdx.tools.project.generator;

/**
 * Identifies a platform module that can be included in a generated project.
 *
 * @author xpenatan
 */
public enum ProjectPlatform {
    DESKTOP("desktop", "Desktop"),
    ANDROID("android", "Android"),
    WEB("web", "Web"),
    DESKTOP_C("desktop_c", "Desktop C"),
    IOS_C("ios_c", "iOS C");

    private final String directory;
    private final String displayName;

    ProjectPlatform(String directory, String displayName) {
        this.directory = directory;
        this.displayName = displayName;
    }

    /**
     * Returns the directory name below {@code platform}.
     *
     * @return the platform directory
     */
    public String directory() {
        return directory;
    }

    /**
     * Returns the user-facing platform name.
     *
     * @return the display name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Resolves a platform from its sample directory name.
     *
     * @param directory the directory name
     * @return the matching platform, or {@code null}
     */
    public static ProjectPlatform fromDirectory(String directory) {
        ProjectPlatform[] values = values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].directory.equals(directory)) {
                return values[i];
            }
        }
        return null;
    }
}
