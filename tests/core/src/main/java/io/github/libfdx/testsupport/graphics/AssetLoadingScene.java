package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.assets.*;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.testsupport.graphics.AssetLoadingFixtures.Card;

/** Two cargo bays borrow the same resources; the overlay owns its separate font assets. */
public final class AssetLoadingScene implements Disposable {
    private final GraphicsContext graphics;
    private final DefaultAssetManager uiAssets;
    private final ShowcaseFont font;
    private final ShowcaseHud hud;
    private final SpriteBatch sprites;
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("ASSET LOADING CARGO BAYS")
            .colorLoadOp(LoadOp.clear(.025f, .035f, .055f, 1)).colorStoreOp(StoreOp.store());
    private final String[] progress = new String[25];
    private boolean disposed;

    public AssetLoadingScene(GraphicsContext graphics, FileSystem files) {
        this.graphics = graphics;
        uiAssets = new DefaultAssetManager(files);
        G2DAssetLoaders.register(uiAssets, graphics);
        font = new ShowcaseFont(uiAssets.createScope(), false);
        hud = new ShowcaseHud(graphics);
        sprites = new SpriteBatch(graphics);
        for (int i = 0; i <= 24; i++) progress[i] = i + " / 24 CARGO ASSETS READY";
    }

    public void draw(int width, int height, AssetHandle<?>[] cards, int loaded,
                     boolean firstClosed, boolean allClosed, float time, boolean automatic) {
        uiAssets.update(3, 1_000_000);
        hud.font(font.poll());
        float scale = Math.min(width / 960f, height / 640f);
        float left = (width - 960 * scale) / 2, top = (height - 640 * scale) / 2;
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        hud.begin(pass, width, height, scale, left, top);
        hud.text("ASSET LOADING / CARGO TRANSFER", 28, 24, 2, .88f, .95f, 1);
        hud.text("TWO LEVELS REQUEST 24 CARGO ASSETS. EVERY CRATE USES ONE SHARED LOGO TEXTURE.", 28, 62, 1.2f, .61f, .73f, .83f);
        hud.text(allClosed ? "03 / ALL RESOURCES RELEASED" : firstClosed ? "02 / BAY A CLOSED - BAY B STILL OWNS THE CARGO"
                : loaded == 24 ? "01 / READY - BOTH BAYS SHARE THE SAME ASSETS" : "01 / LOADING CARGO", 28, 106, 1.4f, .35f, .89f, .73f);
        for (int bay = 0; bay < 2; bay++) {
            float x = 28 + bay * 466;
            boolean closed = bay == 0 ? firstClosed : allClosed;
            hud.rect(x, 148, 438, 302, .065f, .10f, .15f, 1);
            hud.text(bay == 0 ? "BAY A / FIRST LEVEL" : "BAY B / SECOND LEVEL", x + 18, 164, 1.4f, .83f, .90f, .97f);
            hud.text(closed ? "CLOSED / NO OWNERSHIP" : "OPEN / HOLDS 24 ASSET LEASES", x + 18, 191, 1, .51f, .66f, .77f);
            for (int i = 0; i < cards.length; i++) {
                float cx = x + 18 + i % 6 * 68, cy = 228 + i / 6 * 50;
                hud.rect(cx, cy, 60, 40, .12f, .18f, .24f, 1);
            }
            if (closed) hud.text("RELEASED", x + 166, 426, 1, .91f, .66f, .38f);
        }
        hud.rect(28, 480, 904, 10, .12f, .18f, .24f, 1);
        hud.rect(28, 480, 904 * loaded / 24, 10, .24f, .79f, .65f, 1);
        hud.text(allClosed ? "24 CARGO ASSETS + SHARED TEXTURE DISPOSED / CHECKS PASSED" : progress[loaded], 28, 505, 1.3f, .85f, .94f, .93f);
        hud.text(allClosed ? "THE LAST OWNER CLOSED. THE EMPTY BAYS CONFIRM THAT ASSETS ARE GONE."
                : firstClosed ? "THE LOGO AND CARGO REMAIN VALID UNTIL THE LAST LEVEL CLOSES."
                : "COLORED CRATES APPEAR WHEN GPU UPLOADS FINISH. GRAY SLOTS ARE WAITING.", 28, 537, 1.15f, .62f, .74f, .83f);
        hud.text(automatic ? "AUTOMATIC VALIDATION / TRANSITIONS ADVANCE WITHOUT INPUT"
                : allClosed ? "COMPLETE / RETURN TO THE CHOOSER TO RUN AGAIN"
                : loaded == 24 ? firstClosed ? "SPACE / CLOSE BAY B AND RELEASE ALL CARGO" : "SPACE / CLOSE BAY A AND KEEP BAY B ALIVE"
                : "LOADING IS PACED FOR THIS DEMO. THE MOVING SHUTTLE SHOWS RENDERING STAYS LIVE.", 28, 571, 1.15f, .95f, .76f, .38f);
        hud.rect(28 + (time * 110 % 880), 615, 24, 5, .95f, .76f, .38f, 1);
        hud.end();
        sprites.viewport(width, height);
        sprites.begin(pass);
        sprites.color(1, 1, 1, 1);
        for (int bay = 0; bay < 2; bay++) {
            if (bay == 0 ? firstClosed : allClosed) continue;
            for (int i = 0; i < cards.length; i++) {
                Card card = (Card) cards[i].asset();
                if (card == null) continue;
                float x = 46 + bay * 466 + i % 6 * 68, y = 228 + i / 6 * 50;
                float nx = (left + x * scale) * 2 / width - 1;
                float ny = 1 - (top + (y + 40) * scale) * 2 / height;
                sprites.draw(card.tile, nx, ny, 120 * scale / width, 80 * scale / height);
                sprites.draw(card.logo, nx + 8 * scale / width, ny + 20 * scale / height,
                        104 * scale / width, 40 * scale / height);
            }
        }
        sprites.end();
        pass.end();
    }

    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        sprites.dispose(); hud.dispose(); font.dispose(); uiAssets.dispose();
    }
}
