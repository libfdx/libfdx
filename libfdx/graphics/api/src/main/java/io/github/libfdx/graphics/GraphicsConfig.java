package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.display.Display;

public final class GraphicsConfig {
    private final GraphicsAttachmentProvider provider;
    private final Display display;

    private GraphicsConfig(GraphicsAttachmentProvider provider, Display display) {
        if (provider == null) {
            throw new FdxException("Graphics provider cannot be null");
        }
        this.provider = provider;
        this.display = display;
    }

    public static GraphicsConfig provider(GraphicsAttachmentProvider provider) {
        return new GraphicsConfig(provider, null);
    }

    public GraphicsAttachmentProvider provider() {
        return provider;
    }

    public GraphicsConfig display(Display display) {
        if (display == null) {
            throw new FdxException("Graphics display cannot be null");
        }
        return new GraphicsConfig(provider, display);
    }

    public Display display() {
        return display;
    }
}
