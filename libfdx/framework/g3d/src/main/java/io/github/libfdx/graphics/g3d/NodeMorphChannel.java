package io.github.libfdx.graphics.g3d;

/** Immutable node-targeted morph-weight animation track. Values may be negative or exceed one. */
public record NodeMorphChannel(String nodeId, AnimationSampler sampler) {
    public NodeMorphChannel {
        if (nodeId == null || nodeId.trim().isEmpty() || sampler == null || sampler.isRotation())
            throw new IllegalArgumentException("Morph channel needs a named node and vector sampler");
    }
}
