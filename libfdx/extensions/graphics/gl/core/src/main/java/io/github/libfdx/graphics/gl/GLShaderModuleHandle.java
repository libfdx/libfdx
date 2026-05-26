package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;

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

    @Override
    public ShaderLanguage language() {
        return ShaderLanguage.GLSL;
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        gl.deleteProgram(program);
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
