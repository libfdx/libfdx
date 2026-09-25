package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.g3d.*;

/** Loaded-mesh adapter for the shared CPU-only sampler. */
final class LodAnimationSamples {
    private final ModelLodAnimationSampler sampler;
    LodAnimationSamples(Model model, ModelLodAnimationSettings settings) {
        sampler = new ModelLodAnimationSampler(model, settings);
    }
    MeshLodDeformation capture(ModelNode node, ModelNodePart part) {
        return sampler.capture(node.id(), part.meshPart().mesh().sourcePositions(), part.skin(),
                part.skin() == null ? null : part.joints(), part.skin() == null ? null : part.weights(), part.morphTargets());
    }
}
