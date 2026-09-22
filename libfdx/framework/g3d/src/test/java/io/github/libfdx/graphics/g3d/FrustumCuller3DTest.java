package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.camera.*;
import io.github.libfdx.math.*;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

final class FrustumCuller3DTest {
    private static final BoundingBox POINT=BoundingBox.empty();
    private static final BoundingBox BOX=BoundingBox.of(new Vector3(-.5f,-.5f,-.5f),new Vector3(.5f,.5f,.5f));
    @Test void allClipRangesAndProjectionPlanesIncludeTheirBoundaries() {
        ClipDepthRange saved=ClipDepthRange.getDefault();ClipDepthRange.setDefault(ClipDepthRange.ZERO_TO_ONE);
        try {
            for(var range:ClipDepthRange.values())for(var projection:CameraProjection.values()) {
                var camera=new Camera().projection(projection).viewport(4,4).fieldOfView(90).position(0,0,0)
                        .direction(0,0,-1).nearFar(1,10).clipDepthRange(range);
                var culler=new FrustumCuller3D().update(camera);
                for(float sign:new float[]{-1,1}) {
                    assertTrue(point(culler,sign*2,0,-2));assertFalse(point(culler,sign*2.01f,0,-2));
                    assertTrue(point(culler,0,sign*2,-2));assertFalse(point(culler,0,sign*2.01f,-2));
                }
                assertTrue(point(culler,0,0,-1));assertTrue(point(culler,0,0,-10));
                assertFalse(point(culler,0,0,-.99f));assertFalse(point(culler,0,0,-10.01f));assertFalse(point(culler,0,0,2));
                camera.position(100,0,0);assertTrue(point(culler,0,0,-2)); // A deliberate snapshot.
                culler.update(camera);assertFalse(point(culler,0,0,-2));assertTrue(point(culler,100,0,-2));
            }
            var camera=new Camera().projection(CameraProjection.PERSPECTIVE).position(0,0,0).nearFar(1,0)
                    .clipDepthRange(ClipDepthRange.ZERO_TO_ONE_REVERSED);
            var culler=new FrustumCuller3D().update(camera);
            assertTrue(point(culler,0,0,-1e20f));assertFalse(point(culler,0,0,-.9f));
        } finally {ClipDepthRange.setDefault(saved);}
    }
    @Test void cameraRelativePlanesRetainLocalBoundsAtLargeCoordinatesAndUncertainInputsStayVisible() {
        float position=1e11f;
        var camera=new Camera().position(position,position,position).direction(0,0,1).viewport(4,4).nearFar(.1f,10);
        var culler=new FrustumCuller3D().update(camera);
        var transform=new Matrix4().setToTranslation(position,position,position);
        var inside=BoundingBox.of(new Vector3(-.1f,-.1f,1),new Vector3(.1f,.1f,2));
        var outside=BoundingBox.of(new Vector3(3,-.1f,1),new Vector3(4,.1f,2));
        assertTrue(culler.isVisible(inside,transform));assertFalse(culler.isVisible(outside,transform));
        assertTrue(culler.isVisible(null,transform));
        assertTrue(culler.isVisible(BoundingBox.of(new Vector3(Float.NaN,0,0),new Vector3()),transform));
        assertTrue(culler.isVisible(BoundingBox.of(new Vector3(2,2,2),new Vector3(-1,-1,-1)),transform));
        float[] values=new float[16];transform.copyValues(values,0);values[3]=1;
        assertTrue(culler.isVisible(outside,new Matrix4(values)));
        assertThrows(FdxException.class,()->culler.update(null));
        assertThrows(FdxException.class,()->culler.isVisible(null,null));
    }
    @Test void randomAffineBoxesMatchIndependentCornerClipTestsIncludingMirrorsAndShear() {
        Random random=new Random(640091);var camera=new Camera().projection(CameraProjection.PERSPECTIVE).viewport(3,2)
                .nearFar(.1f,50).position(3,4,5).direction(-.2f,-.1f,-1);
        var culler=new FrustumCuller3D().update(camera);float[] projection=new float[16];camera.combined().copyValues(projection,0);
        float[] transform=new float[16];int extra=0;
        for(int i=0;i<5000;i++) {
            for(int j=0;j<16;j++)transform[j]=0;
            for(int column=0;column<3;column++)for(int row=0;row<3;row++)transform[column*4+row]=random.nextFloat()*4-2;
            transform[12]=random.nextFloat()*80-40;transform[13]=random.nextFloat()*80-40;transform[14]=random.nextFloat()*80-60;transform[15]=1;
            boolean expected=cornersVisible(projection,transform,camera.clipDepthRange().isZeroToOne());
            boolean actual=culler.isVisible(BOX,new Matrix4(transform));
            if(expected)assertTrue(actual,"False negative at box "+i);else if(actual)extra++;
        }
        assertTrue(extra<5,"Unexpected conservative overdraw: "+extra);
    }
    @Test void stableQueueCompactionSkipsUnknownSkinBoundsAndHonorsExplicitOverrides() {
        var graphics=new DefaultRenderQueue3DReuseTest.FakeGraphicsContext();
        Mesh mesh=Mesh.coloredTriangle(graphics,"bounds");
        try {
            var part=new MeshPart(mesh,0,3);var material=new Material("bounds");
            var a=new Renderable3D(part,material,new Matrix4().setToTranslation(0,0,-2),BOX);
            var b=new Renderable3D(part,material,new Matrix4().setToTranslation(100,0,-2),BOX);
            var skin=new SkinningPalette(new Skin("empty",new Skeleton(new Array<Bone>())));
            var animated=new Renderable3D(part,material,b.worldTransform(),BOX,skin);
            assertNull(animated.cullingBounds());
            var culler=new FrustumCuller3D().update(new Camera().viewport(4,4).position(0,0,0));
            var queue=new DefaultRenderQueue3D();queue.add(a);queue.add(b);queue.add(animated);
            assertEquals(1,queue.cull(culler));assertSame(a,queue.get(0));assertSame(animated,queue.get(1));
            animated.cullingBounds(BOX);assertEquals(1,queue.cull(culler));assertEquals(1,queue.size());
            b.cullingBounds(null);queue.add(b);assertEquals(0,queue.cull(culler));
        } finally {mesh.dispose();}
    }
    @Test void repeatedCameraRefreshPreservesVisibility() {
        var culler=new FrustumCuller3D();var camera=new Camera().viewport(4,4).position(0,0,0);
        var transform=new Matrix4().setToTranslation(0,0,-3);
        int visible=0;
        for(int i=0;i<2000;i++){culler.update(camera);if(culler.isVisible(BOX,transform))visible++;}
        assertEquals(2000,visible);
    }
    private static boolean point(FrustumCuller3D culler,float x,float y,float z) {
        return culler.isVisible(POINT,new Matrix4().setToTranslation(x,y,z));
    }
    private static boolean cornersVisible(float[] projection,float[] world,boolean zeroToOne) {
        double[][] clip=new double[8][4];
        for(int corner=0;corner<8;corner++) {
            double x=(corner&1)==0?-.5:.5,y=(corner&2)==0?-.5:.5,z=(corner&4)==0?-.5:.5;
            double[] p={world[0]*x+world[4]*y+world[8]*z+world[12],world[1]*x+world[5]*y+world[9]*z+world[13],
                    world[2]*x+world[6]*y+world[10]*z+world[14],1};
            for(int row=0;row<4;row++)for(int col=0;col<4;col++)clip[corner][row]+=projection[col*4+row]*p[col];
        }
        for(int plane=0;plane<6;plane++) {
            boolean allOutside=true;
            for(double[] v:clip) {
                double value=switch(plane){case 0->v[3]+v[0];case 1->v[3]-v[0];case 2->v[3]+v[1];case 3->v[3]-v[1];
                    case 4->zeroToOne?v[2]:v[3]+v[2];default->v[3]-v[2];};
                if(value>=0){allOutside=false;break;}
            }
            if(allOutside)return false;
        }
        return true;
    }
}
