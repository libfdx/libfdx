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
    private static final int MAX_COLLECTIBLES = 16;
    private static final int MAX_HAZARDS = 8;
    private static final int MAX_ENEMIES = 8;
    private static final int MAX_GOALS = 2;

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
    private Sprite player;
    private float playerVelocityX;
    private float playerVelocityY;
    private boolean playerOnGround = true;
    private boolean playerFacingRight = true;
    private boolean leftDown;
    private boolean rightDown;
    private boolean jumpDown;
    private boolean jumpPressed;
    private boolean restartDown;
    private boolean restartPressed;
    private float cameraX;
    private int coinsCollected;
    private boolean gameOver;
    private boolean completed;
    private boolean restarting;

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
        updatePlayer(delta);
        updateEnemies(delta);
        updateInteractions();
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

    Sprite addSprite(float x, float y, float halfWidth, float halfHeight, int regionId, int layer,
            float parallax) {
        if (spriteCount >= sprites.length) {
            throw new IllegalStateException("Platformer sprite capacity exceeded");
        }
        Sprite sprite = new Sprite(x, y, halfWidth, halfHeight, regionId, layer, parallax);
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
        if (enemyCount >= enemies.length) {
            throw new IllegalStateException("Platformer enemy capacity exceeded");
        }
        enemies[enemyCount++] = new Enemy(sprite, minX, maxX, speed);
    }

    void addGoal(Sprite sprite) {
        if (goalCount >= goals.length) {
            throw new IllegalStateException("Platformer goal capacity exceeded");
        }
        goals[goalCount++] = sprite;
    }

    void player(Sprite sprite) {
        player = sprite;
    }

    private void updateInput() {
        boolean nextLeftDown = input != null && input.leftDown();
        boolean nextRightDown = input != null && input.rightDown();
        boolean nextJumpDown = input != null && input.jumpDown();
        boolean nextRestartDown = input != null && input.restartDown();
        leftDown = nextLeftDown;
        rightDown = nextRightDown;
        jumpPressed = nextJumpDown && !jumpDown;
        restartPressed = nextRestartDown && !restartDown;
        jumpDown = nextJumpDown;
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
        cameraX = 0.0f;
        coinsCollected = 0;
        gameOver = false;
        completed = false;
        restarting = true;
        player.x = PlatformerConstants.PLAYER_START_X;
        player.y = PlatformerConstants.PLAYER_START_Y;
        playerVelocityX = 0.0f;
        playerVelocityY = 0.0f;
        playerOnGround = true;
        playerFacingRight = true;
        for (int i = 0; i < collectibleCount; i++) {
            collectibles[i].collected = false;
        }
        for (int i = 0; i < enemyCount; i++) {
            enemies[i].reset();
        }
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
        playerVelocityX = horizontal * PlatformerConstants.PLAYER_MOVE_SPEED;
        if (horizontal < 0.0f) {
            playerFacingRight = false;
        } else if (horizontal > 0.0f) {
            playerFacingRight = true;
        }
        if (jumpPressed && playerOnGround) {
            playerVelocityY = PlatformerConstants.JUMP_VELOCITY;
            playerOnGround = false;
        }
        if (delta <= 0.0f) {
            updatePlayerRegion();
            return;
        }
        playerVelocityY += PlatformerConstants.GRAVITY * delta;
        if (playerVelocityY < PlatformerConstants.TERMINAL_VELOCITY) {
            playerVelocityY = PlatformerConstants.TERMINAL_VELOCITY;
        }

        player.x += playerVelocityX * delta;
        resolveHorizontal();
        player.x = clamp(player.x, PlatformerConstants.LEVEL_LEFT + player.halfWidth,
                PlatformerConstants.LEVEL_RIGHT - player.halfWidth);

        player.y += playerVelocityY * delta;
        playerOnGround = false;
        resolveVertical();
        if (player.y < PlatformerConstants.FALL_Y) {
            gameOver = true;
            playerVelocityY = 0.0f;
        }
        updatePlayerRegion();
    }

    private void resolveHorizontal() {
        if (playerVelocityX == 0.0f) {
            return;
        }
        for (int i = 0; i < solidCount; i++) {
            Sprite solid = solids[i];
            if (!overlaps(player, solid)) {
                continue;
            }
            if (playerVelocityX > 0.0f) {
                player.x = solid.x - solid.halfWidth - player.halfWidth;
            } else {
                player.x = solid.x + solid.halfWidth + player.halfWidth;
            }
            playerVelocityX = 0.0f;
        }
    }

    private void resolveVertical() {
        for (int i = 0; i < solidCount; i++) {
            Sprite solid = solids[i];
            if (!overlaps(player, solid)) {
                continue;
            }
            if (playerVelocityY <= 0.0f) {
                player.y = solid.y + solid.halfHeight + player.halfHeight;
                playerOnGround = true;
            } else {
                player.y = solid.y - solid.halfHeight - player.halfHeight;
            }
            playerVelocityY = 0.0f;
        }
    }

    private void updateEnemies(float delta) {
        if (gameOver || completed || restarting) {
            return;
        }
        for (int i = 0; i < enemyCount; i++) {
            Enemy enemy = enemies[i];
            enemy.sprite.x += enemy.speed * enemy.direction * delta;
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
            }
        }
        for (int i = 0; i < hazardCount; i++) {
            if (overlaps(player, hazards[i])) {
                gameOver = true;
                return;
            }
        }
        for (int i = 0; i < enemyCount; i++) {
            if (overlaps(player, enemies[i].sprite)) {
                gameOver = true;
                return;
            }
        }
        for (int i = 0; i < goalCount; i++) {
            if (overlaps(player, goals[i])) {
                completed = true;
                return;
            }
        }
    }

    private void updateCamera(float delta) {
        if (player == null) {
            return;
        }
        float target = clamp(player.x - 0.25f, PlatformerConstants.CAMERA_MIN_X,
                PlatformerConstants.CAMERA_MAX_X);
        float alpha = Math.min(1.0f, delta * PlatformerConstants.CAMERA_FOLLOW_SPEED);
        cameraX += (target - cameraX) * alpha;
    }

    private void updatePlayerRegion() {
        player.regionId = playerOnGround && Math.abs(playerVelocityX) > 0.001f
                ? PlatformerConstants.REGION_PLAYER_WALK
                : PlatformerConstants.REGION_PLAYER_IDLE;
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
        private int regionId;
        private final int layer;
        private final float parallax;
        private int value;
        private boolean collected;

        private Sprite(float x, float y, float halfWidth, float halfHeight, int regionId, int layer,
                float parallax) {
            this.x = x;
            this.y = y;
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
            this.regionId = regionId;
            this.layer = layer;
            this.parallax = parallax;
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

        public int regionId() {
            return regionId;
        }

        public int layer() {
            return layer;
        }

        public float parallax() {
            return parallax;
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

        private Enemy(Sprite sprite, float minX, float maxX, float speed) {
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

        public float startX() {
            return startX;
        }

        private void reset() {
            sprite.x = startX;
            sprite.y = startY;
            direction = -1;
        }
    }
}
