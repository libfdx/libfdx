package io.github.libfdx.graphics.effects;

import io.github.libfdx.core.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.*;
import io.github.libfdx.graphics.shader.reflection.*;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Package-private reusable resources; no frame/pass survives a call. */
final class FullScreenEffect implements Disposable {
    static final String PRELUDE = """
            struct Input { @location(0) position: vec2f, @location(1) uv: vec2f };
            struct Output { @builtin(position) position: vec4f, @location(0) uv: vec2f };
            @group(0) @binding(0) var source: texture_2d<f32>;
            @group(0) @binding(1) var sourceSampler: sampler;
            @vertex fn vertexMain(input: Input) -> Output {
                var output: Output; output.position=vec4f(input.position,0.0,1.0); output.uv=input.uv; return output;
            }
            fn originUv(uv: vec2f, flip: f32) -> vec2f {
                return vec2f(uv.x, mix(uv.y, 1.0-uv.y, flip));
            }
            fn decodeSrgb(value: vec3f) -> vec3f {
                let c=max(value,vec3f(0.0));
                return select(pow((c+vec3f(0.055))/1.055, vec3f(2.4)), c/12.92, c<=vec3f(0.04045));
            }
            fn encodeSrgb(value: vec3f) -> vec3f {
                let c=max(value,vec3f(0.0));
                return select(1.055*pow(c,vec3f(1.0/2.4))-vec3f(0.055), c*12.92, c<=vec3f(0.0031308));
            }
            """;
    static final String SECOND = """
            @group(0) @binding(2) var auxiliary: texture_2d<f32>;
            @group(0) @binding(3) var auxiliarySampler: sampler;
            """;
    private static final VertexLayout LAYOUT=VertexLayout.of(16,
            VertexAttribute.of(0,VertexFormat.FLOAT32X2,0),VertexAttribute.of(1,VertexFormat.FLOAT32X2,8));
    final GraphicsDevice device;
    final ShaderParameterBlock parameters;
    private ShaderModule shader;
    private Buffer vertices;
    private RenderPipeline[] pipelines=new RenderPipeline[4];
    private String[] keys=new String[4];
    private int pipelineCount;
    private final boolean second;
    private boolean disposed;

    FullScreenEffect(GraphicsDevice device,String label,String parameterFields,String fragment,boolean second) {
        if(device==null) throw new FdxException("Effects require a graphics device");
        this.device=device; this.second=second;
        try {
            shader=device.createShaderModule(ShaderModuleDescriptor.wgsl(label, PRELUDE + (second?SECOND:"")
                    + "struct Parameters { " + parameterFields + " };\n"
                    + "@group(1) @binding(0) var<uniform> params: Parameters;\n" + fragment));
            parameters=ShaderParameterBlock.allocate(shader.reflection().requireBinding(1,0).bufferLayout());
            vertices=device.createBuffer(BufferDescriptor.staticVertex(label+" quad",96));
            float[] xy={-1,-1,1,-1,1,1,-1,-1,1,1,-1,1};
            ByteBuffer data=ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
            for(int i=0;i<xy.length;i+=2) data.putFloat(xy[i]).putFloat(xy[i+1])
                    .putFloat((xy[i]+1)*.5f).putFloat((1-xy[i+1])*.5f);
            data.flip(); device.writeBuffer(vertices,data);
        } catch(RuntimeException | Error failure) {
            close(this,failure); throw failure;
        }
    }
    ShaderParameterHandle handle(String name) { return parameters.layout().requireHandle(name); }
    void draw(RenderPass pass,Texture input,Texture auxiliary) {
        if(disposed) throw new FdxException("Effect disposed");
        requireTexture(input);
        if(second) requireTexture(auxiliary);
        if(pass==null) throw new FdxException("Effect requires an active destination pass");
        RenderTargetLayout layout=pass.compatibility().targetLayout();
        if(layout.colorAttachmentCount()!=1) throw new FdxException("Effect writes one color attachment");
        RenderPipeline pipeline=pipeline(layout);
        pass.setPipeline(pipeline); pass.setVertexBuffer(vertices);
        pass.setTexture(0,input); if(second) pass.setTexture(1,auxiliary);
        pass.setParameterBlock(1,0,parameters);
        pass.draw(6,1,0,0);
    }
    void render(GraphicsFrame frame,OffscreenTarget target,Texture input,Texture auxiliary) {
        RenderPass pass=target.begin(frame,false);
        Throwable failure=null;
        try { draw(pass,input,auxiliary); }
        catch(RuntimeException | Error error) { failure=error; throw error; }
        finally { end(pass,failure); }
    }
    private RenderPipeline pipeline(RenderTargetLayout layout) {
        String key=layout.structuralKey();
        for(int i=0;i<pipelineCount;i++) if(keys[i].equals(key)) return pipelines[i];
        if(pipelineCount==pipelines.length) {
            pipelines=Arrays.copyOf(pipelines,pipelineCount*2); keys=Arrays.copyOf(keys,pipelineCount*2);
        }
        RenderPipeline next=device.createRenderPipeline(RenderPipelineDescriptor.shader(shader,layout.colorFormat(0))
                .renderTargetLayout(layout).colorTargets(ColorTargetState.opaque(layout.colorFormat(0)))
                .vertexLayout(LAYOUT).sampledTextureCount(second?2:1).depthTestEnabled(false).depthWriteEnabled(false));
        keys[pipelineCount]=key; pipelines[pipelineCount++]=next; return next;
    }
    static void requireTexture(Texture texture) {
        if(texture==null || texture.isDisposed() || texture.sampleCount()!=1 || !texture.usage().sampled()
                || !texture.format().isColor() || texture.format()==TextureFormat.R32_FLOAT) {
            throw new FdxException("Effect requires a live resolved sampled RGBA color texture");
        }
    }
    static float flip(TextureOrigin origin) {
        if(origin==null || origin==TextureOrigin.UNKNOWN) throw new FdxException("Effect requires an explicit texture origin");
        return origin==TextureOrigin.BOTTOM_LEFT?1:0;
    }
    static boolean decode(Texture texture,ColorEncoding encoding) {
        requireTexture(texture);
        if(encoding==null) throw new FdxException("Effect requires an explicit input color encoding");
        return encoding.decode(texture);
    }
    static float nonnegative(float value,String name) {
        if(!Float.isFinite(value) || value<0) throw new FdxException(name+" must be finite and nonnegative");
        return value;
    }
    static void end(RenderPass pass,Throwable original) {
        try { pass.end(); }
        catch(RuntimeException | Error error) { if(original==null) throw error; if(error!=original)original.addSuppressed(error); }
    }
    static Throwable close(Disposable value,Throwable original) {
        try { if(value!=null)value.dispose(); }
        catch(RuntimeException | Error error) { if(original==null)return error; if(error!=original)original.addSuppressed(error); }
        return original;
    }
    static void rethrow(Throwable failure) {
        if(failure instanceof RuntimeException runtime)throw runtime;
        if(failure instanceof Error error)throw error;
    }
    @Override
    public boolean isDisposed(){return disposed;}
    @Override
    public void dispose() {
        if(disposed)return; disposed=true;
        Throwable failure=null;
        for(int i=0;i<pipelineCount;i++)failure=close(pipelines[i],failure);
        failure=close(vertices,failure); failure=close(shader,failure); rethrow(failure);
    }
}
