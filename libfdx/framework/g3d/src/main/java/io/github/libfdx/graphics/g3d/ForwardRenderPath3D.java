package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.OffscreenTarget;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.camera.Camera;

/** One forward scene pass. Borrows the context, target, batch, camera, environment and instances.
 * The target must be sized before rendering. No frame/pass is retained and disposal releases no borrowed resources. */
public final class ForwardRenderPath3D implements RenderPath3D {
    private final GraphicsContext graphics;
    private final OffscreenTarget target;
    private final boolean clear;
    private boolean disposed;
    /** clear=false preserves both existing scene color and depth for an additional forward pass. */
    public ForwardRenderPath3D(GraphicsContext graphics, OffscreenTarget target, boolean clear) {
        if (graphics==null || target==null) throw new FdxException("Forward path requires a context and target");
        this.graphics=graphics; this.target=target; this.clear=clear;
    }
    @Override public void render(Batch3D batch, Camera camera, Environment3D environment,
            ObjectIterable<? extends ModelInstance> instances) {
        if(disposed) throw new FdxException("ForwardRenderPath3D disposed");
        if(batch==null || camera==null || instances==null) throw new FdxException("Forward scene inputs cannot be null");
        RenderPass pass=target.begin(graphics.currentFrame(),clear);
        Throwable failure=null; boolean begun=false;
        try {
            batch.environment(environment); batch.begin(pass,camera); begun=true; batch.render(instances);
        } catch(RuntimeException | Error next){failure=next;}
        if(begun) try {batch.end();} catch(RuntimeException | Error next){failure=append(failure,next);}
        try {pass.end();} catch(RuntimeException | Error next){failure=append(failure,next);}
        if(failure instanceof RuntimeException runtime) throw runtime;
        if(failure instanceof Error error) throw error;
    }
    private static Throwable append(Throwable first,Throwable next){
        if(first==null)return next; if(first!=next)first.addSuppressed(next); return first;
    }
    @Override public boolean isDisposed(){return disposed;}
    @Override public void dispose(){disposed=true;}
}
