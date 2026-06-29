package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Updates imported skinned model meshes using CPU animation and skinning.
 *
 * @author xpenatan
 */
public final class CpuSkinnedModelAnimator {
    private final DefaultModelInstance instance;
    private final AnimationController controller;
    private final SkinningPalette[] palettes;
    private final CpuSkinningMeshUpdater[] meshUpdaters;
    private final int[] meshPaletteIndices;

    /**
     * Creates a CPU skinned model animator.
     *
     * @param graphics the graphics context
     * @param instance the model instance
     */
    public CpuSkinnedModelAnimator(GraphicsContext graphics, DefaultModelInstance instance) {
        if (graphics == null) {
            throw new FdxException("CpuSkinnedModelAnimator graphics cannot be null");
        }
        if (instance == null) {
            throw new FdxException("CpuSkinnedModelAnimator instance cannot be null");
        }
        this.instance = instance;
        controller = new AnimationController(instance);

        ArrayList<SkinningPalette> paletteList = new ArrayList<SkinningPalette>();
        ArrayList<CpuSkinningMeshUpdater> updaterList = new ArrayList<CpuSkinningMeshUpdater>();
        ArrayList<Integer> updaterPaletteIndices = new ArrayList<Integer>();
        List<Skin> skins = instance.model().skins();
        for (int i = 0; i < skins.size(); i++) {
            Skin skin = skins.get(i);
            if (skin != null) {
                paletteList.add(new SkinningPalette(skin));
            }
        }
        collectSkinnedParts(graphics, instance.model().nodes(), paletteList, updaterList, updaterPaletteIndices);

        palettes = paletteList.toArray(new SkinningPalette[0]);
        meshUpdaters = updaterList.toArray(new CpuSkinningMeshUpdater[0]);
        meshPaletteIndices = new int[updaterPaletteIndices.size()];
        for (int i = 0; i < updaterPaletteIndices.size(); i++) {
            meshPaletteIndices[i] = updaterPaletteIndices.get(i).intValue();
        }
    }

    /**
     * Starts playing an animation clip and updates skinned meshes.
     *
     * @param clip the clip
     * @param looping the looping
     * @return this animator for chaining
     */
    public CpuSkinnedModelAnimator play(AnimationClip clip, boolean looping) {
        controller.play(clip, looping);
        return updateSkinning();
    }

    /**
     * Sets animation time and updates skinned meshes.
     *
     * @param timeSeconds the time seconds
     * @return this animator for chaining
     */
    public CpuSkinnedModelAnimator time(float timeSeconds) {
        controller.time(timeSeconds);
        return updateSkinning();
    }

    /**
     * Advances animation time and updates skinned meshes.
     *
     * @param deltaSeconds the delta seconds
     * @return this animator for chaining
     */
    public CpuSkinnedModelAnimator update(float deltaSeconds) {
        controller.update(deltaSeconds);
        return updateSkinning();
    }

    /**
     * Stops animation playback.
     *
     * @return this animator for chaining
     */
    public CpuSkinnedModelAnimator stop() {
        controller.stop();
        return this;
    }

    /**
     * Updates skinned meshes from the current instance node transforms.
     *
     * @return this animator for chaining
     */
    public CpuSkinnedModelAnimator updateSkinning() {
        for (int i = 0; i < palettes.length; i++) {
            palettes[i].update(instance);
        }
        for (int i = 0; i < meshUpdaters.length; i++) {
            meshUpdaters[i].update(palettes[meshPaletteIndices[i]]);
        }
        return this;
    }

    /**
     * Returns the model instance.
     *
     * @return the model instance
     */
    public DefaultModelInstance instance() {
        return instance;
    }

    /**
     * Returns the animation controller.
     *
     * @return the animation controller
     */
    public AnimationController controller() {
        return controller;
    }

    /**
     * Returns the skin count.
     *
     * @return the skin count
     */
    public int skinCount() {
        return palettes.length;
    }

    /**
     * Returns the skinned part count.
     *
     * @return the skinned part count
     */
    public int skinnedPartCount() {
        return meshUpdaters.length;
    }

    private static void collectSkinnedParts(GraphicsContext graphics, List<ModelNode> nodes,
            ArrayList<SkinningPalette> palettes, ArrayList<CpuSkinningMeshUpdater> updaters,
            ArrayList<Integer> updaterPaletteIndices) {
        for (int i = 0; i < nodes.size(); i++) {
            ModelNode node = nodes.get(i);
            List<ModelNodePart> parts = node.parts();
            for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                ModelNodePart part = parts.get(partIndex);
                Skin skin = part.skin();
                int[] joints = part.joints();
                float[] weights = part.weights();
                if (skin == null || joints.length == 0 || weights.length == 0) {
                    continue;
                }
                int paletteIndex = paletteIndex(skin, palettes);
                if (paletteIndex < 0) {
                    palettes.add(new SkinningPalette(skin));
                    paletteIndex = palettes.size() - 1;
                }
                updaters.add(new CpuSkinningMeshUpdater(graphics, part.meshPart().mesh(), joints, weights));
                updaterPaletteIndices.add(Integer.valueOf(paletteIndex));
            }
            collectSkinnedParts(graphics, node.children(), palettes, updaters, updaterPaletteIndices);
        }
    }

    private static int paletteIndex(Skin skin, ArrayList<SkinningPalette> palettes) {
        for (int i = 0; i < palettes.size(); i++) {
            if (palettes.get(i).skin() == skin) {
                return i;
            }
        }
        return -1;
    }
}
