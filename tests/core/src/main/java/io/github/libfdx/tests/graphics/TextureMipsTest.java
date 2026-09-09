package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Deterministic mip/filter/attachment colors, including full-chain rewrites after recorded draws. */
public final class TextureMipsTest extends GraphicsParityTest {
    private static final int[] COLORS = {0xe03050, 0x30c060, 0x3060e0, 0xd0a030, 0xa030c0};
    private static final float[] QUAD = {-1,-1,0,0, 1,-1,1,0, 1,1,1,1, -1,-1,0,0, 1,1,1,1, -1,1,0,1};
    private static final VertexLayout LAYOUT = VertexLayout.of(24,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0), VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X2, 16));
    private static final String SOURCE = """
            struct Input {
                @location(0) position: vec2f,
                @location(1) uv: vec2f,
                @location(2) controls: vec2f,
            };
            struct Output {
                @builtin(position) position: vec4f,
                @location(0) uv: vec2f,
                @location(1) @interpolate(flat) controls: vec2f,
            };
            @group(0) @binding(0) var u_texture: texture_2d<f32>;
            @group(0) @binding(1) var u_sampler: sampler;
            @vertex fn vertexMain(input: Input) -> Output {
                var result: Output;
                result.position = vec4f(input.position, 0.0, 1.0);
                result.uv = input.uv;
                result.controls = input.controls;
                return result;
            }
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                let implicitColor = textureSample(u_texture, u_sampler, input.uv);
                let explicitColor = textureSampleLevel(u_texture, u_sampler, input.uv, input.controls.x);
                return select(explicitColor, implicitColor, input.controls.y > 0.5);
            }
            """;
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("mip comparison").colorStoreOp(StoreOp.store());
    private final RenderPassDescriptor mipPass = new RenderPassDescriptor().label("mip attachment clear").colorStoreOp(StoreOp.store());
    private final ByteBuffer vertices = ByteBuffer.allocateDirect(6*24).order(ByteOrder.nativeOrder());
    private final Texture[] filters = new Texture[2];
    private final ByteBuffer[] oldLevels = new ByteBuffer[5], newLevels = new ByteBuffer[5];
    private Buffer vertexBuffer;
    private Texture table, linear, none, nearest, floating;
    private ShaderModule shader;
    private RenderPipeline pipeline;
    private int frames;
    private DefaultAssetManager assets;
    private ShowcaseFont font;
    private ShowcaseHud hud;
    private float uiScale, margin, gap, rowHeight, cellWidth, gridTop;
    private static final String[] ROWS = {
            "ORIGINAL UPLOAD / EACH MIP HAS ITS OWN COLOR",
            "REWRITE / EARLIER DRAWS MUST KEEP THEIR ORIGINAL COLORS",
            "RENDER TO MIPS / EACH LEVEL CLEARED SEPARATELY",
            "FILTERING / SHARP VS SMOOTH SAMPLING",
            "TYPED UPLOAD / BASE ONLY / NEAREST MIP"
    };
    private static final String[] LEVELS = {"LOD 0 / 19 X 11", "LOD 1 / 9 X 5", "LOD 2 / 4 X 2", "LOD 3 / 2 X 1", "LOD 4 / 1 X 1"};
    private static final String[] FILTER_LABELS = {"MAG: NEAREST", "MAG: LINEAR", "MIN: NEAREST", "MIN: LINEAR", "MIP LINEAR: 2.25"};
    private static final String[] UPLOAD_LABELS = {"TYPED: LOD 0", "TYPED: LOD 1", "TYPED: LOD 2", "MIPS OFF: LOD 0", "NEAREST: LOD 3"};

    public TextureMipsTest(long frames) { super(frames); }

    @Override public void create(Fdx fdx) {
        initialize(fdx, "TextureMipsTest");
        graphics.device().capabilities().require(GraphicsFeature.TEXTURE_MIP_LEVELS);
        assets = new DefaultAssetManager(fdx.files(), null);
        G2DAssetLoaders.register(assets, graphics);
        font = new ShowcaseFont(assets.createScope(), false);
        hud = new ShowcaseHud(graphics);
        for (int i = 0; i < 5; i++) {
            oldLevels[i] = solid(Math.max(1,19>>i), Math.max(1,11>>i), COLORS[i]);
            newLevels[i] = solid(Math.max(1,19>>i), Math.max(1,11>>i), COLORS[(i+2)%5]);
        }
        table = graphics.device().createTexture(TextureDescriptor.rgba8RenderTarget("mip rewrites",19,11).mipLevelCount(5)
                .filters(TextureFilter.NEAREST, TextureFilter.NEAREST, TextureMipmapFilter.NEAREST));
        linear = texture("trilinear", TextureMipmapFilter.LINEAR);
        none = texture("base only", TextureMipmapFilter.NONE);
        nearest = texture("nearest mip", TextureMipmapFilter.NEAREST);
        for (int level = 0; level < 5; level++) {
            if (table.view(level).width() != Math.max(1,19>>level) || table.view(level).height() != Math.max(1,11>>level)
                    || table.view(level) != table.view(level)) throw new FdxException("Incorrect mip view dimensions or reuse");
        }
        ByteBuffer checker = rgba8(4,1);
        for (int i = 0; i < 4; i++) {
            byte color = (byte)((i&1)*255);
            checker.put(color).put(color).put(color).put((byte)255);
        }
        checker.flip();
        for (int i = 0; i < 2; i++) {
            filters[i] = graphics.device().createTexture(TextureDescriptor.rgba8("min-mag-"+i,4,1).wrap(TextureWrap.REPEAT)
                    .filters(i==0?TextureFilter.LINEAR:TextureFilter.NEAREST, i==0?TextureFilter.NEAREST:TextureFilter.LINEAR, TextureMipmapFilter.NONE));
            graphics.device().writeTexture(filters[i], checker);
        }
        boolean half = graphics.device().capabilities().supportsColorFormat(TextureFormat.RGBA16_FLOAT);
        floating = graphics.device().createTexture(TextureDescriptor.rgba8("typed mip upload",64,4)
                .format(half?TextureFormat.RGBA16_FLOAT:TextureFormat.RGBA8_UNORM).mipLevelCount(3)
                .filters(TextureFilter.NEAREST,TextureFilter.NEAREST,TextureMipmapFilter.NEAREST));
        ByteBuffer[] typed = new ByteBuffer[3];
        int[] halves = {0x3c00,0x3800,0x3400}, rgba = {0xff8040,0x40ff80,0x8040ff};
        for (int level = 0; level < 3; level++) {
            int width = 64>>level, height = Math.max(1,4>>level);
            if (!half) typed[level] = solid(width,height,rgba[level]);
            else {
                typed[level] = ByteBuffer.allocateDirect(width*height*8+65).order(ByteOrder.LITTLE_ENDIAN);
                typed[level].position(65);
                for (int i = 0; i < width*height; i++) {
                    for (int c = 0; c < 3; c++) typed[level].putShort((short)halves[(c+3-level)%3]);
                    typed[level].putShort((short)0x3c00);
                }
                typed[level].flip().position(65);
            }
        }
        graphics.device().writeTextureMipLevels(floating, typed);
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex("mip vertices",6*24));
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("mip sampling",SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor.shader(shader,graphics.surfaceFormat())
                .vertexLayout(LAYOUT).sampledTextureCount(1).depthWriteEnabled(false));
        logger.info("TextureMipsTest typedUpload="+floating.format()+" NPOT=19x11 levels=5");
        markCreated();
    }

    private Texture texture(String name, TextureMipmapFilter mip) {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(name,19,11).mipLevelCount(5)
                .filters(TextureFilter.NEAREST,TextureFilter.NEAREST,mip));
        graphics.device().writeTextureMipLevels(texture,oldLevels);
        return texture;
    }

    @Override public void render() {
        if (!hud.hasFont()) {
            assets.update(4, 1_000_000);
            hud.font(font.poll());
        }
        uiScale = Math.min(2f, Math.min(framebufferWidth() / 640f, framebufferHeight() / 480f));
        margin = 16 * uiScale;
        gap = 10 * uiScale;
        gridTop = 66 * uiScale;
        rowHeight = (framebufferHeight() - gridTop - margin) / 5;
        cellWidth = (framebufferWidth() - 2 * margin - 4 * gap) / 5;
        GraphicsFrame frame = graphics.currentFrame();
        graphics.device().writeTextureMipLevels(table,oldLevels);
        RenderPass pass = screen(frame,true);
        for (int i = 0; i < 5; i++) draw(pass,table,i,0,i,1,false);
        pass.end();
        graphics.device().writeTextureMipLevels(table,newLevels);
        pass = screen(frame,false);
        for (int i = 0; i < 5; i++) draw(pass,table,i,1,i,1,false);
        pass.end();
        for (int i = 0; i < 5; i++) {
            frame.commandEncoder().beginRenderPass(mipPass.colorAttachment(table.view(i))
                    .colorLoadOp(LoadOp.clear((20+40*i)/255f,(220-32*i)/255f,(40+20*i)/255f,1))).end();
        }
        pass = screen(frame,false);
        for (int i = 0; i < 5; i++) draw(pass,table,i,2,i,1,false);
        draw(pass,filters[0],0,3,0,1,true);
        draw(pass,filters[1],1,3,0,1,true);
        draw(pass,filters[1],2,3,0,64.03125f,true);
        draw(pass,filters[0],3,3,0,64.03125f,true);
        draw(pass,linear,4,3,2.25f,1,false);
        for (int i = 0; i < 3; i++) draw(pass,floating,i,4,i,1,false);
        draw(pass,none,3,4,4,1,false);
        draw(pass,nearest,4,4,2.75f,1,false);
        pass.setViewport(0, 0, framebufferWidth(), framebufferHeight());
        hud.begin(pass, framebufferWidth(), framebufferHeight(), uiScale, 0, 0);
        hud.text("TEXTURE MIP LEVELS", 16, 12, 1.5f, .9f, .95f, 1);
        hud.text("SMALLER TEXTURES FOR DISTANT SURFACES. COLORS IDENTIFY EACH LEVEL.", 16, 38, .85f, .6f, .72f, .82f);
        for (int row = 0; row < 5; row++) {
            float top = (gridTop + row * rowHeight) / uiScale;
            hud.text(ROWS[row], 16, top, .85f, .8f, .87f, .95f);
            String[] labels = row < 3 ? LEVELS : row == 3 ? FILTER_LABELS : UPLOAD_LABELS;
            for (int column = 0; column < 5; column++) {
                hud.text(labels[column], (margin + column * (cellWidth + gap)) / uiScale,
                        top + rowHeight / uiScale - 15, .75f, .6f, .72f, .82f);
            }
        }
        hud.end();
        pass.end();
        for (int i = 0; i < 5; i++) if (oldLevels[i].position()!=4 || newLevels[i].position()!=4) {
            throw new FdxException("Upload changed caller buffer position");
        }
        frames++;
        finishFrame();
    }

    private RenderPass screen(GraphicsFrame frame, boolean clear) {
        return frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment())
                .colorLoadOp(clear?LoadOp.clear(12f/255,18f/255,28f/255,1):LoadOp.load()));
    }

    private void draw(RenderPass pass, Texture texture, int column, int row, float lod, float uScale, boolean implicit) {
        int x = Math.round(margin + column * (cellWidth + gap));
        int width = Math.max(1, Math.round(margin + column * (cellWidth + gap) + cellWidth) - x);
        int top = Math.round(gridTop + row * rowHeight + 18 * uiScale);
        int bottom = Math.round(gridTop + (row + 1) * rowHeight - 21 * uiScale);
        // Preserve the original texel/pixel derivative so resizing never turns minification into magnification.
        if (implicit && uScale > 1) uScale *= width / 104f;
        vertices.clear();
        for (int i = 0; i < 6; i++) {
            vertices.putFloat(QUAD[i*4]).putFloat(QUAD[i*4+1]).putFloat(QUAD[i*4+2]*uScale).putFloat(QUAD[i*4+3]);
            vertices.putFloat(lod).putFloat(implicit?1:0);
        }
        vertices.flip();
        graphics.device().writeBuffer(vertexBuffer,vertices);
        pass.setViewport(x, framebufferHeight() - bottom, width, Math.max(1, bottom - top));
        pass.setPipeline(pipeline);
        pass.setTexture(0,texture);
        pass.setVertexBuffer(vertexBuffer);
        pass.draw(6,1,0,0);
    }

    private static ByteBuffer solid(int width, int height, int rgb) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(width*height*4+8);
        bytes.putInt(0x12345678);
        for (int i = 0; i < width*height; i++) bytes.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb).put((byte)255);
        bytes.flip().position(4);
        return bytes;
    }

    @Override public void dispose() {
        dispose(hud); dispose(font); dispose(assets);
        dispose(pipeline); dispose(shader); dispose(vertexBuffer);
        dispose(table); dispose(linear); dispose(none); dispose(nearest); dispose(floating);
        for (Texture texture : filters) dispose(texture);
        logger.info("TextureMipsTest complete-chain rewrites="+frames*2+" mip attachment passes="+frames*5);
        verifyDisposed();
    }
}
