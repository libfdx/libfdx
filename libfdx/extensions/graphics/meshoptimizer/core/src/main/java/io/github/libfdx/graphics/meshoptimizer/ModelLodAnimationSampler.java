package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.g3d.*;
import java.util.ArrayList;
import java.util.Objects;

/** CPU-only deformation sampling shared by runtime capture and offline exporters. Borrows a live,
 * immutable model's hierarchy, defaults and clips; geometry/GPU meshes are not required. Each capture
 * owns its result and samples private instances synchronously. Call on the model owner's thread,
 * or on a worker when the entire borrowed model is worker-owned. Finite samples do not guarantee
 * accuracy for procedural poses, cubic overshoot or arbitrary combinations of morph weights. */
public final class ModelLodAnimationSampler {
    private final Model model;
    private final ModelLodAnimationSettings settings;

    public ModelLodAnimationSampler(Model model, ModelLodAnimationSettings settings) {
        if (model == null || model.isDisposed()) throw new IllegalArgumentException("A live model is required");
        this.model = model;
        this.settings = Objects.requireNonNull(settings);
    }

    /** Positions/deltas use mesh coordinates; four joint influences address the supplied skin.
     * A null skin requires null influences. Returns null for rigid geometry. The model must contain
     * nodeId and every joint node, with matching morph defaults. Input arrays are never modified. */
    public MeshLodDeformation capture(String nodeId, float[] positions, Skin skin,
            int[] joints, float[] weights, MorphTarget[] targets) {
        if (model.isDisposed()) throw new IllegalStateException("Sampling model is disposed");
        targets = targets == null ? new MorphTarget[0] : targets;
        if (skin == null && targets.length == 0) return null;
        if (positions == null || positions.length == 0 || positions.length % 3 != 0
                || (skin == null) != (joints == null) || (joints == null) != (weights == null)
                || joints != null && (joints.length != (long)positions.length / 3 * 4 || weights.length != joints.length))
            throw new IllegalArgumentException("Invalid deformation vertex domain");
        int needed = Math.addExact(1, Math.addExact(Math.multiplyExact(model.animations().size(), settings.samplesPerClip()),
                Math.multiplyExact(targets.length, 2)));
        if (needed > settings.maxPoseSamples()) throw new IllegalArgumentException("Animated LOD needs " + needed
                + " pose samples; raise maxPoseSamples or reduce samplesPerClip");
        ArrayList<float[]> poses = new ArrayList<>(needed);
        DefaultModelInstance instance = new DefaultModelInstance(model);
        poses.add(positions(instance, nodeId, positions, skin, joints, weights, targets));
        for (int a = 0; a < model.animations().size(); a++) {
            instance = new DefaultModelInstance(model);
            AnimationClip clip = model.animations().get(a);
            AnimationController controller = new AnimationController(instance);
            controller.play(clip, false);
            for (int s = 0; s < settings.samplesPerClip(); s++) {
                controller.time((float)((double)clip.durationSeconds() * s / (settings.samplesPerClip() - 1)));
                poses.add(positions(instance, nodeId, positions, skin, joints, weights, targets));
            }
        }
        for (int target = 0; target < targets.length; target++) for (int end = 0; end < 2; end++) {
            instance = new DefaultModelInstance(model);
            float[] morph = new float[targets.length];
            instance.copyMorphWeights(nodeId, morph, 0);
            morph[target] = end == 0 ? settings.minimumMorphWeight() : settings.maximumMorphWeight();
            instance.morphWeights(nodeId, morph);
            poses.add(positions(instance, nodeId, positions, skin, joints, weights, targets));
        }
        return new MeshLodDeformation(positions.length / 3, joints, weights, targets, poses.toArray(new float[0][]));
    }

    private static float[] positions(DefaultModelInstance instance, String nodeId, float[] source,
            Skin skin, int[] joints, float[] weights, MorphTarget[] targets) {
        instance.copyNodeTransform(nodeId, new io.github.libfdx.math.Matrix4());
        float[] output = new float[source.length], morph = new float[targets.length];
        instance.copyMorphWeights(nodeId, morph, 0);
        float[] palette = skin == null ? null : new SkinningPalette(skin).update(instance).values();
        for (int v = 0; v < source.length / 3; v++) {
            double x = source[v * 3], y = source[v * 3 + 1], z = source[v * 3 + 2];
            for (int t = 0; t < morph.length; t++) {
                x += morph[t] * targets[t].delta(0, v, 0);
                y += morph[t] * targets[t].delta(0, v, 1);
                z += morph[t] * targets[t].delta(0, v, 2);
            }
            double px = x, py = y, pz = z;
            if (palette != null) {
                double sum = 0;
                for (int i = 0; i < 4; i++) sum += weights[v * 4 + i];
                if (sum > 0) {
                    px = py = pz = 0;
                    for (int i = 0; i < 4; i++) {
                        int joint = joints[v * 4 + i];
                        if (joint < 0 || joint >= palette.length / 16) throw new IllegalArgumentException("LOD joint index outside skin palette");
                        double w = weights[v * 4 + i] / sum;
                        int m = joint * 16;
                        px += w * (palette[m] * x + palette[m + 4] * y + palette[m + 8] * z + palette[m + 12]);
                        py += w * (palette[m + 1] * x + palette[m + 5] * y + palette[m + 9] * z + palette[m + 13]);
                        pz += w * (palette[m + 2] * x + palette[m + 6] * y + palette[m + 10] * z + palette[m + 14]);
                    }
                }
            }
            output[v * 3] = (float)px; output[v * 3 + 1] = (float)py; output[v * 3 + 2] = (float)pz;
        }
        return output;
    }
}
