package io.github.libfdx.tests.graphics;

final class TestCameraControllers {
    private static final String CAMERA_ORBIT_PROPERTY = "libfdx.test.cameraOrbit";
    private static final String CAMERA_ORBIT_START_PROPERTY = "libfdx.test.cameraOrbitStartDegrees";
    private static final String CAMERA_ORBIT_DEGREES_PROPERTY = "libfdx.test.cameraOrbitDegrees";
    private static final String CAPTURE_PROPERTY = "libfdx.test.capture";
    private static final String CAPTURE_EVERY_PROPERTY = "libfdx.test.captureEvery";

    private TestCameraControllers() {
    }

    static boolean autoOrbitEnabled() {
        String enabledValue = System.getProperty(CAMERA_ORBIT_PROPERTY);
        return enabledValue != null ? Boolean.parseBoolean(enabledValue) : multiCaptureRequested();
    }

    static float autoOrbitStartDegrees() {
        return floatProperty(CAMERA_ORBIT_START_PROPERTY, 0.0f);
    }

    static float autoOrbitDegrees() {
        return floatProperty(CAMERA_ORBIT_DEGREES_PROPERTY, 360.0f);
    }

    private static boolean multiCaptureRequested() {
        String capture = System.getProperty(CAPTURE_PROPERTY);
        return capture != null
                && capture.indexOf('%') >= 0
                && intProperty(CAPTURE_EVERY_PROPERTY, 0) > 0;
    }

    private static float floatProperty(String name, float defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return Float.parseFloat(value.trim());
    }

    private static int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }
}
