package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;

/**
 * Construction settings for {@link SpriteBatch}.
 *
 * <p>An application-supplied shader provider is borrowed. Disposing the batch
 * never disposes it.</p>
 */
public final class SpriteBatchConfig {
    public static final int DEFAULT_MAX_SPRITES = 1024;

    private int initialMaxSprites = DEFAULT_MAX_SPRITES;
    private ShaderProvider shaderProvider;

    /**
     * Sets initial reusable CPU and GPU batch capacity.
     *
     * @param value positive sprite count
     * @return this configuration
     */
    public SpriteBatchConfig initialMaxSprites(int value) {
        if (value <= 0) {
            throw new FdxException(
                    "SpriteBatch initial sprite count must be greater than zero");
        }
        initialMaxSprites = value;
        return this;
    }

    /**
     * Sets a borrowed common shader provider.
     *
     * @param value provider, or {@code null} for built-in shaders
     * @return this configuration
     */
    public SpriteBatchConfig shaderProvider(ShaderProvider value) {
        shaderProvider = value;
        return this;
    }

    /**
     * Returns initial sprite capacity.
     *
     * @return capacity
     */
    public int initialMaxSprites() {
        return initialMaxSprites;
    }

    /**
     * Returns the borrowed common shader provider.
     *
     * @return provider, or {@code null}
     */
    public ShaderProvider shaderProvider() {
        return shaderProvider;
    }
}
