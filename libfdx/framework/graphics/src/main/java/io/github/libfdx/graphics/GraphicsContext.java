package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for graphics context implementations.
 *
 * @author xpenatan
 */
public interface GraphicsContext extends ProviderHandle {
    /**
     * Returns the device.
     *
     * @return the device
     */
    GraphicsDevice device();

    /**
     * Returns the surface format.
     *
     * @return the surface format
     */
    TextureFormat surfaceFormat();

    /**
     * Returns the current frame.
     *
     * @return the current frame
     */
    GraphicsFrame currentFrame();

    /**
     * Returns diagnostics for the most recently submitted graphics frame.
     *
     * @return backend frame metrics, or an unavailable metrics object
     */
    default GraphicsFrameMetrics frameMetrics() {
        return GraphicsFrameMetrics.UNAVAILABLE;
    }

    /**
     * Runs the clear step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    void clear(float red, float green, float blue, float alpha);
}
