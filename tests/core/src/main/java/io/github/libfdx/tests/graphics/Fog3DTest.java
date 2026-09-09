package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.AtmosphericFogControls;
import io.github.libfdx.testsupport.graphics.FogMovement;
import io.github.libfdx.testsupport.graphics.FogSceneGeometry;
import io.github.libfdx.testsupport.graphics.FogSceneLayout;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.input.*;
import io.github.libfdx.math.Color;

/** Third-person navigation through distance fog; scenery remains opaque and solid. */
public final class Fog3DTest extends GraphicsParityTest {
    private static final float[][] ROUTE = FogSceneLayout.ROUTE;
    private static final Color MIST = new Color(122 / 255f, 153 / 255f, 173 / 255f, 1);
    private Environment3D environment;
    private float appliedStrength = -1;
    private final FogMovement movement = new FogMovement();
    private final Model[] models = new Model[6];
    private final ModelInstance[] sceneryInstances = new ModelInstance[151];
    private int propCount, waypoint = 1, movementTaps;
    private DefaultModelInstance ground, player;
    private DirectionalShadowMap3D shadows;
    private DirectionalLight sun;
    private OrbitCameraController3D cameraInput;
    private Input input;
    private InputAdapter movementInput;
    private ModelBatch batch;
    private Camera camera;
    private AtmosphericFogControls hud;

    public Fog3DTest(long frames) { super(frames); }

    @Override public void create(Fdx fdx) {
        initialize(fdx, "Fog3DTest");
        input = fdx.input();
        sun = new DirectionalLight().direction(-.65f,-1,-.35f)
                .color(new Color(1,.91f,.76f,1)).intensity(3.1f);
        shadows = new DirectionalShadowMap3D(graphics,2048,2048)
                .bounds(0,0,0,34,.1f,110).autoBias(true).strength(.82f).shadowFadeFraction(0);
        environment = new Environment3D().ambientColor(new Color(.14f,.18f,.22f,1))
                .add(sun).directionalShadowMap(shadows).neutralToneMapping(1);
        batch = new ModelBatch(graphics).frustumCulling(true).environment(environment);
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
        camera = new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(52).nearFar(.15f,100);
        cameraInput = new OrbitCameraController3D(input,camera)
                .position(ROUTE[0][0]+3,7.2f,ROUTE[0][1]+10,ROUTE[0][0],.65f,ROUTE[0][1])
                .radiusRange(6,22).keyboardEnabled(false)
                .pointerRegion((x,y)->y>120 && y<display.height()-112);
        movementInput = new InputAdapter() {
            @Override public boolean keyDown(KeyEvent event) {
                int bit=movementBit(event.key()); movementTaps|=bit; return bit!=0;
            }
        };
        input.addProcessor(movementInput);
        hud = new AtmosphericFogControls(fdx, "MISTY COURTYARD  /  3D",
                "Walk toward the ruins. Distant shapes emerge from the mist.",
                "WASD / arrows to walk | Right-drag or touch to look | Wheel to zoom", false, this::restart);
        hud.autoWalk.set(exitAfterFrames != 0);
        restart();
        markCreated();
        logger.info("Fog3DTest: distance fog, solid obstacles; automatic shadow bias="+shadows.bias());
    }

    private void prop(int model,float x,float y,float z,float scale,float halfWidth,float halfDepth,float radius) {
        DefaultModelInstance instance=new DefaultModelInstance(models[model]);
        instance.transform().setToTranslation(x,y,z).scale(scale,scale,scale);
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
        movementTaps=0;
        cameraInput.position(movement.x+3,7.2f,movement.z+10,movement.x,.65f,movement.z);
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
            logger.info("Fog3DTest patrol reached waypoint "+waypoint);
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
        float strength = hud.enabled.get() ? hud.strength.get() : 0;
        if (appliedStrength != strength) {
            if (strength == 0) environment.clearFog();
            else environment.fog(MIST, 7 / strength, 18 / strength);
            appliedStrength = strength;
        }
        camera.viewport(framebufferWidth(),framebufferHeight());
        cameraInput.target(movement.x,.65f,movement.z).update(delta);
        if(camera.position().y()<2.5f)
            cameraInput.position(camera.position().x(),2.5f,camera.position().z(),movement.x,.65f,movement.z);
        player.transform().setToTranslation(movement.x,.45f,movement.z);
        // Every obstacle stays opaque and casts its normal shadow, even in fog.
        shadows.render(sun,sceneryInstances);
        batch.begin(LoadOp.clear(MIST.red(),MIST.green(),MIST.blue(),1),camera);
        batch.render(ground);
        for(ModelInstance instance:sceneryInstances)if(instance!=null)batch.render(instance);
        batch.end();
        hud.render(delta); finishFrame();
    }
    @Override public void resize(int width,int height) { if(hud!=null)hud.resize(width,height); }
    @Override public void dispose() {
        if(input!=null&&movementInput!=null)input.removeProcessor(movementInput);
        dispose(hud);dispose(cameraInput);dispose(batch);dispose(shadows);
        for(Model model:models)dispose(model);
        verifyDisposed();
    }
}
