package io.github.libfdx.net.transport;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.config.NetClientConfig;
import io.github.libfdx.net.config.NetServerConfig;
import io.github.libfdx.net.config.NetPeerConfig;

/**
 * Creates multiplayer transports.
 *
 * @author xpenatan
 */
public interface NetTransports {
    /**
     * Returns whether a provider is supported.
     *
     * @param providerId the provider ID
     * @return true if supported
     */
    boolean supports(ProviderId providerId);

    /**
     * Creates a client endpoint.
     *
     * @param config the config
     * @param listener the listener
     * @return the client endpoint
     */
    NetClient connect(NetClientConfig config, NetClientListener listener);

    /**
     * Creates a server endpoint.
     *
     * @param config the config
     * @param listener the listener
     * @return the server endpoint
     */
    NetServer listen(NetServerConfig config, NetServerListener listener);

    /**
     * Creates a peer group endpoint.
     *
     * @param config the config
     * @param listener the listener
     * @return the peer group endpoint
     */
    NetPeerGroup join(NetPeerConfig config, NetPeerListener listener);
}
