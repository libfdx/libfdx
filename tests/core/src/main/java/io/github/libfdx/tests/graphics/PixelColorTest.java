package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.PixelArtViewport;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.math.Matrix4;
import java.nio.ByteBuffer;

/** Pixel-grid and equivalent PBR color encodings, including sRGB offscreen output. */
public final class PixelColorTest extends GraphicsParityTest {
    private static final LoadOp CLEAR = LoadOp.clear(0, 0, 0, 1);
    private final PixelArtViewport viewport = new PixelArtViewport(160, 120).camera(.25f, .25f);
    private final io.github.libfdx.collections.Array<DefaultModelInstance> targetInstances = new io.github.libfdx.collections.Array<>(3);
    private final RenderPassDescriptor pixelPass = new RenderPassDescriptor().label("pixel grid");
    private final Model[] models = new Model[3];
    private final DefaultModelInstance[] instances = new DefaultModelInstance[3];
    private final Camera camera = new Camera().projection(CameraProjection.ORTHOGRAPHIC)
            .viewport(160, 120).position(0, 0, 5).nearFar(.1f, 10).update();
    private final Camera targetCamera = new Camera().projection(CameraProjection.ORTHOGRAPHIC)
            .viewport(160, 120).position(0, 28, 5).nearFar(.1f, 10).update();
    private Texture raw, srgb, checker, target;
    private ForwardRenderGraph3D targetGraph;
    private io.github.libfdx.graphics.g2d.TextureRegion targetRegion;
    private ModelBatch modelBatch;
    private SpriteBatch sprites;

    public PixelColorTest(long frames) { super(frames); }
    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "PixelColorTest");
        raw = texture("raw", TextureFormat.RGBA8_UNORM, 1, 1, new int[] {0x802008ff});
        srgb = texture("sRGB", TextureFormat.RGBA8_UNORM_SRGB, 1, 1, new int[] {0x802008ff});
        checker = texture("pixel grid", TextureFormat.RGBA8_UNORM, 2, 2,
                new int[] {0xff3030ff, 0x30ff30ff, 0x3030ffff, 0xffff30ff});
        targetGraph = new ForwardRenderGraph3D(graphics,new ModelBatchConfig(),TextureFormat.RGBA8_UNORM_SRGB,1)
                .clearColor(0,0,0,1);
        targetGraph.resize(160,120); target = targetGraph.color();
        targetRegion = io.github.libfdx.graphics.g2d.TextureRegion.rendered(target,targetGraph.origin());
        for (int i = 0; i < models.length; i++) {
            Material material = new Material("encoding " + i).shadingModel(ShadingModel.UNLIT);
            if (i < 2) material.set(MaterialAttributes.baseColorTexture(i == 0 ? raw : srgb));
            else material.set(MaterialAttributes.baseColor(ColorTransfer.srgbToLinear(128 / 255f),
                    ColorTransfer.srgbToLinear(32 / 255f), ColorTransfer.srgbToLinear(8 / 255f), 1));
            models[i] = new ModelBuilder(graphics).material(material).box(32, 24, .1f, ModelVertexUsage.STANDARD_PBR);
            instances[i] = new DefaultModelInstance(models[i]).transform(new Matrix4().setToTranslation((i - 1) * 50, 28, 0));
            targetInstances.add(instances[i]);
        }
        modelBatch = new ModelBatch(graphics);
        sprites = new SpriteBatch(graphics);
        markCreated();
    }
    private Texture texture(String name, TextureFormat format, int width, int height, int[] colors) {
        Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(name, width, height)
                .format(format).filter(TextureFilter.NEAREST));
        ByteBuffer bytes = ByteBuffer.allocateDirect(colors.length * 4);
        for (int rgba : colors) bytes.put((byte) (rgba >>> 24)).put((byte) (rgba >>> 16)).put((byte) (rgba >>> 8)).put((byte) rgba);
        bytes.flip(); graphics.device().writeTexture(texture, bytes); return texture;
    }
    @Override
    public void render() {
        viewport.update(framebufferWidth(), framebufferHeight());
        GraphicsFrame frame = graphics.currentFrame();
        targetGraph.render(targetCamera,null,targetInstances);

        modelBatch.begin(CLEAR, camera);
        for (DefaultModelInstance instance : instances) modelBatch.render(instance);
        modelBatch.end();

        RenderPass pass = frame.commandEncoder().beginRenderPass(pixelPass.colorAttachment(frame.colorAttachment())
                .colorLoadOp(LoadOp.load()).colorStoreOp(StoreOp.store()));
        viewport.apply(pass);
        sprites.viewport(framebufferWidth(), framebufferHeight()); sprites.begin(pass); sprites.color(1, 1, 1, 1);
        draw(checker, 12, 8, 24, 24);
        draw(srgb, 54, 8, 24, 24);
        sprites.draw(targetRegion, viewport.clipX(90), viewport.clipY(8), viewport.clipWidth(64), viewport.clipHeight(48));
        // A sprite outside the logical image proves clipping keeps the letterbox clean.
        draw(checker, -5, 70, 6, 20);
        sprites.end(); pass.end(); finishFrame();
    }
    private void draw(Texture texture, float x, float y, float width, float height) {
        sprites.draw(texture, viewport.clipX(x), viewport.clipY(y), viewport.clipWidth(width), viewport.clipHeight(height));
    }
    @Override
    public void dispose() {
        dispose(sprites); dispose(modelBatch);
        for (Model model : models) dispose(model);
        dispose(targetGraph); dispose(raw); dispose(srgb); dispose(checker);
        verifyDisposed();
    }
}
