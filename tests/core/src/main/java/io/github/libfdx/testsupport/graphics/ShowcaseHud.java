package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.graphics.shader.runtime.ShaderSkippedDraws;
import java.nio.ByteBuffer;

/** Small application-owned overlay; the font is borrowed from the scene's asset scope. */
public final class ShowcaseHud implements Disposable {
    private final SpriteBatch batch;
    private Texture white;
    private BitmapFont font;
    private float sx,sy,left,top;
    private boolean disposed;

    public ShowcaseHud(GraphicsContext graphics) {
        this(graphics, new SpriteBatchConfig().initialMaxSprites(1024));
    }

    /** The optional preparation/plan in this configuration is borrowed by the HUD batch. */
    public ShowcaseHud(GraphicsContext graphics, SpriteBatchConfig configuration) {
        batch=new SpriteBatch(graphics,configuration);
        try {
            white=graphics.device().createTexture(TextureDescriptor.rgba8("gallery solid",1,1));
            ByteBuffer pixel=ByteBuffer.allocateDirect(4);
            pixel.putInt(-1).flip();
            graphics.device().writeTexture(white,pixel);
        } catch (RuntimeException | Error failure) {
            try { dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public void font(BitmapFont font) { this.font=font; }
    public boolean hasFont() { return font!=null; }
    public void begin(RenderPass pass,int width,int height,float scale,float left,float top) {
        sx=2*scale/width; sy=2*scale/height;
        this.left=2*left/width-1; this.top=1-2*top/height;
        batch.viewport(width,height); batch.begin(pass);
    }
    public void rect(float x,float y,float width,float height,float r,float g,float b,float a) {
        batch.color(r,g,b,a);
        batch.draw(white,left+x*sx,top-(y+height)*sy,width*sx,height*sy);
    }
    public void text(String text,float x,float y,float scale,float r,float g,float b) {
        if(font==null) return;
        batch.color(r,g,b,1);
        float glyphScale=font.scale(12*scale);
        float capOffset=font.glyph('H').yOffset();
        int previous=-1;
        for(int i=0;i<text.length();) {
            int codePoint=text.codePointAt(i);
            BitmapFontGlyph glyph=font.glyph(codePoint);
            if(glyph!=null) {
                if(previous>=0) x+=font.kerning(previous,codePoint)*glyphScale;
                TextureRegion region=glyph.region();
                float gx=x+glyph.xOffset()*glyphScale;
                float gy=y+(glyph.yOffset()-capOffset)*glyphScale;
                float w=region.width()*glyphScale,h=region.height()*glyphScale;
                if(w>0 && h>0) batch.draw(region,left+gx*sx,top-(gy+h)*sy,w*sx,h*sy);
                x+=glyph.xAdvance()*glyphScale;
            }
            previous=codePoint;
            i+=Character.charCount(codePoint);
        }
    }
    private float textWidth(String text,float scale) { return font!=null ? font.width(text,12*scale) : 0; }
    private float textHeight(float scale) { return font!=null ? font.glyph('H').region().height()*font.scale(12*scale) : 0; }
    public void button(String label,float x,float y,float width,float height,boolean active) {
        rect(x,y,width,height,active ? .19f : .10f,active ? .49f : .15f,active ? .40f : .18f,1);
        rect(x,y+height-2,width,2,active ? .34f : .16f,active ? .88f : .24f,active ? .72f : .28f,1);
        float scale=Math.min(1.3f,(width-12)/Math.max(1,textWidth(label,1)));
        text(label,x+(width-textWidth(label,scale))/2,y+(height-textHeight(scale))/2,scale,.85f,.94f,.91f);
    }
    public void toggle(String label,float x,float y,float width,float height,boolean enabled) {
        text(label,x,y+(height-textHeight(1.25f))/2,1.25f,.64f,.76f,.78f);
        rect(x+width-38,y+8,38,16,.12f,.23f,.26f,1);
        rect(x+width-(enabled ? 17 : 35),y+11,14,10,enabled ? .34f : .39f,enabled ? .88f : .50f,enabled ? .72f : .54f,1);
    }
    public void end() { batch.end(); }
    public ShaderSkippedDraws skippedDrawsLastFrame() { return batch.skippedDrawsLastFrame(); }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if(disposed) return;
        disposed=true;
        try { font(null); }
        finally { try { batch.dispose(); } finally { if(white!=null) white.dispose(); } }
    }
}
