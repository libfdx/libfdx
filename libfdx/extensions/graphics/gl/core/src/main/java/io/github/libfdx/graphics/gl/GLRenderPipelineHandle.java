package io.github.libfdx.graphics.gl;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.target.ShaderBindingRemap;
import io.github.libfdx.graphics.shader.target.ShaderTargetBinding;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;

/**
 * Represents a GL render pipeline handle.
 *
 * @author xpenatan
 */
final class GLRenderPipelineHandle implements RenderPipeline {
    private final ProviderId providerId;
    private final GLApi gl;
    private final GLResourceDomain resourceDomain;
    private final GLShaderModuleHandle shaderModule;
    private final PrimitiveTopology primitiveTopology;
    private final VertexLayout[] vertexLayouts;
    private final int sampledTextureCount;
    private final boolean depthTestEnabled;
    private final boolean depthWriteEnabled;
    private final boolean alphaBlendEnabled;
    private final int uniformBuffer;
    private final ShaderRenderBindings resourceBindings;
    private final RenderTargetLayout targetLayout;
    private final String[][] textureUniformNames;
    private boolean disposed;

    GLRenderPipelineHandle(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain,
            GLShaderModuleHandle shaderModule, PrimitiveTopology primitiveTopology,
            VertexLayout[] vertexLayouts, int sampledTextureCount, boolean depthTestEnabled,
            boolean depthWriteEnabled, int uniformBuffer, ShaderRenderBindings resourceBindings,
            RenderTargetLayout targetLayout) {
        this(providerId, gl, resourceDomain, shaderModule, primitiveTopology,
                vertexLayouts, sampledTextureCount, depthTestEnabled,
                depthWriteEnabled, true, uniformBuffer, resourceBindings,
                targetLayout);
    }

    GLRenderPipelineHandle(ProviderId providerId, GLApi gl,
            GLResourceDomain resourceDomain,
            GLShaderModuleHandle shaderModule,
            PrimitiveTopology primitiveTopology,
            VertexLayout[] vertexLayouts, int sampledTextureCount,
            boolean depthTestEnabled, boolean depthWriteEnabled,
            boolean alphaBlendEnabled, int uniformBuffer,
            ShaderRenderBindings resourceBindings,
            RenderTargetLayout targetLayout) {
        this.providerId = providerId;
        this.gl = gl;
        this.resourceDomain = resourceDomain;
        this.shaderModule = shaderModule;
        this.primitiveTopology = primitiveTopology != null ? primitiveTopology : PrimitiveTopology.TRIANGLE_LIST;
        this.vertexLayouts = vertexLayouts != null ? vertexLayouts.clone() : new VertexLayout[0];
        this.sampledTextureCount = sampledTextureCount;
        this.depthTestEnabled = depthTestEnabled;
        this.depthWriteEnabled = depthWriteEnabled;
        this.alphaBlendEnabled = alphaBlendEnabled;
        this.uniformBuffer = uniformBuffer;
        this.resourceBindings = resourceBindings;
        this.targetLayout = targetLayout;
        textureUniformNames = textureUniformNames(shaderModule, resourceBindings);
        shaderModule.retainForPipeline();
    }

    int program() {
        return shaderModule.program();
    }

    GLResourceDomain resourceDomain() {
        return resourceDomain;
    }

    PrimitiveTopology primitiveTopology() {
        return primitiveTopology;
    }

    VertexLayout vertexLayout() {
        return vertexLayouts.length > 0 ? vertexLayouts[0] : null;
    }

    VertexLayout[] vertexLayouts() {
        return vertexLayouts.clone();
    }

    int vertexLayoutCount() {
        return vertexLayouts.length;
    }

    VertexLayout vertexLayout(int index) {
        return vertexLayouts[index];
    }

    int sampledTextureCount() {
        return sampledTextureCount;
    }

    boolean depthTestEnabled() {
        return depthTestEnabled;
    }

    boolean depthWriteEnabled() {
        return depthWriteEnabled;
    }

    boolean alphaBlendEnabled() {
        return alphaBlendEnabled;
    }

    int uniformBuffer() {
        return uniformBuffer;
    }

    boolean uniformBufferEnabled() {
        return uniformBuffer != 0;
    }

    ShaderRenderBindings resourceBindings() {
        return resourceBindings;
    }

    String[] textureUniformNames(int slot) {
        return textureUniformNames[slot];
    }

    @Override
    public RenderTargetLayout targetLayout() {
        return targetLayout;
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
        if (uniformBuffer != 0) {
            if (resourceDomain.makeAnyContextCurrent()) {
                gl.deleteBuffer(uniformBuffer);
            }
        }
        disposed = true;
        shaderModule.releaseFromPipeline();
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

    private static String[][] textureUniformNames(GLShaderModuleHandle module,
            ShaderRenderBindings bindings) {
        String[][] result = new String[bindings.sampledTextureCount()][];
        for (int slot = 0; slot < result.length; slot++) {
            Array<String> names = new Array<String>();
            ShaderBinding texture = bindings.texture(slot);
            ShaderBinding sampler = bindings.sampler(slot);
            addTargetNames(module, texture, names);
            addTargetNames(module, sampler, names);
            addCombinedSamplerNames(texture, sampler, names);
            result[slot] = names.toArray(new String[0]);
        }
        return result;
    }

    private static void addCombinedSamplerNames(ShaderBinding texture,
            ShaderBinding sampler, Array<String> names) {
        if (texture == null || sampler == null) {
            return;
        }
        String combined = texture.name() + '_' + sampler.name();
        addName(names, combined);
        addName(names, "v_" + combined);
        addName(names, "f_" + combined);
    }

    private static void addTargetNames(GLShaderModuleHandle module, ShaderBinding binding,
            Array<String> names) {
        if (binding == null || module.translatedInterface() == null) {
            return;
        }
        for (ShaderBindingRemap remap : module.translatedInterface().bindings()) {
            if (remap.sourceGroup() != binding.group()
                    || remap.sourceBinding() != binding.binding()) {
                continue;
            }
            for (ShaderTargetBinding target : remap.targets()) {
                addName(names, target.name());
            }
        }
    }

    private static void addName(Array<String> names, String name) {
        if (name != null && !name.isEmpty() && !names.contains(name)) {
            names.add(name);
        }
    }
}
