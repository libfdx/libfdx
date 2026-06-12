package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;

/**
 * Represents a GL shader module handle.
 *
 * @author xpenatan
 */
final class GLShaderModuleHandle implements ShaderModule {
    private final ProviderId providerId;
    private final GLApi gl;
    private final int program;
    private boolean disposed;

    GLShaderModuleHandle(ProviderId providerId, GLApi gl, int program) {
        this.providerId = providerId;
        this.gl = gl;
        this.program = program;
    }

    int program() {
        return program;
    }

    /**
     * Returns the language.
     *
     * @return the language
     */
    @Override
    public ShaderLanguage language() {
        return ShaderLanguage.GLSL;
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        gl.deleteProgram(program);
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
