package io.github.libfdx.graphics.internal;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.reflection.ShaderBindingSemantic;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterDomain;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterSemantic;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.target.ShaderSemanticOverlay;
import io.github.libfdx.graphics.shader.reflection.ShaderUpdateFrequency;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;

import java.util.ArrayList;
import java.util.List;

/**
 * Checked-in reflection baseline for the framework-owned PBR renderer
 * template.
 *
 * <p>The physical interface is decoded exclusively through
 * {@link ShaderReflection#fromRuntime(RuntimeShaderReflection)}. Framework ownership and update
 * frequency are then applied as a semantic overlay, which cannot alter the Tint-proven physical
 * hash.</p>
 */
public final class BuiltInPbrShaderManifest {
    private static final ShaderReflection STATIC_REFLECTION =
            decode(GeneratedPbrShaderManifestData.staticFdxi(), false);
    private static final ShaderReflection SKINNED_REFLECTION =
            decode(GeneratedPbrShaderManifestData.skinnedFdxi(), true);

    public static ShaderReflection staticReflection() {
        return STATIC_REFLECTION;
    }

    public static ShaderReflection skinnedReflection() {
        return SKINNED_REFLECTION;
    }

    public static String staticSourceHash() {
        return GeneratedPbrShaderManifestData.STATIC_SOURCE_SHA256;
    }

    public static String skinnedSourceHash() {
        return GeneratedPbrShaderManifestData.SKINNED_SOURCE_SHA256;
    }

    /**
     * Returns the static manifest after proving that the supplied WGSL is the source used to
     * generate its checked-in FDXI payload.
     *
     * @param wgsl the canonical static WGSL
     * @return the static manifest
     */
    public static ShaderReflection requireStaticSource(String wgsl) {
        requireSource(wgsl, staticSourceHash(), "static");
        return STATIC_REFLECTION;
    }

    /**
     * Returns the skinned manifest after proving that the supplied WGSL is the source used to
     * generate its checked-in FDXI payload.
     *
     * @param wgsl the canonical skinned WGSL
     * @return the skinned manifest
     */
    public static ShaderReflection requireSkinnedSource(String wgsl) {
        requireSource(wgsl, skinnedSourceHash(), "skinned");
        return SKINNED_REFLECTION;
    }

    /**
     * Returns whether a complete manifest is physically identical to either built-in PBR
     * interface.
     *
     * @param reflection the candidate manifest
     * @return whether it is a built-in PBR interface
     */
    public static boolean matches(ShaderReflection reflection) {
        return reflection != null && reflection.complete()
                && (STATIC_REFLECTION.physicallyEquivalent(reflection)
                || SKINNED_REFLECTION.physicallyEquivalent(reflection));
    }

    private static ShaderReflection decode(byte[] fdxi, boolean skinned) {
        ShaderReflection physical = ShaderReflection.fromRuntime(RuntimeShaderReflection.fromBytes(fdxi));
        return physical.withSemanticOverlay(semanticOverlay(skinned));
    }

    private static ShaderSemanticOverlay semanticOverlay(boolean skinned) {
        List<ShaderBindingSemantic> bindings = new ArrayList<>(19);
        bindings.add(materialBinding(0, "baseColorTexture"));
        bindings.add(materialBinding(1, "baseColorSampler"));
        bindings.add(materialBinding(2, "metallicRoughnessTexture"));
        bindings.add(materialBinding(3, "metallicRoughnessSampler"));
        bindings.add(materialBinding(4, "normalTexture"));
        bindings.add(materialBinding(5, "normalSampler"));
        bindings.add(materialBinding(6, "occlusionTexture"));
        bindings.add(materialBinding(7, "occlusionSampler"));
        bindings.add(materialBinding(8, "emissiveTexture"));
        bindings.add(materialBinding(9, "emissiveSampler"));
        bindings.add(environmentBinding(10, "shadowTexture0"));
        bindings.add(environmentBinding(11, "shadowSampler0"));
        bindings.add(environmentBinding(12, "shadowTexture1"));
        bindings.add(environmentBinding(13, "shadowSampler1"));
        bindings.add(environmentBinding(14, "shadowTexture2"));
        bindings.add(environmentBinding(15, "shadowSampler2"));
        bindings.add(environmentBinding(16, "shadowTexture3"));
        bindings.add(environmentBinding(17, "shadowSampler3"));

        List<ShaderParameterSemantic> parameters = new ArrayList<>(42);
        add(parameters, ShaderParameterDomain.OBJECT_DRAW, ShaderUpdateFrequency.DRAW,
                "model");
        add(parameters, ShaderParameterDomain.FRAME_VIEW, ShaderUpdateFrequency.FRAME,
                "viewProjection", "cameraPosition", "cameraDirection");
        add(parameters, ShaderParameterDomain.ENVIRONMENT_PASS, ShaderUpdateFrequency.PASS,
                "ambientColor", "lightDirection", "lightColorIntensity", "fillLightDirection",
                "fillLightColorIntensity", "postProcessing", "fogColor", "fogParams",
                "skyZenithColor", "skyHorizonColor", "skyNadirColor", "skySunColor",
                "skySunDirection", "skyParams", "pointLightCount", "pointLightPositions",
                "pointLightColors", "spotLightCount", "spotLightPositions", "spotLightDirections",
                "spotLightColors", "spotLightCones", "shadowViewProjection0",
                "shadowViewProjection1", "shadowViewProjection2", "shadowViewProjection3",
                "shadowParams", "shadowCascadeSplits", "shadowBiases", "shadowCameraPosition",
                "shadowCameraDirection", "shadowCameraUp", "shadowCameraParams",
                "shadowFilterParams");
        add(parameters, ShaderParameterDomain.MATERIAL, ShaderUpdateFrequency.ON_CHANGE,
                "textureFlags", "emissiveFlags");
        if (skinned) {
            add(parameters, ShaderParameterDomain.OBJECT_DRAW, ShaderUpdateFrequency.DRAW,
                    "skinningParams", "boneMatrices");
        }
        bindings.add(ShaderBindingSemantic.builder(1, 0, "pbr.uniforms")
                .semantics(ShaderParameterDomain.MIXED, ShaderUpdateFrequency.MIXED)
                .parameters(parameters.toArray(ShaderParameterSemantic[]::new))
                .build());
        return ShaderSemanticOverlay.of(bindings.toArray(ShaderBindingSemantic[]::new));
    }

    private static ShaderBindingSemantic materialBinding(int binding, String name) {
        return ShaderBindingSemantic.builder(0, binding, "pbr.material." + name)
                .semantics(ShaderParameterDomain.MATERIAL, ShaderUpdateFrequency.ON_CHANGE)
                .build();
    }

    private static ShaderBindingSemantic environmentBinding(int binding, String name) {
        return ShaderBindingSemantic.builder(0, binding, "pbr.environment." + name)
                .semantics(ShaderParameterDomain.ENVIRONMENT_PASS, ShaderUpdateFrequency.PASS)
                .build();
    }

    private static void add(List<ShaderParameterSemantic> parameters, ShaderParameterDomain domain,
            ShaderUpdateFrequency frequency, String... paths) {
        for (String path : paths) {
            parameters.add(ShaderParameterSemantic.of(path, "pbr." + path, domain, frequency));
        }
    }

    private static void requireSource(String source, String expectedHash, String variant) {
        if (source == null || !expectedHash.equals(sha256(source))) {
            throw new FdxException("Built-in " + variant
                    + " PBR WGSL does not match its generated shader manifest. Run "
                    + ":libfdx:framework:g3d:generate_pbr_shader_manifest");
        }
    }

    private static String sha256(String source) {
        return PortableSha256.hashUtf8(source);
    }

    private BuiltInPbrShaderManifest() {
    }
}
