package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;

import java.util.Arrays;
import java.util.Locale;

/**
 * Immutable target formats and exact environments accepted by a provider.
 *
 * @author xpenatan
 */
public final class ShaderTargetSupport {
    private static final ShaderTargetSupport NONE = new ShaderTargetSupport(
            new ShaderTargetEnvironment[0]);

    private final ShaderTargetEnvironment[] environments;

    private ShaderTargetSupport(ShaderTargetEnvironment[] environments) {
        this.environments = environments != null
                ? environments.clone() : new ShaderTargetEnvironment[0];
        for (ShaderTargetEnvironment environment : this.environments) {
            if (environment == null) {
                throw new FdxException("Shader provider target environment cannot be null");
            }
        }
        Arrays.sort(this.environments);
        for (int i = 0; i < this.environments.length; i++) {
            if (i > 0 && this.environments[i - 1].id().equals(this.environments[i].id())) {
                throw new FdxException("Duplicate shader provider target environment: "
                        + this.environments[i].id());
            }
        }
    }

    /**
     * Creates provider target support.
     *
     * @param environments the exact environments
     * @return the support data
     */
    public static ShaderTargetSupport of(ShaderTargetEnvironment... environments) {
        if (environments == null || environments.length == 0) {
            return NONE;
        }
        return new ShaderTargetSupport(environments);
    }

    /**
     * Returns built-in support for a provider ID.
     *
     * @param providerId the provider ID
     * @return the support data, empty for providers with no programmable target
     */
    public static ShaderTargetSupport forProvider(ProviderId providerId) {
        if (providerId == null) {
            return NONE;
        }
        String value = providerId.value().toLowerCase(Locale.ROOT);
        if ("webgpu".equals(value)) {
            return of(ShaderTargetEnvironments.WEBGPU_WGSL_1);
        }
        if ("wgpu".equals(value)) {
            return of(ShaderTargetEnvironments.WGPU_WGSL_1);
        }
        if ("webgl".equals(value)) {
            return of(ShaderTargetEnvironments.WEBGL2_GLSL_ES_300);
        }
        if ("gles".equals(value)) {
            return of(ShaderTargetEnvironments.GLES3_GLSL_ES_300);
        }
        if ("gl".equals(value) || "opengl".equals(value)) {
            return of(ShaderTargetEnvironments.OPENGL_33_GLSL_330);
        }
        if ("vulkan".equals(value)) {
            return of(ShaderTargetEnvironments.VULKAN_1_0_SPIRV_1_0);
        }
        if ("metal".equals(value)) {
            return of(ShaderTargetEnvironments.IOS_METAL_2_MSL_2);
        }
        if ("directx".equals(value) || "d3d".equals(value)
                || "d3d11".equals(value) || "d3d12".equals(value)) {
            return of(ShaderTargetEnvironments.D3D12_FXC_SM_5_1);
        }
        return NONE;
    }

    /**
     * Returns accepted environments in stable ID order.
     *
     * @return the environments
     */
    public ShaderTargetEnvironment[] environments() {
        return environments.clone();
    }

    /**
     * Returns whether the provider accepts a target/format pair.
     *
     * @param target the target
     * @param format the format
     * @return true when accepted
     */
    public boolean accepts(ShaderTargetId target, ShaderArtifactFormat format) {
        for (ShaderTargetEnvironment environment : environments) {
            if (environment.target().equals(target) && environment.format().equals(format)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the exact environment is accepted.
     *
     * @param environment the environment
     * @return true when accepted
     */
    public boolean accepts(ShaderTargetEnvironment environment) {
        if (environment == null) {
            return false;
        }
        for (ShaderTargetEnvironment supported : environments) {
            if (supported.equals(environment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requires an artifact accepted by this provider.
     *
     * @param artifact the artifact
     */
    public void require(ShaderTargetArtifact artifact) {
        if (artifact == null || !accepts(artifact.environment())) {
            throw new FdxException("Shader provider does not accept artifact environment "
                    + (artifact != null ? artifact.environment() : "null"));
        }
    }
}
