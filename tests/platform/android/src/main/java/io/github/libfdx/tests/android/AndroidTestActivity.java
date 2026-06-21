package io.github.libfdx.tests.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.android.AndroidApplicationActivity;
import io.github.libfdx.backend.android.AndroidApplicationConfig;
import io.github.libfdx.backend.android.AndroidGraphicsFailureMode;
import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.backend.android.AndroidTextEditorStyle;
import io.github.libfdx.backend.android.AndroidVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUConfiguration;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.tests.AutoTestApplication;
import io.github.libfdx.tests.TestChooserApplication;
import io.github.libfdx.tests.TestSelector;
import java.io.File;

/**
 * Represents an android test activity.
 *
 * @author xpenatan
 */
public class AndroidTestActivity extends AndroidApplicationActivity {
    @Override
    protected AndroidApplicationConfig createApplicationConfig() {
        applyIntentTestProperties();
        configureAndroidCapturePath();
        String testName = selectedTestName();
        int width = intProperty("libfdx.test.width", defaultWidth(testName));
        int height = intProperty("libfdx.test.height", defaultHeight(testName));
        return new AndroidApplicationConfig()
                .title("libfdx Test: " + launchDisplayName(testName) + " - " + graphicsDisplayName())
                .size(width, height)
                .vSync(true)
                .foregroundFps(60)
                .nativeTextEditorStyle(nativeTextEditorStyle())
                .graphicsFailureMode(AndroidGraphicsFailureMode.THROW)
                .graphics(graphicsProvider());
    }

    @Override
    protected ApplicationListener createApplicationListener() {
        applyIntentTestProperties();
        configureAndroidCapturePath();
        configurePlatformTestProperties();
        String testName = selectedTestName();
        if (isSelector(testName)) {
            return new TestChooserApplication(new String[] { graphicsName() }, graphicsName(), null, true, true);
        }
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)) {
            return new AutoTestApplication();
        }
        return TestSelector.create(testName, longProperty("libfdx.test.frames", 0L));
    }

    private void applyIntentTestProperties() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        for (String key : extras.keySet()) {
            if (key != null && (key.startsWith("libfdx.test.") || key.startsWith("libfdx.validation."))) {
                Object value = extras.get(key);
                if (value != null) {
                    System.setProperty(key, String.valueOf(value));
                }
            }
        }
        Object test = extras.get("test");
        if (test != null && String.valueOf(test).trim().length() > 0) {
            System.setProperty("libfdx.test.name", String.valueOf(test).trim());
        }
    }

    private void configurePlatformTestProperties() {
        if (System.getProperty("libfdx.test.desktopImageCapture") == null) {
            System.setProperty("libfdx.test.desktopImageCapture", "false");
        }
        if (System.getProperty("libfdx.test.validate") == null) {
            System.setProperty("libfdx.test.validate", "false");
        }
    }

    private GraphicsAttachmentProvider graphicsProvider() {
        if ("gles".equalsIgnoreCase(graphicsName())) {
            return new AndroidGlesProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            return new AndroidVulkanProvider();
        }
        WGPUProvider provider = new WGPUProvider();
        if (captureRequested()) {
            provider.configuration(new WGPUConfiguration().offscreenReadback(true));
        }
        return provider;
    }

    private boolean captureRequested() {
        String capture = System.getProperty("libfdx.test.capture", "");
        return capture != null && capture.trim().length() > 0;
    }

    private void configureAndroidCapturePath() {
        String capture = System.getProperty("libfdx.test.capture", "");
        if (capture == null || capture.trim().length() == 0) {
            return;
        }
        File captureFile = new File(capture);
        if (!captureFile.isAbsolute()) {
            System.setProperty("libfdx.test.capture", new File(getFilesDir(), capture).getAbsolutePath());
        }
    }

    private AndroidTextEditorStyle nativeTextEditorStyle() {
        return new AndroidTextEditorStyle()
                .panelBackgroundColor(0xFFF8FAFC)
                .panelBorderColor(0xFF94A3B8)
                .editorBackgroundColor(0xFFFFFFFF)
                .editorBorderColor(0xFFCBD5E1)
                .editorTextColor(0xFF111827)
                .editorHintTextColor(0xFF64748B)
                .acceptButtonBackgroundColor(0xFF111827)
                .acceptButtonTextColor(0xFFFFFFFF)
                .cancelButtonBackgroundColor(0xFFE5E7EB)
                .cancelButtonBorderColor(0xFFCBD5E1)
                .cancelButtonTextColor(0xFF111827)
                .editorTextSizeSp(16.0f)
                .actionTextSizeSp(14.0f)
                .actionButtonWidthDp(48.0f)
                .actionButtonHeightDp(38.0f)
                .cancelText("X")
                .acceptText("OK");
    }

    private String selectedTestName() {
        String requested = requestedTestName();
        if (requested != null && requested.length() > 0) {
            if (isSelector(requested)) {
                return TestSelector.SELECTOR_NAME;
            }
            if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(requested)) {
                return TestSelector.AUTO_TEST_NAME;
            }
            return requested;
        }
        String mode = trim(System.getProperty("libfdx.test.mode"));
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(mode)) {
            return TestSelector.AUTO_TEST_NAME;
        }
        if (isSelector(mode) || shouldOpenSelector()) {
            return TestSelector.SELECTOR_NAME;
        }
        return TestSelector.DEFAULT_TEST_NAME;
    }

    private String requestedTestName() {
        String property = trim(System.getProperty("libfdx.test.name"));
        if (property != null) {
            return property;
        }
        Intent intent = getIntent();
        if (intent != null) {
            String extra = intent.getStringExtra("libfdx.test.name");
            if (extra != null && extra.trim().length() > 0) {
                return extra.trim();
            }
            String shortExtra = intent.getStringExtra("test");
            if (shortExtra != null && shortExtra.trim().length() > 0) {
                return shortExtra.trim();
            }
            Uri data = intent.getData();
            if (data != null) {
                String queryValue = data.getQueryParameter("test");
                if (queryValue != null && queryValue.trim().length() > 0) {
                    return queryValue.trim();
                }
            }
        }
        return null;
    }

    private boolean shouldOpenSelector() {
        if (longProperty("libfdx.test.frames", 0L) > 0L) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("libfdx.test.validate", "false"))) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("libfdx.test.driveInput", "false"))) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("libfdx.test.visualValidate", "false"))) {
            return false;
        }
        return trim(System.getProperty("libfdx.test.capture")) == null;
    }

    private static boolean isSelector(String value) {
        return TestSelector.SELECTOR_NAME.equalsIgnoreCase(value)
                || "menu".equalsIgnoreCase(value)
                || "chooser".equalsIgnoreCase(value);
    }

    private static int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private static long longProperty(String name, long defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        return Long.parseLong(value.trim());
    }

    private static int defaultWidth(String testName) {
        if (isSelector(testName)) {
            return 900;
        }
        return TestSelector.defaultWidth(testName);
    }

    private static int defaultHeight(String testName) {
        if (isSelector(testName)) {
            return 740;
        }
        return TestSelector.defaultHeight(testName);
    }

    protected String graphicsName() {
        return "wgpu";
    }

    protected String graphicsDisplayName() {
        if ("gles".equalsIgnoreCase(graphicsName())) {
            return "GLES";
        }
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            return "Vulkan JNI";
        }
        return "WGPU JNI";
    }

    private static String launchDisplayName(String testName) {
        if (isSelector(testName)) {
            return "selector";
        }
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)) {
            return "auto";
        }
        return testName;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 0 ? trimmed : null;
    }
}
