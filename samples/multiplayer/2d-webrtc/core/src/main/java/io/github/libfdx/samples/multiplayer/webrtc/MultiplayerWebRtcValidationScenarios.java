package io.github.libfdx.samples.multiplayer.webrtc;

import io.github.libfdx.input.Key;
import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioActions;
import io.github.libfdx.validation.scenario.ScenarioCatalog;
import io.github.libfdx.validation.scenario.ScenarioContext;
import io.github.libfdx.validation.scenario.ScenarioWaits;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioActions;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioAssertions;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioWaits;

/**
 * Scenario-validator coverage for the WebRTC multiplayer sample.
 *
 * @author xpenatan
 */
public final class MultiplayerWebRtcValidationScenarios {
    public static final String ROOT = "webrtc.root";
    public static final String TITLE = "webrtc.title";
    public static final String STATUS = "webrtc.status";
    public static final String ERROR = "webrtc.error";
    public static final String MAIN_MULTIPLAYER = "webrtc.main.multiplayer";
    public static final String LOBBY_CREATE = "webrtc.lobby.create";
    public static final String LOBBY_REFRESH = "webrtc.lobby.refresh";
    public static final String LOBBY_BACK = "webrtc.lobby.back";
    public static final String LOBBY_EMPTY = "webrtc.lobby.empty";
    public static final String VALIDATION_ROOM_JOIN = "webrtc.lobby.join.validation-room";
    public static final String GAME_MODE = "webrtc.game.mode";
    public static final String GAME_STATS = "webrtc.game.stats";
    public static final String GAME_LEAVE = "webrtc.game.leave";

    private MultiplayerWebRtcValidationScenarios() {
    }

    /**
     * Builds the sample scenario catalog.
     *
     * @return the scenario catalog
     */
    public static ScenarioCatalog catalog() {
        return ScenarioCatalog.create()
                .add(mainScreen())
                .add(openLobby())
                .add(createRoom())
                .add(joinRoom())
                .add(movePlayer())
                .add(shootBullet())
                .add(hostDisconnected())
                .add(twoPlayerVideo());
    }

    private static Scenario mainScreen() {
        return Scenario.named("main-screen")
                .custom("reset-main", context -> app(context).validationResetToMain())
                .waitFor(UiScenarioWaits.visible(MAIN_MULTIPLAYER).timeoutFrames(2))
                .expect(UiScenarioAssertions.visible(ROOT))
                .expect(UiScenarioAssertions.textEquals(TITLE, "WEBRTC ARENA"))
                .expect(UiScenarioAssertions.visible(MAIN_MULTIPLAYER));
    }

    private static Scenario openLobby() {
        return Scenario.named("open-lobby")
                .custom("reset-main", context -> app(context).validationResetToMain())
                .waitFor(UiScenarioWaits.visible(MAIN_MULTIPLAYER).timeoutFrames(2))
                .action(UiScenarioActions.press(MAIN_MULTIPLAYER))
                .waitFor(ScenarioWaits.frames(1))
                .action(UiScenarioActions.release(MAIN_MULTIPLAYER))
                .custom("show-lobby", context -> app(context).validationPrepareLobby())
                .waitFor(UiScenarioWaits.visible(LOBBY_CREATE).timeoutFrames(3))
                .expect(UiScenarioAssertions.visible(LOBBY_CREATE))
                .expect(UiScenarioAssertions.visible(LOBBY_REFRESH))
                .expect(UiScenarioAssertions.visible(LOBBY_BACK));
    }

    private static Scenario createRoom() {
        return Scenario.named("create-room")
                .custom("prepare-lobby", context -> app(context).validationPrepareLobby())
                .waitFor(UiScenarioWaits.visible(LOBBY_CREATE).timeoutFrames(2))
                .action(UiScenarioActions.press(LOBBY_CREATE))
                .waitFor(ScenarioWaits.frames(1))
                .action(UiScenarioActions.release(LOBBY_CREATE))
                .custom("enter-host-game", context -> app(context).validationEnterHostGame())
                .waitFor(ScenarioWaits.until("host-game", context -> app(context).validationIsHost())
                        .timeoutFrames(4))
                .expect(UiScenarioAssertions.textContains(GAME_MODE, "HOST"))
                .expect(UiScenarioAssertions.visible(GAME_LEAVE))
                .custom("assert-host", context -> context.assertTrue(app(context).validationIsHost(),
                        "Create room did not enter host game state."));
    }

    private static Scenario joinRoom() {
        return Scenario.named("join-room")
                .custom("prepare-room-list", context -> app(context).validationPrepareRoomList())
                .waitFor(UiScenarioWaits.visible(VALIDATION_ROOM_JOIN).timeoutFrames(2))
                .action(UiScenarioActions.press(VALIDATION_ROOM_JOIN))
                .waitFor(ScenarioWaits.frames(1))
                .action(UiScenarioActions.release(VALIDATION_ROOM_JOIN))
                .custom("enter-client-game", context -> app(context).validationEnterClientGame())
                .waitFor(ScenarioWaits.until("client-game", context -> app(context).validationIsClient())
                        .timeoutFrames(4))
                .expect(UiScenarioAssertions.textContains(GAME_MODE, "CLIENT"))
                .expect(UiScenarioAssertions.visible(GAME_STATS))
                .custom("assert-client", context -> context.assertTrue(app(context).validationIsClient(),
                        "Join room did not enter client game state."));
    }

    private static Scenario movePlayer() {
        return Scenario.named("move-player")
                .custom("enter-host-game", context -> app(context).validationEnterHostGame())
                .custom("remember-x", context -> app(context).validationRememberLocalPlayerX())
                .action(ScenarioActions.holdKey(Key.D, 8))
                .custom("assert-moved", context -> context.assertTrue(app(context).validationLocalPlayerMovedRight(),
                        "Local player did not move right while D was held."))
                .custom("assert-camera", context -> context.assertTrue(app(context).validationCameraTracksLocalPlayer(),
                        "Camera did not track the local player."));
    }

    private static Scenario shootBullet() {
        return Scenario.named("shoot-bullet")
                .custom("enter-host-game", context -> app(context).validationEnterHostGame())
                .custom("clear-bullets", context -> app(context).validationClearBullets())
                .action(ScenarioActions.pointerMove(720.0f, 320.0f))
                .action(ScenarioActions.pointerDown())
                .waitFor(ScenarioWaits.frames(1))
                .action(ScenarioActions.pointerUp())
                .custom("assert-bullet", context -> context.assertTrue(app(context).validationActiveBulletCount() > 0,
                        "Host did not spawn a bullet from pointer input."));
    }

    private static Scenario hostDisconnected() {
        return Scenario.named("host-disconnected")
                .custom("enter-client-game", context -> app(context).validationEnterClientGame())
                .custom("disconnect-host", context -> app(context).validationSimulateHostDisconnected())
                .waitFor(UiScenarioWaits.visible(ERROR).timeoutFrames(2))
                .expect(UiScenarioAssertions.textContains(ERROR, "Host disconnected"))
                .expect(UiScenarioAssertions.visible(LOBBY_CREATE));
    }

    private static Scenario twoPlayerVideo() {
        return Scenario.named("two-player-video")
                .custom("enter-two-player-video", context -> app(context).validationEnterTwoPlayerVideoDemo())
                .waitFor(ScenarioWaits.frames(90).timeoutFrames(100))
                .custom("assert-two-player-video", context -> context.assertTrue(
                        app(context).validationTwoPlayerVideoDemoActive(),
                        "Two player video demo is not active."))
                .custom("assert-camera", context -> context.assertTrue(app(context).validationCameraTracksLocalPlayer(),
                        "Camera did not track the local player during the two player video demo."));
    }

    private static MultiplayerWebRtcApplication app(ScenarioContext context) {
        return context.requireProbe(MultiplayerWebRtcApplication.class);
    }
}
