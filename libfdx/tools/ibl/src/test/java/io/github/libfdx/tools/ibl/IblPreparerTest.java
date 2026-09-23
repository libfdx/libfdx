package io.github.libfdx.tools.ibl;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

final class IblPreparerTest {
    @Test
    void constantHdrEnergySurvivesEveryLevelAndEncodingIsReproducible() {
        float[] source=new float[64*32*3];
        float[] color={2,4,12};
        for(int i=0;i<source.length;i++) source[i]=color[i%3];
        var data=IblPreparer.prepare(source,64,32,16,8,16,256);
        byte[] encoded=data.encode();
        assertArrayEquals(encoded,IblPreparer.prepare(source,64,32,16,8,16,256).encode());
        ByteBuffer values=ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        int end=encoded.length-16*16*8;
        for(int at=32;at<end;at+=8) {
            assertEquals(2,half(values,at),.0001);
            assertEquals(4,half(values,at+2),.0001);
            assertEquals(12,half(values,at+4),.0001);
        }
    }

    @Test
    void diffuseMatchesAnalyticDirectionalRadianceAndBrdfMatchesIndependentHemisphereQuadrature() {
        float[] source=new float[256*128*3];
        for(int y=0;y<128;y++) for(int x=0;x<256;x++) {
            double ny=Math.cos(Math.PI*(y+.5)/128);
            for(int c=0;c<3;c++) source[(y*256+x)*3+c]=(float)(2+ny);
        }
        var data=IblPreparer.prepare(source,256,128,16,16,16,4096);
        ByteBuffer pixels=ByteBuffer.wrap(data.encode()).order(ByteOrder.LITTLE_ENDIAN);
        int diffuseOffset=32;
        for(int i=0;i<pixels.getInt(20);i++) diffuseOffset+=Math.max(1,16>>i)*Math.max(1,8>>i)*8;
        for(int y=0;y<8;y++) for(int x=0;x<16;x++) {
            double expected=2+(2.0/3)*Math.cos(Math.PI*(y+.5)/8);
            assertEquals(expected,half(pixels,diffuseOffset+(y*16+x)*8),.012);
        }
        int lutOffset=diffuseOffset+16*8*8;
        for(int[] xy:new int[][]{{7,7},{3,11},{12,9},{11,4}}) {
            double ndv=(xy[0]+.5)/16,r=(xy[1]+.5)/16;
            double[] expected=quadrature(ndv,r);
            int at=lutOffset+(xy[1]*16+xy[0])*8;
            assertEquals(expected[0],half(pixels,at),.013,"BRDF A");
            assertEquals(expected[1],half(pixels,at+2),.006,"BRDF B");
        }
    }

    // Uniform-solid-angle hemisphere integration of the BRDF, independent of importance sampling.
    private static double[] quadrature(double nv,double r) {
        int rows=384,cols=768;
        double vx=Math.sqrt(1-nv*nv),a2=Math.pow(r,4),A=0,B=0;
        for(int y=0;y<rows;y++) {
            double nl=(y+.5)/rows,s=Math.sqrt(1-nl*nl);
            for(int x=0;x<cols;x++) {
                double phi=2*Math.PI*(x+.5)/cols,lx=s*Math.cos(phi),ly=s*Math.sin(phi);
                double hx=vx+lx,hy=ly,hz=nv+nl,len=Math.sqrt(hx*hx+hy*hy+hz*hz);
                hx/=len;hz/=len;
                double vh=vx*hx+nv*hz,den=hz*hz*(a2-1)+1;
                double D=a2/(Math.PI*den*den);
                double G=2*nl*nv/(nl*Math.sqrt(a2+(1-a2)*nv*nv)+nv*Math.sqrt(a2+(1-a2)*nl*nl));
                double weight=D*G/(4*nv),F=Math.pow(1-vh,5);
                A+=weight*(1-F);B+=weight*F;
            }
        }
        return new double[]{A*2*Math.PI/(rows*cols),B*2*Math.PI/(rows*cols)};
    }

    @Test
    void radianceRleAndRawAgreeAndMalformedRunsCannotOverrun() throws Exception {
        byte[] header="#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y 4 +X 8\n".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream raw=new ByteArrayOutputStream(),rle=new ByteArrayOutputStream();
        raw.write(header);rle.write(header);
        for(int y=0;y<4;y++) {
            rle.write(new byte[]{2,2,0,8});
            for(int v:new int[]{128,64,32,130}) rle.write(new byte[]{(byte)136,(byte)v});
            for(int x=0;x<8;x++) raw.write(new byte[]{(byte)128,64,32,(byte)130});
        }
        var image=RadianceImage.decode(rle.toByteArray());
        assertArrayEquals(image.rgb,RadianceImage.decode(raw.toByteArray()).rgb);
        assertEquals(2,image.rgb[0]);assertEquals(1,image.rgb[1]);assertEquals(.5,image.rgb[2]);
        ByteArrayOutputStream corrected=new ByteArrayOutputStream();
        corrected.write(new String(header,StandardCharsets.US_ASCII).replace("\n\n", "\nEXPOSURE=2\nEXPOSURE=2\nCOLORCORR=1 2 .5\n\n").getBytes(StandardCharsets.US_ASCII));
        corrected.write(Arrays.copyOfRange(raw.toByteArray(),header.length,raw.size()));
        float[] correctedRgb=RadianceImage.decode(corrected.toByteArray()).rgb;
        assertEquals(.5,correctedRgb[0]);assertEquals(.125,correctedRgb[1]);assertEquals(.25,correctedRgb[2]);
        byte[] bad=rle.toByteArray();bad[header.length+4]=(byte)137;
        assertThrows(IllegalArgumentException.class,()->RadianceImage.decode(bad));
        assertThrows(IllegalArgumentException.class,()->RadianceImage.decode(Arrays.copyOf(raw.toByteArray(),header.length+8)));
        assertThrows(IllegalArgumentException.class,()->IblPreparer.prepare(image.rgb,8,4,2048,2048,1024,4096));
    }
    private static float half(ByteBuffer bytes,int at) { return Float.float16ToFloat(bytes.getShort(at)); }
}
