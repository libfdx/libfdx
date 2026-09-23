package io.github.libfdx.graphics.effects;

import io.github.libfdx.core.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;

/**
 * Application-owned opaque-scene effects: exposure, optional Reinhard tone mapping, reduced-size bloom
 * and a five-tap contrast-triggered edge smoother. This is not temporal or geometry antialiasing.
 * Borrows the device and scene texture. Confined to the graphics thread; resize/dispose outside active passes.
 * Internal targets and shaders are owned; no frame/pass is retained. Pipeline warm-up may allocate.
 * Call resize with presentation dimensions, process before starting the presentation pass, then present and draw UI.
 * Disable any scene renderer tone mapping/exposure when this processor owns those operations.
 */
public final class PostProcessor implements Disposable {
    public enum ToneMapping { NONE, REINHARD }
    private static final String EXTRACT="""
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                var color=textureSample(source,sourceSampler,originUv(input.uv,params.values.x)).rgb;
                if(params.values.y>0.5) { color=decodeSrgb(color); }
                return vec4f(max(color-vec3f(params.values.z),vec3f(0.0)),1.0);
            }
            """;
    private static final String BLUR="""
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                let uv=originUv(input.uv,params.values.x);
                let step=params.values.zw;
                var color=textureSample(source,sourceSampler,uv).rgb*0.2270270270;
                color+=textureSample(source,sourceSampler,uv+step*1.3846153846).rgb*0.3162162162;
                color+=textureSample(source,sourceSampler,uv-step*1.3846153846).rgb*0.3162162162;
                color+=textureSample(source,sourceSampler,uv+step*3.2307692308).rgb*0.0702702703;
                color+=textureSample(source,sourceSampler,uv-step*3.2307692308).rgb*0.0702702703;
                return vec4f(color,1.0);
            }
            """;
    private static final String TONE="""
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                var scene=textureSample(source,sourceSampler,originUv(input.uv,params.inputs.x)).rgb;
                let glow=textureSample(auxiliary,auxiliarySampler,originUv(input.uv,params.inputs.y)).rgb;
                if(params.inputs.z>0.5) { scene=decodeSrgb(scene); }
                var color=max((scene+glow*params.values.y)*params.values.x,vec3f(0.0));
                if(params.values.z>0.5) { color=color/(vec3f(1.0)+color); }
                return vec4f(encodeSrgb(clamp(color,vec3f(0.0),vec3f(1.0))),1.0);
            }
            """;
    private static final String EDGE="""
            fn luma(c: vec3f) -> f32 { return dot(c,vec3f(0.2126,0.7152,0.0722)); }
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                let uv=originUv(input.uv,params.values.x);
                let x=vec2f(params.values.z,0.0); let y=vec2f(0.0,params.values.w);
                let center=textureSample(source,sourceSampler,uv).rgb;
                let west=textureSample(source,sourceSampler,uv-x).rgb;
                let east=textureSample(source,sourceSampler,uv+x).rgb;
                let north=textureSample(source,sourceSampler,uv-y).rgb;
                let south=textureSample(source,sourceSampler,uv+y).rgb;
                let hi=max(luma(center),max(max(luma(west),luma(east)),max(luma(north),luma(south))));
                let lo=min(luma(center),min(min(luma(west),luma(east)),min(luma(north),luma(south))));
                let filtered=center*0.5+(west+east+north+south)*0.125;
                return vec4f(select(center,filtered,hi-lo>=max(0.0312,hi*0.125)),1.0);
            }
            """;
    private static final String PRESENT="""
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                var color=textureSample(source,sourceSampler,originUv(input.uv,params.values.x)).rgb;
                if(params.values.y>0.5) { color=decodeSrgb(color); }
                return vec4f(color,1.0);
            }
            """;

    private final GraphicsDevice device;
    private final EffectQuality quality;
    private FullScreenEffect extract,blur,tone,edge,presentation;
    private ShaderParameterHandle extractValues,blurValues,toneInputs,toneValues,edgeValues,presentValues;
    private Targets targets;
    private float exposure=1,threshold,strength=.35f;
    private ToneMapping toneMapping=ToneMapping.REINHARD;
    private boolean disposed,processed;
    private int revision;

    /** Creates unsized resources for the exact preset. Unsupported requests fail, without silently falling back. */
    public PostProcessor(GraphicsDevice device,EffectQuality quality) {
        if(device==null || quality==null) throw new FdxException("PostProcessor requires a device and quality");
        quality.require(device.capabilities()); this.device=device; this.quality=quality;
        threshold=quality==EffectQuality.HIGH?1:.65f;
        try {
            if(quality.blurPairs()>0) {
                extract=new FullScreenEffect(device,"bloom extraction","values: vec4f,",EXTRACT,false);
                extractValues=extract.handle("values");
                blur=new FullScreenEffect(device,"bloom blur","values: vec4f,",BLUR,false);
                blurValues=blur.handle("values");
            }
            tone=new FullScreenEffect(device,"exposure tone mapping","inputs: vec4f, values: vec4f,",TONE,true);
            toneInputs=tone.handle("inputs"); toneValues=tone.handle("values");
            if(quality.edgeAntialiasing()) {
                edge=new FullScreenEffect(device,"edge smoothing","values: vec4f,",EDGE,false);
                edgeValues=edge.handle("values");
            }
            presentation=new FullScreenEffect(device,"effects presentation","values: vec4f,",PRESENT,false);
            presentValues=presentation.handle("values");
        } catch(RuntimeException | Error failure) { FullScreenEffect.close(this,failure); throw failure; }
    }

    /** Allocates all replacement targets before publishing them. Allocation failure preserves the previous output.
     * Equal dimensions reuse resources. Zero/minimized dimensions must be deferred by the caller. */
    public boolean resize(int width,int height) {
        ensureOpen();
        if(width<=0||height<=0) throw new FdxException("Post-processing dimensions must be positive");
        if(targets!=null&&targets.output.width()==width&&targets.output.height()==height) return false;
        Targets next=new Targets(device,quality,width,height),old=targets;
        targets=next; revision++; processed=false;
        FullScreenEffect.rethrow(FullScreenEffect.close(old,null)); return true;
    }
    /** Multiplier applied once after adding bloom. */
    public PostProcessor exposure(float value) {
        ensureOpen(); FullScreenEffect.nonnegative(value,"Exposure");
        if(value>64) throw new FdxException("Exposure must be at most 64");
        exposure=value; return this;
    }
    /** Per-channel linear threshold and nonnegative contribution, before exposure. LDR bloom clips at one.
     * Zero strength skips bloom rendering; allocation remains stable for later re-enabling. */
    public PostProcessor bloom(float threshold,float strength) {
        ensureOpen(); FullScreenEffect.nonnegative(threshold,"Bloom threshold"); FullScreenEffect.nonnegative(strength,"Bloom strength");
        if(strength>16) throw new FdxException("Bloom strength must be at most 16");
        this.threshold=threshold;this.strength=strength;return this;
    }
    public PostProcessor toneMapping(ToneMapping value) {
        ensureOpen();if(value==null)throw new FdxException("Tone mapping must be explicit");toneMapping=value;return this;
    }
    public EffectQuality quality(){return quality;}
    public int width(){return targets==null?0:targets.output.width();}
    public int height(){return targets==null?0:targets.output.height();}
    public int revision(){return revision;}
    /** Actual pass count for process with the current settings, excluding presentation and scene rendering. */
    public int passCount(){return 1+(edge==null?0:1)+(extract!=null&&strength>0?1+2*quality.blurPairs():0);}
    /** Unpadded target bytes; does not include the caller-owned scene, shader/driver metadata or retired in-flight allocations. */
    public long estimatedBytes(){return targets==null?0:targets.estimatedBytes();}
    public TextureOrigin origin(){return device.capabilities().renderedTextureOrigin();}

    /** Borrows a resolved opaque input; alpha is ignored. Output becomes available only after this call succeeds.
     * Input may use a different size (for render scale). No active writing pass may exist for input or internal targets. */
    public void process(GraphicsFrame frame,Texture scene,TextureOrigin sourceOrigin,ColorEncoding encoding) {
        ensureSized(); if(frame==null)throw new FdxException("Post-processing requires an active frame");
        boolean decode=FullScreenEffect.decode(scene,encoding);
        float sourceFlip=FullScreenEffect.flip(sourceOrigin), ownFlip=FullScreenEffect.flip(origin());
        if(targets.contains(scene))throw new FdxException("Post-processing input cannot alias its owned targets");
        processed=false;
        boolean bloom=extract!=null&&strength>0;
        Texture glow=scene;
        if(bloom) {
            extract.parameters.setFloat4(extractValues,sourceFlip,decode?1:0,threshold,0);
            extract.render(frame,targets.bloomA,scene,null);
            for(int i=0;i<quality.blurPairs();i++) {
                blur.parameters.setFloat4(blurValues,ownFlip,0,1f/targets.bloomA.width(),0);
                blur.render(frame,targets.bloomB,targets.bloomA.color(),null);
                blur.parameters.setFloat4(blurValues,ownFlip,0,0,1f/targets.bloomA.height());
                blur.render(frame,targets.bloomA,targets.bloomB.color(),null);
            }
            glow=targets.bloomA.color();
        }
        tone.parameters.setFloat4(toneInputs,sourceFlip,bloom?ownFlip:sourceFlip,decode?1:0,0);
        tone.parameters.setFloat4(toneValues,exposure,bloom?strength:0,toneMapping==ToneMapping.REINHARD?1:0,0);
        tone.render(frame,edge==null?targets.output:targets.toned,scene,glow);
        if(edge!=null) {
            edge.parameters.setFloat4(edgeValues,ownFlip,0,1f/width(),1f/height());
            edge.render(frame,targets.output,targets.toned.color(),null);
        }
        processed=true;
    }
    /** Borrowed encoded SRGB bytes in an RGBA8_UNORM texture, alpha one. Invalid after resize/dispose.
     * Use present for attachment-aware transfer, or declare ColorEncoding.SRGB when feeding another effect. */
    public Texture color() { ensureProcessed(); return targets.output.color(); }

    /** Draws over the caller's viewport/scissor; caller ends the pass and may draw UI afterward.
     * Encoded bytes go to normalized presentation formats; sRGB and float destinations receive linear RGB.
     * Does not apply another exposure or tone mapping. */
    public void present(RenderPass pass) {
        ensureProcessed();
        if(pass==null)throw new FdxException("Presentation requires an active pass");
        var layout=pass.compatibility().targetLayout();
        if(layout.colorAttachmentCount()!=1)throw new FdxException("Effects presentation writes one color attachment");
        TextureFormat format=layout.colorFormat(0);
        boolean linear=format.isSrgb()||format==TextureFormat.RGBA16_FLOAT||format==TextureFormat.R32_FLOAT;
        presentation.parameters.setFloat4(presentValues,FullScreenEffect.flip(origin()),linear?1:0,0,0);
        presentation.draw(pass,targets.output.color(),null);
    }
    private void ensureOpen(){if(disposed)throw new FdxException("PostProcessor disposed");}
    private void ensureSized(){ensureOpen();if(targets==null)throw new FdxException("Resize PostProcessor before use");}
    private void ensureProcessed(){ensureSized();if(!processed)throw new FdxException("Process a scene after creation or resize before reading the output");}
    @Override
    public boolean isDisposed(){return disposed;}
    @Override
    public void dispose() {
        if(disposed)return;disposed=true;processed=false;
        Throwable failure=FullScreenEffect.close(targets,null);targets=null;
        failure=FullScreenEffect.close(presentation,failure);failure=FullScreenEffect.close(edge,failure);
        failure=FullScreenEffect.close(tone,failure);failure=FullScreenEffect.close(blur,failure);
        failure=FullScreenEffect.close(extract,failure);FullScreenEffect.rethrow(failure);
    }

    private static final class Targets implements Disposable {
        private OffscreenTarget output,toned,bloomA,bloomB;
        private boolean disposed;
        Targets(GraphicsDevice device,EffectQuality quality,int w,int h) {
            try {
                output=allocate(device,TextureFormat.RGBA8_UNORM,w,h);
                if(quality.edgeAntialiasing())toned=allocate(device,TextureFormat.RGBA8_UNORM,w,h);
                if(quality.blurPairs()>0) {
                    int bw=Math.max(1,w/quality.bloomDivisor()),bh=Math.max(1,h/quality.bloomDivisor());
                    bloomA=allocate(device,quality.sceneFormat(),bw,bh);
                    bloomB=allocate(device,quality.sceneFormat(),bw,bh);
                }
            } catch(RuntimeException | Error failure) { FullScreenEffect.close(this,failure);throw failure; }
        }
        private static OffscreenTarget allocate(GraphicsDevice device,TextureFormat format,int w,int h) {
            OffscreenTarget target=new OffscreenTarget(device,format,null,1,TextureFilter.LINEAR);
            try { target.resize(w,h);return target; }
            catch(RuntimeException | Error failure) {FullScreenEffect.close(target,failure);throw failure;}
        }
        boolean contains(Texture texture) {
            return output.color()==texture || toned!=null&&toned.color()==texture
                    || bloomA!=null&&(bloomA.color()==texture||bloomB.color()==texture);
        }
        long estimatedBytes(){return output.estimatedBytes()+(toned==null?0:toned.estimatedBytes())
                +(bloomA==null?0:bloomA.estimatedBytes()+bloomB.estimatedBytes());}
        @Override
        public boolean isDisposed(){return disposed;}
        @Override
        public void dispose() {
            if(disposed)return;disposed=true;
            Throwable failure=FullScreenEffect.close(output,null);failure=FullScreenEffect.close(toned,failure);
            failure=FullScreenEffect.close(bloomA,failure);failure=FullScreenEffect.close(bloomB,failure);
            FullScreenEffect.rethrow(failure);
        }
    }
}
