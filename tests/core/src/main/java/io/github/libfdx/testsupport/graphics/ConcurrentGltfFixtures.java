package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DefaultRenderQueue3D;
import io.github.libfdx.math.Vector3;

/** Existing, distinct fixtures with a mix of large meshes, textures and shared dependencies. */
public final class ConcurrentGltfFixtures {
    private static final String[] PATHS = {
            "data/g3d/gltf/Ducky/ducky.gltf",
            "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf",
            "data/g3d/gltf/StanfordDragon/stanfordDragon.gltf",
            "showcase/sculpture.gltf",
            "gltf-materials/authored.gltf",
            "gltf-materials/reference.gltf",
            "gltf-materials/khronos/TextureTransformTest.gltf",
            "gltf-materials/khronos/reference.gltf",
            "gltf-materials/khronos-mirror/NormalTangentMirrorTest.gltf",
            "ibl/authored.gltf",
            "ibl/reference.gltf",
            "gltf-animation/mixed.gltf"
    };
    public static int count() { return PATHS.length; }
    public static String path(int index) { return PATHS[index]; }
    public static String joinedPaths() { return String.join("\n", PATHS); }
    public static String[] deferredDirectories() {
        String[] result = new String[PATHS.length];
        for (int i = 0; i < PATHS.length; i++) result[i] = PATHS[i].substring(0, PATHS[i].lastIndexOf('/') + 1);
        return result;
    }

    /** Fits each complete scene inside one grid cell, including authored node transforms. */
    public static void position(DefaultModelInstance instance, int index) {
        var queue = new DefaultRenderQueue3D();
        instance.collectRenderables(queue);
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        Vector3 point = new Vector3();
        for (int i = 0; i < queue.size(); i++) {
            var part = queue.get(i);
            var bounds = part.bounds();
            for (int corner = 0; corner < 8; corner++) {
                point.set((corner & 1) == 0 ? bounds.min().x() : bounds.max().x(),
                        (corner & 2) == 0 ? bounds.min().y() : bounds.max().y(),
                        (corner & 4) == 0 ? bounds.min().z() : bounds.max().z());
                part.worldTransform().transformPosition(point, point);
                minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x());
                minY = Math.min(minY, point.y()); maxY = Math.max(maxY, point.y());
                minZ = Math.min(minZ, point.z()); maxZ = Math.max(maxZ, point.z());
            }
        }
        float extent = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        if (!Float.isFinite(extent) || extent <= 0) throw new IllegalStateException("Empty model: " + path(index));
        float scale = 1.9f / extent;
        instance.transform().setToTranslation((index % 4 - 1.5f) * 2.6f, (1 - index / 4) * 2.6f, 0)
                .scale(scale, scale, scale)
                .translate(-(minX + maxX) * .5f, -(minY + maxY) * .5f, -(minZ + maxZ) * .5f);
    }
    private ConcurrentGltfFixtures() { }
}
