package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;

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
    private final ShaderReflection reflection;
    private final ShaderTranslatedInterface translatedInterface;
    private int pipelineReferences;
    private boolean disposed;

    GLShaderModuleHandle(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain, int program) {
        this(providerId, gl, resourceDomain, program, ShaderReflection.empty(), null);
    }

    GLShaderModuleHandle(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain, int program,
            ShaderReflection reflection) {
        this(providerId, gl, resourceDomain, program, reflection, null);
    }

    GLShaderModuleHandle(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain, int program,
            ShaderReflection reflection, ShaderTranslatedInterface translatedInterface) {
        this.providerId = providerId;
        this.gl = gl;
        this.resourceDomain = resourceDomain;
        this.program = program;
        this.reflection = reflection != null ? reflection : ShaderReflection.empty();
        this.translatedInterface = translatedInterface;
    }

    int program() {
        return program;
    }

    GLResourceDomain resourceDomain() {
        return resourceDomain;
    }

    ShaderTranslatedInterface translatedInterface() {
        return translatedInterface;
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

    @Override
    public ShaderReflection reflection() {
        return reflection;
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
