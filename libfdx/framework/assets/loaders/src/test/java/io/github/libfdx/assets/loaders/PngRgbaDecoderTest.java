package io.github.libfdx.assets.loaders;

import io.github.libfdx.core.FdxException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PngRgbaDecoderTest {
    @Test void decodesImageIoPngExactlyIncludingHiddenRgbAndPartialAlpha() throws Exception {
        BufferedImage source=new BufferedImage(64,17,BufferedImage.TYPE_INT_ARGB);
        Random random=new Random(90210);
        for(int y=0;y<17;y++) for(int x=0;x<64;x++) source.setRGB(x,y,x%7==0 ? random.nextInt()&0x00ffffff : random.nextInt());
        byte[] png=encode(source,"png");
        ImageData result=ImageAssetLoader.decodeAsync("not-preloaded.png",png).get();
        assertEquals(64,result.width()); assertEquals(17,result.height());
        ByteBuffer rgba=result.rgba();
        for(int y=0;y<17;y++) for(int x=0;x<64;x++) {
            int expected=source.getRGB(x,y);
            assertEquals((expected>>>16)&255,rgba.get()&255); assertEquals((expected>>>8)&255,rgba.get()&255);
            assertEquals(expected&255,rgba.get()&255); assertEquals(expected>>>24,rgba.get()&255);
        }
    }
    @Test void allFiveFiltersAndSplitIdatRecoverIndependentReferencePixels() throws Exception {
        // 1-pixel-wide rows: filter left/diagonal inputs are zero, previous row is known.
        byte[][] expected={{20,40,60,0},{10,30,50,127},{30,60,90,(byte)255},{16,32,48,64},{4,8,12,16}};
        byte[] scanlines=new byte[25];
        for(int y=0;y<5;y++) {
            scanlines[y*5]=(byte)y;
            for(int c=0;c<4;c++) {
                int up=y==0?0:expected[y-1][c]&255;
                int predictor=y==2 || y==4 ? up : y==3 ? up/2 : 0;
                scanlines[y*5+c+1]=(byte)((expected[y][c]&255)-predictor);
            }
        }
        ImageData image=PngRgbaDecoder.decode(png(1,5,scanlines));
        ByteBuffer rgba=image.rgba();
        for(byte[] row:expected) for(byte value:row) assertEquals(value,rgba.get());
    }
    @Test void rejectsCrcTruncationAndDecompressionBeyondDeclaredExtent() throws Exception {
        byte[] png=png(1,1,new byte[]{0,10,20,30,40});
        byte[] bad=png.clone(); bad[29]^=1;
        assertThrows(FdxException.class,() -> PngRgbaDecoder.decode(bad));
        assertThrows(FdxException.class,() -> PngRgbaDecoder.decode(java.util.Arrays.copyOf(png,png.length-1)));
        assertThrows(FdxException.class,() -> PngRgbaDecoder.decode(png(1,1,new byte[10])));
        assertThrows(FdxException.class,() -> PngRgbaDecoder.decode(png(1,2,new byte[5])));
        assertThrows(FdxException.class,() -> PngRgbaDecoder.decode(png(8192,8192,new byte[5])));
        assertNull(PngRgbaDecoder.decode(new byte[]{1,2,3}));
    }
    @Test void otherFormatsStillUsePlatformFallbackWithNoTeaVmRuntimeOnJvm() throws Exception {
        ImageData jpeg=ImageAssetLoader.decodeAsync(null,encode(new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB),"jpg")).get();
        assertEquals(3,jpeg.width()); assertEquals(2,jpeg.height());
        assertTrue(ImageAssetLoader.decodeAsync(null,new byte[]{1,2,3}).isFailed());
    }
    private static byte[] encode(BufferedImage image,String format) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); assertTrue(ImageIO.write(image,format,bytes)); return bytes.toByteArray();
    }
    private static byte[] png(int width,int height,byte[] raw) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeLong(0x89504e470d0a1a0aL);
        ByteBuffer header=ByteBuffer.allocate(13); header.putInt(width).putInt(height).put((byte)8).put((byte)6).put((byte)0).put((byte)0).put((byte)0);
        chunk(out,"IHDR",header.array());
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(DeflaterOutputStream deflate=new DeflaterOutputStream(compressed)) { deflate.write(raw); }
        byte[] data=compressed.toByteArray(); int middle=data.length/2;
        chunk(out,"IDAT",java.util.Arrays.copyOfRange(data,0,middle)); chunk(out,"IDAT",java.util.Arrays.copyOfRange(data,middle,data.length));
        chunk(out,"IEND",new byte[0]); return bytes.toByteArray();
    }
    private static void chunk(DataOutputStream out,String name,byte[] data) throws IOException {
        byte[] type=name.getBytes(java.nio.charset.StandardCharsets.US_ASCII); CRC32 crc=new CRC32(); crc.update(type); crc.update(data);
        out.writeInt(data.length); out.write(type); out.write(data); out.writeInt((int)crc.getValue());
    }
}
