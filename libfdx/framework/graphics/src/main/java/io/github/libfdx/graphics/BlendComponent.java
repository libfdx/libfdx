package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable blend factors and operation for one color component class.
 */
public final class BlendComponent {
    private final BlendFactor sourceFactor;
    private final BlendFactor destinationFactor;
    private final BlendOperation operation;

    private BlendComponent(BlendFactor sourceFactor,
            BlendFactor destinationFactor, BlendOperation operation) {
        if (sourceFactor == null || destinationFactor == null || operation == null) {
            throw new FdxException("Blend component values cannot be null");
        }
        this.sourceFactor = sourceFactor;
        this.destinationFactor = destinationFactor;
        this.operation = operation;
    }

    public static BlendComponent of(BlendFactor sourceFactor,
            BlendFactor destinationFactor, BlendOperation operation) {
        return new BlendComponent(sourceFactor, destinationFactor, operation);
    }

    public static BlendComponent replace() {
        return of(BlendFactor.ONE, BlendFactor.ZERO, BlendOperation.ADD);
    }

    public BlendFactor sourceFactor() {
        return sourceFactor;
    }

    public BlendFactor destinationFactor() {
        return destinationFactor;
    }

    public BlendOperation operation() {
        return operation;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof BlendComponent other
                && sourceFactor == other.sourceFactor
                && destinationFactor == other.destinationFactor
                && operation == other.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFactor, destinationFactor, operation);
    }
}
