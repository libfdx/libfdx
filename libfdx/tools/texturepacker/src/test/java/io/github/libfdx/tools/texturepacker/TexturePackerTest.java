package io.github.libfdx.tools.texturepacker;

import io.github.libfdx.assets.loaders.AtlasData;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class TexturePackerTest {
    @TempDir Path temp;
    @Test void deterministicPagesTrimPivotsBleedExtrusionAndPadding() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")),out=temp.resolve("out");
        BufferedImage image=new BufferedImage(12,10,BufferedImage.TYPE_INT_ARGB);
        for(int y=2;y<7;y++) for(int x=3;x<8;x++) image.setRGB(x,y,0xffff8020);
        image.setRGB(5,4,0); // Transparent hole: bleed keeps alpha zero and restores orange RGB.
        write(source.resolve("hero.png"),image);
        Files.writeString(source.resolve("hero.sprite.properties"),"pivotX=0.25\npivotY=0\nname=walk/hero\n");
        write(source.resolve("empty.png"),new BufferedImage(7,9,BufferedImage.TYPE_INT_ARGB));
        AtlasSpec spec=new AtlasSpec(source,out,"test","atlas",32,2,1,true,0,true,.5f,.5f,8);
        Path metadata=TexturePacker.generate(spec); Map<String,byte[]> original=contents(out);
        AtlasData data=AtlasData.parse(Files.readAllBytes(metadata));
        AtlasData.Sprite s=data.sprite(data.indexOf("walk/hero"));
        assertEquals(5,s.width()); assertEquals(5,s.height()); assertEquals(3,s.trimX()); assertEquals(3,s.trimY());
        assertEquals(12,s.originalWidth()); assertEquals(10,s.originalHeight()); assertEquals(.25f,s.pivotX()); assertEquals(0,s.pivotY());
        BufferedImage page=ImageIO.read(metadata.getParent().resolve(data.page(s.page()).image()).toFile());
        assertEquals(0x00ff8020,page.getRGB(s.x()+2,s.y()+2));
        assertEquals(0xffff8020,page.getRGB(s.x()-1,s.y()-1));
        assertEquals(0,page.getRGB(s.x()-2,s.y()-2));
        AtlasData.Sprite empty=data.sprite(data.indexOf("empty")); assertEquals(1,empty.width()); assertEquals(9,empty.originalHeight());
        TexturePacker.generate(spec); Map<String,byte[]> rerun=contents(out);
        assertEquals(original.keySet(),rerun.keySet()); original.forEach((name,bytes) -> assertArrayEquals(bytes,rerun.get(name),name));
    }
    @Test void pagesShrinkAndOnlyOwnedObsoletePagesAreRemoved() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")),out=temp.resolve("out");
        for(int i=0;i<3;i++) write(source.resolve(i+".png"),solid(8,8));
        AtlasSpec spec=new AtlasSpec(source,out,"test","",16,1,1,false,0,false,0,0,4);
        Path metadata=TexturePacker.generate(spec); assertEquals(3,AtlasData.parse(Files.readAllBytes(metadata)).pageCount());
        Files.writeString(out.resolve("unrelated.txt"),"keep"); Files.write(out.resolve("test-99.png"),new byte[]{7});
        Files.delete(source.resolve("1.png")); Files.delete(source.resolve("2.png"));
        TexturePacker.generate(spec);
        assertTrue(Files.exists(out.resolve("test-0.png"))); assertFalse(Files.exists(out.resolve("test-1.png")));
        assertFalse(Files.exists(out.resolve("test-2.png"))); assertEquals("keep",Files.readString(out.resolve("unrelated.txt")));
        assertArrayEquals(new byte[]{7},Files.readAllBytes(out.resolve("test-99.png")));
    }
    @Test void invalidInputsLeavePublishedAtlasIntact() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")),out=temp.resolve("out");
        write(source.resolve("hero.png"),solid(8,8));
        AtlasSpec spec=new AtlasSpec(source,out,"test","",16,1,1,true,1,true,0,0,1);
        TexturePacker.generate(spec); Map<String,byte[]> original=contents(out);
        write(source.resolve("other.png"),solid(8,8));
        assertThrows(IOException.class,() -> TexturePacker.generate(spec));
        Files.writeString(source.resolve("other.sprite.properties"),"name=HERO\n");
        assertThrows(IOException.class,() -> TexturePacker.generate(spec));
        Files.delete(source.resolve("other.png")); Files.delete(source.resolve("other.sprite.properties"));
        write(source.resolve("huge.png"),solid(16385,1));
        assertThrows(IOException.class,() -> TexturePacker.generate(spec));
        Map<String,byte[]> after=contents(out); assertEquals(original.keySet(),after.keySet());
        original.forEach((name,bytes) -> assertArrayEquals(bytes,after.get(name),name));
        assertThrows(IllegalArgumentException.class,() -> AtlasSpec.defaults(source,source.resolve("out"),"bad"));
        assertThrows(RuntimeException.class,() -> new AtlasSpec(source,out,"test","../escape",16,1,1,true,1,true,0,0,1));
    }
    private static BufferedImage solid(int width,int height) {
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) image.setRGB(x,y,0xff30c080);
        return image;
    }
    private static void write(Path path,BufferedImage image) throws IOException { ImageIO.write(image,"png",path.toFile()); image.flush(); }
    private static Map<String,byte[]> contents(Path root) throws IOException {
        Map<String,byte[]> result=new TreeMap<>();
        try(var files=Files.walk(root)) {
            for(Path file:files.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(),Files.readAllBytes(file));
        }
        return result;
    }
}
