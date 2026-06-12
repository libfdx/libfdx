package io.github.libfdx.backend.web;

import org.teavm.model.MethodReference;
import org.teavm.platform.metadata.MetadataGenerator;
import org.teavm.platform.metadata.MetadataGeneratorContext;
import org.teavm.platform.metadata.Resource;
import org.teavm.platform.metadata.builders.ObjectResourceBuilder;
import org.teavm.platform.metadata.builders.ResourceArrayBuilder;
import org.teavm.platform.metadata.builders.ResourceBuilder;

import java.util.Properties;

/**
 * Represents a web asset metadata generator.
 *
 * @author xpenatan
 */
public final class WebAssetMetadataGenerator implements MetadataGenerator {
    private static final String COUNT_PROPERTY = "libfdx.web.assets.count";
    private static final String ASSET_PROPERTY_PREFIX = "libfdx.web.assets.";

    /**
     * Runs the generate metadata step.
     *
     * @param context the context
     * @param method the method
     * @return the generate metadata
     */
    @Override
    public ResourceBuilder generateMetadata(MetadataGeneratorContext context, MethodReference method) {
        Properties properties = context.getProperties();
        int count = parseCount(properties.getProperty(COUNT_PROPERTY));
        ResourceArrayBuilder<WebAssetBuilder> result = new ResourceArrayBuilder<>();
        for (int index = 0; index < count; index++) {
            String path = properties.getProperty(ASSET_PROPERTY_PREFIX + index + ".path", "");
            if (path.isEmpty()) {
                continue;
            }
            WebAssetBuilder asset = new WebAssetBuilder();
            asset.path = path;
            asset.size = parseSize(properties.getProperty(ASSET_PROPERTY_PREFIX + index + ".size"));
            result.values.add(asset);
        }
        return result;
    }

    private static int parseCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int count = Integer.parseInt(value);
            if (count < 0) {
                throw new IllegalArgumentException("Web asset count must not be negative: " + value);
            }
            return count;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid web asset count: " + value, error);
        }
    }

    private static int parseSize(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            long size = Long.parseLong(value);
            if (size < 0 || size > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Web asset size is outside supported range: " + value);
            }
            return (int) size;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid web asset size: " + value, error);
        }
    }

    /**
     * Builds web asset instances and related output.
     *
     * @author xpenatan
     */
    public static final class WebAssetBuilder extends ObjectResourceBuilder {
        private static final String[] FIELD_NAMES = { "path", "size" };

        String path;
        int size;

        /**
         * Returns the value.
         *
         * @param index the index
         * @return the get value
         */
        @Override
        public Object getValue(int index) {
            return switch (index) {
                case 0 -> path;
                case 1 -> size;
                default -> null;
            };
        }

        /**
         * Returns the field names.
         *
         * @return the field names
         */
        @Override
        public String[] fieldNames() {
            return FIELD_NAMES;
        }

        /**
         * Returns the output class.
         *
         * @return the get output class
         */
        @Override
        public Class<? extends Resource> getOutputClass() {
            return WebGeneratedAsset.class;
        }
    }
}
