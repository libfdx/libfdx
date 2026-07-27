package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.ShaderStableId;

/**
 * Stable extensible shader artifact format.
 *
 * @author xpenatan
 */
public final class ShaderArtifactFormat implements Comparable<ShaderArtifactFormat> {
    private final String id;
    private final ShaderArtifactEncoding encoding;
    private final String mediaType;

    private ShaderArtifactFormat(String id, ShaderArtifactEncoding encoding, String mediaType) {
        this.id = ShaderStableId.normalize(id, "Shader artifact format");
        if (encoding == null) {
            throw new FdxException("Shader artifact encoding cannot be null");
        }
        this.encoding = encoding;
        this.mediaType = ShaderStableId.requireValue(mediaType, "Shader artifact media type");
    }

    /**
     * Creates a format.
     *
     * @param id the stable ID
     * @param encoding the storage encoding
     * @param mediaType the descriptive media type
     * @return the format
     */
    public static ShaderArtifactFormat of(String id, ShaderArtifactEncoding encoding, String mediaType) {
        return new ShaderArtifactFormat(id, encoding, mediaType);
    }

    /**
     * Returns the stable ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the encoding.
     *
     * @return the encoding
     */
    public ShaderArtifactEncoding encoding() {
        return encoding;
    }

    /**
     * Returns the media type.
     *
     * @return the media type
     */
    public String mediaType() {
        return mediaType;
    }

    @Override
    public int compareTo(ShaderArtifactFormat other) {
        return other != null ? id.compareTo(other.id) : 1;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof ShaderArtifactFormat)) {
            return false;
        }
        ShaderArtifactFormat other = (ShaderArtifactFormat)object;
        return id.equals(other.id) && encoding == other.encoding && mediaType.equals(other.mediaType);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + encoding.hashCode();
        result = 31 * result + mediaType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return id;
    }
}
