package io.github.libfdx.net.spi;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.Network;

/**
 * Creates a backend or extension network service.
 *
 * @author xpenatan
 */
public interface NetworkProvider {
    /**
     * Returns the provider ID.
     *
     * @return the provider ID
     */
    ProviderId providerId();

    /**
     * Creates the network service.
     *
     * @return the network service
     */
    Network createNetwork();
}
