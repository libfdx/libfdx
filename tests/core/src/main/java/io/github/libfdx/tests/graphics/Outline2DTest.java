package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.OutlineControls;

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
import io.github.libfdx.graphics.g2d.SpriteOutlineRenderer2D;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.testsupport.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Runs the 2D sprite outline shader test scenario.
 *
 * @author xpenatan
 */
public final class Outline2DTest extends ApplicationAdapter {
    private static final int SPRITE_SIZE = 256;

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private SpriteOutlineRenderer2D outlineRenderer;
    private final Texture[] textures = new Texture[7];
    private final TextureRegion[] regions = new TextureRegion[7];
    private OutlineControls controls;
    private String capturePath;
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates an outline2 d test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public Outline2DTest(long exitAfterFrames) {
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
        fpsLogger = TestFpsLogger.create(logger, "Outline2DTest");
        outlineRenderer = new SpriteOutlineRenderer2D(graphics);
        controls = new OutlineControls(fdx, "MOONLIT GROVE / 2D OUTLINES",
                "Cyan explorer  /  amber treasure  /  violet crystal  /  mint creature");
        for (int i = 0; i < textures.length; i++) {
            textures[i] = createSpriteTexture(i);
            regions[i] = new TextureRegion(textures[i]);
        }
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "2"));
        created = true;
        logger.info("Outline2DTest created WGSL sprite outline renderer for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        controls.update(deltaSeconds);
        outlineRenderer.begin(LoadOp.clear(0.035f, 0.065f, 0.11f, 1));
        // A fixed-aspect game stage: sprite pixels and stroke stay square on resize.
        float unit = Math.min(framebufferWidth() / 1000f, framebufferHeight() / 700f);
        float sx = 2 * unit / framebufferWidth(), sy = 2 * unit / framebufferHeight();
        outlineRenderer.outlineWidth(0);
        outlineRenderer.color(.65f,.78f,1,1);
        outlineRenderer.draw(regions[6], 275*sx, 130*sy, 140*sx, 140*sy);
        for (int i = 0; i < 15; i++) {
            float x = -570 + i * 80;
            float height = 270 + (i % 4) * 35;
            outlineRenderer.color(.36f,.52f,.62f,1);
            outlineRenderer.draw(regions[4], x*sx, -180*sy, 210*sx, height*sy);
        }
        for (int i = 0; i < 11; i++) {
            outlineRenderer.color(.85f,.95f,.92f,1);
            outlineRenderer.draw(regions[5], (-560+i*110)*sx, -228*sy, 160*sx, 100*sy);
        }
        for (int i = 0; i < 4; i++) {
            outlineRenderer.color(1, 1, 1, 1);
            if (i == 0) outlineRenderer.outlineColor(.20f, .87f, 1, 1);
            if (i == 1) outlineRenderer.outlineColor(1, .72f, .20f, 1);
            if (i == 2) outlineRenderer.outlineColor(.78f, .46f, 1, 1);
            if (i == 3) outlineRenderer.outlineColor(.35f, 1, .62f, 1);
            outlineRenderer.outlineWidth(controls.width.get() * SPRITE_SIZE / (220 * unit));
            outlineRenderer.draw(regions[i], (-440+i*220)*sx, -170*sy, 220*sx, 220*sy);
        }
        outlineRenderer.end();
        controls.render();

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
        if (outlineRenderer != null) {
            outlineRenderer.dispose();
            outlineRenderer = null;
        }
        for (Texture texture : textures) if (texture != null) texture.dispose();
        if (controls != null) controls.dispose();
        if (!created) {
            throw new FdxException("Outline2DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("Outline2DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("Outline2DTest did not capture framebuffer to " + capturePath);
        }
        logger.info("Outline2DTest rendered " + renderedFrames + " frames");
    }

    @Override
    public void resize(int width, int height) {
        if (controls != null) controls.resize(width, height);
    }

    private Texture createSpriteTexture(int kind) {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8("outline game sprite " + kind,
                SPRITE_SIZE, SPRITE_SIZE));
        ByteBuffer pixels = ByteBuffer.allocateDirect(SPRITE_SIZE * SPRITE_SIZE * 4);
        for (int y = 0; y < SPRITE_SIZE; y++) for (int x = 0; x < SPRITE_SIZE; x++) {
            float u = (x + .5f - 128) / 128f, v = (y + .5f - 128) / 128f;
            float distance;
            int rgb;
            if (kind == 0) {
                // Explorer: helmet, body and separated boots, with a glass visor.
                distance = Math.min(roundBox(u, v+.28f, .40f, .33f, .13f), roundBox(u,v-.23f,.30f,.30f,.08f));
                distance = Math.min(distance, Math.min(roundBox(u-.21f,v-.57f,.12f,.13f,.06f),roundBox(u+.21f,v-.57f,.12f,.13f,.06f)));
                rgb = v < -.08f ? 0xdceaf2 : 0xde654b;
                if (roundBox(u, v+.29f, .31f,.15f,.08f)<0) rgb = u+v < -.4f ? 0x79d5e4 : 0x24475e;
                if (Math.abs(u)<.09f && v>.06f && v<.29f) rgb=0xffdb85;
            } else if (kind == 1) {
                distance = roundBox(u,v-.18f,.61f,.44f,.09f);
                rgb = v < .0f ? 0xbc8350 : 0x805035;
                if (Math.abs(u)>.43f || Math.abs(v-.12f)<.035f) rgb=0xe3b665;
                if (Math.abs(u)<.105f && v>.04f && v<.28f) rgb=0xffdf83;
            } else if (kind == 2) {
                distance = (Math.abs(u)*1.5f + Math.abs(v)*.85f - .61f)/1.72f;
                rgb = u < 0 ? (v<0 ? 0xd5c3ff : 0x8b70d6) : (v<0 ? 0x9774dc : 0x58439b);
            } else if (kind == 3) {
                distance = (float)Math.sqrt(u*u + (v-.22f)*(v-.22f)*1.4f) - .58f;
                distance = Math.min(distance, roundBox(u,v-.60f,.56f,.07f,.06f));
                rgb = v<.1f ? 0x87d5aa : 0x459b80;
                if ((u-.20f)*(u-.20f)+(v-.19f)*(v-.19f)<.006f || (u+.20f)*(u+.20f)+(v-.19f)*(v-.19f)<.006f) rgb=0x163544;
                if (Math.abs(u)<.10f && Math.abs(v-.36f)<.022f) rgb=0x163544;
             } else if (kind == 4) {
                distance = roundBox(u,v-.42f,.065f,.36f,.02f);
                for (int layer=0;layer<3;layer++) {
                    float top=-.82f+layer*.30f, bottom=top+.66f;
                    float half=(v-top)*.70f;
                    distance=Math.min(distance, Math.max(Math.abs(u)-half, Math.max(top-v,v-bottom)));
                }
                rgb=u<0 ? 0x244d54 : 0x183840;
            } else if (kind == 5) {
                distance=roundBox(u,v,.70f,.40f,.12f);
                rgb=v<-.20f ? 0x55836b : (v<-.10f ? 0x344e48 : 0x26383c);
                if(v>.05f && Math.abs(u+.20f)<.012f) rgb=0x192b32;
                if(v>.15f && Math.abs(v-.28f)<.01f) rgb=0x192b32;
            } else {
                distance=(float)Math.sqrt(u*u+v*v)-.65f;
                rgb=0xbbd9ed;
                if((u-.20f)*(u-.20f)+(v+.22f)*(v+.22f)<.035f || (u+.28f)*(u+.28f)+(v-.14f)*(v-.14f)<.012f) rgb=0x99bdd5;
            }
            int alpha = Math.round(Math.max(0, Math.min(1, .5f-distance*128))*255);
            // Extend edge RGB into transparent texels to avoid dark filtering fringes.
            putRgba(pixels, (rgb << 8) | alpha);
        }
        pixels.flip();
        graphics.device().writeTexture(texture, pixels);
        return texture;
    }

    private static float roundBox(float x, float y, float w, float h, float radius) {
        float dx=Math.abs(x)-w+radius, dy=Math.abs(y)-h+radius;
        return (float)Math.sqrt(Math.max(dx,0)*Math.max(dx,0)+Math.max(dy,0)*Math.max(dy,0))
                +Math.min(Math.max(dx,dy),0)-radius;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("Outline2DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture Outline2DTest framebuffer", e);
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

    private void putRgba(ByteBuffer pixels, int rgba) {
        pixels.put((byte)((rgba >>> 24) & 0xFF));
        pixels.put((byte)((rgba >>> 16) & 0xFF));
        pixels.put((byte)((rgba >>> 8) & 0xFF));
        pixels.put((byte)(rgba & 0xFF));
    }
}
