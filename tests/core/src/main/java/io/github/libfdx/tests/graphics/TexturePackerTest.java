package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;

/** Window-filling atlas inspection: source parity, trimmed pages, and shared-texture batching. */
public final class TexturePackerTest extends GraphicsParityTest {
    private static final String[] NAMES={"amber","cyan","rose"};
    private static final LoadOp CLEAR=LoadOp.clear(12f/255,18f/255,28f/255,1);
    private final AssetExecutor executor;
    private final DefaultAssetManager[] managers=new DefaultAssetManager[2];
    private final AssetHandle<?>[] pending=new AssetHandle<?>[8];
    private final SpriteAtlas[] atlases=new SpriteAtlas[2];
    private final TextureRegion[][] originals=new TextureRegion[2][3];
    private final AtlasRegion[][] packed=new AtlasRegion[2][3];
    private AssetHandle<io.github.libfdx.assets.loaders.ImageData> fallback;
    private SpriteBatch sprites;
    private io.github.libfdx.testsupport.graphics.PixelBatch batch;
    private ShowcaseFont font;
    private ShowcaseHud hud;
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("Atlas comparison")
            .colorLoadOp(CLEAR).colorStoreOp(StoreOp.store());
    private static final String[] CASES = {"01 / NEAREST", "02 / LINEAR + ROTATION", "03 / LINEAR + MIRROR"};
    private static final String[] DETAILS = {"CRISP PIXEL EDGES", "27 DEG / SUBPIXEL SCALE", "NEGATIVE X / -17 DEG"};
    private String pageLabel;
    private final String[] regionLabels = new String[3];
    private int readyFrames;
    public TexturePackerTest(long frames) { this(frames,null); }
    public TexturePackerTest(long frames,AssetExecutor executor) { super(frames); this.executor=executor; }
    @Override
    public void create(Fdx fdx) {
        initialize(fdx,"TexturePackerTest");
        for(int m=0;m<2;m++) {
            DefaultAssetManager manager=managers[m]=new DefaultAssetManager(fdx.files(),executor);
            G2DAssetLoaders.register(manager,graphics);
            TextureLoadOptions options=m==0 ? TextureLoadOptions.PIXEL_ART : TextureLoadOptions.DEFAULT;
            pending[m*4]=manager.load(options.descriptor("atlas/test.atlas.json",SpriteAtlas.class));
            for(int i=0;i<3;i++) pending[m*4+i+1]=manager.load(options.descriptor("atlas-source/"+NAMES[i]+".png",TextureRegion.class));
        }
        fallback=managers[0].load(AssetDescriptor.of("deferred-image/gray.png",io.github.libfdx.assets.loaders.ImageData.class));
        font = new ShowcaseFont(managers[0].createScope(), false);
        hud = new ShowcaseHud(graphics);
        sprites=new SpriteBatch(graphics); batch=new io.github.libfdx.testsupport.graphics.PixelBatch(sprites); markCreated();
    }
    @Override
    public void render() {
        for(DefaultAssetManager manager:managers) manager.update(3,1_000_000);
        if(fallback.future().isFailed()) fallback.future().get();
        hud.font(font.poll());
        boolean ready=fallback.isLoaded() && hud.hasFont();
        for(AssetHandle<?> handle:pending) {
            if(handle.future().isFailed()) {
                handle.future().onFailure(error -> logger.error("Atlas dependency failed",error));
                handle.future().get();
            }
            ready&=handle.isLoaded();
        }
        int width = framebufferWidth(), height = framebufferHeight();
        batch.viewport(width, height);
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        if(ready) {
            if(readyFrames==0) {
                var gray=fallback.asset();
                if(gray.width()!=2 || gray.height()!=2 || (gray.rgba().get(0)&255)!=64) throw new FdxException("Deferred grayscale decode differs");
                for(int m=0;m<2;m++) {
                    atlases[m]=(SpriteAtlas)pending[m*4].asset();
                    for(int i=0;i<3;i++) { packed[m][i]=atlases[m].find(NAMES[i]); originals[m][i]=(TextureRegion)pending[m*4+i+1].asset(); }
                }
                for (int i = 0; i < 3; i++) {
                    var data = packed[0][i].data();
                    regionLabels[i] = NAMES[i].toUpperCase(java.util.Locale.ROOT) + "  "
                            + data.originalWidth() + "X" + data.originalHeight() + " > "
                            + data.width() + "X" + data.height();
                }
                pageLabel = atlases[0].size()+" SPRITES / "+atlases[0].data().pageCount()+" PAGE(S)";
                logger.info("TexturePackerTest ready: pages="+atlases[0].data().pageCount()+", sprites="+atlases[0].size()+", deferredGray=true");
            }
            if(readyFrames==10 || readyFrames==20) {
                GraphicsFrameMetrics metrics=graphics.frameMetrics();
                logger.info("TexturePackerTest "+(readyFrames==10?"original":"packed")+" strip: available="+metrics.available()
                        +", draws="+metrics.drawCalls()+", textureBinds="+metrics.textureBinds());
            }
            if(readyFrames<20) {
                // Keep HUD draws out of the two isolated texture-switch measurements.
                batch.begin(pass);
                strip(readyFrames>=10,70);
                batch.end();
            } else scene(pass, width, height);
            readyFrames++;
        }
        if (!ready) {
            hud.begin(pass, width, height, Math.min(width / 640f, height / 480f), 0, 0);
            hud.text("SPRITE ATLAS / LOADING ASSETS...", 24, 28, 1.5f, .7f, .85f, 1);
            hud.end();
        }
        pass.end(); finishFrame();
    }
    private void scene(RenderPass pass, int width, int height) {
        // Uniform scale preserves sprite proportions; panels consume the remaining window area.
        float unit = Math.min(width / 1000f, height / 720f);
        float w = width / unit, h = height / unit;
        float left = 24, top = 130, gap = 16;
        float main = (w - 64) * .72f, side = w - main - 64;
        float card = (main - gap * 2) / 3, body = h - 350;
        float rowHeight = (body - 120) / 2;
        float sourceLabelY = top + 74, atlasLabelY = sourceLabelY + rowHeight;
        float sourceY = sourceLabelY + (rowHeight + 40) / 2;
        float atlasY = sourceY + rowHeight;
        hud.begin(pass, width, height, unit, 0, 0);
        hud.text("GRAPHICS 2D / RUNTIME ATLAS", left, 23, 1.1f, .35f, .8f, .83f);
        hud.text("SAME SPRITES. SHARED TEXTURE.", left, 49, 2.5f, .94f, .96f, 1);
        hud.text("PACKING REMOVES EMPTY PIXELS. DRAWING RESTORES THE ORIGINAL PIVOT AND APPEARANCE.", left, 94, 1.05f, .62f, .72f, .81f);
        for (int i = 0; i < 3; i++) {
            float x = left + i * (card + gap);
            hud.rect(x, top, card, body, .075f, .11f, .16f, 1);
            hud.rect(x, top, card, 3, .26f, .72f, .77f, 1);
            hud.text(CASES[i], x + 14, top + 18, 1.05f, .89f, .94f, 1);
            hud.text(DETAILS[i], x + 14, top + 41, .9f, .57f, .7f, .79f);
            hud.text("SOURCE PNG", x + 14, sourceLabelY, 1, .62f, .72f, .81f);
            hud.text("ATLAS REGION", x + 14, atlasLabelY, 1, .38f, .86f, .73f);
            hud.text("MATCH SHAPE + WHITE CORNER", x + 14, top + body - 24, .8f, .62f, .72f, .81f);
        }
        float px = left + main + gap;
        hud.rect(px, top, side, body, .075f, .11f, .16f, 1);
        hud.text("THE PACKED TEXTURE", px + 16, top + 18, 1.15f, .94f, .96f, 1);
        hud.text(pageLabel, px + 16, top + 44, 1, .38f, .86f, .73f);
        Texture page = atlases[0].page(0);
        float pageScale = Math.min((side - 40) / page.width(), (body - 220) / page.height());
        float pageWidth = page.width() * pageScale, pageHeight = page.height() * pageScale;
        // Checkerboard makes transparent space and padding visible on the actual atlas page.
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            float c = ((x + y) & 1) == 0 ? .15f : .20f;
            hud.rect(px + 20 + x * pageWidth / 8, top + 78 + y * pageHeight / 8,
                    pageWidth / 8, pageHeight / 8, c, c + .025f, c + .05f, 1);
        }
        hud.text("SOURCE > TRIMMED PIXELS", px + 16, top + 94 + pageHeight, .9f, .38f, .86f, .73f);
        for (int i = 0; i < 3; i++) {
            hud.text(regionLabels[i], px + 16, top + 116 + pageHeight + i * 19,
                    .95f, .78f, .85f, .91f);
        }
        hud.text("CROPPED SPRITES + EDGE PADDING", px + 16, top + body - 42, .85f, .62f, .72f, .81f);
        hud.text("REGIONS REUSE THIS PAGE.", px + 16, top + body - 24, .85f, .62f, .72f, .81f);
        float rowTop = h - 198;
        hud.rect(left, rowTop, w - 48, 152, .075f, .11f, .16f, 1);
        hud.text("BATCHING / SAME ORDER, SAME RESULT", left + 16, rowTop + 15, 1.2f, .94f, .96f, 1);
        hud.text("SOURCE TEXTURES", left + 16, rowTop + 56, .95f, .62f, .72f, .81f);
        hud.text("ATLAS REGIONS", left + 16, rowTop + 104, .95f, .38f, .86f, .73f);
        hud.text("SOURCE ROW SWITCHES TEXTURES. ATLAS ROW SHARES PAGE TEXTURES, ALLOWING FEWER BATCH FLUSHES.", left, h - 27, 1, .62f, .72f, .81f);
        hud.end();
        batch.begin(pass);
        batch.color(1, 1, 1, 1);
        for (int i = 0; i < 3; i++) {
            int m = i == 0 ? 0 : 1;
            float scale = Math.min(card / 48, (rowHeight - 40) / 30) * unit;
            if (i > 0) scale *= .8125f;
            float sx = i == 2 ? -scale : scale, rotation = i == 0 ? 0 : i == 1 ? 27 : -17;
            float x = (left + i * (card + gap) + card * .5f) * unit;
            original(m, i, x, height - sourceY * unit, sx, scale, rotation);
            packed[m][i].draw(batch, x, height - atlasY * unit, sx, scale, rotation);
        }
        batch.draw(page, (px + 20) * unit,
                height - (top + 78 + pageHeight) * unit, pageWidth * unit, pageHeight * unit);
        for (int row = 0; row < 2; row++) for (int i = 0; i < 12; i++) {
            float x = (left + 190 + i * (w - 270) / 12) * unit;
            float y = height - (rowTop + 73 + row * 48) * unit;
            if (row == 0) original(0, i % 3, x, y, 1.7f * unit, 1.7f * unit, 0);
            else packed[0][i % 3].draw(batch, x, y, 1.7f * unit, 1.7f * unit, 0);
        }
        batch.end();
    }

    private void strip(boolean atlas,float y) {
        for(int i=0;i<12;i++) {
            int sprite=i%3; float x=30+i*50;
            if(atlas) packed[0][sprite].draw(batch,x,y,1,1,0);
            else original(0,sprite,x,y,1,1,0);
        }
    }
    private void original(int m,int index,float x,float y,float sx,float sy,float rotation) {
        batch.draw(originals[m][index],x-8*sx,y-3*sy,32*sx,24*sy,8*sx,3*sy,rotation);
    }
    @Override
    public void dispose() {
        dispose(hud);
        dispose(font);
        dispose(sprites);
        for(DefaultAssetManager manager:managers) dispose(manager);
        dispose(executor);
        if(requiresCompletion() && readyFrames<30) throw new FdxException("Atlas scenario did not finish loading/render checks");
        for(SpriteAtlas atlas:atlases) if(atlas!=null && !atlas.isDisposed()) throw new FdxException("Atlas was not released");
        verifyDisposed();
    }
}
