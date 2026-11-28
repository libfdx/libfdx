package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.samples.g2d.platformer.input.PlatformerInput;

/**
 * Owns the complete platformer simulation with no per-frame allocation.
 *
 * @author xpenatan
 */
public final class PlatformerGame {
    private static final int MAX_SPRITES = 192;
    private static final int MAX_SOLIDS = 96;
    private static final int MAX_COLLECTIBLES = 32;
    private static final int MAX_HAZARDS = 8;
    private static final int MAX_ENEMIES = 20;
    private static final int MAX_GOALS = 2;
    // Less than 0.001 source pixel: tolerate roundoff on the non-moving axis only.
    private static final float CONTACT_EPSILON = 0.000001f;

    private final PlatformerInput input;
    private final Sprite[] sprites = new Sprite[MAX_SPRITES];
    private final Sprite[] solids = new Sprite[MAX_SOLIDS];
    private final Sprite[] collectibles = new Sprite[MAX_COLLECTIBLES];
    private final Sprite[] hazards = new Sprite[MAX_HAZARDS];
    private final Enemy[] enemies = new Enemy[MAX_ENEMIES];
    private final Sprite[] goals = new Sprite[MAX_GOALS];
    private int spriteCount;
    private int solidCount;
    private int collectibleCount;
    private int hazardCount;
    private int enemyCount;
    private int goalCount;
    private final String[] siteNames = new String[8];
    private final float[] siteX = new float[8], siteY = new float[8];
    private int siteCount, visitedMask, lastSite = -1;
    public enum Scenery { MILL, WINCH, GROVE, PORTAL }
    private final Scenery[] scenery = new Scenery[16];
    private final float[] sceneryX = new float[16], sceneryY = new float[16];
    private int sceneryCount;

    void addScenery(Scenery kind, float x, float y) {
        if (sceneryCount == scenery.length) throw new IllegalArgumentException("Too many scenic installations");
        scenery[sceneryCount] = kind;
        sceneryX[sceneryCount] = x;
        sceneryY[sceneryCount++] = y;
    }

    public int sceneryCount() { return sceneryCount; }

    public Scenery scenery(int i) { return scenery[i]; }

    public float sceneryX(int i) { return sceneryX[i]; }

    public float sceneryY(int i) { return sceneryY[i]; }

    private Sprite player;
    private ClockworkGate gate;

    ClockworkGate puzzle() {
        if (gate == null) gate = new ClockworkGate();
        return gate;
    }

    public ClockworkGate gate() { return gate; }

    public int nearbyDial() {
        return gate != null && playerOnGround && player != null ? gate.nearby(player) : -1;
    }

    private float playerVelocityX;
    private float playerVelocityY;
    private boolean playerOnGround = true;
    private boolean playerFacingRight = true;
    private boolean leftDown;
    private boolean rightDown;
    private boolean jumpDown;
    private boolean jumpPressed;
    private float liftTime, recoilTime, recoilDirection;
    private float previousFeet, jumpBuffer, groundGrace, landingTime, hurtTime;
    private int health = MAX_HEALTH;
    public static final int MAX_HEALTH = 3;
    private boolean dropPressed;
    private boolean restartDown;
    private boolean restartPressed;
    private float cameraX;
    private int coinsCollected;
    private boolean gameOver;
    private boolean completed;
    private boolean restarting;
    private float levelLeft = PlatformerConstants.LEVEL_LEFT, levelRight = PlatformerConstants.LEVEL_RIGHT;
    private float spawnX = PlatformerConstants.PLAYER_START_X, spawnY = PlatformerConstants.PLAYER_START_Y;
    private float cameraMaximum = PlatformerConstants.CAMERA_MAX_X;
    private int events;
    public static final int JUMP = 1, COIN = 2, HURT = 4, COMPLETE = 8, RESTART = 16, STOMP = 32, LAND = 64;
    public static final int TURN = 128;

    /** Returns and clears simulation events since the last call; no callbacks or event objects. */
    public int consumeEvents() { int result = events; events = 0; return result; }
    public boolean playerFacingRight() { return playerFacingRight; }
    /** Configures authored horizontal bounds before play; units match simulation positions. */
    void levelBounds(float left, float right) {
        if (!Float.isFinite(left) || !Float.isFinite(right) || right-left < PlatformerConstants.VIEW_WIDTH) {
            throw new IllegalArgumentException("Level bounds must contain the view");
        }
        levelLeft = left; levelRight = right; cameraMaximum = right - PlatformerConstants.VIEW_RIGHT;
    }

    /**
     * Creates an empty platformer simulation.
     *
     * @param input the input source, or {@code null}
     */
    public PlatformerGame(PlatformerInput input) {
        this.input = input;
    }

    /**
     * Advances input, simulation, interactions, and camera state.
     *
     * @param deltaTime elapsed seconds
     */
    public void update(float deltaTime) {
        updateInput();
        updateRestart();
        float delta = clampedDelta(deltaTime);
        if (!gameOver && !completed) {
            if (gate != null) {
                gate.update(delta);
                int dial = dropPressed ? nearbyDial() : -1;
                if (dial >= 0) {
                    gate.turn(dial);
                    dropPressed = false;
                    events |= TURN;
                }
            }
            updateLifts(delta);
            hurtTime = Math.max(0, hurtTime - delta);
            landingTime = Math.max(0, landingTime - delta);
        }
        updatePlayer(delta);
        updateEnemies(delta);
        updateInteractions();
        if (delta > 0 && player != null && !restarting && !gameOver && !completed) {
            for (int i = 0; i < enemyCount && !gameOver; i++) {
                Enemy enemy = enemies[i];
                if (enemy.attack != null) enemy.attack.update(this, enemy, delta);
            }
        }
        updateCamera(delta);
    }

    /**
     * Returns the number of drawable sprites.
     *
     * @return the sprite count
     */
    public int spriteCount() {
        return spriteCount;
    }

    /**
     * Returns one drawable sprite.
     *
     * @param index the sprite index
     * @return the sprite
     */
    public Sprite spriteAt(int index) {
        if (index < 0 || index >= spriteCount) {
            throw new IndexOutOfBoundsException("Sprite index " + index + " outside [0, " + spriteCount + ")");
        }
        return sprites[index];
    }

    /**
     * Returns the player sprite.
     *
     * @return the player
     */
    public Sprite player() {
        return player;
    }

    /**
     * Returns the player horizontal velocity.
     *
     * @return the horizontal velocity
     */
    public float playerVelocityX() {
        return playerVelocityX;
    }

    /**
     * Returns the player vertical velocity.
     *
     * @return the vertical velocity
     */
    public float playerVelocityY() {
        return playerVelocityY;
    }

    /**
     * Returns whether the player is standing on a solid.
     *
     * @return {@code true} when grounded
     */
    public boolean playerOnGround() {
        return playerOnGround;
    }

    /**
     * Returns the camera horizontal position.
     *
     * @return the camera position
     */
    public float cameraX() {
        return cameraX;
    }

    /**
     * Returns the collected coin value.
     *
     * @return the collected value
     */
    public int coinsCollected() {
        return coinsCollected;
    }

    /**
     * Returns the total collectible value.
     *
     * @return the total value
     */
    public int coinTotal() {
        int total = 0;
        for (int i = 0; i < collectibleCount; i++) {
            total += collectibles[i].value;
        }
        return total;
    }

    /**
     * Returns whether play stopped after a hazard, enemy, or fall.
     *
     * @return {@code true} when game over
     */
    public boolean gameOver() {
        return gameOver;
    }

    /**
     * Returns whether the player reached the goal.
     *
     * @return {@code true} when complete
     */
    public boolean completed() {
        return completed;
    }

    /**
     * Returns the number of solid level sprites.
     *
     * @return the solid count
     */
    public int solidCount() {
        return solidCount;
    }

    /**
     * Returns the number of collectibles.
     *
     * @return the collectible count
     */
    public int collectibleCount() {
        return collectibleCount;
    }

    /**
     * Returns one collectible sprite.
     *
     * @param index the collectible index
     * @return the collectible
     */
    public Sprite collectibleAt(int index) {
        return checkedSprite(collectibles, collectibleCount, index, "Collectible");
    }

    /**
     * Returns the number of hazards.
     *
     * @return the hazard count
     */
    public int hazardCount() {
        return hazardCount;
    }

    /**
     * Returns one hazard sprite.
     *
     * @param index the hazard index
     * @return the hazard
     */
    public Sprite hazardAt(int index) {
        return checkedSprite(hazards, hazardCount, index, "Hazard");
    }

    /**
     * Returns the number of enemies.
     *
     * @return the enemy count
     */
    public int enemyCount() {
        return enemyCount;
    }

    /**
     * Returns one enemy.
     *
     * @param index the enemy index
     * @return the enemy
     */
    public Enemy enemyAt(int index) {
        if (index < 0 || index >= enemyCount) {
            throw new IndexOutOfBoundsException("Enemy index " + index + " outside [0, " + enemyCount + ")");
        }
        return enemies[index];
    }

    /**
     * Returns one goal sprite.
     *
     * @param index the goal index
     * @return the goal
     */
    public Sprite goalAt(int index) {
        return checkedSprite(goals, goalCount, index, "Goal");
    }
    public int goalCount() { return goalCount; }

    Sprite addSprite(float x, float y, float halfWidth, float halfHeight) {
        if (spriteCount >= sprites.length) {
            throw new IllegalStateException("Platformer sprite capacity exceeded");
        }
        Sprite sprite = new Sprite(x, y, halfWidth, halfHeight);
        sprites[spriteCount++] = sprite;
        return sprite;
    }

    void addSolid(Sprite sprite) {
        if (solidCount >= solids.length) {
            throw new IllegalStateException("Platformer solid capacity exceeded");
        }
        solids[solidCount++] = sprite;
    }

    void addCollectible(Sprite sprite, int value) {
        if (collectibleCount >= collectibles.length) {
            throw new IllegalStateException("Platformer collectible capacity exceeded");
        }
        sprite.value = value;
        collectibles[collectibleCount++] = sprite;
    }

    void addHazard(Sprite sprite) {
        if (hazardCount >= hazards.length) {
            throw new IllegalStateException("Platformer hazard capacity exceeded");
        }
        hazards[hazardCount++] = sprite;
    }

    void addEnemy(Sprite sprite, float minX, float maxX, float speed) {
        addEnemy(sprite, minX, maxX, speed, EnemyKind.CRAWLER);
    }

    void addEnemy(Sprite sprite, float minX, float maxX, float speed, EnemyKind kind) {
        addEnemy(sprite, minX, maxX, speed, kind, null);
    }

    void addEnemy(Sprite sprite, float minX, float maxX, float speed, EnemyKind kind, RangedAttack.Kind shot) {
        if (enemyCount >= enemies.length) {
            throw new IllegalStateException("Platformer enemy capacity exceeded");
        }
        enemies[enemyCount++] = new Enemy(sprite, minX, maxX, speed, kind, shot);
    }

    void addGoal(Sprite sprite) {
        if (goalCount >= goals.length) {
            throw new IllegalStateException("Platformer goal capacity exceeded");
        }
        goals[goalCount++] = sprite;
    }

    void addSite(String name, float x, float y) {
        if (name.isBlank() || siteCount == siteNames.length) throw new IllegalArgumentException("Invalid trail site");
        siteNames[siteCount] = name;
        siteX[siteCount] = x;
        siteY[siteCount++] = y;
    }

    public int siteCount() { return siteCount; }

    public int visitedCount() { return Integer.bitCount(visitedMask); }

    public String siteName(int index) { return siteNames[index]; }

    public float siteX(int index) { return siteX[index]; }

    public float siteY(int index) { return siteY[index]; }

    public boolean visited(int index) { return (visitedMask & (1 << index)) != 0; }

    public int lastSite() { return lastSite; }

    public Sprite solidAt(int index) { return checkedSprite(solids, solidCount, index, "Surface"); }

    void player(Sprite sprite) {
        player = sprite;
        spawnX = sprite.x; spawnY = sprite.y;
    }

    private void updateInput() {
        boolean nextLeftDown = input != null && input.leftDown();
        boolean nextRightDown = input != null && input.rightDown();
        boolean nextJumpDown = input != null && input.jumpDown();
        boolean nextRestartDown = input != null && input.restartDown();
        leftDown = nextLeftDown;
        rightDown = nextRightDown;
        jumpPressed = input != null && input.consumeJumpPress() || nextJumpDown && !jumpDown;
        restartPressed = input != null && input.consumeRestartPress() || nextRestartDown && !restartDown;
        jumpDown = nextJumpDown;
        dropPressed = input != null && input.consumeDropPress();
        restartDown = nextRestartDown;
    }

    private void updateRestart() {
        if (restarting) {
            restarting = false;
            return;
        }
        if ((!gameOver && !completed) || !restartPressed) {
            return;
        }
        restart();
    }

    /** Restarts at the authored spawn and restores collectibles/enemies; input bindings remain owned by the caller. */
    public void restart() {
        if (player == null) { return; }
        cameraX = 0.0f;
        coinsCollected = 0;
        visitedMask = 0;
        if (gate != null) gate.reset();
        lastSite = -1;
        liftTime = 0;
        for (int i = 0; i < solidCount; i++) {
            if (solids[i].lift) solids[i].y = solids[i].previousY = solids[i].restY;
        }
        respawn(spawnX, spawnY);
        for (int i = 0; i < collectibleCount; i++) {
            collectibles[i].collected = false;
        }
        for (int i = 0; i < enemyCount; i++) {
            enemies[i].reset();
        }
        events |= RESTART;
    }

    public int health() { return health; }

    public float hurtTime() { return hurtTime; }

    public float landingTime() { return landingTime; }

    /** Re-enter at a lit lantern or beyond the unlocked gate, retaining discoveries, gears and puzzle state. */
    public void retry() {
        boolean useGate = gate != null && gate.solved() && (lastSite < 0 || siteX[lastSite] <= gate.x());
        if (useGate) {
            respawn(gate.x() + ClockworkGate.HALF_WIDTH + player.halfWidth + .01f, gate.y() + player.halfHeight);
        } else if (lastSite >= 0) {
            respawn(siteX[lastSite], siteY[lastSite]);
        } else {
            restart();
            return;
        }
        cameraX = clamp(player.x - .25f, PlatformerConstants.CAMERA_MIN_X, cameraMaximum);
        hurtTime = 1.2f;
        events = RESTART;
    }

    private void respawn(float x, float y) {
        for (int i = 0; i < enemyCount; i++) {
            if (enemies[i].attack != null) enemies[i].attack.reset();
        }
        gameOver = completed = false;
        restarting = true;
        player.position(x, y);
        playerVelocityX = playerVelocityY = 0;
        playerOnGround = playerFacingRight = true;
        jumpBuffer = groundGrace = landingTime = hurtTime = recoilTime = 0;
        health = MAX_HEALTH;
    }

    private void updatePlayer(float delta) {
        if (player == null || restarting) {
            return;
        }
        if (gameOver || completed) {
            playerVelocityX = 0.0f;
            return;
        }
        float horizontal = 0.0f;
        if (leftDown) {
            horizontal -= 1.0f;
        }
        if (rightDown) {
            horizontal += 1.0f;
        }
        recoilTime = Math.max(0, recoilTime - delta);
        playerVelocityX = recoilTime > 0 ? recoilDirection * .63f : horizontal * PlatformerConstants.PLAYER_MOVE_SPEED;
        if (horizontal < 0.0f) {
            playerFacingRight = false;
        } else if (horizontal > 0.0f) {
            playerFacingRight = true;
        }
        boolean groundedBefore = playerOnGround;
        jumpBuffer = jumpPressed ? .095f : Math.max(0, jumpBuffer - delta);
        groundGrace = playerOnGround ? .075f : Math.max(0, groundGrace - delta);
        if (jumpBuffer > 0 && groundGrace > 0) {
            jumpBuffer = groundGrace = 0;
            playerVelocityY = PlatformerConstants.JUMP_VELOCITY;
            playerOnGround = false;
            events |= JUMP;
        }
        if (delta <= 0.0f) {
            return;
        }
        playerVelocityY += PlatformerConstants.GRAVITY * delta;
        if (playerVelocityY < PlatformerConstants.TERMINAL_VELOCITY) {
            playerVelocityY = PlatformerConstants.TERMINAL_VELOCITY;
        }

        player.x += playerVelocityX * delta;
        resolveHorizontal();
        player.x = clamp(player.x, levelLeft + player.halfWidth, levelRight - player.halfWidth);

        previousFeet = player.y - player.halfHeight;
        player.y += playerVelocityY * delta;
        playerOnGround = false;
        float impactVelocity = playerVelocityY;
        resolveVertical(previousFeet);
        if (playerOnGround && !groundedBefore && impactVelocity < -.5f) {
            landingTime = .09f;
            events |= LAND;
        }
        if (player.y < PlatformerConstants.FALL_Y) {
            gameOver = true;
            events |= HURT;
            playerVelocityY = 0.0f;
        }
    }

    private void resolveHorizontal() {
        if (playerVelocityX == 0.0f) {
            return;
        }
        boolean movingRight = playerVelocityX > 0.0f;
        if (gate != null && gate.opening() < 1 && Math.abs(player.x - gate.x()) < player.halfWidth + ClockworkGate.HALF_WIDTH
                && player.y + player.halfHeight > gate.y() + gate.opening() * ClockworkGate.HEIGHT
                && player.y - player.halfHeight < gate.y() + ClockworkGate.HEIGHT) {
            float clearance = player.halfWidth + ClockworkGate.HALF_WIDTH;
            player.x = gate.x() + (movingRight ? -clearance : clearance);
            playerVelocityX = 0;
        }
        for (int i = 0; i < solidCount; i++) {
            Sprite solid = solids[i];
            if (solid.bridge || !overlaps(player, solid)
                    || player.y - player.halfHeight >= solid.y + solid.halfHeight - CONTACT_EPSILON
                    || player.y + player.halfHeight <= solid.y - solid.halfHeight + CONTACT_EPSILON) {
                continue;
            }
            if (movingRight) {
                player.x = solid.x - solid.halfWidth - player.halfWidth;
            } else {
                player.x = solid.x + solid.halfWidth + player.halfWidth;
            }
            playerVelocityX = 0.0f;
        }
    }

    private void resolveVertical(float previousFeet) {
        boolean falling = playerVelocityY <= 0.0f;
        for (int i = 0; i < solidCount; i++) {
            Sprite solid = solids[i];
            float entryTop = (solid.lift ? Math.min(solid.previousY, solid.y) : solid.y) + solid.halfHeight;
            if (solid.bridge && (!falling || previousFeet < entryTop - CONTACT_EPSILON || dropPressed)) {
                continue;
            }
            if (!overlaps(player, solid)
                    || player.x - player.halfWidth >= solid.x + solid.halfWidth - CONTACT_EPSILON
                    || player.x + player.halfWidth <= solid.x - solid.halfWidth + CONTACT_EPSILON) {
                continue;
            }
            if (falling) {
                player.y = solid.y + solid.halfHeight + player.halfHeight;
                playerOnGround = true;
            } else {
                player.y = solid.y - solid.halfHeight - player.halfHeight;
            }
            playerVelocityY = 0.0f;
        }
    }

    private void updateLifts(float delta) {
        liftTime += delta;
        for (int i = 0; i < solidCount; i++) {
            Sprite lift = solids[i];
            if (!lift.lift) continue;
            float top = lift.y + lift.halfHeight;
            boolean riding = player != null && playerOnGround
                    && Math.abs(player.y - player.halfHeight - top) < CONTACT_EPSILON
                    && Math.abs(player.x - lift.x) < player.halfWidth + lift.halfWidth - CONTACT_EPSILON;
            lift.previousY = lift.y;
            lift.y = lift.restY + .13f * (float)Math.sin(liftTime * 1.85f);
            if (riding) player.y += lift.y - lift.previousY;
        }
    }

    private void updateEnemies(float delta) {
        if (gameOver || completed || restarting) {
            return;
        }
        for (int i = 0; i < enemyCount; i++) {
            Enemy enemy = enemies[i];
            enemy.previousTop = enemy.sprite.y + enemy.sprite.halfHeight;
            enemy.age += delta;
            if (enemy.defeated) continue;
            float travel = delta;
            if (enemy.kind == EnemyKind.MOTH) {
                enemy.sprite.y = enemy.startY + .095f * (float) Math.sin(enemy.age * 3.2f);
            } else if (enemy.kind == EnemyKind.HOPPER) {
                float phase = enemy.age % 1.45f;
                float flight = Math.min(1, phase / .72f);
                enemy.sprite.y = enemy.startY + .23f * 4 * flight * (1 - flight);
                if (phase >= .72f) travel = 0;
            }
            enemy.sprite.x += enemy.speed * enemy.direction * travel;
            if (enemy.sprite.x < enemy.minX) {
                enemy.sprite.x = enemy.minX;
                enemy.direction = 1;
            } else if (enemy.sprite.x > enemy.maxX) {
                enemy.sprite.x = enemy.maxX;
                enemy.direction = -1;
            }
        }
    }

    private void updateInteractions() {
        if (player == null || gameOver || completed || restarting) {
            return;
        }
        for (int i = 0; i < collectibleCount; i++) {
            Sprite collectible = collectibles[i];
            if (!collectible.collected && overlaps(player, collectible)) {
                collectible.collected = true;
                coinsCollected += collectible.value;
                events |= COIN;
            }
        }
        for (int i = 0; i < hazardCount; i++) {
            if (overlaps(player, hazards[i])) {
                gameOver = true;
                events |= HURT;
                return;
            }
        }
        for (int i = 0; i < enemyCount; i++) {
            Enemy enemy = enemies[i];
            if (enemy.defeated || !overlaps(player, enemy.sprite)) continue;
            if (!playerOnGround && playerVelocityY < 0 && previousFeet >= enemy.previousTop - .008f) {
                enemy.defeated = true;
                enemy.age = 0;
                player.y = enemy.sprite.y + enemy.sprite.halfHeight + player.halfHeight;
                playerVelocityY = PlatformerConstants.JUMP_VELOCITY * .80f;
                groundGrace = jumpBuffer = 0;
                events |= STOMP;
            } else {
                hurtPlayer(player.x < enemy.sprite.x ? -1 : 1);
                if (gameOver) return;
            }
        }
        for (int i = 0; i < siteCount; i++) {
            if (!visited(i) && Math.abs(player.x - siteX[i]) < .14f && Math.abs(player.y - siteY[i]) < .115f) {
                visitedMask |= 1 << i;
                lastSite = i;
                health = Math.min(MAX_HEALTH, health + 1);
            }
        }
        for (int i = 0; i < goalCount; i++) {
            if (playerOnGround && (gate == null || gate.solved()) && overlaps(player, goals[i])) {
                completed = true;
                events |= COMPLETE;
                return;
            }
        }
    }

    private void updateCamera(float delta) {
        if (player == null) {
            return;
        }
        float target = clamp(player.x - 0.25f, PlatformerConstants.CAMERA_MIN_X,
                cameraMaximum);
        float alpha = Math.min(1.0f, delta * PlatformerConstants.CAMERA_FOLLOW_SPEED);
        cameraX += (target - cameraX) * alpha;
    }

    private static Sprite checkedSprite(Sprite[] values, int size, int index, String label) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(label + " index " + index + " outside [0, " + size + ")");
        }
        return values[index];
    }

    private static boolean overlaps(Sprite first, Sprite second) {
        return Math.abs(first.x - second.x) < first.halfWidth + second.halfWidth
                && Math.abs(first.y - second.y) < first.halfHeight + second.halfHeight;
    }

    private static float clampedDelta(float deltaTime) {
        if (deltaTime <= 0.0f || !Float.isFinite(deltaTime)) {
            return 0.0f;
        }
        return Math.min(deltaTime, PlatformerConstants.MAX_DELTA);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Mutable level sprite allocated once during level construction.
     */
    public static final class Sprite {
        private float x;
        private float y;
        private final float halfWidth;
        private final float halfHeight;
        private int value;
        private boolean collected;
        boolean bridge, lift;
        private final float restY;
        private float previousY;

        public boolean isLift() { return lift; }

        public float restY() { return restY; }


        public boolean isBridge() { return bridge; }


        private Sprite(float x, float y, float halfWidth, float halfHeight) {
            this.x = x;
            this.restY = this.previousY = this.y = y;
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float halfWidth() {
            return halfWidth;
        }

        public float halfHeight() {
            return halfHeight;
        }

        public boolean collected() {
            return collected;
        }

        /**
         * Repositions the sprite. This is used by the simulation tests and can
         * also be useful for sample experimentation.
         *
         * @param x the new x coordinate
         * @param y the new y coordinate
         */
        public void position(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    void hurtPlayer(float direction) {
        if (hurtTime > 0) return;
        health--;
        recoilDirection = direction;
        recoilTime = .16f;
        playerVelocityY = .73f;
        playerOnGround = false;
        jumpBuffer = groundGrace = 0;
        hurtTime = 1.15f;
        events |= HURT;
        if (health == 0) gameOver = true;
    }

    public enum EnemyKind { CRAWLER, MOTH, HOPPER }

    /**
     * Enemy patrol data allocated once during level construction.
     */
    public static final class Enemy {
        private final Sprite sprite;
        private final float startX;
        private final float startY;
        private final float minX;
        private final float maxX;
        private final float speed;
        private int direction = -1;
        private final EnemyKind kind;
        private final RangedAttack attack;
        private float age, previousTop;
        private boolean defeated;

        public EnemyKind kind() { return kind; }

        public RangedAttack attack() { return attack; }

        public float age() { return age; }

        public boolean defeated() { return defeated; }

        private Enemy(Sprite sprite, float minX, float maxX, float speed, EnemyKind kind, RangedAttack.Kind shot) {
            this.kind = kind;
            attack = shot == null ? null : new RangedAttack(shot);
            this.previousTop = sprite.y + sprite.halfHeight;
            this.sprite = sprite;
            this.startX = sprite.x;
            this.startY = sprite.y;
            this.minX = minX;
            this.maxX = maxX;
            this.speed = speed;
        }

        public Sprite sprite() {
            return sprite;
        }

        public boolean movingLeft() { return direction < 0; }

        public float startX() {
            return startX;
        }

        private void reset() {
            sprite.x = startX;
            sprite.y = startY;
            direction = -1;
            age = 0;
            defeated = false;
            previousTop = startY + sprite.halfHeight;
        }
    }
}
