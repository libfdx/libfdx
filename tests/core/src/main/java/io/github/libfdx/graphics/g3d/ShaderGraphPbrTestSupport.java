package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;

/**
 * Creates the public standard graph PBR provider used by cross-provider
 * acceptance scenarios.
 */
public final class ShaderGraphPbrTestSupport {
    private ShaderGraphPbrTestSupport() {
    }

    /**
     * Creates a borrowed graph-backed PBR provider for a test batch.
     *
     * @param graphics the graphics context
     * @return the graph-backed provider
     */
    public static ShaderProvider provider(GraphicsContext graphics) {
        StandardPbrTechnique standard =
                StandardPbrTechnique.create(graphics);
        return new ShaderGraphProvider(graphics,
                standard.technique());
    }

    /**
     * Disposes a provider created by {@link #provider(GraphicsContext)}.
     *
     * @param provider the provider
     */
    public static void dispose(ShaderProvider provider) {
        if (provider instanceof Disposable disposable
                && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}
