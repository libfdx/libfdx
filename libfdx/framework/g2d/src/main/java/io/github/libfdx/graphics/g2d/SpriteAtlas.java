package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.loaders.AtlasData;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Texture;

/**
 * Application-thread atlas views over borrowed page textures. Construction copies
 * membership and verifies texture dimensions. Disposal invalidates views but never
 * disposes pages. When loaded by an asset manager, release the lease/scope instead;
 * the manager retains and disposes the page dependencies after the atlas.
 */
public final class SpriteAtlas implements Disposable {
    private final AtlasData data;
    private final Texture[] pages;
    private final AtlasRegion[] regions;
    private boolean disposed;

    public SpriteAtlas(AtlasData data,Texture[] pages) {
        if(data==null || pages==null || pages.length!=data.pageCount()) throw new FdxException("Atlas pages required");
        this.data=data; this.pages=pages.clone();
        for(int i=0;i<pages.length;i++) {
            Texture texture=this.pages[i]; AtlasData.Page page=data.page(i);
            if(texture==null || texture.isDisposed() || texture.width()!=page.width() || texture.height()!=page.height()) {
                throw new FdxException("Atlas texture size/lifetime disagrees with page "+page.image());
            }
        }
        regions=new AtlasRegion[data.spriteCount()];
        for(int i=0;i<regions.length;i++) {
            AtlasData.Sprite s=data.sprite(i);
            regions[i]=new AtlasRegion(this,s,new TextureRegion(this.pages[s.page()],s.x(),s.y(),s.width(),s.height()));
        }
    }
    public AtlasData data() { check(); return data; }
    public int size() { check(); return regions.length; }
    /** Borrowed page, valid until this atlas's owning asset scope releases it. */
    public Texture page(int index) { check(); return pages[index]; }
    public AtlasRegion region(int index) { check(); return regions[index]; }
    /** Borrowed view, or null if absent. Cache the view outside rendering loops. */
    public AtlasRegion find(String name) { check(); int i=data.indexOf(name); return i<0 ? null : regions[i]; }
    @Override
    public void dispose() { disposed=true; }
    @Override
    public boolean isDisposed() { return disposed; }
    void check() { if(disposed) throw new FdxException("SpriteAtlas is disposed"); }
}
