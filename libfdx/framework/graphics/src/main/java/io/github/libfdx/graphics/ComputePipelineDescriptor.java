package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderWorkgroupSizeKind;
import io.github.libfdx.core.FdxException;

/**
 * Describes a compute entry point and its reflected resource layout.
 */
public final class ComputePipelineDescriptor {
    private String label = "";
    private ShaderModule shaderModule;
    private String entryPoint = "computeMain";
    private ShaderReflection shaderReflection = ShaderReflection.empty();
    private boolean reflectionExplicit;
    private ShaderResourceLayout resourceLayout;

    public static ComputePipelineDescriptor shader(ShaderModule module) {
        return new ComputePipelineDescriptor().shaderModule(module);
    }

    public String label() {
        return label;
    }

    public ComputePipelineDescriptor label(String value) {
        label = value != null ? value : "";
        return this;
    }

    public ShaderModule shaderModule() {
        return shaderModule;
    }

    public ComputePipelineDescriptor shaderModule(ShaderModule value) {
        if (value == null) {
            throw new FdxException("Compute pipeline shader module cannot be null");
        }
        shaderModule = value;
        return this;
    }

    public String entryPoint() {
        return entryPoint;
    }

    public ComputePipelineDescriptor entryPoint(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new FdxException("Compute pipeline entry point cannot be empty");
        }
        entryPoint = value.trim();
        return this;
    }

    public ShaderReflection shaderReflection() {
        if (!reflectionExplicit && shaderModule != null) {
            return shaderModule.reflection();
        }
        return shaderReflection;
    }

    public ComputePipelineDescriptor shaderReflection(ShaderReflection value) {
        shaderReflection = value != null ? value : ShaderReflection.empty();
        reflectionExplicit = true;
        return this;
    }

    public ShaderResourceLayout resourceLayout() {
        if (resourceLayout != null) {
            return resourceLayout;
        }
        ShaderReflection reflection = shaderReflection();
        if (!reflection.complete()) {
            throw new FdxException("Compute pipeline requires complete shader reflection");
        }
        return ShaderResourceLayout.compute(reflection, entryPoint);
    }

    public ComputePipelineDescriptor resourceLayout(ShaderResourceLayout value) {
        if (value == null) {
            throw new FdxException("Compute pipeline resource layout cannot be null");
        }
        resourceLayout = value;
        return this;
    }

    public void validate(GraphicsCapabilities capabilities) {
        if (shaderModule == null) {
            throw new FdxException("Compute pipeline shader module cannot be null");
        }
        capabilities.require(GraphicsFeature.COMPUTE);
        ShaderReflection reflection = shaderReflection();
        if (!reflection.complete()) {
            throw new FdxException("Compute pipeline requires complete shader reflection");
        }
        ShaderEntryPoint entry = reflection.requireEntryPoint(
                ShaderStage.COMPUTE, entryPoint);
        validateWorkgroupSize(entry, capabilities.limits());
        resourceLayout().validate(capabilities);
    }

    private static void validateWorkgroupSize(ShaderEntryPoint entry,
            GraphicsLimits limits) {
        if (entry.workgroupSizeKind()
                != ShaderWorkgroupSizeKind.FIXED) {
            throw new FdxException(
                    "Compute pipeline requires a fixed reflected workgroup size");
        }
        int x = entry.workgroupX();
        int y = entry.workgroupY();
        int z = entry.workgroupZ();
        if (x > limits.maxComputeWorkgroupSizeX()
                || y > limits.maxComputeWorkgroupSizeY()
                || z > limits.maxComputeWorkgroupSizeZ()) {
            throw new FdxException(
                    "Compute workgroup dimensions exceed provider limits");
        }
        long invocations = (long) x * y * z;
        if (invocations > limits.maxComputeInvocationsPerWorkgroup()) {
            throw new FdxException(
                    "Compute workgroup invocation count exceeds the provider limit");
        }
    }
}
