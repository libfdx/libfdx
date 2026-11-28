package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.samples.g2d.platformer.*;
import static io.github.libfdx.samples.g2d.platformer.PlatformerLevel.pixel;

/** Side-on world rendering. Positions and machinery are anchored to the same surfaces used by physics. */
public final class AuthoredPlatformerRenderer {
    static final int HOUSE_SITE_INDEX = 3;
    static final int HOUSE_SIZE = 80;
    private static final int HOUSE_LEFT = -8;
    private final SpriteBatch batch;
    private final PlatformerView view;
    private final PlatformerGround ground;
    private final RenderPassDescriptor descriptor = new RenderPassDescriptor().label("Ridgeline");
    private static final LoadOp CLEAR = LoadOp.clear(.09f, .16f, .21f, 1);
    private final PlatformerParticles particles = new PlatformerParticles();
    private TextureRegion[] props;
    private TextureRegion wheel, cog;
    private float left, bottom, unitX, unitY;

    public AuthoredPlatformerRenderer(SpriteBatch batch, PlatformerView view) {
        this.batch = batch;
        this.view = view;
        ground = new PlatformerGround(batch);
    }

    public void effects(PlatformerGame game, int events) {
        particles.step(game, events);
    }

    public void clearEffects() {
        particles.clear();
    }

    public void render(GraphicsFrame frame, PlatformerLevelAsset level, PlatformerGame game, double time, double arrival) {
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor.colorAttachment(frame.colorAttachment())
                .colorLoadOp(CLEAR).colorStoreOp(StoreOp.store()));
        try {
            if (level == null || game == null) return;
            if (props != level.props()) {
                props = level.props();
                wheel = insetForRotation(props[0]);
                cog = insetForRotation(props[1]);
            }
            float limit = Math.max(0, level.map().worldWidth() - view.width());
            left = Math.max(0, Math.min(limit, game.cameraX() * PlatformerLevel.PIXELS_PER_UNIT + 187.5f - view.width() * .38f));
            bottom = -24 + Math.max(0, pixel(game.player().y()) - 162) * .45f - Math.max(0, view.height() - 264) * .42f;
            unitX = 2 / view.width();
            unitY = 2 / view.height();
            float t = (float)(time + arrival);
            batch.viewport(frame.width(), frame.height());
            batch.begin(pass);
            try {
                white();
                backdrop(level, limit);
                scenery(game, t);
                surfaces(level, game, t);
                places(level, game, t);
                machinery(level, game.gate());
                destination(level, game.goalAt(0), (float)arrival);
                encounters(level, game, t);
                traveler(level, game, t);
                particles.draw(this, level.actors());
                weather(level, t);
                white();
            } finally {
                batch.end();
            }
        } finally {
            pass.end();
        }
    }

    private void backdrop(PlatformerLevelAsset level, float limit) {
        Texture sky = level.panorama();
        float w = Math.max(view.width() * 1.13f, view.height() * sky.width() / sky.height());
        float h = w * sky.height() / sky.width();
        float pan = limit > 0 ? left / limit : 0;
        draw(sky, left - (w - view.width()) * pan, bottom - (h - view.height()) * .4f, w, h);
    }

    private void scenery(PlatformerGame game, float t) {
        for (int i = 0; i < game.sceneryCount(); i++) {
            float x = pixel(game.sceneryX(i)), y = pixel(game.sceneryY(i));
            if (!visible(x - 90, 180)) continue;
            batch.color(.78f, .84f, .82f, 1);
            switch (game.scenery(i)) {
                case MILL -> {
                    prop(2, x - 35, y - 1, 45, 42, false);
                    rotate(wheel, x + 16, y + 16, 32, -t * 26);
                    rotate(cog, x + 6, y + 34, 10, t * 84);
                }
                case WINCH -> {
                    member(props[4], x - 16, y, x - 3, y + 31, 4);
                    member(props[4], x + 10, y, x - 3, y + 31, 4);
                    prop(5, x - 5, y + 27, 23, 4, false);
                    prop(5, x + 14, y + 13, 1.5f, 15, false);
                    prop(7, x + 10, y + 6, 9, 10, false);
                    rotate(cog, x - 3, y + 28, 16, t * 30);
                }
                case GROVE -> {
                    prop(10, x - 28, y - 1, 35, 63, false);
                    prop(10, x, y - 1, 23, 40, true);
                }
                case PORTAL -> prop(3, x - 26, y - 1, 52, 43, false);
            }
        }
        white();
    }

    private void surfaces(PlatformerLevelAsset level, PlatformerGame game, float t) {
        ground.prepare(game);
        // Draw all trestles behind the cliffs so their foundations disappear into the ground.
        for (int i = 0; i < game.solidCount(); i++) {
            var s = game.solidAt(i);
            if (!s.isBridge()) continue;
            float w = s.halfWidth() * 300, x = pixel(s.x()) - w / 2, top = pixel(s.y()) + s.halfHeight() * 150;
            if (!visible(x - 12, w + 24)) continue;
            if (s.isLift()) lift(x, top, w, pixel(s.restY()) + s.halfHeight() * 150, t);
            else trestle(level.trail(), x, top, w);
        }
        ground.draw(level.ground(), left, bottom, view.width(), view.height());
        for (int i = 0; i < game.solidCount(); i++) {
            var s = game.solidAt(i);
            if (s.isBridge()) continue;
            float w = s.halfWidth() * 300, x = pixel(s.x()) - w / 2, top = pixel(s.y()) + s.halfHeight() * 150;
            if (!visible(x, w)) continue;
            boolean flip = (i & 1) != 0;
            int patches = Math.max(1, Math.round(w / 113));
            for (int j = 0; j < patches; j++) {
                float tileWidth = w / patches, tx = x + j * tileWidth;
                int detail = (j + i) % 3;
                float width = detail == 0 ? 17 : 16 + detail * 3;
                float desired = tx + tileWidth * (detail == 0 ? .77f : .17f);
                float fitted = PlatformerGround.fitLeft(desired, width,
                        x + PlatformerGround.EDGE_INSET, x + w - PlatformerGround.EDGE_INSET);
                if (!Float.isNaN(fitted) && clearForPlants(game, fitted, width)) prop(detail == 0 ? 9 : 8, fitted, top - 1, width, 10 + detail, flip);
            }
        }
        white();
    }

    // Leave a clear silhouette around structures, trail markers and hazards on the ground.
    private boolean clearForPlants(PlatformerGame game, float x, float w) {
        for (int i = 0; i < game.solidCount(); i++) {
            var s = game.solidAt(i);
            if (!s.isBridge()) continue;
            float half = s.halfWidth() * PlatformerLevel.PIXELS_PER_UNIT + (s.isLift() ? 18 : 2);
            if (overlaps(x, w, pixel(s.x()) - half, pixel(s.x()) + half)) return false;
        }
        for (int i = 0; i < game.sceneryCount(); i++) {
            float center = pixel(game.sceneryX(i));
            if (overlaps(x, w, center - 38, center + 36)) return false;
        }
        for (int i = 0; i < game.siteCount(); i++) {
            float center = pixel(game.siteX(i));
            if (overlaps(x, w, center - 33, center + siteRight(i) + 3)) return false;
        }
        for (int i = 0; i < game.hazardCount(); i++) {
            float center = pixel(game.hazardAt(i).x());
            if (overlaps(x, w, center - 12, center + 12)) return false;
        }
        var gate = game.gate();
        if (gate != null) {
            if (overlaps(x, w, pixel(gate.x()) - 24, pixel(gate.x()) + 24)) return false;
            for (int i = 0; i < 3; i++) {
                float center = pixel(gate.dialX(i));
                if (overlaps(x, w, center - 16, center + 16)) return false;
            }
        }
        float goal = pixel(game.goalAt(0).x());
        return !overlaps(x, w, goal - 75, goal + 75);
    }

    private static boolean overlaps(float x, float width, float left, float right) {
        return x < right && x + width > left;
    }

    private void trestle(Trail trail, float x, float top, float w) {
        boolean iron = trail == Trail.QUARRY || trail == Trail.FORGE;
        boolean roots = trail == Trail.HOLLOW;
        boolean stone = trail == Trail.SUMMIT;
        int deck = iron ? 12 : roots ? 15 : stone ? 18 : 4;
        int post = iron ? 13 : roots ? 16 : stone ? 19 : 5;
        float width = roots ? 26 : stone ? 11 : iron ? 9 : 7;
        float first = x + 3 + width / 2, last = x + w - 3 - width / 2;
        int bays = w >= 112 ? 2 : 1;
        float previous = Float.NaN, previousFoot = 0;
        batch.color(.78f, .83f, .83f, 1);
        for (int i = 0; i <= bays; i++) {
            float center = first + (last - first) * i / bays;
            float foot = ground.pierTop(center - width / 2, width, top - 5);
            if (Float.isNaN(foot)) continue;
            prop(post, center - width / 2, foot - 2, width, top - foot - 2, false);
            if (!Float.isNaN(previous) && !stone && !roots) {
                float lowerJoint = Math.max(previousFoot + 4, top - 23);
                member(props[deck], previous, lowerJoint, center, top - 5, 3);
            }
            previous = center;
            previousFoot = foot;
        }
        white();
        int boards = Math.max(1, Math.round(w / 62));
        for (int i = 0; i < boards; i++) {
            float start = x + i * w / boards - (i > 0 ? 2 : 0);
            float end = x + (i + 1) * w / boards + (i + 1 < boards ? 2 : 0);
            prop(deck, start, top - 6, end - start, 6, false);
        }
    }

    private void member(TextureRegion region, float x1, float y1, float x2, float y2, float thickness) {
        float dx = x2 - x1, dy = y2 - y1;
        float height = thickness * unitY;
        batch.draw(region, (x1 - left) * unitX - 1, (y1 - bottom) * unitY - 1 - height / 2,
                (float)Math.hypot(dx, dy) * unitX, height, 0, height / 2, (float)Math.toDegrees(Math.atan2(dy, dx)));
    }

    private void lift(float x, float top, float w, float rest, float t) {
        float beam = rest + 44;
        float near = ground.liftMast(x, false, beam);
        float far = ground.liftMast(x + w, true, beam);
        if (!Float.isNaN(near) && !Float.isNaN(far)) {
            batch.color(.73f, .80f, .81f, 1);
            liftPost(near, beam);
            liftPost(far, beam);
            float start = Math.min(near, x), end = Math.max(far + 5, x + w);
            prop(4, start, beam, end - start, 4, false);
            for (int i = 0; i < 2; i++) {
                float cable = x + (i == 0 ? 2 : w - 4);
                prop(5, cable, top, 1.6f, beam - top, false);
                rotate(cog, cable + 1, beam + 2, 8, (float)Math.sin(t * 1.85f) * 180);
            }
        }
        white();
        prop(4, x, top - 6, w, 6, false);
    }

    private void liftPost(float x, float beam) {
        float foot = ground.topAt(x + 2.5f, beam) - 2;
        prop(5, x, foot, 5, beam - foot, false);
    }

    static int siteRight(int index) {
        return index < HOUSE_SITE_INDEX ? 24 : HOUSE_LEFT + HOUSE_SIZE;
    }

    private void places(PlatformerLevelAsset level, PlatformerGame game, float t) {
        for (int i = 0; i < game.siteCount(); i++) {
            float x = pixel(game.siteX(i)), y = pixel(game.siteY(i)) - 12;
            if (!visible(x - 29, 29 + siteRight(i))) continue;
            float markerX = x;
            batch.color(.9f, .91f, .85f, 1);
            if (i < HOUSE_SITE_INDEX) {
                regionQuad(level.places()[i], .002f, 0, .998f, 1, x - 29, y, 37, 37);
            } else {
                prop(2, x + HOUSE_LEFT, y - 1, HOUSE_SIZE, HOUSE_SIZE, false);
                markerX = x - 23;
            }
            white();
            prop(5, markerX + 6, y, 2.8f, 16, false);
            sprite(level.actors()[29], markerX + 2, y + 11, 12, 15, false);
            if (game.visited(i)) sprite(level.actors()[30], markerX - 6, y + 5, 28 + (float)Math.sin(t * 3), 28, false);
        }
    }

    private void machinery(PlatformerLevelAsset level, ClockworkGate gate) {
        if (gate == null) return;
        for (int i = 0; i < 3; i++) {
            float x = pixel(gate.dialX(i)), y = pixel(gate.dialY(i));
            if (!visible(x - 21, 42)) continue;
            prop(13, x - 12, y - 1, 24, 15, false);
            prop(5, x - 9, y + 11, 4, 29, false);
            prop(5, x + 5, y + 11, 4, 29, false);
            prop(12, x - 13, y + 39, 26, 4, false);
            if (gate.bells()) instrument(level, 3, x - 10, y + 19, 20);
            else {
                rotate(cog, x, y + 27, 21, gate.lit(i) ? 35 : 0);
                instrument(level, gate.lit(i) ? 5 : 4, x - 5, y + 22, 10);
            }
            instrument(level, i, x - 10, y + 2, 20);
        }
        float x = pixel(gate.x()), y = pixel(gate.y());
        if (!visible(x - 24, 48)) return;
        float high = ClockworkGate.HEIGHT * PlatformerLevel.PIXELS_PER_UNIT;
        prop(13, x - 18, y - 1, 6, high + 2, false);
        prop(13, x + 12, y - 1, 6, high + 2, false);
        float rise = high * gate.opening();
        if (rise < high) {
            // The lower section of the grille disappears into the overhead housing as it rises.
            regionQuad(props[14], .004f, gate.opening(), .996f, .996f,
                    x - 12, y + rise, 24, high - rise);
        }
        prop(12, x - 21, y + high - 2, 42, 12, false);
        for (int i = 0; i < 3; i++) {
            boolean lit = gate.bells() ? i < gate.progress() : gate.lit(i);
            instrument(level, lit ? 5 : 4, x - 14 + i * 10, y + high - 1, 9);
        }
    }

    private void instrument(PlatformerLevelAsset level, int cell, float x, float y, float size) {
        regionQuad(level.instruments()[cell], 0, 0, 1, 1, x, y, size, size);
    }

    private void encounters(PlatformerLevelAsset level, PlatformerGame game, float t) {
        for (int i = 0; i < game.collectibleCount(); i++) {
            var item = game.collectibleAt(i);
            if (item.collected()) continue;
            float w = 4 + 5 * Math.abs((float)Math.cos(t * 4 + i * .3f));
            sprite(level.actors()[28], pixel(item.x()) - w / 2, pixel(item.y()) - 4 + (float)Math.sin(t * 3 + i), w, 9, false);
        }
        for (int i = 0; i < game.hazardCount(); i++) {
            var hazard = game.hazardAt(i);
            sprite(level.actors()[31], pixel(hazard.x()) - 9, pixel(hazard.y()) - 5, 18, 12, false);
        }
        for (int i = 0; i < game.enemyCount(); i++) {
            var e = game.enemyAt(i);
            if (e.defeated() && e.age() > .18f) continue;
            float x = pixel(e.sprite().x()), feet = pixel(e.sprite().y() - e.sprite().halfHeight());
            boolean moth = e.kind() == PlatformerGame.EnemyKind.MOTH;
            int frame = switch (e.kind()) {
                case CRAWLER -> 16 + ((int)(e.age() * 9) & 3);
                case MOTH -> 20 + ((int)(e.age() * 13) & 3);
                case HOPPER -> {
                    float phase = e.age() % 1.45f;
                    yield 24 + (phase < .14f ? 1 : phase < .58f ? 2 : phase < .82f ? 3 : 0);
                }
            };
            float w = moth ? 33 : 30, h = moth ? 32 : 30;
            if (e.defeated()) {
                float squash = e.age() / .18f;
                w *= 1 + squash * .35f;
                h *= Math.max(.04f, 1 - squash);
                batch.color(1, .77f, .52f, 1 - squash);
            }
            var attack = e.attack();
            float charge = attack == null ? 0 : attack.charge();
            w *= 1 + .12f * charge;
            h *= 1 - .18f * charge;
            boolean facesLeft = charge > 0 ? attack.facesLeft() : e.movingLeft();
            sprite(level.actors()[frame], x - w / 2, feet - (moth ? 10 : .5f), w, h, facesLeft);
            white();
        }
        for (int i = 0; i < game.enemyCount(); i++) {
            var shot = game.enemyAt(i).attack();
            if (shot == null || (!shot.flying() && shot.charge() <= 0)) continue;
            float x = pixel(shot.x()), y = pixel(shot.y());
            float size = shot.flying() ? 10 : 4 + 7 * shot.charge();
            float halo = shot.flying() ? 14 : size * (1.8f + .3f * (float)Math.sin(t * 26));
            sprite(level.actors()[30], x - halo / 2, y - halo / 2, halo, halo, false);
            sprite(level.shots()[shot.kind().ordinal()], x - size / 2, y - size / 2, size, size, shot.facesLeft());
        }
    }

    private void traveler(PlatformerLevelAsset level, PlatformerGame game, float t) {
        var p = game.player();
        float x = pixel(p.x()), y = pixel(p.y() - p.halfHeight());
        int frame;
        if (!game.playerOnGround()) {
            float vy = game.playerVelocityY();
            frame = vy > 1.1f ? 12 : vy > .12f ? 13 : 14;
        } else if (game.landingTime() > 0) frame = 15;
        else if (!game.completed() && !game.gameOver() && Math.abs(game.playerVelocityX()) > .01f) frame = 4 + ((int)(t * 13) & 7);
        else frame = (int)(t * 4) & 3;
        if (game.playerOnGround()) sprite(level.actors()[32], x - 10, y - 1.4f, 20, 3.2f, false);
        if (game.hurtTime() > 0 && ((int)(game.hurtTime() * 12) & 1) != 0) batch.color(1, .76f, .77f, .65f);
        sprite(level.actors()[frame], x - 16, y - .6f, 32, 32, !game.playerFacingRight());
        white();
    }

    private void destination(PlatformerLevelAsset level, PlatformerGame.Sprite goal, float arrival) {
        float x = pixel(goal.x()), y = pixel(goal.y() - goal.halfHeight());
        float h = level.trail().isLast() ? 112 : 80;
        float w = h * level.landmark().width() / level.landmark().height();
        if (!visible(x - w, w * 2)) return;
        regionQuad(level.landmark(), 0, 0, 1, 1, x - w / 2, y, w, h);
        if (arrival <= 0) return;
        if (level.trail().isLast()) {
            float lamp = x - w * .19f, lightY = y + h * .86f, reach = 180 * (float)Math.cos(arrival * .86);
            sprite(level.actors()[34], Math.min(lamp, lamp - reach), lightY - 14, Math.abs(reach), 28, reach > 0);
            sprite(level.actors()[30], lamp - 12, lightY - 12, 24, 24, false);
        } else {
            for (int i = 0; i < 18; i++) {
                float rise = (arrival * 19 + 6 * i) % 79;
                batch.color(1, .9f, .57f, 1 - rise / 79);
                sprite(level.actors()[30], x + (float)Math.sin(i * 3.7 + arrival) * (9 + rise * .18f),
                        y + 12 + rise, 6, 6, false);
            }
        }
        white();
    }

    private void weather(PlatformerLevelAsset level, float t) {
        for (int i = 0; i < 18; i++) {
            float wx = i * 67 + t * (i % 3 + 1) * 1.8f - left * .83f;
            float x = left + wx - (float)Math.floor(wx / view.width()) * view.width();
            float y = 35 + i * 41 % 162 + 5 * (float)Math.sin(t + i);
            batch.color(1, .92f, .73f, .32f + .15f * (float)Math.sin(i + t));
            sprite(level.actors()[30], x, y, i % 3 + 2, i % 3 + 2, false);
        }
        white();
    }

    void fleck(TextureRegion region, float x, float y, float size, float alpha) {
        batch.color(1, .88f, .62f, alpha);
        sprite(region, x - size / 2, y - size / 2, size, size, false);
    }

    private static TextureRegion insetForRotation(TextureRegion region) {
        return new TextureRegion(region.texture(), region.x() + 1, region.y() + 1,
                region.width() - 2, region.height() - 2);
    }

    private void rotate(TextureRegion region, float x, float y, float size, float degrees) {
        float w = size * unitX, h = size * unitY;
        batch.draw(region, (x - size / 2 - left) * unitX - 1, (y - size / 2 - bottom) * unitY - 1, w, h, w / 2, h / 2, degrees);
    }

    private void prop(int frame, float x, float y, float w, float h, boolean flip) {
        sprite(props[frame], x, y, w, h, flip);
    }

    private void sprite(TextureRegion region, float x, float y, float w, float h, boolean flip) {
        if (visible(x, w)) {
            regionQuad(region, flip ? .997f : .003f, .003f, flip ? .003f : .997f, .997f, x, y, w, h);
        }
    }

    private void regionQuad(TextureRegion region, float u, float v, float u2, float v2,
            float x, float y, float w, float h) {
        float du = region.u2() - region.u(), dv = region.v2() - region.v();
        quad(region.texture(), region.u() + u * du, region.v() + v * dv,
                region.u() + u2 * du, region.v() + v2 * dv, x, y, w, h);
    }

    private boolean visible(float x, float w) { return x + w >= left && x <= left + view.width(); }

    private void quad(Texture texture, float u, float v, float u2, float v2, float x, float y, float w, float h) {
        batch.drawUv(texture, u, v, u2, v2, (x - left) * unitX - 1, (y - bottom) * unitY - 1, w * unitX, h * unitY);
    }

    private void draw(Texture texture, float x, float y, float w, float h) {
        quad(texture, 0, 0, 1, 1, x, y, w, h);
    }

    private void white() { batch.color(1, 1, 1, 1); }

}
