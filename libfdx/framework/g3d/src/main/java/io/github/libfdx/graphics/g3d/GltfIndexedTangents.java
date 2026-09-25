package io.github.libfdx.graphics.g3d;

/** Angle-weighted indexed tangent preparation for retained editable geometry. */
final class GltfIndexedTangents {
    private GltfIndexedTangents() { }
    /** Leaves ambiguous mirrored charts on the loader's per-corner path. Authored tangents take precedence. */
    static float[] generate(float[] p,float[] n,float[] uv,int[] indices) {
        if (n == null || uv == null) return null;
        int count = p.length/3;
        double[] tangent = new double[count*3], bitangent = new double[count*3];
        int[] orientation = new int[count];
        for (int f = 0; f < indices.length; f += 3) {
            int a=indices[f],b=indices[f+1],c=indices[f+2];
            double ux=p[b*3]-p[a*3],uy=p[b*3+1]-p[a*3+1],uz=p[b*3+2]-p[a*3+2];
            double vx=p[c*3]-p[a*3],vy=p[c*3+1]-p[a*3+1],vz=p[c*3+2]-p[a*3+2];
            double s=uv[b*2]-uv[a*2],t=uv[b*2+1]-uv[a*2+1],r=uv[c*2]-uv[a*2],q=uv[c*2+1]-uv[a*2+1];
            double det=s*q-t*r; if (Math.abs(det)<1e-20) continue;
            double tx=(ux*q-vx*t)/det,ty=(uy*q-vy*t)/det,tz=(uz*q-vz*t)/det;
            double bx=(vx*s-ux*r)/det,by=(vy*s-uy*r)/det,bz=(vz*s-uz*r)/det;
            double tl=Math.sqrt(tx*tx+ty*ty+tz*tz),bl=Math.sqrt(bx*bx+by*by+bz*bz);
            if (!(tl>0) || !(bl>0)) continue;
            tx/=tl; ty/=tl; tz/=tl; bx/=bl; by/=bl; bz/=bl;
            for (int k=0;k<3;k++) {
                int v=indices[f+k],j=indices[f+(k+1)%3],h=indices[f+(k+2)%3],o=v*3;
                int sign=det<0?-1:1;
                if (orientation[v]!=0 && orientation[v]!=sign) return null;
                orientation[v]=sign;
                double ex=p[j*3]-p[o],ey=p[j*3+1]-p[o+1],ez=p[j*3+2]-p[o+2];
                double fx=p[h*3]-p[o],fy=p[h*3+1]-p[o+1],fz=p[h*3+2]-p[o+2];
                double denominator=Math.sqrt((ex*ex+ey*ey+ez*ez)*(fx*fx+fy*fy+fz*fz));
                double angle=denominator>0?Math.acos(Math.max(-1,Math.min(1,(ex*fx+ey*fy+ez*fz)/denominator))):0;
                tangent[o]+=tx*angle; tangent[o+1]+=ty*angle; tangent[o+2]+=tz*angle;
                bitangent[o]+=bx*angle; bitangent[o+1]+=by*angle; bitangent[o+2]+=bz*angle;
            }
        }
        float[] output=new float[count*4];
        for (int v=0;v<count;v++) {
            int o=v*3; double nx=n[o],ny=n[o+1],nz=n[o+2],nn=nx*nx+ny*ny+nz*nz;
            if (!(nn>0)) return null;
            double dot=(nx*tangent[o]+ny*tangent[o+1]+nz*tangent[o+2])/nn;
            double tx=tangent[o]-nx*dot,ty=tangent[o+1]-ny*dot,tz=tangent[o+2]-nz*dot;
            double length=Math.sqrt(tx*tx+ty*ty+tz*tz);
            if (!(length>1e-15)) {
                tx=Math.abs(nx)<.9?1:0; ty=Math.abs(nx)<.9?0:1; tz=0;
                dot=(nx*tx+ny*ty)/nn; tx-=nx*dot; ty-=ny*dot; tz-=nz*dot;
                length=Math.sqrt(tx*tx+ty*ty+tz*tz);
            }
            output[v*4]=(float)(tx/length); output[v*4+1]=(float)(ty/length); output[v*4+2]=(float)(tz/length);
            output[v*4+3]=(ny*tz-nz*ty)*bitangent[o]+(nz*tx-nx*tz)*bitangent[o+1]+(nx*ty-ny*tx)*bitangent[o+2]<0?-1:1;
        }
        return output;
    }
}
