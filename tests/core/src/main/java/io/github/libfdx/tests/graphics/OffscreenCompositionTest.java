package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.TextureBlitter;
import io.github.libfdx.graphics.shader.*;

/** Asymmetric translucent quadrants, explicit clear, stored depth, chained composition, resolve and repeated resize. */
public final class OffscreenCompositionTest extends GraphicsParityTest {
    private static final VertexLayout LAYOUT=VertexLayout.of(28,
            VertexAttribute.of(0,VertexFormat.FLOAT32X3,0),VertexAttribute.of(1,VertexFormat.FLOAT32X4,12));
    private static final String SOURCE="""
            struct Input { @location(0) position: vec3f, @location(1) color: vec4f };
            struct Output { @builtin(position) position: vec4f, @location(0) color: vec4f };
            @vertex fn vertexMain(input: Input) -> Output {
                var output: Output; output.position=vec4f(input.position,1.0); output.color=input.color; return output;
            }
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f { return input.color; }
            """;
    private OffscreenTarget regular, multisample, chain;
    private ShaderModule shader;
    private RenderPipeline one, four;
    private Buffer vertices;
    private TextureBlitter blitter;
    private final RenderPassDescriptor screen=new RenderPassDescriptor().label("offscreen presentation")
            .colorLoadOp(LoadOp.clear(.08f,.16f,.24f,1));
    private RenderPassDescriptor regularClear, multiClear;
    private int frames, resizeCount;
    public OffscreenCompositionTest(long frames){super(frames);}
    @Override public void create(Fdx fdx) {
        initialize(fdx,"OffscreenCompositionTest");
        regular=target(1); chain=new OffscreenTarget(graphics.device(),TextureFormat.RGBA8_UNORM,null,1,TextureFilter.NEAREST); chain.resize(128,96);
        var caps=graphics.device().capabilities();
        if(caps.supportsResolveFormat(TextureFormat.RGBA8_UNORM)
                && caps.supportsSampleCount(TextureFormat.DEPTH32_FLOAT,4)
                && caps.supportsSampleCount(TextureFormat.RGBA8_UNORM,4)) multisample=target(4);
        else logger.info("OffscreenCompositionTest MSAA NOT_RUN: format/sample resolve unsupported");
        shader=graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("offscreen reference",SOURCE));
        one=pipeline(1); if(multisample!=null)four=pipeline(4);
        float[] data=new float[36*7]; int i=0;
        i=quad(data,i,-1,0,0,1,.2f,1,0,0,1);
        i=quad(data,i,0,0,1,1,.2f,0,1,0,.5f);
        i=quad(data,i,-1,-1,0,0,.2f,0,0,1,.25f);
        i=quad(data,i,0,-1,1,0,.2f,1,1,0,.75f);
        i=quad(data,i,-1,-1,1,1,.8f,1,1,1,1);
        quad(data,i,-.25f,-.25f,.25f,.25f,.1f,1,1,1,1);
        vertices=graphics.device().createBuffer(BufferDescriptor.staticVertex("offscreen quadrants",data.length*4));
        graphics.device().writeBuffer(vertices,floats(data));
        blitter=new TextureBlitter(graphics.device());
        logger.info("OffscreenCompositionTest origin="+regular.origin()+", samples="+(multisample==null?1:4));
        markCreated();
    }
    private OffscreenTarget target(int samples){
        return new OffscreenTarget(graphics.device(),TextureFormat.RGBA8_UNORM,TextureFormat.DEPTH32_FLOAT,samples,TextureFilter.NEAREST);
    }
    private RenderPipeline pipeline(int samples){
        return graphics.device().createRenderPipeline(RenderPipelineDescriptor.shader(shader,TextureFormat.RGBA8_UNORM)
                .vertexLayout(LAYOUT).depthTestEnabled(true).depthWriteEnabled(true)
                .renderTargetLayout(RenderTargetLayout.of(new TextureFormat[]{TextureFormat.RGBA8_UNORM},TextureFormat.DEPTH32_FLOAT,samples)));
    }
    private RenderPassDescriptor clear(OffscreenTarget target) {
        Texture color=target.color(), render=target.renderColor();
        return new RenderPassDescriptor().label("explicit depth clear")
                .colorAttachments(color==render
                        ? RenderPassColorAttachment.of(render.view(),LoadOp.clear(0,0,0,0),StoreOp.store())
                        : RenderPassColorAttachment.resolve(render.view(),color.view(),LoadOp.clear(0,0,0,0),StoreOp.store()))
                .depthStencilAttachment(RenderPassDepthStencilAttachment.of(target.depth().view(),
                        LoadOp.clear(.7f,0,0,0),StoreOp.store(),LoadOp.load(),StoreOp.store()));
    }
    @Override public void render(){
        GraphicsFrame frame=graphics.currentFrame();
        int size=((frames/10)%3+1)*32;
        Texture old=frames==0?null:regular.color();
        if(regular.resize(size,size*3/4)){
            if(old!=null&&!old.isDisposed())throw new FdxException("Resize retained old color");
            regularClear=clear(regular); resizeCount++;
            if(multisample!=null){multisample.resize(size,size*3/4);multiClear=clear(multisample);}
        }
        scene(frame,regular,regularClear,one);
        if(multisample!=null)scene(frame,multisample,multiClear,four);
        RenderPass pass=chain.begin(frame,true); blitter.draw(pass,regular,true); pass.end();
        pass=frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        for(int row=0;row<2;row++)for(int column=0;column<2;column++){
            int x=32+320*column,y=40+208*row;
            pass.setViewport(x,y,256,192); pass.setScissor(x,y,256,192);
            blitter.draw(pass,row==0&&multisample!=null?multisample:column==0?regular:chain,true);
        }
        pass.end(); frames++; finishFrame();
    }
    private void scene(GraphicsFrame frame,OffscreenTarget target,RenderPassDescriptor clear,RenderPipeline pipeline){
        RenderPass pass=frame.commandEncoder().beginRenderPass(clear);
        pass.setPipeline(pipeline);pass.setVertexBuffer(vertices);
        // Far white must fail against the explicit .7 clear on both clip-depth ranges.
        pass.draw(6,1,24,0);pass.draw(24,1,0,0);pass.end();
        pass=target.begin(frame,false);pass.setPipeline(pipeline);pass.setVertexBuffer(vertices);
        // Far white must also fail against depth stored by the preceding pass.
        pass.draw(6,1,24,0);pass.draw(6,1,30,0);pass.end();
    }
    private static int quad(float[] data,int i,float l,float b,float r,float t,float z,float red,float green,float blue,float a){
        float[] xy={l,b,r,b,r,t,l,b,r,t,l,t};
        for(int j=0;j<xy.length;j+=2){data[i++]=xy[j];data[i++]=xy[j+1];data[i++]=z;
            data[i++]=red;data[i++]=green;data[i++]=blue;data[i++]=a;}
        return i;
    }
    @Override public void dispose(){
        dispose(blitter);dispose(vertices);dispose(one);dispose(four);dispose(shader);
        dispose(chain);dispose(multisample);dispose(regular);
        logger.info("OffscreenCompositionTest resize generations="+resizeCount);
        verifyDisposed();
    }
}
