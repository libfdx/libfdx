package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable shader-stage visibility mask.
 */
public final class ShaderStageVisibility {
    public static final int VERTEX_BIT = 1;
    public static final int FRAGMENT_BIT = 1 << 1;
    public static final int COMPUTE_BIT = 1 << 2;
    public static final int ALL_BITS = VERTEX_BIT | FRAGMENT_BIT | COMPUTE_BIT;
    public static final ShaderStageVisibility NONE = new ShaderStageVisibility(0);
    public static final ShaderStageVisibility VERTEX = new ShaderStageVisibility(VERTEX_BIT);
    public static final ShaderStageVisibility FRAGMENT = new ShaderStageVisibility(FRAGMENT_BIT);
    public static final ShaderStageVisibility COMPUTE = new ShaderStageVisibility(COMPUTE_BIT);
    public static final ShaderStageVisibility ALL = new ShaderStageVisibility(ALL_BITS);

    private final int mask;

    private ShaderStageVisibility(int mask) {
        this.mask = mask;
    }

    /**
     * Creates visibility for the supplied stages.
     *
     * @param stages the stages
     * @return the visibility
     */
    public static ShaderStageVisibility of(ShaderStage... stages) {
        if (stages == null || stages.length == 0) {
            return NONE;
        }
        int mask = 0;
        for (ShaderStage stage : stages) {
            if (stage == null) {
                throw new FdxException("Shader visibility stage cannot be null");
            }
            mask |= bit(stage);
        }
        return fromMask(mask);
    }

    /**
     * Creates visibility from the stable public bit mask.
     *
     * @param mask the visibility mask
     * @return the visibility
     */
    public static ShaderStageVisibility fromMask(int mask) {
        if ((mask & ~ALL_BITS) != 0) {
            throw new FdxException("Shader visibility contains unknown stage bits: " + mask);
        }
        return switch (mask) {
            case 0 -> NONE;
            case VERTEX_BIT -> VERTEX;
            case FRAGMENT_BIT -> FRAGMENT;
            case COMPUTE_BIT -> COMPUTE;
            case ALL_BITS -> ALL;
            default -> new ShaderStageVisibility(mask);
        };
    }

    public int mask() {
        return mask;
    }

    public boolean contains(ShaderStage stage) {
        return stage != null && (mask & bit(stage)) != 0;
    }

    private static int bit(ShaderStage stage) {
        return switch (stage) {
            case VERTEX -> VERTEX_BIT;
            case FRAGMENT -> FRAGMENT_BIT;
            case COMPUTE -> COMPUTE_BIT;
        };
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderStageVisibility other && mask == other.mask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mask);
    }

    @Override
    public String toString() {
        return "ShaderStageVisibility[" + mask + ']';
    }
}
