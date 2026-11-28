package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.maps.TileMap;
import io.github.libfdx.maps.tiled.TiledMapLoader;
import io.github.libfdx.samples.g2d.platformer.input.TestPlatformerInput;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

final class AuthoredPlatformerLevelTest {
    @Test
    void everyTrailCanVisitFourPlacesSolveItsGateAndReachItsDestinationInOneRun() {
        var assets = new DefaultAssetManager(new DefaultFileSystem().classpathResourceResolver(
                path -> getClass().getResourceAsStream("/" + path)));
        var names = new HashSet<String>();
        var campaignShots = new HashSet<RangedAttack.Kind>();
        try {
            TiledMapLoader.register(assets);
            for (Trail trail : Trail.values()) {
                var lease = assets.acquire(AssetDescriptor.of(trail.mapPath(), TileMap.class));
                try {
                    for (int i = 0; i < 100 && !lease.future().isDone(); i++) assets.update(4, Long.MAX_VALUE);
                    TileMap map = lease.future().get();
                    var input = new TestPlatformerInput();
                    var game = PlatformerLevel.create(input, map);
                    assertEquals(4, game.siteCount(), trail.title);
                    for (int i = 0; i < game.siteCount(); i++) assertTrue(names.add(game.siteName(i)));
                    var creatures = new HashSet<PlatformerGame.EnemyKind>();
                    var trailShots = new HashSet<RangedAttack.Kind>();
                    int shooters = 0;
                    for (int i = 0; i < game.enemyCount(); i++) {
                        var enemy = game.enemyAt(i);
                        creatures.add(enemy.kind());
                        if (enemy.attack() != null) {
                            shooters++;
                            trailShots.add(enemy.attack().kind());
                        }
                    }
                    assertEquals(1, trailShots.size(), trail.title);
                    assertTrue(campaignShots.addAll(trailShots), trail.title);
                    assertTrue(shooters > 0 && shooters < game.enemyCount(), trail.title);
                    assertEquals(3, creatures.size(), trail.title);
                    assertEquals(14, game.enemyCount(), trail.title);
                    assertTrue(map.worldWidth() >= 3500, trail.title);
                    assertEquals(1, game.goalCount());
                    float spawnX = game.player().x(), spawnY = game.player().y();

                    assertGateStopsThePlayer(game, input);
                    game.restart();
                    playRoute(trail, game, input);
                    assertTrue(game.gate().solved(), trail.title);
                    assertTrue(game.completed(), trail.title);
                    assertTrue(game.playerOnGround(), trail.title);
                    assertEquals(4, game.visitedCount(), trail.title);
                    assertTrue(game.coinsCollected() > 0);
                    game.retry();
                    assertTrue(game.gate().solved(), trail.title);
                    assertTrue(game.player().x() > game.gate().x(), trail.title);
                    game.restart();
                    assertFalse(game.gate().solved(), trail.title);
                    assertEquals(spawnX, game.player().x());
                    assertEquals(spawnY, game.player().y());
                    assertEquals(0, game.visitedCount());
                    assertEquals(0, game.coinsCollected());
                    assertFalse(game.completed());
                    assertEquals(36, map.findObject(100).x());
                    if (trail == Trail.RAVINE) assertCanDescend(game, input);
                } finally {
                    lease.dispose();
                }
            }
        } finally {
            assets.dispose();
        }
    }

    private static void assertCanDescend(PlatformerGame game, TestPlatformerInput input) {
        // Enter the first raised bridge from below, land on it, then drop back to the low road.
        PlatformerGame.Sprite bridge = null;
        for (int i = 0; i < game.solidCount(); i++) {
            if (game.solidAt(i).isBridge()) {
                bridge = game.solidAt(i);
                break;
            }
        }
        assertNotNull(bridge);
        input.right = input.jump = false;
        var player = game.player();
        player.position(bridge.x(), player.y());
        for (int i = 0; i < 10; i++) game.update(1f / 60);
        float lowRoad = player.y();
        input.jump = true;
        game.update(1f / 60);
        input.jump = false;
        for (int i = 0; i < 60; i++) game.update(1f / 60);
        assertEquals(bridge.y() + bridge.halfHeight() + player.halfHeight(), player.y(), .0001);
        assertTrue(game.playerOnGround());

        input.drop = true;
        for (int i = 0; i < 60; i++) game.update(1f / 60);
        assertTrue(game.playerOnGround());
        assertEquals(lowRoad, player.y(), .0001);
    }

    private static void assertGateStopsThePlayer(PlatformerGame game, TestPlatformerInput input) {
        var gate = game.gate();
        game.restart();
        game.player().position(gate.x() - .24f, gate.y() + game.player().halfHeight());
        input.right = true;
        for (int frame = 0; frame < 75; frame++) {
            input.jump = frame == 7;
            game.update(1f / 60);
        }
        assertEquals(gate.x() - ClockworkGate.HALF_WIDTH - game.player().halfWidth(), game.player().x(), .0001);
        assertFalse(game.completed());
        input.right = input.jump = false;
    }

    // Recorded fixed-step controls. R/L/S hold right, hold left or stop. J jumps and T turns a dial.
    private static void playRoute(Trail trail, PlatformerGame game, TestPlatformerInput input) {
        String[] actions = route(trail).split(" ");
        int next = 0;
        for (int frame = 0; frame < 2400 && !game.completed() && !game.gameOver(); frame++) {
            input.jump = input.drop = false;
            while (next < actions.length) {
                String action = actions[next];
                int end = action.length() - 1;
                if (Integer.parseInt(action.substring(0, end)) != frame) break;
                switch (action.charAt(end)) {
                    case 'J' -> input.jump = true;
                    case 'T' -> input.drop = true;
                    case 'R', 'L', 'S' -> {
                        input.right = action.charAt(end) == 'R';
                        input.left = action.charAt(end) == 'L';
                    }
                    default -> fail("Unknown replay action " + action);
                }
                next++;
            }
            game.update(1f / 60);
        }
    }

    private static String route(Trail trail) {
        return switch (trail) {
            case RAVINE -> "0R 14J 67J 132J 205J 247J 285J 319J 374J 475J 528J 571J 603J 681J 745J 817J 861J 927J"
                    + " 994T 1103T 1104L 1129J 1153S 1160T 1161R 1162J 1213J 1270J 1355J 1392J 1473J 1527J"
                    + " 1593J 1663J";
            case QUARRY -> "0R 0J 92J 188J 231J 288J 338J 397J 508J 561J 613J 657J 713J 782J 827J 904J 955J 1047J"
                    + " 1149T 1150L 1179J 1203S 1211T 1212R 1227J 1291J 1323J 1391J 1477J 1541J 1576J 1644J"
                    + " 1729J 1799J";
            case HOLLOW -> "0R 98J 181J 237J 325J 399J 478J 601J 674J 718J 768J 816J 865J 915J 954J 1072J 1096S"
                    + " 1104T 1105L 1153T 1154R 1253J 1278S 1286L 1286J 1310S 1319T 1320R 1329J 1386J 1427J"
                    + " 1484J 1556J 1610J 1664J 1711J 1783J";
            case SUMMIT -> "0R 10J 92J 158J 253J 317J 359J 421J 456J 527J 561J 622J 674J 714J 825J 866J 976J"
                    + " 1046J 1069S 1078T 1079R 1133T 1168J 1198J 1252J 1294J 1361J 1394J 1451J 1520J 1567J"
                    + " 1615J 1662J";
            case FORGE -> "0R 13J 54J 155J 196J 244J 294J 340J 381J 500J 622J 670J 711J 763J 815J 883J 921J 991J"
                    + " 1081J 1238J 1262S 1271L 1271J 1295S 1304T 1305L 1349T 1401T 1402R 1403J 1435J 1499J"
                    + " 1534J 1594J 1699J 1768J 1845J 1897J 1931J 1982J";
            case SOUND -> "0R 118J 187J 225J 271J 309J 348J 441J 503J 543J 581J 631J 698J 739J 794J 891J 942J"
                    + " 983J 1020J 1189J 1214S 1221T 1222L 1269T 1317J 1341S 1349T 1350R 1351J 1448J 1490J"
                    + " 1544J 1581J 1634J 1692J 1748J 1796J 1858J 1901J 1960J 2000J";
        };
    }

}
