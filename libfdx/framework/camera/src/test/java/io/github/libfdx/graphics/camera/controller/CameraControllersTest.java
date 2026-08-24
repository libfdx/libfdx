package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.math.Vector2;
import io.github.libfdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraControllersTest {
    private static final float EPSILON = 0.001f;

    @Test
    void freeCameraMovesWithBindingsScrollAndBoost() {
        DefaultInput input = new DefaultInput();
        Camera camera = perspectiveCamera();
        FreeCameraController3D controller = new FreeCameraController3D(input, camera)
                .position(0.0f, 0.0f, 0.0f)
                .speed(2.0f)
                .speedRange(0.001f, 100.0f);

        input.dispatchKeyDown(Key.W);
        controller.update(1.0f);
        input.dispatchKeyUp(Key.W);
        assertNear(-2.0f, camera.position().z());

        float beforeScroll = controller.speed();
        input.dispatchScrolled(10, 10, 0.0f, -2.0f);
        controller.update(0.0f);
        assertTrue(controller.speed() > beforeScroll);

        input.dispatchKeyDown(Key.CONTROL_LEFT);
        input.dispatchKeyDown(Key.W);
        float zBeforeBoost = camera.position().z();
        controller.update(1.0f);
        input.dispatchKeyUp(Key.W);
        input.dispatchKeyUp(Key.CONTROL_LEFT);
        assertTrue(camera.position().z() < zBeforeBoost - controller.speed() * 1.5f);
    }

    @Test
    void freeCameraPreservesDiagonalMovementAtFarWorldCoordinates() {
        DefaultInput input = new DefaultInput();
        float diagonal = 0.70710677f;
        Camera camera = perspectiveCamera()
                .position(1_000_000.0f, 0.0f, 1_000_000.0f)
                .direction(-diagonal, 0.0f, -diagonal)
                .update();
        FreeCameraController3D controller = new FreeCameraController3D(input, camera)
                .speed(10.0f);

        input.dispatchKeyDown(Key.W);
        input.dispatchKeyDown(Key.D);
        for(int i = 0; i < 240; i++) {
            controller.update(1.0f / 240.0f);
        }
        input.dispatchKeyUp(Key.W);
        input.dispatchKeyUp(Key.D);

        assertClose(1_000_000.0f, camera.position().x(), Math.ulp(1_000_000.0f));
        assertClose(1_000_000.0f - diagonal * 20.0f, camera.position().z(), Math.ulp(1_000_000.0f));
    }

    @Test
    void disabledFreeCameraIgnoresKeyboardPointerAndScroll() {
        DefaultInput input = new DefaultInput();
        Camera camera = perspectiveCamera();
        FreeCameraController3D controller = new FreeCameraController3D(input, camera)
                .position(0.0f, 0.0f, 0.0f)
                .speed(4.0f)
                .enabled(false);

        input.dispatchKeyDown(Key.W);
        input.dispatchPointerDown(MouseButton.RIGHT, 20, 20);
        input.dispatchPointerMoved(5, 5);
        input.dispatchScrolled(20, 20, 0.0f, -4.0f);
        controller.update(1.0f);

        assertNear(0.0f, camera.position().x());
        assertNear(0.0f, camera.position().y());
        assertNear(0.0f, camera.position().z());
        assertNear(-1.0f, camera.direction().z());
        assertNear(4.0f, controller.speed());
    }

    @Test
    void firstPersonFollowsAnchorWithCustomUpAndDoesNotMoveAnchor() {
        MutableAnchor3D anchor = new MutableAnchor3D(10.0f, 20.0f, 30.0f, 0.0f, 0.0f, 1.0f);
        Camera camera = perspectiveCamera();
        FirstPersonCameraController3D controller = new FirstPersonCameraController3D(null, camera, anchor)
                .eyeOffset(0.0f, 2.0f, 0.0f);

        controller.update(1.0f);

        assertNear(10.0f, anchor.position.x());
        assertNear(20.0f, anchor.position.y());
        assertNear(30.0f, anchor.position.z());
        assertNear(10.0f, camera.position().x());
        assertNear(20.0f, camera.position().y());
        assertNear(32.0f, camera.position().z());
        assertNear(1.0f, camera.direction().x());
        assertNear(1.0f, camera.up().z());
    }

    @Test
    void firstPersonUsesRadialUpForPlanetStyleAnchors() {
        MutableAnchor3D anchor = new MutableAnchor3D(100.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f);
        Camera camera = perspectiveCamera();
        FirstPersonCameraController3D controller = new FirstPersonCameraController3D(null, camera, anchor)
                .eyeOffset(0.0f, 3.0f, 0.0f);

        controller.update(1.0f);

        assertNear(103.0f, camera.position().x());
        assertNear(0.0f, camera.position().y());
        assertNear(0.0f, camera.position().z());
        assertNear(1.0f, camera.up().x());
    }

    @Test
    void thirdPersonFollowsCustomUpAndClampsDistance() {
        MutableAnchor3D anchor = new MutableAnchor3D(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        Camera camera = perspectiveCamera();
        ThirdPersonCameraController3D controller = new ThirdPersonCameraController3D(null, camera, anchor)
                .distanceRange(2.0f, 5.0f)
                .distance(50.0f)
                .offsets(0.0f, 1.0f, 0.0f)
                .damping(0.0f);

        controller.update(1.0f);

        assertNear(0.0f, camera.position().y());
        assertNear(5.0f, distance(camera.position().x(), camera.position().y(), camera.position().z() - 1.0f));
        assertNear(1.0f, camera.up().z());

        anchor.position.set(0.0f, 5.0f, 0.0f);
        controller.update(1.0f);
        assertNear(5.0f, camera.position().y());
    }

    @Test
    void cinematic2DFollowsOffsetZoomRotationAndSmoothsLaterUpdates() {
        MutableAnchor2D anchor = new MutableAnchor2D(5.0f, 7.0f);
        Camera camera = new Camera().viewport(100.0f, 50.0f);
        CinematicCameraController controller = new CinematicCameraController(camera)
                .anchor(anchor)
                .offset2D(2.0f, -1.0f)
                .zoom(0.5f)
                .rotation((float)Math.PI * 0.5f)
                .damping(1.0f);

        controller.update(1.0f);
        assertEquals(CameraProjection.ORTHOGRAPHIC, camera.projection());
        assertNear(7.0f, camera.position().x());
        assertNear(6.0f, camera.position().y());
        assertNear(0.5f, camera.zoom());
        assertNear(-1.0f, camera.up().x());

        anchor.position.set(15.0f, 7.0f);
        controller.update(0.25f);
        assertTrue(camera.position().x() > 7.0f);
        assertTrue(camera.position().x() < 17.0f);
    }

    @Test
    void cinematic3DLooksAtMovingAnchorWithCustomUp() {
        MutableAnchor3D anchor = new MutableAnchor3D(1.0f, 2.0f, 3.0f, 0.0f, 0.0f, 1.0f);
        Camera camera = perspectiveCamera();
        CinematicCameraController controller = new CinematicCameraController(camera)
                .anchor(anchor)
                .orbit(0.0f, 0.0f, 4.0f)
                .offsets3D(0.0f, 2.0f, 0.0f)
                .damping(0.0f);

        controller.update(1.0f);

        assertNear(-3.0f, camera.position().x());
        assertNear(2.0f, camera.position().y());
        assertNear(5.0f, camera.position().z());
        assertNear(1.0f, camera.up().z());

        anchor.position.set(1.0f, 8.0f, 3.0f);
        controller.update(1.0f);
        assertNear(8.0f, camera.position().y());
    }

    @Test
    void cinematic3DFollowsKeyframePathAndLooksAcrossScene() {
        Camera camera = perspectiveCamera();
        KeyframeCinematicCameraPath3D path = new KeyframeCinematicCameraPath3D(4.0f,
                new float[] {
                        0.0f, 1.0f, 6.0f,
                        3.0f, 2.0f, 1.0f,
                        -2.0f, 1.5f, -3.0f
                },
                new float[] {
                        0.0f, 0.8f, 0.0f,
                        1.0f, 1.0f, 0.0f,
                        2.0f, 0.7f, -2.0f
                },
                new float[] {
                        0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 1.0f,
                        0.0f, 1.0f, 0.0f
                }).loop(false);
        CinematicCameraController controller = new CinematicCameraController(camera)
                .path3D(path)
                .pathPlaybackSpeed(0.0f)
                .pathTime(0.0f)
                .damping(0.0f);

        controller.update(0.0f);
        assertNear(0.0f, camera.position().x());
        assertNear(1.0f, camera.position().y());
        assertNear(6.0f, camera.position().z());

        float firstSegment = distance(3.0f, 1.0f, -5.0f);
        float secondSegment = distance(-5.0f, -0.5f, -4.0f);
        float firstKeyframeTime = 4.0f * firstSegment / (firstSegment + secondSegment);
        controller.pathTime(firstKeyframeTime).update(0.0f);
        assertClose(3.0f, camera.position().x(), 0.006f);
        assertClose(2.0f, camera.position().y(), 0.006f);
        assertClose(1.0f, camera.position().z(), 0.006f);
        assertNear(1.0f, camera.up().z());
        assertTrue(camera.direction().x() < 0.0f);

        controller.pathTime(10.0f).update(0.0f);
        assertNear(-2.0f, camera.position().x());
        assertNear(1.5f, camera.position().y());
        assertNear(-3.0f, camera.position().z());
    }

    @Test
    void keyframeCinematicPathSamplesCameraAtConstantDistance() {
        KeyframeCinematicCameraPath3D path = new KeyframeCinematicCameraPath3D(11.0f,
                new float[] {
                        0.0f, 0.0f, 0.0f,
                        1.0f, 0.0f, 0.0f,
                        11.0f, 0.0f, 0.0f
                },
                new float[] {
                        0.0f, 1.0f, 0.0f,
                        1.0f, 1.0f, 0.0f,
                        11.0f, 1.0f, 0.0f
                }).loop(false);
        CinematicCameraPathSample3D sample = new CinematicCameraPathSample3D();

        path.sample(5.5f, sample);

        assertNear(5.5f, sample.cameraX());
        assertNear(0.0f, sample.cameraY());
        assertNear(0.0f, sample.cameraZ());
        assertNear(5.5f, sample.lookAtX());
    }

    @Test
    void keyframeCinematicPathSmoothsCornersBetweenTargets() {
        KeyframeCinematicCameraPath3D path = new KeyframeCinematicCameraPath3D(4.0f,
                new float[] {
                        0.0f, 0.0f, 0.0f,
                        2.0f, 0.0f, 0.0f,
                        2.0f, 2.0f, 0.0f,
                        4.0f, 2.0f, 0.0f
                },
                new float[] {
                        0.0f, 0.0f, -1.0f,
                        2.0f, 0.0f, -1.0f,
                        2.0f, 2.0f, -1.0f,
                        4.0f, 2.0f, -1.0f
                }).loop(false);
        CinematicCameraPathSample3D sample = new CinematicCameraPathSample3D();

        path.sample(1.0f, sample);

        assertTrue(sample.cameraX() > 1.0f);
        assertTrue(sample.cameraX() < 2.0f);
        assertTrue(sample.cameraY() < -0.01f);
    }

    @Test
    void orbitAndOrthographicControllersPreserveEditorStyleBehavior() {
        DefaultInput input = new DefaultInput();
        Camera orbitCamera = perspectiveCamera();
        OrbitCameraController3D orbit = new OrbitCameraController3D(input, orbitCamera)
                .target(0.0f, 0.0f, 0.0f)
                .radius(10.0f);
        float orbitZ = orbitCamera.position().z();
        input.dispatchScrolled(5, 5, 0.0f, -4.0f);
        orbit.update(1.0f);
        assertTrue(orbitCamera.position().z() < orbitZ);

        Camera orthoCamera = new Camera().viewport(100.0f, 100.0f);
        OrthographicCameraController3D orthographic = new OrthographicCameraController3D(input, orthoCamera)
                .position(0.0f, 0.0f, 10.0f)
                .zoomRange(0.01f, 1.0f);
        input.dispatchKeyDown(Key.D);
        orthographic.update(1.0f);
        input.dispatchKeyUp(Key.D);
        assertEquals(CameraProjection.ORTHOGRAPHIC, orthoCamera.projection());
        assertTrue(orthoCamera.position().x() > 0.0f);
    }

    @Test
    void pointerRegionsRoutePointerInputToTheAcceptedControllerOnly() {
        DefaultInput input = new DefaultInput();
        Camera leftCamera = perspectiveCamera();
        Camera rightCamera = perspectiveCamera();
        FreeCameraController3D left = new FreeCameraController3D(input, leftCamera)
                .pointerRegion((x, y) -> x < 50);
        FreeCameraController3D right = new FreeCameraController3D(input, rightCamera)
                .pointerRegion((x, y) -> x >= 50);

        input.dispatchPointerDown(MouseButton.RIGHT, 75, 20);
        input.dispatchPointerMoved(65, 20);
        input.dispatchPointerUp(MouseButton.RIGHT, 65, 20);
        left.update(1.0f);
        right.update(1.0f);

        assertNear(-1.0f, leftCamera.direction().z());
        assertTrue(rightCamera.direction().x() != 0.0f);
    }

    private static Camera perspectiveCamera() {
        return new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(800.0f, 600.0f)
                .nearFar(0.1f, 100.0f)
                .position(0.0f, 0.0f, 0.0f)
                .direction(0.0f, 0.0f, -1.0f)
                .up(0.0f, 1.0f, 0.0f)
                .update();
    }

    private static void assertNear(float expected, float actual) {
        assertEquals(expected, actual, EPSILON);
    }

    private static void assertClose(float expected, float actual, float tolerance) {
        assertEquals(expected, actual, tolerance);
    }

    private static float distance(float x, float y, float z) {
        return (float)Math.sqrt(x * x + y * y + z * z);
    }

    private static final class MutableAnchor2D implements CameraAnchor2D {
        private final Vector2 position;

        MutableAnchor2D(float x, float y) {
            position = new Vector2(x, y);
        }

        @Override
        public void position(Vector2 out) {
            out.set(position);
        }
    }

    private static final class MutableAnchor3D implements CameraAnchor3D {
        private final Vector3 position;
        private final Vector3 up;

        MutableAnchor3D(float x, float y, float z, float upX, float upY, float upZ) {
            position = new Vector3(x, y, z);
            up = new Vector3(upX, upY, upZ);
        }

        @Override
        public void position(Vector3 out) {
            out.set(position);
        }

        @Override
        public void up(Vector3 out) {
            out.set(up);
        }
    }
}
