package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.maps.TileTransform;
import io.github.libfdx.samples.g2d.platformer.*;
import io.github.libfdx.samples.g2d.platformer.input.BackendPlatformerInput;

/** Draws borrowed map/assets and retained gameplay/UI state. All pass handles stay inside one frame. */
public final class AuthoredPlatformerRenderer {
    private static final LoadOp CLEAR=LoadOp.clear(.035f,.055f,.08f,1);
    private final SpriteBatch sprites;
    private final PlatformerView view;
    private final Texture white;
    private final BitmapFont font;
    private final TileMapRenderer tiles=new TileMapRenderer();
    private final RenderPassDescriptor descriptor=new RenderPassDescriptor().label("Platformer scene and HUD");
    private final PixelBatch batch;
    public AuthoredPlatformerRenderer(SpriteBatch sprites, PlatformerView view, Texture white, BitmapFont font) {
        this.sprites=sprites; this.view=view; this.white=white; this.font=font;
        batch=new PixelBatch(sprites,view.viewport());
    }
    public void render(GraphicsFrame frame, PlatformerLevelAsset level, PlatformerGame game,
            PlatformerHud hud, BackendPlatformerInput controls, double seconds) {
        PixelArtViewport viewport=view.viewport();
        RenderPass pass=frame.commandEncoder().beginRenderPass(descriptor.colorAttachment(frame.colorAttachment())
                .colorLoadOp(CLEAR).colorStoreOp(StoreOp.store()));
        boolean begun=false;
        try {
            viewport.apply(pass); batch.viewport(frame.width(),frame.height()); batch.begin(pass); begun=true;
            if (level!=null && game!=null) {
                float camera=game.cameraX()*PlatformerLevel.PIXELS_PER_UNIT;
                viewport.camera(camera,0); level.map().tiles().animationTime((long)(seconds*1000));
                tiles.render(level.map().map(),level.map().tiles(),batch,0,0,camera,0,300,180);
                for (int layer=PlatformerConstants.LAYER_ITEM;layer<=PlatformerConstants.LAYER_PLAYER;layer++) {
                    for (int i=0;i<game.spriteCount();i++) {
                        PlatformerGame.Sprite sprite=game.spriteAt(i);
                        if (sprite.layer()!=layer || sprite.collected() || sprite.regionId()<0) continue;
                        TextureRegion region=level.map().tiles().region(sprite.regionId()+1);
                        float x=PlatformerLevel.pixel(sprite.x()),y=PlatformerLevel.pixel(sprite.y());
                        float width=Math.round(sprite.halfWidth()*300),height=Math.round(sprite.halfHeight()*300);
                        if (x+width/2<camera || x-width/2>camera+300) continue;
                        if (layer==PlatformerConstants.LAYER_ITEM) y+=Math.round(Math.sin(seconds*4+i)*2);
                        if (sprite==game.player() && game.playerOnGround() && Math.abs(game.playerVelocityX())>.001
                                && ((int)(seconds*9)&1)==0) region=level.map().tiles().region(PlatformerConstants.REGION_PLAYER_IDLE+1);
                        int flip=sprite==game.player() && !game.playerFacingRight() ? TileTransform.FLIP_X : 0;
                        if (layer==PlatformerConstants.LAYER_PLAYER || layer==PlatformerConstants.LAYER_ENEMY) {
                            batch.color(.03f,.06f,.08f,.2f); batch.draw(white,x-width*.45f,y-height/2-1,width*.9f,2);
                        }
                        batch.color(1,1,1,1); batch.draw(region,x-width/2,y-height/2,width,height,flip);
                    }
                }
            }
            viewport.camera(0,0); drawHud(hud,controls);
        } finally {
            try { if (begun) batch.end(); } finally { pass.end(); }
        }
    }
    private void drawHud(PlatformerHud hud,BackendPlatformerInput controls) {
        panel(6,152,288,22,.08f,.13f,.18f,.93f);
        text(hud.title,12,160,.99f,.88f,.61f); text(hud.score,160,160,.85f,.92f,.87f);
        button("MENU",258,156,34,16,hud.menuOpen);
        if (!hud.menuOpen && !hud.loading && !hud.failed) {
            button("<",8,8,36,28,controls.leftDown()); button(">",50,8,36,28,controls.rightDown());
            button("JUMP",242,8,50,28,controls.jumpDown());
            panel(94,8,140,16,.08f,.13f,.18f,.75f);
            centered(hud.audioLocked ? "PRESS A KEY FOR SOUND" : hud.audioAvailable ? "A/D MOVE  SPACE JUMP" : "SILENT MODE",164,12,.83f,.9f,.9f);
        }
        if (hud.menuOpen || hud.loading || hud.failed) {
            panel(0,0,300,152,.03f,.05f,.09f,.62f);
            panel(47,39,206,101,.36f,.52f,.55f,1);
            panel(49,41,202,97,.07f,.12f,.17f,1);
            centered(hud.heading,150,123,1,.87f,.57f);
            if (hud.loading || hud.failed) {
                centered(hud.status,150,96,.87f,.94f,.93f);
                centered(hud.failed ? "R RETRY  P RETURN" : "PREPARING YOUR TRAIL",150,73,.62f,.78f,.81f);
            } else {
                button("RESUME",62,92,80,18,false); button("RESTART",158,92,80,18,false);
                button("NEXT TRAIL",62,68,176,18,false);
                button("-",62,46,24,18,false); button("+",180,46,24,18,false); button("M",212,46,26,18,hud.volume==0);
                for (int i=0;i<10;i++) panel(94+i*8,52,5,6,i<hud.volume?.98f:.21f,i<hud.volume?.76f:.3f,.32f,1);
                centered(hud.audioAvailable ? "VOLUME: Q/E   M MUTE" : "NO AUDIO PROVIDER",150,26,.73f,.84f,.85f);
            }
        }
    }
    private void button(String text,float x,float y,float width,float height,boolean active) {
        panel(x,y,width,height,.22f,.37f,.39f,.96f);
        panel(x+1,y+1,width-2,height-3,active?.46f:.1f,active?.48f:.22f,active?.27f:.25f,1);
        centered(text,x+width/2,y+(height-8)/2,1,.93f,.73f);
    }
    private void panel(float x,float y,float width,float height,float r,float g,float b,float a) {
        batch.color(r,g,b,a); batch.draw(white,x,y,width,height);
    }
    private void centered(String text,float x,float y,float r,float g,float b) { text(text,x-text.length()*3,y,r,g,b); }
    private void text(String text,float x,float y,float r,float g,float b) {
        batch.color(r,g,b,1);
        for (int i=0;i<text.length();i++) {
            BitmapFontGlyph glyph=font.glyph(text.charAt(i));
            if (glyph!=null) batch.draw(glyph.region(),x,y,6,8);
            x+=6;
        }
    }
    private static final class PixelBatch implements Batch2D {
        private final SpriteBatch batch;
        private final PixelArtViewport view;
        PixelBatch(SpriteBatch batch,PixelArtViewport view) { this.batch=batch; this.view=view; }
        @Override public Batch2D viewport(int width,int height) { batch.viewport(width,height); return this; }
        @Override public Batch2D color(float r,float g,float b,float a) { batch.color(r,g,b,a); return this; }
        @Override public void begin() { batch.begin(); }
        @Override public void begin(LoadOp load) { batch.begin(load); }
        @Override public void begin(RenderPass pass) { batch.begin(pass); }
        @Override public void end() { batch.end(); }
        @Override public void draw(Texture texture,float x,float y,float w,float h) { batch.draw(texture,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h)); }
        @Override public void draw(Texture texture,int tx,int ty,int tw,int th,float x,float y,float w,float h) { batch.draw(texture,tx,ty,tw,th,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h)); }
        @Override public void draw(Texture texture,float x,float y,float w,float h,float ox,float oy,float rotation) { batch.draw(texture,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h),view.clipWidth(ox),view.clipHeight(oy),rotation); }
        @Override public void draw(TextureRegion region,float x,float y,float w,float h) { batch.draw(region,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h)); }
        @Override public void draw(TextureRegion region,float x,float y,float w,float h,float ox,float oy,float rotation) { draw(region,x,y,w,h,ox,oy,rotation,0); }
        @Override public void draw(TextureRegion region,float x,float y,float w,float h,float ox,float oy,float rotation,int flags) { batch.draw(region,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h),view.clipWidth(ox),view.clipHeight(oy),rotation,flags); }
        @Override public void draw(TextureRegion region,float[] x,float[] y,int n,float w,float h,float ox,float oy,float rotation) { for(int i=0;i<n;i++) draw(region,x[i]-ox,y[i]-oy,w,h,ox,oy,rotation); }
        @Override public boolean isDisposed() { return batch.isDisposed(); }
        @Override public void dispose() { }
    }
}
