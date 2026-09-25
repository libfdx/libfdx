package io.github.libfdx.graphics.meshoptimizer;

/** Independent geometric quadrics in each sampled pose. Candidate cost is the worst pose, so
 * errors do not average away a bad bend. Endpoint provenance preserves deformation attributes. */
final class LodDeformationQuadric {
    private final MeshLodData source;
    private final MeshLodDeformation data;
    private final double scale;
    private final double[][] quadrics;
    private final double[] origin;
    private int initialized;
    LodDeformationQuadric(MeshLodData source,double scale) {
        this.source=source;this.data=source.deformation();this.scale=scale;
        quadrics=new double[data.poseCount()][];origin=new double[data.poseCount()*3];
        for(int p=0;p<data.poseCount();p++) for(int c=0;c<3;c++) origin[p*3+c]=data.position(p,source.sourceVertex(0),c);
    }
    boolean initializeStep() {
        if (initialized==quadrics.length) return true;
        quadrics[initialized++]=new double[Math.multiplyExact(source.vertexCount(),11)];return false;
    }
    private double x(int p,int v,int c) { return (data.position(p,source.sourceVertex(v),c)-origin[p*3+c])/scale; }
    void triangle(int a,int b,int c) {
        for (int p=0;p<quadrics.length;p++) {
            double ax=x(p,a,0),ay=x(p,a,1),az=x(p,a,2);
            double ux=x(p,b,0)-ax,uy=x(p,b,1)-ay,uz=x(p,b,2)-az,vx=x(p,c,0)-ax,vy=x(p,c,1)-ay,vz=x(p,c,2)-az;
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,len=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if (len<1e-20) continue;
            nx/=len;ny/=len;nz/=len;double h=-(nx*ax+ny*ay+nz*az),area=len*.5;
            plane(p,a,nx,ny,nz,h,area);plane(p,b,nx,ny,nz,h,area);plane(p,c,nx,ny,nz,h,area);
            quadrics[p][a*11+10]+=area;quadrics[p][b*11+10]+=area;quadrics[p][c*11+10]+=area;
        }
    }
    void border(int a,int b,int c) {
        for (int p=0;p<quadrics.length;p++) {
            double ax=x(p,a,0),ay=x(p,a,1),az=x(p,a,2),ux=x(p,b,0)-ax,uy=x(p,b,1)-ay,uz=x(p,b,2)-az;
            double vx=x(p,c,0)-ax,vy=x(p,c,1)-ay,vz=x(p,c,2)-az,len=ux*ux+uy*uy+uz*uz;
            if (len<1e-20) continue;
            double dot=(ux*vx+uy*vy+uz*vz)/len,nx=vx-dot*ux,ny=vy-dot*uy,nz=vz-dot*uz,n=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if (n<1e-20) continue;
            nx/=n;ny/=n;nz/=n;plane(p,a,nx,ny,nz,-(nx*ax+ny*ay+nz*az),Math.sqrt(len)*10);
        }
    }
    private void plane(int p,int v,double x,double y,double z,double h,double w) {
        double[] q=quadrics[p];int i=v*11;
        q[i]+=x*x*w;q[i+1]+=x*y*w;q[i+2]+=x*z*w;q[i+3]+=x*h*w;
        q[i+4]+=y*y*w;q[i+5]+=y*z*w;q[i+6]+=y*h*w;q[i+7]+=z*z*w;q[i+8]+=z*h*w;q[i+9]+=h*h*w;
    }
    double error(int a,int b) {
        double error=0;
        for (int p=0;p<quadrics.length;p++) {
            double[] q=quadrics[p];double area=q[a*11+10]+q[b*11+10];
            if (area>1e-30) error=Math.max(error,Math.max(0,evaluate(p,a,b)+evaluate(p,b,b))/area);
        }
        return error;
    }
    private double evaluate(int p,int v,int target) {
        double[] q=quadrics[p];int i=v*11;double x=x(p,target,0),y=x(p,target,1),z=x(p,target,2);
        return q[i]*x*x+2*q[i+1]*x*y+2*q[i+2]*x*z+2*q[i+3]*x+q[i+4]*y*y+2*q[i+5]*y*z+2*q[i+6]*y+q[i+7]*z*z+2*q[i+8]*z+q[i+9];
    }
    void collapse(int from,int to) { for (double[] q : quadrics) for(int i=0;i<11;i++) q[to*11+i]+=q[from*11+i]; }
    boolean oriented(int a,int b,int c,int from,int to) {
        for (int p=0;p<quadrics.length;p++) {
            double ax=x(p,a,0),ay=x(p,a,1),az=x(p,a,2),ux=x(p,b,0)-ax,uy=x(p,b,1)-ay,uz=x(p,b,2)-az;
            double vx=x(p,c,0)-ax,vy=x(p,c,1)-ay,vz=x(p,c,2)-az;
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,n=nx*nx+ny*ny+nz*nz;
            if (n<1e-30) continue;
            int aa=a==from?to:a,bb=b==from?to:b,cc=c==from?to:c;
            ax=x(p,aa,0);ay=x(p,aa,1);az=x(p,aa,2);ux=x(p,bb,0)-ax;uy=x(p,bb,1)-ay;uz=x(p,bb,2)-az;
            vx=x(p,cc,0)-ax;vy=x(p,cc,1)-ay;vz=x(p,cc,2)-az;
            double mx=uy*vz-uz*vy,my=uz*vx-ux*vz,mz=ux*vy-uy*vx,m=mx*mx+my*my+mz*mz;
            if (m<1e-30 || nx*mx+ny*my+nz*mz < .1*Math.sqrt(n*m)) return false;
        }
        return true;
    }
}
