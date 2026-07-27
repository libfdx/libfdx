package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.internal.ShaderStableId;

import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable deterministic target option set.
 *
 * @author xpenatan
 */
public final class ShaderTargetOptions {
    private static final ShaderTargetOptions EMPTY = new ShaderTargetOptions(new String[0], new String[0]);

    private final String[] keys;
    private final String[] values;
    private final String hash;

    private ShaderTargetOptions(String[] keys, String[] values) {
        this.keys = keys;
        this.values = values;
        PortableSha256 digest = new PortableSha256().updateSizedUtf8("fdx-shader-target-options-v1")
                .updateInt(keys.length);
        for (int i = 0; i < keys.length; i++) {
            digest.updateSizedUtf8(keys[i]).updateSizedUtf8(values[i]);
        }
        hash = digest.digestHex();
    }

    /**
     * Returns an empty option set.
     *
     * @return the option set
     */
    public static ShaderTargetOptions empty() {
        return EMPTY;
    }

    /**
     * Creates a builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the number of options.
     *
     * @return the count
     */
    public int size() {
        return keys.length;
    }

    /**
     * Returns an option or the supplied fallback.
     *
     * @param key the option key
     * @param fallback the fallback
     * @return the value
     */
    public String value(String key, String fallback) {
        if (key == null) {
            return fallback;
        }
        String normalized = ShaderStableId.normalize(key, "Shader target option");
        int low = 0;
        int high = keys.length - 1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            int comparison = keys[middle].compareTo(normalized);
            if (comparison == 0) {
                return values[middle];
            }
            if (comparison < 0) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return fallback;
    }

    /**
     * Returns one option key.
     *
     * @param index the index
     * @return the key
     */
    public String key(int index) {
        return keys[index];
    }

    /**
     * Returns one option value.
     *
     * @param index the index
     * @return the value
     */
    public String value(int index) {
        return values[index];
    }

    /**
     * Returns the deterministic option hash.
     *
     * @return the hash
     */
    public String hash() {
        return hash;
    }

    /**
     * Builds immutable target options.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final TreeMap<String, String> options = new TreeMap<>();

        private Builder() {
        }

        /**
         * Adds an option.
         *
         * @param key the stable key
         * @param value the value
         * @return this builder
         */
        public Builder option(String key, String value) {
            String normalized = ShaderStableId.normalize(key, "Shader target option");
            String actual = ShaderStableId.requireValue(value, "Shader target option value");
            if (options.put(normalized, actual) != null) {
                throw new FdxException("Duplicate shader target option: " + normalized);
            }
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public ShaderTargetOptions build() {
            if (options.isEmpty()) {
                return EMPTY;
            }
            String[] keys = new String[options.size()];
            String[] values = new String[options.size()];
            int index = 0;
            for (Map.Entry<String, String> entry : options.entrySet()) {
                keys[index] = entry.getKey();
                values[index] = entry.getValue();
                index++;
            }
            return new ShaderTargetOptions(keys, values);
        }
    }
}
