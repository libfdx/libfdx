package io.github.libfdx.ecs.system;

import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.camera.Camera;

/** Participates in a world's UI rendering phase. */
public interface UiRenderSystem extends System {
    /**
     * Records UI rendering for one host-selected target.
     *
     * <p>The frame and target views are borrowed for this call. Implementations
     * must not retain or dispose them. The camera is nullable and remains owned
     * by its project or host.</p>
     *
     * @param frame active borrowed graphics frame
     * @param colorTarget borrowed color target
     * @param depthTarget optional borrowed depth target
     * @param width target width in pixels
     * @param height target height in pixels
     * @param camera resolved UI camera, or {@code null}
     */
    void renderUi(
            GraphicsFrame frame,
            TextureView colorTarget,
            TextureView depthTarget,
            int width,
            int height,
            Camera camera);
}
