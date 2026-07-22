package io.github.libfdx.ecs.tooling;

/** Stable identifiers shared by ECS projects, tools, and scene files. */
public final class EcsTooling {
    /** Binary/tooling contract version used by external project bundles. */
    public static final int TOOLING_ABI = 1;

    /** Exact libFDX binary contract version accepted by this tooling build. */
    public static final String LIBFDX_ABI = "0.0.2";

    /** Project manifest format identifier. */
    public static final String PROJECT_FORMAT = "libfdx.ecs.project";

    /** Scene document format identifier. */
    public static final String SCENE_FORMAT = "libfdx.ecs.scene";

    /** Current scene document version. */
    public static final int SCENE_VERSION = 1;

    private EcsTooling() {
    }
}
