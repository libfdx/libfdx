package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.*;
import io.github.libfdx.graphics.g2d.TextureBlitter;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;

/** Synchronized GPU/CPU ribbon animation with analytic skinning, bounds, event and culling checks. */
public final class AnimationIntegrationTest extends GraphicsParityTest {
    private static final int ROWS=24, JOINTS=3;
    private static final String[] JOINT_IDS={"joint0","joint1","joint2"};
    private final DefaultModelInstance[][] instances=new DefaultModelInstance[2][2];
    private final AnimationController[][] controllers=new AnimationController[2][2];
    private final CpuSkinnedModelAnimator[] cpu=new CpuSkinnedModelAnimator[2];
    private final ModelInstance[][] casters=new ModelInstance[2][3];
    private final ModelBatch[] batches=new ModelBatch[2];
    private final CascadedShadowMap3D[] shadows=new CascadedShadowMap3D[2];
    private final OffscreenTarget[] targets=new OffscreenTarget[2];
    private final DefaultRenderQueue3D[] queues={new DefaultRenderQueue3D(),new DefaultRenderQueue3D()};
    private final int[][] events=new int[2][2];
    private final Camera camera=new Camera().projection(CameraProjection.ORTHOGRAPHIC).viewport(8,11)
            .nearFar(.1f,45).position(2,5,14).lookAt(0,2,0);
    private final DirectionalLight sun=new DirectionalLight().direction(-.65f,-1,-.35f).intensity(3);
    private final RenderPassDescriptor screen=new RenderPassDescriptor().label("Animated CPU/GPU comparison")
            .colorLoadOp(LoadOp.clear(12f/255,18f/255,28f/255,1)).colorStoreOp(StoreOp.store());
    private final float[][] jointTransforms=new float[JOINTS][16];
    private final Matrix4 scratch=new Matrix4();
    private Model model,ground;
    private Mesh ribbon,marker;
    private AnimationClip wave,reach;
    private TextureBlitter blitter;
    private DefaultAssetManager assets;
    private ShowcaseFont font;
    private ShowcaseHud hud;
    private float accumulator;
    private int simulationFrame;
    private int cycle;
    private int frames;
    private long vertexChecks;

    public AnimationIntegrationTest(long frames) { super(frames); }

    @Override public void create(Fdx fdx) {
        initialize(fdx,"AnimationIntegrationTest");
        assets=new DefaultAssetManager(fdx.files());
        G2DAssetLoaders.register(assets,graphics);
        font=new ShowcaseFont(assets.createScope(),false);
        hud=new ShowcaseHud(graphics);
        model=createModel();
        ground=new ModelBuilder(graphics).material(new Material("floor",
                MaterialAttributes.baseColor(.36f,.4f,.46f,1),PbrAttributes.roughnessFactor(.9f)))
                .plane("floor",12,12,ModelVertexUsage.STANDARD_PBR);
        wave=clip("wave",1); reach=clip("reach",-1.6f);
        for (int method=0;method<2;method++) {
            for (int i=0;i<2;i++) {
                instances[method][i]=new DefaultModelInstance(model);
                controllers[method][i]=method==0 ? new AnimationController(instances[method][i])
                        : (cpu[i]=new CpuSkinnedModelAnimator(graphics,instances[method][i])).controller();
                final int m=method,index=i;
                controllers[method][i].listener((controller,clip,event)->events[m][index]++);
                casters[method][i+1]=instances[method][i];
            }
            casters[method][0]=new DefaultModelInstance(ground);
            shadows[method]=new CascadedShadowMap3D(graphics,2,512,512).maxDistance(30)
                    .bias(.006f).minTexelBias(.5f).strength(.85f).shadowFadeFraction(0);
            Environment3D environment=new Environment3D().ambientColor(new Color(.13f,.15f,.19f,1))
                    .add(sun).cascadedShadowMap(shadows[method]).neutralToneMapping(1);
            batches[method]=new ModelBatch(graphics).environment(environment).frustumCulling(true);
            targets[method]=new OffscreenTarget(graphics.device(),true).clearColor(12f/255,18f/255,28f/255,1);
        }
        blitter=new TextureBlitter(graphics.device());
        markCreated();
    }

    private void advanceAnimation() {
        cycle=simulationFrame%360;
        if (cycle==0 || cycle==120 || cycle==180 || cycle==300)
            logger.info("AnimationIntegrationTest phase="+cycle+" frame="+frames);
        for (int method=0;method<2;method++) for (int i=0;i<2;i++) {
            AnimationController controller=controllers[method][i];
            if (cycle==0) controller.play(wave,true).time(i*.63f);
            if (cycle==120) controller.crossFade(reach,true,2);
            if (cycle==180) controller.crossFade(wave,true,.75f);
            controller.update(1/60f);
            instances[method][i].transform().setToTranslation(cycle>=300 && i==1 ? 40 : i==0 ? -1.6f : 1.8f,.1f,i==0 ? .3f : -.6f);
            if (i==1) instances[method][i].transform().scale(-1,1,1).rotateY(.15f);
            if (method==1) cpu[i].updateSkinning();
        }
        for (int i=0;i<2;i++) {
            if (events[0][i]!=events[1][i]) throw new FdxException("CPU/GPU event delivery diverged");
            verifyVertices(i);
        }
        if (ribbon.sourcePositions()[0]!=31.45f) throw new FdxException("CPU animation mutated the shared model");
        simulationFrame++;
    }

    @Override public void render() {
        assets.update(2,1_000_000);
        hud.font(font.poll());
        boolean ready=hud.hasFont();
        if (ready) {
            // Automated runs sample every tick; interactive playback follows wall time.
            accumulator+=requiresCompletion() ? 1/60f : Math.min(.1f,application.deltaTime());
            if (simulationFrame==0 && accumulator<1/60f) accumulator=1/60f;
            while (accumulator>=1/60f) {
                advanceAnimation();
                accumulator-=1/60f;
            }
        }
        GraphicsFrame frame=graphics.currentFrame();
        int width=framebufferWidth(),height=framebufferHeight();
        float scale=Math.min(width/1000f,height/720f);
        float uiWidth=width/scale,uiHeight=height/scale;
        boolean stacked=height>width;
        int margin=Math.max(1,Math.round(20*scale)),gap=Math.max(1,Math.round(12*scale));
        int top=Math.round(114*scale),bottom=Math.round(140*scale);
        int viewWidth=Math.max(1,stacked ? width-2*margin : (width-2*margin-gap)/2);
        int viewHeight=Math.max(1,stacked ? (height-top-bottom-gap)/2 : height-top-bottom);
        float aspect=viewWidth/(float)viewHeight;
        float worldHeight=Math.max(6.4f,8.6f/aspect);
        camera.viewport(worldHeight*aspect,worldHeight);
        if (ready) for (int method=0;method<2;method++) {
            targets[method].resize(viewWidth,viewHeight);
            shadows[method].renderIfNeeded(sun,camera,casters[method],frames);
            RenderPass pass=targets[method].begin(frame,true);
            batches[method].begin(pass,camera);
            for (ModelInstance instance : casters[method]) batches[method].render(instance);
            batches[method].end(); pass.end();
            int expected=cycle>=300 ? 3 : 5;
            if (batches[method].lastFlushVisibleCount()!=expected)
                throw new FdxException("Animated culling mismatch method="+method+" expected="+expected
                        +" actual="+batches[method].lastFlushVisibleCount()+" frame="+frames);
        }
        RenderPass pass=frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        if (ready) for (int method=0;method<2;method++) {
            int x=margin+(stacked ? 0 : method*(viewWidth+gap));
            int y=height-top-viewHeight-(stacked ? method*(viewHeight+gap) : 0);
            pass.setViewport(x,y,viewWidth,viewHeight); pass.setScissor(x,y,viewWidth,viewHeight);
            blitter.draw(pass,targets[method].color(),targets[method].origin(),false);
        }
        pass.setViewport(0,0,width,height); pass.setScissor(0,0,width,height);
        hud.begin(pass,width,height,scale,0,0);
        hud.text("SKELETAL ANIMATION",20,20,2.2f,.9f,.95f,1);
        hud.text("TWO SKINNING PATHS / SAME CLIPS, POSES AND SHADOWS",20,58,1.1f,.6f,.73f,.85f);
        hud.text("FLEXIBLE RIBBONS: THREE JOINTS / SECOND INSTANCE MIRRORED + OFFSET IN TIME",20,84,1,.6f,.73f,.85f);
        for (int method=0;method<2;method++) {
            float x=(margin+(stacked ? 0 : method*(viewWidth+gap)))/scale;
            float y=(top+(stacked ? method*(viewHeight+gap) : 0))/scale;
            hud.rect(x,y,viewWidth/scale,3,.25f,.8f,.7f,1);
            hud.text(method==0 ? "GPU SKINNING" : "CPU SKINNING",x+12,y+15,1.35f,.8f,.94f,.95f);
        }
        float timelineY=uiHeight-110;
        hud.text(cycle<120 ? "WAVE / LOOPING WITH INDEPENDENT PLAYHEADS"
                : cycle<180 ? "CROSSFADE / WAVE TO REACH"
                : cycle<300 ? "INTERRUPTED CROSSFADE / RETURN TO WAVE"
                : "CULLING / SECOND RIBBON MOVED OUTSIDE BOTH VIEWS",20,timelineY,1.3f,.9f,.95f,1);
        hud.rect(20,timelineY+28,uiWidth-40,4,.18f,.27f,.36f,1);
        hud.rect(20,timelineY+28,(uiWidth-40)*cycle/360f,4,.3f,.83f,.7f,1);
        hud.text("0S  WAVE",20,timelineY+44,1,.6f,.73f,.85f);
        hud.text("2S  REACH",20+(uiWidth-40)/3,timelineY+44,1,.6f,.73f,.85f);
        hud.text("3S  WAVE",20+(uiWidth-40)/2,timelineY+44,1,.6f,.73f,.85f);
        hud.text("5S  CULL",20+(uiWidth-40)*5/6,timelineY+44,1,.6f,.73f,.85f);
        hud.text("YELLOW TRIANGLES: ZERO-WEIGHT VERTICES MUST STAY RIGID AS THE RIBBONS BEND.",
                20,uiHeight-48,1,.8f,.72f,.4f);
        hud.text(ready ? "CHECKS PASSING: VERTICES / BOUNDS / EVENTS / CULLING. BOTH VIEWS SHOULD MATCH."
                : "LOADING FONT",20,uiHeight-26,1,.4f,.9f,.7f);
        hud.end();
        pass.end();
        if (ready) { frames++; finishFrame(); }
    }

    private void verifyVertices(int index) {
        for (int joint=0;joint<JOINTS;joint++)
            instances[0][index].copyNodeModelTransform(JOINT_IDS[joint],scratch).copyValues(jointTransforms[joint],0);
        queues[0].clear(); queues[1].clear();
        instances[0][index].collectRenderables(queues[0]); instances[1][index].collectRenderables(queues[1]);
        for (int part=0;part<2;part++) {
            Mesh original=part==0 ? ribbon : marker;
            float[] positions=original.sourcePositions(),weights=original.sourceWeights();
            int[] joints=original.sourceJoints();
            float[] actual=queues[1].get(part).meshPart().mesh().sourcePositions();
            BoundingBox bounds=queues[0].get(part).bounds();
            for (int vertex=0;vertex<positions.length/3;vertex++) {
                double sum=0; for (int c=0;c<4;c++) sum+=weights[vertex*4+c];
                for (int axis=0;axis<3;axis++) {
                    double expected=0;
                    if (sum==0) expected=positions[vertex*3+axis];
                    else for (int c=0;c<4;c++) {
                        int joint=joints[vertex*4+c]; float[] matrix=jointTransforms[joint];
                        // Independent bind translation and matrix-vector evaluation, without a palette/updater.
                        double x=positions[vertex*3]-32,y=positions[vertex*3+1]-joint*2,z=positions[vertex*3+2];
                        expected+=weights[vertex*4+c]/sum*(matrix[axis]*x+matrix[4+axis]*y+matrix[8+axis]*z+matrix[12+axis]);
                    }
                    if (Math.abs(actual[vertex*3+axis]-expected)>2e-5)
                        throw new FdxException("CPU vertex mismatch part="+part+" vertex="+vertex+" axis="+axis);
                    double min=axis==0 ? bounds.min().x() : axis==1 ? bounds.min().y() : bounds.min().z();
                    double max=axis==0 ? bounds.max().x() : axis==1 ? bounds.max().y() : bounds.max().z();
                    if (expected<min || expected>max) throw new FdxException("Animated bounds omitted a vertex");
                    vertexChecks++;
                }
            }
        }
    }

    private Model createModel() {
        int vertices=ROWS*6;
        float[] positions=new float[vertices*3],colors=new float[vertices*4],normals=new float[vertices*3];
        float[] uv=new float[vertices*2],pbr=new float[vertices*3],emissive=new float[vertices*3];
        float[] weights=new float[vertices*4],tangents=new float[vertices*4]; int[] joints=new int[weights.length];
        int[] corners={0,1,3,0,3,2};
        for (int row=0;row<ROWS;row++) for (int c=0;c<6;c++) {
            int vertex=row*6+c,corner=corners[c];
            float t=(row+(corner>=2 ? 1 : 0))/(float)ROWS,y=t*4;
            positions[vertex*3]=32+((corner&1)==0 ? -.55f : .55f)*(1-.35f*t);
            positions[vertex*3+1]=y;
            normals[vertex*3+2]=1;
            colors[vertex*4]=.95f-.85f*t; colors[vertex*4+1]=.27f+.5f*t; colors[vertex*4+2]=.05f+.6f*t; colors[vertex*4+3]=1;
            uv[vertex*2]=corner&1; uv[vertex*2+1]=t;
            pbr[vertex*3]=1; pbr[vertex*3+1]=.15f; pbr[vertex*3+2]=.55f;
            int lower=Math.min(1,(int)(y/2)); float blend=(y-lower*2)/2;
            joints[vertex*4]=lower; joints[vertex*4+1]=lower+1;
            weights[vertex*4]=(1-blend)*3; weights[vertex*4+1]=blend*3;
            tangents[vertex*4]=tangents[vertex*4+3]=1;
        }
        ribbon=Mesh.positionColor3D(graphics,"ribbon",positions,colors,null,normals,uv,pbr,null,emissive,null,
                joints,weights,new BoundingBox(new Vector3(31.45f,0,0),new Vector3(32.55f,4,0)),true,uv,tangents);
        marker=Mesh.positionColor3D(graphics,"zero-weight marker",new float[]{.8f,1,.1f, 1.3f,1,.1f, 1.05f,1.5f,.1f},
                new float[]{1,.7f,.1f,1, 1,.7f,.1f,1, 1,.7f,.1f,1},null,new float[]{0,0,1,0,0,1,0,0,1},
                new float[6],new float[]{1,0,.8f,1,0,.8f,1,0,.8f},null,new float[9],null,new int[12],new float[12],
                new BoundingBox(new Vector3(.8f,1,.1f),new Vector3(1.3f,1.5f,.1f)),true);
        Array<Bone> bones=new Array<>(); ModelNode root=new ModelNode("root");
        for (int joint=0;joint<JOINTS;joint++) {
            bones.add(new Bone("joint"+joint,-1,new Matrix4().setToTranslation(-32,-joint*2,0)));
            ModelNode node=new ModelNode("joint"+joint); node.localTransform().setToTranslation(0,joint*2,0); root.addChild(node);
        }
        Skin skin=new Skin("ribbon skin",new Skeleton(bones));
        Material material=new Material("ribbon").doubleSided(true);
        root.addPart(new ModelNodePart(new MeshPart(ribbon,0,ribbon.vertexCount()),material,skin,joints,weights));
        root.addPart(new ModelNodePart(new MeshPart(marker,0,3),material,skin,marker.sourceJoints(),marker.sourceWeights()));
        Array<ModelNode> nodes=new Array<>(); nodes.add(root);
        Array<Material> materials=new Array<>(); materials.add(material);
        Array<Skin> skins=new Array<>(); skins.add(skin);
        Array<Mesh> meshes=new Array<>(); meshes.add(ribbon); meshes.add(marker);
        return new DefaultModel(nodes,materials,new Array<AnimationClip>(),skins,meshes);
    }

    private AnimationClip clip(String id,float sign) {
        AnimationClip.NodeTransformChannel[] channels=new AnimationClip.NodeTransformChannel[JOINTS];
        for (int joint=0;joint<JOINTS;joint++) {
            AnimationClip.TransformKeyframe[] keys=new AnimationClip.TransformKeyframe[3];
            for (int key=0;key<3;key++) {
                float angle=sign*(key==1 ? -.3f : .3f)*(joint+.2f);
                keys[key]=AnimationClip.keyframe(key,sign*.12f*joint,joint*2,0,0,0,
                        (float)Math.sin(angle/2),(float)Math.cos(angle/2),1,1,1);
            }
            channels[joint]=AnimationClip.nodeTransform("joint"+joint,keys);
        }
        return new AnimationClip(id,2,channels,new AnimationClip.Event[]{new AnimationClip.Event(.5f,"beat"),
                new AnimationClip.Event(1.5f,"return")});
    }

    @Override public void dispose() {
        for (var batch : batches) dispose(batch);
        for (var shadow : shadows) dispose(shadow);
        for (var animator : cpu) dispose(animator);
        dispose(model); dispose(ground); dispose(blitter);
        dispose(hud); dispose(font); dispose(assets);
        for (var target : targets) dispose(target);
        logger.info("AnimationIntegrationTest analytic_components="+vertexChecks+" matching_events="+events[0][0]+","+events[0][1]);
        verifyDisposed();
    }
}
