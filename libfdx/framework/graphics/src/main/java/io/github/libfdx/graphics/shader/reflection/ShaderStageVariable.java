package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable logical input or output of a shader entry point.
 */
public final class ShaderStageVariable {
    public static final int ABSENT = -1;

    private final String name;
    private final String variableName;
    private final int location;
    private final int color;
    private final int blendSource;
    private final ShaderValueType valueType;
    private final ShaderInterpolation interpolation;
    private final ShaderInterpolationSampling sampling;

    private ShaderStageVariable(String name, String variableName, int location, int color, int blendSource,
            ShaderValueType valueType, ShaderInterpolation interpolation, ShaderInterpolationSampling sampling) {
        this.name = requireName(name, "Shader stage-variable name");
        this.variableName = variableName != null ? variableName : "";
        this.location = requireOptional(location, "location");
        this.color = requireOptional(color, "color");
        this.blendSource = requireOptional(blendSource, "blend source");
        if (valueType == null || (valueType.kind() != ShaderValueKind.SCALAR
                && valueType.kind() != ShaderValueKind.VECTOR)) {
            throw new FdxException("Shader stage-variable type must be a scalar or vector");
        }
        this.valueType = valueType;
        this.interpolation = interpolation != null ? interpolation : ShaderInterpolation.UNKNOWN;
        this.sampling = sampling != null ? sampling : ShaderInterpolationSampling.UNKNOWN;
    }

    /**
     * Creates a stage variable.
     *
     * @param name the logical name
     * @param variableName the source variable name
     * @param location the location, or {@link #ABSENT}
     * @param color the color index, or {@link #ABSENT}
     * @param blendSource the blend-source index, or {@link #ABSENT}
     * @param valueType the scalar or vector type
     * @param interpolation the interpolation
     * @param sampling the interpolation sampling
     * @return the stage variable
     */
    public static ShaderStageVariable of(String name, String variableName, int location, int color, int blendSource,
            ShaderValueType valueType, ShaderInterpolation interpolation, ShaderInterpolationSampling sampling) {
        return new ShaderStageVariable(name, variableName, location, color, blendSource, valueType, interpolation,
                sampling);
    }

    public String name() {
        return name;
    }

    public String variableName() {
        return variableName;
    }

    public int location() {
        return location;
    }

    public int color() {
        return color;
    }

    public int blendSource() {
        return blendSource;
    }

    public ShaderValueType valueType() {
        return valueType;
    }

    public ShaderInterpolation interpolation() {
        return interpolation;
    }

    public ShaderInterpolationSampling sampling() {
        return sampling;
    }

    private static int requireOptional(int value, String label) {
        if (value < ABSENT) {
            throw new FdxException("Shader stage-variable " + label + " cannot be less than -1");
        }
        return value;
    }

    private static String requireName(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new FdxException(label + " cannot be empty");
        }
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ShaderStageVariable other)) {
            return false;
        }
        return location == other.location && color == other.color && blendSource == other.blendSource
                && name.equals(other.name) && variableName.equals(other.variableName)
                && valueType.equals(other.valueType) && interpolation == other.interpolation
                && sampling == other.sampling;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, variableName, location, color, blendSource, valueType, interpolation, sampling);
    }
}
