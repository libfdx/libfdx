package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;

import java.util.Arrays;

/**
 * Complete compute artifact independent of graph lowering and cache origin.
 */
public final class ShaderGraphComputeRuntimeTechnique {
    private final String id;
    private final Pass[] passes;

    private ShaderGraphComputeRuntimeTechnique(String id, Pass[] passes) {
        if (id == null || id.trim().isEmpty()
                || passes == null || passes.length == 0) {
            throw new FdxException(
                    "Shader graph compute runtime technique is incomplete");
        }
        this.id = id.trim();
        this.passes = passes.clone();
        Arrays.sort(this.passes);
        for (int i = 0; i < this.passes.length; i++) {
            if (this.passes[i] == null || i > 0
                    && this.passes[i - 1].passId()
                            .equals(this.passes[i].passId())) {
                throw new FdxException(
                        "Compute runtime passes must be non-null and unique");
            }
        }
    }

    public static ShaderGraphComputeRuntimeTechnique of(
            String id, Pass... passes) {
        return new ShaderGraphComputeRuntimeTechnique(id, passes);
    }

    public String id() {
        return id;
    }

    public Pass[] passes() {
        return passes.clone();
    }

    public static final class Pass implements Comparable<Pass> {
        private final ShaderPassId passId;
        private final String defaultVariantKey;
        private final Variant[] variants;

        private Pass(ShaderPassId passId, String defaultVariantKey,
                Variant[] variants) {
            if (passId == null || variants == null
                    || variants.length == 0) {
                throw new FdxException(
                        "Compute runtime pass is incomplete");
            }
            this.passId = passId;
            this.defaultVariantKey =
                    normalize(defaultVariantKey);
            this.variants = variants.clone();
            Arrays.sort(this.variants);
            for (int i = 0; i < this.variants.length; i++) {
                if (this.variants[i] == null || i > 0
                        && this.variants[i - 1].key()
                                .equals(this.variants[i].key())) {
                    throw new FdxException(
                            "Compute runtime variants must be "
                                    + "non-null and unique");
                }
            }
            if (variant(this.defaultVariantKey) == null) {
                throw new FdxException(
                        "Compute runtime default variant is missing");
            }
        }

        public static Pass of(ShaderPassId passId,
                String defaultVariantKey, Variant... variants) {
            return new Pass(passId, defaultVariantKey, variants);
        }

        public ShaderPassId passId() {
            return passId;
        }

        public String defaultVariantKey() {
            return defaultVariantKey;
        }

        public Variant[] variants() {
            return variants.clone();
        }

        public Variant variant(String key) {
            String requested = normalize(key);
            for (Variant variant : variants) {
                if (variant.key().equals(requested)) {
                    return variant;
                }
            }
            return null;
        }

        @Override
        public int compareTo(Pass other) {
            return passId.compareTo(other.passId);
        }
    }

    public static final class Variant implements Comparable<Variant> {
        private final String key;
        private final ShaderModuleDescriptor shader;
        private final String entryPoint;
        private final ShaderProfile[] profiles;
        private final GraphicsFeature[] features;
        private final String fallbackKey;
        private final ShaderProfile compiledProfile;
        private final int workgroupX;
        private final int workgroupY;
        private final int workgroupZ;

        private Variant(String key, ShaderModuleDescriptor shader,
                String entryPoint, ShaderProfile[] profiles,
                GraphicsFeature[] features, String fallbackKey,
                ShaderProfile compiledProfile,
                int workgroupX, int workgroupY, int workgroupZ) {
            if (shader == null || entryPoint == null
                    || entryPoint.isBlank()
                    || workgroupX <= 0 || workgroupY <= 0
                    || workgroupZ <= 0) {
                throw new FdxException(
                        "Compute runtime variant is incomplete");
            }
            this.key = normalize(key);
            this.shader = shader;
            this.entryPoint = entryPoint;
            this.profiles = sorted(profiles, ShaderProfile[]::new);
            this.features = sorted(features, GraphicsFeature[]::new);
            this.fallbackKey = fallbackKey != null
                    ? normalize(fallbackKey) : null;
            this.compiledProfile = compiledProfile;
            if (this.key.equals(this.fallbackKey)) {
                throw new FdxException(
                        "Compute runtime variant cannot fall back to itself");
            }
            this.workgroupX = workgroupX;
            this.workgroupY = workgroupY;
            this.workgroupZ = workgroupZ;
        }

        public static Variant of(String key,
                ShaderModuleDescriptor shader, String entryPoint,
                ShaderProfile[] profiles, GraphicsFeature[] features,
                String fallbackKey, ShaderProfile compiledProfile,
                int workgroupX,
                int workgroupY, int workgroupZ) {
            return new Variant(key, shader, entryPoint,
                    profiles, features, fallbackKey, compiledProfile,
                    workgroupX, workgroupY, workgroupZ);
        }

        public String key() {
            return key;
        }

        public ShaderModuleDescriptor shader() {
            return shader;
        }

        public String entryPoint() {
            return entryPoint;
        }

        public ShaderProfile[] profiles() {
            return profiles.clone();
        }

        public GraphicsFeature[] features() {
            return features.clone();
        }

        public String fallbackKey() {
            return fallbackKey;
        }

        public ShaderProfile compiledProfile() {
            return compiledProfile;
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

        @Override
        public int compareTo(Variant other) {
            return key.compareTo(other.key);
        }
    }

    private static String normalize(String value) {
        return value != null ? value.trim() : "";
    }

    private static <T extends Comparable<? super T>> T[] sorted(
            T[] values, java.util.function.IntFunction<T[]> factory) {
        T[] result = values != null ? values.clone() : factory.apply(0);
        for (T value : result) {
            if (value == null) {
                throw new FdxException(
                        "Compute runtime capability cannot be null");
            }
        }
        Arrays.sort(result);
        for (int i = 1; i < result.length; i++) {
            if (result[i - 1].compareTo(result[i]) == 0) {
                throw new FdxException(
                        "Compute runtime capabilities must be unique");
            }
        }
        return result;
    }
}
