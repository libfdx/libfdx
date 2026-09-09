package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;

/**
 * Reusable conservative view-frustum test for affine, local-space bounds. Application-owned,
 * single-thread confined and allocation-free after construction. update snapshots the camera;
 * call it again after camera changes. No camera, frame, mesh or renderable references are retained.
 * Uncertain bounds/transforms stay visible; this does not perform occlusion or animation bounds updates.
 */
public final class FrustumCuller3D {
    private final float[] projection=new float[16],view=new float[16],world=new float[16];
    private final double[] combined=new double[16],planes=new double[24];
    private double eyeX,eyeY,eyeZ;
    private boolean ready;

    /** Captures projection, orientation and eye using the camera's clip-depth range, including infinite reversed depth.
     * An invalid camera fails and invalidates the previous snapshot. */
    public FrustumCuller3D update(Camera camera) {
        ready=false;
        if(camera==null)throw new FdxException("Frustum camera cannot be null");
        camera.projectionMatrix().copyValues(projection,0);camera.view().copyValues(view,0);
        eyeX=camera.position().x();eyeY=camera.position().y();eyeZ=camera.position().z();
        if(!Double.isFinite(eyeX)||!Double.isFinite(eyeY)||!Double.isFinite(eyeZ))throw new FdxException("Frustum eye must be finite");
        // Keep translation out of the projection product: relative positions retain local detail in far worlds.
        for(int column=0;column<4;column++)for(int row=0;row<4;row++) {
            double value=column==3?projection[12+row]:0;
            if(column!=3)for(int k=0;k<3;k++)value+=projection[k*4+row]*(double)view[column*4+k];
            if(!Double.isFinite(value))throw new FdxException("Frustum projection must be finite");
            combined[column*4+row]=value;
        }
        plane(0,3,0,1);plane(1,3,0,-1);plane(2,3,1,1);plane(3,3,1,-1);
        plane(4,camera.clipDepthRange().isZeroToOne()?-1:3,2,1);plane(5,3,2,-1);
        ready=true;return this;
    }
    private void plane(int index,int base,int row,int sign) {
        int offset=index*4;
        for(int c=0;c<4;c++)planes[offset+c]=(base<0?0:combined[c*4+base])+sign*combined[c*4+row];
        double x=planes[offset],y=planes[offset+1],z=planes[offset+2];
        double length=Math.sqrt(x*x+y*y+z*z);
        if(length==0) {
            if(planes[offset+3]<0)throw new FdxException("Frustum contains an impossible clip plane");
            planes[offset+3]=0; // An infinite far plane imposes no half-space constraint.
        } else for(int c=0;c<4;c++)planes[offset+c]/=length;
    }
    /** Tests the renderable's explicit culling bounds. Skinned renderables default to always visible. */
    public boolean isVisible(Renderable3D renderable) {
        if(renderable==null)throw new FdxException("Renderable cannot be null");
        return isVisible(renderable.cullingBounds(),renderable.worldTransform());
    }
    /** Conservative intersection; null/invalid bounds and nonaffine/invalid transforms stay visible.
     * A null transform means identity. Bounds describe positions after any deformation, before this transform.
     * Touching a plane counts as visible. */
    public boolean isVisible(BoundingBox bounds,Matrix4 transform) {
        if(!ready)throw new FdxException("Frustum must be updated before use");
        if(bounds==null)return true;
        var min=bounds.min();var max=bounds.max();
        double cx=((double)min.x()+max.x())*.5,cy=((double)min.y()+max.y())*.5,cz=((double)min.z()+max.z())*.5;
        double ex=((double)max.x()-min.x())*.5,ey=((double)max.y()-min.y())*.5,ez=((double)max.z()-min.z())*.5;
        if(ex<0||ey<0||ez<0||!Double.isFinite(cx)||!Double.isFinite(cy)||!Double.isFinite(cz)
                ||!Double.isFinite(ex)||!Double.isFinite(ey)||!Double.isFinite(ez))return true;
        (transform==null?Matrix4.IDENTITY:transform).copyValues(world,0);
        for(int i=0;i<16;i++)if(!Float.isFinite(world[i]))return true;
        if(world[3]!=0||world[7]!=0||world[11]!=0||world[15]!=1)return true;
        double x=((double)world[12]-eyeX)+world[0]*cx+world[4]*cy+world[8]*cz;
        double y=((double)world[13]-eyeY)+world[1]*cx+world[5]*cy+world[9]*cz;
        double z=((double)world[14]-eyeZ)+world[2]*cx+world[6]*cy+world[10]*cz;
        for(int i=0;i<24;i+=4) {
            double nx=planes[i],ny=planes[i+1],nz=planes[i+2],d=planes[i+3];
            double radius=Math.abs(nx*world[0]+ny*world[1]+nz*world[2])*ex
                    +Math.abs(nx*world[4]+ny*world[5]+nz*world[6])*ey
                    +Math.abs(nx*world[8]+ny*world[9]+nz*world[10])*ez;
            double signed=nx*x+ny*y+nz*z+d;
            double slack=1e-5+1e-6*(Math.abs(nx*x)+Math.abs(ny*y)+Math.abs(nz*z)+Math.abs(d)+radius);
            if(signed+radius < -slack)return false;
        }
        return true;
    }
}
