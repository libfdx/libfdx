package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DefaultRenderQueue3DTest {
    private final Mesh mesh = new Mesh(new DefaultRenderQueue3DAllocationTest.FakeGraphicsContext(),
            "ordering-mesh", Mesh.POSITION_COLOR_LAYOUT,
            new float[] {
                    0, 0, 0, 1, 1, 1, 1,
                    1, 0, 0, 1, 1, 1, 1,
                    0, 1, 0, 1, 1, 1, 1
            }, 3, BoundingBox.empty());
    private final DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
    private final Camera camera = new Camera().position(0, 0, 0).direction(0, 0, -1);

    @AfterEach
    void dispose() {
        mesh.dispose();
    }

    @Test
    void keepsOpaqueAndMaskedStateGroupsBeforeDepthSortedBlends() {
        Renderable3D near = renderable("a-near", MaterialAlphaMode.BLEND, 0, 0, -1);
        Renderable3D far = renderable("z-far", MaterialAlphaMode.BLEND, 0, 0, -10);
        Renderable3D opaqueB = renderable("b-opaque", MaterialAlphaMode.OPAQUE, 0, 0, -20);
        Renderable3D mask = renderable("mask", MaterialAlphaMode.MASK, 0, 0, -30);
        Renderable3D opaqueA = renderable("a-opaque", MaterialAlphaMode.OPAQUE, 0, 0, -2);
        add(near, opaqueB, far, mask, opaqueA);

        queue.sort(camera);

        assertOrder(opaqueA, opaqueB, mask, far, near);
    }

    @Test
    void usesViewDepthRatherThanDistanceAndUpdatesWhenCameraTurns() {
        Renderable3D lateralNear = renderable("a-near", MaterialAlphaMode.BLEND, 100, 0, -2);
        Renderable3D far = renderable("z-far", MaterialAlphaMode.BLEND, 0, 0, -10);
        add(lateralNear, far);
        queue.sort(camera);
        assertOrder(far, lateralNear);

        camera.position(-5, 0, 0).direction(1, 0, 0);
        queue.sort(camera);
        assertOrder(lateralNear, far);
    }

    @Test
    void transformsLocalBoundsCenterIncludingRotationAndScale() {
        Renderable3D transformed = new Renderable3D(new MeshPart(mesh, 0, 3),
                new Material("a-transformed").alphaMode(MaterialAlphaMode.BLEND),
                new Matrix4().setToTranslation(0, 0, -3)
                        .rotateY((float)(Math.PI * 0.5)).scale(2, 1, 1),
                BoundingBox.of(new Vector3(1, -1, -1), new Vector3(3, 1, 1)));
        Renderable3D other = renderable("z-other", MaterialAlphaMode.BLEND, 0, 0, -5);
        add(other, transformed);

        queue.sort(camera);

        // The local center (2,0,0) ends up at world Z=-7, behind the other object.
        assertOrder(transformed, other);
    }

    @Test
    void preservesLocalDepthDifferencesFarFromWorldOrigin() {
        float worldZ = 1.0e11f;
        Renderable3D near = new Renderable3D(new MeshPart(mesh, 0, 3),
                new Material("a-near").alphaMode(MaterialAlphaMode.BLEND),
                new Matrix4().setToTranslation(0, 0, worldZ),
                BoundingBox.of(new Vector3(0, 0, 1), new Vector3(0, 0, 3)));
        Renderable3D far = new Renderable3D(new MeshPart(mesh, 0, 3),
                new Material("z-far").alphaMode(MaterialAlphaMode.BLEND),
                new Matrix4().setToTranslation(0, 0, worldZ),
                BoundingBox.of(new Vector3(0, 0, 3), new Vector3(0, 0, 5)));
        camera.position(0, 0, worldZ).direction(0, 0, 1);
        add(near, far);

        queue.sort(camera);

        assertOrder(far, near);
    }

    @Test
    void breaksEqualDepthTiesByStateAndPreservesEqualKeySubmissionOrder() {
        Renderable3D sameFirst = renderable("same", MaterialAlphaMode.BLEND, 1, 0, -5);
        Renderable3D sameSecond = renderable("same", MaterialAlphaMode.BLEND, -1, 0, -5);
        Renderable3D earlierState = renderable("a", MaterialAlphaMode.BLEND, 0, 0, -5);
        add(sameFirst, sameSecond, earlierState);

        queue.sort(camera);
        assertOrder(earlierState, sameFirst, sameSecond);
        queue.sort(camera);
        assertOrder(earlierState, sameFirst, sameSecond);
    }

    @Test
    void refreshesDepthsWhenReusingTheQueueAndMutatingTransforms() {
        Renderable3D a = renderable("a", MaterialAlphaMode.BLEND, 0, 0, -1);
        Renderable3D z = renderable("z", MaterialAlphaMode.BLEND, 0, 0, -10);
        add(a, z);
        queue.sort(camera);
        assertOrder(z, a);

        queue.clear();
        a.worldTransform().setToTranslation(0, 0, -20);
        add(z, a);
        queue.sort(camera);
        assertOrder(a, z);
    }

    @Test
    void rejectsMissingCameraAndNonFiniteBlendedDepth() {
        assertThrows(FdxException.class, () -> queue.sort(null));
        add(renderable("invalid", MaterialAlphaMode.BLEND, 0, 0, Float.NaN),
                renderable("finite", MaterialAlphaMode.BLEND, 0, 0, -1));
        assertThrows(FdxException.class, () -> queue.sort(camera));
    }

    private Renderable3D renderable(String materialId, MaterialAlphaMode alphaMode,
            float x, float y, float z) {
        return new Renderable3D(new MeshPart(mesh, 0, 3),
                new Material(materialId).alphaMode(alphaMode),
                new Matrix4().setToTranslation(x, y, z), null);
    }

    private void add(Renderable3D... values) {
        for (Renderable3D value : values) {
            queue.add(value);
        }
    }

    private void assertOrder(Renderable3D... values) {
        for (int i = 0; i < values.length; i++) {
            assertSame(values[i], queue.get(i), "draw at index " + i);
        }
    }
}
