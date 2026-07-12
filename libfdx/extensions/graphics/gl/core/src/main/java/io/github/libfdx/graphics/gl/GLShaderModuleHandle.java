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
    private final GLResourceDomain resourceDomain;
    private final int program;
    private int pipelineReferences;
    private boolean disposed;

    GLShaderModuleHandle(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain, int program) {
        this.providerId = providerId;
        this.gl = gl;
        this.resourceDomain = resourceDomain;
        this.program = program;
    }

    int program() {
        return program;
    }

    GLResourceDomain resourceDomain() {
        return resourceDomain;
    }

    void retainForPipeline() {
        GLResources.requireUsable(this, resourceDomain, "Shader module");
        pipelineReferences++;
    }

    void releaseFromPipeline() {
        if (pipelineReferences > 0) {
            pipelineReferences--;
        }
        deleteProgramWhenUnused();
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
        deleteProgramWhenUnused();
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

    private void deleteProgramWhenUnused() {
        if (disposed && pipelineReferences == 0 && resourceDomain.makeAnyContextCurrent()) {
            gl.deleteProgram(program);
        }
    }
}
