package io.github.libfdx.display;

import io.github.libfdx.core.ProviderHandle;

public interface Display extends ProviderHandle {
    String title();

    void title(String title);

    int width();

    int height();

    int framebufferWidth();

    int framebufferHeight();

    default float contentScaleX() {
        return inferredContentScale(framebufferWidth(), width());
    }

    default float contentScaleY() {
        return inferredContentScale(framebufferHeight(), height());
    }

    default float contentScale() {
        return Math.max(1.0f, (contentScaleX() + contentScaleY()) * 0.5f);
    }

    private static float inferredContentScale(int framebufferSize, int logicalSize) {
        if (framebufferSize <= 0 || logicalSize <= 0) {
            return 1.0f;
        }
        float scale = framebufferSize / (float) logicalSize;
        return scale > 0.0f && Float.isFinite(scale) ? scale : 1.0f;
    }

    boolean closeRequested();

    void requestClose();
}
