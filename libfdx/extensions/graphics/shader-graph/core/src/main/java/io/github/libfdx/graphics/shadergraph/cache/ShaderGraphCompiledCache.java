package io.github.libfdx.graphics.shadergraph.cache;

import io.github.libfdx.core.FdxException;

import java.util.Arrays;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable bounded collection of embedded compilation results.
 */
public final class ShaderGraphCompiledCache {
    public static final int MAX_ENTRIES = 1024;

    private final ShaderGraphCompiledCacheEntry[] entries;

    private ShaderGraphCompiledCache(
            ShaderGraphCompiledCacheEntry[] entries) {
        if (entries == null || entries.length > MAX_ENTRIES) {
            throw new FdxException(
                    "Shader graph compiled cache entry count is invalid");
        }
        this.entries = entries.clone();
        Arrays.sort(this.entries);
        for (int i = 0; i < this.entries.length; i++) {
            if (this.entries[i] == null) {
                throw new FdxException(
                        "Shader graph compiled cache entry cannot be null");
            }
            if (i > 0 && this.entries[i - 1].key()
                    .equals(this.entries[i].key())) {
                throw new FdxException(
                        "Duplicate shader graph compiled cache key "
                                + this.entries[i].key().hash());
            }
        }
    }

    public static ShaderGraphCompiledCache empty() {
        return new ShaderGraphCompiledCache(
                new ShaderGraphCompiledCacheEntry[0]);
    }

    public static ShaderGraphCompiledCache of(
            ShaderGraphCompiledCacheEntry... entries) {
        return new ShaderGraphCompiledCache(entries);
    }

    public ShaderGraphCompiledCacheEntry[] entries() {
        return entries.clone();
    }

    public int size() {
        return entries.length;
    }

    /**
     * Returns a cache containing the current entries plus the supplied
     * entries. An exact key replaces its previous value; entries for other
     * targets, profiles, and environments remain intact.
     */
    public ShaderGraphCompiledCache replacing(
            ShaderGraphCompiledCacheEntry... replacements) {
        if (replacements == null) {
            throw new FdxException(
                    "Shader graph cache replacements cannot be null");
        }
        TreeMap<ShaderGraphCacheKey, ShaderGraphCompiledCacheEntry>
                merged = new TreeMap<>();
        for (ShaderGraphCompiledCacheEntry entry : entries) {
            merged.put(entry.key(), entry);
        }
        for (ShaderGraphCompiledCacheEntry entry : replacements) {
            if (entry == null) {
                throw new FdxException(
                        "Shader graph cache replacement cannot be null");
            }
            merged.put(entry.key(), entry);
        }
        if (merged.size() > MAX_ENTRIES) {
            throw new FdxException(
                    "Shader graph compiled cache exceeds "
                            + MAX_ENTRIES + " entries");
        }
        return new ShaderGraphCompiledCache(
                merged.values().toArray(
                        ShaderGraphCompiledCacheEntry[]::new));
    }

    public Lookup lookup(ShaderGraphCacheKey key) {
        if (key == null) {
            throw new FdxException(
                    "Shader graph cache lookup key cannot be null");
        }
        for (ShaderGraphCompiledCacheEntry entry : entries) {
            if (entry.key().equals(key)) {
                return Lookup.hit(entry);
            }
        }
        return Lookup.miss(entries.length == 0
                ? MissReason.EMPTY : MissReason.NO_EXACT_MATCH);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphCompiledCache other
                && Arrays.equals(entries, other.entries);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(entries);
    }

    public enum MissReason {
        EMPTY,
        NO_EXACT_MATCH
    }

    /**
     * Result of exact cache selection.
     */
    public static final class Lookup {
        private final ShaderGraphCompiledCacheEntry entry;
        private final MissReason missReason;

        private Lookup(ShaderGraphCompiledCacheEntry entry,
                MissReason missReason) {
            this.entry = entry;
            this.missReason = missReason;
        }

        static Lookup hit(ShaderGraphCompiledCacheEntry entry) {
            return new Lookup(Objects.requireNonNull(entry), null);
        }

        static Lookup miss(MissReason reason) {
            return new Lookup(null, Objects.requireNonNull(reason));
        }

        public boolean hit() {
            return entry != null;
        }

        public ShaderGraphCompiledCacheEntry entry() {
            return entry;
        }

        public MissReason missReason() {
            return missReason;
        }
    }
}
