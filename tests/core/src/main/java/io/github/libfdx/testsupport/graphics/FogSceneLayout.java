package io.github.libfdx.testsupport.graphics;

/** Shared scene placements and patrol route; regression tests use these exact obstacles. */
public final class FogSceneLayout {
    static final float TREE_RADIUS = 1.15f, ROCK_RADIUS = .8f;
    static final float WALL_HALF_WIDTH = .6f, WALL_HALF_DEPTH = .7f, PILLAR_HALF_SIZE = .7f;
    public static final float[][] ROUTE = {{0,12},{0,-3},{12,-3},{12,-12},{0,-12},{0,-3},{-12,-3},{0,-3},{0,12}};
    @FunctionalInterface public interface Props {
        void add(int model,float x,float y,float z,float scale,float halfWidth,float halfDepth,float footprint);
    }
    public static void populate(Props props) {
        Placement placement = new Placement(props);
        // Build the gate first so generated scenery cannot occupy its footprint.
        for(int side=-1;side<=1;side+=2)for(int section=0;section<4;section++)
            placement.add(2,side*(3.62f+section*1.25f),3,1,WALL_HALF_WIDTH,WALL_HALF_DEPTH);
        placement.add(3,-2.2f,3,1,PILLAR_HALF_SIZE,PILLAR_HALF_SIZE);
        placement.add(3,2.2f,3,1,PILLAR_HALF_SIZE,PILLAR_HALF_SIZE);
        tree(placement,-3,12,1);tree(placement,3,11,.9f);
        tree(placement,-4,8,1.1f);tree(placement,4.2f,7,.95f);
        rock(placement,-2.4f,9,.8f);rock(placement,3.2f,14,.65f);
        for(int row=0;row<6;row++)for(int col=0;col<8;col++) {
            float x=-20+col*5.7f+(float)Math.sin(row*9+col)*.8f;
            float z=-16+row*6.3f+(float)Math.cos(col*4+row)*.7f;
            if(Math.abs(x)<3||Math.abs(z+3)<2.6f||Math.abs(x-12)<2
                    ||Math.abs(x+12)<2||Math.abs(z+12)<1.8f||Math.abs(z-12)<2.1f)continue;
            tree(placement,x,z,.8f+.24f*(1+(float)Math.sin(col*7+row*3)));
            if((col+row)%3==0)rock(placement,x+1.7f,z+1.1f,.65f);
        }
        for(int i=0;i<7;i++)rock(placement,-18+i*5.5f,-17,.6f+i%3*.15f);
    }
    private static void tree(Placement placement,float x,float z,float scale) {
        placement.tryAdd(1,x,z,scale,TREE_RADIUS*scale,TREE_RADIUS*scale);
    }
    private static void rock(Placement placement,float x,float z,float scale) {
        placement.tryAdd(5,x,z,scale,ROCK_RADIUS*scale,ROCK_RADIUS*scale);
    }

    /** Reserves full model bounds, including foliage, with breathing room for scenery. */
    private static final class Placement {
        private final Props props;
        private final float[] left=new float[150], right=new float[150], top=new float[150], bottom=new float[150];
        private int count;

        Placement(Props props) { this.props=props; }

        void tryAdd(int model,float x,float z,float scale,float halfWidth,float halfDepth) {
            float gap=.3f;
            for(int i=0;i<count;i++) {
                if(x+halfWidth+gap>left[i] && x-halfWidth-gap<right[i]
                        && z+halfDepth+gap>top[i] && z-halfDepth-gap<bottom[i]) return;
            }
            add(model,x,z,scale,halfWidth,halfDepth);
        }

        void add(int model,float x,float z,float scale,float halfWidth,float halfDepth) {
            left[count]=x-halfWidth; right[count]=x+halfWidth;
            top[count]=z-halfDepth; bottom[count++]=z+halfDepth;
            props.add(model,x,0,z,scale,halfWidth,halfDepth,Math.max(halfWidth,halfDepth));
        }
    }
}
