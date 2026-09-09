package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;

/**
 * A provider detected terminal loss of a graphics resource domain. Existing handles
 * cannot be revived by surface resize or native context restoration. Stop recording,
 * dispose application-owned graphics resources, then reconstruct them on a fresh
 * backend/provider context. Detection and recovery support are provider-specific;
 * this exception does not promise automatic recovery or a callback on every device.
 */
public final class GraphicsContextLostException extends FdxException {
    private final ProviderId providerId;

    /** Creates a terminal loss report for the non-null provider identity. */
    public GraphicsContextLostException(ProviderId providerId) {
        super("Graphics context lost for " + providerId + "; dispose resources and rebuild on a fresh context");
        if (providerId==null) throw new FdxException("Graphics loss requires a provider identity");
        this.providerId=providerId;
    }

    /** Identity of the provider that reported the loss. */
    public ProviderId providerId() { return providerId; }
}
