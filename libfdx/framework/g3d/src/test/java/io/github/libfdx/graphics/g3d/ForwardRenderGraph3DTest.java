package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.math.ClipDepthRange;
import java.lang.reflect.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ForwardRenderGraph3DTest {
    @Test
    void modelTargetForwardsAllColorsDepthAndResolveAndReusesUnchangedMetadata() {
        Fixture f=new Fixture();
        ModelBatch batch=new ModelBatch(f.graphics,new ModelBatchConfig().shaderProvider(f.provider));
        TextureView first=view(TextureFormat.RGBA8_UNORM,64,32,4);
        TextureView second=view(TextureFormat.BGRA8_UNORM,64,32,4);
        TextureView resolve=view(TextureFormat.RGBA8_UNORM,64,32,1);
        TextureView depth=view(TextureFormat.DEPTH32_FLOAT,64,32,4);
        var target=new DefaultRenderTarget3D(64,32,new TextureView[]{first,second},new TextureView[]{resolve,null},depth);
        batch.begin(target,new Camera());
        var descriptor=f.descriptor;
        assertEquals(2,descriptor.colorAttachments().length);
        assertSame(second,descriptor.colorAttachments()[1].view());
        assertSame(resolve,descriptor.colorAttachments()[0].resolveView());
        assertSame(depth,descriptor.depthStencilAttachment().view());
        batch.end(); assertEquals(1,f.passEnds);
        batch.begin(target,new Camera()); assertSame(descriptor,f.descriptor); batch.end();
        assertEquals(2,f.passEnds); batch.dispose(); assertEquals(0,f.providerDisposals);
        assertThrows(FdxException.class,()->new DefaultRenderTarget3D(64,32,
                new TextureView[]{first},new TextureView[]{view(TextureFormat.RGBA8_UNORM,32,32,1)},depth));
    }

    @Test
    void forwardGraphOwnsOnlyItsResourcesAndKeepsPriorViewsOnFailedResize() {
        Fixture f=new Fixture();
        var graph=new ForwardRenderGraph3D(f.graphics,new ModelBatchConfig().shaderProvider(f.provider),TextureFormat.RGBA8_UNORM,1);
        assertThrows(FdxException.class,()->graph.target("scene"));
        graph.resize(64,32); Texture color=graph.color(); RenderTarget3D view=graph.target("scene");
        assertFalse(graph.resize(64,32)); assertSame(color,graph.color());
        graph.render(new Camera(),null,new Array<>());
        assertEquals(1,f.passEnds); assertTrue(f.descriptor.depthStencilAttachment().depthLoadOp().isClear());
        f.failTextureAt=4;
        assertThrows(FdxException.class,()->graph.resize(128,64));
        assertSame(color,graph.color()); assertSame(view,graph.target("scene")); assertFalse(color.isDisposed());
        f.failTextureAt=0; graph.resize(128,64); assertTrue(color.isDisposed());
        graph.dispose(); graph.dispose(); assertEquals(0,f.providerDisposals);
        for(int[] disposal:f.textureDisposals)assertEquals(1,disposal[0]);
        assertThrows(FdxException.class,()->graph.target("missing"));
    }

    @Test
    void forwardPathEndsBorrowedBatchAndPassEvenWhenEachStageFails() {
        Fixture f=new Fixture(); OffscreenTarget target=new OffscreenTarget(f.device,true); target.resize(8,8);
        var path=new ForwardRenderPath3D(f.graphics,target,true);
        FdxException primary=new FdxException("render"), secondary=new FdxException("batch end");
        f.passError=new FdxException("pass end"); int[] ended={0}, disposed={0};
        Batch3D batch=proxy(Batch3D.class,(p,m,a)->switch(m.getName()){
            case "environment" -> p;
            case "begin" -> null;
            case "render" -> throw primary;
            case "end" -> {ended[0]++;throw secondary;}
            case "dispose" -> {disposed[0]++;yield null;}
            default -> throw new AssertionError(m);
        });
        assertSame(primary,assertThrows(FdxException.class,()->path.render(batch,new Camera(),null,new Array<>())));
        assertArrayEquals(new Throwable[]{secondary,f.passError},primary.getSuppressed());
        assertEquals(1,ended[0]); assertEquals(1,f.passEnds);
        path.dispose(); assertEquals(0,disposed[0]); assertFalse(target.isDisposed()); target.dispose();
    }

    private static final ProviderId ID=ProviderId.of("forward-test");
    private static TextureView view(TextureFormat format,int width,int height,int samples){
        return proxy(TextureView.class,(p,m,a)->switch(m.getName()){
            case "format" -> format; case "width" -> width; case "height" -> height; case "sampleCount" -> samples;
            case "providerId" -> ID; default -> throw new AssertionError(m);
        });
    }
    private static final class Fixture {
        RenderPassDescriptor descriptor; int passEnds,providerDisposals,textureAttempts,failTextureAt;
        FdxException passError;
        final ArrayList<int[]> textureDisposals=new ArrayList<>();
        final GraphicsCapabilities capabilities=GraphicsCapabilities.builder().profile(ShaderProfile.PORTABLE_WEBGPU)
                .clipDepthRange(ClipDepthRange.ZERO_TO_ONE).renderedTextureOrigin(TextureOrigin.TOP_LEFT)
                .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS).feature(GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS)
                .feature(GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS).feature(GraphicsFeature.MULTISAMPLE).feature(GraphicsFeature.RESOLVE_ATTACHMENTS)
                .colorFormats(TextureFormat.RGBA8_UNORM,TextureFormat.BGRA8_UNORM).depthStencilFormats(TextureFormat.DEPTH32_FLOAT)
                .resolveFormats(TextureFormat.RGBA8_UNORM).sampleCounts(1,4).limits(GraphicsLimits.builder().maxColorAttachments(4).build()).build();
        final ShaderProvider3D provider=proxy(ShaderProvider3D.class,(p,m,a)->{
            if(m.getName().equals("dispose"))providerDisposals++;
            return null;
        });
        final GraphicsDevice device=proxy(GraphicsDevice.class,(p,m,a)->switch(m.getName()){
            case "capabilities" -> capabilities;
            case "createTexture" -> {
                if(++textureAttempts==failTextureAt)throw new FdxException("allocation");
                TextureDescriptor d=(TextureDescriptor)a[0]; int w=d.width(),h=d.height(),samples=d.sampleCount();
                TextureFormat format=d.format(); TextureUsage usage=d.usage();
                TextureView view=view(format,w,h,samples); int[] disposals={0}; textureDisposals.add(disposals);
                yield proxy(Texture.class,(t,n,b)->switch(n.getName()){
                    case "view" -> view; case "width" -> w; case "height" -> h; case "sampleCount" -> samples;
                    case "format" -> format; case "usage" -> usage;
                    case "dispose" -> {disposals[0]++;yield null;}
                    case "isDisposed" -> disposals[0]>0;
                    default -> throw new AssertionError(n);
                });
            }
            default -> throw new AssertionError(m);
        });
        final RenderPass pass=proxy(RenderPass.class,(p,m,a)->switch(m.getName()){
            case "compatibility" -> descriptor.compatibility();
            case "end" -> {passEnds++;if(passError!=null)throw passError;yield null;}
            default -> throw new AssertionError(m);
        });
        final CommandEncoder encoder=proxy(CommandEncoder.class,(p,m,a)->{
            if(!m.getName().equals("beginRenderPass"))throw new AssertionError(m);
            descriptor=(RenderPassDescriptor)a[0]; descriptor.validate(capabilities); return pass;
        });
        final GraphicsFrame frame=proxy(GraphicsFrame.class,(p,m,a)->{
            if(m.getName().equals("commandEncoder"))return encoder;throw new AssertionError(m);
        });
        final GraphicsContext graphics=proxy(GraphicsContext.class,(p,m,a)->switch(m.getName()){
            case "device" -> device; case "currentFrame" -> frame;
            case "surfaceFormat" -> TextureFormat.RGBA8_UNORM;
            case "providerId" -> ID; default -> throw new AssertionError(m);
        });
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,InvocationHandler handler){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},handler);
    }
}
