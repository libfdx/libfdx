package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderId;

public interface GraphicsAttachmentProvider {
    ProviderId providerId();

    GraphicsAttachmentRequirements requirements();

    GraphicsAttachment create(GraphicsEnvironment environment);
}
