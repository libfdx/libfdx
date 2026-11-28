package io.github.libfdx.samples.g2d.platformer;

/** One authored shooter's wind-up and reusable projectile, in simulation units. */
public final class RangedAttack {
    public enum Kind {
        SEED(.78f, 0, 0), SHARD(1.02f, 0, 0), SPORE(.55f, 0, 0),
        SNOWBALL(.78f, .36f, -.85f), EMBER(.92f, .22f, -.48f), PEARL(.70f, .15f, -.70f);

        final float speed, lift, gravity;

        Kind(float speed, float lift, float gravity) {
            this.speed = speed;
            this.lift = lift;
            this.gravity = gravity;
        }

    }

    private static final float WARNING = .62f;
    private static final float RADIUS = 3 / PlatformerLevel.PIXELS_PER_UNIT;
    private final Kind kind;
    private float cooldown, windup, direction, x, y, launchY, velocityY, flightTime;
    private boolean flying, bounced;

    RangedAttack(Kind kind) {
        this.kind = kind;
        reset();
    }

    public Kind kind() { return kind; }

    public boolean flying() { return flying; }

    public float charge() { return windup > 0 ? 1 - windup / WARNING : 0; }

    public boolean facesLeft() { return direction < 0; }

    public float x() { return x; }

    public float y() { return y; }

    void reset() {
        windup = 0;
        cooldown = .8f;
        flying = false;
    }

    void update(PlatformerGame game, PlatformerGame.Enemy enemy, float delta) {
        if (flying) {
            advance(game, delta);
            return;
        }
        if (enemy.defeated()) {
            windup = 0;
            return;
        }
        var body = enemy.sprite();
        if (windup > 0) {
            x = body.x() + direction * (body.halfWidth() + RADIUS);
            y = body.y() + RADIUS;
            windup = Math.max(0, windup - delta);
            if (windup == 0) {
                launchY = y;
                velocityY = kind.lift;
                flightTime = 0;
                bounced = false;
                flying = true;
                cooldown = 2.2f;
            }
            return;
        }
        cooldown = Math.max(0, cooldown - delta);
        if (cooldown > 0) return;
        float dx = game.player().x() - body.x();
        if (Math.abs(dx) < .32f || Math.abs(dx) > .96f || Math.abs(game.player().y() - body.y()) > .19f) return;
        direction = Math.signum(dx);
        windup = WARNING;
        x = body.x() + direction * (body.halfWidth() + RADIUS);
        y = body.y() + RADIUS;
    }

    private void advance(PlatformerGame game, float delta) {
        // Small steps keep even the fastest shard from passing through a deck or a player.
        int steps = Math.max(1, (int)Math.ceil(delta * kind.speed / RADIUS));
        float dt = delta / steps;
        for (int i = 0; i < steps && flying; i++) {
            float lastY = y;
            flightTime += dt;
            x += direction * kind.speed * dt;
            velocityY += kind.gravity * dt;
            y = kind == Kind.SPORE ? launchY + .055f * (float)Math.sin(flightTime * 6) : y + velocityY * dt;
            if (flightTime >= 1.75f || y < PlatformerConstants.FALL_Y) {
                flying = false;
                return;
            }
            for (int j = 0; j < game.solidCount(); j++) {
                var solid = game.solidAt(j);
                if (!touches(solid)) continue;
                float top = solid.y() + solid.halfHeight();
                if (kind == Kind.PEARL && !bounced && velocityY < 0 && lastY - RADIUS >= top - .00001f) {
                    y = top + RADIUS;
                    velocityY = .31f;
                    bounced = true;
                    break;
                }
                flying = false;
                return;
            }
            var gate = game.gate();
            if (gate != null && gate.opening() < 1 && Math.abs(x - gate.x()) < ClockworkGate.HALF_WIDTH + RADIUS
                    && y + RADIUS > gate.y() + ClockworkGate.HEIGHT * gate.opening()
                    && y - RADIUS < gate.y() + ClockworkGate.HEIGHT) {
                flying = false;
                return;
            }
            if (touches(game.player())) {
                flying = false;
                game.hurtPlayer(direction);
            }
        }
    }

    private boolean touches(PlatformerGame.Sprite sprite) {
        return Math.abs(x - sprite.x()) < sprite.halfWidth() + RADIUS
                && Math.abs(y - sprite.y()) < sprite.halfHeight() + RADIUS;
    }

}
