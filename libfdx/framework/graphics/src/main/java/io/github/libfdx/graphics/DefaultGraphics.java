package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.ClipDepthRange;

/**
 * Provides the default implementation of a graphics.
 *
 * @author xpenatan
 */
public final class DefaultGraphics implements Graphics {
    private final GraphicsContext main;
    private boolean clipRangePublished;

    /**
     * Creates a default graphics.
     *
     * <p>The context is borrowed. An asynchronous attachment may still be
     * initializing; its device is only queried after it reports readiness.
     * Backends must access {@link #main()} once ready, before creating listeners,
     * to publish the device's camera depth convention.</p>
     *
     * @param main the main context
     */
    public DefaultGraphics(GraphicsContext main) {
        if (main == null) {
            throw new FdxException("Main graphics context cannot be null");
        }
        this.main = main;
        publishClipRangeWhenReady();
    }

    /**
     * Runs the launcher entry point.
     *
     * @return the main
     */
    @Override
    public GraphicsContext main() {
        publishClipRangeWhenReady();
        return main;
    }

    private void publishClipRangeWhenReady() {
        if (clipRangePublished || main instanceof GraphicsAttachmentReadiness readiness && !readiness.isReady()) return;
        // WebGPU devices become available asynchronously. Never query their
        // capabilities while the attachment is still waiting for an adapter.
        ClipDepthRange.setDefault(ClipDepthRange.resolveFor(main.device().capabilities().clipDepthRange()));
        clipRangePublished = true;
    }

    /**
     * Returns the supports multiple.
     *
     * @return true if supports multiple succeeds or is active; false otherwise
     */
    @Override
    public boolean supportsMultiple() {
        return false;
    }

    /**
     * Creates a value.
     *
     * @param config the configuration
     * @return the created value
     */
    @Override
    public GraphicsAttachment create(GraphicsConfig config) {
        throw new FdxException("This backend does not support creating additional graphics contexts");
    }
}
