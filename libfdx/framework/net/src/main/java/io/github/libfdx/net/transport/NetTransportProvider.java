package io.github.libfdx.net.transport;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.config.NetClientConfig;
import io.github.libfdx.net.config.NetServerConfig;
import io.github.libfdx.net.config.NetPeerConfig;

/**
 * Creates provider-backed multiplayer transports.
 *
 * @author xpenatan
 */
public interface NetTransportProvider {
    /**
     * Returns the provider ID.
     *
     * @return the provider ID
     */
    ProviderId providerId();

    /**
     * Returns whether client endpoints are supported.
     *
     * @return true if supported
     */
    boolean supportsClient();

    /**
     * Returns whether server endpoints are supported.
     *
     * @return true if supported
     */
    boolean supportsServer();

    /**
     * Returns whether peer groups are supported.
     *
     * @return true if supported
     */
    boolean supportsPeerGroup();

    /**
     * Creates a client endpoint.
     *
     * @param config the config
     * @param listener the listener
     * @return the client
     */
    NetClient connect(NetClientConfig config, NetClientListener listener);

    /**
     * Creates a server endpoint.
     *
     * @param config the config
     * @param listener the listener
     * @return the server
     */
    NetServer listen(NetServerConfig config, NetServerListener listener);

    /**
     * Creates a peer group endpoint.
     *
     * @param config the config
     * @param listener the listener
     * @return the peer group
     */
    NetPeerGroup join(NetPeerConfig config, NetPeerListener listener);
}
