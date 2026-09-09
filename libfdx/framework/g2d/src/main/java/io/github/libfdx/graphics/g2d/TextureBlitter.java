package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Application-owned full-viewport texture compositor; borrows the graphics device,
 * input textures and active destination pass. Caller sets viewport/scissor and ends the pass.
 * Declared source origin is applied explicitly. Straight-alpha over blending produces
 * premultiplied color in a transparent target. Set premultiplied=true for that subsequent input
 * to avoid multiplying alpha twice. No tone mapping or manual color transfer is performed;
 * choose source/destination formats with matching color semantics. UI can be drawn afterward.
 * Pipelines are cached by destination layout; steady-state draws allocate no Java objects.
 */
public final class TextureBlitter implements Disposable {
    private static final VertexLayout VERTICES=VertexLayout.of(16,
            VertexAttribute.of(0,VertexFormat.FLOAT32X2,0),VertexAttribute.of(1,VertexFormat.FLOAT32X2,8));
    private static final String SOURCE="""
            struct Input { @location(0) position: vec2f, @location(1) uv: vec2f };
            struct Output { @builtin(position) position: vec4f, @location(0) uv: vec2f };
            @group(0) @binding(0) var source: texture_2d<f32>;
            @group(0) @binding(1) var sourceSampler: sampler;
            @vertex fn vertexMain(input: Input) -> Output {
                var output: Output;
                output.position=vec4f(input.position,0.0,1.0);
                output.uv=input.uv;
                return output;
            }
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                return textureSample(source,sourceSampler,input.uv);
            }
            """;
    private static final String PREMULTIPLIED_SOURCE=SOURCE.replace(
            "return textureSample(source,sourceSampler,input.uv);",
            "let value=textureSample(source,sourceSampler,input.uv); return vec4f(value.rgb/max(value.a,0.00001),value.a);");
    private final GraphicsDevice device;
    private ShaderModule shader;
    private ShaderModule premultipliedShader;
    private Buffer top, bottom;
    private RenderPipeline[] pipelines=new RenderPipeline[4];
    private String[] keys=new String[4];
    private boolean[] premultipliedInputs=new boolean[4];
    private int count;
    private boolean disposed;
    public TextureBlitter(GraphicsDevice device) {
        if(device==null) throw new FdxException("TextureBlitter requires a device");
        this.device=device;
        try {
            shader=device.createShaderModule(ShaderModuleDescriptor.wgsl("texture composition",SOURCE));
            premultipliedShader=device.createShaderModule(ShaderModuleDescriptor.wgsl("premultiplied composition",PREMULTIPLIED_SOURCE));
            top=quad(TextureOrigin.TOP_LEFT); bottom=quad(TextureOrigin.BOTTOM_LEFT);
        } catch(RuntimeException | Error failure) {
            try { dispose(); } catch(RuntimeException | Error cleanup) { if(cleanup!=failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    /** Draws the resolved target color; rebuilds no region object. Premultiplied applies to RGB, never to origin. */
    public void draw(RenderPass pass, OffscreenTarget source, boolean premultiplied) {
        draw(pass,source.color(),source.origin(),premultiplied);
    }
    public void draw(RenderPass pass, Texture source, TextureOrigin origin, boolean premultiplied) {
        if(disposed) throw new FdxException("TextureBlitter disposed");
        if(pass==null || source==null || source.isDisposed() || !source.usage().sampled() || source.sampleCount()!=1) {
            throw new FdxException("TextureBlitter requires an active pass and a live resolved sampled texture");
        }
        if(origin==null || origin==TextureOrigin.UNKNOWN) throw new FdxException("TextureBlitter requires an explicit source origin");
        var layout=pass.compatibility().targetLayout();
        if(layout.colorAttachmentCount()!=1) throw new FdxException("TextureBlitter writes one color attachment");
        RenderPipeline pipeline=pipeline(layout,premultiplied);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(origin==TextureOrigin.TOP_LEFT?top:bottom);
        pass.setTexture(0,source);
        pass.draw(6,1,0,0);
    }
    private RenderPipeline pipeline(RenderTargetLayout layout,boolean premultiplied) {
        String key=layout.structuralKey();
        for(int i=0;i<count;i++) if(keys[i].equals(key) && premultipliedInputs[i]==premultiplied) return pipelines[i];
        RenderPipeline pipeline=device.createRenderPipeline(RenderPipelineDescriptor.shader(premultiplied?premultipliedShader:shader,layout.colorFormat(0))
                .renderTargetLayout(layout)
                .vertexLayout(VERTICES).sampledTextureCount(1).depthTestEnabled(false).depthWriteEnabled(false));
        if(count==pipelines.length) {
            int next=count*2;
            pipelines=Arrays.copyOf(pipelines,next); keys=Arrays.copyOf(keys,next);
            premultipliedInputs=Arrays.copyOf(premultipliedInputs,next);
        }
        keys[count]=key; premultipliedInputs[count]=premultiplied; pipelines[count++]=pipeline;
        return pipeline;
    }
    private Buffer quad(TextureOrigin origin) {
        Buffer buffer=device.createBuffer(BufferDescriptor.staticVertex("composition quad",6*16));
        try {
            float[] xy={-1,-1,1,-1,1,1,-1,-1,1,1,-1,1};
            ByteBuffer bytes=ByteBuffer.allocateDirect(6*16).order(ByteOrder.nativeOrder());
            for(int i=0;i<xy.length;i+=2) {
                float x=xy[i],y=xy[i+1];
                bytes.putFloat(x).putFloat(y).putFloat((x+1)*.5f).putFloat(origin.v((1-y)*.5f));
            }
            bytes.flip(); device.writeBuffer(buffer,bytes);
            return buffer;
        } catch(RuntimeException | Error failure) {
            try { buffer.dispose(); } catch(RuntimeException | Error cleanup) { if(failure!=cleanup) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public boolean isDisposed(){return disposed;}
    @Override public void dispose(){
        if(disposed)return; disposed=true;
        Throwable failure=null;
        for(int i=0;i<count;i++) failure=close(pipelines[i],failure);
        failure=close(top,failure); failure=close(bottom,failure); failure=close(shader,failure); failure=close(premultipliedShader,failure);
        if(failure instanceof RuntimeException runtime)throw runtime;
        if(failure instanceof Error error)throw error;
    }
    private static Throwable close(Disposable value,Throwable first) {
        try { if(value!=null)value.dispose(); }
        catch(RuntimeException | Error next) { if(first==null)return next; if(next!=first)first.addSuppressed(next); }
        return first;
    }
}
