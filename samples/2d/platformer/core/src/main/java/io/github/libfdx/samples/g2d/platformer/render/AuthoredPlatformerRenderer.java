package io.github.libfdx.samples.g2d.platformer.render;

import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.maps.TileTransform;
import io.github.libfdx.samples.g2d.platformer.*;

/** Draws borrowed map/assets and retained gameplay/UI state. All pass handles stay inside one frame. */
public final class AuthoredPlatformerRenderer {
    private static final LoadOp CLEAR=LoadOp.clear(.035f,.055f,.08f,1);
    private final SpriteBatch sprites;
    private final PlatformerView view;
    private final Texture white;
    private final TileMapRenderer tiles=new TileMapRenderer();
    private final RenderPassDescriptor descriptor=new RenderPassDescriptor().label("Platformer scene and HUD");
    private final PixelBatch batch;
    public AuthoredPlatformerRenderer(SpriteBatch sprites, PlatformerView view, Texture white) {
        this.sprites=sprites; this.view=view; this.white=white;
        batch=new PixelBatch(sprites,view.viewport());
    }
    public void render(GraphicsFrame frame, PlatformerLevelAsset level, PlatformerGame game,
            double seconds) {
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
            viewport.camera(0,0);
        } finally {
            try { if (begun) batch.end(); } finally { pass.end(); }
        }
    }

    private static final class PixelBatch implements Batch2D {
        private final SpriteBatch batch;
        private final PixelArtViewport view;
        PixelBatch(SpriteBatch batch,PixelArtViewport view) { this.batch=batch; this.view=view; }
        @Override
        public Batch2D viewport(int width,int height) { batch.viewport(width,height); return this; }
        @Override
        public Batch2D color(float r,float g,float b,float a) { batch.color(r,g,b,a); return this; }
        @Override
        public void begin() { batch.begin(); }
        @Override
        public void begin(LoadOp load) { batch.begin(load); }
        @Override
        public void begin(RenderPass pass) { batch.begin(pass); }
        @Override
        public void end() { batch.end(); }
        @Override
        public void draw(Texture texture,float x,float y,float w,float h) { batch.draw(texture,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h)); }
        @Override
        public void draw(Texture texture,int tx,int ty,int tw,int th,float x,float y,float w,float h) { batch.draw(texture,tx,ty,tw,th,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h)); }
        @Override
        public void draw(Texture texture,float x,float y,float w,float h,float ox,float oy,float rotation) { batch.draw(texture,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h),view.clipWidth(ox),view.clipHeight(oy),rotation); }
        @Override
        public void draw(TextureRegion region,float x,float y,float w,float h) { batch.draw(region,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h)); }
        @Override
        public void draw(TextureRegion region,float x,float y,float w,float h,float ox,float oy,float rotation) { draw(region,x,y,w,h,ox,oy,rotation,0); }
        @Override
        public void draw(TextureRegion region,float x,float y,float w,float h,float ox,float oy,float rotation,int flags) { batch.draw(region,view.clipX(x),view.clipY(y),view.clipWidth(w),view.clipHeight(h),view.clipWidth(ox),view.clipHeight(oy),rotation,flags); }
        @Override
        public void draw(TextureRegion region,float[] x,float[] y,int n,float w,float h,float ox,float oy,float rotation) { for(int i=0;i<n;i++) draw(region,x[i]-ox,y[i]-oy,w,h,ox,oy,rotation); }
        @Override
        public boolean isDisposed() { return batch.isDisposed(); }
        @Override
        public void dispose() { }
    }
}
