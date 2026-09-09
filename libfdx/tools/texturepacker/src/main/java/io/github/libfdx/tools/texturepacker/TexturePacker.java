package io.github.libfdx.tools.texturepacker;

import io.github.libfdx.assets.loaders.AtlasData;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Deterministic, unrotated shelf packing of PNG sprites. Input dimensions are
 * checked before decode (16M aggregate pixels); output is limited to 64M pixels.
 * Input RGB is straight alpha. Transparent RGB bleed preserves alpha; extrusion
 * copies edge texels, then transparent padding separates neighbors. No mipmaps.
 */
public final class TexturePacker {
    private static final long MAX_INPUT_PIXELS=16L*1024*1024;
    private TexturePacker() { }

    /**
     * Generates pages and metadata under the asset path and returns the metadata
     * file. All input/layout errors precede publication. Files are staged and the
     * metadata is replaced last; this is not an atomic live-reload transaction.
     * Only old pages named by this atlas's prior valid metadata are removed.
     */
    public static Path generate(AtlasSpec spec) throws IOException {
        Path source=spec.sourceDirectory().toRealPath();
        if(!Files.isDirectory(source)) throw new IOException("Atlas source is not a directory");
        ArrayList<Path> files=new ArrayList<>();
        try(var walk=Files.walk(source)) {
            var iterator=walk.iterator();
            while(iterator.hasNext()) {
                Path file=iterator.next();
                if(Files.isSymbolicLink(file)) throw new IOException("Atlas input cannot contain symbolic links: "+file);
                if(Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                    if(files.size()==AtlasData.MAX_SPRITES) throw new IOException("Too many input sprites");
                    files.add(file);
                }
            }
        }
        if(files.isEmpty()) throw new IOException("No PNG sprites in "+source);
        files.sort(Comparator.comparing(path -> source.relativize(path).toString()));
        ArrayList<Sprite> sprites=new ArrayList<>(); Set<String> names=new HashSet<>(); long inputPixels=0;
        for(Path file:files) {
            try(ImageInputStream input=ImageIO.createImageInputStream(file.toFile())) {
                Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new IOException("Unreadable PNG: "+file);
                ImageReader reader=readers.next();
                try {
                    if(!reader.getFormatName().equalsIgnoreCase("png")) throw new IOException("Expected PNG: "+file);
                    reader.setInput(input,true,true);
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    inputPixels+=(long)width*height;
                    if(width<1 || height<1 || width>16384 || height>16384 || inputPixels>MAX_INPUT_PIXELS) {
                        throw new IOException("Atlas inputs exceed dimensions or 16M pixel budget");
                    }
                    String relative=source.relativize(file).toString().replace('\\','/');
                    String name=relative.substring(0,relative.length()-4);
                    Properties overrides=new Properties();
                    Path sidecar=file.resolveSibling(file.getFileName().toString().substring(0,file.getFileName().toString().length()-4)+".sprite.properties");
                    if(Files.exists(sidecar)) {
                        if(Files.size(sidecar)>16384) throw new IOException("Sprite properties exceed 16 KiB: "+sidecar);
                        try(var text=Files.newBufferedReader(sidecar,StandardCharsets.UTF_8)) { overrides.load(text); }
                        for(String key:overrides.stringPropertyNames()) if(!Set.of("name","pivotX","pivotY").contains(key)) {
                            throw new IOException("Unknown sprite property: "+key);
                        }
                    }
                    name=overrides.getProperty("name",name);
                    float px=Float.parseFloat(overrides.getProperty("pivotX",Float.toString(spec.pivotX())));
                    float py=Float.parseFloat(overrides.getProperty("pivotY",Float.toString(spec.pivotY())));
                    new AtlasData.Sprite(name,0,0,0,1,1,width,height,0,0,px,py);
                    if(!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate or case-colliding sprite name: "+name);
                    BufferedImage image=reader.read(0);
                    try { sprites.add(crop(name,image,width,height,px,py,spec)); }
                    finally { image.flush(); }
                } finally { reader.dispose(); }
            }
        }
        sprites.sort(Comparator.<Sprite>comparingInt(s -> s.height).reversed()
                .thenComparing(Comparator.<Sprite>comparingInt(s -> s.width).reversed()).thenComparing(s -> s.name));
        ArrayList<Page> pages=pack(sprites,spec);
        AtlasData.Page[] pageData=new AtlasData.Page[pages.size()];
        for(int i=0;i<pages.size();i++) pageData[i]=new AtlasData.Page(pageName(spec,i),power2(pages.get(i).usedWidth),power2(pages.get(i).usedHeight));
        // Canonical metadata order is name order, independent of shelf placement.
        sprites.sort(Comparator.comparing(s -> s.name));
        AtlasData.Sprite[] regions=new AtlasData.Sprite[sprites.size()];
        for(int i=0;i<regions.length;i++) {
            Sprite s=sprites.get(i);
            regions[i]=new AtlasData.Sprite(s.name,s.page,s.x,s.y,s.width,s.height,s.originalWidth,s.originalHeight,s.trimX,s.trimY,s.pivotX,s.pivotY);
        }
        byte[] metadata=new AtlasData(pageData,regions).encode().getBytes(StandardCharsets.UTF_8);
        Path root=spec.outputDirectory();
        rejectLinks(root);
        Files.createDirectories(root);
        Path realRoot=root.toRealPath();
        if(realRoot.startsWith(source) || source.startsWith(realRoot)) throw new IOException("Resolved source and output overlap");
        Path output=root.resolve(spec.assetPath()).normalize();
        if(!output.startsWith(root)) throw new IOException("Atlas output escapes root");
        rejectLinks(output); Files.createDirectories(output);
        Path target=output.resolve(spec.name()+".atlas.json");
        List<Path> oldPages=oldPages(target,spec);
        Path staging=Files.createTempDirectory(output,".atlas-");
        ArrayList<Path> staged=new ArrayList<>();
        try {
            for(int i=0;i<pages.size();i++) {
                AtlasData.Page p=pageData[i];
                BufferedImage image=new BufferedImage(p.width(),p.height(),BufferedImage.TYPE_INT_ARGB);
                try {
                    for(Sprite sprite:sprites) if(sprite.page==i) draw(image,sprite,spec.extrusion());
                    Path file=staging.resolve(p.image()); staged.add(file);
                    if(!ImageIO.write(image,"png",file.toFile())) throw new IOException("PNG writer unavailable");
                } finally { image.flush(); }
            }
            Path text=staging.resolve(target.getFileName()); staged.add(text); Files.write(text,metadata);
            for(Path file:staged) {
                Path destination=output.resolve(file.getFileName()); rejectLinks(destination);
                Files.move(file,destination,StandardCopyOption.REPLACE_EXISTING);
            }
            for(Path old:oldPages) {
                boolean keep=false;
                for(AtlasData.Page p:pageData) if(old.getFileName().toString().equals(p.image())) { keep=true; break; }
                if(!keep) Files.deleteIfExists(old);
            }
        } finally {
            // Delete only the exact temporary files we created; never recurse over user output.
            for(Path file:staged) Files.deleteIfExists(file);
            Files.deleteIfExists(staging);
        }
        return target;
    }

    private static Sprite crop(String name,BufferedImage image,int width,int height,float px,float py,AtlasSpec spec) {
        int left=0,top=0,right=width,bottom=height;
        if(spec.trim()) {
            left=width; top=height; right=0; bottom=0;
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) if((image.getRGB(x,y)>>>24)!=0) {
                left=Math.min(left,x); top=Math.min(top,y); right=Math.max(right,x+1); bottom=Math.max(bottom,y+1);
            }
            if(right==0) { left=top=0; right=bottom=1; }
            else {
                left=Math.max(0,left-spec.trimMargin()); top=Math.max(0,top-spec.trimMargin());
                right=Math.min(width,right+spec.trimMargin()); bottom=Math.min(height,bottom+spec.trimMargin());
            }
        }
        Sprite s=new Sprite(); s.name=name; s.originalWidth=width; s.originalHeight=height;
        s.width=right-left; s.height=bottom-top; s.trimX=left; s.trimY=height-bottom; s.pivotX=px; s.pivotY=py;
        s.pixels=image.getRGB(left,top,s.width,s.height,null,0,s.width);
        if(spec.bleed()) bleed(s.pixels,s.width,s.height);
        return s;
    }
    /** Multi-source Manhattan nearest-color fill. Row-major tie order is stable. */
    private static void bleed(int[] pixels,int width,int height) {
        int[] queue=new int[pixels.length]; boolean[] visited=new boolean[pixels.length]; int head=0,tail=0;
        for(int i=0;i<pixels.length;i++) if((pixels[i]>>>24)!=0) { visited[i]=true; queue[tail++]=i; }
        while(head<tail) {
            int i=queue[head++],x=i%width,y=i/width;
            if(x>0) tail=spread(pixels,visited,queue,tail,i,i-1);
            if(x+1<width) tail=spread(pixels,visited,queue,tail,i,i+1);
            if(y>0) tail=spread(pixels,visited,queue,tail,i,i-width);
            if(y+1<height) tail=spread(pixels,visited,queue,tail,i,i+width);
        }
    }
    private static int spread(int[] pixels,boolean[] visited,int[] queue,int tail,int from,int to) {
        if(!visited[to]) { visited[to]=true; pixels[to]=pixels[from]&0x00ffffff; queue[tail++]=to; }
        return tail;
    }
    private static ArrayList<Page> pack(List<Sprite> sprites,AtlasSpec spec) throws IOException {
        ArrayList<Page> pages=new ArrayList<>(); int border=spec.padding()+spec.extrusion();
        for(Sprite s:sprites) {
            int width=s.width+2*border,height=s.height+2*border;
            if(width>spec.maxPageSize() || height>spec.maxPageSize()) throw new IOException("Sprite with border exceeds page: "+s.name);
            boolean placed=false;
            for(int i=0;i<pages.size() && !placed;i++) placed=place(pages.get(i),s,i,width,height,border,spec.maxPageSize());
            if(!placed) {
                if(pages.size()==spec.maxPages()) throw new IOException("Atlas exceeds page limit");
                Page page=new Page(); pages.add(page); place(page,s,pages.size()-1,width,height,border,spec.maxPageSize());
            }
        }
        long pixels=0;
        for(Page p:pages) pixels+=(long)power2(p.usedWidth)*power2(p.usedHeight);
        if(pixels>AtlasData.MAX_PAGE_PIXELS) throw new IOException("Atlas exceeds 64M page pixel budget");
        return pages;
    }
    private static boolean place(Page page,Sprite s,int index,int w,int h,int border,int limit) {
        for(Row row:page.rows) if(h<=row.height && row.used+w<=limit) {
            s.page=index; s.x=row.used+border; s.y=row.y+border; row.used+=w;
            page.usedWidth=Math.max(page.usedWidth,row.used); return true;
        }
        if(page.usedHeight+h>limit) return false;
        Row row=new Row(); row.y=page.usedHeight; row.height=h; row.used=w; page.rows.add(row);
        s.page=index; s.x=border; s.y=row.y+border; page.usedHeight+=h; page.usedWidth=Math.max(page.usedWidth,w); return true;
    }
    private static void draw(BufferedImage image,Sprite s,int extrusion) {
        for(int y=-extrusion;y<s.height+extrusion;y++) for(int x=-extrusion;x<s.width+extrusion;x++) {
            image.setRGB(s.x+x,s.y+y,s.pixels[Math.clamp(y,0,s.height-1)*s.width+Math.clamp(x,0,s.width-1)]);
        }
    }
    private static String pageName(AtlasSpec spec,int page) { return spec.name()+"-"+page+".png"; }
    private static int power2(int value) { return value<=1 ? 1 : Integer.highestOneBit(value-1)<<1; }
    private static List<Path> oldPages(Path target,AtlasSpec spec) throws IOException {
        rejectLinks(target);
        if(!Files.exists(target)) return List.of();
        if(Files.size(target)>AtlasData.MAX_BYTES) throw new IOException("Existing atlas metadata too large");
        AtlasData old=AtlasData.parse(Files.readAllBytes(target)); ArrayList<Path> pages=new ArrayList<>();
        for(int i=0;i<old.pageCount();i++) {
            String name=old.page(i).image();
            if(!name.matches(java.util.regex.Pattern.quote(spec.name())+"-[0-9]+\\.png")) {
                throw new IOException("Existing atlas contains a page not owned by this tool: "+name);
            }
            Path page=target.getParent().resolve(name); rejectLinks(page); pages.add(page);
        }
        return pages;
    }
    private static void rejectLinks(Path path) throws IOException {
        for(Path p=path;p!=null;p=p.getParent()) if(Files.isSymbolicLink(p)) throw new IOException("Atlas output cannot contain symbolic links: "+p);
    }
    private static final class Sprite {
        String name; int[] pixels;
        int width,height,originalWidth,originalHeight,trimX,trimY,page,x,y; float pivotX,pivotY;
    }
    private static final class Page { final ArrayList<Row> rows=new ArrayList<>(); int usedWidth,usedHeight; }
    private static final class Row { int y,height,used; }
}
