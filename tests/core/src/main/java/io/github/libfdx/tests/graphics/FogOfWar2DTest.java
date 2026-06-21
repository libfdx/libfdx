package io.github.libfdx.tests.graphics;

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
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.FogOfWarRenderer2D;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.graphics.g2d.TileLayer;
import io.github.libfdx.graphics.g2d.TileMap;
import io.github.libfdx.graphics.g2d.TileMapRenderer;
import io.github.libfdx.graphics.g2d.TileSet;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Runs the 2D fog-of-war shader test scenario.
 *
 * @author xpenatan
 */
public class FogOfWar2DTest extends ApplicationAdapter {
    private static final int TILE_SIZE = 16;
    private static final int TILE_COLUMNS = 4;
    private static final int MAP_WIDTH = 11;
    private static final int MAP_HEIGHT = 7;
    private static final float TILE_WORLD_SIZE = 0.16f;
    private static final int[] TILE_COLORS = {
            0x335C67FF,
            0x2E7D32FF,
            0x8D6E63FF,
            0xDDA15EFF
    };

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private Batch2D batch;
    private FogOfWarRenderer2D fogRenderer;
    private TileMapRenderer mapRenderer;
    private TileMap map;
    private TileSet tileSet;
    private Texture atlas;
    private String capturePath;
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a 2D fog-of-war test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public FogOfWar2DTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "FogOfWar2DTest");
        batch = new SpriteBatch(graphics);
        fogRenderer = new FogOfWarRenderer2D(graphics);
        fogRenderer.color(0.0f, 0.025f, 0.055f, 0.88f);
        mapRenderer = new TileMapRenderer();
        atlas = createAtlas();
        tileSet = TileSet.from(TextureRegion.split(atlas, TILE_SIZE, TILE_SIZE));
        map = createMap();
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "2"));
        created = true;
        logger.info("FogOfWar2DTest created WGSL fog-of-war renderer over " + MAP_WIDTH + "x" + MAP_HEIGHT
                + " generated tile map for provider " + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        batch.begin(LoadOp.clear(0.03f, 0.04f, 0.055f, 1.0f));
        mapRenderer.render(map, tileSet, batch, -map.worldWidth() * 0.5f, -map.worldHeight() * 0.5f);
        batch.end();

        fogRenderer.clearLights()
                .light(-0.43f, 0.12f, 0.42f, 0.18f)
                .light(0.32f, -0.26f, 0.36f, 0.16f)
                .light(0.24f, 0.38f, 0.26f, 0.12f);
        fogRenderer.begin(LoadOp.load());
        fogRenderer.draw(-1.0f, -1.0f, 2.0f, 2.0f);
        fogRenderer.end();

        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureFrame(capturePath);
            captured = true;
        }
        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (fogRenderer != null) {
            fogRenderer.dispose();
            fogRenderer = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
        }
        if (!created) {
            throw new FdxException("FogOfWar2DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("FogOfWar2DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("FogOfWar2DTest did not capture framebuffer to " + capturePath);
        }
        logger.info("FogOfWar2DTest rendered " + renderedFrames + " frames");
    }

    private Texture createAtlas() {
        int width = TILE_SIZE * TILE_COLUMNS;
        int height = TILE_SIZE;
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8("fog of war 2d tile atlas",
                width, height));
        graphics.device().writeTexture(texture, atlasPixels(width, height));
        return texture;
    }

    private ByteBuffer atlasPixels(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int tile = x / TILE_SIZE;
                int color = TILE_COLORS[tile];
                boolean grid = x % TILE_SIZE == 0 || y % TILE_SIZE == 0
                        || x % TILE_SIZE == TILE_SIZE - 1 || y % TILE_SIZE == TILE_SIZE - 1;
                boolean marker = (x % TILE_SIZE == y % TILE_SIZE) || (x % TILE_SIZE + y % TILE_SIZE == TILE_SIZE - 1);
                putRgba(pixels, grid ? brighten(color, 34) : marker ? brighten(color, 18) : color);
            }
        }
        pixels.flip();
        return pixels;
    }

    private TileMap createMap() {
        TileMap newMap = new TileMap(MAP_WIDTH, MAP_HEIGHT, TILE_WORLD_SIZE, TILE_WORLD_SIZE);
        TileLayer ground = newMap.addLayer();
        for (int y = 0; y < MAP_HEIGHT; y++) {
            for (int x = 0; x < MAP_WIDTH; x++) {
                int tile = 1 + (x * 2 + y) % TILE_COLUMNS;
                if (x == 0 || y == 0 || x == MAP_WIDTH - 1 || y == MAP_HEIGHT - 1) {
                    tile = 3;
                }
                ground.tile(x, y, tile);
            }
        }
        TileLayer path = newMap.addLayer();
        for (int x = 2; x < MAP_WIDTH - 2; x++) {
            path.tile(x, 2, 4);
        }
        for (int y = 2; y < MAP_HEIGHT - 1; y++) {
            path.tile(7, y, 2);
        }
        path.tile(3, 4, 1);
        path.tile(4, 4, 1);
        path.tile(5, 4, 1);
        return newMap;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("FogOfWar2DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture FogOfWar2DTest framebuffer", e);
        }
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private int brighten(int rgba, int amount) {
        int red = Math.min(255, ((rgba >>> 24) & 0xFF) + amount);
        int green = Math.min(255, ((rgba >>> 16) & 0xFF) + amount);
        int blue = Math.min(255, ((rgba >>> 8) & 0xFF) + amount);
        return red << 24 | green << 16 | blue << 8 | (rgba & 0xFF);
    }

    private void putRgba(ByteBuffer pixels, int rgba) {
        pixels.put((byte)((rgba >>> 24) & 0xFF));
        pixels.put((byte)((rgba >>> 16) & 0xFF));
        pixels.put((byte)((rgba >>> 8) & 0xFF));
        pixels.put((byte)(rgba & 0xFF));
    }
}
