package io.github.libfdx.graphics.g3d;

/** One typed value attached to a {@link Material}. */
public interface MaterialAttribute {
    /**
     * Returns the stable type token used for lookup and serialization.
     *
     * @return attribute type
     */
    MaterialAttributeType<? extends MaterialAttribute> type();
}
