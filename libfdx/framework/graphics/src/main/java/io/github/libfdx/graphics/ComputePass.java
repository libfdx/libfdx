package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.runtime.ShaderResourceSet;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderHandle;

/**
 * Borrowed frame-owned compute command scope.
 */
public interface ComputePass extends ProviderHandle {
    void setPipeline(ComputePipeline pipeline);

    void setResourceSet(ShaderResourceSet set);

    void dispatch(int workgroupCountX, int workgroupCountY, int workgroupCountZ);

    default void dispatch(int workgroupCountX) {
        dispatch(workgroupCountX, 1, 1);
    }

    default void validateDispatch(int x, int y, int z, GraphicsLimits limits) {
        if (x <= 0 || y <= 0 || z <= 0) {
            throw new FdxException("Compute workgroup counts must be positive");
        }
        int maximum = limits.maxComputeWorkgroupsPerDimension();
        if (x > maximum || y > maximum || z > maximum) {
            throw new FdxException("Compute workgroup count exceeds the provider limit");
        }
    }

    void end();
}
