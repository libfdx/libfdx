package io.github.libfdx.tools.ibl;

import io.github.libfdx.graphics.g3d.ImageBasedLightingData;

/** Deterministic offline cosine/GGX environment convolution and correlated Smith split-sum integration.
 * All inputs use linear RGB with the application's working primaries. This CPU tool allocates and can
 * take seconds; it is never called by the runtime renderer or asset loader. */
public final class IblPreparer {
    private static final double PI = Math.PI;
    private static final double LOG_2 = Math.log(2);
    private IblPreparer() { }

    /** Copies a 2:1 radiance image, prepares all mips and returns immutable upload data.
     * Output widths are powers of two (2..2048), LUT size 2..1024, samples 32..4096.
     * Source size is bounded to 4096x2048 and work to 250 million integration samples.
     * Larger source images or budgets must be explicitly reduced before preparation. */
    public static ImageBasedLightingData prepare(float[] rgb, int width, int height,
            int specularWidth, int diffuseWidth, int lutSize, int samples) {
        dimension(specularWidth, 2048); dimension(diffuseWidth, 2048); dimension(lutSize, 1024);
        if (samples < 32 || samples > 4096) throw new IllegalArgumentException("IBL sample count must be 32..4096");
        // Stop at 16x8 for ordinary probes: collapsing very rough reflections to 2x1 or 1x1
        // destroys directional illumination. Small diagnostic probes still have at least two levels.
        int levels = Math.max(2, 28 - Integer.numberOfLeadingZeros(specularWidth));
        long work = (long) diffuseWidth * diffuseWidth / 2 + (long) lutSize * lutSize;
        for (int i=1;i<levels;i++) work += (long) Math.max(1, specularWidth >> i) * Math.max(1, (specularWidth/2) >> i);
        if (work * samples > 250_000_000L) throw new IllegalArgumentException("IBL integration budget exceeds 250 million samples");
        Source source = new Source(rgb, width, height);
        double[] cosine = new double[samples], sine = new double[samples], sequence = new double[samples];
        for (int i=0;i<samples;i++) {
            double phi = 2*PI*i/samples;
            cosine[i] = Math.cos(phi); sine[i] = Math.sin(phi);
            sequence[i] = Integer.toUnsignedLong(Integer.reverse(i)) * 0x1p-32;
        }
        float[][] specular = new float[levels][];
        for (int level=0;level<levels;level++) {
            int w=Math.max(1,specularWidth>>level), h=Math.max(1,(specularWidth/2)>>level);
            specular[level] = convolve(source, w, h, (double)level/(levels-1), false, level==0, cosine, sine, sequence);
        }
        float[] diffuse = convolve(source, diffuseWidth, diffuseWidth/2, 1, true, false, cosine, sine, sequence);
        float[] brdf = new float[lutSize*lutSize*2];
        for (int y=0;y<lutSize;y++) for (int x=0;x<lutSize;x++) {
            integrateBrdf((x+.5)/lutSize, (y+.5)/lutSize, cosine, sine, sequence, brdf, (y*lutSize+x)*2);
        }
        return ImageBasedLightingData.of(specularWidth, specular, diffuseWidth, diffuse, lutSize, brdf);
    }

    private static float[] convolve(Source source, int width, int height, double roughness,
            boolean diffuse, boolean direct, double[] cosine, double[] sine, double[] sequence) {
        int samples = sequence.length;
        double[] dx=new double[samples], dy=new double[samples], dz=new double[samples], pdf=new double[samples];
        double alpha2 = roughness*roughness*roughness*roughness;
        for (int i=0;i<samples;i++) {
            double z = diffuse ? Math.sqrt(1-sequence[i]) : Math.sqrt((1-sequence[i])/(1+(alpha2-1)*sequence[i]));
            double r = Math.sqrt(Math.max(0,1-z*z));
            dx[i]=r*cosine[i]; dy[i]=r*sine[i]; dz[i]=z;
            if (diffuse) pdf[i]=z/PI;
            else {
                double d=z*z*(alpha2-1)+1;
                pdf[i]=alpha2/(4*PI*d*d);
                dx[i]*=2*z; dy[i]*=2*z; dz[i]=2*z*z-1;
            }
        }
        float[] result = new float[width*height*3];
        if (width == 1 && height == 1) {
            // A single longitude/latitude texel represents the whole sphere.
            System.arraycopy(source.levels[source.levels.length-1], 0, result, 0, 3);
            return result;
        }
        double[] pixel = new double[3];
        double directLod = Math.max(0, Math.log((double)source.width/width)/LOG_2);
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
            double theta=PI*(y+.5)/height, phi=2*PI*((x+.5)/width-.5);
            double nx=Math.sin(theta)*Math.cos(phi), ny=Math.cos(theta), nz=Math.sin(theta)*Math.sin(phi);
            int offset=(y*width+x)*3;
            if (direct) {
                source.sample(nx,ny,nz,directLod,pixel);
                for (int c=0;c<3;c++) result[offset+c]=(float)pixel[c];
                continue;
            }
            // Tangent = normalize(cross(+Y,N)); all pixel-center normals avoid the exact poles.
            double inverse = 1/Math.sqrt(nx*nx+nz*nz), tx=nz*inverse, tz=-nx*inverse;
            double bx=ny*tz, by=nz*tx-nx*tz, bz=-ny*tx;
            double red=0,green=0,blue=0,total=0;
            for (int i=0;i<samples;i++) {
                if (dz[i]<=0) continue;
                double lx=tx*dx[i]+bx*dy[i]+nx*dz[i], ly=by*dy[i]+ny*dz[i], lz=tz*dx[i]+bz*dy[i]+nz*dz[i];
                double texelArea = 2*PI*PI/(source.width*source.height) * Math.max(1e-6,Math.sqrt(Math.max(0,1-ly*ly)));
                double lod=.5*Math.log(1/Math.max(1e-20,samples*pdf[i]*texelArea))/LOG_2;
                source.sample(lx,ly,lz,lod,pixel);
                double weight=diffuse?1:dz[i];
                red+=pixel[0]*weight; green+=pixel[1]*weight; blue+=pixel[2]*weight; total+=weight;
            }
            result[offset]=(float)(red/total); result[offset+1]=(float)(green/total); result[offset+2]=(float)(blue/total);
        }
        return result;
    }

    private static void integrateBrdf(double ndv, double roughness, double[] cosine, double[] sine,
            double[] sequence, float[] output, int offset) {
        double vx=Math.sqrt(1-ndv*ndv), alpha2=Math.pow(roughness,4), sumA=0,sumB=0;
        for (int i=0;i<sequence.length;i++) {
            double hz=Math.sqrt((1-sequence[i])/(1+(alpha2-1)*sequence[i]));
            double hx=Math.sqrt(Math.max(0,1-hz*hz))*cosine[i];
            double vh=vx*hx+ndv*hz;
            double ndl=2*vh*hz-ndv;
            if (ndl<=0 || vh<=0) continue;
            double denominator=ndl*Math.sqrt(alpha2+(1-alpha2)*ndv*ndv)
                    + ndv*Math.sqrt(alpha2+(1-alpha2)*ndl*ndl);
            double visibility = 2*ndl*vh/(hz*denominator);
            double fresnel=Math.pow(1-vh,5);
            sumA+=(1-fresnel)*visibility; sumB+=fresnel*visibility;
        }
        output[offset]=(float)Math.max(0,Math.min(1,sumA/sequence.length));
        output[offset+1]=(float)Math.max(0,Math.min(1,sumB/sequence.length));
    }

    private static void dimension(int value, int max) {
        if (value<2 || value>max || (value&(value-1))!=0) throw new IllegalArgumentException("Output dimensions must be bounded powers of two");
    }

    /** Solid-angle weighted source pyramid; sampling wraps at the longitude seam and clamps the poles. */
    private static final class Source {
        final int width,height;
        final float[][] levels;
        Source(float[] rgb,int width,int height) {
            if (width<2 || width>4096 || height<1 || width!=height*2 || rgb==null || rgb.length!=(long)width*height*3) {
                throw new IllegalArgumentException("IBL source must be a 2:1 linear RGB image, at most 4096x2048");
            }
            for(float value:rgb) if(!Float.isFinite(value)||value<0||value>65504) throw new IllegalArgumentException("Invalid source radiance");
            this.width=width;this.height=height;
            levels=new float[32-Integer.numberOfLeadingZeros(width)][];
            levels[0]=rgb.clone();
            for(int level=1;level<levels.length;level++) {
                int w=Math.max(1,width>>level),h=Math.max(1,height>>level),pw=Math.max(1,width>>(level-1)),ph=Math.max(1,height>>(level-1));
                float[] previous=levels[level-1],next=levels[level]=new float[w*h*3];
                for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                    double x0=(double)x*pw/w,x1=(double)(x+1)*pw/w,y0=(double)y*ph/h,y1=(double)(y+1)*ph/h;
                    double red=0,green=0,blue=0,total=0;
                    for(int sy=(int)y0;sy<Math.ceil(y1);sy++) for(int sx=(int)x0;sx<Math.ceil(x1);sx++) {
                        double area=(Math.min(x1,sx+1)-Math.max(x0,sx))
                                *(Math.cos(PI*Math.max(y0,sy)/ph)-Math.cos(PI*Math.min(y1,sy+1)/ph));
                        int at=(sy*pw+sx)*3;
                        red+=previous[at]*area;green+=previous[at+1]*area;blue+=previous[at+2]*area;total+=area;
                    }
                    int at=(y*w+x)*3;next[at]=(float)(red/total);next[at+1]=(float)(green/total);next[at+2]=(float)(blue/total);
                }
            }
        }
        void sample(double x,double y,double z,double lod,double[] output) {
            double u=Math.atan2(z,x)/(2*PI)+.5,v=Math.acos(Math.max(-1,Math.min(1,y)))/PI;
            lod=Math.max(0,Math.min(levels.length-1,lod));
            int level=(int)lod;
            output[0]=output[1]=output[2]=0;
            sampleLevel(u,v,level,1-(lod-level),output);
            if(lod>level) sampleLevel(u,v,level+1,lod-level,output);
        }
        void sampleLevel(double u,double v,int level,double weight,double[] output) {
            int w=Math.max(1,width>>level),h=Math.max(1,height>>level);
            double px=u*w-.5,py=v*h-.5;
            int ix=(int)Math.floor(px),iy=(int)Math.floor(py);
            double fx=px-ix,fy=py-iy;
            for(int row=0;row<2;row++) for(int col=0;col<2;col++) {
                int sx=((ix+col)%w+w)%w,sy=Math.max(0,Math.min(h-1,iy+row));
                double factor=weight*(col==0?1-fx:fx)*(row==0?1-fy:fy);
                int at=(sy*w+sx)*3;
                for(int c=0;c<3;c++) output[c]+=levels[level][at+c]*factor;
            }
        }
    }
}
