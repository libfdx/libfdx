package io.github.libfdx.graphics;

import io.github.libfdx.core.*;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.math.ClipDepthRange;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class OffscreenTargetTest {
    private static final ProviderId ID=ProviderId.of("target-test");
    @Test
    void resizePublishesAtomicallyAndDisposesOnlyOwnedResourcesOnce() {
        Device fixture=new Device();
        OffscreenTarget target=new OffscreenTarget(fixture.device,true);
        assertThrows(FdxException.class,target::color);
        assertTrue(target.resize(80,40));
        var color=target.color(); var depth=target.depth(); int revision=target.revision();
        assertFalse(target.resize(80,40)); assertEquals(2,fixture.textures.size());
        fixture.failAt=4; // replacement color succeeds; replacement depth fails.
        assertThrows(FdxException.class,()->target.resize(160,90));
        assertSame(color,target.color()); assertSame(depth,target.depth());
        assertEquals(revision,target.revision()); assertEquals(80,target.width());
        assertEquals(1,fixture.textures.get(2).disposals);
        assertFalse(color.isDisposed()); assertFalse(depth.isDisposed());
        fixture.failAt=0;
        assertTrue(target.resize(160,90));
        assertTrue(color.isDisposed()); assertTrue(depth.isDisposed());
        assertEquals(160L*90*8,target.estimatedBytes());
        target.dispose(); target.dispose();
        for (Tex texture:fixture.textures) assertEquals(1,texture.disposals);
        assertThrows(FdxException.class,target::color);
        assertThrows(FdxException.class,()->target.resize(80,40));
    }
    @Test
    void multisamplingResolvesColorAndRetainsDepthAcrossPasses() {
        Device fixture=new Device();
        OffscreenTarget target=new OffscreenTarget(fixture.device,TextureFormat.RGBA8_UNORM,
                TextureFormat.DEPTH32_FLOAT,4,TextureFilter.NEAREST);
        target.resize(160,90);
        assertEquals(3,fixture.textures.size());
        assertNotSame(target.color(),target.renderColor());
        assertEquals(160L*90*36,target.estimatedBytes());
        RenderPassDescriptor[] recorded={null};
        CommandEncoder encoder=proxy(CommandEncoder.class,(p,m,a)->{
            if (m.getName().equals("beginRenderPass")) { recorded[0]=(RenderPassDescriptor)a[0]; return null; }
            throw new AssertionError(m);
        });
        GraphicsFrame frame=proxy(GraphicsFrame.class,(p,m,a)->encoder);
        target.begin(frame,true);
        var clear=recorded[0];
        assertSame(target.color().view(),clear.colorAttachments()[0].resolveView());
        assertSame(target.renderColor().view(),clear.colorAttachments()[0].view());
        assertTrue(clear.depthStencilAttachment().depthLoadOp().isClear());
        assertTrue(clear.depthStencilAttachment().depthStoreOp().isStore());
        target.begin(frame,false);
        var load=recorded[0];
        assertFalse(load.colorLoadOp().isClear());
        assertFalse(load.depthStencilAttachment().depthLoadOp().isClear());
        target.begin(frame,true); assertSame(clear,recorded[0]);
        assertEquals(TextureOrigin.TOP_LEFT,target.origin());
        target.dispose();
    }
    @Test
    void explicitDepthClearWinsOverLegacyAndInvalidDepthFailsBeforeRecording() {
        Device fixture=new Device();
        var target=new OffscreenTarget(fixture.device,true); target.resize(8,8);
        var descriptor=new RenderPassDescriptor().colorAttachment(target.color().view()).depthClear(1)
                .depthStencilAttachment(RenderPassDepthStencilAttachment.of(target.depth().view(),
                        LoadOp.clear(.25f,0,0,0),StoreOp.store(),LoadOp.load(),StoreOp.store()));
        assertEquals(.25f,descriptor.depthClearValue());
        assertThrows(FdxException.class,()->RenderPassDepthStencilAttachment.of(target.depth().view(),
                LoadOp.clear(Float.NaN,0,0,0),StoreOp.store(),LoadOp.load(),StoreOp.store()));
        assertThrows(FdxException.class,()->new OffscreenTarget(fixture.device,TextureFormat.UNKNOWN,null,1,TextureFilter.LINEAR));
        assertThrows(FdxException.class,()->new OffscreenTarget(fixture.device,TextureFormat.RGBA8_UNORM,null,2,TextureFilter.LINEAR));
        assertThrows(FdxException.class,()->target.resize(0,8));
        assertThrows(IllegalStateException.class,()->TextureOrigin.UNKNOWN.v(0));
        target.dispose();
    }
    private static final class Device {
        final ArrayList<Tex> textures=new ArrayList<>();
        int attempts,failAt;
        final GraphicsCapabilities capabilities=GraphicsCapabilities.builder().profile(ShaderProfile.PORTABLE_WEBGPU)
                .clipDepthRange(ClipDepthRange.ZERO_TO_ONE).renderedTextureOrigin(TextureOrigin.TOP_LEFT)
                .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS).feature(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS)
                .feature(GraphicsFeature.MULTISAMPLE).feature(GraphicsFeature.RESOLVE_ATTACHMENTS)
                .colorFormats(TextureFormat.RGBA8_UNORM).depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
                .resolveFormats(TextureFormat.RGBA8_UNORM).sampleCounts(1,4).build();
        final GraphicsDevice device=proxy(GraphicsDevice.class,(p,m,a)->switch(m.getName()) {
            case "capabilities" -> capabilities;
            case "providerId" -> ID;
            case "createTexture" -> {
                if (++attempts==failAt) throw new FdxException("Injected allocation failure");
                Tex value=new Tex((TextureDescriptor)a[0]); textures.add(value); yield value;
            }
            default -> throw new AssertionError(m);
        });
    }
    private static final class Tex implements Texture {
        final int w,h,samples; final TextureFormat format; final TextureUsage usage;
        final TextureView view; int disposals;
        Tex(TextureDescriptor d) {
            w=d.width(); h=d.height(); samples=d.sampleCount(); format=d.format(); usage=d.usage();
            view=proxy(TextureView.class,(p,m,a)->switch(m.getName()) {
                case "width" -> w; case "height" -> h; case "sampleCount" -> samples; case "format" -> format;
                case "providerId" -> ID; default -> throw new AssertionError(m);
            });
        }
        public int width(){return w;} public int height(){return h;} public int sampleCount(){return samples;}
        public TextureFormat format(){return format;} public TextureUsage usage(){return usage;}
        public TextureView view(){return view;} public ProviderId providerId(){return ID;}
        public <T> T as(){throw new UnsupportedOperationException();}
        public void dispose(){disposals++;} public boolean isDisposed(){return disposals>0;}
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);
    }
}
