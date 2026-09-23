package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;

/** Internal platform service. Only an audited binding supplies an implementation.
 * The provider owns this service; applications use GraphicsDevice and ShaderPreparation. */
public abstract class WGPUPreparation implements AutoCloseable {
    boolean supports(WGPUConfiguration configuration) { return true; }
    abstract void initialize(WGPUContext context, int workers);
    abstract ShaderPreparationCapabilities capabilities();
    abstract ShaderPreparationOperation submit(ShaderPipelineRequest request);
    @Override
    public abstract void close();
}
