package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

public interface GraphicsContext extends ProviderHandle {
    GraphicsDevice device();

    TextureFormat surfaceFormat();

    GraphicsFrame currentFrame();

    void clear(float red, float green, float blue, float alpha);
}
