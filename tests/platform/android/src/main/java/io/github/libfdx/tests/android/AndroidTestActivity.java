package io.github.libfdx.tests.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.android.AndroidApplicationActivity;
import io.github.libfdx.backend.android.AndroidApplicationConfig;
import io.github.libfdx.backend.android.AndroidAssetExecutor;
import io.github.libfdx.tests.graphics.GltfLoadingTest;
import io.github.libfdx.tests.graphics.ConcurrentGltfLoadingTest;
import io.github.libfdx.testsupport.graphics.ConcurrentGltfObserver;
import io.github.libfdx.tests.graphics.ModelBatchTest;
import io.github.libfdx.testsupport.graphics.GltfLoadingObserver;
import io.github.libfdx.backend.android.AndroidGraphicsFailureMode;
import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.backend.android.AndroidTextEditorStyle;
import io.github.libfdx.backend.android.AndroidVulkanProvider;
import io.github.libfdx.backend.android.AndroidShaderCacheStore;
import io.github.libfdx.backend.android.AndroidVulkanCacheMergeTest;
import io.github.libfdx.backend.android.AndroidVulkanLifecycleTest;
import io.github.libfdx.backend.android.AndroidGlesProgramBinaryTest;
import io.github.libfdx.backend.android.AndroidGlesResetDetectionTest;
import io.github.libfdx.backend.android.AndroidShaderPreloadDestination;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import io.github.libfdx.graphics.wgpu.WGPUConfiguration;
import io.github.libfdx.graphics.wgpu.WGPUBackend;
import io.github.libfdx.graphics.wgpu.WGPULoaderBackend;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.graphics.wgpu.WGPUAndroidPreparationTest;
import io.github.libfdx.graphics.wgpu.WGPUAndroidLifecycleTest;
import io.github.libfdx.testsupport.AutoTestApplication;
import io.github.libfdx.testsupport.ManagedTestApplication;
import io.github.libfdx.testsupport.TestChooserApplication;
import io.github.libfdx.testsupport.TestSelector;
import io.github.libfdx.tests.graphics.ShaderPreloadingTest;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import io.github.libfdx.backend.android.AndroidApplicationBackend;
import io.github.libfdx.testsupport.android.WGPUStartupFaultProvider;
import io.github.libfdx.testsupport.android.WGPUStartupFaultListener;

/**
 * Represents an android test activity.
 *
 * @author xpenatan
 */
public class AndroidTestActivity extends AndroidApplicationActivity {
    private ManagedTestApplication managedTest;
    private AndroidShaderCacheStore shaderCacheStore;
    private ShaderArtifactCache shaderCache;
    @Override
    protected AndroidApplicationConfig createApplicationConfig() {
        applyIntentTestProperties();
        configureAndroidCapturePath();
        String testName = selectedTestName();
        int width = intProperty("libfdx.test.width", defaultWidth(testName));
        int height = intProperty("libfdx.test.height", defaultHeight(testName));
        AndroidApplicationConfig config = new AndroidApplicationConfig()
                .title("libfdx Test: " + launchDisplayName(testName) + " - " + graphicsDisplayName())
                .size(width, height)
                .vSync(true)
                .foregroundFps(60)
                .nativeTextEditorStyle(nativeTextEditorStyle())
                .graphicsFailureMode(AndroidGraphicsFailureMode.THROW)
                .graphics(graphicsProvider());
        if(Boolean.getBoolean("libfdx.test.wgpuStartupFallback")) {
            String key = System.getProperty("libfdx.test.wgpuRecoveryKey", "wgpu-validation");
            if(Boolean.getBoolean("libfdx.test.wgpuRecoveryReset")) {
                AndroidApplicationBackend.clearGraphicsStartupRecovery(this, key);
            }
            WGPUProvider primary = new WGPUProvider().configuration(new WGPUConfiguration()
                    .loaderBackend(wgpuLoader()).backend(WGPUBackend.VULKAN).offscreenReadback(captureRequested()));
            String fault = System.getProperty("libfdx.test.wgpuStartupFault", "");
            config.graphics(fault.isEmpty() ? primary : new WGPUStartupFaultProvider(primary, fault));
            config.fallbackGraphics(new WGPUProvider().configuration(new WGPUConfiguration()
                    .loaderBackend(wgpuLoader()).backend(WGPUBackend.OPENGL_ES).offscreenReadback(captureRequested())));
            config.graphicsStartupRecoveryKey(key);
        }
        return config;
    }

    @Override
    protected ApplicationListener createApplicationListener() {
        applyIntentTestProperties();
        configureAndroidCapturePath();
        configurePlatformTestProperties();
        String testName = selectedTestName();
        if (isSelector(testName)) {
            return new TestChooserApplication(new String[] { graphicsName() }, graphicsName(), null, true, true,
                    AndroidTestActivity::createSharedTest);
        }
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)) {
            return new AutoTestApplication(null, true, AndroidTestActivity::createSharedTest);
        }
        ApplicationListener test;
        if ("WGPUAndroidDeviceLossTest".equalsIgnoreCase(testName)) return new WGPUAndroidDeviceLossTest();
        if ("AndroidVulkanDeviceLossTest".equalsIgnoreCase(testName)) return new AndroidVulkanDeviceLossTest();
        if ("AndroidNativeCompilationStressTest".equalsIgnoreCase(testName)) return new AndroidNativeCompilationStressTest();
        if ("AndroidVulkanCacheMergeTest".equalsIgnoreCase(testName)) return new AndroidVulkanCacheMergeTest();
        if ("AndroidVulkanLifecycleTest".equalsIgnoreCase(testName)) return new AndroidVulkanLifecycleTest();
        if ("AndroidGlesProgramBinaryTest".equalsIgnoreCase(testName)) return new AndroidGlesProgramBinaryTest();
        if ("AndroidGlesResetDetectionTest".equalsIgnoreCase(testName)) return new AndroidGlesResetDetectionTest();
        if ("WGPUAndroidPreparationTest".equalsIgnoreCase(testName)) {
            return new WGPUAndroidPreparationTest();
        } else if ("WGPUReadbackTest".equalsIgnoreCase(testName)) {
            return new WGPUReadbackTest();
        } else if ("WGPUAndroidLifecycleTest".equalsIgnoreCase(testName)) {
            return new WGPUAndroidLifecycleTest();
        } else if ("ShaderPreloadingTest".equals(testName)) {
            String manifest = System.getProperty("libfdx.test.shaderManifest", "");
            String destination = System.getProperty("libfdx.test.shaderCaptureDir", "");
            try {
                String json = manifest.isEmpty() ? null : new String(Files.readAllBytes(shaderFile(manifest)), StandardCharsets.UTF_8);
                test = new ShaderPreloadingTest(longProperty("libfdx.test.frames", 0L),
                        destination.isEmpty() ? null : new AndroidShaderPreloadDestination(shaderFile(destination)), json);
            } catch (IOException failure) { throw new FdxException("Could not load the shader preload manifest", failure); }
        } else test = createSharedTest(testName, longProperty("libfdx.test.frames", 0L));
        String startupStage = System.getProperty("libfdx.test.wgpuStartupListenerFault", "");
        if (!startupStage.isEmpty()) test = new WGPUStartupFaultListener(test, startupStage);
        if (Boolean.getBoolean("libfdx.test.autoChild")) {
            managedTest = new ManagedTestApplication(test);
            return managedTest;
        }
        return test;
    }

    private static ApplicationListener createSharedTest(String name, long frames) {
        if ("ConcurrentGltfLoadingTest".equalsIgnoreCase(name))
            return new ConcurrentGltfLoadingTest(frames, ConcurrentGltfObserver.NONE, new AndroidAssetExecutor(2, 8));
        if ("GltfLoadingTest".equalsIgnoreCase(name))
            return new GltfLoadingTest(frames, GltfLoadingObserver.NONE, new AndroidAssetExecutor(2, 8));
        if ("ModelBatchTest".equalsIgnoreCase(name))
            return new ModelBatchTest(frames, System.getProperty("libfdx.test.modelAsset", ModelBatchTest.DEFAULT_GLTF_ASSET),
                    null, null, new AndroidAssetExecutor(2, 8));
        return TestSelector.create(name, frames);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (shaderCacheStore != null) {
            shaderCacheStore.flushAsync().onSuccess(ignored -> {
                for (ShaderCacheLayer layer : ShaderCacheLayer.values()) {
                    System.out.println("[info] SHADER_CACHE layer=" + layer + " " + shaderCache.metrics(layer));
                }
            }).onFailure(failure -> System.out.println("[info] SHADER_CACHE flush failed: " + failure));
            shaderCacheStore.dispose();
        }
        if (managedTest != null) {
            managedTest.verifyCompleted();
            System.out.println("[libfdx-auto] PASS " + System.getProperty("libfdx.test.autoToken", ""));
        }
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

    private Path shaderFile(String relativePath) {
        Path directory = getFilesDir().toPath().toAbsolutePath().normalize();
        Path result = directory.resolve(relativePath).normalize();
        if (!result.startsWith(directory)) throw new IllegalArgumentException("Shader capture/manifest paths must stay in app-private files");
        return result;
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
            AndroidGlesProvider provider = new AndroidGlesProvider();
            int workers = intProperty("libfdx.test.shaderWorkers", 0);
            if (workers != 0) provider.preparationWorkerLimit(workers);
            provider.shaderCache(createShaderCache());
            return provider;
        }
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            AndroidVulkanProvider provider = new AndroidVulkanProvider();
            int workers = intProperty("libfdx.test.shaderWorkers", 0);
            if (workers != 0) provider.configuration().preparationWorkerLimit(workers);
            provider.configuration().shaderCache(createShaderCache());
            return provider;
        }
        WGPUProvider provider = new WGPUProvider();
        // Explicit selection lets emulator diagnostics distinguish Vulkan from a GL fallback.
        String requestedBackend = System.getProperty("libfdx.test.wgpuBackend", "default");
        WGPUBackend backend = switch (requestedBackend) {
            case "default" -> WGPUBackend.DEFAULT;
            case "vulkan" -> WGPUBackend.VULKAN;
            case "gles" -> WGPUBackend.OPENGL_ES;
            default -> throw new IllegalArgumentException("Unknown Android WGPU backend: " + requestedBackend
                    + ". Expected default, vulkan or gles.");
        };
        System.out.println("[info] Android WGPU requested backend: " + requestedBackend);
        System.out.println("[info] Android WGPU requested implementation: " + wgpuLoader());
        provider.configuration(new WGPUConfiguration().loaderBackend(wgpuLoader()).backend(backend).offscreenReadback(captureRequested())
                .shaderCache(createShaderCache()));
        int workers = intProperty("libfdx.test.shaderWorkers", 0);
        if (workers != 0) provider.configuration().preparationWorkerLimit(workers);
        return provider;
    }

    private static WGPULoaderBackend wgpuLoader() {
        return WGPULoaderBackend.valueOf(System.getProperty("libfdx.test.wgpuLoader", "WGPU").toUpperCase(Locale.ROOT));
    }

    private ShaderArtifactCache createShaderCache() {
        String directory = System.getProperty("libfdx.test.shaderCacheDirectory", "");
        if (directory.isEmpty()) return null;
        if (!directory.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Android shaderCacheDirectory must be a name under the app's private cache directory");
        }
        shaderCacheStore = new AndroidShaderCacheStore(getCacheDir().toPath().resolve(directory), 128L * 1024 * 1024);
        shaderCache = new ShaderArtifactCache(shaderCacheStore);
        return shaderCache;
    }

    private boolean captureRequested() {
        String capture = System.getProperty("libfdx.test.capture", "");
        return capture != null && capture.trim().length() > 0;
    }

    private void configureAndroidCapturePath() {
        for (String property : new String[]{"libfdx.test.capture", "libfdx.test.shaderCapturePending", "libfdx.test.shaderPendingCapture"}) {
            String capture = System.getProperty(property, "");
            if (capture == null || capture.trim().length() == 0) continue;
            File captureFile = new File(capture);
            if (!captureFile.isAbsolute()) {
                System.setProperty(property, new File(getFilesDir(), capture).getAbsolutePath());
            }
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
            return TestSelector.normalize(requested);
        }
        String mode = trim(System.getProperty("libfdx.test.mode"));
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(mode)) {
            return TestSelector.AUTO_TEST_NAME;
        }
        if (isSelector(mode) || shouldOpenSelector()) {
            return TestSelector.SELECTOR_NAME;
        }
        return TestSelector.defaultTestName();
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
