package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.samples.g2d.platformer.input.PlatformerInput;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.maps.*;

/**
 * Builds the fixed sample level into ordinary application-owned game state.
 */
public final class PlatformerLevel {
    public static final float PIXELS_PER_UNIT = 150;
    public static final int VIEW_WIDTH = 300, VIEW_HEIGHT = 180;
    private static final float ITEM_HALF = 0.038f;
    private static final float ENEMY_HALF_WIDTH = 0.056f;
    private static final float ENEMY_HALF_HEIGHT = 0.058f;

    private PlatformerLevel() {
    }

    /**
     * Builds private gameplay state from borrowed Tiled data. Collision rectangles and
     * entity points are copied; imported layers/properties are never mutated. Unknown
     * gameplay classes, rotated collision shapes and invalid spawns fail before use.
     */
    public static PlatformerGame create(PlatformerInput input, TileMap map) {
        if (map == null || map.worldWidth() < VIEW_WIDTH || map.worldHeight() < VIEW_HEIGHT) {
            throw new FdxException("Platformer maps must be at least 300 pixels wide and at least 180 pixels high");
        }
        PlatformerGame game = new PlatformerGame(input);
        game.levelBounds(-1, world(map.worldWidth()));
        for (int i = 0; i < map.mapLayerCount(); i++) { objects(game, map, map.mapLayer(i), 0, 0, false); }
        if (game.player() == null || game.solidCount() == 0 || game.goalCount() != 1) {
            throw new FdxException("Platformer level needs one player, collision geometry and one goal");
        }
        if (game.gate() != null) game.gate().validate(game.goalAt(0).x());
        return game;
    }
    public static float world(float pixel) { return pixel / PIXELS_PER_UNIT - 1; }
    public static float pixel(float world) { return (world + 1) * PIXELS_PER_UNIT; }
    private static void objects(PlatformerGame game, TileMap map, MapLayer layer, float x, float y, boolean parallax) {
        x += layer.offsetX(); y += layer.offsetY();
        parallax |= layer.parallaxX() != 1 || layer.parallaxY() != 1;
        if (layer instanceof GroupLayer group) {
            for (int i = 0; i < group.layerCount(); i++) { objects(game, map, group.layer(i), x, y, parallax); }
        } else if (layer instanceof ObjectLayer objects) {
            for (int i = 0; i < objects.objectCount(); i++) {
                MapObject object = objects.object(i); String kind = object.className();
                if (kind.isEmpty()) { continue; }
                try {
                    if (object.rotationDegrees() != 0 || parallax) {
                        throw new FdxException("Gameplay objects require unrotated, non-parallax geometry");
                    }
                    float px = x + object.x(), py = y + object.y();
                    if (px < 0 || px > map.worldWidth() || py < 0 || py > map.worldHeight()) {
                        throw new FdxException("Gameplay anchor outside the level");
                    }
                    if (kind.equals("solid") || kind.equals("bridge") || kind.equals("lift")) {
                        if (object.shape() != MapObject.Shape.RECTANGLE || object.width() <= 0 || object.height() <= 0) {
                            throw new FdxException("Collision must be a positive rectangle");
                        }
                        if ((double)px+object.width() > map.worldWidth() || py-object.height() < 0) {
                            throw new FdxException("Collision rectangle outside the level");
                        }
                        var surface = game.addSprite(world(px+object.width()/2), world(py-object.height()/2),
                                object.width()/PIXELS_PER_UNIT/2, object.height()/PIXELS_PER_UNIT/2);
                        game.addSolid(surface);
                        surface.bridge = !kind.equals("solid");
                        surface.lift = kind.equals("lift");
                        continue;
                    }
                    if (object.shape() != MapObject.Shape.POINT) { throw new FdxException("Entity spawn must be a point"); }
                    if (kind.equals("dial")) {
                        game.puzzle().dial(Math.toIntExact(object.properties().get("number").longValue()), world(px), world(py));
                        continue;
                    }
                    if (kind.equals("bell-gate") || kind.equals("lamp-gate")) {
                        game.puzzle().mechanism(kind.equals("bell-gate"),
                                Math.toIntExact(object.properties().get("setting").longValue()), object.name(), world(px), world(py));
                        continue;
                    }
                    if (kind.equals("site")) {
                        game.addSite(object.name(), world(px), world(py));
                        continue;
                    }
                    var scenery = switch (kind) {
                        case "mill" -> PlatformerGame.Scenery.MILL;
                        case "winch" -> PlatformerGame.Scenery.WINCH;
                        case "grove" -> PlatformerGame.Scenery.GROVE;
                        case "portal" -> PlatformerGame.Scenery.PORTAL;
                        default -> null;
                    };
                    if (scenery != null) {
                        game.addScenery(scenery, world(px), world(py));
                        continue;
                    }
                    float halfWidth = 8, halfHeight = 8;
                    switch (kind) {
                        case "player" -> halfHeight = 12;
                        case "coin" -> halfWidth = halfHeight = 6;
                        case "hazard" -> halfHeight = 6;
                        case "enemy", "moth", "hopper" -> {
                            halfWidth = kind.equals("enemy") ? 12 : 9;
                            halfHeight = kind.equals("moth") ? 6 : 8;
                        }
                        case "goal" -> { halfWidth = 10; halfHeight = 19; }
                        default -> throw new FdxException("Unknown gameplay class " + kind);
                    }
                    if (px-halfWidth < 0 || px+halfWidth > map.worldWidth() || py-halfHeight < 0 || py+halfHeight > map.worldHeight()) {
                        throw new FdxException("Entity bounds outside the level");
                    }
                    PlatformerGame.Sprite sprite = game.addSprite(world(px), world(py), halfWidth/PIXELS_PER_UNIT,
                            halfHeight/PIXELS_PER_UNIT);
                    switch (kind) {
                        case "player" -> {
                            if (game.player() != null) { throw new FdxException("Duplicate player spawn"); }
                            game.player(sprite);
                        }
                        case "coin" -> game.addCollectible(sprite, 1);
                        case "hazard" -> game.addHazard(sprite);
                        case "goal" -> game.addGoal(sprite);
                        case "enemy", "moth", "hopper" -> {
                            float min = number(object, "minX"), max = number(object, "maxX"), speed = number(object, "speed");
                            if (min < 0 || max > map.worldWidth() || min > px || max < px || min >= max || speed <= 0) {
                                throw new FdxException("Invalid patrol range/speed");
                            }
                            PlatformerGame.EnemyKind species = switch (kind) {
                                case "moth" -> PlatformerGame.EnemyKind.MOTH;
                                case "hopper" -> PlatformerGame.EnemyKind.HOPPER;
                                default -> PlatformerGame.EnemyKind.CRAWLER;
                            };
                            var shot = object.properties().find("shot");
                            game.addEnemy(sprite, world(min), world(max), speed / PIXELS_PER_UNIT, species,
                                    shot == null ? null : RangedAttack.Kind.valueOf(shot.stringValue()));
                        }
                    }
                } catch (RuntimeException error) { throw new FdxException("Platformer object " + object.id() + ": " + error.getMessage(), error); }
            }
        }
    }
    private static float number(MapObject object, String property) {
        MapProperty value = object.properties().get(property);
        double number = value.type() == MapProperty.Type.INT ? value.longValue() : value.doubleValue();
        if (!Float.isFinite((float)number)) { throw new FdxException("Invalid " + property); }
        return (float)number;
    }

    /**
     * Creates the complete platformer game state.
     *
     * @param input the input source
     * @return the game
     */
    public static PlatformerGame create(PlatformerInput input) {
        PlatformerGame game = new PlatformerGame(input);
        createLevel(game);
        createPlayer(game);
        return game;
    }

    private static void createPlayer(PlatformerGame game) {
        game.player(game.addSprite(
                PlatformerConstants.PLAYER_START_X,
                PlatformerConstants.PLAYER_START_Y,
                PlatformerConstants.PLAYER_HALF_WIDTH,
                PlatformerConstants.PLAYER_HALF_HEIGHT));
    }

    private static void createLevel(PlatformerGame game) {
        createSolidSpan(game, PlatformerConstants.LEVEL_LEFT, PlatformerConstants.GROUND_TILE_Y, 14);
        createSolidSpan(game, 0.92f, PlatformerConstants.GROUND_TILE_Y, 7);
        createSolidSpan(game, 2.00f, PlatformerConstants.GROUND_TILE_Y, 13);

        createSolidSpan(game, -0.56f, -0.55f, 4);
        createSolidSpan(game, 0.06f, -0.33f, 4);
        createSolidSpan(game, 0.76f, -0.09f, 5);
        createSolidSpan(game, 1.48f, -0.35f, 4);
        createSolidSpan(game, 2.26f, -0.08f, 5);
        createSolidSpan(game, 2.86f, -0.45f, 4);

        createCollectible(game, -0.40f, -0.40f);
        createCollectible(game, -0.26f, -0.40f);
        createCollectible(game, 0.20f, -0.18f);
        createCollectible(game, 0.92f, 0.06f);
        createCollectible(game, 1.08f, 0.06f);
        createCollectible(game, 1.62f, -0.20f);
        createCollectible(game, 2.42f, 0.07f);
        createCollectible(game, 2.58f, 0.07f);

        createHazard(game, 0.54f, PlatformerConstants.GROUND_TOP_Y + 0.036f);
        createHazard(game, 2.02f, PlatformerConstants.GROUND_TOP_Y + 0.036f);
        createEnemy(game, 1.26f, PlatformerConstants.GROUND_TOP_Y + ENEMY_HALF_HEIGHT,
                1.04f, 1.62f, 0.30f);
        createEnemy(game, 2.66f, PlatformerConstants.GROUND_TOP_Y + ENEMY_HALF_HEIGHT,
                2.36f, 3.10f, 0.34f);
        createGoal(game, 3.22f, PlatformerConstants.GROUND_TOP_Y + 0.13f);
    }

    private static void createSolidSpan(PlatformerGame game, float startX, float y, int count) {
        float half = PlatformerConstants.TILE_HALF;
        for (int i = 0; i < count; i++) {
            float x = startX + half + i * PlatformerConstants.TILE_SIZE;
            game.addSolid(game.addSprite(x, y, half, half));
        }
    }

    private static void createCollectible(PlatformerGame game, float x, float y) {
        game.addCollectible(game.addSprite(x, y, ITEM_HALF, ITEM_HALF), 1);
    }

    private static void createHazard(PlatformerGame game, float x, float y) {
        game.addHazard(game.addSprite(x, y, PlatformerConstants.TILE_HALF * 0.82f, PlatformerConstants.TILE_HALF * 0.62f));
    }

    private static void createEnemy(PlatformerGame game, float x, float y, float minX, float maxX, float speed) {
        game.addEnemy(game.addSprite(x, y, ENEMY_HALF_WIDTH, ENEMY_HALF_HEIGHT), minX, maxX, speed);
    }

    private static void createGoal(PlatformerGame game, float x, float y) {
        game.addGoal(game.addSprite(x, y, 0.075f, 0.13f));
    }

}
