package io.github.libfdx.graphics.camera;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Ray;
import io.github.libfdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        Ray ray = camera.getPickRay(400.0f, 300.0f);

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

        Ray ray = camera.getPickRay(8.0f, 0.0f);

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
}
