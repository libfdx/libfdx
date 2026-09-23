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

    static ImageData decode(byte[] bytes) {
        Decoder decoder = begin(bytes);
        if (decoder == null) return null;
        try { while (!decoder.step(65536)) { } return decoder.result(); }
        finally { decoder.close(); }
    }

    /** Null delegates other formats/layouts to the platform. The decoder borrows encoded bytes. */
    static Decoder begin(byte[] bytes) {
        if(bytes==null || bytes.length<8 || integer(bytes,0)!=0x89504e47 || integer(bytes,4)!=0x0d0a1a0a) return null;
        if(bytes.length<33 || bytes.length>MAX_BYTES || integer(bytes,8)!=13 || integer(bytes,12)!=IHDR) throw invalid("Invalid PNG header or size");
        int width=integer(bytes,16),height=integer(bytes,20),type=bytes[25]&255;
        if(width<1 || height<1 || (long)width*height>MAX_PIXELS) throw invalid("PNG exceeds 16M pixel budget");
        if(bytes[24]!=8 || (type!=2 && type!=6) || bytes[28]!=0) return null;
        if(bytes[26]!=0 || bytes[27]!=0) throw invalid("Unsupported PNG compression/filter method");
        return new Decoder(bytes, width, height, type);
    }

    static final class Decoder {
        final byte[] bytes;
        final int width, height, type, bpp;
        final CRC32 crc = new CRC32();
        int phase, offset=8, chunks, length, kind, checked=-1, compressedSize, copied, chunkCopied;
        boolean data, endedData, palette, done, fallback;
        byte[] compressed, row, previous;
        ByteBuffer rgba;
        Inflater inflater;
        int y, filled, filterAt=1, pixelAt=1;
        Decoder(byte[] bytes, int width, int height, int type) {
            this.bytes=bytes;this.width=width;this.height=height;this.type=type;bpp=type==6?4:3;
        }

        /** At most budget encoded/filter bytes or pixels per step; allocation is indivisible. */
        boolean step(int budget) {
            if (budget < 1) throw invalid("PNG work budget must be positive");
            if (done) return true;
            try {
                if (phase == 0) return checkChunk(budget);
                if (phase == 1) { copyChunk(budget); return false; }
                if (y == height) {
                    byte[] tail = new byte[1];
                    if(!inflater.finished() && inflater.inflate(tail)!=0) throw invalid("PNG decoded extent exceeds dimensions");
                    if(!inflater.finished() || inflater.getRemaining()!=0) throw invalid("Invalid PNG compressed stream end");
                    rgba.flip();done=true;close();return true;
                }
                if (filled < row.length) {
                    int n=inflater.inflate(row,filled,Math.min(budget,row.length-filled));
                    if(n==0) throw invalid("Truncated PNG image data");
                    filled+=n; return false;
                }
                int filter=row[0]&255;
                if(filter>4) throw invalid("Unknown PNG row filter");
                if (filterAt < row.length) {
                    int end=Math.min(row.length,filterAt+budget);
                    for(;filterAt<end;filterAt++) {
                        int i=filterAt,left=i>bpp ? row[i-bpp]&255 : 0,up=previous[i]&255,diagonal=i>bpp ? previous[i-bpp]&255 : 0;
                        int prediction=switch(filter) {case 0->0;case 1->left;case 2->up;case 3->(left+up)/2;default->paeth(left,up,diagonal);};
                        row[i]=(byte)((row[i]&255)+prediction);
                    }
                    return false;
                }
                int end=(int)Math.min(row.length,(long)pixelAt+(long)budget*bpp);
                for(;pixelAt<end;pixelAt+=bpp) rgba.put(row[pixelAt]).put(row[pixelAt+1]).put(row[pixelAt+2]).put(bpp==4?row[pixelAt+3]:(byte)255);
                if(pixelAt==row.length) {byte[] swap=previous;previous=row;row=swap;y++;filled=0;filterAt=1;pixelAt=1;}
                return false;
            } catch(DataFormatException error) {close();throw new FdxException("Invalid PNG deflate stream",error);}
            catch(RuntimeException | Error error) {close();throw error;}
        }

        private boolean checkChunk(int budget) {
            if(checked<0) {
                if(++chunks>65536 || bytes.length-offset<12) throw invalid("Invalid PNG chunk count/extent");
                length=integer(bytes,offset);kind=integer(bytes,offset+4);
                if(length<0 || length>bytes.length-offset-12) throw invalid("PNG chunk exceeds input");
                crc.reset();checked=0;
            }
            int n=Math.min(budget,length+4-checked);crc.update(bytes,offset+4+checked,n);checked+=n;
            if(checked<length+4)return false;
            if((int)crc.getValue()!=integer(bytes,offset+8+length)) throw invalid("PNG chunk CRC mismatch");
            if(kind==IHDR && offset!=8) throw invalid("Duplicate PNG header");
            if(kind==TRNS) {
                if(type==6) throw invalid("RGBA PNG cannot contain a transparency key");
                fallback=true;done=true;return true;
            }
            if(kind==PLTE) {
                if(palette || data || length==0 || length>768 || length%3!=0) throw invalid("Invalid PNG suggested palette");
                palette=true;
            }
            if(kind==IDAT) {if(endedData) throw invalid("PNG image data must be consecutive");compressedSize+=length;data=true;}
            else if(data) endedData=true;
            if(kind==IEND) {
                if(length!=0 || !data || compressedSize==0 || offset+12!=bytes.length) throw invalid("Invalid PNG end");
                compressed=new byte[compressedSize];phase=1;offset=8;return false;
            }
            if(kind!=IHDR && kind!=IDAT && kind!=PLTE && (bytes[offset+4]&32)==0) throw invalid("Unsupported critical PNG chunk");
            offset+=length+12;checked=-1;return false;
        }

        private void copyChunk(int budget) {
            int size=integer(bytes,offset);
            if(integer(bytes,offset+4)==IDAT) {
                int n=Math.min(budget,size-chunkCopied);
                System.arraycopy(bytes,offset+8+chunkCopied,compressed,copied,n);copied+=n;chunkCopied+=n;
                if(chunkCopied<size)return;
            }
            offset+=size+12;chunkCopied=0;
            if(offset==bytes.length) {
                row=new byte[width*bpp+1];previous=new byte[row.length];rgba=ByteBuffer.allocateDirect(width*height*4);
                inflater=new Inflater();inflater.setInput(compressed);phase=2;
            }
        }

        ImageData result() {
            if(!done)throw invalid("PNG decode is still pending");
            return fallback ? null : new ImageData(width,height,rgba);
        }
        void close() {if(inflater!=null){inflater.end();inflater=null;}}
    }

    private static int paeth(int left,int up,int diagonal) {
        int p=left+up-diagonal,a=Math.abs(p-left),b=Math.abs(p-up),c=Math.abs(p-diagonal);
        return a<=b && a<=c ? left : b<=c ? up : diagonal;
    }
    private static int integer(byte[] b,int i) {return ((b[i]&255)<<24)|((b[i+1]&255)<<16)|((b[i+2]&255)<<8)|(b[i+3]&255);}
    private static FdxException invalid(String message) {return new FdxException(message);}
}
