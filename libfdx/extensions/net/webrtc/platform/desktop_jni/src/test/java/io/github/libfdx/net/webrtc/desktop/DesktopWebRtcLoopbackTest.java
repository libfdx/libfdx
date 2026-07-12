package io.github.libfdx.net.webrtc.desktop;

import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.packet.NetPacket;
import io.github.libfdx.net.transport.NetClientListener;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.transport.NetServer;
import io.github.libfdx.net.transport.NetServerListener;
import io.github.libfdx.net.webrtc.config.WebRtcClientConfig;
import io.github.libfdx.net.webrtc.config.WebRtcServerConfig;
import io.github.libfdx.net.webrtc.signaling.server.WebRtcSignalingServer;
import io.github.libfdx.net.webrtc.signaling.server.WebRtcSignalingServerConfig;
import io.github.libfdx.net.webrtc.transport.WebRtcNetClient;
import io.github.libfdx.net.webrtc.transport.WebRtcNetServer;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;





final class DesktopWebRtcLoopbackTest {
    @Test
    void serverAndClientExchangeReliablePackets() throws Exception {
        int port = freePort();
        WebRtcSignalingServer signalingServer = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .idleTimeoutMillis(0)
                .build());
        DesktopWebRtcPlatformFactory serverFactory = new DesktopWebRtcPlatformFactory();
        DesktopWebRtcPlatformFactory clientFactory = new DesktopWebRtcPlatformFactory();
        WebRtcNetServer server = null;
        WebRtcNetClient client = null;
        ServerEvents serverEvents = new ServerEvents();
        ClientEvents clientEvents = new ClientEvents();
        signalingServer.start();
        try {
            String signalingUrl = "ws://127.0.0.1:" + port + "/";
            server = new WebRtcNetServer(WebRtcServerConfig.builder()
                    .signalingUrl(signalingUrl)
                    .roomId("desktop-loopback")
                    .hostPeerId("server")
                    .build(), serverEvents, serverFactory);
            client = new WebRtcNetClient(WebRtcClientConfig.builder()
                    .signalingUrl(signalingUrl)
                    .roomId("desktop-loopback")
                    .peerId("client")
                    .build(), clientEvents, clientFactory);

            WebRtcNetServer loopServer = server;
            WebRtcNetClient loopClient = client;
            assertTrue(processUntil(new Condition() {
                @Override
                public boolean met() {
                    return serverEvents.started && serverEvents.connected && clientEvents.connected;
                }
            }, signalingServer, loopServer, loopClient, 10000), "Desktop WebRTC peers did not connect");

            NetBuffer clientPacket = client.buffers().acquire();
            clientPacket.writer().putByte(42);
            assertEquals(NetSendResult.SENT, client.connection().send(0, clientPacket));
            clientPacket.release();

            assertTrue(processUntil(new Condition() {
                @Override
                public boolean met() {
                    return serverEvents.lastMessage == 42;
                }
            }, signalingServer, loopServer, loopClient, 5000), "Server did not receive the reliable packet");

            NetBuffer serverPacket = server.buffers().acquire();
            serverPacket.writer().putByte(84);
            assertEquals(NetSendResult.SENT, server.connectionAt(0).send(0, serverPacket));
            serverPacket.release();

            assertTrue(processUntil(new Condition() {
                @Override
                public boolean met() {
                    return clientEvents.lastMessage == 84;
                }
            }, signalingServer, loopServer, loopClient, 5000), "Client did not receive the reliable packet");
        }
        finally {
            if (client != null) {
                client.dispose();
            }
            if (server != null) {
                server.dispose();
            }
            clientFactory.dispose();
            serverFactory.dispose();
            signalingServer.dispose();
        }
    }

    private static boolean processUntil(Condition condition, WebRtcSignalingServer signalingServer,
            WebRtcNetServer server, WebRtcNetClient client, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            signalingServer.process(1f / 60f);
            server.process(1f / 60f);
            client.process(1f / 60f);
            if (condition.met()) {
                return true;
            }
            Thread.sleep(10);
        }
        signalingServer.process(1f / 60f);
        server.process(1f / 60f);
        client.process(1f / 60f);
        return condition.met();
    }

    private static int freePort() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    private interface Condition {
        boolean met();
    }

    private static final class ServerEvents implements NetServerListener {
        private volatile boolean started;
        private volatile boolean connected;
        private volatile int lastMessage = -1;

        @Override
        public void started(io.github.libfdx.net.transport.NetServer server) {
            started = true;
        }

        @Override
        public void connected(NetConnection connection) {
            connected = true;
        }

        @Override
        public void disconnected(NetConnection connection) {
        }

        @Override
        public void message(NetConnection connection, NetPacket packet) {
            lastMessage = packet.reader().getUnsignedByte();
        }

        @Override
        public void error(Throwable error) {
            throw new AssertionError(error);
        }
    }

    private static final class ClientEvents implements NetClientListener {
        private volatile boolean connected;
        private volatile int lastMessage = -1;

        @Override
        public void connected(NetConnection connection) {
            connected = true;
        }

        @Override
        public void disconnected(NetConnection connection) {
        }

        @Override
        public void message(NetConnection connection, NetPacket packet) {
            lastMessage = packet.reader().getUnsignedByte();
        }

        @Override
        public void error(Throwable error) {
            throw new AssertionError(error);
        }
    }
}
