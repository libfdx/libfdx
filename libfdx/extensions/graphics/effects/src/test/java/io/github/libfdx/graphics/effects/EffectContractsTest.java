package io.github.libfdx.graphics.effects;

import io.github.libfdx.core.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.*;
import io.github.libfdx.graphics.shader.reflection.*;
import io.github.libfdx.math.ClipDepthRange;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

final class EffectContractsTest {
    private static final ProviderId ID=ProviderId.of("effect-contract-test");
    @Test
    void hdrFilteringAndBlendingAreSeparateFromRenderability() {
        var caps=caps(false);
        assertTrue(caps.supportsColorFormat(TextureFormat.RGBA16_FLOAT));
        assertFalse(caps.supportsColorFiltering(TextureFormat.RGBA16_FLOAT));
        assertFalse(caps.supportsColorBlending(TextureFormat.RGBA16_FLOAT));
        assertEquals(EffectQuality.BALANCED,EffectQuality.bestSupported(caps));
        assertThrows(FdxException.class,()->EffectQuality.HIGH.require(caps));
        var descriptor=new TextureDescriptor().size(1,1).format(TextureFormat.RGBA16_FLOAT);
        assertThrows(FdxException.class,()->descriptor.validate(caps));
        assertDoesNotThrow(()->descriptor.filter(TextureFilter.NEAREST).validate(caps));
        assertDoesNotThrow(()->descriptor.filter(TextureFilter.LINEAR).validate(caps(true)));
        var device=new Device();
        var shader=device.shader("struct Parameters { values: vec4f, };");
        var pipeline=RenderPipelineDescriptor.shader(shader,TextureFormat.RGBA16_FLOAT);
        assertThrows(FdxException.class,()->pipeline.validate(caps));
        assertDoesNotThrow(()->pipeline.colorTargets(ColorTargetState.opaque(TextureFormat.RGBA16_FLOAT)).validate(caps));
        assertEquals(EffectQuality.HIGH,EffectQuality.bestSupported(caps(true)));
    }
    @Test
    void replacementFailurePreservesPriorOutputAndReleasesPartialAllocation() {
        var fixture=new Device(); var post=new PostProcessor(fixture.device,EffectQuality.BALANCED);
        var scene=fixture.texture(new TextureDescriptor().size(32,32));
        assertThrows(FdxException.class,post::color);
        post.resize(32,32);post.process(fixture.frame,scene,TextureOrigin.TOP_LEFT,ColorEncoding.LINEAR);
        Texture old=post.color();int revision=post.revision(),allocations=fixture.textures;
        assertFalse(post.resize(32,32));assertEquals(allocations,fixture.textures);
        fixture.failTexture=allocations+3; // two replacement textures succeed, third fails
        assertThrows(FdxException.class,()->post.resize(64,64));
        assertSame(old,post.color());assertFalse(old.isDisposed());assertEquals(revision,post.revision());
        assertEquals(32,post.width());
        fixture.failTexture=0;post.resize(64,64);
        assertTrue(old.isDisposed());assertThrows(FdxException.class,post::color);
        post.process(fixture.frame,scene,TextureOrigin.BOTTOM_LEFT,ColorEncoding.LINEAR);
        assertEquals(5,post.passCount());assertEquals(64L*64*8+16L*16*8,post.estimatedBytes());
        int pipelineCount=fixture.pipelines;
        post.process(fixture.frame,scene,TextureOrigin.TOP_LEFT,ColorEncoding.LINEAR);
        assertEquals(pipelineCount,fixture.pipelines);
        Texture result=post.color();
        assertThrows(FdxException.class,()->post.process(fixture.frame,result,TextureOrigin.TOP_LEFT,ColorEncoding.SRGB));
        post.dispose();post.dispose();
        assertFalse(scene.isDisposed());scene.dispose();
        fixture.assertClosedOnce();
    }
    @Test
    void drawAndEndFailureLeaveNoActiveScopeAndCannotExposeIncompleteOutput() {
        var fixture=new Device();var post=new PostProcessor(fixture.device,EffectQuality.LOW);
        var scene=fixture.texture(new TextureDescriptor().size(8,8));post.resize(8,8);
        fixture.failDraw=true;fixture.failEnd=true;
        var error=assertThrows(FdxException.class,()->post.process(fixture.frame,scene,TextureOrigin.TOP_LEFT,ColorEncoding.LINEAR));
        assertEquals("draw failed",error.getMessage());
        assertEquals("end failed",error.getSuppressed()[0].getMessage());
        assertEquals(0,fixture.activePasses);
        assertThrows(FdxException.class,post::color);
        fixture.failDraw=false;fixture.failEnd=false;
        post.process(fixture.frame,scene,TextureOrigin.TOP_LEFT,ColorEncoding.LINEAR);
        assertNotNull(post.color());
        post.dispose();scene.dispose();fixture.assertClosedOnce();
    }
    @Test
    void partialShaderConstructionAndInvalidLightingInputsDoNotLeakOrRecordWork() {
        var failed=new Device();failed.failShader=2;
        assertThrows(FdxException.class,()->new PostProcessor(failed.device,EffectQuality.BALANCED));
        failed.assertClosedOnce();
        var fixture=new Device();var lighting=new Lighting2D(fixture.device);
        assertThrows(FdxException.class,()->lighting.lightCount(17));
        assertThrows(FdxException.class,()->lighting.light(0,0,0,0,1,1,1,1,1));
        assertThrows(FdxException.class,()->lighting.ambient(1,Float.NaN,1));
        var srgb=fixture.texture(new TextureDescriptor().size(1,1).format(TextureFormat.RGBA8_UNORM_SRGB));
        assertThrows(FdxException.class,()->lighting.draw(null,srgb,TextureOrigin.TOP_LEFT,ColorEncoding.LINEAR));
        assertThrows(FdxException.class,()->lighting.draw(null,srgb,TextureOrigin.UNKNOWN,ColorEncoding.SRGB));
        assertThrows(FdxException.class,()->lighting.draw(null,srgb,TextureOrigin.TOP_LEFT,ColorEncoding.SRGB,srgb,TextureOrigin.TOP_LEFT));
        assertEquals(0,fixture.activePasses);
        lighting.dispose();srgb.dispose();fixture.assertClosedOnce();
    }

    private static GraphicsCapabilities caps(boolean hdr) {
        var builder=GraphicsCapabilities.builder().profile(ShaderProfile.PORTABLE_WEBGPU)
                .clipDepthRange(ClipDepthRange.ZERO_TO_ONE).renderedTextureOrigin(TextureOrigin.TOP_LEFT)
                .feature(GraphicsFeature.ALPHA_BLEND_CONTROL)
                .colorFormats(TextureFormat.RGBA8_UNORM,TextureFormat.RGBA8_UNORM_SRGB,TextureFormat.RGBA16_FLOAT);
        if(hdr)builder.filterableColorFormats(TextureFormat.RGBA8_UNORM,TextureFormat.RGBA8_UNORM_SRGB,TextureFormat.RGBA16_FLOAT)
                .blendableColorFormats(TextureFormat.RGBA8_UNORM,TextureFormat.RGBA8_UNORM_SRGB,TextureFormat.RGBA16_FLOAT);
        return builder.build();
    }
    private static final class Device {
        final ArrayList<Owned> owned=new ArrayList<>();
        int textures,failTexture,shaders,failShader,pipelines,activePasses;
        boolean failDraw,failEnd;
        final GraphicsDevice device=proxy(GraphicsDevice.class,(p,m,a)->switch(m.getName()) {
            case "capabilities"->caps(true);case "providerId"->ID;
            case "createShaderModule"-> {
                if(++shaders==failShader)throw new FdxException("shader failed");
                yield shader(((ShaderModuleDescriptor)a[0]).source());
            }
            case "createBuffer"->resource(Buffer.class);
            case "writeBuffer","writeTexture"->null;
            case "createTexture"-> {
                if(++textures==failTexture)throw new FdxException("texture failed");
                yield texture((TextureDescriptor)a[0]);
            }
            case "createRenderPipeline"->{pipelines++;yield resource(RenderPipeline.class);}
            default->throw new AssertionError(m);
        });
        final CommandEncoder encoder=proxy(CommandEncoder.class,(p,m,a)-> {
            if(!m.getName().equals("beginRenderPass"))throw new AssertionError(m);
            activePasses++;var descriptor=(RenderPassDescriptor)a[0];
            return proxy(RenderPass.class,(pp,mm,aa)->switch(mm.getName()) {
                case "compatibility"->descriptor.compatibility();
                case "draw"->{if(failDraw)throw new FdxException("draw failed");yield null;}
                case "end"->{activePasses--;if(failEnd)throw new FdxException("end failed");yield null;}
                case "setPipeline","setVertexBuffer","setTexture","setParameterBlock"->null;
                default->throw new AssertionError(mm);
            });
        });
        final GraphicsFrame frame=proxy(GraphicsFrame.class,(p,m,a)->{
            if(m.getName().equals("commandEncoder"))return encoder;
            throw new AssertionError(m);
        });
        Texture texture(TextureDescriptor d) {
            Owned value=new Owned();owned.add(value);
            TextureView view=proxy(TextureView.class,(p,m,a)->switch(m.getName()) {
                case "width"->d.width();case "height"->d.height();case "sampleCount"->1;
                case "format"->d.format();case "providerId"->ID;default->throw new AssertionError(m);
            });
            return proxy(Texture.class,(p,m,a)->switch(m.getName()) {
                case "width"->d.width();case "height"->d.height();case "format"->d.format();case "usage"->d.usage();
                case "view"->view;case "sampleCount"->1;default->value.invoke(p,m,a);
            });
        }
        ShaderModule shader(String source) {
            // CPU ownership tests only: a small fixture layout, not a shader parser or rendering proof.
            int start=source.indexOf("struct Parameters {")+"struct Parameters {".length();
            String fields=source.substring(start,source.indexOf('}',start));
            var matcher=Pattern.compile("(\\w+):\\s*(vec4f|array<vec4f,16>)").matcher(fields);
            var params=new ArrayList<ShaderParameter>();int offset=0;
            while(matcher.find()) {
                var type=ShaderValueType.vector(ShaderScalarType.F32,4);int size=16;
                if(matcher.group(2).startsWith("array")){type=ShaderValueType.array(type,16,16);size=256;}
                params.add(ShaderParameter.of(matcher.group(1),type,offset,size,16));offset+=size;
            }
            var layout=ShaderParameterLayout.of(offset,16,params.toArray(ShaderParameter[]::new));
            var binding=ShaderBinding.builder(1,0,"params",ShaderResourceKind.UNIFORM_BUFFER)
                    .visibility(ShaderStageVisibility.FRAGMENT).access(ShaderResourceAccess.READ).bufferLayout(layout).build();
            var reflection=ShaderReflection.of(new ShaderBinding[]{binding},new ShaderAttribute[0]);
            Owned value=new Owned();owned.add(value);
            return proxy(ShaderModule.class,(p,m,a)->switch(m.getName()) {
                case "reflection"->reflection;case "language"->ShaderLanguage.WGSL;default->value.invoke(p,m,a);
            });
        }
        <T> T resource(Class<T> type){Owned value=new Owned();owned.add(value);return proxy(type,value);}
        void assertClosedOnce(){for(Owned value:owned)assertEquals(1,value.disposals);}
    }
    private static final class Owned implements InvocationHandler {
        int disposals;
        @Override
        public Object invoke(Object p,Method m,Object[] a) {
            return switch(m.getName()) {
                case "dispose"->{disposals++;yield null;}
                case "isDisposed"->disposals>0;case "providerId"->ID;default->throw new AssertionError(m);
            };
        }
    }
    @SuppressWarnings("unchecked")
    private static <T>T proxy(Class<T> type,InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
}
