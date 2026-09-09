package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/** Mutable CPU tile data at an immutable signed tile origin. Cells use local x/right, y/up coordinates.
 * Owns a dense TileLayer; no graphics, files or lifetime management are involved. Confine mutations to one thread. */
public final class TileChunk {
    private final int x, y;
    private final TileLayer cells;

    public TileChunk(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0 || (long)x + width > (long)Integer.MAX_VALUE + 1
                || (long)y + height > (long)Integer.MAX_VALUE + 1) {
            throw new FdxException("Chunk dimensions must fit the signed tile coordinate domain");
        }
        this.x=x; this.y=y; cells=new TileLayer(width,height);
    }
    public int x(){return x;}
    public int y(){return y;}
    public int width(){return cells.width();}
    public int height(){return cells.height();}
    public long right(){return (long)x+width();}
    public long top(){return (long)y+height();}
    public int cellCount(){return cells.size();}
    public long revision(){return cells.revision();}
    public int tile(int x,int y){return cells.tile(x,y);}
    public int transform(int x,int y){return cells.transform(x,y);}
    public TileChunk tile(int x,int y,int id,int transform){cells.tile(x,y,id,transform);return this;}
    public TileChunk tile(int x,int y,int id){return tile(x,y,id,0);}
    public TileChunk fill(int id){cells.fill(id);return this;}
}
