package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;

/**
 * Borrowed immutable resolution of one graph compute pass/variant.
 */
public final class ShaderGraphResolvedComputePass {
    private final ShaderPassId passId;
    private final String variantKey;
    private final ComputePipeline pipeline;
    private final ShaderResourceLayout resourceLayout;
    private final int workgroupX;
    private final int workgroupY;
    private final int workgroupZ;
    private final long providerRevision;

    ShaderGraphResolvedComputePass(ShaderPassId passId, String variantKey,
            ComputePipeline pipeline, ShaderResourceLayout resourceLayout,
            int workgroupX, int workgroupY, int workgroupZ,
            long providerRevision) {
        if (passId == null || variantKey == null || pipeline == null
                || resourceLayout == null || workgroupX <= 0
                || workgroupY <= 0 || workgroupZ <= 0
                || providerRevision <= 0) {
            throw new FdxException(
                    "Resolved graph compute pass is incomplete");
        }
        this.passId = passId;
        this.variantKey = variantKey;
        this.pipeline = pipeline;
        this.resourceLayout = resourceLayout;
        this.workgroupX = workgroupX;
        this.workgroupY = workgroupY;
        this.workgroupZ = workgroupZ;
        this.providerRevision = providerRevision;
    }

    public ShaderPassId passId() {
        return passId;
    }

    public String variantKey() {
        return variantKey;
    }

    public ComputePipeline pipeline() {
        return pipeline;
    }

    public ShaderResourceLayout resourceLayout() {
        return resourceLayout;
    }

    public int workgroupX() {
        return workgroupX;
    }

    public int workgroupY() {
        return workgroupY;
    }

    public int workgroupZ() {
        return workgroupZ;
    }

    public long providerRevision() {
        return providerRevision;
    }

    public int workgroupCountX(int invocationCount) {
        return groups(invocationCount, workgroupX);
    }

    public int workgroupCountY(int invocationCount) {
        return groups(invocationCount, workgroupY);
    }

    public int workgroupCountZ(int invocationCount) {
        return groups(invocationCount, workgroupZ);
    }

    private static int groups(int invocations, int size) {
        if (invocations <= 0) {
            throw new FdxException(
                    "Compute invocation count must be positive");
        }
        return (invocations + size - 1) / size;
    }
}
