package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.graphics.g3d.*;
import java.util.Arrays;

/** Original scene geometry. Every reusable prop has its base at local Y=0. */
public final class FogSceneGeometry {
    private float[] positions = new float[4096], colors = new float[4096];
    private int vertices;

    private void vertex(float x, float y, float z, float r, float g, float b) {
        if ((vertices + 1) * 4 > colors.length) {
            positions = Arrays.copyOf(positions, positions.length * 2);
            colors = Arrays.copyOf(colors, colors.length * 2);
        }
        int p = vertices * 3, c = vertices++ * 4;
        positions[p] = x; positions[p + 1] = y; positions[p + 2] = z;
        colors[c] = r; colors[c + 1] = g; colors[c + 2] = b; colors[c + 3] = 1;
    }

    private void box(float x, float y, float z, float w, float h, float d, float r, float g, float b) {
        float[] xyz = {x-w/2,y,z-d/2, x+w/2,y,z-d/2, x+w/2,y+h,z-d/2, x-w/2,y+h,z-d/2,
                x-w/2,y,z+d/2, x+w/2,y,z+d/2, x+w/2,y+h,z+d/2, x-w/2,y+h,z+d/2};
        int[] indices = {0,2,1,0,3,2,4,5,6,4,6,7,0,4,7,0,7,3,1,2,6,1,6,5,3,7,6,3,6,2,0,1,5,0,5,4};
        for (int i : indices) vertex(xyz[i*3], xyz[i*3+1], xyz[i*3+2], r,g,b);
    }

    private void taper(float y, float height, float lower, float upper, float r, float g, float b) {
        int slices = 12;
        for (int i = 0; i < slices; i++) {
            float a = (float)(i*Math.PI*2/slices), next = (float)((i+1)*Math.PI*2/slices);
            float ax = (float)Math.cos(a), az = (float)Math.sin(a), bx = (float)Math.cos(next), bz = (float)Math.sin(next);
            float shade = 1 + .035f * (float)Math.sin(i * 2.3);
            vertex(ax*lower,y,az*lower,r*shade,g*shade,b*shade);
            vertex(ax*upper,y+height,az*upper,r*shade,g*shade,b*shade);
            vertex(bx*lower,y,bz*lower,r*shade,g*shade,b*shade);
            if (upper > 0) {
                vertex(ax*upper,y+height,az*upper,r,g,b);
                vertex(bx*upper,y+height,bz*upper,r,g,b);
                vertex(bx*lower,y,bz*lower,r,g,b);
            }
            vertex(0,y,0,r*.7f,g*.7f,b*.7f);
            vertex(ax*lower,y,az*lower,r*.7f,g*.7f,b*.7f);
            vertex(bx*lower,y,bz*lower,r*.7f,g*.7f,b*.7f);
        }
    }

    private Model build(ModelBuilder builder, String name) {
        Material material = new Material(name, MaterialAttributes.baseColor(1,1,1,1),
                PbrAttributes.roughnessFactor(.86f), PbrAttributes.metallicFactor(0));
        return builder.material(material).triangles(name, Arrays.copyOf(positions, vertices*3), null,
                Arrays.copyOf(colors, vertices*4), ModelVertexUsage.STANDARD_PBR);
    }

    public static Model pine(ModelBuilder builder) {
        FogSceneGeometry mesh = new FogSceneGeometry();
        mesh.taper(0,.25f,.43f,.24f,.22f,.12f,.065f);
        mesh.taper(.25f,1.55f,.24f,.13f,.27f,.16f,.09f);
        mesh.taper(.95f,1.8f,FogSceneLayout.TREE_RADIUS,0,.08f,.25f,.17f);
        mesh.taper(1.75f,1.65f,.88f,0,.11f,.32f,.21f);
        mesh.taper(2.6f,1.35f,.59f,0,.16f,.38f,.24f);
        return mesh.build(builder,"layered pine");
    }

    public static Model wall(ModelBuilder builder) {
        FogSceneGeometry mesh = new FogSceneGeometry();
        mesh.box(0,0,0,FogSceneLayout.WALL_HALF_WIDTH*2,.22f,
                FogSceneLayout.WALL_HALF_DEPTH*2,.30f,.32f,.29f);
        for (int row=0;row<4;row++) {
            int col=0;
            float shade=1 + .04f*(float)Math.sin(row*5+col*7);
            mesh.box(0,.22f+row*.49f,0,1.11f,.47f,1.06f,
                    .43f*shade,.46f*shade,.42f*shade);
        }
        mesh.box(0,2.18f,0,1.2f,.18f,1.25f,.59f,.57f,.47f);
        mesh.box(0,2.36f,0,.7f,.48f,1.15f,.48f,.50f,.44f);
        return mesh.build(builder,"courtyard wall");
    }

    public static Model pillar(ModelBuilder builder) {
        FogSceneGeometry mesh=new FogSceneGeometry();
        mesh.box(0,0,0,1.35f,.25f,1.35f,.37f,.40f,.36f);
        mesh.box(0,.25f,0,.95f,2.7f,.95f,.48f,.51f,.45f);
        mesh.box(0,2.95f,0,FogSceneLayout.PILLAR_HALF_SIZE*2,.26f,
                FogSceneLayout.PILLAR_HALF_SIZE*2,.66f,.61f,.47f);
        mesh.box(0,3.21f,0,.75f,.18f,.75f,.41f,.44f,.39f);
        return mesh.build(builder,"gate pillar");
    }

    public static Model rock(ModelBuilder builder) {
        FogSceneGeometry mesh=new FogSceneGeometry();
        // A flat base rests on the terrain; no sphere buried in the floor.
        mesh.taper(0,.35f,.62f,FogSceneLayout.ROCK_RADIUS,.29f,.33f,.28f);
        mesh.taper(.35f,.65f,FogSceneLayout.ROCK_RADIUS,.38f,.32f,.35f,.30f);
        mesh.taper(1,.18f,.38f,0,.35f,.38f,.32f);
        return mesh.build(builder,"grounded boulder");
    }

    public static Model ground(ModelBuilder builder) {
        FogSceneGeometry mesh=new FogSceneGeometry();
        int[] corners={0,2,3,0,3,1};
        for(int row=0;row<FogExploration.DEPTH*2;row++) for(int col=0;col<FogExploration.WIDTH*2;col++) {
            for(int corner:corners) {
                float x=(col+corner%2)*.5f-FogExploration.WIDTH/2f;
                float z=(row+corner/2)*.5f-FogExploration.DEPTH/2f;
                float path=Math.max(0,Math.min(1,(1.8f-Math.min(Math.abs(x),Math.abs(z+3)))*2));
                float variation=1+.07f*(float)(Math.sin(x*.83)*Math.cos(z*.63))+.025f*(float)Math.sin(x*4+z*3);
                float r=(.15f*(1-path)+.39f*path)*variation;
                float g=(.25f*(1-path)+.34f*path)*variation;
                float b=(.15f*(1-path)+.25f*path)*variation;
                mesh.vertex(x,0,z,r,g,b);
            }
        }
        // Raised pavers give the road crisp joints and a physical edge.
        for(int row=0;row<44;row++) for(int col=0;col<3;col++) {
            float shade=1+.07f*(float)Math.sin(row*8+col*3);
            mesh.box((col-1)*1.02f,0,-19.4f+row*.9f,.97f,.025f,.85f,
                    .39f*shade,.36f*shade,.28f*shade);
        }
        for(int col=0;col<52;col++) {
            float x=-23.1f+col*.9f;
            if(Math.abs(x)<2)continue;
            for(int row=0;row<3;row++) {
                float shade=1+.06f*(float)Math.sin(row*6+col*5);
                mesh.box(x,0,-3+(row-1)*.95f,.85f,.025f,.90f,
                        .39f*shade,.36f*shade,.28f*shade);
            }
        }
        return mesh.build(builder,"forest floor and stone paths");
    }
}
