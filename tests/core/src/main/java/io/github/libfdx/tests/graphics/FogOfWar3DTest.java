package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.FogExploration;
import io.github.libfdx.testsupport.graphics.FogForegroundFade;
import io.github.libfdx.testsupport.graphics.FogMaskRenderer;
import io.github.libfdx.testsupport.graphics.FogMovement;
import io.github.libfdx.testsupport.graphics.FogOfWarControls3D;
import io.github.libfdx.testsupport.graphics.FogSceneGeometry;
import io.github.libfdx.testsupport.graphics.FogSceneLayout;
import io.github.libfdx.testsupport.graphics.FogSurfacePositions;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.input.*;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.ClipDepthRange;

/**
 * Explore a forest courtyard with camera-relative WASD/arrows and a following
 * third-person orbit camera. Terrain remains explored until Restart.
 * Solid scenery remains in place; world-space fog covers its visible surfaces.
 */
public final class FogOfWar3DTest extends GraphicsParityTest {
    private static final float[][] ROUTE = FogSceneLayout.ROUTE;
    private final FogExploration explored = new FogExploration();
    private final FogMovement movement = new FogMovement();
    private final Model[] models = new Model[6];
    private final ModelInstance[] sceneryInstances = new ModelInstance[151];
    private final ModelInstance[] opaqueInstances = new ModelInstance[151], shadowInstances = new ModelInstance[151];
    private final DefaultModelInstance[] shadowProps = new DefaultModelInstance[150];
    private final ModelInstance[] singleLayer = new ModelInstance[1];
    private final Material[] shadowAlpha = new Material[256];
    private final String[] propNode = new String[150];
    private final BoundingBox[] propBounds = new BoundingBox[150];
    private final FrustumCuller3D fadeCuller = new FrustumCuller3D();
    private final float[] propAlpha = new float[150], propDistance = new float[150];
    private final int[] fading = new int[150];
    private boolean fadeInitialized;
    private final float[] propX = new float[150],propZ = new float[150],propRadius = new float[150];
    private int propCount, waypoint = 1, movementTaps;
    private DefaultModelInstance ground, player;
    private DirectionalShadowMap3D shadows;
    private DirectionalLight sun;
    private OrbitCameraController3D cameraInput;
    private Input input;
    private InputAdapter movementInput;
    private ModelBatch batch;
    private OffscreenTarget scenery;
    private FogSurfacePositions surfacePositions, layerPositions;
    private final RenderPassDescriptor sceneryPass = new RenderPassDescriptor()
            .label("solid courtyard").colorLoadOp(LoadOp.clear(.025f,.035f,.05f,1));
    private FogMaskRenderer fog;
    private Camera camera;
    private FogOfWarControls3D hud;

    public FogOfWar3DTest(long frames) { super(frames); }

    @Override public void create(Fdx fdx) {
        initialize(fdx, "FogOfWar3DTest");
        input = fdx.input();
        sun = new DirectionalLight().direction(-.65f,-1,-.35f)
                .color(new Color(1,.91f,.76f,1)).intensity(3.1f);
        shadows = new DirectionalShadowMap3D(graphics,2048,2048)
                .bounds(0,0,0,34,.1f,110).autoBias(true).strength(.82f).shadowFadeFraction(0);
        scenery = new OffscreenTarget(graphics.device(),graphics.surfaceFormat(),
                null,1,TextureFilter.NEAREST);
        surfacePositions = new FogSurfacePositions(graphics);
        layerPositions = new FogSurfacePositions(graphics);
        for(int i=0;i<shadowAlpha.length;i++)shadowAlpha[i]=new Material("foreground shadow "+i,
                MaterialAttributes.baseColor(1,1,1,i/255f)).alphaMode(MaterialAlphaMode.BLEND);
        batch = new ModelBatch(graphics)
                .frustumCulling(true).environment(new Environment3D()
                .ambientColor(new Color(.14f,.18f,.22f,1)).add(sun).directionalShadowMap(shadows)
                .neutralToneMapping(1));
        ModelBuilder builder = new ModelBuilder(graphics);
        models[0] = FogSceneGeometry.ground(builder);
        models[1] = FogSceneGeometry.pine(builder);
        models[2] = FogSceneGeometry.wall(builder);
        models[3] = FogSceneGeometry.pillar(builder);
        models[4] = builder.material(material("player",.08f,.65f,.85f,.35f))
                .cube("player cube",.9f,ModelVertexUsage.STANDARD_PBR);
        models[5] = FogSceneGeometry.rock(builder);
        ground = new DefaultModelInstance(models[0]);
        player = new DefaultModelInstance(models[4]);

        FogSceneLayout.populate(this::prop);
        sceneryInstances[propCount] = player;
        opaqueInstances[propCount] = player;
        shadowInstances[propCount] = player;
        fog = new FogMaskRenderer(graphics);
        camera = new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(52).nearFar(.15f,100);
        cameraInput = new OrbitCameraController3D(input,camera)
                .position(movement.x+3,7.2f,movement.z+10,movement.x,.65f,movement.z)
                .radiusRange(6,22).keyboardEnabled(false)
                .pointerRegion((x,y)->y>78 && y<display.height()-(hud==null?90:hud.bottomInset()));
        movementInput = new InputAdapter() {
            @Override public boolean keyDown(KeyEvent event) {
                int bit=movementBit(event.key()); movementTaps|=bit; return bit!=0;
            }
        };
        input.addProcessor(movementInput);
        hud=new FogOfWarControls3D(fdx,this::restart,this::lowCamera,this::overheadCamera);
        hud.autoWalk.set(exitAfterFrames != 0);
        restart();
        markCreated();
        logger.info("FogOfWar3DTest: continuous exploration, solid obstacles; automatic shadow bias="+shadows.bias());
    }

    private void prop(int model,float x,float y,float z,float scale,float halfWidth,float halfDepth,float radius) {
        DefaultModelInstance instance=new DefaultModelInstance(models[model]);
        instance.transform().setToTranslation(x,y,z).scale(scale,scale,scale);
        propX[propCount]=x;propZ[propCount]=z;propRadius[propCount]=radius;
        ModelNode node=models[model].nodes().get(0);
        propNode[propCount]=node.id();
        propBounds[propCount]=node.parts().get(0).meshPart().mesh().bounds();
        shadowProps[propCount]=new DefaultModelInstance(models[model]);
        shadowProps[propCount].transform().set(instance.transform());
        sceneryInstances[propCount++]=instance;
        movement.obstacle(x,z,halfWidth,halfDepth);
    }
    private static Material material(String name,float r,float g,float b,float roughness) {
        return new Material(name,MaterialAttributes.baseColor(r,g,b,1),
                PbrAttributes.roughnessFactor(roughness),PbrAttributes.metallicFactor(0));
    }
    private static int movementBit(Key key) {
        return switch(key) { case D,RIGHT->1; case A,LEFT->2; case W,UP->4; case S,DOWN->8; default->0; };
    }
    private void restart() {
        movement.x=ROUTE[0][0]; movement.z=ROUTE[0][1]; waypoint=1;
        explored.dimExplored(hud.dimExplored.get());
        explored.reset(); explored.reveal(movement.x,movement.z); explored.settle();
        fadeInitialized=false;
        if(Boolean.parseBoolean(System.getProperty("libfdx.test.fogLowCamera","false")))lowCamera();
    }
    private void lowCamera() {
        cameraInput.position(movement.x+3,2.5f,movement.z+10,movement.x,.65f,movement.z);
    }
    private void overheadCamera() {
        cameraInput.position(movement.x,16,movement.z+1,movement.x,.65f,movement.z);
    }
    private void walk(float sideways,float forward,float delta) {
        hud.autoWalk.set(false);
        float fx=camera.direction().x(),fz=camera.direction().z();
        float length=(float)Math.sqrt(fx*fx+fz*fz);
        if(length<.001f)return;
        fx/=length; fz/=length;
        float distance=3.6f*delta/Math.max(1,(float)Math.sqrt(sideways*sideways+forward*forward));
        movement.move((fx*forward-fz*sideways)*distance,(fz*forward+fx*sideways)*distance);
    }
    private void patrol(float delta) {
        float dx=ROUTE[waypoint][0]-movement.x,dz=ROUTE[waypoint][1]-movement.z;
        float length=(float)Math.sqrt(dx*dx+dz*dz);
        if(length<.1f) {
            logger.info("FogOfWar3DTest patrol reached waypoint "+waypoint);
            waypoint=(waypoint+1)%ROUTE.length; return;
        }
        float step=Math.min(length,delta*2.7f);
        movement.move(dx/length*step,dz/length*step);
    }
    @Override public void render() {
        float delta=Math.min(application.deltaTime(),.05f);
        float sideways=(input.isKeyPressed(Key.D)||input.isKeyPressed(Key.RIGHT)||(movementTaps&1)!=0?1:0)
                -(input.isKeyPressed(Key.A)||input.isKeyPressed(Key.LEFT)||(movementTaps&2)!=0?1:0);
        float forward=(input.isKeyPressed(Key.W)||input.isKeyPressed(Key.UP)||(movementTaps&4)!=0?1:0)
                -(input.isKeyPressed(Key.S)||input.isKeyPressed(Key.DOWN)||(movementTaps&8)!=0?1:0);
        movementTaps=0;
        if(sideways!=0||forward!=0)walk(sideways,forward,delta);
        else if(hud.autoWalk.get())patrol(delta);
        explored.dimExplored(hud.dimExplored.get());
        explored.reveal(movement.x,movement.z); explored.update(delta);
        camera.viewport(framebufferWidth(),framebufferHeight());
        cameraInput.target(movement.x,.65f,movement.z).update(delta);
        if(camera.position().y()<2.5f)
            cameraInput.position(camera.position().x(),2.5f,camera.position().z(),movement.x,.65f,movement.z);
        player.transform().setToTranslation(movement.x,.45f,movement.z);
        int fadeCount=updateForeground(Math.max(0,application.deltaTime()));
        // Shadow alpha follows the same fade; collision obstacles never change.
        shadows.render(sun,shadowInstances);
        if(scenery.resize(framebufferWidth(),framebufferHeight()))
            sceneryPass.colorAttachment(scenery.color().view());
        GraphicsFrame frame=graphics.currentFrame();
        renderScenery(frame,ground,opaqueInstances);
        surfacePositions.render(frame,camera,ground,opaqueInstances);
        fog.render(explored,scenery.color(),surfacePositions.texture());
        // Mixed fog boundaries also need layers when camera alpha is zero: their cleared surfaces
        // must still draw. Each layer keeps only nearest surfaces, avoiding transparent self-overlap.
        for(int j=0;j<fadeCount;j++) {
            int i=fading[j]; singleLayer[0]=sceneryInstances[i];
            renderScenery(frame,null,singleLayer);
            layerPositions.render(frame,camera,null,singleLayer);
            fog.renderLayer(scenery.color(),layerPositions.texture(),surfacePositions.texture(),
                    camera.position().x(),camera.position().z(),propAlpha[i]);
        }
        hud.render(delta); finishFrame();
    }
    private int updateForeground(float delta) {
        int count=0;
        fadeCuller.update(camera);
        for(int i=0;i<propCount;i++) {
            float desired=FogForegroundFade.target(camera.position().x(),camera.position().y(),camera.position().z(),
                    movement.x,movement.z,propX[i],propZ[i],propRadius[i]);
            float alpha=fadeInitialized?FogForegroundFade.advance(propAlpha[i],desired,delta):desired;
            propAlpha[i]=alpha;
            float leastFog=explored.minimumOpacity(propX[i],propZ[i],propRadius[i]);
            boolean solid=alpha==1 || explored.maximumOpacity(propX[i],propZ[i],propRadius[i])<=.5f;
            opaqueInstances[i]=solid?sceneryInstances[i]:null;
            float shadowOpacity=FogForegroundFade.surfaceAlpha(alpha,leastFog);
            shadowProps[i].nodeMaterial(propNode[i],0,shadowAlpha[Math.round(shadowOpacity*255)]);
            shadowInstances[i]=shadowOpacity==0?null:shadowProps[i];
            if(!solid && (alpha>0 || leastFog<1)
                    && fadeCuller.isVisible(propBounds[i],sceneryInstances[i].transform())) {
                float dx=propX[i]-camera.position().x(),dz=propZ[i]-camera.position().z();
                propDistance[i]=dx*camera.direction().x()+dz*camera.direction().z();
                int at=count;
                while(at>0 && propDistance[fading[at-1]]<propDistance[i]) {
                    fading[at]=fading[at-1];at--;
                }
                fading[at]=i;count++;
            }
        }
        fadeInitialized=true;
        return count;
    }

    private void renderScenery(GraphicsFrame frame,ModelInstance terrain,ModelInstance[] instances) {
        RenderPass pass=frame.commandEncoder().beginRenderPass(
                sceneryPass.depthClear(ClipDepthRange.getDefault().depthClearValue()));
        batch.begin(pass,camera);
        if(terrain!=null)batch.render(terrain);
        for(ModelInstance instance:instances)if(instance!=null)batch.render(instance);
        batch.end();pass.end();
    }

    @Override public void resize(int width,int height) { if(hud!=null)hud.resize(width,height); }
    @Override public void dispose() {
        if(input!=null&&movementInput!=null)input.removeProcessor(movementInput);
        dispose(hud);dispose(cameraInput);dispose(fog);dispose(batch);dispose(shadows);
        dispose(surfacePositions);dispose(layerPositions);dispose(scenery);
        for(Model model:models)dispose(model);
        verifyDisposed();
    }
}
