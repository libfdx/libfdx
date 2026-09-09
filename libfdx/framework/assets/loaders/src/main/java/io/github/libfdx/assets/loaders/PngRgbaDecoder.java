package io.github.libfdx.assets.loaders;

import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Raw 8-bit, non-interlaced RGB/RGBA PNG decoding, preserving RGB at alpha zero. */
final class PngRgbaDecoder {
    private static final int IHDR=0x49484452,IDAT=0x49444154,IEND=0x49454e44,TRNS=0x74524e53,PLTE=0x504c5445;
    private static final int MAX_BYTES=64*1024*1024,MAX_PIXELS=16*1024*1024;
    private PngRgbaDecoder() { }

    /** Null delegates other PNG layouts/formats to the platform decoder; malformed supported PNGs fail. */
    static ImageData decode(byte[] bytes) {
        if(bytes==null || bytes.length<8 || integer(bytes,0)!=0x89504e47 || integer(bytes,4)!=0x0d0a1a0a) return null;
        if(bytes.length<33 || bytes.length>MAX_BYTES || integer(bytes,8)!=13 || integer(bytes,12)!=IHDR) throw invalid("Invalid PNG header or size");
        int width=integer(bytes,16),height=integer(bytes,20),type=bytes[25]&255,bpp=type==6?4:3;
        if(width<1 || height<1 || (long)width*height>MAX_PIXELS) throw invalid("PNG exceeds 16M pixel budget");
        if(bytes[24]!=8 || (type!=2 && type!=6) || bytes[28]!=0) return null;
        if(bytes[26]!=0 || bytes[27]!=0) throw invalid("Unsupported PNG compression/filter method");
        int compressedSize=0,chunks=0; boolean data=false,endedData=false,end=false,palette=false;
        CRC32 crc=new CRC32();
        for(int offset=8;offset<bytes.length;) {
            if(++chunks>65536 || bytes.length-offset<12) throw invalid("Invalid PNG chunk count/extent");
            int length=integer(bytes,offset),kind=integer(bytes,offset+4);
            if(length<0 || length>bytes.length-offset-12) throw invalid("PNG chunk exceeds input");
            crc.reset(); crc.update(bytes,offset+4,length+4);
            if((int)crc.getValue()!=integer(bytes,offset+8+length)) throw invalid("PNG chunk CRC mismatch");
            if(kind==IHDR && offset!=8) throw invalid("Duplicate PNG header");
            if(kind==TRNS) {
                if(type==6) throw invalid("RGBA PNG cannot contain a transparency key");
                return null; // Platform handles the optional RGB transparency key.
            }
            if(kind==PLTE) {
                if(palette || data || length==0 || length>768 || length%3!=0) throw invalid("Invalid PNG suggested palette");
                palette=true;
            }
            if(kind==IDAT) {
                if(endedData) throw invalid("PNG image data must be consecutive");
                compressedSize+=length; data=true;
            } else if(data) endedData=true;
            if(kind==IEND) {
                if(length!=0 || !data || offset+12!=bytes.length) throw invalid("Invalid PNG end");
                end=true; break;
            }
            if(kind!=IHDR && kind!=IDAT && kind!=IEND && kind!=PLTE && (bytes[offset+4]&32)==0) throw invalid("Unsupported critical PNG chunk");
            offset+=length+12;
        }
        if(!end || compressedSize==0) throw invalid("Incomplete PNG image");
        byte[] compressed=new byte[compressedSize]; int at=0;
        for(int offset=8;offset<bytes.length;) {
            int length=integer(bytes,offset);
            if(integer(bytes,offset+4)==IDAT) { System.arraycopy(bytes,offset+8,compressed,at,length); at+=length; }
            offset+=length+12;
        }
        int stride=width*bpp;
        byte[] row=new byte[stride+1],previous=new byte[stride+1],tail=new byte[1];
        ByteBuffer rgba=ByteBuffer.allocateDirect(width*height*4);
        Inflater inflater=new Inflater();
        try {
            inflater.setInput(compressed);
            for(int y=0;y<height;y++) {
                int filled=0;
                while(filled<row.length) {
                    int n=inflater.inflate(row,filled,row.length-filled);
                    if(n==0) throw invalid("Truncated PNG image data");
                    filled+=n;
                }
                int filter=row[0]&255;
                if(filter>4) throw invalid("Unknown PNG row filter");
                for(int i=1;i<row.length;i++) {
                    int left=i>bpp ? row[i-bpp]&255 : 0,up=previous[i]&255,upperLeft=i>bpp ? previous[i-bpp]&255 : 0;
                    int prediction=switch(filter) {
                        case 0 -> 0; case 1 -> left; case 2 -> up; case 3 -> (left+up)/2;
                        default -> paeth(left,up,upperLeft);
                    };
                    row[i]=(byte)((row[i]&255)+prediction);
                }
                for(int x=1;x<row.length;x+=bpp) rgba.put(row[x]).put(row[x+1]).put(row[x+2]).put(bpp==4?row[x+3]:(byte)255);
                byte[] swap=previous; previous=row; row=swap;
            }
            if(!inflater.finished() && inflater.inflate(tail)!=0) throw invalid("PNG decoded extent exceeds dimensions");
            if(!inflater.finished() || inflater.getRemaining()!=0) throw invalid("Invalid PNG compressed stream end");
        } catch(DataFormatException error) { throw new FdxException("Invalid PNG deflate stream",error); }
        finally { inflater.end(); }
        rgba.flip(); return new ImageData(width,height,rgba);
    }
    private static int paeth(int left,int up,int diagonal) {
        int p=left+up-diagonal,a=Math.abs(p-left),b=Math.abs(p-up),c=Math.abs(p-diagonal);
        return a<=b && a<=c ? left : b<=c ? up : diagonal;
    }
    private static int integer(byte[] b,int i) { return ((b[i]&255)<<24)|((b[i+1]&255)<<16)|((b[i+2]&255)<<8)|(b[i+3]&255); }
    private static FdxException invalid(String message) { return new FdxException(message); }
}
