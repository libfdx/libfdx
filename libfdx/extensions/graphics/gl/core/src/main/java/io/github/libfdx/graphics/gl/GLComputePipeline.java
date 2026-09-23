package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.ComputePipelineDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;

final class GLComputePipeline implements ComputePipeline {
    final GLResourceDomain domain;
    final GLShaderModuleHandle module;
    final ShaderResourceLayout layout;
    final int[] slots;
    private boolean disposed;

    GLComputePipeline(GLResourceDomain domain, GLComputeModule shader, ComputePipelineDescriptor descriptor) {
        this.domain = domain;
        module = shader.program(descriptor.entryPoint());
        layout = descriptor.resourceLayout();
        slots = new int[layout.bindingCount()];
        for (int i = 0; i < slots.length; i++) {
            var binding = layout.binding(i);
            switch (binding.resourceKind()) {
                case STORAGE_BUFFER, UNIFORM_BUFFER, STORAGE_TEXTURE -> { }
                default -> throw new FdxException("GL compute binding is not implemented: " + binding.resourceKind());
            }
            var remap = module.translatedInterface().findBinding(ShaderArtifactStage.COMPUTE,
                    descriptor.entryPoint(), binding.group(), binding.binding());
            if (remap == null || remap.targetCount() != 1) throw new FdxException("Invalid GL compute binding remap");
            slots[i] = remap.target(0).binding();
        }
        module.retainForPipeline();
    }

    void requireLive() {
        domain.requireUsable();
        if (disposed) throw new FdxException("GL compute pipeline is disposed");
    }
    @Override
    public ProviderId providerId() { return module.providerId(); }
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() { return (T) this; }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if (!disposed) { disposed = true; module.releaseFromPipeline(); }
    }
}
