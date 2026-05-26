package io.github.libfdx.graphics;

import io.github.libfdx.core.ProviderHandle;

public interface CommandEncoder extends ProviderHandle {
    RenderPass beginRenderPass(RenderPassDescriptor descriptor);
}
