package io.github.libfdx.graphics.shader;

import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.target.ShaderArtifactEncoding;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.target.ShaderEntryPointSelection;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetId;
import io.github.libfdx.graphics.shader.target.ShaderTargetVerification;
import io.github.libfdx.graphics.shader.target.ShaderTargets;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents canonical WGSL plus immutable ID-keyed target artifacts.
 *
 * @author xpenatan
 */
public final class ShaderBundle {
    private static final ShaderCompilerId PRECOMPILED = ShaderCompilerId.of("libfdx.precompiled");
    private static final String PRECOMPILED_VERSION = "1";

    private final String label;
    private final ShaderProfile profile;
    private final String wgslSource;
    private final ShaderReflection reflection;
    private final ShaderTargetArtifact[] artifacts;

    private ShaderBundle(Builder builder) {
        label = builder.label;
        profile = builder.profile;
        wgslSource = builder.wgslSource;
        reflection = builder.reflection != null ? builder.reflection : ShaderReflection.empty();

        TreeMap<ShaderTargetId, ShaderTargetArtifact> values = new TreeMap<>();
        for (ShaderTargetArtifact artifact : builder.artifacts.values()) {
            validateArtifactInterface(artifact, reflection);
            values.put(artifact.target(), artifact);
        }
        for (Map.Entry<ShaderTargetId, GeneratedTarget> entry : builder.generatedTargets.entrySet()) {
            if (values.containsKey(entry.getKey())) {
                throw new FdxException("Duplicate shader bundle target artifact: " + entry.getKey());
            }
            values.put(entry.getKey(), entry.getValue().artifact(reflection));
        }
        artifacts = values.values().toArray(new ShaderTargetArtifact[0]);
    }

    /**
     * Creates a builder.
     *
     * @param label the debug label
     * @return the builder
     */
    public static Builder builder(String label) {
        return new Builder(label);
    }

    public String label() {
        return label;
    }

    public ShaderProfile profile() {
        return profile;
    }

    public String wgslSource() {
        return wgslSource;
    }

    public ShaderReflection reflection() {
        return reflection;
    }

    /**
     * Returns all packaged target artifacts in stable target-ID order.
     *
     * @return the artifacts
     */
    public ShaderTargetArtifact[] artifacts() {
        return artifacts.clone();
    }

    public ShaderModuleDescriptor descriptorForProvider(ProviderId providerId) {
        return descriptorForTarget(ShaderTarget.forProvider(providerId));
    }

    public ShaderModuleDescriptor descriptorForProvider(String providerId) {
        return descriptorForTarget(ShaderTarget.forProvider(providerId));
    }

    public ShaderModuleDescriptor descriptorForTarget(ShaderTarget target) {
        if (target == null) {
            throw new FdxException("Shader target cannot be null");
        }
        return descriptorForTarget(target.id());
    }

    /**
     * Resolves a descriptor for a stable built-in or custom target ID.
     *
     * @param target the target ID
     * @return the descriptor
     */
    public ShaderModuleDescriptor descriptorForTarget(ShaderTargetId target) {
        if (target == null) {
            throw new FdxException("Shader target cannot be null");
        }
        ShaderTargetArtifact artifact = artifact(target);
        if (artifact != null) {
            return ShaderModuleDescriptors.descriptor(artifact, label, wgslSource)
                    .reflection(reflection);
        }
        return ShaderModuleDescriptor.wgsl(label, wgslSource).reflection(reflection);
    }

    /**
     * Returns a packaged target artifact by stable ID.
     *
     * @param target the target
     * @return the artifact, or null
     */
    public ShaderTargetArtifact artifact(ShaderTargetId target) {
        if (target == null) {
            return null;
        }
        int low = 0;
        int high = artifacts.length - 1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            int comparison = artifacts[middle].target().compareTo(target);
            if (comparison == 0) {
                return artifacts[middle];
            }
            if (comparison < 0) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return null;
    }

    public ShaderValidationResult validateProfile() {
        if (wgslSource == null || wgslSource.length() == 0) {
            return ShaderValidationResult.of(new ShaderValidationDiagnostic[] {
                    ShaderValidationDiagnostic.error("shader.bundle.wgsl-missing",
                            "Shader bundle " + label + " does not contain its WGSL source of truth")
            });
        }
        return ShaderProfileValidator.validateWgsl(profile, wgslSource);
    }

    /**
     * Returns whether canonical WGSL can be translated or an artifact is packaged.
     *
     * @param target the target
     * @return true when target resolution is possible
     */
    public boolean hasTarget(ShaderTarget target) {
        return target != null && wgslSource != null && wgslSource.length() > 0;
    }

    /**
     * Returns whether a derived artifact is packaged for a target.
     *
     * @param target the target
     * @return true when packaged
     */
    public boolean hasArtifact(ShaderTargetId target) {
        return artifact(target) != null;
    }

    private static void validateArtifactInterface(ShaderTargetArtifact artifact, ShaderReflection reflection) {
        if (artifact == null) {
            throw new FdxException("Shader bundle target artifact cannot be null");
        }
        ShaderReflection canonical = artifact.translatedInterface().canonical();
        if (reflection.complete() && canonical.complete() && !reflection.physicallyEquivalent(canonical)) {
            throw new FdxException("Shader bundle artifact interface does not match canonical reflection for "
                    + artifact.target());
        }
    }

    /**
     * Builds shader bundles.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final String label;
        private ShaderProfile profile = ShaderProfile.PORTABLE_WEBGPU;
        private String wgslSource;
        private ShaderReflection reflection = ShaderReflection.empty();
        private final TreeMap<ShaderTargetId, ShaderTargetArtifact> artifacts = new TreeMap<>();
        private final TreeMap<ShaderTargetId, GeneratedTarget> generatedTargets = new TreeMap<>();

        private Builder(String label) {
            if (label == null || label.trim().length() == 0) {
                throw new FdxException("Shader bundle label cannot be empty");
            }
            this.label = label;
        }

        public Builder profile(ShaderProfile profile) {
            this.profile = profile != null ? profile : ShaderProfile.PORTABLE_WEBGPU;
            return this;
        }

        public Builder wgsl(String source) {
            wgslSource = requireSource(source, "WGSL shader source");
            return this;
        }

        public Builder reflection(ShaderReflection reflection) {
            this.reflection = reflection != null ? reflection : ShaderReflection.empty();
            return this;
        }

        /**
         * Adds an immutable built-in or custom target artifact.
         *
         * @param artifact the artifact
         * @return this builder
         */
        public Builder artifact(ShaderTargetArtifact artifact) {
            if (artifact == null) {
                throw new FdxException("Shader bundle target artifact cannot be null");
            }
            if (artifacts.put(artifact.target(), artifact) != null
                    || generatedTargets.containsKey(artifact.target())) {
                throw new FdxException("Duplicate shader bundle target artifact: " + artifact.target());
            }
            return this;
        }

        public Builder generatedGlsl(ShaderTarget target, String vertexSource, String fragmentSource) {
            if (target != ShaderTarget.OPENGL_GLSL
                    && target != ShaderTarget.GLES_GLSL_ES
                    && target != ShaderTarget.WEBGL_GLSL_ES) {
                throw new FdxException("Generated GLSL requires an OpenGL, GLES, or WebGL shader target");
            }
            putGenerated(target.id(), GeneratedTarget.textPair(target,
                    requireSource(vertexSource, "GLSL vertex shader source"),
                    requireSource(fragmentSource, "GLSL fragment shader source")));
            return this;
        }

        public Builder generatedSpirv(int[] vertexWords, int[] fragmentWords) {
            putGenerated(ShaderTargets.VULKAN_SPIRV, GeneratedTarget.binaryPair(ShaderTarget.VULKAN_SPIRV,
                    wordsToBytes(requireWords(vertexWords, "SPIR-V vertex shader words")),
                    wordsToBytes(requireWords(fragmentWords, "SPIR-V fragment shader words"))));
            return this;
        }

        public Builder generatedMsl(String source) {
            putGenerated(ShaderTargets.METAL_MSL, GeneratedTarget.module(ShaderTarget.METAL_MSL,
                    requireSource(source, "MSL shader source")));
            return this;
        }

        /**
         * Adds generated HLSL output.
         *
         * @param vertexSource the vertex source
         * @param fragmentSource the fragment source
         * @return this builder
         */
        public Builder generatedHlsl(String vertexSource, String fragmentSource) {
            putGenerated(ShaderTargets.DIRECTX_HLSL, GeneratedTarget.textPair(ShaderTarget.DIRECTX_HLSL,
                    requireSource(vertexSource, "HLSL vertex shader source"),
                    requireSource(fragmentSource, "HLSL fragment shader source")));
            return this;
        }

        public ShaderBundle build() {
            ShaderBundle bundle = new ShaderBundle(this);
            bundle.validateProfile().throwIfFailed(label);
            return bundle;
        }

        private void putGenerated(ShaderTargetId target, GeneratedTarget generated) {
            if (generatedTargets.put(target, generated) != null || artifacts.containsKey(target)) {
                throw new FdxException("Duplicate shader bundle target artifact: " + target);
            }
        }

        private static String requireSource(String source, String name) {
            if (source == null || source.length() == 0) {
                throw new FdxException(name + " cannot be empty");
            }
            return source;
        }

        private static int[] requireWords(int[] words, String name) {
            if (words == null || words.length == 0) {
                throw new FdxException(name + " cannot be empty");
            }
            return words.clone();
        }
    }

    private static final class GeneratedTarget {
        private final ShaderTarget target;
        private final String vertex;
        private final String fragment;
        private final byte[] vertexBinary;
        private final byte[] fragmentBinary;
        private final String module;

        private GeneratedTarget(ShaderTarget target, String vertex, String fragment,
                byte[] vertexBinary, byte[] fragmentBinary, String module) {
            this.target = target;
            this.vertex = vertex;
            this.fragment = fragment;
            this.vertexBinary = vertexBinary;
            this.fragmentBinary = fragmentBinary;
            this.module = module;
        }

        private static GeneratedTarget textPair(ShaderTarget target, String vertex, String fragment) {
            return new GeneratedTarget(target, vertex, fragment, null, null, null);
        }

        private static GeneratedTarget binaryPair(ShaderTarget target, byte[] vertex, byte[] fragment) {
            return new GeneratedTarget(target, null, null, vertex, fragment, null);
        }

        private static GeneratedTarget module(ShaderTarget target, String module) {
            return new GeneratedTarget(target, null, null, null, null, module);
        }

        private ShaderTargetArtifact artifact(ShaderReflection reflection) {
            ShaderEntryPointSelection[] selections = {
                    ShaderEntryPointSelection.of(ShaderArtifactStage.VERTEX,
                            ShaderModuleDescriptor.DEFAULT_VERTEX_ENTRY_POINT),
                    ShaderEntryPointSelection.of(ShaderArtifactStage.FRAGMENT,
                            ShaderModuleDescriptor.DEFAULT_FRAGMENT_ENTRY_POINT)
            };
            ShaderStageArtifact[] stages;
            if (module != null) {
                stages = new ShaderStageArtifact[] {
                        ShaderStageArtifact.text(ShaderArtifactStage.MODULE, "", target.format(), module)
                };
            } else if (target.format().encoding() == ShaderArtifactEncoding.BINARY) {
                stages = new ShaderStageArtifact[] {
                        ShaderStageArtifact.binary(ShaderArtifactStage.VERTEX,
                                ShaderModuleDescriptor.DEFAULT_VERTEX_ENTRY_POINT, target.format(), vertexBinary),
                        ShaderStageArtifact.binary(ShaderArtifactStage.FRAGMENT,
                                ShaderModuleDescriptor.DEFAULT_FRAGMENT_ENTRY_POINT, target.format(), fragmentBinary)
                };
            } else {
                stages = new ShaderStageArtifact[] {
                        ShaderStageArtifact.text(ShaderArtifactStage.VERTEX,
                                ShaderModuleDescriptor.DEFAULT_VERTEX_ENTRY_POINT, target.format(), vertex),
                        ShaderStageArtifact.text(ShaderArtifactStage.FRAGMENT,
                                ShaderModuleDescriptor.DEFAULT_FRAGMENT_ENTRY_POINT, target.format(), fragment)
                };
            }
            ShaderTranslatedInterface translated = ShaderTranslatedInterface.identity(reflection, selections);
            ShaderTargetArtifact compiled = ShaderTargetArtifact.compiled(target.id(), target.format(),
                    target.environment(), stages, translated, PRECOMPILED, PRECOMPILED_VERSION, null);
            return compiled.withVerification(ShaderTargetVerification.providerPipeline(
                    target.environment(), translated.entryPoints(), compiled.compileCacheKey()));
        }
    }

    private static byte[] wordsToBytes(int[] words) {
        ByteBuffer buffer = ByteBuffer.allocate(words.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int word : words) {
            buffer.putInt(word);
        }
        return buffer.array();
    }
}
