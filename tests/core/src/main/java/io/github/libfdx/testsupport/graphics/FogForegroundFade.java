package io.github.libfdx.testsupport.graphics;

/** Short camera-side obstruction fades restricted to scenery in black, unexplored fog. */
public final class FogForegroundFade {
    static final float SECONDS = .12f;
    private FogForegroundFade() { }

    public static float target(float cameraX, float cameraY, float cameraZ, float playerX, float playerZ,
            float x, float z, float radius) {
        float dx=playerX-cameraX,dz=playerZ-cameraZ;
        float length=(float)Math.sqrt(dx*dx+dz*dz);
        if(length<.001f)return 1;
        // Looking down from above should retain the scenery beneath the camera.
        float angle=smooth((length/Math.max(.001f,Math.abs(cameraY-.65f))-.6f)/.7f);
        float farEdge=((x-cameraX)*dx+(z-cameraZ)*dz)/length+radius;
        float plane=smooth((farEdge-length+2.5f+.4f)/.8f);
        return 1-angle*(1-plane);
    }

    public static float advance(float alpha,float target,float delta) {
        // Retain intermediate frames even when creating a layer pipeline stalls rendering.
        float step=Math.min(1f/3,Math.max(0,delta)/SECONDS);
        return alpha<target?Math.min(target,alpha+step):Math.max(target,alpha-step);
    }

    /** Surface opacity: remembered/cleared samples stay solid; black samples use camera alpha. */
    public static float surfaceAlpha(float cameraAlpha, float fogOpacity) {
        return 1 - (1 - cameraAlpha) * smooth((fogOpacity - .5f) / .5f);
    }

    private static float smooth(float value) {
        float t=Math.max(0,Math.min(1,value));
        return t*t*(3-2*t);
    }
}
