package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;

/**
 * Immutable copied vector/quaternion track with its own time domain. Sampling uses caller-owned
 * output and allocates nothing. Times are finite, nonnegative and strictly increasing; sampling
 * outside the domain clamps to its endpoint. Input/output times are seconds.
 */
public final class AnimationSampler {
    public enum Interpolation { LINEAR, STEP, CUBICSPLINE }
    private final Interpolation interpolation;
    private final boolean rotation;
    private final int components,stride;
    private final float[] times,values;

    /** Creates a three-component vector or XYZW quaternion track. Cubic values use in-tangent,
     * value, out-tangent triples for each time; tangents are derivatives per second. Cubic tracks
     * require two keys. Rotation keys' squared lengths must be within 0.001 of one; tangents need
     * not be unit. Cubic rotation tangents/signs are preserved, and interpolated output is normalized. */
    public AnimationSampler(boolean rotation,Interpolation interpolation,float[] times,float[] values) {
        if(interpolation==null||times==null||times.length==0||values==null)throw new FdxException("Animation sampler requires a mode, times and values");
        this.rotation=rotation;this.interpolation=interpolation;components=rotation?4:3;
        stride=components*(interpolation==Interpolation.CUBICSPLINE?3:1);
        if((long)times.length*stride!=values.length||interpolation==Interpolation.CUBICSPLINE&&times.length<2)
            throw new FdxException("Animation sampler value count does not match keyframes/interpolation");
        float previous=-1;
        for(float time:times) {
            if(!Float.isFinite(time)||time<0||time<=previous)throw new FdxException("Animation sampler times must be finite, nonnegative and strictly increasing");
            previous=time;
        }
        for(float value:values)if(!Float.isFinite(value))throw new FdxException("Animation sampler values must be finite");
        if(rotation)for(int key=0;key<times.length;key++) {
            int offset=key*stride+(interpolation==Interpolation.CUBICSPLINE?components:0);
            double length=0;for(int c=0;c<4;c++)length+=(double)values[offset+c]*values[offset+c];
            if(Math.abs(length-1)>.001)throw new FdxException("Animation rotation keys must be unit quaternions");
        }
        this.times=times.clone();this.values=values.clone();
    }
    public Interpolation interpolation(){return interpolation;}
    public boolean isRotation(){return rotation;}
    public int keyCount(){return times.length;}
    public float firstTime(){return times[0];}
    public float lastTime(){return times[times.length-1];}
    /** Returns a copy; sampling does not require copying the track. */
    public float[] times(){return times.clone();}
    /** Returns copied raw values/tangents in constructor layout. */
    public float[] values(){return values.clone();}
    /** Writes three vector or four normalized rotation components. A cubic quaternion passing through
     * zero, or a nonfinite evaluated component, fails explicitly instead of producing an invalid transform. */
    public void sample(float time,float[] out,int offset) {
        if(out==null||offset<0||offset>out.length-components)throw new FdxException("Animation sampler output is too small");
        int key=interval(time);
        if(rotation)rotation(key,time,out,offset,null,0,0,0,1,1,1);
        else for(int c=0;c<3;c++)out[offset+c]=component(key,time,c);
    }
    int interval(float time) {
        if(!Float.isFinite(time))throw new FdxException("Animation sample time must be finite");
        int low=0,high=times.length;
        while(low<high){int middle=(low+high)>>>1;if(times[middle]<=time)low=middle+1;else high=middle;}
        return Math.max(0,low-1);
    }
    float component(int key,float time,int component) {
        int offset=key*stride+(interpolation==Interpolation.CUBICSPLINE?components:0);
        if(key==times.length-1||time<=times[key]||interpolation==Interpolation.STEP)return values[offset+component];
        double dt=(double)times[key+1]-times[key],t=(time-times[key])/dt;
        double result;
        if(interpolation==Interpolation.LINEAR)result=values[offset+component]*(1-t)+values[offset+stride+component]*t;
        else {
            double t2=t*t,t3=t2*t;
            result=(2*t3-3*t2+1)*values[offset+component]+(t3-2*t2+t)*dt*values[offset+components+component]
                    +(-2*t3+3*t2)*values[offset+stride+component]+(t3-t2)*dt*values[offset+stride-components+component];
        }
        float value=(float)result;
        if(!Float.isFinite(value))throw new FdxException("Animation sample overflow");return value;
    }
    Matrix4 compose(float time,Matrix4 out,float x,float y,float z,float sx,float sy,float sz) {
        rotation(interval(time),time,null,0,out,x,y,z,sx,sy,sz);return out;
    }
    private void rotation(int key,float time,float[] out,int offset,Matrix4 matrix,float tx,float ty,float tz,float sx,float sy,float sz) {
        double x,y,z,w;
        if(interpolation==Interpolation.LINEAR&&key<times.length-1&&time>times[key]) {
            int a=key*4,b=a+4;double t=(time-times[key])/((double)times[key+1]-times[key]);
            double dot=0, normA=0, normB=0;
            for(int c=0;c<4;c++) {
                dot+=(double)values[a+c]*values[b+c];
                normA+=(double)values[a+c]*values[a+c];
                normB+=(double)values[b+c]*values[b+c];
            }
            normA=Math.sqrt(normA);normB=Math.sqrt(normB);dot/=normA*normB;
            double sign=dot<0?-1:1;dot=Math.min(1,Math.abs(dot));double left,right;
            if(dot>.9995){left=1-t;right=t*sign;}
            else {double angle=Math.acos(dot),sin=Math.sin(angle);left=Math.sin((1-t)*angle)/sin;right=Math.sin(t*angle)/sin*sign;}
            left/=normA;right/=normB;
            x=left*values[a]+right*values[b];y=left*values[a+1]+right*values[b+1];
            z=left*values[a+2]+right*values[b+2];w=left*values[a+3]+right*values[b+3];
        } else {x=component(key,time,0);y=component(key,time,1);z=component(key,time,2);w=component(key,time,3);}
        double length=Math.sqrt(x*x+y*y+z*z+w*w);
        if(!Double.isFinite(length)||length<1e-20)throw new FdxException("Interpolated rotation has zero or invalid length");
        float qx=(float)(x/length),qy=(float)(y/length),qz=(float)(z/length),qw=(float)(w/length);
        if(matrix!=null)matrix.setToTrs(tx,ty,tz,qx,qy,qz,qw,sx,sy,sz);
        else {out[offset]=qx;out[offset+1]=qy;out[offset+2]=qz;out[offset+3]=qw;}
    }
}
