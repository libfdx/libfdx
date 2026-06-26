package io.github.libfdx.samples.multiplayer.webrtc;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.graphics.g2d.TileLayer;
import io.github.libfdx.graphics.g2d.TileMap;
import io.github.libfdx.graphics.g2d.TileMapRenderer;
import io.github.libfdx.graphics.g2d.TileSet;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.net.Network;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetBufferPoolConfig;
import io.github.libfdx.net.buffer.NetReader;
import io.github.libfdx.net.buffer.NetWriter;
import io.github.libfdx.net.config.NetChannelConfig;
import io.github.libfdx.net.config.NetProcessingConfig;
import io.github.libfdx.net.packet.NetPacket;
import io.github.libfdx.net.transport.NetClient;
import io.github.libfdx.net.transport.NetClientListener;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.transport.NetServer;
import io.github.libfdx.net.transport.NetServerListener;
import io.github.libfdx.net.webrtc.config.WebRtcClientConfig;
import io.github.libfdx.net.webrtc.config.WebRtcServerConfig;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingListener;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessage;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessageType;
import io.github.libfdx.net.webrtc.transport.WebRtcNetworkProvider;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiToolkit;
import io.github.libfdx.validation.scenario.ScenarioContext;
import io.github.libfdx.validation.scenario.ScenarioHost;
import io.github.libfdx.validation.scenario.ScenarioInputDriver;
import io.github.libfdx.validation.scenario.ScenarioReport;
import io.github.libfdx.validation.scenario.ScenarioResult;
import io.github.libfdx.validation.scenario.ScenarioValidator;
import java.nio.ByteBuffer;

/**
 * WebRTC-only multiplayer sample with a signaling-backed lobby and small 2D arena.
 *
 * @author xpenatan
 */
public final class MultiplayerWebRtcApplication extends ApplicationAdapter {
    private static final int CHANNEL_RELIABLE = 0;
    private static final int CHANNEL_UNRELIABLE = 1;
    private static final int TILE_SIZE = 16;
    private static final int TILE_COLUMNS = 4;
    private static final int MAP_WIDTH = 18;
    private static final int MAP_HEIGHT = 11;
    private static final int MAX_ROOMS = 16;
    private static final int MAX_PLAYERS = 8;
    private static final int MAX_BULLETS = 96;
    private static final int MAX_SIGNALING_EVENTS = 64;
    private static final float CAMERA_VIEW_WORLD_WIDTH = 2.0f;
    private static final float CAMERA_VIEW_WORLD_HEIGHT = 2.0f;
    private static final float TILE_WORLD_SIZE = 0.18f;
    private static final float ARENA_MIN_X = -1.46f;
    private static final float ARENA_MAX_X = 1.46f;
    private static final float ARENA_MIN_Y = -0.86f;
    private static final float ARENA_MAX_Y = 0.86f;
    private static final float PLAYER_SIZE = 0.14f;
    private static final float BULLET_SIZE = 0.055f;
    private static final float PLAYER_SPEED = 1.05f;
    private static final float BULLET_SPEED = 1.95f;
    private static final float SHOOT_INTERVAL = 0.18f;
    private static final float SEND_INTERVAL = 1.0f / 30.0f;
    private static final float SNAPSHOT_INTERVAL = 1.0f / 20.0f;
    private static final byte MSG_WELCOME = 1;
    private static final byte MSG_INPUT = 2;
    private static final byte MSG_SHOT = 3;
    private static final byte MSG_SNAPSHOT = 4;
    private static final byte MSG_BULLET = 5;
    private static final int[] TILE_COLORS = new int[] {
            0x334B3BFF,
            0x4A7051FF,
            0x546674FF,
            0x2D3646FF
    };

    private final MultiplayerWebRtcConfig config;
    private final RoomEntry[] rooms = new RoomEntry[MAX_ROOMS];
    private final Player[] players = new Player[MAX_PLAYERS];
    private final Bullet[] bullets = new Bullet[MAX_BULLETS];
    private final SignalingEvent[] signalingEvents = new SignalingEvent[MAX_SIGNALING_EVENTS];
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Input input;
    private DefaultInput validationInput;
    private Logger logger;
    private UiRoot ui;
    private SpriteBatch batch;
    private TileMapRenderer tileRenderer;
    private TileMap map;
    private TileSet tileSet;
    private Texture tileAtlas;
    private Texture playerAtlas;
    private Texture bulletTexture;
    private TextureRegion[] playerRegions;
    private TextureRegion bulletRegion;
    private WebRtcPlatformFactory platformFactory;
    private Network network;
    private WebRtcSignalingClient lobbySignaling;
    private NetServer server;
    private NetClient client;
    private Screen screen = Screen.MAIN;
    private Role role = Role.NONE;
    private int roomCount;
    private int localPlayerId;
    private int nextBulletId = 1;
    private int signalHead;
    private int signalTail;
    private boolean lobbyConnected;
    private boolean roomRegistered;
    private boolean autoActionDone;
    private String lobbyPeerId;
    private String activeRoomId;
    private String activeRoomName;
    private String status = "Ready";
    private String error = "";
    private float shootCooldown;
    private float sendTimer;
    private float snapshotTimer;
    private float roomUpdateTimer;
    private float cameraX;
    private float cameraY;
    private float validationTwoPlayerDemoTime;
    private float validationRememberedLocalX;
    private long renderedFrames;
    private boolean validationComplete;
    private boolean validationTwoPlayerDemo;

    /**
     * Creates the multiplayer sample.
     *
     * @param config the sample config
     */
    public MultiplayerWebRtcApplication(MultiplayerWebRtcConfig config) {
        if (config == null) {
            throw new FdxException("Multiplayer WebRTC sample config cannot be null");
        }
        this.config = config;
        for (int i = 0; i < rooms.length; i++) {
            rooms[i] = new RoomEntry();
        }
        for (int i = 0; i < players.length; i++) {
            players[i] = new Player();
            players[i].id = i;
        }
        for (int i = 0; i < bullets.length; i++) {
            bullets[i] = new Bullet();
        }
        for (int i = 0; i < signalingEvents.length; i++) {
            signalingEvents[i] = new SignalingEvent();
        }
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        Input backendInput = fdx.input();
        if (config.validationEnabled()) {
            validationInput = backendInput instanceof DefaultInput ? (DefaultInput)backendInput : new DefaultInput();
            input = validationInput;
        } else {
            input = backendInput;
        }
        logger = fdx.logger();
        platformFactory = config.platformFactory();
        network = new WebRtcNetworkProvider(platformFactory).createNetwork();
        batch = new SpriteBatch(graphics);
        tileRenderer = new TileMapRenderer();
        createGeneratedAssets();
        createArena();
        ui = new UiToolkit(fdx.files()).root(display, graphics).input(input);
        ui.setContent(this::buildUi);
        connectLobby();
        logger.info("WebRTC multiplayer sample created with signaling " + config.signalingUrl());
    }

    @Override
    public void resize(int width, int height) {
        if (ui != null) {
            ui.resize(width, height);
        }
    }

    @Override
    public void render() {
        if (application == null) {
            return;
        }
        float delta = application.deltaTime();
        updateSignaling(delta);
        updateNetwork(delta);
        updateGame(delta);
        renderGame();
        if (ui != null && !validationTwoPlayerDemo) {
            ui.update(delta);
            ui.render();
        }
        runValidationScenariosIfNeeded();
        renderedFrames++;
        if (config.exitAfterFrames() > 0 && renderedFrames >= config.exitAfterFrames()) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        unregisterRoom();
        closeEndpoint();
        closeLobby();
        disposeTexture(tileAtlas);
        disposeTexture(playerAtlas);
        disposeTexture(bulletTexture);
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (ui != null) {
            ui.dispose();
            ui = null;
        }
        if (platformFactory != null) {
            platformFactory.dispose();
            platformFactory = null;
        }
        if (logger != null) {
            logger.info("WebRTC multiplayer sample disposed");
        }
    }

    private void buildUi(UiScope root) {
        if (screen == Screen.GAME) {
            buildUiPanel(root, Ui.modifier().width(360.0f).padding(12.0f).gap(8.0f)
                    .validationId("webrtc.root"));
            return;
        }
        root.column(Ui.modifier().fill().padding(28.0f), column -> {
            column.stack(Ui.modifier().fillWidth().weight(1.0f), spacer -> {
            });
            buildUiPanel(column, Ui.modifier().width(420.0f).maxWidth(420.0f).padding(18.0f).gap(10.0f)
                    .align(UiAlign.CENTER).validationId("webrtc.root"));
            column.stack(Ui.modifier().fillWidth().weight(1.0f), spacer -> {
            });
        });
    }

    private void buildUiPanel(UiScope root, UiModifier modifier) {
        root.panel(modifier, panel -> {
            panel.text("WEBRTC ARENA", Ui.modifier().validationId("webrtc.title"));
            if (screen != Screen.MAIN) {
                if (error != null && error.length() > 0) {
                    panel.text(error, Ui.modifier().validationId("webrtc.error"));
                }
                panel.text(status, Ui.modifier().validationId("webrtc.status"));
            }
            if (screen == Screen.MAIN) {
                buildMainUi(panel);
            } else if (screen == Screen.LOBBY) {
                buildLobbyUi(panel);
            } else {
                buildGameUi(panel);
            }
        });
    }

    private void buildMainUi(UiScope panel) {
        panel.button("MULTIPLAYER", Ui.modifier().validationId("webrtc.main.multiplayer"), () -> {
            screen = Screen.LOBBY;
            error = "";
            requestUiCompose();
            requestRoomList();
        });
    }

    private void buildLobbyUi(UiScope panel) {
        panel.row(Ui.modifier().gap(6.0f), row -> {
            row.button("CREATE ROOM", Ui.modifier().validationId("webrtc.lobby.create"), this::createRoom);
            row.button("REFRESH", Ui.modifier().validationId("webrtc.lobby.refresh"), this::requestRoomList);
        });
        if (!lobbyConnected) {
            panel.text("SIGNALING CONNECTING", Ui.modifier().validationId("webrtc.lobby.connecting"));
        } else if (roomCount == 0) {
            panel.text("NO ROOMS", Ui.modifier().validationId("webrtc.lobby.empty"));
        }
        for (int i = 0; i < roomCount; i++) {
            RoomEntry room = rooms[i];
            final String joinRoomId = room.roomId;
            panel.row(Ui.modifier().gap(6.0f).validationId("webrtc.lobby.room." + i), row -> {
                row.text(room.name + "  " + room.players + "/" + room.maxPeers,
                        Ui.modifier().width(220.0f).validationId("webrtc.lobby.roomLabel"));
                row.button("JOIN", Ui.modifier().validationId("webrtc.lobby.join." + joinRoomId),
                        () -> joinRoom(joinRoomId));
            });
        }
        panel.button("BACK", Ui.modifier().validationId("webrtc.lobby.back"), () -> {
            screen = Screen.MAIN;
            requestUiCompose();
        });
    }

    private void buildGameUi(UiScope panel) {
        String mode = role == Role.HOST ? "HOST" : "CLIENT";
        panel.text(mode + "  ROOM " + activeRoomName, Ui.modifier().validationId("webrtc.game.mode"));
        panel.text("PLAYERS " + activePlayerCount() + "  BULLETS " + activeBulletCount(),
                Ui.modifier().validationId("webrtc.game.stats"));
        panel.button("LEAVE", Ui.modifier().validationId("webrtc.game.leave"), this::leaveGame);
    }

    private void connectLobby() {
        lobbySignaling = platformFactory.signalingClient();
        String requestedPeerId = cleanId(config.playerName()) + "-" + Long.toHexString(System.nanoTime());
        lobbySignaling.connect(config.signalingUrl(), config.lobbyRoomId(), requestedPeerId,
                new WebRtcSignalingListener() {
                    @Override
                    public void connected(String localPeerId) {
                        queueSignalingConnected(localPeerId);
                    }

                    @Override
                    public void message(WebRtcSignalingMessage message) {
                        queueSignalingMessage(message);
                    }

                    @Override
                    public void disconnected(String reason) {
                        queueSignalingDisconnected(reason);
                    }

                    @Override
                    public void error(Throwable error) {
                        queueSignalingError(error);
                    }
                });
        status = "Connecting to signaling";
        requestUiCompose();
    }

    private void updateSignaling(float delta) {
        if (lobbySignaling != null) {
            lobbySignaling.process(delta);
        }
        drainSignalingEvents();
        if (!autoActionDone && lobbyConnected) {
            if (config.autoHost()) {
                autoActionDone = true;
                screen = Screen.LOBBY;
                requestUiCompose();
                createRoom();
            } else if (config.autoJoinRoom() != null) {
                autoActionDone = true;
                screen = Screen.LOBBY;
                requestUiCompose();
                joinRoom(config.autoJoinRoom());
            }
        }
    }

    private void updateNetwork(float delta) {
        if (server != null) {
            server.process(delta);
        }
        if (client != null) {
            client.process(delta);
        }
    }

    private void updateGame(float delta) {
        if (screen != Screen.GAME) {
            return;
        }
        shootCooldown = Math.max(0.0f, shootCooldown - delta);
        Player local = players[localPlayerId];
        if (validationTwoPlayerDemo) {
            updateValidationTwoPlayerDemo(delta);
        } else {
            updateLocalPlayer(local, delta);
        }
        updateBullets(delta);
        sendTimer += delta;
        snapshotTimer += delta;
        if (role == Role.HOST) {
            roomUpdateTimer += delta;
            if (snapshotTimer >= SNAPSHOT_INTERVAL) {
                snapshotTimer = 0.0f;
                broadcastSnapshot();
            }
            if (roomRegistered && roomUpdateTimer >= 1.0f) {
                roomUpdateTimer = 0.0f;
                registerRoomUpdate();
            }
        } else if (sendTimer >= SEND_INTERVAL) {
            sendTimer = 0.0f;
            sendClientInput(local);
        }
    }

    private void updateLocalPlayer(Player player, float delta) {
        if (player == null || !player.active) {
            return;
        }
        float dx = 0.0f;
        float dy = 0.0f;
        if (input.isKeyPressed(Key.A) || input.isKeyPressed(Key.LEFT)) {
            dx -= 1.0f;
        }
        if (input.isKeyPressed(Key.D) || input.isKeyPressed(Key.RIGHT)) {
            dx += 1.0f;
        }
        if (input.isKeyPressed(Key.W) || input.isKeyPressed(Key.UP)) {
            dy += 1.0f;
        }
        if (input.isKeyPressed(Key.S) || input.isKeyPressed(Key.DOWN)) {
            dy -= 1.0f;
        }
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length > 0.0001f) {
            dx /= length;
            dy /= length;
            player.x = clamp(player.x + dx * PLAYER_SPEED * delta, ARENA_MIN_X, ARENA_MAX_X);
            player.y = clamp(player.y + dy * PLAYER_SPEED * delta, ARENA_MIN_Y, ARENA_MAX_Y);
        }
        updateCamera(player);
        player.aimX = pointerWorldX();
        player.aimY = pointerWorldY();
        if (input.isMouseButtonPressed(MouseButton.LEFT) && shootCooldown <= 0.0f) {
            shootCooldown = SHOOT_INTERVAL;
            if (role == Role.HOST) {
                spawnBullet(player.id, player.x, player.y, player.aimX, player.aimY, true);
            } else {
                sendClientShot(player);
            }
        }
    }

    private void updateBullets(float delta) {
        for (int i = 0; i < bullets.length; i++) {
            Bullet bullet = bullets[i];
            if (!bullet.active) {
                continue;
            }
            bullet.x += bullet.vx * delta;
            bullet.y += bullet.vy * delta;
            bullet.life -= delta;
            if (bullet.life <= 0.0f || bullet.x < ARENA_MIN_X - 0.25f || bullet.x > ARENA_MAX_X + 0.25f
                    || bullet.y < ARENA_MIN_Y - 0.25f || bullet.y > ARENA_MAX_Y + 0.25f) {
                bullet.active = false;
            }
        }
    }

    private void renderGame() {
        if (batch == null) {
            return;
        }
        if (screen != Screen.GAME) {
            batch.begin(LoadOp.clear(0.035f, 0.04f, 0.055f, 1.0f));
            batch.end();
            return;
        }
        updateCamera(localPlayer());
        float mapX = -map.worldWidth() * 0.5f;
        float mapY = -map.worldHeight() * 0.5f;
        batch.begin(LoadOp.clear(0.055f, 0.065f, 0.085f, 1.0f));
        tileRenderer.render(map, tileSet, batch, viewX(mapX), viewY(mapY));
        for (int i = 0; i < bullets.length; i++) {
            Bullet bullet = bullets[i];
            if (bullet.active) {
                batch.draw(bulletRegion, viewX(bullet.x - BULLET_SIZE * 0.5f),
                        viewY(bullet.y - BULLET_SIZE * 0.5f),
                        BULLET_SIZE, BULLET_SIZE);
            }
        }
        for (int i = 0; i < players.length; i++) {
            Player player = players[i];
            if (!player.active) {
                continue;
            }
            TextureRegion region = playerRegions[Math.min(playerRegions.length - 1, player.id)];
            float angle = angleDegrees(player.aimX - player.x, player.aimY - player.y);
            batch.draw(region, viewX(player.x - PLAYER_SIZE * 0.5f), viewY(player.y - PLAYER_SIZE * 0.5f),
                    PLAYER_SIZE, PLAYER_SIZE, PLAYER_SIZE * 0.5f, PLAYER_SIZE * 0.5f, angle);
        }
        batch.end();
    }

    private void createRoom() {
        if (!lobbyConnected) {
            error = "Signaling is not connected";
            requestUiCompose();
            return;
        }
        closeEndpoint();
        resetGameState();
        role = Role.HOST;
        screen = Screen.GAME;
        localPlayerId = 0;
        activeRoomId = config.hostRoomId() != null ? config.hostRoomId()
                : "arena-" + Long.toHexString(System.nanoTime());
        activeRoomName = config.playerName() + "'s room";
        Player host = players[0];
        host.active = true;
        host.x = -0.4f;
        host.y = 0.0f;
        host.aimX = 0.4f;
        host.aimY = 0.0f;
        server = network.transports().listen(WebRtcServerConfig.builder()
                .signalingUrl(config.signalingUrl())
                .roomId(activeRoomId)
                .hostPeerId(lobbyPeerId != null ? lobbyPeerId : cleanId(config.playerName()))
                .maxConnections(MAX_PLAYERS - 1)
                .buffers(bufferConfig())
                .processing(processingConfig())
                .channels(NetChannelConfig.reliable(CHANNEL_RELIABLE), NetChannelConfig.unreliable(CHANNEL_UNRELIABLE))
                .stunServers("stun:stun.l.google.com:19302")
                .build(), new ServerEvents());
        registerRoom();
        status = "Hosting room";
        requestUiCompose();
    }

    private void joinRoom(String roomId) {
        if (roomId == null || roomId.length() == 0) {
            return;
        }
        closeEndpoint();
        resetGameState();
        role = Role.CLIENT;
        screen = Screen.GAME;
        localPlayerId = 1;
        activeRoomId = roomId;
        activeRoomName = roomId;
        Player local = players[localPlayerId];
        local.active = true;
        local.x = 0.35f;
        local.y = 0.0f;
        local.aimX = -0.2f;
        local.aimY = 0.0f;
        client = network.transports().connect(WebRtcClientConfig.builder()
                .signalingUrl(config.signalingUrl())
                .roomId(roomId)
                .peerId((lobbyPeerId != null ? lobbyPeerId : cleanId(config.playerName())) + "-client")
                .buffers(bufferConfig())
                .processing(processingConfig())
                .channels(NetChannelConfig.reliable(CHANNEL_RELIABLE), NetChannelConfig.unreliable(CHANNEL_UNRELIABLE))
                .stunServers("stun:stun.l.google.com:19302")
                .build(), new ClientEvents());
        status = "Joining room";
        requestUiCompose();
    }

    private void leaveGame() {
        unregisterRoom();
        closeEndpoint();
        resetGameState();
        role = Role.NONE;
        screen = Screen.LOBBY;
        status = "Left room";
        requestUiCompose();
        requestRoomList();
    }

    private void registerRoom() {
        if (lobbySignaling == null || !lobbyConnected || activeRoomId == null || roomRegistered) {
            return;
        }
        JsonValue payload = JsonValue.object()
                .put("roomId", activeRoomId)
                .put("name", activeRoomName)
                .put("hostPeerId", lobbyPeerId)
                .put("players", activePlayerCount())
                .put("maxPeers", MAX_PLAYERS);
        lobbySignaling.send(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_REGISTER)
                .payload(payload)
                .build());
        roomRegistered = true;
    }

    private void unregisterRoom() {
        if (lobbySignaling == null || !lobbyConnected || activeRoomId == null || !roomRegistered) {
            roomRegistered = false;
            return;
        }
        lobbySignaling.send(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_UNREGISTER)
                .payload(JsonValue.object().put("roomId", activeRoomId))
                .build());
        roomRegistered = false;
    }

    private void requestRoomList() {
        if (lobbySignaling != null && lobbyConnected) {
            lobbySignaling.send(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_LIST).build());
            status = "Refreshing rooms";
            requestUiCompose();
        }
    }

    private void sendClientInput(Player local) {
        if (client == null || !client.isConnected()) {
            return;
        }
        NetConnection connection = client.connection();
        if (connection == null) {
            return;
        }
        NetBuffer buffer = client.buffers().tryAcquire();
        if (buffer == null) {
            return;
        }
        NetWriter writer = buffer.writer();
        writer.putByte(MSG_INPUT)
                .putByte(localPlayerId)
                .putFloat(local.x)
                .putFloat(local.y)
                .putFloat(local.aimX)
                .putFloat(local.aimY);
        connection.send(CHANNEL_UNRELIABLE, buffer);
        buffer.release();
    }

    private void sendClientShot(Player local) {
        if (client == null || !client.isConnected()) {
            return;
        }
        NetConnection connection = client.connection();
        if (connection == null) {
            return;
        }
        NetBuffer buffer = client.buffers().tryAcquire();
        if (buffer == null) {
            return;
        }
        buffer.writer()
                .putByte(MSG_SHOT)
                .putByte(localPlayerId)
                .putFloat(local.x)
                .putFloat(local.y)
                .putFloat(local.aimX)
                .putFloat(local.aimY);
        connection.send(CHANNEL_RELIABLE, buffer);
        buffer.release();
    }

    private void broadcastSnapshot() {
        if (server == null || server.connectionCount() == 0) {
            return;
        }
        NetBuffer buffer = server.buffers().tryAcquire();
        if (buffer == null) {
            return;
        }
        NetWriter writer = buffer.writer();
        writer.putByte(MSG_SNAPSHOT);
        int countPosition = writer.position();
        writer.putByte(0);
        int count = 0;
        for (int i = 0; i < players.length; i++) {
            Player player = players[i];
            if (!player.active) {
                continue;
            }
            writer.putByte(player.id)
                    .putFloat(player.x)
                    .putFloat(player.y)
                    .putFloat(player.aimX)
                    .putFloat(player.aimY);
            count++;
        }
        buffer.bytes()[countPosition] = (byte)count;
        server.broadcast(CHANNEL_UNRELIABLE, buffer);
        buffer.release();
    }

    private void registerRoomUpdate() {
        if (lobbySignaling == null || !lobbyConnected || activeRoomId == null) {
            return;
        }
        JsonValue payload = JsonValue.object()
                .put("roomId", activeRoomId)
                .put("name", activeRoomName)
                .put("hostPeerId", lobbyPeerId)
                .put("players", activePlayerCount())
                .put("maxPeers", MAX_PLAYERS);
        lobbySignaling.send(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_REGISTER)
                .payload(payload)
                .build());
    }

    private void sendWelcome(NetConnection connection, int playerId) {
        if (server == null || connection == null) {
            return;
        }
        NetBuffer buffer = server.buffers().tryAcquire();
        if (buffer == null) {
            return;
        }
        buffer.writer().putByte(MSG_WELCOME).putByte(playerId);
        connection.send(CHANNEL_RELIABLE, buffer);
        buffer.release();
    }

    private void broadcastBullet(Bullet bullet) {
        if (server == null || bullet == null) {
            return;
        }
        NetBuffer buffer = server.buffers().tryAcquire();
        if (buffer == null) {
            return;
        }
        buffer.writer()
                .putByte(MSG_BULLET)
                .putInt(bullet.id)
                .putByte(bullet.ownerId)
                .putFloat(bullet.x)
                .putFloat(bullet.y)
                .putFloat(bullet.vx)
                .putFloat(bullet.vy);
        NetSendResult result = server.broadcast(CHANNEL_RELIABLE, buffer);
        if (result != NetSendResult.SENT && result != NetSendResult.QUEUED) {
            status = "Bullet send " + result;
            requestUiCompose();
        }
        buffer.release();
    }

    private void onServerMessage(NetConnection connection, NetPacket packet) {
        NetReader reader = packet.reader();
        int type = reader.getUnsignedByte();
        if (type == MSG_INPUT) {
            int playerId = playerIdForConnection(connection);
            if (playerId <= 0) {
                return;
            }
            int declaredId = reader.getUnsignedByte();
            Player player = players[playerId];
            if (declaredId >= 0) {
                player.x = clamp(reader.getFloat(), ARENA_MIN_X, ARENA_MAX_X);
                player.y = clamp(reader.getFloat(), ARENA_MIN_Y, ARENA_MAX_Y);
                player.aimX = reader.getFloat();
                player.aimY = reader.getFloat();
            }
        } else if (type == MSG_SHOT) {
            int playerId = playerIdForConnection(connection);
            if (playerId <= 0) {
                return;
            }
            reader.getUnsignedByte();
            float x = reader.getFloat();
            float y = reader.getFloat();
            float aimX = reader.getFloat();
            float aimY = reader.getFloat();
            spawnBullet(playerId, x, y, aimX, aimY, true);
        }
    }

    private void onClientMessage(NetPacket packet) {
        NetReader reader = packet.reader();
        int type = reader.getUnsignedByte();
        if (type == MSG_WELCOME) {
            int assigned = reader.getUnsignedByte();
            if (assigned > 0 && assigned < players.length) {
                localPlayerId = assigned;
                players[localPlayerId].active = true;
                status = "Connected as player " + assigned;
                requestUiCompose();
            }
        } else if (type == MSG_SNAPSHOT) {
            int count = reader.getUnsignedByte();
            for (int i = 0; i < count; i++) {
                int id = reader.getUnsignedByte();
                float x = reader.getFloat();
                float y = reader.getFloat();
                float aimX = reader.getFloat();
                float aimY = reader.getFloat();
                if (id >= 0 && id < players.length && id != localPlayerId) {
                    Player player = players[id];
                    player.active = true;
                    player.x = x;
                    player.y = y;
                    player.aimX = aimX;
                    player.aimY = aimY;
                }
            }
        } else if (type == MSG_BULLET) {
            Bullet bullet = firstFreeBullet();
            if (bullet == null) {
                return;
            }
            bullet.active = true;
            bullet.id = reader.getInt();
            bullet.ownerId = reader.getUnsignedByte();
            bullet.x = reader.getFloat();
            bullet.y = reader.getFloat();
            bullet.vx = reader.getFloat();
            bullet.vy = reader.getFloat();
            bullet.life = 1.8f;
        }
    }

    private int assignPlayer(NetConnection connection) {
        for (int i = 1; i < players.length; i++) {
            Player player = players[i];
            if (!player.active) {
                player.active = true;
                player.connectionId = connection.id();
                player.x = 0.25f + i * 0.06f;
                player.y = -0.25f + i * 0.04f;
                player.aimX = 0.0f;
                player.aimY = 0.0f;
                return i;
            }
        }
        return -1;
    }

    private int playerIdForConnection(NetConnection connection) {
        if (connection == null) {
            return -1;
        }
        int connectionId = connection.id();
        for (int i = 1; i < players.length; i++) {
            if (players[i].active && players[i].connectionId == connectionId) {
                return i;
            }
        }
        return -1;
    }

    private void removeConnection(NetConnection connection) {
        int playerId = playerIdForConnection(connection);
        if (playerId > 0) {
            players[playerId].active = false;
            players[playerId].connectionId = -1;
        }
    }

    private Bullet spawnBullet(int ownerId, float x, float y, float aimX, float aimY, boolean broadcast) {
        Bullet bullet = firstFreeBullet();
        if (bullet == null) {
            return null;
        }
        float dx = aimX - x;
        float dy = aimY - y;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001f) {
            dx = 1.0f;
            dy = 0.0f;
        } else {
            dx /= length;
            dy /= length;
        }
        bullet.active = true;
        bullet.id = nextBulletId++;
        bullet.ownerId = ownerId;
        bullet.x = x + dx * (PLAYER_SIZE * 0.65f);
        bullet.y = y + dy * (PLAYER_SIZE * 0.65f);
        bullet.vx = dx * BULLET_SPEED;
        bullet.vy = dy * BULLET_SPEED;
        bullet.life = 1.8f;
        if (broadcast) {
            broadcastBullet(bullet);
        }
        return bullet;
    }

    private Bullet firstFreeBullet() {
        for (int i = 0; i < bullets.length; i++) {
            if (!bullets[i].active) {
                return bullets[i];
            }
        }
        return null;
    }

    private void parseRoomList(JsonValue payload) {
        JsonValue roomsValue = payload != null ? payload.get("rooms") : null;
        roomCount = 0;
        if (roomsValue == null || !roomsValue.isArray()) {
            status = "Rooms refreshed";
            requestUiCompose();
            return;
        }
        int count = Math.min(MAX_ROOMS, roomsValue.size());
        for (int i = 0; i < count; i++) {
            JsonValue value = roomsValue.get(i);
            if (value == null || !value.isObject()) {
                continue;
            }
            RoomEntry room = rooms[roomCount++];
            room.roomId = value.stringValue("roomId", "");
            room.name = value.stringValue("name", room.roomId);
            room.hostPeerId = value.stringValue("hostPeerId", "");
            room.players = value.intValue("players", 1);
            room.maxPeers = value.intValue("maxPeers", MAX_PLAYERS);
        }
        status = "Rooms refreshed";
        requestUiCompose();
    }

    private void resetGameState() {
        for (int i = 0; i < players.length; i++) {
            players[i].active = false;
            players[i].connectionId = -1;
            players[i].x = 0.0f;
            players[i].y = 0.0f;
            players[i].aimX = 1.0f;
            players[i].aimY = 0.0f;
        }
        for (int i = 0; i < bullets.length; i++) {
            bullets[i].active = false;
        }
        shootCooldown = 0.0f;
        sendTimer = 0.0f;
        snapshotTimer = 0.0f;
        roomUpdateTimer = 0.0f;
        nextBulletId = 1;
        cameraX = 0.0f;
        cameraY = 0.0f;
        validationTwoPlayerDemo = false;
        validationTwoPlayerDemoTime = 0.0f;
    }

    private void runValidationScenariosIfNeeded() {
        if (!config.validationEnabled() || validationComplete || ui == null || validationInput == null) {
            return;
        }
        validationComplete = true;
        ScenarioHost host = ScenarioHost.create()
                .frameDeltaMillis(16L)
                .frameDriver(this::advanceValidationFrame)
                .inputDriver(new ValidationInputDriver())
                .registerProbe(UiRoot.class, ui)
                .registerProbe(MultiplayerWebRtcApplication.class, this);
        ScenarioValidator validator = ScenarioValidator.fromSystemProperties(host,
                MultiplayerWebRtcValidationScenarios.catalog());
        if (config.validationSelection() != null) {
            validator.select(config.validationSelection());
        }
        ScenarioReport report = validator.run();
        if (logger != null) {
            logger.info("WebRTC sample validation " + report.summary());
        }
        if (!report.passed()) {
            ScenarioResult failure = firstFailure(report);
            String message = failureMessage(failure);
            error = message;
            requestUiCompose();
            throw new FdxException(message);
        }
        status = "Validation passed";
        requestUiCompose();
    }

    private void advanceValidationFrame(ScenarioContext context) {
        float delta = context.host().frameDeltaMillis() / 1000.0f;
        updateSignaling(delta);
        updateNetwork(delta);
        updateGame(delta);
        if (ui != null) {
            ui.update(delta);
            ui.rootNode();
        }
    }

    private ScenarioResult firstFailure(ScenarioReport report) {
        for (int i = 0; i < report.results().size(); i++) {
            ScenarioResult result = report.results().get(i);
            if (!result.passed()) {
                return result;
            }
        }
        return null;
    }

    private String failureMessage(ScenarioResult failure) {
        if (failure == null) {
            return "WebRTC sample scenario validation failed";
        }
        return "WebRTC sample scenario failed: " + failure.scenarioName()
                + " at " + failure.operationName()
                + " - " + failure.message();
    }

    void validationResetToMain() {
        unregisterRoom();
        closeEndpoint();
        resetGameState();
        roomRegistered = false;
        role = Role.NONE;
        screen = Screen.MAIN;
        activeRoomId = null;
        activeRoomName = null;
        error = "";
        status = "Ready";
        roomCount = 0;
        requestUiCompose();
    }

    void validationPrepareLobby() {
        closeEndpoint();
        resetGameState();
        closeLobby();
        lobbyConnected = true;
        lobbyPeerId = "validation-peer";
        roomRegistered = false;
        role = Role.NONE;
        screen = Screen.LOBBY;
        activeRoomId = null;
        activeRoomName = null;
        error = "";
        status = "Signaling connected";
        requestUiCompose();
    }

    void validationPrepareRoomList() {
        validationPrepareLobby();
        roomCount = 1;
        RoomEntry room = rooms[0];
        room.roomId = "validation-room";
        room.name = "Validation Room";
        room.hostPeerId = "validation-host";
        room.players = 1;
        room.maxPeers = MAX_PLAYERS;
        requestUiCompose();
    }

    void validationEnterHostGame() {
        closeEndpoint();
        resetGameState();
        role = Role.HOST;
        screen = Screen.GAME;
        localPlayerId = 0;
        activeRoomId = "validation-room";
        activeRoomName = "Validation Room";
        Player host = players[0];
        host.active = true;
        host.x = -0.4f;
        host.y = 0.0f;
        host.aimX = 0.5f;
        host.aimY = 0.0f;
        error = "";
        status = "Hosting room";
        requestUiCompose();
    }

    void validationEnterClientGame() {
        closeEndpoint();
        resetGameState();
        role = Role.CLIENT;
        screen = Screen.GAME;
        localPlayerId = 1;
        activeRoomId = "validation-room";
        activeRoomName = "Validation Room";
        Player clientPlayer = players[1];
        clientPlayer.active = true;
        clientPlayer.x = 0.35f;
        clientPlayer.y = 0.0f;
        clientPlayer.aimX = -0.2f;
        clientPlayer.aimY = 0.0f;
        error = "";
        status = "Joined room";
        requestUiCompose();
    }

    void validationRememberLocalPlayerX() {
        validationRememberedLocalX = players[localPlayerId].x;
    }

    boolean validationLocalPlayerMovedRight() {
        return players[localPlayerId].x > validationRememberedLocalX + 0.01f;
    }

    void validationClearBullets() {
        for (int i = 0; i < bullets.length; i++) {
            bullets[i].active = false;
        }
        shootCooldown = 0.0f;
    }

    int validationActiveBulletCount() {
        return activeBulletCount();
    }

    boolean validationIsHost() {
        return role == Role.HOST && screen == Screen.GAME;
    }

    boolean validationIsClient() {
        return role == Role.CLIENT && screen == Screen.GAME;
    }

    boolean validationCameraTracksLocalPlayer() {
        Player local = localPlayer();
        return local != null && local.active
                && Math.abs(cameraX - local.x) < 0.0001f
                && Math.abs(cameraY - local.y) < 0.0001f;
    }

    void validationEnterTwoPlayerVideoDemo() {
        closeEndpoint();
        resetGameState();
        role = Role.HOST;
        screen = Screen.GAME;
        localPlayerId = 0;
        activeRoomId = "video-demo-room";
        activeRoomName = "Video Demo Room";
        Player local = players[0];
        local.active = true;
        local.x = -0.55f;
        local.y = -0.15f;
        local.aimX = 0.55f;
        local.aimY = 0.15f;
        Player remote = players[1];
        remote.active = true;
        remote.x = 0.55f;
        remote.y = 0.15f;
        remote.aimX = local.x;
        remote.aimY = local.y;
        error = "";
        status = "Two player video demo";
        validationTwoPlayerDemo = true;
        validationTwoPlayerDemoTime = 0.0f;
        updateCamera(local);
        requestUiCompose();
    }

    boolean validationTwoPlayerVideoDemoActive() {
        return validationTwoPlayerDemo && screen == Screen.GAME && players[0].active && players[1].active;
    }

    void validationSimulateHostDisconnected() {
        closeEndpoint();
        resetGameState();
        role = Role.NONE;
        screen = Screen.LOBBY;
        error = "Host disconnected";
        status = "Disconnected from host";
        requestUiCompose();
    }

    private void closeEndpoint() {
        if (client != null) {
            client.dispose();
            client = null;
        }
        if (server != null) {
            server.dispose();
            server = null;
        }
    }

    private void closeLobby() {
        if (lobbySignaling != null) {
            lobbySignaling.close();
            lobbySignaling = null;
        }
    }

    private void createGeneratedAssets() {
        tileAtlas = createTexture("webrtc-sample-tiles", TILE_SIZE * TILE_COLUMNS, TILE_SIZE,
                this::writeTilePixel);
        tileSet = TileSet.from(TextureRegion.split(tileAtlas, TILE_SIZE, TILE_SIZE));
        playerAtlas = createTexture("webrtc-sample-players", 32 * MAX_PLAYERS, 32, this::writePlayerPixel);
        playerRegions = new TextureRegion[MAX_PLAYERS];
        for (int i = 0; i < MAX_PLAYERS; i++) {
            playerRegions[i] = new TextureRegion(playerAtlas, i * 32, 0, 32, 32);
        }
        bulletTexture = createTexture("webrtc-sample-bullet", 12, 12, this::writeBulletPixel);
        bulletRegion = new TextureRegion(bulletTexture);
    }

    private Texture createTexture(String label, int width, int height, PixelWriter writer) {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(label, width, height));
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                putRgba(pixels, writer.rgba(x, y, width, height));
            }
        }
        pixels.flip();
        graphics.device().writeTexture(texture, pixels);
        return texture;
    }

    private int writeTilePixel(int x, int y, int width, int height) {
        int tile = x / TILE_SIZE;
        int localX = x % TILE_SIZE;
        int localY = y % TILE_SIZE;
        boolean edge = localX == 0 || localY == 0 || localX == TILE_SIZE - 1 || localY == TILE_SIZE - 1;
        boolean detail = ((localX * 3 + localY * 5 + tile * 7) & 15) == 0;
        int color = TILE_COLORS[tile];
        if (edge) {
            return brighten(color, 36);
        }
        if (detail) {
            return brighten(color, 18);
        }
        return color;
    }

    private int writePlayerPixel(int x, int y, int width, int height) {
        int player = x / 32;
        int localX = x % 32;
        int localY = y;
        float dx = localX - 15.5f;
        float dy = localY - 15.5f;
        float distance = (float)Math.sqrt(dx * dx + dy * dy);
        if (distance > 14.5f) {
            return 0x00000000;
        }
        int base = player == 0 ? 0x4EA5FFFF : 0xFFB347FF;
        if (player > 1) {
            int red = 80 + (player * 37) % 155;
            int green = 90 + (player * 53) % 145;
            int blue = 110 + (player * 29) % 125;
            base = red << 24 | green << 16 | blue << 8 | 0xFF;
        }
        boolean nose = localX > 17 && Math.abs(localY - 16) <= 4;
        if (nose) {
            return 0xF8F5D7FF;
        }
        if (distance > 11.5f) {
            return darken(base, 45);
        }
        return base;
    }

    private int writeBulletPixel(int x, int y, int width, int height) {
        float dx = x - (width - 1) * 0.5f;
        float dy = y - (height - 1) * 0.5f;
        float distance = (float)Math.sqrt(dx * dx + dy * dy);
        if (distance > 5.0f) {
            return 0x00000000;
        }
        return distance < 2.5f ? 0xFFF8B2FF : 0xF4B942FF;
    }

    private void createArena() {
        map = new TileMap(MAP_WIDTH, MAP_HEIGHT, TILE_WORLD_SIZE, TILE_WORLD_SIZE);
        TileLayer ground = map.addLayer();
        for (int y = 0; y < MAP_HEIGHT; y++) {
            for (int x = 0; x < MAP_WIDTH; x++) {
                int tile = 1 + (x + y) % 2;
                if (x == 0 || y == 0 || x == MAP_WIDTH - 1 || y == MAP_HEIGHT - 1) {
                    tile = 4;
                } else if (x == MAP_WIDTH / 2 || y == MAP_HEIGHT / 2) {
                    tile = 3;
                }
                ground.tile(x, y, tile);
            }
        }
    }

    private NetBufferPoolConfig bufferConfig() {
        return NetBufferPoolConfig.builder()
                .initialPackets(128)
                .maxPackets(512)
                .packetBytes(512)
                .build();
    }

    private NetProcessingConfig processingConfig() {
        return NetProcessingConfig.builder()
                .tickRate(30)
                .maxTicksPerFrame(2)
                .maxReceivePacketsPerTick(64)
                .maxReceiveBytesPerTick(32 * 1024)
                .maxSendPacketsPerTick(64)
                .dropUnreliableWhenBehind(true)
                .build();
    }

    private void queueSignalingConnected(String peerId) {
        SignalingEvent event = nextSignalingEvent();
        if (event != null) {
            event.type = SignalingEvent.CONNECTED;
            event.text = peerId;
            event.message = null;
            event.error = null;
        }
    }

    private void queueSignalingMessage(WebRtcSignalingMessage message) {
        SignalingEvent event = nextSignalingEvent();
        if (event != null) {
            event.type = SignalingEvent.MESSAGE;
            event.text = null;
            event.message = message;
            event.error = null;
        }
    }

    private void queueSignalingDisconnected(String reason) {
        SignalingEvent event = nextSignalingEvent();
        if (event != null) {
            event.type = SignalingEvent.DISCONNECTED;
            event.text = reason;
            event.message = null;
            event.error = null;
        }
    }

    private void queueSignalingError(Throwable throwable) {
        SignalingEvent event = nextSignalingEvent();
        if (event != null) {
            event.type = SignalingEvent.ERROR;
            event.text = null;
            event.message = null;
            event.error = throwable;
        }
    }

    private SignalingEvent nextSignalingEvent() {
        synchronized (signalingEvents) {
            int nextTail = (signalTail + 1) % signalingEvents.length;
            if (nextTail == signalHead) {
                signalHead = (signalHead + 1) % signalingEvents.length;
            }
            SignalingEvent event = signalingEvents[signalTail];
            signalTail = nextTail;
            return event;
        }
    }

    private void drainSignalingEvents() {
        while (true) {
            SignalingEvent event;
            synchronized (signalingEvents) {
                if (signalHead == signalTail) {
                    return;
                }
                event = signalingEvents[signalHead];
                signalHead = (signalHead + 1) % signalingEvents.length;
            }
            handleSignalingEvent(event);
            event.message = null;
            event.error = null;
            event.text = null;
        }
    }

    private void handleSignalingEvent(SignalingEvent event) {
        if (event.type == SignalingEvent.CONNECTED) {
            lobbyConnected = true;
            lobbyPeerId = event.text;
            status = "Signaling connected";
            requestRoomList();
        } else if (event.type == SignalingEvent.MESSAGE) {
            WebRtcSignalingMessage message = event.message;
            if (message != null && (message.type() == WebRtcSignalingMessageType.ROOM_LIST
                    || message.type() == WebRtcSignalingMessageType.ROOM_LIST_CHANGED)) {
                parseRoomList(message.payload());
            }
        } else if (event.type == SignalingEvent.DISCONNECTED) {
            lobbyConnected = false;
            status = "Signaling disconnected";
            if (event.text != null && event.text.length() > 0) {
                error = event.text;
            }
        } else if (event.type == SignalingEvent.ERROR) {
            error = event.error != null ? event.error.getMessage() : "Signaling error";
        }
        requestUiCompose();
    }

    private int activePlayerCount() {
        int count = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].active) {
                count++;
            }
        }
        return count;
    }

    private int activeBulletCount() {
        int count = 0;
        for (int i = 0; i < bullets.length; i++) {
            if (bullets[i].active) {
                count++;
            }
        }
        return count;
    }

    private Player localPlayer() {
        if (localPlayerId < 0 || localPlayerId >= players.length) {
            return null;
        }
        return players[localPlayerId];
    }

    private void updateCamera(Player player) {
        if (player == null || !player.active) {
            return;
        }
        cameraX = player.x;
        cameraY = player.y;
    }

    private void updateValidationTwoPlayerDemo(float delta) {
        validationTwoPlayerDemoTime += delta;
        Player local = players[0];
        Player remote = players[1];
        local.active = true;
        remote.active = true;
        float t = validationTwoPlayerDemoTime;
        local.x = clamp((float)Math.sin(t * 1.15f) * 0.74f, ARENA_MIN_X, ARENA_MAX_X);
        local.y = clamp((float)Math.sin(t * 0.82f) * 0.42f, ARENA_MIN_Y, ARENA_MAX_Y);
        remote.x = clamp((float)Math.cos(t * 0.95f) * 0.82f, ARENA_MIN_X, ARENA_MAX_X);
        remote.y = clamp((float)Math.sin(t * 1.35f + 1.2f) * 0.54f, ARENA_MIN_Y, ARENA_MAX_Y);
        local.aimX = remote.x;
        local.aimY = remote.y;
        remote.aimX = local.x;
        remote.aimY = local.y;
        updateCamera(local);
    }

    private float viewX(float worldX) {
        return worldX - cameraX;
    }

    private float viewY(float worldY) {
        return worldY - cameraY;
    }

    private float pointerWorldX() {
        int width = display.width() > 0 ? display.width() : 1;
        return cameraX + (input.pointerX() / (float)width) * CAMERA_VIEW_WORLD_WIDTH
                - CAMERA_VIEW_WORLD_WIDTH * 0.5f;
    }

    private float pointerWorldY() {
        int height = display.height() > 0 ? display.height() : 1;
        return cameraY + CAMERA_VIEW_WORLD_HEIGHT * 0.5f
                - (input.pointerY() / (float)height) * CAMERA_VIEW_WORLD_HEIGHT;
    }

    private float angleDegrees(float x, float y) {
        return (float)Math.toDegrees(Math.atan2(y, x));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private String cleanId(String value) {
        if (value == null || value.length() == 0) {
            return "player";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            } else if (builder.length() == 0 || builder.charAt(builder.length() - 1) != '-') {
                builder.append('-');
            }
        }
        return builder.length() > 0 ? builder.toString() : "player";
    }

    private int brighten(int rgba, int amount) {
        int red = Math.min(255, ((rgba >>> 24) & 0xFF) + amount);
        int green = Math.min(255, ((rgba >>> 16) & 0xFF) + amount);
        int blue = Math.min(255, ((rgba >>> 8) & 0xFF) + amount);
        return red << 24 | green << 16 | blue << 8 | (rgba & 0xFF);
    }

    private int darken(int rgba, int amount) {
        int red = Math.max(0, ((rgba >>> 24) & 0xFF) - amount);
        int green = Math.max(0, ((rgba >>> 16) & 0xFF) - amount);
        int blue = Math.max(0, ((rgba >>> 8) & 0xFF) - amount);
        return red << 24 | green << 16 | blue << 8 | (rgba & 0xFF);
    }

    private void putRgba(ByteBuffer pixels, int rgba) {
        pixels.put((byte)((rgba >>> 24) & 0xFF));
        pixels.put((byte)((rgba >>> 16) & 0xFF));
        pixels.put((byte)((rgba >>> 8) & 0xFF));
        pixels.put((byte)(rgba & 0xFF));
    }

    private void disposeTexture(Texture texture) {
        if (texture != null) {
            texture.dispose();
        }
    }

    private void requestUiCompose() {
        if (ui != null) {
            ui.requestCompose();
        }
    }

    private final class ValidationInputDriver implements ScenarioInputDriver {
        @Override
        public void keyDown(Key key) {
            validationInput.dispatchKeyDown(key);
        }

        @Override
        public void keyUp(Key key) {
            validationInput.dispatchKeyUp(key);
        }

        @Override
        public void pointerMove(float x, float y) {
            validationInput.dispatchPointerMoved(Math.round(x), Math.round(y));
        }

        @Override
        public void pointerDown(float x, float y) {
            validationInput.dispatchPointerDown(MouseButton.LEFT, Math.round(x), Math.round(y));
        }

        @Override
        public void pointerUp(float x, float y) {
            validationInput.dispatchPointerUp(MouseButton.LEFT, Math.round(x), Math.round(y));
        }

        @Override
        public void text(String text) {
            validationInput.dispatchTextInput(text != null ? text : "");
        }

        @Override
        public void scroll(float amountX, float amountY) {
            int x = display != null ? Math.max(0, display.width() / 2) : 0;
            int y = display != null ? Math.max(0, display.height() / 2) : 0;
            validationInput.dispatchScrolled(x, y, amountX, amountY);
        }
    }

    private enum Screen {
        MAIN,
        LOBBY,
        GAME
    }

    private enum Role {
        NONE,
        HOST,
        CLIENT
    }

    private interface PixelWriter {
        int rgba(int x, int y, int width, int height);
    }

    private static final class RoomEntry {
        private String roomId = "";
        private String name = "";
        private String hostPeerId = "";
        private int players;
        private int maxPeers;
    }

    private static final class Player {
        private int id;
        private int connectionId = -1;
        private boolean active;
        private float x;
        private float y;
        private float aimX = 1.0f;
        private float aimY;
    }

    private static final class Bullet {
        private int id;
        private int ownerId;
        private boolean active;
        private float x;
        private float y;
        private float vx;
        private float vy;
        private float life;
    }

    private static final class SignalingEvent {
        private static final int CONNECTED = 1;
        private static final int MESSAGE = 2;
        private static final int DISCONNECTED = 3;
        private static final int ERROR = 4;
        private int type;
        private String text;
        private WebRtcSignalingMessage message;
        private Throwable error;
    }

    private final class ServerEvents implements NetServerListener {
        @Override
        public void started(NetServer server) {
            status = "Room ready";
        }

        @Override
        public void connected(NetConnection connection) {
            int playerId = assignPlayer(connection);
            if (playerId < 0) {
                connection.close();
                return;
            }
            sendWelcome(connection, playerId);
            status = "Player joined";
            registerRoomUpdate();
        }

        @Override
        public void disconnected(NetConnection connection) {
            removeConnection(connection);
            status = "Player left";
            registerRoomUpdate();
        }

        @Override
        public void message(NetConnection connection, NetPacket packet) {
            onServerMessage(connection, packet);
        }

        @Override
        public void error(Throwable error) {
            MultiplayerWebRtcApplication.this.error = error != null ? error.getMessage() : "Server error";
        }
    }

    private final class ClientEvents implements NetClientListener {
        @Override
        public void connected(NetConnection connection) {
            status = "Connected to host";
        }

        @Override
        public void disconnected(NetConnection connection) {
            closeEndpoint();
            resetGameState();
            role = Role.NONE;
            screen = Screen.LOBBY;
            error = "Host disconnected";
            status = "Disconnected";
            requestRoomList();
        }

        @Override
        public void message(NetConnection connection, NetPacket packet) {
            onClientMessage(packet);
        }

        @Override
        public void error(Throwable error) {
            MultiplayerWebRtcApplication.this.error = error != null ? error.getMessage() : "Client error";
        }
    }
}
