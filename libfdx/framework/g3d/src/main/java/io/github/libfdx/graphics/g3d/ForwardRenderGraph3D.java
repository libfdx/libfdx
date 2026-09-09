package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.core.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;

/**.
 * Small application-owned graph: an owned ModelBatch renders to an owned scene color/depth
 * target, ready for subsequent effects/presentation. resize() is explicit, so resolution may
 * differ from the window. The context, scene inputs and configured shader provider are borrowed.
 * No frame/pass survives render(). Dispose before the graphics context is destroyed.
 */
public final class ForwardRenderGraph3D implements RenderGraph3D {
    private final OffscreenTarget scene;
    private final ModelBatch batch;
    private final ForwardRenderPath3D path;
    private RenderTarget3D sceneView;
    private boolean disposed;
    public ForwardRenderGraph3D(GraphicsContext graphics) {
        this(graphics,new ModelBatchConfig(),TextureFormat.RGBA8_UNORM,1);
    }
    /** Requests exact scene color format and samples; unsupported capabilities fail, without silently reducing quality. */
    public ForwardRenderGraph3D(GraphicsContext graphics,ModelBatchConfig config,TextureFormat colorFormat,int samples) {
        if(graphics==null || config==null) throw new FdxException("Forward graph requires a context and batch configuration");
        scene=new OffscreenTarget(graphics.device(),colorFormat,TextureFormat.DEPTH32_FLOAT,samples,TextureFilter.LINEAR);
        try { batch=new ModelBatch(graphics,config); }
        catch(RuntimeException | Error failure){scene.dispose();throw failure;}
        path=new ForwardRenderPath3D(graphics,scene,true);
    }
    /** Atomically resizes scene resources. Borrowed color/target views from an earlier size become invalid. */
    public boolean resize(int width,int height) {
        ensureOpen();
        if(!scene.resize(width,height))return false;
        sceneView=new DefaultRenderTarget3D(width,height,new TextureView[]{scene.renderColor().view()},
                scene.sampleCount()==1?null:new TextureView[]{scene.color().view()},scene.depth().view());
        return true;
    }
    public ForwardRenderGraph3D clearColor(float r,float g,float b,float a){ensureOpen();scene.clearColor(r,g,b,a);return this;}
    /** Returns the borrowed scene attachment set; an unknown name or unsized target fails clearly. */
    @Override public RenderTarget3D target(String name){
        ensureOpen();
        if(!"scene".equals(name))throw new FdxException("Unknown forward render target: "+name);
        if(sceneView==null)throw new FdxException("Resize the forward graph before use");
        return sceneView;
    }
    /** Borrowed resolved color, ready to sample after render() returns. */
    public Texture color(){ensureOpen();return scene.color();}
    public TextureOrigin origin(){return scene.origin();}
    public long estimatedBytes(){return scene.estimatedBytes();}
    @Override public void render(Camera camera,Environment3D environment,ObjectIterable<? extends ModelInstance> instances){
        ensureOpen();path.render(batch,camera,environment,instances);
    }
    private void ensureOpen(){if(disposed)throw new FdxException("ForwardRenderGraph3D disposed");}
    @Override public boolean isDisposed(){return disposed;}
    @Override public void dispose(){
        if(disposed)return;disposed=true;
        path.dispose(); Throwable failure=null;
        try{batch.dispose();}catch(RuntimeException | Error next){failure=next;}
        try{scene.dispose();}catch(RuntimeException | Error next){if(failure==null)failure=next;else if(failure!=next)failure.addSuppressed(next);}
        sceneView=null;
        if(failure instanceof RuntimeException runtime)throw runtime;
        if(failure instanceof Error error)throw error;
    }
}
