package io.github.libfdx.net.webrtc.signaling.server;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the standalone libFDX WebRTC signaling server.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingServerLauncher {
    private static final String HOST_PROPERTY = "libfdx.webrtc.signaling.host";
    private static final String PORT_PROPERTY = "libfdx.webrtc.signaling.port";
    private static final String MAX_PEERS_PROPERTY = "libfdx.webrtc.signaling.maxPeersPerRoom";
    private static final String IDLE_TIMEOUT_PROPERTY = "libfdx.webrtc.signaling.idleTimeoutMillis";
    private static final String LOG_PROPERTY = "libfdx.webrtc.signaling.log";
    private static final String TICK_RATE_PROPERTY = "libfdx.webrtc.signaling.tickRate";
    private static final String MAX_TICKS_PROPERTY = "libfdx.webrtc.signaling.maxTicksPerFrame";
    private static final String MAX_EVENTS_PROPERTY = "libfdx.webrtc.signaling.maxEventsPerTick";
    private static final String MAX_BYTES_PROPERTY = "libfdx.webrtc.signaling.maxBytesPerTick";
    private static final String INITIAL_EVENTS_PROPERTY = "libfdx.webrtc.signaling.initialEvents";
    private static final String MAX_QUEUED_EVENTS_PROPERTY = "libfdx.webrtc.signaling.maxQueuedEvents";

    private WebRtcSignalingServerLauncher() {
    }

    /**
     * Starts the standalone signaling server process.
     *
     * @param args the command-line args
     */
    public static void main(String[] args) {
        if (hasArg(args, "--help") || hasArg(args, "-h")) {
            printUsage();
            return;
        }

        String bindHost = stringOption(args, "--bind-host=", HOST_PROPERTY, "127.0.0.1");
        bindHost = stringOption(args, "--host=", bindHost);
        int port = intOption(args, "--port=", PORT_PROPERTY, 7777);
        int maxPeersPerRoom = intOption(args, "--max-peers-per-room=", MAX_PEERS_PROPERTY, 32);
        long idleTimeoutMillis = longOption(args, "--idle-timeout-millis=", IDLE_TIMEOUT_PROPERTY, 30000L);
        boolean log = booleanOption(args, "--log=", LOG_PROPERTY, true);
        WebRtcSignalingProcessingConfig processing = WebRtcSignalingProcessingConfig.builder()
                .tickRate(intOption(args, "--tick-rate=", TICK_RATE_PROPERTY, 30))
                .maxTicksPerFrame(intOption(args, "--max-ticks-per-frame=", MAX_TICKS_PROPERTY, 2))
                .maxEventsPerTick(intOption(args, "--max-events-per-tick=", MAX_EVENTS_PROPERTY, 128))
                .maxBytesPerTick(intOption(args, "--max-bytes-per-tick=", MAX_BYTES_PROPERTY, 256 * 1024))
                .initialEvents(intOption(args, "--initial-events=", INITIAL_EVENTS_PROPERTY, 256))
                .maxQueuedEvents(intOption(args, "--max-queued-events=", MAX_QUEUED_EVENTS_PROPERTY, 4096))
                .build();

        WebRtcSignalingServerConfig config = WebRtcSignalingServerConfig.builder(port)
                .bindHost(bindHost)
                .maxPeersPerRoom(maxPeersPerRoom)
                .idleTimeoutMillis(idleTimeoutMillis)
                .processing(processing)
                .logger(log ? consoleLogger() : WebRtcSignalingServerLogger.none())
                .build();
        WebRtcSignalingServer server = new WebRtcSignalingServer(config);
        AtomicBoolean running = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            server.dispose();
        }, "libfdx-webrtc-signaling-shutdown"));

        server.start();
        System.out.println("libFDX WebRTC signaling server running on ws://" + displayHost(bindHost) + ":" + port);
        System.out.println("Press Ctrl+C to stop.");
        long lastTime = System.nanoTime();
        try {
            while (running.get()) {
                long now = System.nanoTime();
                float deltaTime = (now - lastTime) / 1000000000.0f;
                lastTime = now;
                server.process(deltaTime);
                Thread.sleep(1L);
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
            server.dispose();
        }
    }

    private static WebRtcSignalingServerLogger consoleLogger() {
        return new WebRtcSignalingServerLogger() {
            @Override
            public void info(String message) {
                System.out.println("[webrtc-signaling] " + message);
            }

            @Override
            public void error(String message, Throwable error) {
                System.err.println("[webrtc-signaling] " + message);
                if (error != null) {
                    error.printStackTrace(System.err);
                }
            }
        };
    }

    private static boolean hasArg(String[] args, String expected) {
        if (args == null) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            if (expected.equals(args[i])) {
                return true;
            }
        }
        return false;
    }

    private static String stringOption(String[] args, String prefix, String propertyName, String fallback) {
        String value = stringOption(args, prefix, null);
        if (value != null) {
            return value;
        }
        value = System.getProperty(propertyName);
        return value != null && value.trim().length() > 0 ? value.trim() : fallback;
    }

    private static String stringOption(String[] args, String prefix, String fallback) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg != null && arg.startsWith(prefix)) {
                    String value = arg.substring(prefix.length()).trim();
                    return value.length() > 0 ? value : fallback;
                }
            }
        }
        return fallback;
    }

    private static int intOption(String[] args, String prefix, String propertyName, int fallback) {
        String value = stringOption(args, prefix, propertyName, Integer.toString(fallback));
        return Integer.parseInt(value);
    }

    private static long longOption(String[] args, String prefix, String propertyName, long fallback) {
        String value = stringOption(args, prefix, propertyName, Long.toString(fallback));
        return Long.parseLong(value);
    }

    private static boolean booleanOption(String[] args, String prefix, String propertyName, boolean fallback) {
        String value = stringOption(args, prefix, propertyName, Boolean.toString(fallback));
        return Boolean.parseBoolean(value);
    }

    private static String displayHost(String bindHost) {
        if ("0.0.0.0".equals(bindHost) || "::".equals(bindHost)) {
            return "127.0.0.1";
        }
        return bindHost;
    }

    private static void printUsage() {
        System.out.println("Usage: webrtc_signaling_server [options]");
        System.out.println("Options:");
        System.out.println("  --host=<host> | --bind-host=<host>       Bind host, default 127.0.0.1");
        System.out.println("  --port=<port>                            Bind port, default 7777");
        System.out.println("  --max-peers-per-room=<count>             Max peers per room, default 32");
        System.out.println("  --idle-timeout-millis=<millis>           Idle peer timeout, default 30000");
        System.out.println("  --log=<true|false>                       Console logging, default true");
        System.out.println("  --tick-rate=<hz>                         Processing tick rate, default 30");
        System.out.println("  --max-ticks-per-frame=<count>            Max processing ticks per loop, default 2");
        System.out.println("  --max-events-per-tick=<count>            Max queued events per tick, default 128");
        System.out.println("  --max-bytes-per-tick=<bytes>             Max queued event bytes per tick, default 262144");
        System.out.println("  --initial-events=<count>                 Preallocated event objects, default 256");
        System.out.println("  --max-queued-events=<count>              Max queued event objects, default 4096");
        System.out.println();
        System.out.println("Gradle properties:");
        System.out.println("  -D" + HOST_PROPERTY + "=127.0.0.1");
        System.out.println("  -D" + PORT_PROPERTY + "=7777");
        System.out.println("  -D" + MAX_PEERS_PROPERTY + "=32");
        System.out.println("  -D" + IDLE_TIMEOUT_PROPERTY + "=30000");
        System.out.println("  -D" + LOG_PROPERTY + "=true");
        System.out.println("  -D" + TICK_RATE_PROPERTY + "=30");
        System.out.println("  -D" + MAX_TICKS_PROPERTY + "=2");
        System.out.println("  -D" + MAX_EVENTS_PROPERTY + "=128");
        System.out.println("  -D" + MAX_BYTES_PROPERTY + "=262144");
        System.out.println("  -D" + INITIAL_EVENTS_PROPERTY + "=256");
        System.out.println("  -D" + MAX_QUEUED_EVENTS_PROPERTY + "=4096");
    }
}
