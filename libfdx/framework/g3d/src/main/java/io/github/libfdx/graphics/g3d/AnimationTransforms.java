package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;

/** Internal reusable TRS storage: translation XYZ, quaternion XYZW, scale XYZ. */
final class AnimationTransforms {
    static final int STRIDE = 10;
    private AnimationTransforms() { }

    static Matrix4 matrix(float[] values, int offset, Matrix4 out) {
        return out.setToTrs(values[offset], values[offset+1], values[offset+2],
                values[offset+3], values[offset+4], values[offset+5], values[offset+6],
                values[offset+7], values[offset+8], values[offset+9]);
    }

    static void blend(float[] a, int ai, float[] b, int bi, double t, float[] out, int oi) {
        rotation(a[ai+3], a[ai+4], a[ai+5], a[ai+6], b[bi+3], b[bi+4], b[bi+5], b[bi+6], t, out, oi+3);
        for (int i = 0; i < 3; i++) {
            out[oi+i] = (float)((1-t)*a[ai+i] + t*b[bi+i]);
            out[oi+7+i] = (float)((1-t)*a[ai+7+i] + t*b[bi+7+i]);
        }
    }

    static void rotation(double ax, double ay, double az, double aw, double bx, double by, double bz, double bw,
            double t, float[] out, int offset) {
        double an=Math.sqrt(ax*ax+ay*ay+az*az+aw*aw), bn=Math.sqrt(bx*bx+by*by+bz*bz+bw*bw);
        if (!Double.isFinite(an) || !Double.isFinite(bn) || an < 1e-20 || bn < 1e-20)
            throw new FdxException("Animation rotation must have finite nonzero length");
        ax/=an; ay/=an; az/=an; aw/=an; bx/=bn; by/=bn; bz/=bn; bw/=bn;
        double dot=ax*bx+ay*by+az*bz+aw*bw, sign=dot < 0 ? -1 : 1;
        dot=Math.min(1,Math.abs(dot));
        double left=1-t, right=t*sign;
        if (dot < .9995) {
            double angle=Math.acos(dot), sin=Math.sin(angle);
            left=Math.sin((1-t)*angle)/sin; right=Math.sin(t*angle)/sin*sign;
        }
        double x=left*ax+right*bx, y=left*ay+right*by, z=left*az+right*bz, w=left*aw+right*bw;
        double norm=Math.sqrt(x*x+y*y+z*z+w*w);
        out[offset]=(float)(x/norm); out[offset+1]=(float)(y/norm); out[offset+2]=(float)(z/norm); out[offset+3]=(float)(w/norm);
    }

    /** Setup-only decomposition for programmatic matrix defaults. Imported tracks retain authored TRS. */
    static void decompose(Matrix4 matrix, float[] out, int offset, float[] scratch) {
        matrix.copyValues(scratch,0);
        for (float value : scratch) if (!Float.isFinite(value)) throw new FdxException("Animation defaults must be finite");
        if (scratch[3] != 0 || scratch[7] != 0 || scratch[11] != 0 || scratch[15] != 1)
            throw new FdxException("Animation defaults must be affine TRS matrices");
        double x0=scratch[0], x1=scratch[1], x2=scratch[2], y0=scratch[4], y1=scratch[5], y2=scratch[6];
        double z0=scratch[8], z1=scratch[9], z2=scratch[10];
        double sx=Math.sqrt(x0*x0+x1*x1+x2*x2), sy=Math.sqrt(y0*y0+y1*y1+y2*y2), sz=Math.sqrt(z0*z0+z1*z1+z2*z2);
        if (Math.abs(x0*y0+x1*y1+x2*y2) > 1e-4*sx*sy || Math.abs(x0*z0+x1*z1+x2*z2) > 1e-4*sx*sz
                || Math.abs(y0*z0+y1*z1+y2*z2) > 1e-4*sy*sz)
            throw new FdxException("Animation defaults cannot contain shear");
        if (x0*(y1*z2-y2*z1)+x1*(y2*z0-y0*z2)+x2*(y0*z1-y1*z0) < 0) sx=-sx;
        if (sx != 0) { x0/=sx; x1/=sx; x2/=sx; }
        if (sy != 0) { y0/=sy; y1/=sy; y2/=sy; }
        if (sz != 0) { z0/=sz; z1/=sz; z2/=sz; }
        // A zero scale loses an axis' rotation information. Complete any orthonormal basis that
        // reconstructs the matrix; imported channels keep their original, unambiguous TRS instead.
        if (sx == 0 && sy == 0 && sz == 0) { x0=y1=z2=1; }
        else if (sx != 0 && sy == 0 && sz == 0) {
            if (Math.abs(x2) < .9) { y0=-x1; y1=x0; y2=0; } else { y0=x2; y1=0; y2=-x0; }
            double length=Math.sqrt(y0*y0+y1*y1+y2*y2); y0/=length; y1/=length; y2/=length;
            z0=x1*y2-x2*y1; z1=x2*y0-x0*y2; z2=x0*y1-x1*y0;
        } else if (sy != 0 && sx == 0 && sz == 0) {
            if (Math.abs(y0) < .9) { z0=0; z1=-y2; z2=y1; } else { z0=-y1; z1=y0; z2=0; }
            double length=Math.sqrt(z0*z0+z1*z1+z2*z2); z0/=length; z1/=length; z2/=length;
            x0=y1*z2-y2*z1; x1=y2*z0-y0*z2; x2=y0*z1-y1*z0;
        } else if (sz != 0 && sx == 0 && sy == 0) {
            if (Math.abs(z1) < .9) { x0=z2; x1=0; x2=-z0; } else { x0=0; x1=-z2; x2=z1; }
            double length=Math.sqrt(x0*x0+x1*x1+x2*x2); x0/=length; x1/=length; x2/=length;
            y0=z1*x2-z2*x1; y1=z2*x0-z0*x2; y2=z0*x1-z1*x0;
        } else if (sx == 0) { x0=y1*z2-y2*z1; x1=y2*z0-y0*z2; x2=y0*z1-y1*z0; }
        else if (sy == 0) { y0=z1*x2-z2*x1; y1=z2*x0-z0*x2; y2=z0*x1-z1*x0; }
        else if (sz == 0) { z0=x1*y2-x2*y1; z1=x2*y0-x0*y2; z2=x0*y1-x1*y0; }
        double qx,qy,qz,qw,trace=x0+y1+z2;
        if (trace > 0) { double s=Math.sqrt(trace+1)*2; qw=.25*s; qx=(y2-z1)/s; qy=(z0-x2)/s; qz=(x1-y0)/s; }
        else if (x0 > y1 && x0 > z2) { double s=Math.sqrt(1+x0-y1-z2)*2; qw=(y2-z1)/s; qx=.25*s; qy=(y0+x1)/s; qz=(z0+x2)/s; }
        else if (y1 > z2) { double s=Math.sqrt(1+y1-x0-z2)*2; qw=(z0-x2)/s; qx=(y0+x1)/s; qy=.25*s; qz=(z1+y2)/s; }
        else { double s=Math.sqrt(1+z2-x0-y1)*2; qw=(x1-y0)/s; qx=(z0+x2)/s; qy=(z1+y2)/s; qz=.25*s; }
        out[offset]=scratch[12]; out[offset+1]=scratch[13]; out[offset+2]=scratch[14];
        out[offset+3]=(float)qx; out[offset+4]=(float)qy; out[offset+5]=(float)qz; out[offset+6]=(float)qw;
        out[offset+7]=(float)sx; out[offset+8]=(float)sy; out[offset+9]=(float)sz;
    }
}
