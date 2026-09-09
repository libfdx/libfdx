package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.graphics.particles.ParticleSolidRenderer;
import io.github.libfdx.graphics.particles.ParticleVolume;

/** Two solid colors at three fixed depths; reversing the camera must reverse visibility. */
public final class ParticleSolidOcclusionFixture {
    public static void media(String mode, ParticleVolume fire, ParticleVolume smoke) {
        if (mode.startsWith("clear")) return;
        if (mode.startsWith("stack")) {
            smoke.add(0,0,0,0.8f,0.5f,0,ParticleVolume.Medium.SMOKE); return;
        }
        for (int i=0;i<6;i++) {
            if (mode.startsWith("fire")) fire.add(i-2.5f,0,0,0.46f,8,1,ParticleVolume.Medium.FIRE);
            else smoke.add(i-2.5f,0,0,0.46f,2,0,ParticleVolume.Medium.SMOKE);
        }
    }

    public static void solids(String mode, ParticleSolidRenderer renderer) {
        if (mode.endsWith("empty")) return;
        if (mode.startsWith("stack")) {
            for (int j=0;j<2;j++) {
                int i=mode.endsWith("swapped") ? 1-j : j;
                renderer.add(0,0,i==0 ? -0.6f : 0.6f,0.3f,i==0 ? 1 : 0,0,i==0 ? 0 : 1,0.6f);
            }
            return;
        }
        // Reverse submission as well as camera in the alternate case.
        for (int j=0;j<6;j++) {
            int i=mode.contains("reverse") ? 5-j : j;
            boolean snow=i<3;
            renderer.add(i-2.5f,0,1-i%3,0.13f,snow ? 0.8f : 1,snow ? 0.88f : 0.6f,snow ? 1 : 0.12f,1);
        }
    }
}
