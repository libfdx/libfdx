package io.github.libfdx.tools.shader;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ShaderTarget;

/**
 * Describes a native Tint bridge request.
 *
 * @author xpenatan
 */
public final class FdxTintCompilerBridgeRequest {
    private final String source;
    private final ShaderTarget target;
    private final FdxTintShaderStage stage;
    private final String entryPoint;
    private final String glslProfile;
    private final String glslEsProfile;

    private FdxTintCompilerBridgeRequest(String source, ShaderTarget target, FdxTintShaderStage stage,
            String entryPoint, String glslProfile, String glslEsProfile) {
        if (source == null || source.length() == 0) {
            throw new FdxException("Tint bridge source cannot be empty");
        }
        this.source = source;
        this.target = target != null ? target : ShaderTarget.WEBGPU_WGSL;
        this.stage = stage != null ? stage : FdxTintShaderStage.MODULE;
        this.entryPoint = entryPoint != null ? entryPoint : "";
        this.glslProfile = glslProfile != null ? glslProfile : "330";
        this.glslEsProfile = glslEsProfile != null ? glslEsProfile : "300";
    }

    /**
     * Creates a bridge request.
     *
     * @param request the compiler request
     * @param stage the stage
     * @param entryPoint the entry point
     * @return a new request
     */
    public static FdxTintCompilerBridgeRequest of(FdxShaderCompilerRequest request, FdxTintShaderStage stage,
            String entryPoint) {
        return new FdxTintCompilerBridgeRequest(request.source(), request.target(), stage, entryPoint,
                request.glslProfile(), request.glslEsProfile());
    }

    /**
     * Creates a bridge request.
     *
     * @param source the source
     * @param target the target
     * @param stage the stage
     * @param entryPoint the entry point
     * @param glslProfile the GLSL profile
     * @param glslEsProfile the GLSL ES profile
     * @return a new request
     */
    public static FdxTintCompilerBridgeRequest of(String source, ShaderTarget target, FdxTintShaderStage stage,
            String entryPoint, String glslProfile, String glslEsProfile) {
        return new FdxTintCompilerBridgeRequest(source, target, stage, entryPoint, glslProfile, glslEsProfile);
    }

    public String source() {
        return source;
    }

    public ShaderTarget target() {
        return target;
    }

    public FdxTintShaderStage stage() {
        return stage;
    }

    public String entryPoint() {
        return entryPoint;
    }

    public String glslProfile() {
        return glslProfile;
    }

    public String glslEsProfile() {
        return glslEsProfile;
    }
}
