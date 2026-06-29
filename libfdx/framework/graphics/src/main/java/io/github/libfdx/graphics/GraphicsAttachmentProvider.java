package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderId;

/**
 * Defines the provider contract for graphics attachment services.
 *
 * @author xpenatan
 */
public interface GraphicsAttachmentProvider {
    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    ProviderId providerId();

    /**
     * Returns the requirements.
     *
     * @return the requirements
     */
    GraphicsAttachmentRequirements requirements();

    /**
     * Creates a value.
     *
     * @param environment the environment
     * @return the created value
     */
    GraphicsAttachment create(GraphicsEnvironment environment);
}
