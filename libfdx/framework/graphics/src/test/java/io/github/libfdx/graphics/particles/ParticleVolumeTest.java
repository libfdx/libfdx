package io.github.libfdx.graphics.particles;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ParticleVolumeTest {
    @Test void sphericalDensityHasDepthAndIsCameraIndependent() {
        ParticleVolume volume = new ParticleVolume(16,16,16).bounds(-1,-1,-1,2,2,2);
        volume.add(0,0,0,0.6f,1,0.8f,ParticleVolume.Medium.FIRE);
        int nonemptySlices = 0;
        for (int z=0; z<16; z++) {
            float slice=0;
            for (int y=0;y<16;y++) for (int x=0;x<16;x++) slice+=volume.field[((z*16+y)*16+x)*4];
            if(slice>0) nonemptySlices++;
        }
        assertTrue(nonemptySlices >= 8, "A particle occupies a volume, not a plane");
        assertEquals(volume.field[((8*16+8)*16+10)*4],volume.field[((10*16+8)*16+8)*4],0.00001f);
        assertSame(volume.pixels,volume.pack());
        volume.clear();
        for(float value:volume.field) assertEquals(0,value);
    }

    @Test void overlappingMediaAccumulateAndOutOfBoundsDoesNotWrap() {
        ParticleVolume volume = new ParticleVolume(8,8,8).bounds(0,0,0,1,1,1);
        volume.add(0.5f,0.5f,0.5f,0.3f,1,0.7f,ParticleVolume.Medium.FIRE);
        float before=volume.field[((4*8+4)*8+4)*4];
        volume.add(0.5f,0.5f,0.5f,0.3f,1,0.7f,ParticleVolume.Medium.FIRE);
        assertEquals(before*2,volume.field[((4*8+4)*8+4)*4]);
        volume.add(0.5f,0.5f,0.5f,0.3f,1,0,ParticleVolume.Medium.SMOKE);
        assertTrue(volume.field[((4*8+4)*8+4)*4+2]>0);
        volume.clear();
        volume.add(10,10,10,0.1f,1,1,ParticleVolume.Medium.FIRE);
        for(float value:volume.field) assertEquals(0,value);
        assertThrows(FdxException.class,()->volume.add(0,0,0,Float.NaN,1,1,ParticleVolume.Medium.FIRE));
        assertThrows(FdxException.class,()->volume.bounds(0,0,0,0,1,1));
    }
}
