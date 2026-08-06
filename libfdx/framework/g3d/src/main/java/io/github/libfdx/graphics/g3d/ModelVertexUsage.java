package io.github.libfdx.graphics.g3d;

/**
 * Selects the vertex attributes emitted by {@link ModelBuilder}.
 *
 * <p>Usage values are bit flags and can be combined with the bitwise OR
 * operator.</p>
 *
 * @author xpenatan
 */
public final class ModelVertexUsage {
    /** Includes vertex positions. Every model requires this usage. */
    public static final long POSITION = 1L;
    /** Includes vertex colors. */
    public static final long COLOR = 1L << 1;
    /** Includes vertex normals. */
    public static final long NORMAL = 1L << 2;
    /**
     * Emits the full standard-PBR vertex layout. This flag requires
     * {@link #COLOR} and {@link #NORMAL}; material factors are read from
     * {@link MaterialAttributes} and {@link PbrAttributes} when the mesh is
     * built.
     */
    public static final long PBR_LAYOUT = 1L << 3;
    /** Preserves the position/color output used by the original builders. */
    public static final long DEFAULT = POSITION | COLOR;
    /** Standard lit geometry accepted by the GPU PBR and shadow pipelines. */
    public static final long STANDARD_PBR = DEFAULT | NORMAL | PBR_LAYOUT;
    /** Includes every independently selectable vertex attribute. */
    public static final long ALL = POSITION | COLOR | NORMAL;

    private ModelVertexUsage() {
    }
}
