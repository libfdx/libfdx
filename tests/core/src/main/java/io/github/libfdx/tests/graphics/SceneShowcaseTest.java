package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.*;
import io.github.libfdx.graphics.effects.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.input.*;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import java.util.ArrayList;
import java.util.Arrays;

/** Interactive original gallery and a bounded deterministic graphics regression workload. */
public final class SceneShowcaseTest extends GraphicsParityTest {
    private final AssetExecutor executor;
    private final ArrayList<Model> ownedModels=new ArrayList<>();
    private final ArrayList<DefaultModelInstance> scene=new ArrayList<>();
    private final ArrayList<ModelInstance> shadowCasters=new ArrayList<>();
    private final DefaultModelInstance[] sculptures=new DefaultModelInstance[2];
    private final AnimationController[] animations=new AnimationController[2];
    private final long[][][] timings=new long[3][4][512];
    private final int[] sampleCounts=new int[3];
    private final long[] sorted=new long[512];
    private final Camera camera=new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(44).nearFar(.15f,40);
    private final DirectionalLight sun=new DirectionalLight().direction(-.55f,-1,-.45f)
            .color(new Color(1,.82f,.61f,1)).intensity(1.8f);
    private final Environment environment=new Environment().ambientColor(new Color(.015f,.02f,.025f,1)).add(sun)
            .add(new DirectionalLight().direction(.6f,-.25f,.45f).color(new Color(.16f,.45f,.65f,1)).intensity(.7f));
    private final RenderPassDescriptor screen=new RenderPassDescriptor().label("Kinetic gallery and controls")
            .colorLoadOp(LoadOp.clear(.025f,.035f,.05f,1)).colorStoreOp(StoreOp.store());
    private DefaultAssetManager assets;
    private AssetScope scope;
    private AssetLease<Model> sculptureAsset;
    private AssetLease<ImageBasedLighting3D> probeAsset;
    private ShowcaseFont fontAsset;
    private ModelBatch batch;
    private ShowcaseHud hud;
    private ModelInstance[] casters;
    private Targets targets;
    private CascadedShadowMap3D shadows;
    private AnimationClip[] clips;
    private Input input;
    private EffectQuality desired,quality,rejectedQuality;
    private boolean ready,paused,platformPaused,ibl=true,shadowEnabled=true,dragging,disposed;
    private int rejectedWidth,rejectedHeight;
    private int frames,qualityAge,activeClip,eventCount,lastX,lastY,width,height,sceneX,sceneY,sceneWidth,sceneHeight;
    private long casterRevision,retirements;
    private float yaw=.62f,pitch=.4f,distance=11.8f,uiScale=1,uiLeft,uiTop;
    private String providerLabel,message="PREPARING THE GALLERY",drawStats="WARMING UP",timeStats="CPU RECORD / WARMING UP",budgetStats="";
    private final InputAdapter controls=new InputAdapter() {
        @Override
        public boolean keyDown(KeyEvent event) {
            switch (event.key()) {
                case NUM_1 -> desired=EffectQuality.LOW;
                case NUM_2 -> desired=EffectQuality.BALANCED;
                case NUM_3 -> desired=EffectQuality.HIGH;
                case SPACE -> paused=!paused;
                case I -> ibl=!ibl;
                case S -> shadowEnabled=!shadowEnabled;
                case A -> chooseClip(1-activeClip);
                case R -> { yaw=.62f; pitch=.4f; distance=11.8f; rejectedQuality=null; if(!ready) requestAssets(); }
                default -> { return false; }
            }
            return true;
        }
        @Override
        public boolean pointerDown(PointerEvent event) {
            if (activate(uiX(event.x()),uiY(event.y()))) return true;
            dragging=insideView(uiX(event.x()),uiY(event.y())); lastX=event.x(); lastY=event.y();
            return dragging;
        }
        @Override
        public boolean pointerUp(PointerEvent event) { boolean used=dragging; dragging=false; return used; }
        @Override
        public boolean pointerMoved(PointerEvent event) {
            if (!dragging) return false;
            yaw-=(event.x()-lastX)*.008f;
            pitch=Math.max(.12f,Math.min(.85f,pitch+(event.y()-lastY)*.006f));
            lastX=event.x(); lastY=event.y(); return true;
        }
        @Override
        public boolean scrolled(PointerEvent event) {
            if (!insideView(uiX(event.x()),uiY(event.y()))) return false;
            distance=Math.max(8,Math.min(17,distance+event.scrollY()*.6f)); return true;
        }
        @Override
        public boolean touchDown(TouchEvent event) { return activate(uiX(event.point().x()),uiY(event.point().y())); }
    };

    public SceneShowcaseTest(long frames) { this(frames, null); }

    /** Takes ownership of the optional worker executor; null uses cooperative preparation. */
    public SceneShowcaseTest(long frames, AssetExecutor executor) {
        super(frames); desired=EffectQuality.BALANCED; this.executor=executor;
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, getClass().getSimpleName());
        providerLabel=graphics.providerId().value().toUpperCase(java.util.Locale.ROOT);
        if (!ImageBasedLighting3D.isSupported(graphics))
            throw new FdxException("Kinetic gallery requires the standard GPU PBR path and filterable HDR environment textures");
        input=fdx.input();
        assets=new DefaultAssetManager(fdx.files(),executor);
        G2DAssetLoaders.register(assets,graphics); G3DAssetLoaders.register(assets,graphics);
        hud=new ShowcaseHud(graphics);
        batch=new ModelBatch(graphics).environment(environment).frustumCulling(true);
        updateLayout();
        input.addProcessor(controls);
        requestAssets();
        markCreated();
    }

    private void requestAssets() {
        if (ready) return;
        hud.font(null);
        if (fontAsset!=null) fontAsset.dispose();
        if (scope!=null) scope.dispose();
        scope=assets.createScope();
        sculptureAsset=scope.load(AssetDescriptor.of("showcase/sculpture.gltf",Model.class));
        probeAsset=scope.load(AssetDescriptor.of("ibl/studio.fdxibl",ImageBasedLighting3D.class));
        fontAsset=new ShowcaseFont(scope,false);
        message="PREPARING THE GALLERY";
    }

    private void completeAssets() {
        if (!sculptureAsset.isLoaded() || !probeAsset.isLoaded()) {
            message="ASSET LOAD FAILED / R TO RETRY"; return;
        }
        Model imported=sculptureAsset.asset();
        if (imported.animations().size()!=2) throw new FdxException("Sculpture animation fixture is incomplete");
        clips=new AnimationClip[2];
        for (int i=0;i<2;i++) {
            AnimationClip source=imported.animations().get(i);
            clips[i]=new AnimationClip(source.id(),source.durationSeconds(),source.nodeTransformChannels(),
                    new AnimationClip.Event[]{new AnimationClip.Event(2,"quarter"),new AnimationClip.Event(6,"return")});
        }
        for (int i=0;i<2;i++) {
            sculptures[i]=new DefaultModelInstance(imported);
            if (i==1) sculptures[i].transform().setToTranslation(3.15f,.4f,-1.4f).scale(.38f,.38f,.38f);
            animations[i]=new AnimationController(sculptures[i]).play(clips[0],true).time(i*2);
            animations[i].listener((controller,clip,event)->eventCount++);
            scene.add(sculptures[i]); shadowCasters.add(sculptures[i]);
        }
        createStage();
        casters=shadowCasters.toArray(new ModelInstance[0]);
        environment.imageBasedLighting(probeAsset.asset()).imageBasedLightingTransform(.9f,.5f);
        ready=true; message="ORIGINAL ASSETS / GLTF 2.0";
        logger.info("SceneShowcaseTest assets ready: original licensed sculpture, 2 independent skins, authored materials, IBL, transparent panels");
    }

    private void createStage() {
        ModelBuilder builder=new ModelBuilder(graphics);
        Material floor=new Material("slate",MaterialAttributes.baseColor(.085f,.12f,.15f,1),PbrAttributes.roughnessFactor(.62f));
        Material rim=new Material("plinth",MaterialAttributes.baseColor(.14f,.19f,.22f,1),PbrAttributes.metallicFactor(.6f),PbrAttributes.roughnessFactor(.3f));
        Model floorTile=own(builder.material(floor).box("floor tile",1.98f,.1f,1.98f,ModelVertexUsage.STANDARD_PBR));
        for (int z=0;z<5;z++) for (int x=0;x<6;x++) add(floorTile,(x-2.5f)*2,-.08f,(z-2.5f)*2,true);
        Model pedestal=own(builder.material(rim).box("main plinth",4.15f,.32f,4.15f,ModelVertexUsage.STANDARD_PBR));
        add(pedestal,0,.13f,0,true);
        Model trim=own(builder.material(new Material("copper rim",MaterialAttributes.baseColor(.75f,.42f,.16f,1),
                PbrAttributes.metallicFactor(.9f),PbrAttributes.roughnessFactor(.24f)))
                .box("plinth edge",4.23f,.035f,4.23f,ModelVertexUsage.STANDARD_PBR));
        add(trim,0,.29f,0,true);
        Model stem=own(builder.material(rim).box("stem",.16f,2.1f,.16f,ModelVertexUsage.STANDARD_PBR));
        add(stem,0,1.35f,0,true);
        Model displayBase=own(builder.material(rim).box("small base",1.4f,.3f,1.4f,ModelVertexUsage.STANDARD_PBR));
        add(displayBase,-3.1f,.1f,.9f,true); add(displayBase,3.15f,.1f,-1.4f,true);
        Material pearl=new Material("ceramic",MaterialAttributes.baseColor(.78f,.75f,.62f,1),
                PbrAttributes.metallicFactor(.05f),PbrAttributes.roughnessFactor(.18f));
        Model sphere=own(builder.material(pearl).sphere("ceramic orb",.64f,40,24,ModelVertexUsage.STANDARD_PBR));
        add(sphere,-3.1f,.9f,.9f,true);
        Model rail=own(builder.material(rim).box("gallery rail",.035f,3.2f,.07f,ModelVertexUsage.STANDARD_PBR));
        for (int i=0;i<11;i++) add(rail,-5+i,1.55f,-4.9f,true);
        Material acrylic=new Material("transparent teal",MaterialAttributes.baseColor(.08f,.45f,.48f,.24f),
                PbrAttributes.metallicFactor(.08f),PbrAttributes.roughnessFactor(.12f))
                .alphaMode(MaterialAlphaMode.BLEND).doubleSided(true);
        Model panel=own(builder.material(acrylic).triangles("acrylic screen",
                new float[]{-.7f,0,0, .7f,0,0, .7f,1.5f,0, -.7f,0,0, .7f,1.5f,0, -.7f,1.5f,0},
                null,null,ModelVertexUsage.STANDARD_PBR));
        add(panel,2.1f,.3f,1.8f,false).transform().rotateY(-.35f);
        add(panel,2.5f,.3f,1.1f,false).transform().rotateY(.15f);
    }

    private Model own(Model model) { ownedModels.add(model); return model; }
    private DefaultModelInstance add(Model model,float x,float y,float z,boolean caster) {
        DefaultModelInstance instance=new DefaultModelInstance(model).transform(new Matrix4().setToTranslation(x,y,z));
        scene.add(instance); if(caster) shadowCasters.add(instance); return instance;
    }

    @Override
    public void render() {
        updateLayout();
        if (!ready) {
            boolean complete=assets.update(4,1_000_000);
            hud.font(fontAsset.poll());
            if (complete && hud.hasFont()) completeAssets();
        }

        if (ready && (targets==null || quality!=desired || targets.width!=sceneWidth || targets.height!=sceneHeight)
                && (desired!=rejectedQuality || sceneWidth!=rejectedWidth || sceneHeight!=rejectedHeight))
            configure();
        GraphicsFrame frame=graphics.currentFrame();
        if (ready && targets!=null) {
            long started=System.nanoTime();
            float delta=exitAfterFrames>0 ? 1/60f : Math.max(0,Math.min(.1f,application.deltaTime()));
            if (!paused && !platformPaused) {
                for (AnimationController animation : animations) animation.update(delta);
                casterRevision++;
            }
            camera.viewport(sceneWidth,sceneHeight).position((float)(Math.sin(yaw)*Math.cos(pitch)*distance),
                    1.7f+(float)Math.sin(pitch)*distance,(float)(Math.cos(yaw)*Math.cos(pitch)*distance)).lookAt(0,1.7f,0);
            environment.imageBasedLighting(ibl ? probeAsset.asset() : null)
                    .cascadedShadowMap(shadowEnabled ? shadows : null);
            long updated=System.nanoTime();
            if (shadowEnabled) shadows.renderIfNeeded(sun,camera,casters,casterRevision);
            long shadowed=System.nanoTime();
            RenderPass pass=targets.scene.begin(frame,true);
            batch.begin(pass,camera);
            for (int i=0;i<scene.size();i++) batch.render(scene.get(i));
            batch.end(); pass.end();
            targets.post.process(frame,targets.scene.color(),targets.scene.origin(),
                    targets.scene.color().format().isSrgb() ? ColorEncoding.SRGB : ColorEncoding.LINEAR);
            long rendered=System.nanoTime();
            int q=quality.ordinal(),sample=sampleCounts[q];
            if (qualityAge>=120 && sample<512) {
                timings[q][0][sample]=updated-started;
                timings[q][1][sample]=shadowed-updated;
                timings[q][2][sample]=rendered-shadowed;
                timings[q][3][sample]=Math.max(0,(long)(application.deltaTime()*1_000_000_000d));
                sampleCounts[q]++;
            }
            if (qualityAge%30==0) refreshStats();
            qualityAge++;
        }
        RenderPass pass=frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        if (ready && targets!=null) {
            pass.setViewport(sceneX,sceneY,sceneWidth,sceneHeight); pass.setScissor(sceneX,sceneY,sceneWidth,sceneHeight);
            targets.post.present(pass);
        }
        pass.setViewport(0,0,width,height); pass.setScissor(0,0,width,height);
        hud.begin(pass,width,height,uiScale,uiLeft,uiTop);
        drawHud();
        hud.end(); pass.end();
        if (ready && targets!=null) { frames++; finishFrame(); }
    }

    private void configure() {
        if (!desired.supported(graphics.device().capabilities())) {
            message="REQUESTED QUALITY UNAVAILABLE"; desired=quality==null ? EffectQuality.bestSupported(graphics.device().capabilities()) : quality;
        }
        Targets next=null;
        CascadedShadowMap3D nextShadows=shadows;
        try {
            next=new Targets(graphics,desired,sceneWidth,sceneHeight);
            if (quality!=desired || shadows==null) nextShadows=budget(desired).create(graphics)
                    .maxDistance(35).bias(.012f).minTexelBias(.6f).shadowFadeFraction(.15f).strength(.82f);
        } catch (RuntimeException | Error failure) {
            if (next!=null) try { next.dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            if (nextShadows!=shadows && nextShadows!=null) try { nextShadows.dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            if (targets==null) throw failure;
            rejectedQuality=desired; rejectedWidth=sceneWidth; rejectedHeight=sceneHeight;
            desired=quality; message="QUALITY CHANGE FAILED / R TO RETRY";
            logger.error("SceneShowcaseTest quality allocation failed",failure); return;
        }
        Targets old=targets; CascadedShadowMap3D oldShadows=shadows;
        targets=next; shadows=nextShadows; quality=desired; qualityAge=0;
        sampleCounts[quality.ordinal()]=0; rejectedQuality=null;
        environment.cascadedShadowMap(shadowEnabled ? shadows : null);
        if (old!=null) { old.dispose(); retirements++; }
        if (oldShadows!=null && oldShadows!=shadows) oldShadows.dispose();
        budgetStats="TARGETS "+((targets.bytes()+shadows.estimatedBytes())/1_048_576)+" MB";
        logger.info("SceneShowcaseTest quality="+quality+" frame="+frames+" scene="+targets.scene.width()+"x"+targets.scene.height()
                +" target_bytes="+(targets.bytes()+shadows.estimatedBytes())+" cascades="+budget(quality).cascadeCount()
                +" shadow_resolution="+budget(quality).resolution()+" retired_target_sets="+retirements);
    }

    private static ShadowBudget3D budget(EffectQuality quality) {
        return quality==EffectQuality.LOW ? ShadowBudget3D.LOW : quality==EffectQuality.HIGH ? ShadowBudget3D.HIGH : ShadowBudget3D.BALANCED;
    }

    private void updateLayout() {
        width=framebufferWidth(); height=framebufferHeight();
        uiScale=Math.min(width/960f,height/640f); uiLeft=(width-960*uiScale)/2; uiTop=(height-640*uiScale)/2;
        sceneX=Math.round(uiLeft+24*uiScale); sceneY=height-Math.round(uiTop+580*uiScale);
        sceneWidth=Math.max(1,Math.round(648*uiScale)); sceneHeight=Math.max(1,Math.round(468*uiScale));
    }

    private void chooseClip(int index) {
        if (!ready || activeClip==index) return;
        activeClip=index;
        for (AnimationController animation : animations) animation.crossFade(clips[index],true,.8f);
        casterRevision++;
    }
    private float uiX(int x) { return (x*width/(float)Math.max(1,display.width())-uiLeft)/uiScale; }
    private float uiY(int y) { return (y*height/(float)Math.max(1,display.height())-uiTop)/uiScale; }
    private static boolean insideView(float x,float y) { return x>=24 && x<672 && y>=112 && y<580; }
    private boolean activate(float x,float y) {
        if (x<712 || x>=920) return false;
        if (y>=158 && y<194) { desired=x<780 ? EffectQuality.LOW : x<850 ? EffectQuality.BALANCED : EffectQuality.HIGH; return true; }
        if (y>=242 && y<276) { chooseClip(x<816 ? 0 : 1); return true; }
        if (y>=306 && y<338) { ibl=!ibl; return true; }
        if (y>=342 && y<374) { shadowEnabled=!shadowEnabled; return true; }
        if (y>=396 && y<434) { paused=!paused; return true; }
        return false;
    }

    private void drawHud() {
        hud.rect(24,24,912,68,.055f,.079f,.10f,1);
        hud.rect(24,24,4,68,.34f,.88f,.72f,1);
        hud.text("LIBFDX",44,42,2.5f,.87f,.96f,.92f);
        hud.text("KINETIC GALLERY",168,42,2.5f,.87f,.96f,.92f);
        hud.text("LIGHT / MATERIAL / MOTION",168,70,1.1f,.49f,.62f,.66f);
        hud.text("REALTIME 3D",780,44,1.3f,.34f,.88f,.72f);
        hud.text(providerLabel,780,65,1.1f,.56f,.67f,.71f);
        hud.rect(696,112,240,468,.055f,.079f,.10f,1);
        hud.text("QUALITY",712,132,1.5f,.6f,.73f,.77f);
        hud.button("LOW",712,158,62,36,quality==EffectQuality.LOW);
        hud.button("BAL",783,158,62,36,quality==EffectQuality.BALANCED);
        hud.button("HIGH",854,158,66,36,quality==EffectQuality.HIGH);
        hud.rect(712,210,208,1,.14f,.21f,.25f,1);
        hud.text("ANIMATION",712,222,1.3f,.6f,.73f,.77f);
        hud.button("ORBIT",712,242,98,34,activeClip==0);
        hud.button("DRIFT",822,242,98,34,activeClip==1);
        hud.rect(712,290,208,1,.14f,.21f,.25f,1);
        hud.toggle("ENVIRONMENT",712,306,208,32,ibl);
        hud.toggle("SHADOWS",712,342,208,32,shadowEnabled);
        hud.button(paused ? "RESUME [SPACE]" : "PAUSE [SPACE]",712,396,208,38,paused);
        hud.text("FRAME INSPECTOR",712,458,1.25f,.34f,.88f,.72f);
        hud.text(timeStats,712,484,1.05f,.75f,.83f,.84f);
        hud.text(drawStats,712,505,1.05f,.56f,.67f,.71f);
        hud.text(budgetStats,712,526,1.05f,.56f,.67f,.71f);
        hud.text("GPU TIME: UNAVAILABLE",712,547,1.05f,.42f,.54f,.59f);
        if (!ready) {
            hud.rect(24,112,648,468,.045f,.064f,.084f,1);
            hud.text(message,56,322,1.7f,.66f,.82f,.83f);
        } else {
            hud.rect(40,130,184,29,.035f,.052f,.066f,.88f);
            hud.text("01 / MOTION STUDY",52,139,1.2f,.69f,.85f,.83f);
            hud.rect(40,529,226,34,.035f,.052f,.066f,.88f);
            hud.text(paused ? "PLAYBACK PAUSED" : activeClip==0 ? "ORBIT / 8 SECOND LOOP" : "DRIFT / 8 SECOND LOOP",52,540,1.25f,.78f,.9f,.87f);
            float progress=animations[0].timeSeconds()/8;
            hud.rect(40,569,616,2,.12f,.2f,.23f,1);
            hud.rect(40,569,616*progress,2,.34f,.88f,.72f,1);
        }
        hud.text("DRAG TO ORBIT / SCROLL TO ZOOM / R RESET",24,606,1.2f,.46f,.6f,.64f);
        hud.text(message,592,606,.95f,.46f,.6f,.64f);
    }

    private void refreshStats() {
        int q=quality.ordinal(),count=sampleCounts[q];
        if (count>0) {
            for (int i=0;i<count;i++) sorted[i]=timings[q][0][i]+timings[q][1][i]+timings[q][2][i];
            Arrays.sort(sorted,0,count);
            timeStats="CPU RECORD "+millis(sorted[count/2])+" MS";
        }
        drawStats="VISIBLE "+batch.lastFlushVisibleCount()+" / CULLED "+batch.lastFlushCulledCount();
    }
    private static String millis(long nanos) {
        long micros=Math.max(0,nanos/1000);
        return micros/1000+"."+Long.toString(1000+micros%1000).substring(1);
    }

    @Override
    public void pause() { platformPaused=true; dragging=false; }
    @Override
    public void resume() { platformPaused=false; }
    @Override
    public void dispose() {
        if(disposed) return;
        disposed=true;
        if(input!=null) input.removeProcessor(controls);
        for (int q=0;q<3;q++) if(sampleCounts[q]>0) for(int phase=0;phase<4;phase++) {
            int count=sampleCounts[q]; System.arraycopy(timings[q][phase],0,sorted,0,count); Arrays.sort(sorted,0,count);
            logger.info("SceneShowcaseTest MEASURE quality="+EffectQuality.values()[q]+" phase="+(phase==0 ? "UPDATE" : phase==1 ? "SHADOW_RECORD" : phase==2 ? "SCENE_POST_RECORD" : "FRAME")
                    +" samples="+count+" median_us="+sorted[count/2]/1000+" p95_us="+sorted[(count-1)*95/100]/1000
                    +" p99_us="+sorted[(count-1)*99/100]/1000+" max_us="+sorted[count-1]/1000+" GPU_NOT_MEASURED");
        }
        Throwable failure=close(batch,null);
        failure=close(hud,failure); failure=close(targets,failure); failure=close(shadows,failure);
        for(Model model:ownedModels) failure=close(model,failure);
        failure=close(fontAsset,failure); failure=close(assets,failure); failure=close(executor,failure);
        if(failure instanceof RuntimeException runtime) throw runtime;
        if(failure instanceof Error error) throw error;
        if(!ready && requiresCompletion()) throw new FdxException("Gallery assets did not complete");
        logger.info("SceneShowcaseTest events="+eventCount+" retired_target_sets="+retirements);
        verifyDisposed();
    }

    private static Throwable close(Disposable resource,Throwable failure) {
        try { dispose(resource); }
        catch(RuntimeException | Error cleanup) {
            if(failure==null) return cleanup;
            failure.addSuppressed(cleanup);
        }
        return failure;
    }

    private static final class Targets implements Disposable {
        final int width,height;
        OffscreenTarget scene;
        PostProcessor post;
        private boolean disposed;
        Targets(GraphicsContext graphics,EffectQuality quality,int width,int height) {
            this.width=width; this.height=height;
            try {
                scene=new OffscreenTarget(graphics.device(),quality.sceneFormat(),TextureFormat.DEPTH32_FLOAT,1,TextureFilter.LINEAR)
                        .clearColor(.014f,.023f,.035f,1);
                scene.resize(quality.sceneDimension(width),quality.sceneDimension(height));
                post=new PostProcessor(graphics.device(),quality).exposure(1.1f).bloom(quality==EffectQuality.HIGH ? 1 : .8f,.14f);
                post.resize(width,height);
            } catch(RuntimeException | Error failure) {
                try { dispose(); } catch(RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        long bytes() { return scene.estimatedBytes()+post.estimatedBytes(); }
        @Override
        public boolean isDisposed() { return disposed; }
        @Override
        public void dispose() {
            if(disposed)return; disposed=true;
            try { SceneShowcaseTest.dispose(scene); } finally { SceneShowcaseTest.dispose(post); }
        }
    }
}
