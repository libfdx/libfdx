package io.github.libfdx.graphics.meshoptimizer;

import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LodMeshPackingTest {
    @Test void partitionsBeyondUnsignedShortWithoutDroppingOrWrappingTriangles() {
        int count=65538;
        float[][] channels=new float[MeshLodData.CHANNEL_COUNT][];
        channels[0]=new float[count*3];
        int[] indices=new int[count];
        for (int v=0;v<count;v++) { channels[0][v*3]=v; channels[0][v*3+1]=v%3; indices[v]=v; }
        MeshLodData source=new MeshLodData(channels,indices);
        LodPreparedMesh[] parts=LodMeshPacking.pack(source,() -> false);
        assertEquals(2,parts.length);
        int actual=0;
        for (LodPreparedMesh part:parts) {
            assertTrue(part.vertices()<=65536);
            assertEquals(0,part.indices().length%3);
            for (short index:part.indices()) assertTrue((index&65535)<part.vertices());
            actual+=part.indices().length;
        }
        assertEquals(count,actual);
        assertEquals(65535,parts[0].vertices()); assertEquals(3,parts[1].vertices());
        assertEquals(65534,parts[0].indices()[65534]&65535);
        assertArrayEquals(new short[]{0,1,2},parts[1].indices());
        assertThrows(CancellationException.class,() -> LodMeshPacking.pack(source,() -> true));
    }

    @Test void optionsAreDetachedAndRejectAmbiguousTransitions() {
        ModelLodTarget original=new ModelLodTarget(.5f,.01f,240);
        ModelLodTarget[] targets={original};
        ModelLodSettings settings=new ModelLodSettings(targets,.1f,false,.5f,10,1,true,true);
        targets[0]=new ModelLodTarget(.4f,.02f,200);
        settings.targets()[0]=null;
        assertSame(original,settings.target(0));
        assertThrows(IllegalArgumentException.class,() -> new ModelLodSettings(new ModelLodTarget[]{
                original,new ModelLodTarget(.3f,.02f,250)},.1f,false,.5f,10,1,true,true));
        assertThrows(IllegalArgumentException.class,() -> new ModelLodSettings(new ModelLodTarget[9],.1f,false,.5f,10,1,true,true));
    }
}
