package io.github.libfdx.graphics.shadergraph.cache;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * One immutable key, artifact, and interface tuple.
 */
public final class ShaderGraphCompiledCacheEntry
        implements Comparable<ShaderGraphCompiledCacheEntry> {
    private final ShaderGraphCacheKey key;
    private final ShaderGraphCompiledArtifact artifact;
    private final ShaderGraphCompiledInterface shaderInterface;

    private ShaderGraphCompiledCacheEntry(ShaderGraphCacheKey key,
            ShaderGraphCompiledArtifact artifact,
            ShaderGraphCompiledInterface shaderInterface) {
        if (key == null || artifact == null || shaderInterface == null) {
            throw new FdxException(
                    "Shader graph compiled cache entry is incomplete");
        }
        if (!key.artifactFormat().equals(artifact.format())) {
            throw new FdxException(
                    "Shader graph cache artifact format does not match key");
        }
        if (!key.interfaceAbiVersion().equals(
                shaderInterface.abiVersion())) {
            throw new FdxException(
                    "Shader graph cache interface ABI does not match key");
        }
        if (!key.entryPointsHash().equals(
                shaderInterface.entryPointsHash())) {
            throw new FdxException(
                    "Shader graph cache entry points do not match key");
        }
        this.key = key;
        this.artifact = artifact;
        this.shaderInterface = shaderInterface;
    }

    public static ShaderGraphCompiledCacheEntry of(
            ShaderGraphCacheKey key,
            ShaderGraphCompiledArtifact artifact,
            ShaderGraphCompiledInterface shaderInterface) {
        return new ShaderGraphCompiledCacheEntry(
                key, artifact, shaderInterface);
    }

    public ShaderGraphCacheKey key() {
        return key;
    }

    public ShaderGraphCompiledArtifact artifact() {
        return artifact;
    }

    public ShaderGraphCompiledInterface shaderInterface() {
        return shaderInterface;
    }

    @Override
    public int compareTo(ShaderGraphCompiledCacheEntry other) {
        return key.compareTo(other.key);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphCompiledCacheEntry other
                && key.equals(other.key)
                && artifact.equals(other.artifact)
                && shaderInterface.equals(other.shaderInterface);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, artifact, shaderInterface);
    }
}
