package io.github.libfdx.graphics.shadergraph.cache;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentFormat;

import java.util.Locale;
import java.util.Objects;

/**
 * Complete identity of one reusable shader-graph compilation result.
 *
 * <p>Equality is intentionally exact. A change to any field is a cache miss,
 * including tool versions, target environment, profile/capabilities, options,
 * interface ABI, pass, variant, or entry points.</p>
 */
public final class ShaderGraphCacheKey
        implements Comparable<ShaderGraphCacheKey> {
    private final int documentFormatVersion;
    private final String semanticHash;
    private final String dependencyHash;
    private final String compilerId;
    private final String compilerVersion;
    private final String nodeLibraryVersion;
    private final String standardLibraryVersion;
    private final String profileId;
    private final String capabilitiesHash;
    private final String targetId;
    private final String artifactFormat;
    private final String consumerEnvironment;
    private final String verifierId;
    private final String verifierVersion;
    private final String optionsHash;
    private final String interfaceAbiVersion;
    private final String compilationUnit;
    private final String passId;
    private final String variantKey;
    private final String entryPointsHash;
    private final String structuralKey;
    private final String hash;

    private ShaderGraphCacheKey(Builder builder) {
        if (builder.documentFormatVersion <= 0) {
            throw new FdxException(
                    "Shader graph cache document version must be positive");
        }
        documentFormatVersion = builder.documentFormatVersion;
        semanticHash = hash(builder.semanticHash, "semantic");
        dependencyHash = hash(builder.dependencyHash, "dependency");
        compilerId = required(builder.compilerId, "compiler ID");
        compilerVersion = required(
                builder.compilerVersion, "compiler version");
        nodeLibraryVersion = required(
                builder.nodeLibraryVersion, "node-library version");
        standardLibraryVersion = required(
                builder.standardLibraryVersion,
                "standard-library version");
        profileId = required(builder.profileId, "profile");
        capabilitiesHash = hash(
                builder.capabilitiesHash, "capabilities");
        targetId = required(builder.targetId, "target");
        artifactFormat = required(
                builder.artifactFormat, "artifact format");
        consumerEnvironment = required(
                builder.consumerEnvironment, "consumer environment");
        verifierId = optional(builder.verifierId, "verifier ID");
        verifierVersion = optional(
                builder.verifierVersion, "verifier version");
        if (verifierId.isEmpty() != verifierVersion.isEmpty()) {
            throw new FdxException(
                    "Shader graph cache verifier ID and version "
                            + "must both be present or absent");
        }
        optionsHash = hash(builder.optionsHash, "options");
        interfaceAbiVersion = required(
                builder.interfaceAbiVersion, "interface ABI version");
        compilationUnit = required(
                builder.compilationUnit, "compilation unit");
        passId = optional(builder.passId, "pass ID");
        variantKey = optional(builder.variantKey, "variant key");
        entryPointsHash = hash(
                builder.entryPointsHash, "entry points");
        structuralKey = structuralKey();
        hash = PortableSha256.hashUtf8(structuralKey);
    }

    public static Builder builder(String semanticHash) {
        return new Builder(semanticHash);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public int documentFormatVersion() {
        return documentFormatVersion;
    }

    public String semanticHash() {
        return semanticHash;
    }

    public String dependencyHash() {
        return dependencyHash;
    }

    public String compilerId() {
        return compilerId;
    }

    public String compilerVersion() {
        return compilerVersion;
    }

    public String nodeLibraryVersion() {
        return nodeLibraryVersion;
    }

    public String standardLibraryVersion() {
        return standardLibraryVersion;
    }

    public String profileId() {
        return profileId;
    }

    public String capabilitiesHash() {
        return capabilitiesHash;
    }

    public String targetId() {
        return targetId;
    }

    public String artifactFormat() {
        return artifactFormat;
    }

    public String consumerEnvironment() {
        return consumerEnvironment;
    }

    public String verifierId() {
        return verifierId;
    }

    public String verifierVersion() {
        return verifierVersion;
    }

    public String optionsHash() {
        return optionsHash;
    }

    public String interfaceAbiVersion() {
        return interfaceAbiVersion;
    }

    public String compilationUnit() {
        return compilationUnit;
    }

    public String passId() {
        return passId;
    }

    public String variantKey() {
        return variantKey;
    }

    public String entryPointsHash() {
        return entryPointsHash;
    }

    /**
     * Returns the SHA-256 identity of the complete key.
     */
    public String hash() {
        return hash;
    }

    @Override
    public int compareTo(ShaderGraphCacheKey other) {
        return structuralKey.compareTo(other.structuralKey);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphCacheKey other
                && structuralKey.equals(other.structuralKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(structuralKey);
    }

    private String structuralKey() {
        return documentFormatVersion + "\n"
                + semanticHash + "\n"
                + dependencyHash + "\n"
                + compilerId + "\n"
                + compilerVersion + "\n"
                + nodeLibraryVersion + "\n"
                + standardLibraryVersion + "\n"
                + profileId + "\n"
                + capabilitiesHash + "\n"
                + targetId + "\n"
                + artifactFormat + "\n"
                + consumerEnvironment + "\n"
                + verifierId + "\n"
                + verifierVersion + "\n"
                + optionsHash + "\n"
                + interfaceAbiVersion + "\n"
                + compilationUnit + "\n"
                + passId + "\n"
                + variantKey + "\n"
                + entryPointsHash;
    }

    private static String hash(String value, String label) {
        String normalized = required(value, label + " hash")
                .toLowerCase(Locale.ROOT);
        if (normalized.length() != 64) {
            throw new FdxException("Shader graph cache " + label
                    + " hash must be SHA-256");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (character < '0' || character > '9'
                    && (character < 'a' || character > 'f')) {
                throw new FdxException("Shader graph cache " + label
                        + " hash must be SHA-256");
            }
        }
        return normalized;
    }

    private static String required(String value, String label) {
        String normalized = optional(value, label);
        if (normalized.isEmpty()) {
            throw new FdxException(
                    "Shader graph cache " + label + " cannot be empty");
        }
        return normalized;
    }

    private static String optional(String value, String label) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            throw new FdxException(
                    "Shader graph cache " + label
                            + " cannot contain line breaks");
        }
        return normalized;
    }

    /**
     * Mutable construction scope for a cache key.
     */
    public static final class Builder {
        private int documentFormatVersion =
                ShaderGraphDocumentFormat.CURRENT_VERSION;
        private String semanticHash;
        private String dependencyHash;
        private String compilerId;
        private String compilerVersion;
        private String nodeLibraryVersion;
        private String standardLibraryVersion;
        private String profileId;
        private String capabilitiesHash;
        private String targetId;
        private String artifactFormat;
        private String consumerEnvironment;
        private String verifierId;
        private String verifierVersion;
        private String optionsHash;
        private String interfaceAbiVersion;
        private String compilationUnit;
        private String passId;
        private String variantKey;
        private String entryPointsHash;

        private Builder(String semanticHash) {
            this.semanticHash = semanticHash;
        }

        private Builder(ShaderGraphCacheKey key) {
            documentFormatVersion = key.documentFormatVersion;
            semanticHash = key.semanticHash;
            dependencyHash = key.dependencyHash;
            compilerId = key.compilerId;
            compilerVersion = key.compilerVersion;
            nodeLibraryVersion = key.nodeLibraryVersion;
            standardLibraryVersion = key.standardLibraryVersion;
            profileId = key.profileId;
            capabilitiesHash = key.capabilitiesHash;
            targetId = key.targetId;
            artifactFormat = key.artifactFormat;
            consumerEnvironment = key.consumerEnvironment;
            verifierId = key.verifierId;
            verifierVersion = key.verifierVersion;
            optionsHash = key.optionsHash;
            interfaceAbiVersion = key.interfaceAbiVersion;
            compilationUnit = key.compilationUnit;
            passId = key.passId;
            variantKey = key.variantKey;
            entryPointsHash = key.entryPointsHash;
        }

        public Builder semanticHash(String value) {
            semanticHash = value;
            return this;
        }

        public Builder documentFormatVersion(int value) {
            documentFormatVersion = value;
            return this;
        }

        public Builder dependencyHash(String value) {
            dependencyHash = value;
            return this;
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

        public Builder profile(String id, String capabilityHash) {
            profileId = id;
            capabilitiesHash = capabilityHash;
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

        public Builder optionsHash(String value) {
            optionsHash = value;
            return this;
        }

        public Builder interfaceAbiVersion(String value) {
            interfaceAbiVersion = value;
            return this;
        }

        public Builder compilationUnit(String value) {
            compilationUnit = value;
            return this;
        }

        public Builder pass(String value) {
            passId = value;
            return this;
        }

        public Builder variant(String value) {
            variantKey = value;
            return this;
        }

        public Builder entryPointsHash(String value) {
            entryPointsHash = value;
            return this;
        }

        public ShaderGraphCacheKey build() {
            return new ShaderGraphCacheKey(this);
        }
    }
}
