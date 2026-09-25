package io.github.libfdx.testsupport.graphics.lodoptimizer;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.ui.UiRect;
import java.nio.ByteBuffer;

/** Fixture checks within the actual image rectangles, excluding text and control pixels. */
final class LodOptimizerFrameCheck {
    static void verifyAnimated(ByteBuffer rgba,int width,int height,LodOptimizerControls controls) {
        UiRect left=controls.imageBounds(0),right=controls.imageBounds(1);
        float sx=width/(float)controls.root().display().width(),sy=height/(float)controls.root().display().height();
        int ax=Math.round(controls.root().displayX(left.x())*sx),bx=Math.round(controls.root().displayX(right.x())*sx);
        int ay=Math.round(controls.root().displayY(left.y())*sy),by=Math.round(controls.root().displayY(right.y())*sy);
        int w=Math.round(controls.root().displayX(Math.min(left.width(),right.width()))*sx);
        int h=Math.round(controls.root().displayY(Math.min(left.height(),right.height()))*sy),intersection=0,union=0;
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            int a=((height-1-ay-y)*width+ax+x)*4,b=((height-1-by-y)*width+bx+x)*4;
            boolean l=(rgba.get(a)&255)>80,r=(rgba.get(b)&255)>80;
            if (l || r) union++;
            if (l && r) intersection++;
        }
        if (union==0 || intersection/(double)union<.80) throw new FdxException("Animated LOD silhouette differs from the current source pose: "+intersection+"/"+union);
    }
    static void verify(ByteBuffer rgba,int width,int height,LodOptimizerControls controls,boolean duck) {
        float sx=width/(float)controls.root().display().width(),sy=height/(float)controls.root().display().height();
        for (int side=0;side<2;side++) {
            UiRect bounds=controls.imageBounds(side);
            int left=Math.max(0,Math.round(controls.root().displayX(bounds.x())*sx));
            int top=Math.max(0,Math.round(controls.root().displayY(bounds.y())*sy));
            int right=Math.min(width,Math.round(controls.root().displayX(bounds.right())*sx));
            int bottom=Math.min(height,Math.round(controls.root().displayY(bounds.bottom())*sy));
            int colored=0,minY=bottom,maxY=top;
            for (int y=top;y<bottom;y++) for (int x=left;x<right;x++) {
                int i=((height-1-y)*width+x)*4;
                if (Math.max(rgba.get(i)&255,Math.max(rgba.get(i+1)&255,rgba.get(i+2)&255))>65) {
                    colored++; minY=Math.min(minY,y); maxY=Math.max(maxY,y);
                }
            }
            if (colored<(right-left)*(bottom-top)/100 || maxY-minY<(bottom-top)/4)
                throw new FdxException("LOD preview "+side+" is blank or too small");
            if (duck) {
                int upper=0,lower=0,mid=(minY+maxY)/2;
                for (int y=top;y<bottom;y++) for (int x=left;x<right;x++) {
                    int i=((height-1-y)*width+x)*4,r=rgba.get(i)&255,g=rgba.get(i+1)&255,b=rgba.get(i+2)&255;
                    if (r>150 && g>55 && g<180 && b<65) { if (y<mid) upper++; else lower++; }
                }
                if (lower<=upper) throw new FdxException("Duck preview "+side+" is upside down: orange feet must be below the body");
            }
        }
    }
}
