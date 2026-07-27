package io.github.libfdx.graphics;

/**
 * Describes a compute command scope.
 */
public final class ComputePassDescriptor {
    private String label = "";

    public static ComputePassDescriptor create(String label) {
        return new ComputePassDescriptor().label(label);
    }

    public String label() {
        return label;
    }

    public ComputePassDescriptor label(String value) {
        label = value != null ? value : "";
        return this;
    }
}
