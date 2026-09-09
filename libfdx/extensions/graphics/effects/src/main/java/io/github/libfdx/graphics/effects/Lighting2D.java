package io.github.libfdx.graphics.effects;

import io.github.libfdx.core.*;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;

/**
 * Application-owned ambient and bounded point lighting for an opaque 2D scene.
 * Borrows the device, source textures and active pass. Caller owns pass/viewport/end.
 * All calls are confined to the graphics thread; no frame allocation after pipeline warm-up.
 * RGB output is linear and alpha is one. Select a linear UNORM, sRGB or HDR destination accordingly.
 * Transparent layers and UI compose afterward. No occlusion shadows, specular or physical units are implied.
 */
public final class Lighting2D implements Disposable {
    public static final int MAX_LIGHTS=16;
    private static final String FRAGMENT="""
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                var albedo=textureSample(source,sourceSampler,originUv(input.uv,params.inputs.x)).rgb;
                if(params.inputs.z>0.5) { albedo=decodeSrgb(albedo); }
                let packedNormal=textureSample(auxiliary,auxiliarySampler,originUv(input.uv,params.inputs.y)).rgb;
                let vector=packedNormal*2.0-vec3f(1.0);
                let mappedNormal=vector/max(length(vector),0.00001);
                let normal=select(vec3f(0.0,0.0,1.0),mappedNormal,params.inputs.w>0.5);
                let world=vec2f(params.bounds.x+input.uv.x*params.bounds.z,
                        params.bounds.y+(1.0-input.uv.y)*params.bounds.w);
                var light=params.ambientCount.rgb;
                for(var i=0; i<16; i=i+1) {
                    if(f32(i)>=params.ambientCount.w) { break; }
                    let position=params.positions[i];
                    let direction=vec3f(position.xy-world,position.z);
                    let distance=length(direction);
                    let radial=max(1.0-distance/position.w,0.0);
                    let diffuse=max(dot(normal,direction/max(distance,0.00001)),0.0);
                    light+=params.colors[i].rgb*radial*radial*diffuse;
                }
                return vec4f(albedo*light,1.0);
            }
            """;
    private final FullScreenEffect effect;
    private final ShaderParameterHandle inputs,bounds,ambientCount;
    private final ShaderParameterHandle[] positions=new ShaderParameterHandle[MAX_LIGHTS], colors=new ShaderParameterHandle[MAX_LIGHTS];
    private float ambientR=1,ambientG=1,ambientB=1;
    private int lightCount;

    public Lighting2D(GraphicsDevice device) {
        effect=new FullScreenEffect(device,"2D point lighting",
                "inputs: vec4f, bounds: vec4f, ambientCount: vec4f, positions: array<vec4f,16>, colors: array<vec4f,16>,",FRAGMENT,true);
        try {
            inputs=effect.handle("inputs"); bounds=effect.handle("bounds"); ambientCount=effect.handle("ambientCount");
            for(int i=0;i<MAX_LIGHTS;i++) {
                positions[i]=effect.handle("positions["+i+"]"); colors[i]=effect.handle("colors["+i+"]");
                effect.parameters.setFloat4(positions[i],0,0,1,2);
            }
            bounds(0,0,1,1); ambient(1,1,1);
        } catch(RuntimeException | Error failure) { FullScreenEffect.close(effect,failure); throw failure; }
    }
    /** World rectangle mapped across the destination viewport; x/right and y/up. Width and height must be positive. */
    public Lighting2D bounds(float x,float y,float width,float height) {
        ensureOpen();
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0) {
            throw new FdxException("Lighting bounds must be finite with positive extent");
        }
        effect.parameters.setFloat4(bounds,x,y,width,height); return this;
    }
    /** Nonnegative linear RGB ambient multiplier. */
    public Lighting2D ambient(float r,float g,float b) {
        ensureOpen(); FullScreenEffect.nonnegative(r,"Ambient red"); FullScreenEffect.nonnegative(g,"Ambient green");
        FullScreenEffect.nonnegative(b,"Ambient blue");
        ambientR=r;ambientG=g;ambientB=b; updateAmbient(); return this;
    }
    /** Enables the first count configured light slots, including zero. Disabled slots retain their values. */
    public Lighting2D lightCount(int count) {
        ensureOpen(); if(count<0||count>MAX_LIGHTS) throw new FdxException("Light count must be in [0,"+MAX_LIGHTS+"]");
        lightCount=count; updateAmbient(); return this;
    }
    /** Configures a slot without changing lightCount. Position/radius/height use the bounds' world units.
     * Height must be positive; radius exceeds height to reach the scene plane. RGB is linear and intensity is a multiplier. */
    public Lighting2D light(int index,float x,float y,float height,float radius,float r,float g,float b,float intensity) {
        ensureOpen();
        if(index<0||index>=MAX_LIGHTS||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(height)
                ||!Float.isFinite(radius)||height<=0||radius<=height) throw new FdxException("Invalid 2D point light geometry or slot");
        FullScreenEffect.nonnegative(intensity,"Light intensity"); FullScreenEffect.nonnegative(r,"Light red");
        FullScreenEffect.nonnegative(g,"Light green"); FullScreenEffect.nonnegative(b,"Light blue");
        FullScreenEffect.nonnegative(r*intensity,"Light red intensity"); FullScreenEffect.nonnegative(g*intensity,"Light green intensity");
        FullScreenEffect.nonnegative(b*intensity,"Light blue intensity");
        effect.parameters.setFloat4(positions[index],x,y,height,radius);
        effect.parameters.setFloat4(colors[index],r*intensity,g*intensity,b*intensity,0); return this;
    }
    public void draw(RenderPass pass,Texture source,TextureOrigin origin,ColorEncoding encoding) {
        draw(pass,source,origin,encoding,null,TextureOrigin.TOP_LEFT);
    }
    /** Optional normal texture: screen-aligned RGB encodes world +X,+Y,+Z as (normal+1)/2;
     * declare its row origin separately. No color transfer is applied; sRGB normals are rejected.
     * Null normals use a flat +Z surface. Inputs must not alias an active attachment. */
    public void draw(RenderPass pass,Texture source,TextureOrigin origin,ColorEncoding encoding,
            Texture normals,TextureOrigin normalOrigin) {
        ensureOpen();
        float sourceFlip=FullScreenEffect.flip(origin), normalFlip=0;
        boolean decode=FullScreenEffect.decode(source,encoding);
        if(normals!=null) {
            FullScreenEffect.requireTexture(normals);
            normalFlip=FullScreenEffect.flip(normalOrigin);
            if(normals.format().isSrgb()) throw new FdxException("Normal maps must use a linear texture format");
        }
        effect.parameters.setFloat4(inputs,sourceFlip,normalFlip,decode?1:0,normals==null?0:1);
        effect.draw(pass,source,normals==null?source:normals);
    }
    private void updateAmbient() { effect.parameters.setFloat4(ambientCount,ambientR,ambientG,ambientB,lightCount); }
    private void ensureOpen() { if(isDisposed())throw new FdxException("Lighting2D disposed"); }
    @Override public boolean isDisposed(){return effect.isDisposed();}
    @Override public void dispose(){effect.dispose();}
}
