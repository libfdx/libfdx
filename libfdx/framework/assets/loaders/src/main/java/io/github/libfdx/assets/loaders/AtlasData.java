package io.github.libfdx.assets.loaders;

import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.core.FdxException;
import java.nio.charset.StandardCharsets;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;
import java.math.BigDecimal;

/**
 * Immutable CPU atlas metadata, independent of textures/devices. The version-one
 * .atlas.json format is UTF-8 JSON with straight alpha, unrotated
 * regions, image-space page coordinates and Y-up original-image trim/pivots.
 * Parsing is bounded to 4 MiB, 256 pages, 65,536 sprites and 64M page pixels.
 */
public final class AtlasData {
    public static final int VERSION=1;
    public static final int MAX_BYTES=4*1024*1024, MAX_PAGES=256, MAX_SPRITES=65536;
    public static final long MAX_PAGE_PIXELS=64L*1024*1024;
    private final Page[] pages;
    private final Sprite[] sprites;
    private final ObjectMap<String,Integer> names=new ObjectMap<>();

    public record Page(String image,int width,int height) {
        public Page {
            identifier(image);
            if(image.startsWith("/") || image.indexOf('\\')>=0 || image.indexOf(':')>=0) {
                throw invalid("Page image must be a relative portable path");
            }
            for(String part:image.split("/",-1)) if(part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw invalid("Page path cannot contain empty or dot components");
            }
            dimension(width); dimension(height);
        }
    }
    /** Trim x/y are offsets from the original image's bottom-left; pivots are normalized there. */
    public record Sprite(String name,int page,int x,int y,int width,int height,
            int originalWidth,int originalHeight,int trimX,int trimY,float pivotX,float pivotY) {
        public Sprite {
            identifier(name); dimension(width); dimension(height); dimension(originalWidth); dimension(originalHeight);
            if(page<0 || x<0 || y<0 || trimX<0 || trimY<0
                    || (long)trimX+width>originalWidth || (long)trimY+height>originalHeight
                    || !Float.isFinite(pivotX) || !Float.isFinite(pivotY) || pivotX<0 || pivotX>1 || pivotY<0 || pivotY>1) {
                throw invalid("Invalid sprite crop, original extent, or pivot: "+name);
            }
        }
    }
    /** Copies membership; records are immutable. Page references and duplicate names are validated. */
    public AtlasData(Page[] pages,Sprite[] sprites) {
        if(pages==null || sprites==null || pages.length>MAX_PAGES || sprites.length>MAX_SPRITES) {
            throw invalid("Atlas membership exceeds limits");
        }
        this.pages=pages.clone(); this.sprites=sprites.clone(); long pixels=0;
        for(Page page:this.pages) {
            if(page==null) throw invalid("Null page");
            pixels+=(long)page.width()*page.height();
            if(pixels>MAX_PAGE_PIXELS) throw invalid("Atlas page pixels exceed limit");
        }
        for(int i=0;i<this.sprites.length;i++) {
            Sprite sprite=this.sprites[i];
            if(sprite==null || sprite.page()>=pages.length) throw invalid("Invalid sprite page reference");
            Page page=pages[sprite.page()];
            if((long)sprite.x()+sprite.width()>page.width() || (long)sprite.y()+sprite.height()>page.height()) {
                throw invalid("Sprite exceeds its page: "+sprite.name());
            }
            if(names.containsKey(sprite.name())) throw invalid("Duplicate sprite name: "+sprite.name());
            names.put(sprite.name(),i);
        }
    }
    public int pageCount() { return pages.length; }
    public Page page(int index) { return pages[index]; }
    public int spriteCount() { return sprites.length; }
    public Sprite sprite(int index) { return sprites[index]; }
    /** Index or -1 when absent; cache the returned record/view for rendering. */
    public int indexOf(String name) { Integer index=names.get(name); return index==null ? -1 : index; }

    /**
     * Reads JSON only. Required fields are type checked; unknown properties at any
     * level are ignored for additive format evolution and are not retained by encode().
     * Incompatible versions and alpha conventions are rejected.
     */
    public static AtlasData parse(byte[] bytes) {
        if(bytes==null || bytes.length>MAX_BYTES) throw invalid("Atlas metadata exceeds 4 MiB");
        JsonValue root=new JsonReader().parse(bytes);
        if(integer(root,"version")!=VERSION || !root.requireString("alpha").equals("straight")) {
            throw invalid("Unsupported atlas version or alpha convention");
        }
        JsonValue pageValues=root.require("pages"), spriteValues=root.require("sprites");
        if(!pageValues.isArray() || !spriteValues.isArray()) throw invalid("Atlas pages and sprites must be arrays");
        if(pageValues.size()>MAX_PAGES || spriteValues.size()>MAX_SPRITES) throw invalid("Atlas membership exceeds limits");
        Page[] pages=new Page[pageValues.size()];
        Sprite[] sprites=new Sprite[spriteValues.size()];
        for(int i=0;i<pages.length;i++) {
            JsonValue page=pageValues.require(i);
            pages[i]=new Page(page.requireString("image"),integer(page,"width"),integer(page,"height"));
        }
        for(int i=0;i<sprites.length;i++) {
            JsonValue s=spriteValues.require(i);
            sprites[i]=new Sprite(s.requireString("name"),integer(s,"page"),integer(s,"x"),integer(s,"y"),
                    integer(s,"width"),integer(s,"height"),integer(s,"originalWidth"),integer(s,"originalHeight"),
                    integer(s,"trimX"),integer(s,"trimY"),s.require("pivotX").floatValue(),s.require("pivotY").floatValue());
        }
        return new AtlasData(pages,sprites);
    }
    /** Deterministic, indented JSON; allocation belongs to preparation/tooling. */
    public String encode() {
        JsonValue root=JsonValue.object().put("version",VERSION).put("alpha","straight");
        JsonValue pageValues=JsonValue.array(), spriteValues=JsonValue.array();
        root.put("pages",pageValues).put("sprites",spriteValues);
        for(Page page:pages) pageValues.add(JsonValue.object()
                .put("image",page.image()).put("width",page.width()).put("height",page.height()));
        for(Sprite s:sprites) spriteValues.add(JsonValue.object()
                .put("name",s.name()).put("page",s.page()).put("x",s.x()).put("y",s.y())
                .put("width",s.width()).put("height",s.height())
                .put("originalWidth",s.originalWidth()).put("originalHeight",s.originalHeight())
                .put("trimX",s.trimX()).put("trimY",s.trimY()).put("pivotX",s.pivotX()).put("pivotY",s.pivotY()));
        String result=JsonWriter.pretty(root)+"\n";
        if(result.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) throw invalid("Atlas metadata exceeds 4 MiB");
        return result;
    }
    private static int integer(JsonValue object,String name) {
        String literal=object.require(name).numberLiteral();
        try { return new BigDecimal(literal).intValueExact(); }
        catch(ArithmeticException | NumberFormatException error) {
            throw new FdxException("Atlas field '"+name+"' must be a 32-bit integer",error);
        }
    }
    private static void dimension(int value) { if(value<1 || value>16384) throw invalid("Atlas dimension must be 1..16384"); }
    private static void identifier(String value) {
        if(value==null || value.isEmpty() || value.length()>1024) throw invalid("Invalid atlas identifier");
        for(int i=0;i<value.length();i++) if(value.charAt(i)<32 || value.charAt(i)==127 || value.charAt(i)=='\ufffd') {
            throw invalid("Atlas identifiers cannot contain control or malformed UTF-8 characters");
        }
    }
    private static FdxException invalid(String message) { return new FdxException(message); }
}
