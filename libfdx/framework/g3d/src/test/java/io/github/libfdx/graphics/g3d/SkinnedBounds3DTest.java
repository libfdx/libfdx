package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SkinnedBounds3DTest {
    private static SkinningPalette palette(int count) {
        Array<Bone> bones=new Array<>();
        for (int i=0;i<count;i++) bones.add(new Bone("joint"+i,-1,new Matrix4()));
        return new SkinningPalette(new Skin("test",new Skeleton(bones)));
    }

    @Test
    void jointBoxesEncloseIndependentlySkinnedVerticesAcrossRandomAffinePoses() {
        Random random=new Random(94013);
        int vertices=128,jointCount=5;
        float[] positions=new float[vertices*3],weights=new float[vertices*4]; int[] joints=new int[weights.length];
        for (int i=0;i<positions.length;i++) positions[i]=(random.nextFloat()-.5f)*20;
        for (int i=0;i<weights.length;i++) { joints[i]=random.nextInt(jointCount); weights[i]=i/4%9==0 ? 0 : random.nextFloat()*3; }
        SkinnedBounds3D prepared=new SkinnedBounds3D(positions,joints,weights,jointCount);
        SkinningPalette palette=palette(jointCount); float[] matrices=palette.valuesUnsafe();
        BoundingBox box=new BoundingBox(new Vector3(),new Vector3());
        for (int pose=0;pose<500;pose++) {
            for (int joint=0;joint<jointCount;joint++) {
                int m=joint*16;
                for (int i=0;i<16;i++) matrices[m+i]=(random.nextFloat()-.5f)*10;
                matrices[m+3]=matrices[m+7]=matrices[m+11]=0; matrices[m+15]=1;
            }
            prepared.update(palette,box);
            for (int v=0;v<vertices;v++) {
                double sum=0; for (int i=0;i<4;i++) sum+=weights[v*4+i];
                for (int axis=0;axis<3;axis++) {
                    double actual=0;
                    if (sum==0) actual=positions[v*3+axis];
                    else for (int i=0;i<4;i++) {
                        int m=joints[v*4+i]*16;
                        double transformed=matrices[m+axis]*positions[v*3]+matrices[m+4+axis]*positions[v*3+1]
                                +matrices[m+8+axis]*positions[v*3+2]+matrices[m+12+axis];
                        actual+=weights[v*4+i]/sum*transformed;
                    }
                    double min=axis==0 ? box.min().x() : axis==1 ? box.min().y() : box.min().z();
                    double max=axis==0 ? box.max().x() : axis==1 ? box.max().y() : box.max().z();
                    assertTrue(actual>=min && actual<=max,"pose="+pose+" vertex="+v+" axis="+axis);
                }
            }
        }
    }

    @Test
    void preparationOwnsItsBoundsAndInvalidPaletteDoesNotChangeOutput() {
        float[] positions={1,2,3},weights={1,0,0,0}; int[] joints={0,0,0,0};
        SkinnedBounds3D prepared=new SkinnedBounds3D(positions,joints,weights,1);
        positions[0]=99; weights[0]=0; joints[0]=12;
        SkinningPalette palette=palette(1); BoundingBox box=new BoundingBox(new Vector3(),new Vector3());
        prepared.update(palette,box);
        assertEquals(1,box.min().x(),1e-4); assertEquals(3,box.max().z(),1e-4);
        palette.valuesUnsafe()[3]=1;
        assertThrows(FdxException.class,()->prepared.update(palette,box));
        assertEquals(1,box.min().x(),1e-4);
        assertThrows(FdxException.class,()->new SkinnedBounds3D(new float[]{0,0,0},new int[]{0,0,0,0},new float[]{-1,0,0,0},1));
        assertThrows(FdxException.class,()->new SkinnedBounds3D(new float[]{0,0,0},new int[]{1,0,0,0},new float[]{1,0,0,0},1));
    }
}
