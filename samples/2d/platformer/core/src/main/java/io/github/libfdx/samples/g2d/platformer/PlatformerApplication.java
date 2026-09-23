package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.*;
import io.github.libfdx.assets.*;
import io.github.libfdx.audio.*;
import io.github.libfdx.audio.loaders.AudioAssetLoaders;
import io.github.libfdx.core.*;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.input.*;
import io.github.libfdx.maps.tiled.TiledMapLoader;
import io.github.libfdx.samples.g2d.platformer.input.*;
import io.github.libfdx.samples.g2d.platformer.render.*;

/** Owns the authored-level workflow; graphics/audio roots and input are borrowed from the backend. */
public final class PlatformerApplication extends ApplicationAdapter {
    private static final String FIRST_LEVEL="levels/meadow.tmj";
    private static final String[] EFFECTS={"jump","coin","ui","hurt","step"};
    private static final LoadOp LOADING_COLOR=LoadOp.clear(.035f,.055f,.08f,1);
    private final FixedStepClock simulationClock=new FixedStepClock(1.0/60,8);
    private final PlatformerView view=new PlatformerView();
    private final PlatformerHud hud=new PlatformerHud();
    private final InputRouter router=new InputRouter();
    private final RenderPassDescriptor loadingPass=new RenderPassDescriptor().label("Platformer loading");
    private final long exitAfterFrames;
    private final AssetExecutor executor;
    private final AssetLease<Sound>[] effects;
    private Application application;
    private GraphicsContext graphics;
    private Display display;
    private Input input;
    private Logger logger;
    private Audio audio;
    private DefaultAssetManager assets;
    private AudioMixer mixer;
    private AssetScope globals,currentScope,pendingScope,retiringScope;
    private AssetLease<PlatformerLevelAsset> pending;
    private AssetLease<Texture> fontPage,white;
    private PlatformerLevelAsset current,retiring;
    private PlatformerGame game;
    private BackendPlatformerInput controls;
    private PlatformerMenuInput menu;
    private SpriteBatch sprites;
    private BitmapFont font;
    private AuthoredPlatformerRenderer renderer;
    private String requestedPath=FIRST_LEVEL,capturePath;
    private long captureAt,renderedFrames;
    private boolean paused,systemPaused,captured,disposed,musicFailureReported;
    private double elapsed,fadeRemaining,footstep;
    private int previousVolume=7,loadedLevels;

    public PlatformerApplication() { this(0,null); }
    public PlatformerApplication(long exitAfterFrames) { this(exitAfterFrames,null); }
    /** Transfers ownership of an optional executor; it is closed after assets on shutdown. */
    @SuppressWarnings("unchecked")
    public PlatformerApplication(long exitAfterFrames,AssetExecutor executor) {
        this.exitAfterFrames=exitAfterFrames; this.executor=executor; effects=new AssetLease[EFFECTS.length];
    }
    @Override
    public void create(Fdx fdx) {
        if (fdx==null) throw new IllegalArgumentException("fdx required");
        application=fdx.app(); graphics=fdx.graphics().main(); display=fdx.displays().main();
        input=fdx.input(); logger=fdx.logger(); audio=fdx.audio();
        capturePath=System.getProperty("libfdx.sample.capture","");
        captureAt=Long.parseLong(System.getProperty("libfdx.sample.captureFrame","120"));
        try {
            assets=new DefaultAssetManager(fdx.files(),executor); globals=assets.createScope();
            TiledMapLoader.register(assets); G2DAssetLoaders.register(assets,graphics);
            if (audio!=null) {
                AudioAssetLoaders.register(assets,audio);
                if (audio.maxMusicStreams()>0) AudioAssetLoaders.registerMusic(assets,audio,MusicBuffering.DEFAULT,executor);
                mixer=new AudioMixer(audio).masterGain(hud.volume/10f).busGain(AudioBus.MUSIC,.55f);
                for(int i=0;i<EFFECTS.length;i++) effects[i]=globals.load(AssetDescriptor.of("audio/"+EFFECTS[i]+".wav",Sound.class));
            }
            hud.audioAvailable=audio!=null;
            PlatformerLevelAsset.register(assets,audio!=null && audio.maxMusicStreams()>0);
            fontPage=globals.load(TextureLoadOptions.PIXEL_ART.descriptor("ui/font.png",Texture.class));
            white=globals.load(TextureLoadOptions.PIXEL_ART.descriptor("ui/white.png",Texture.class));
            sprites=new SpriteBatch(graphics);
            menu=new PlatformerMenuInput(input,router,view,audio,logger);
            controls=new BackendPlatformerInput(input,router); menu.controls(controls); input.addProcessor(router);
            requestLevel(FIRST_LEVEL);
            logger.info("Platformer created: "+graphics.providerId().value()+", budget 4 tasks / 1 ms, audio="+(audio!=null));
        } catch (RuntimeException | Error failure) {
            try { dispose(); } catch (RuntimeException | Error cleanup) { if(failure!=cleanup) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private void requestLevel(String path) {
        if (pendingScope!=null) pendingScope.dispose();
        if (retiringScope!=null) { mixer.update(fadeRemaining); releaseRetiring(); }
        requestedPath=path; pendingScope=assets.createScope();
        pending=pendingScope.load(AssetDescriptor.of(path,PlatformerLevelAsset.class));
        hud.loading=true; hud.failed=false; hud.status="LOADING LEVEL"; hud.heading="LOADING";
        controls.enabled(false); simulationClock.pause(); menu.state(true,true);
    }
    @Override
    public void render() {
        if (disposed || application==null) return;
        GraphicsFrame frame=graphics.currentFrame();
        view.update(display.width(),display.height(),frame.width(),frame.height()); controls.layout(view);
        commands(menu.consumeCommands());
        assets.update(4,1_000_000);
        if (renderer==null && ready(fontPage) && ready(white)) {
            StringBuilder characters=new StringBuilder(64);
            for(int i=32;i<96;i++) characters.append((char)i);
            font=BitmapFont.fromGrid(fontPage.asset(),characters.toString(),6,8);
            renderer=new AuthoredPlatformerRenderer(sprites,view,white.asset(),font);
        }
        boolean effectsReady=true;
        if (audio!=null) for(AssetLease<Sound> effect:effects) effectsReady &= ready(effect);
        if (pending!=null) {
            if (pending.future().isFailed()) failLevel();
            else if (pending.isLoaded() && renderer!=null && effectsReady) publishLevel();
        }
        double delta=Math.max(0,Math.min(.1,application.deltaTime()));
        boolean running=game!=null && !paused && !systemPaused && !hud.loading && !hud.failed;
        controls.enabled(running && !game.gameOver() && !game.completed());
        controls.update(display.width(),display.height());
        if (running) {
            int steps=simulationClock.advance(Math.max(0,application.deltaTime()));
            for(int i=0;i<steps;i++) {
                game.update((float)simulationClock.stepSeconds());
                elapsed+=simulationClock.stepSeconds();
                int events=game.consumeEvents();
                if((events&PlatformerGame.JUMP)!=0) play(0,.7f);
                if((events&PlatformerGame.COIN)!=0) play(1,.65f);
                if((events&PlatformerGame.HURT)!=0) play(3,.8f);
                if((events&PlatformerGame.COMPLETE)!=0) { play(1,.8f); logger.info("Platformer trail complete"); }
                if(game.playerOnGround() && Math.abs(game.playerVelocityX())>.001 && !game.gameOver() && !game.completed()) {
                    footstep+=simulationClock.stepSeconds();
                    if(footstep>=.23) { footstep-=.23; play(4,.16f); }
                } else footstep=0;
            }
        }
        if(mixer!=null && !paused && !systemPaused) {
            mixer.update(delta);
            if(retiringScope!=null && (fadeRemaining-=delta)<=0) releaseRetiring();
        }
        if(current!=null && current.music()!=null && current.music().failure()!=null && !musicFailureReported) {
            musicFailureReported=true; logger.warn("Platformer music failed: "+current.music().failure().getMessage());
        }
        hud.game(game); hud.audioLocked=audio!=null && audio.isSuspended();
        hud.menuOpen=paused || game!=null && (game.gameOver() || game.completed());
        if(!hud.loading && !hud.failed) hud.heading=game!=null && game.completed()?"TRAIL CLEAR":game!=null && game.gameOver()?"TRY AGAIN":"PAUSED";
        menu.state(hud.menuOpen,hud.loading || hud.failed);
        if(renderer!=null) renderer.render(frame,current,game,hud,controls,elapsed);
        else frame.commandEncoder().beginRenderPass(loadingPass.colorAttachment(frame.colorAttachment())
                .colorLoadOp(LOADING_COLOR).colorStoreOp(StoreOp.store())).end();
        renderedFrames++;
        if(!captured && capturePath!=null && !capturePath.isEmpty() && game!=null && renderedFrames>=captureAt) {
            captureFrame(capturePath); captured=true;
        }
        if(exitAfterFrames>0 && renderedFrames>=exitAfterFrames) application.requestExit();
    }
    private static boolean ready(AssetLease<?> lease) {
        if(lease==null) return false;
        if(lease.future().isFailed()) lease.future().get();
        return lease.isLoaded();
    }
    private void publishLevel() {
        PlatformerLevelAsset next=pending.asset();
        PlatformerGame nextGame;
        try {
            nextGame=PlatformerLevel.create(controls,next.map().map());
            if(next.music()!=null) {
                next.music().looping(true); mixer.music(next.music(),.55f);
                if(current!=null && current.music()!=null && current.music()!=next.music()) {
                    mixer.crossfade(current.music(),next.music(),.6); fadeRemaining=.6;
                } else next.music().play();
            }
        } catch(RuntimeException failure) {
            if(next.music()!=null) mixer.remove(next.music());
            failLevel(failure); return;
        }
        retiring=current; retiringScope=currentScope;
        current=next; currentScope=pendingScope; game=nextGame;
        pending=null; pendingScope=null; elapsed=footstep=0; paused=false;
        hud.title=next.title(); hud.loading=hud.failed=false; musicFailureReported=false;
        simulationClock.reset(); if(systemPaused) simulationClock.pause(); else simulationClock.resume();
        if(retiringScope!=null && (current.music()==null || retiring.music()==null || current.music()==retiring.music())) releaseRetiring();
        if(systemPaused) pauseMusic();
        loadedLevels++; logger.info("Platformer level ready: "+next.title()+", solids="+game.solidCount()+", coins="+game.coinTotal()+", loadedLevels="+loadedLevels);
    }
    private void failLevel() {
        try { pending.future().get(); } catch(RuntimeException failure) { failLevel(failure); }
    }
    private void failLevel(RuntimeException failure) {
        logger.warn("Platformer level load failed: "+requestedPath+": "+failure.getMessage());
        pending=null;
        if(pendingScope!=null) { pendingScope.dispose(); pendingScope=null; }
        hud.loading=false; hud.failed=true; hud.heading="LEVEL UNAVAILABLE"; hud.status="COULD NOT LOAD TRAIL";
    }
    private void releaseRetiring() {
        if(retiringScope==null) return;
        Music oldMusic=retiring.music();
        if(oldMusic!=null && (current==null || oldMusic!=current.music())) mixer.remove(oldMusic);
        Texture shared=retiring.map().tiles().region(1).texture();
        boolean sharedWithCurrent=current!=null && current.map().tiles().region(1).texture()==shared;
        retiringScope.dispose(); retiringScope=null; retiring=null; fadeRemaining=0;
        if(sharedWithCurrent && shared.isDisposed()) throw new FdxException("Level transition disposed shared terrain");
        logger.info("Platformer previous level released; shared terrain retained="+sharedWithCurrent);
    }
    private void commands(int commands) {
        if(commands==0) return;
        if((commands&PlatformerMenuInput.QUIETER)!=0) volume(hud.volume-1);
        if((commands&PlatformerMenuInput.LOUDER)!=0) volume(hud.volume+1);
        if((commands&PlatformerMenuInput.MUTE)!=0) {
            if(hud.volume==0) volume(previousVolume); else { previousVolume=hud.volume; volume(0); }
        }
        if((commands&PlatformerMenuInput.PAUSE)!=0 && !hud.loading) {
            if(hud.failed && current!=null) hud.failed=false;
            if(game!=null && (game.gameOver() || game.completed())) { game.restart(); game.consumeEvents(); paused=false; }
            else paused=!paused;
            if(paused) { simulationClock.pause(); controls.enabled(false); pauseMusic(); }
            else { simulationClock.resume(); resumeMusic(); }
            logger.info("Platformer paused="+paused+", playerX="+(game==null?0:game.player().x()));
        }
        if((commands&PlatformerMenuInput.RESTART)!=0 && !hud.loading) {
            if(hud.failed) requestLevel(requestedPath);
            else if(game!=null) {
                game.restart(); game.consumeEvents(); paused=false; controls.enabled(false);
                simulationClock.reset(); simulationClock.resume(); resumeMusic(); elapsed=0;
                logger.info("Platformer restarted at authored spawn");
            }
        }
        if((commands&PlatformerMenuInput.NEXT)!=0 && !hud.loading && current!=null) requestLevel(current.next());
        play(2,.4f);
    }
    private void volume(int value) {
        hud.volume=Math.max(0,Math.min(10,value));
        if(mixer!=null) mixer.masterGain(hud.volume/10f);
        logger.info("Platformer volume="+hud.volume);
    }
    private void play(int index,float gain) {
        if(mixer!=null && effects[index]!=null && effects[index].isLoaded()) {
            mixer.play(index==2?AudioBus.UI:AudioBus.SFX,effects[index].asset(),gain,1,0,false);
        }
    }
    private void pauseMusic() {
        if(current!=null && current.music()!=null && current.music().failure()==null) current.music().pause();
        if(retiring!=null && retiring.music()!=null && retiring.music().failure()==null) retiring.music().pause();
    }
    private void resumeMusic() {
        if(systemPaused || paused) return;
        if(current!=null && current.music()!=null && current.music().failure()==null) current.music().play();
        if(retiring!=null && retiring.music()!=null && retiring.music().failure()==null) retiring.music().play();
    }
    @Override
    public void pause() {
        systemPaused=true; simulationClock.pause();
        if(controls!=null) controls.enabled(false); if(menu!=null) menu.enabled(false); pauseMusic();
    }
    @Override
    public void resume() {
        systemPaused=false; if(menu!=null) menu.enabled(true);
        if(!paused && !hud.loading && !hud.failed) { simulationClock.resume(); resumeMusic(); }
    }
    private void captureFrame(String path) {
        try {
            GraphicsFrame frame=graphics.currentFrame();
            PlatformerFramebufferCapture.writePpm(path,frame.width(),frame.height(),frame.frameBuffer().readPixelsRgba8());
            logger.info("Platformer captured framebuffer to "+path);
        } catch(Exception failure) { throw new FdxException("Could not capture platformer framebuffer",failure); }
    }
    @Override
    public void dispose() {
        if(disposed) return; disposed=true;
        Throwable failure=null;
        if(input!=null) input.removeProcessor(router);
        failure=close(menu,failure); failure=close(controls,failure);
        failure=close(mixer,failure); failure=close(font,failure); failure=close(sprites,failure);
        Texture terrain=current==null?null:current.map().tiles().region(1).texture();
        int underruns=current==null || current.music()==null?0:current.music().underruns();
        failure=close(assets,failure); failure=close(executor,failure);
        if(exitAfterFrames>0 && renderedFrames>0 && (loadedLevels==0 || terrain==null || !terrain.isDisposed())) {
            failure=append(failure,new FdxException("Platformer finite run did not load/release level assets"));
        }
        if(capturePath!=null && !capturePath.isEmpty() && !captured) failure=append(failure,new FdxException("Platformer did not capture "+capturePath));
        if(logger!=null) logger.info("Platformer disposed: levels="+loadedLevels+", last music underruns="+underruns);
        current=retiring=null; game=null; renderer=null;
        if(failure instanceof RuntimeException runtime) throw runtime;
        if(failure instanceof Error fatal) throw fatal;
    }
    private static Throwable close(Disposable value,Throwable failure) {
        if(value!=null) try { value.dispose(); } catch(RuntimeException | Error next) { failure=append(failure,next); }
        return failure;
    }
    private static Throwable append(Throwable first,Throwable next) {
        if(first==null) return next; if(first!=next) first.addSuppressed(next); return first;
    }
}
