package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.input.*;
import io.github.libfdx.maps.MapObject;
import io.github.libfdx.maps.ObjectLayer;
import io.github.libfdx.maps.tiled.TiledMapLoader;
import io.github.libfdx.testsupport.graphics.*;
import java.nio.ByteBuffer;

/** One explorable authored Tiled scene; controls exercise the imported map in place. */
public final class TiledMapTest extends GraphicsParityTest {
    private final AssetExecutor executor;
    private final TileMapRenderer renderer = new TileMapRenderer();
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("Tiled coastal village")
            .colorLoadOp(LoadOp.clear(.125f, .416f, .518f, 1)).colorStoreOp(StoreOp.store());
    private DefaultAssetManager assets;
    private AssetLease<TileMapAsset> map;
    private ShowcaseFont font;
    private ShowcaseHud hud;
    private SpriteBatch sprites;
    private PixelBatch batch;
    private Texture solid;
    private TextureRegion white;
    private Input input;
    private float panX, panY, zoom = 1, scale = 1, originX, originY, sceneWidth;
    private double time;
    private boolean paused, mist = true, objects = true, dragging, moved, ready;
    private int lastX, lastY, selected = -1;
    private final InputAdapter controls = new InputAdapter() {
        @Override
        public boolean keyDown(KeyEvent event) {
            switch (event.key()) {
                case SPACE -> paused = !paused;
                case P -> mist = !mist;
                case O -> objects = !objects;
                case R -> reset();
                default -> { return false; }
            }
            return true;
        }
        @Override
        public boolean pointerDown(PointerEvent event) {
            float x = event.x() * framebufferWidth() / (float) Math.max(1, display.width());
            float y = event.y() * framebufferHeight() / (float) Math.max(1, display.height());
            if (x >= sceneWidth) {
                float row = y / scale;
                if (row >= 260 && row < 296) paused = !paused;
                else if (row >= 306 && row < 342) mist = !mist;
                else if (row >= 352 && row < 388) objects = !objects;
                else if (row >= 398 && row < 434) reset();
                return true;
            }
            dragging = true; moved = false; lastX = event.x(); lastY = event.y();
            return true;
        }
        @Override
        public boolean pointerMoved(PointerEvent event) {
            if (!dragging) return false;
            int dx = event.x() - lastX, dy = event.y() - lastY;
            if (dx != 0 || dy != 0) moved = true;
            panX -= dx * framebufferWidth() / (float)Math.max(1, display.width()) / effectiveZoom();
            panY += dy * framebufferHeight() / (float)Math.max(1, display.height()) / effectiveZoom();
            lastX = event.x(); lastY = event.y(); clampPan(); return true;
        }
        @Override
        public boolean pointerUp(PointerEvent event) {
            if (dragging && !moved) select(event.x(), event.y());
            dragging = false; return true;
        }
        @Override
        public boolean scrolled(PointerEvent event) {
            zoom = Math.max(.65f, Math.min(2.4f, zoom - event.scrollY() * .12f)); return true;
        }
    };

    public TiledMapTest(long frames) { this(frames, null); }
    /** Owns the optional asset executor. */
    public TiledMapTest(long frames, AssetExecutor executor) { super(frames); this.executor = executor; }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, getClass().getSimpleName());
        input = fdx.input();
        zoom = Math.max(.65f, Math.min(2.4f, Float.parseFloat(System.getProperty("libfdx.test.tiled.zoom", "1"))));
        panX = Float.parseFloat(System.getProperty("libfdx.test.tiled.panX", "0"));
        panY = Float.parseFloat(System.getProperty("libfdx.test.tiled.panY", "0"));
        assets = new DefaultAssetManager(fdx.files(), executor);
        TiledMapLoader.register(assets); G2DAssetLoaders.register(assets, graphics);
        AssetScope scope = assets.createScope();
        TextureLoadOptions textureOptions = Boolean.parseBoolean(System.getProperty("libfdx.test.tiled.linear", "false"))
                ? TextureLoadOptions.DEFAULT : TextureLoadOptions.PIXEL_ART;
        map = scope.load(textureOptions.descriptor("tiled/coast.tmj", TileMapAsset.class));
        font = new ShowcaseFont(scope, false);
        hud = new ShowcaseHud(graphics);
        sprites = new SpriteBatch(graphics, 4096); batch = new PixelBatch(sprites);
        solid = graphics.device().createTexture(TextureDescriptor.rgba8("Landmark outline", 1, 1));
        ByteBuffer pixel = ByteBuffer.allocateDirect(4); pixel.putInt(-1).flip();
        graphics.device().writeTexture(solid, pixel); white = new TextureRegion(solid);
        input.addProcessor(controls); markCreated();
    }

    @Override
    public void render() {
        assets.update(6, 2_000_000);
        if (map.future().isFailed()) map.future().get();
        hud.font(font.poll());
        int width = framebufferWidth(), height = framebufferHeight();
        scale = Math.min(width / 1120f, height / 720f);
        sceneWidth = width - 300 * scale;
        float dt = Math.min(application.deltaTime(), .05f);
        float speed = 240 * dt / zoom;
        if (input.isKeyPressed(Key.A) || input.isKeyPressed(Key.LEFT)) panX -= speed;
        if (input.isKeyPressed(Key.D) || input.isKeyPressed(Key.RIGHT)) panX += speed;
        if (input.isKeyPressed(Key.W) || input.isKeyPressed(Key.UP)) panY += speed;
        if (input.isKeyPressed(Key.S) || input.isKeyPressed(Key.DOWN)) panY -= speed;
        clampPan();
        float z = effectiveZoom();
        originX = sceneWidth / z / 2 - 640 - panX;
        originY = height / z / 2 - 384 - panY;
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        batch.viewport(Math.max(1, Math.round(width / z)), Math.max(1, Math.round(height / z)));
        batch.begin(pass);
        if (map.isLoaded()) {
            if (!paused) time += dt * 1000;
            TileMapAsset asset = map.asset();
            asset.tiles().animationTime((long)time);
            asset.map().mapLayer(2).visible(mist);
            renderer.camera(sceneWidth / z / 2, height / z / 2);
            int draws = renderer.render(asset.map(), asset.tiles(), batch, originX, originY,
                    0, 0, sceneWidth / z, height / z);
            if (objects) drawObjects();
            if (!ready && hud.hasFont()) {
                if (draws == 0 || landmarks().objectCount() != 3) throw new FdxException("Coastal scene is incomplete");
                ready = true; logger.info("Tiled coastal village ready: animated atlas, group, image parallax, 3 selectable landmarks");
            }
        }
        batch.end();
        hud.begin(pass, width, height, scale, sceneWidth, 0);
        hud.rect(0, 0, 300, height / scale, .035f, .085f, .105f, 1);
        hud.text("TILED / INTERACTIVE MAP", 22, 27, 1.05f, .43f, .78f, .76f);
        hud.text("COASTAL", 22, 65, 2.7f, .94f, .91f, .79f);
        hud.text("VILLAGE", 22, 102, 2.7f, .94f, .91f, .79f);
        hud.text("EXPLORE A MAP LOADED FROM TILED JSON.", 22, 161, 1.03f, .68f, .78f, .77f);
        hud.text("DRAG OR WASD / ARROWS TO PAN", 22, 192, 1.12f, .85f, .9f, .85f);
        hud.text("SCROLL TO ZOOM / CLICK GOLD LANDMARKS", 22, 215, 1.03f, .85f, .9f, .85f);
        hud.button(paused ? "SPACE / RESUME WATER" : "SPACE / PAUSE WATER", 22, 260, 256, 36, !paused);
        hud.button("P / PARALLAX MIST", 22, 306, 256, 36, mist);
        hud.button("O / OBJECT OUTLINES", 22, 352, 256, 36, objects);
        hud.button("R / RESET VIEW", 22, 398, 256, 36, false);
        hud.text("WHAT TO LOOK FOR", 22, 468, 1.15f, .92f, .76f, .46f);
        hud.text("WATER RIPPLES SWITCH ATLAS FRAMES.", 22, 499, 1.02f, .72f, .82f, .8f);
        hud.text("MIST MOVES SLOWER AS YOU PAN.", 22, 520, 1.02f, .72f, .82f, .8f);
        hud.text("TREES AND ROOFS USE A GROUPED LAYER.", 22, 541, 1.02f, .72f, .82f, .8f);
        hud.text("GOLD BOUNDS COME FROM MAP OBJECTS.", 22, 562, 1.02f, .72f, .82f, .8f);
        hud.rect(22, 609, 256, 2, .2f, .35f, .35f, 1);
        hud.text(!ready ? "LOADING SCENE..." : selected < 0 ? "SELECT A GOLD LANDMARK" : landmarks().object(selected).name(),
                22, 631, 1.12f, .95f, .81f, .49f);
        hud.text(selected < 0 ? "ITS NAME IS READ FROM THE TMJ OBJECT." : "SELECTED MAP OBJECT / R TO CLEAR", 22, 658, 1, .66f, .77f, .76f);
        hud.end(); pass.end(); finishFrame();
    }

    private float effectiveZoom() { return Math.max(.01f, zoom * scale * .78f); }
    private void clampPan() { panX = Math.max(-600, Math.min(600, panX)); panY = Math.max(-360, Math.min(360, panY)); }
    private void reset() { panX = panY = 0; zoom = 1; time = 0; paused = false; mist = objects = true; selected = -1; }
    private ObjectLayer landmarks() { return (ObjectLayer)map.asset().map().mapLayer(3); }
    private void select(int x, int y) {
        if (!map.isLoaded() || !objects) return;
        float wx = x * framebufferWidth() / (float)Math.max(1, display.width()) / effectiveZoom() - originX;
        float wy = (framebufferHeight() - y * framebufferHeight() / (float)Math.max(1, display.height())) / effectiveZoom() - originY;
        selected = -1;
        for (int i = 0; i < landmarks().objectCount(); i++) {
            MapObject object = landmarks().object(i);
            if (wx >= object.x() && wx <= object.x() + object.width() && wy >= object.y() - object.height() && wy <= object.y()) selected = i;
        }
    }
    private void drawObjects() {
        for (int i = 0; i < landmarks().objectCount(); i++) {
            MapObject object = landmarks().object(i);
            float x = originX + object.x(), y = originY + object.y() - object.height(), w = object.width(), h = object.height();
            batch.color(1, i == selected ? 1 : .78f, i == selected ? 1 : .35f, 1);
            float line = (i == selected ? 3 : 1.5f) / effectiveZoom();
            batch.draw(white, x, y, w, line); batch.draw(white, x, y + h, w, line);
            batch.draw(white, x, y, line, h); batch.draw(white, x + w, y, line, h);
        }
        batch.color(1, 1, 1, 1);
    }
    @Override
    public void dispose() {
        if (input != null) input.removeProcessor(controls);
        dispose(hud); dispose(sprites); dispose(solid); dispose(font); dispose(assets); dispose(executor);
        if (requiresCompletion() && !ready) throw new FdxException("Coastal map did not finish loading");
        verifyDisposed();
    }
}
