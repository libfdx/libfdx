package io.github.libfdx.graphics.camera;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.controller.FreeCameraController3D;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Ray;
import io.github.libfdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void projectsAndUnprojectsPerspectivePositionWithTopLeftOrigin() {
        Camera camera = perspectiveCamera(800.0f, 600.0f);
        Vector3 world = new Vector3(1.0f, 1.0f, -2.0f);
        Vector3 screen = new Vector3();

        assertSame(screen, camera.project(world, screen));
        assertVector(screen, 550.0f, 150.0f, 50.0f / 99.0f);

        Vector3 roundTrip = new Vector3();
        assertSame(roundTrip, camera.unproject(screen, roundTrip));
        assertVector(roundTrip, world.x(), world.y(), world.z());
    }

    @Test
    void mapsLogicalProjectViewportIndependentlyFromPhysicalCameraViewport() {
        Camera camera = perspectiveCamera(1920.0f, 1080.0f);
        Vector3 world = new Vector3(8.0f / 9.0f, 0.0f, -1.0f);
        Vector3 screen = new Vector3();

        camera.project(world, 0.0f, 0.0f, 1280.0f, 720.0f, screen);

        assertVector(screen, 960.0f, 360.0f, 0.0f);
        camera.unproject(screen, 0.0f, 0.0f, 1280.0f, 720.0f, screen);
        assertVector(screen, world.x(), world.y(), world.z());
    }

    @Test
    void projectsAndUnprojectsOrthographicPositionWithTopLeftOrigin() {
        Camera camera = orthographicCamera();
        Vector3 world = new Vector3(12.0f, 21.5f, 29.0f);
        Vector3 screen = new Vector3();

        camera.project(world, screen);

        assertVector(screen, 8.0f, 0.0f, 0.0f);
        camera.unproject(screen, screen);
        assertVector(screen, world.x(), world.y(), world.z());
    }

    @Test
    void createsPerspectivePickRayFromScreenCenter() {
        Camera camera = perspectiveCamera(800.0f, 600.0f);

        Ray ray = camera.getPickRay(400.0f, 300.0f, new Ray());

        assertVector(ray.origin(), 0.0f, 0.0f, -1.0f);
        assertVector(ray.direction(), 0.0f, 0.0f, -1.0f);
    }

    @Test
    void mapsLogicalScreenViewportIndependentlyFromPhysicalCameraViewport() {
        Camera camera = perspectiveCamera(1920.0f, 1080.0f);
        Ray output = new Ray();

        Ray ray = camera.getPickRay(960.0f, 360.0f,
                0.0f, 0.0f, 1280.0f, 720.0f, output);

        assertSame(output, ray);
        assertEquals(8.0f / 9.0f, ray.origin().x(), EPSILON);
        assertEquals(0.0f, ray.origin().y(), EPSILON);
        assertEquals(-1.0f, ray.origin().z(), EPSILON);
        assertEquals(8.0f / 9.0f, ray.direction().x() / -ray.direction().z(), EPSILON);
    }

    @Test
    void createsOrthographicPickRayFromScreenCorner() {
        Camera camera = orthographicCamera();

        Ray ray = camera.getPickRay(8.0f, 0.0f, new Ray());

        assertVector(ray.origin(), 12.0f, 21.5f, 29.0f);
        assertVector(ray.direction(), 0.0f, 0.0f, -1.0f);
    }

    @Test
    void rejectsInvalidPickRayOutputAndViewport() {
        Camera camera = perspectiveCamera(800.0f, 600.0f);

        assertThrows(FdxException.class,
                () -> camera.getPickRay(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 600.0f));
        assertThrows(FdxException.class,
                () -> camera.getPickRay(0.0f, 0.0f, 0.0f, 0.0f, 800.0f, 600.0f, null));
    }

    @Test
    void rejectsInvalidProjectionArguments() {
        Camera camera = perspectiveCamera(800.0f, 600.0f);
        Vector3 coordinates = new Vector3();

        assertThrows(FdxException.class, () -> camera.project(null, coordinates));
        assertThrows(FdxException.class, () -> camera.project(coordinates, null));
        assertThrows(FdxException.class,
                () -> camera.project(coordinates, 0.0f, 0.0f, -1.0f, 600.0f, coordinates));
        assertThrows(FdxException.class, () -> camera.unproject(null, coordinates));
        assertThrows(FdxException.class, () -> camera.unproject(coordinates, null));
        assertThrows(FdxException.class,
                () -> camera.unproject(coordinates, 0.0f, 0.0f, 800.0f, 0.0f, coordinates));
    }

    @Test
    void updatesPerspectiveCameraAtLargeWorldCoordinates() {
        float targetX = 10_000_000.0f;
        float targetY = 20.0f;
        float positionX = 10_000_038.0f;
        float positionY = 9.581109f;
        float positionZ = 45.26439f;

        Camera camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(2549.0f, 1352.0f)
                .fieldOfView(60.0f)
                .nearFar(0.1f, 1000.0f)
                .position(positionX, positionY, positionZ)
                .lookAt(targetX, targetY, 0.0f);

        new FreeCameraController3D(null, camera).update(0.0f);

        Ray ray = camera.getPickRay(2549.0f * 0.5f, 1352.0f * 0.5f, new Ray());
        assertFinite(ray.origin());
        assertFinite(ray.direction());
    }

    @Test
    void updatesPerspectiveCameraAfterRotatingAtFarWorldCoordinates() {
        float targetX = 1_000_000.0f;
        float targetZ = 1_000_000.0f;

        Camera camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(2549.0f, 1352.0f)
                .fieldOfView(60.0f)
                .nearFar(0.1f, 1000.0f)
                .position(1_000_006.8f, -2.0905693f, 1_000_018.7f)
                .lookAt(targetX, 0.0f, targetZ);

        new FreeCameraController3D(null, camera).update(0.0f);

        Ray ray = camera.getPickRay(2549.0f * 0.5f, 1352.0f * 0.5f, new Ray());
        assertFinite(ray.origin());
        assertFinite(ray.direction());
        assertTrue(ray.direction().dot(camera.direction()) > 0.99f);
    }

    @Test
    void viewRotationIsIndependentOfDistanceFromOrigin() {
        // A large world puts the eye at astronomical coordinates. Deriving the
        // view basis from a look-at target of position + direction collapses
        // there: a unit vector is below one float ULP past ~1.7e7, so the
        // forward vector becomes zero and the matrix silently falls back to
        // looking down -Z. The basis must depend only on direction and up.
        float dirX = 0.6f;
        float dirY = -0.3f;
        float dirZ = -0.74f;

        Camera atOrigin = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(800.0f, 600.0f)
                .fieldOfView(90.0f)
                .nearFar(1.0f, 1000000.0f)
                .position(0.0f, 0.0f, 0.0f)
                .direction(dirX, dirY, dirZ)
                .up(0.0f, 1.0f, 0.0f);

        Camera farOut = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(800.0f, 600.0f)
                .fieldOfView(90.0f)
                .nearFar(1.0f, 1000000.0f)
                .position(-1.475957e11f, 0.0f, -5.0398563e8f)
                .direction(dirX, dirY, dirZ)
                .up(0.0f, 1.0f, 0.0f);

        float[] near = atOrigin.view().values();
        float[] far = farOut.view().values();
        int[] basis = {0, 1, 2, 4, 5, 6, 8, 9, 10};
        for (int index : basis) {
            assertEquals(near[index], far[index], 1.0e-6f,
                    "view basis entry " + index + " changed with eye distance");
        }

        // Matrix4 stores the forward row negated; a degenerate basis would leave
        // a hard-coded (0,0,-1) there instead of the requested direction.
        float length = (float)Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        assertEquals(dirX / length, -far[2], 1.0e-5f);
        assertEquals(dirY / length, -far[6], 1.0e-5f);
        assertEquals(dirZ / length, -far[10], 1.0e-5f);
    }

    private static Camera perspectiveCamera(float viewportWidth, float viewportHeight) {
        return new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(viewportWidth, viewportHeight)
                .fieldOfView(90.0f)
                .nearFar(1.0f, 100.0f)
                .position(0.0f, 0.0f, 0.0f)
                .direction(0.0f, 0.0f, -1.0f)
                .up(0.0f, 1.0f, 0.0f);
    }

    private static Camera orthographicCamera() {
        return new Camera()
                .projection(CameraProjection.ORTHOGRAPHIC)
                .viewport(8.0f, 6.0f)
                .nearFar(1.0f, 100.0f)
                .zoom(0.5f)
                .position(10.0f, 20.0f, 30.0f)
                .direction(0.0f, 0.0f, -1.0f)
                .up(0.0f, 1.0f, 0.0f);
    }

    private static void assertVector(Vector3 vector,
            float expectedX, float expectedY, float expectedZ) {
        assertEquals(expectedX, vector.x(), EPSILON);
        assertEquals(expectedY, vector.y(), EPSILON);
        assertEquals(expectedZ, vector.z(), EPSILON);
    }

    private static void assertFinite(Vector3 vector) {
        assertTrue(Float.isFinite(vector.x()));
        assertTrue(Float.isFinite(vector.y()));
        assertTrue(Float.isFinite(vector.z()));
    }

    @Test
    public void zeroToOneIsTheDefaultAndMapsNearAndFarToZeroAndOne() {
        Camera camera = new Camera();
        assertEquals(ClipDepthRange.ZERO_TO_ONE, camera.clipDepthRange());
        camera.projection(CameraProjection.PERSPECTIVE)
                .viewport(800.0f, 600.0f)
                .nearFar(0.5f, 1000.0f)
                .position(0.0f, 0.0f, 0.0f)
                .direction(0.0f, 0.0f, -1.0f)
                .up(0.0f, 1.0f, 0.0f)
                .update();

        Vector3 atNear = camera.project(new Vector3(0.0f, 0.0f, -0.5f), new Vector3());
        Vector3 atFar = camera.project(new Vector3(0.0f, 0.0f, -1000.0f), new Vector3());
        assertEquals(0.0f, atNear.z(), 1.0e-5f, "near plane depth");
        assertEquals(1.0f, atFar.z(), 1.0e-5f, "far plane depth");
    }

    /**
     * The whole point of the split: a point just past the near plane must
     * survive. Under an OpenGL-convention matrix fed to a zero-to-one clipper
     * its clip z is negative all the way out to twice the near distance, so it
     * would be discarded with no error reported.
     */
    @Test
    public void geometryJustPastNearIsNotClippedUnderZeroToOne() {
        Camera camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(800.0f, 600.0f)
                .nearFar(0.5f, 1000.0f)
                .position(0.0f, 0.0f, 0.0f)
                .direction(0.0f, 0.0f, -1.0f)
                .up(0.0f, 1.0f, 0.0f)
                .update();
        float[] m = camera.projectionMatrix().values();
        float zView = -0.75f;
        float clipZ = m[10] * zView + m[14];
        float clipW = m[11] * zView;
        assertTrue(clipZ >= 0.0f && clipZ <= clipW,
                "0 <= z <= w must hold at 1.5x near, got z=" + clipZ + " w=" + clipW);
    }

    @Test
    public void openGlConversionMatchesTheDirectOpenGlForm() {
        Matrix4 converted = new Matrix4().setToPerspective(
                67.0f, 4.0f / 3.0f, 0.5f, 1000.0f,
                ClipDepthRange.NEGATIVE_ONE_TO_ONE);
        // Near maps to -1 and far to +1 in the OpenGL convention.
        float[] m = converted.values();
        for(float[] probe : new float[][]{{-0.5f, -1.0f}, {-1000.0f, 1.0f}}) {
            float zView = probe[0];
            float ndc = (m[10] * zView + m[14]) / (m[11] * zView);
            assertEquals(probe[1], ndc, 1.0e-4f, "ndc depth at zView " + zView);
        }
    }

    @Test
    public void projectUnprojectRoundTripsInBothConventions() {
        for(ClipDepthRange range : ClipDepthRange.values()) {
            Camera camera = new Camera()
                    .projection(CameraProjection.PERSPECTIVE)
                    .viewport(800.0f, 600.0f)
                    .nearFar(0.5f, 1000.0f)
                    .position(0.0f, 0.0f, 5.0f)
                    .direction(0.0f, 0.0f, -1.0f)
                    .up(0.0f, 1.0f, 0.0f);
            camera.clipDepthRange(range).update();

            Vector3 projected = camera.project(new Vector3(1.0f, -2.0f, -3.0f), new Vector3());
            Vector3 back = camera.unproject(new Vector3(
                    projected.x(), projected.y(), projected.z()), new Vector3());
            assertEquals(1.0f, back.x(), 1.0e-3f, range + " x");
            assertEquals(-2.0f, back.y(), 1.0e-3f, range + " y");
            assertEquals(-3.0f, back.z(), 1.0e-3f, range + " z");
        }
    }
}
