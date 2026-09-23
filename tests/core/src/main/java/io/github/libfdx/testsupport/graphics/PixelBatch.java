package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;

/** Sample-owned pixel-to-clip adapter; borrows the SpriteBatch and owns no resources. */
public final class PixelBatch implements Batch2D {
    private final SpriteBatch sprites;
    private float sx, sy;
    public PixelBatch(SpriteBatch sprites) { this.sprites = sprites; }
    @Override
    public Batch2D viewport(int width, int height) {
        sx = 2f / width; sy = 2f / height; sprites.viewport(width, height); return this;
    }
    @Override
    public Batch2D color(float r,float g,float b,float a) { sprites.color(r,g,b,a); return this; }
    @Override
    public void begin() { sprites.begin(); }
    @Override
    public void begin(LoadOp op) { sprites.begin(op); }
    @Override
    public void begin(RenderPass pass) { sprites.begin(pass); }
    @Override
    public void end() { sprites.end(); }
    @Override
    public void draw(Texture texture,float x,float y,float w,float h) { sprites.draw(texture,x*sx-1,y*sy-1,w*sx,h*sy); }
    @Override
    public void draw(Texture texture,float x,float y,float w,float h,float ox,float oy,float r) {
        sprites.draw(texture,x*sx-1,y*sy-1,w*sx,h*sy,ox*sx,oy*sy,r);
    }
    @Override
    public void draw(Texture texture,int tx,int ty,int tw,int th,float x,float y,float w,float h) {
        sprites.draw(texture,tx,ty,tw,th,x*sx-1,y*sy-1,w*sx,h*sy);
    }
    @Override
    public void draw(TextureRegion region,float x,float y,float w,float h) { sprites.draw(region,x*sx-1,y*sy-1,w*sx,h*sy); }
    @Override
    public void draw(TextureRegion region,float x,float y,float w,float h,float ox,float oy,float r) {
        sprites.draw(region,x*sx-1,y*sy-1,w*sx,h*sy,ox*sx,oy*sy,r);
    }
    @Override
    public void draw(TextureRegion region,float x,float y,float w,float h,float ox,float oy,float r,int flags) {
        sprites.draw(region,x*sx-1,y*sy-1,w*sx,h*sy,ox*sx,oy*sy,r,flags);
    }
    @Override
    public void draw(TextureRegion region,float[] x,float[] y,int n,float w,float h,float ox,float oy,float r) {
        for (int i=0;i<n;i++) { draw(region,x[i]-ox,y[i]-oy,w,h,ox,oy,r); }
    }
    @Override
    public void dispose() { }
    @Override
    public boolean isDisposed() { return sprites.isDisposed(); }
}
