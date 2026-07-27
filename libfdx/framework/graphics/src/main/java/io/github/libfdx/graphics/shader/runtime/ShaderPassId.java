package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.internal.ShaderStableId;

import java.util.Objects;

/**
 * Stable renderer/application semantic identifying one technique pass.
 */
public final class ShaderPassId implements Comparable<ShaderPassId> {
    public static final ShaderPassId FORWARD = of("forward");
    public static final ShaderPassId DEPTH = of("depth");
    public static final ShaderPassId SHADOW = of("shadow");
    public static final ShaderPassId PICKING = of("picking");
    public static final ShaderPassId POST_PROCESS = of("post-process");

    private final String value;

    private ShaderPassId(String value) {
        this.value = ShaderStableId.normalize(value, "Shader pass ID");
    }

    public static ShaderPassId of(String value) {
        return new ShaderPassId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public int compareTo(ShaderPassId other) {
        return other != null ? value.compareTo(other.value) : 1;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderPassId other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
