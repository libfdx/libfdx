package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCacheKey;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledInterface;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphDependency;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLibrary;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;

import java.util.Map;
import java.util.TreeMap;

/**
 * Exact compiler/target environment used to build and select cache keys.
 */
public final class ShaderGraphCacheContext {
    public static final String WGPU_TARGET = "wgpu-wgsl";
    public static final String WGSL_FORMAT = "wgsl";
    public static final String INTERFACE_ABI = "fdx-graph-interface-v1";

    private final ShaderGraphCompileOptions compileOptions;
    private final String compilerId;
    private final String compilerVersion;
    private final String nodeLibraryVersion;
    private final String standardLibraryVersion;
    private final String targetId;
    private final String artifactFormat;
    private final String consumerEnvironment;
    private final String verifierId;
    private final String verifierVersion;
    private final String interfaceAbiVersion;
    private final String capabilitiesHash;
    private final String optionsHash;

    private ShaderGraphCacheContext(Builder builder) {
        compileOptions = builder.compileOptions != null
                ? builder.compileOptions
                : ShaderGraphCompileOptions.builder().build();
        compilerId = required(builder.compilerId, "compiler ID");
        compilerVersion = required(
                builder.compilerVersion, "compiler version");
        nodeLibraryVersion = required(
                builder.nodeLibraryVersion, "node-library version");
        standardLibraryVersion = required(
                builder.standardLibraryVersion,
                "standard-library version");
        targetId = required(builder.targetId, "target ID");
        artifactFormat = required(
                builder.artifactFormat, "artifact format");
        consumerEnvironment = required(
                builder.consumerEnvironment, "consumer environment");
        verifierId = optional(builder.verifierId);
        verifierVersion = optional(builder.verifierVersion);
        if (verifierId.isEmpty() != verifierVersion.isEmpty()) {
            throw new FdxException(
                    "Shader graph verifier ID and version "
                            + "must both be present or absent");
        }
        interfaceAbiVersion = required(
                builder.interfaceAbiVersion, "interface ABI");
        capabilitiesHash = capabilitiesHash(
                compileOptions.capabilities());
        optionsHash = optionsHash(compileOptions);
    }

    /**
     * Creates the current WGPU/WGSL cache context.
     */
    public static ShaderGraphCacheContext wgpu(
            ShaderGraphCompileOptions options) {
        return builder(options)
                .compiler("libfdx-shader-graph-wgsl", "1")
                .libraries("standard-nodes-v1", "standard-wgsl-v1")
                .target(WGPU_TARGET, WGSL_FORMAT,
                        "libfdx-wgsl-runtime-v1")
                .interfaceAbiVersion(INTERFACE_ABI)
                .build();
    }

    public static Builder builder(ShaderGraphCompileOptions options) {
        return new Builder(options);
    }

    public ShaderGraphCompileOptions compileOptions() {
        return compileOptions;
    }

    public String targetId() {
        return targetId;
    }

    public String artifactFormat() {
        return artifactFormat;
    }

    public String interfaceAbiVersion() {
        return interfaceAbiVersion;
    }

    ShaderGraphCacheKey key(ShaderGraphDocument document,
            ShaderGraphCompiledInterface shaderInterface,
            String compilationUnit, String passId,
            String variantKey) {
        return key(document, shaderInterface.entryPointsHash(),
                compilationUnit, passId, variantKey);
    }

    ShaderGraphCacheKey key(ShaderGraphDocument document,
            String entryPointsHash, String compilationUnit,
            String passId, String variantKey) {
        return ShaderGraphCacheKey.builder(document.semanticHash())
                .dependencyHash(dependencyHash(document))
                .compiler(compilerId, compilerVersion)
                .libraries(nodeLibraryVersion,
                        standardLibraryVersion)
                .profile(compileOptions.profile().id(),
                        capabilitiesHash)
                .target(targetId, artifactFormat,
                        consumerEnvironment)
                .verifier(verifierId, verifierVersion)
                .optionsHash(optionsHash)
                .interfaceAbiVersion(interfaceAbiVersion)
                .compilationUnit(compilationUnit)
                .pass(passId)
                .variant(variantKey)
                .entryPointsHash(entryPointsHash)
                .build();
    }

    private String dependencyHash(ShaderGraphDocument document) {
        TreeMap<String, ShaderGraph> roots =
                new TreeMap<String, ShaderGraph>();
        collectGraphs(document, roots);
        TreeMap<String, String> visited =
                new TreeMap<String, String>();
        StringBuilder key = new StringBuilder();
        for (ShaderGraph root : roots.values()) {
            dependencies(root, compileOptions.library(),
                    visited, key);
        }
        return PortableSha256.hashUtf8(key.toString());
    }

    private static void dependencies(ShaderGraph graph,
            ShaderGraphLibrary library, Map<String, String> visited,
            StringBuilder key) {
        String previous = visited.putIfAbsent(
                graph.id().value(), graph.semanticHash());
        if (previous != null) {
            return;
        }
        key.append(graph.id().value()).append('=')
                .append(graph.semanticHash()).append('\n');
        for (ShaderGraphDependency dependency : graph.dependencies()) {
            key.append("requires:")
                    .append(dependency.graphId().value())
                    .append('=')
                    .append(dependency.semanticHash()).append('\n');
            ShaderGraph resolved =
                    library.resolve(dependency.graphId());
            if (resolved == null) {
                key.append("missing\n");
            } else {
                dependencies(resolved, library, visited, key);
            }
        }
    }

    private static void collectGraphs(ShaderGraphDocument document,
            Map<String, ShaderGraph> graphs) {
        switch (document.kind()) {
            case GRAPH -> add(graphs, document.graph());
            case PROGRAM -> {
                add(graphs, document.program().vertex());
                add(graphs, document.program().fragment());
            }
            case COMPUTE_PROGRAM ->
                    add(graphs, document.computeProgram().graph());
            case TECHNIQUE -> {
                for (ShaderGraphTechniquePass pass
                        : document.technique().passes()) {
                    for (ShaderGraphVariant variant
                            : pass.variants()) {
                        add(graphs,
                                variant.sourceProgram().vertex());
                        add(graphs,
                                variant.sourceProgram().fragment());
                    }
                }
            }
            case COMPUTE_TECHNIQUE -> {
                for (ShaderGraphComputeTechniquePass pass
                        : document.computeTechnique().passes()) {
                    for (ShaderGraphComputeVariant variant
                            : pass.variants()) {
                        add(graphs,
                                variant.sourceProgram().graph());
                    }
                }
            }
        }
    }

    private static void add(Map<String, ShaderGraph> graphs,
            ShaderGraph graph) {
        ShaderGraph previous =
                graphs.putIfAbsent(graph.id().value(), graph);
        if (previous != null && !previous.semanticHash().equals(
                graph.semanticHash())) {
            throw new FdxException(
                    "Shader graph document contains conflicting graph ID "
                            + graph.id());
        }
    }

    private static String capabilitiesHash(
            GraphicsCapabilities capabilities) {
        if (capabilities == null) {
            return PortableSha256.hashUtf8("unspecified");
        }
        StringBuilder key = new StringBuilder();
        for (ShaderProfile profile : ShaderProfile.values()) {
            key.append("profile:").append(profile.id()).append('=')
                    .append(capabilities.supports(profile)).append('\n');
        }
        for (GraphicsFeature feature : GraphicsFeature.values()) {
            key.append("feature:").append(feature.name()).append('=')
                    .append(capabilities.supports(feature)).append('\n');
        }
        for (TextureFormat format : capabilities.colorFormats()) {
            key.append("color:").append(format.name()).append('\n');
        }
        for (TextureFormat format
                : capabilities.depthStencilFormats()) {
            key.append("depth:").append(format.name()).append('\n');
        }
        for (TextureFormat format : capabilities.resolveFormats()) {
            key.append("resolve:").append(format.name()).append('\n');
        }
        for (int count : capabilities.sampleCounts()) {
            key.append("samples:").append(count).append('\n');
            for (TextureFormat format : TextureFormat.values()) {
                if (capabilities.supportsSampleCount(format, count)) {
                    key.append("format-samples:")
                            .append(format.name()).append('=')
                            .append(count).append('\n');
                }
            }
        }
        GraphicsLimits limits = capabilities.limits();
        key.append(limits.maxBindGroups()).append(':')
                .append(limits.maxBindingsPerGroup()).append(':')
                .append(limits.maxUniformBuffersPerStage()).append(':')
                .append(limits.maxStorageBuffersPerStage()).append(':')
                .append(limits.maxSampledTexturesPerStage()).append(':')
                .append(limits.maxSamplersPerStage()).append(':')
                .append(limits.maxStorageTexturesPerStage()).append(':')
                .append(limits.maxColorAttachments()).append(':')
                .append(limits.maxVertexBuffers()).append(':')
                .append(limits.maxVertexAttributes()).append(':')
                .append(limits.maxComputeWorkgroupsPerDimension())
                .append(':')
                .append(limits.maxComputeWorkgroupSizeX()).append(':')
                .append(limits.maxComputeWorkgroupSizeY()).append(':')
                .append(limits.maxComputeWorkgroupSizeZ()).append(':')
                .append(limits.maxComputeInvocationsPerWorkgroup())
                .append(':')
                .append(limits.maxComputeWorkgroupStorageSize())
                .append(':')
                .append(limits.maxUniformBufferBindingSize())
                .append(':')
                .append(limits.maxStorageBufferBindingSize());
        return PortableSha256.hashUtf8(key.toString());
    }

    private static String optionsHash(
            ShaderGraphCompileOptions options) {
        return PortableSha256.hashUtf8(
                "profile=" + options.profile().id()
                        + "\nstage="
                        + (options.stage() != null
                                ? options.stage().name() : "auto"));
    }

    private static String required(String value, String label) {
        String normalized = optional(value);
        if (normalized.isEmpty()) {
            throw new FdxException(
                    "Shader graph cache " + label + " cannot be empty");
        }
        return normalized;
    }

    private static String optional(String value) {
        return value != null ? value.trim() : "";
    }

    public static final class Builder {
        private final ShaderGraphCompileOptions compileOptions;
        private String compilerId;
        private String compilerVersion;
        private String nodeLibraryVersion;
        private String standardLibraryVersion;
        private String targetId;
        private String artifactFormat;
        private String consumerEnvironment;
        private String verifierId = "";
        private String verifierVersion = "";
        private String interfaceAbiVersion;

        private Builder(ShaderGraphCompileOptions compileOptions) {
            this.compileOptions = compileOptions;
        }

        public Builder compiler(String id, String version) {
            compilerId = id;
            compilerVersion = version;
            return this;
        }

        public Builder libraries(String nodeVersion,
                String standardVersion) {
            nodeLibraryVersion = nodeVersion;
            standardLibraryVersion = standardVersion;
            return this;
        }

        public Builder target(String id, String format,
                String environment) {
            targetId = id;
            artifactFormat = format;
            consumerEnvironment = environment;
            return this;
        }

        public Builder verifier(String id, String version) {
            verifierId = id;
            verifierVersion = version;
            return this;
        }

        public Builder interfaceAbiVersion(String value) {
            interfaceAbiVersion = value;
            return this;
        }

        public ShaderGraphCacheContext build() {
            return new ShaderGraphCacheContext(this);
        }
    }
}
