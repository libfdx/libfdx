package io.github.libfdx.ecs;

/** Stable identifiers shared by ECS projects, hosts, and bundles. */
public final class EcsProjectFormat {
    /** Binary project contract version used by external project bundles. */
    public static final int PROJECT_ABI = 6;

    /** Exact libFDX binary contract version accepted by this project API build. */
    public static final String LIBFDX_ABI = "0.0.2";

    /** Project manifest format identifier. */
    public static final String PROJECT_FORMAT = "libfdx.ecs.project";

    /** Current project manifest version. */
    public static final int PROJECT_VERSION = 2;

    private EcsProjectFormat() {
    }
}
