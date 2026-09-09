package io.github.libfdx.tools.ibl;

import java.nio.charset.StandardCharsets;

/** Bounded RGBE reader for ordinary -Y/+X Radiance images; no process execution or header commands. */
final class RadianceImage {
    final int width, height;
    final float[] rgb;
    private RadianceImage(int width, int height, float[] rgb) { this.width=width;this.height=height;this.rgb=rgb; }

    static RadianceImage decode(byte[] bytes) {
        Cursor input=new Cursor(bytes);
        String magic=input.line();
        if (!magic.equals("#?RADIANCE") && !magic.equals("#?RGBE")) fail("Expected a Radiance RGBE header");
        boolean format=false;
        double exposure=1;
        double[] correction={1,1,1};
        for (int line=0;line<128;line++) {
            String text=input.line();
            if (text.isEmpty()) break;
            if (text.startsWith("FORMAT=")) {
                if (format) fail("Duplicate Radiance FORMAT");
                if (!text.equals("FORMAT=32-bit_rle_rgbe")) fail("Only RGBE Radiance data is supported");
                format=true;
            }
            if (text.startsWith("EXPOSURE=")) exposure=positive(exposure*positive(Double.parseDouble(text.substring(9).trim())));
            if (text.startsWith("COLORCORR=")) {
                String[] values=text.substring(10).trim().split("\\s+");
                if(values.length!=3) fail("Expected three COLORCORR values");
                for(int c=0;c<3;c++) correction[c]=positive(correction[c]*positive(Double.parseDouble(values[c])));
            }
            if (text.startsWith("PIXASPECT=") && Double.parseDouble(text.substring(10).trim())!=1) fail("IBL requires square source pixels");
            if (text.startsWith("GAMMA=") && Double.parseDouble(text.substring(6).trim())!=1) fail("IBL requires linear source pixels");
            if (line==127) fail("Radiance header is too long");
        }
        if (!format) fail("Missing Radiance FORMAT");
        String[] size=input.line().trim().split("\\s+");
        if (size.length!=4 || !size[0].equals("-Y") || !size[2].equals("+X")) fail("Expected Radiance -Y height +X width orientation");
        int height=Integer.parseInt(size[1]),width=Integer.parseInt(size[3]);
        if (width<2 || width>4096 || height<1 || width!=2L*height) fail("Environment must be 2:1 and at most 4096x2048");
        float[] rgb=new float[width*height*3];
        byte[] scanline=new byte[width*4];
        for (int y=0;y<height;y++) {
            int a=input.get(),b=input.get(),c=input.get(),d=input.get();
            if (width>=8 && a==2 && b==2 && (c&128)==0) {
                if (((c<<8)|d)!=width) fail("Radiance RLE scanline width mismatch");
                for (int channel=0;channel<4;channel++) {
                    int x=0;
                    while (x<width) {
                        int code=input.get(),count=code>128?code-128:code;
                        if(count==0 || x+count>width) fail("Invalid Radiance RLE run");
                        if(code>128) {
                            int value=input.get();
                            for(int i=0;i<count;i++) scanline[(x++)*4+channel]=(byte)value;
                        } else for(int i=0;i<count;i++) scanline[(x++)*4+channel]=(byte)input.get();
                    }
                }
            } else {
                scanline[0]=(byte)a;scanline[1]=(byte)b;scanline[2]=(byte)c;scanline[3]=(byte)d;
                for(int i=4;i<scanline.length;i++) scanline[i]=(byte)input.get();
                for(int x=0;x<width;x++) if((scanline[x*4]&255)==1 && (scanline[x*4+1]&255)==1 && (scanline[x*4+2]&255)==1) {
                    fail("Legacy Radiance repeated-pixel encoding is unsupported; re-export scanline RLE");
                }
            }
            for(int x=0;x<width;x++) {
                int exponent=scanline[x*4+3]&255;
                double scale=exponent==0?0:Math.scalb(1.0,exponent-136);
                for(int channel=0;channel<3;channel++) {
                    double value=(scanline[x*4+channel]&255)*scale/exposure/correction[channel];
                    if(!Double.isFinite(value) || value>65504) fail("Radiance exceeds half-float range; reduce source radiance");
                    rgb[(y*width+x)*3+channel]=(float)value;
                }
            }
        }
        if(input.position!=bytes.length) fail("Unexpected data after Radiance pixels");
        return new RadianceImage(width,height,rgb);
    }
    private static double positive(double value) {
        if(!Double.isFinite(value)||value<=0) fail("Radiance correction must be positive and finite");
        return value;
    }
    private static final class Cursor {
        final byte[] bytes; int position;
        Cursor(byte[] bytes) {
            if(bytes==null || bytes.length==0 || bytes.length>128*1024*1024) fail("Invalid Radiance file size");
            this.bytes=bytes;
        }
        int get() { if(position==bytes.length) fail("Truncated Radiance data");return bytes[position++]&255; }
        String line() {
            int start=position;
            while(get()!=10) if(position-start>4096) fail("Radiance header line is too long");
            int end=position-1;
            if(end>start && bytes[end-1]==13) end--;
            return new String(bytes,start,end-start,StandardCharsets.US_ASCII);
        }
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
