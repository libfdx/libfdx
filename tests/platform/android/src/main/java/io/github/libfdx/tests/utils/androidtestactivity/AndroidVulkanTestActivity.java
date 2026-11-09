package io.github.libfdx.tests.utils.androidtestactivity;

/**
 * Represents an android vulkan test activity.
 *
 * @author xpenatan
 */
public final class AndroidVulkanTestActivity extends AndroidTestActivity {
    @Override
    protected String graphicsName() {
        return "vulkan";
    }
}
