package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.Fdx;
import io.github.libfdx.ui.*;
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
import io.github.libfdx.samples.g2d.platformer.render.PlatformerHud.Screen;

/** Owns the authored-level workflow; graphics/audio roots and input are borrowed from the backend. */
public final class PlatformerApplication extends ApplicationAdapter {
    private static final String[] EFFECTS={"jump","coin","ui","hurt","step","bell"};
    private final FixedStepClock simulationClock=new FixedStepClock(1.0/60,8);
    private final PlatformerView view=new PlatformerView();
    private final PlatformerHud hud=new PlatformerHud();
    private final InputRouter router=new InputRouter();
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
    private PlatformerLevelAsset current,retiring;
    private PlatformerGame game;
    private BackendPlatformerInput controls;
    private PlatformerMenuInput menu;
    private SpriteBatch sprites;
    private UiRoot ui;
    private AuthoredPlatformerRenderer renderer;
    private String requestedPath,capturePath;
    private long captureAt,renderedFrames;
    private boolean systemPaused,captured,disposed,musicFailureReported;
    private double elapsed,fadeRemaining,footstep,arrival;
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
            globals.load(AssetDescriptor.of("levels/art/" + Trail.RAVINE.id + "-sky.jpg", Texture.class))
                    .future().onSuccess(sky -> hud.panorama.set(new TextureRegion(sky)));
            globals.load(AssetDescriptor.of("levels/atlases/shared.atlas.json", SpriteAtlas.class))
                    .future().onSuccess(atlas -> hud.emblem.set(atlas.find("actors/gear").region()));
            if (audio!=null) {
                AudioAssetLoaders.register(assets,audio);
                if (audio.maxMusicStreams()>0) AudioAssetLoaders.registerMusic(assets,audio,MusicBuffering.DEFAULT,executor);
                mixer=new AudioMixer(audio).masterGain(hud.volume.get()/10f).busGain(AudioBus.MUSIC,.55f);
                for(int i=0;i<EFFECTS.length;i++) effects[i]=globals.load(AssetDescriptor.of("audio/"+EFFECTS[i]+".wav",Sound.class));
            }
            hud.audioAvailable.set(audio!=null);
            PlatformerLevelAsset.register(assets,audio!=null && audio.maxMusicStreams()>0);
            sprites=new SpriteBatch(graphics);
            menu = new PlatformerMenuInput(input, audio, logger);
            router.add(menu);
            controls = new BackendPlatformerInput(input, router);
            ui = new UiToolkit(fdx.files()).root(display, graphics).autoUiScale(false).input(input, router);
            router.add(menu.shortcuts());
            input.addProcessor(router);
            ui.setContent(scope -> hud.build(scope, menu, controls));
            renderer = new AuthoredPlatformerRenderer(sprites, view);
            enterScreen(Screen.TITLE);
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
        hud.canReplay.set(current != null);
        hud.screen.set(Screen.LOADING);
        controls.enabled(false); simulationClock.pause();
    }
    @Override
    public void render() {
        if (disposed || application==null) return;
        GraphicsFrame frame=graphics.currentFrame();
        view.update(display.width(),display.height(),frame.width(),frame.height());
        hud.layout(ui, view);
        commands(menu.consumeCommands());
        assets.update(4,1_000_000);
        boolean effectsReady=true;
        if (audio!=null) for(AssetLease<Sound> effect:effects) effectsReady &= ready(effect);
        if (pending!=null) {
            if (pending.future().isFailed()) failLevel();
            else if (pending.isLoaded() && effectsReady) publishLevel();
        }
        double delta=Math.max(0,Math.min(.1,application.deltaTime()));
        boolean running = hud.screen.get() == Screen.PLAYING && !systemPaused;
        controls.enabled(running);
        controls.update(display.width(),display.height());
        if (running) {
            int steps=simulationClock.advance(Math.max(0,application.deltaTime()));
            for(int i=0;i<steps;i++) {
                game.update((float)simulationClock.stepSeconds());
                elapsed+=simulationClock.stepSeconds();
                int events=game.consumeEvents();
                if((events&PlatformerGame.JUMP)!=0) play(0,.7f);
                if((events&PlatformerGame.COIN)!=0) play(1,.65f);
                if ((events & PlatformerGame.TURN) != 0) {
                    var gate = game.gate();
                    play(gate.bells() ? 5 : 2, .8f, .82f + .22f * gate.lastDial());
                }
                if((events&PlatformerGame.STOMP)!=0) play(4,.95f);
                renderer.effects(game, events);
                if((events&PlatformerGame.HURT)!=0) play(3,.8f);
                if((events&PlatformerGame.COMPLETE)!=0) { play(1,.8f); logger.info("Platformer trail complete"); }
                if(game.playerOnGround() && Math.abs(game.playerVelocityX())>.001 && !game.gameOver() && !game.completed()) {
                    footstep+=simulationClock.stepSeconds();
                    if(footstep>=.23) { footstep-=.23; play(4,.16f); }
                } else footstep=0;
                if (game.gameOver() || game.completed()) {
                    arrival = 0;
                    renderer.clearEffects();
                    enterScreen(game.completed() ? Screen.ARRIVING : Screen.LOST);
                    break;
                }
            }
        }
        Screen screen = hud.screen.get();
        if (!systemPaused && (screen == Screen.ARRIVING || screen == Screen.CLEAR)) {
            arrival += delta;
            if (screen == Screen.ARRIVING && arrival >= 1.25) enterScreen(Screen.CLEAR);
        }
        if(mixer!=null && !systemPaused && (running || screen == Screen.ARRIVING || screen == Screen.CLEAR)) {
            mixer.update(delta);
            if(retiringScope!=null && (fadeRemaining-=delta)<=0) releaseRetiring();
        }
        if(current!=null && current.music()!=null && current.music().failure()!=null && !musicFailureReported) {
            musicFailureReported=true; logger.warn("Platformer music failed: "+current.music().failure().getMessage());
        }
        hud.game(game);
        renderer.render(frame, current, game, elapsed, arrival);
        ui.update((float) delta);
        ui.render();
        renderedFrames++;
        if (!captured && capturePath != null && !capturePath.isEmpty() && renderedFrames >= captureAt
                && (game != null || (screen == Screen.TITLE && hud.panorama.get() != null && hud.emblem.get() != null))) {
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
            nextGame=PlatformerLevel.create(controls,next.map());
            if(next.music()!=null) {
                next.music().looping(true); mixer.music(next.music(),.55f);
                if(!systemPaused && current!=null && current.music()!=null && current.music()!=next.music()) {
                    mixer.crossfade(current.music(),next.music(),.6); fadeRemaining=.6;
                }
            }
        } catch(RuntimeException failure) {
            if(next.music()!=null) mixer.remove(next.music());
            failLevel(failure); return;
        }
        retiring=current; retiringScope=currentScope;
        current=next; currentScope=pendingScope; game=nextGame;
        pending=null; pendingScope=null; arrival=elapsed=footstep=0;
        renderer.clearEffects();
        hud.trail.set(next.trail()); musicFailureReported=false;
        simulationClock.reset();
        if (retiringScope != null && fadeRemaining <= 0) releaseRetiring();
        enterScreen(Screen.PLAYING);
        loadedLevels++; logger.info("Platformer level ready: "+next.trail().title+", solids="+game.solidCount()+", coins="+game.coinTotal()+", loadedLevels="+loadedLevels);
    }
    private void failLevel() {
        try { pending.future().get(); } catch(RuntimeException failure) { failLevel(failure); }
    }
    private void failLevel(RuntimeException failure) {
        logger.warn("Platformer level load failed: "+requestedPath+": "+failure.getMessage());
        pending=null;
        if(pendingScope!=null) { pendingScope.dispose(); pendingScope=null; }
        enterScreen(Screen.FAILED);
    }
    private void releaseRetiring() {
        if(retiringScope==null) return;
        Music oldMusic=retiring.music();
        if(oldMusic!=null && (current==null || oldMusic!=current.music())) mixer.remove(oldMusic);
        SpriteAtlas shared=retiring.sharedAtlas();
        boolean sharedWithCurrent=current!=null && current.sharedAtlas()==shared;
        retiringScope.dispose(); retiringScope=null; retiring=null; fadeRemaining=0;
        if(sharedWithCurrent && shared.isDisposed()) throw new FdxException("Level transition disposed shared characters");
        logger.info("Platformer previous level released; shared characters retained="+sharedWithCurrent);
    }
    private void enterScreen(Screen screen) {
        hud.screen.set(screen);
        controls.enabled(false);
        if (screen == Screen.PLAYING && !systemPaused) {
            simulationClock.resume();
            resumeMusic();
        } else {
            simulationClock.pause();
            if (screen != Screen.ARRIVING && screen != Screen.CLEAR) pauseMusic();
        }
    }

    private void restart(boolean fromCheckpoint) {
        if (fromCheckpoint) game.retry();
        else game.restart();
        renderer.clearEffects();
        game.consumeEvents();
        arrival = elapsed = footstep = 0;
        simulationClock.reset();
        enterScreen(Screen.PLAYING);
    }

    private void mainMenu() {
        enterScreen(Screen.TITLE);
        releaseRetiring();
        if (current != null && current.music() != null) mixer.remove(current.music());
        if (currentScope != null) currentScope.dispose();
        currentScope = null;
        current = null;
        game = null;
        renderer.clearEffects();
    }

    private void commands(int commands) {
        if (commands == 0 || systemPaused) return;
        Screen screen = hud.screen.get();
        if (screen == Screen.LOADING || screen == Screen.ARRIVING) return;
        if (commands == PlatformerMenuInput.NEXT && screen != Screen.CLEAR) return;
        if ((commands & PlatformerMenuInput.QUIETER) != 0) volume(hud.volume.get() - 1);
        if ((commands & PlatformerMenuInput.LOUDER) != 0) volume(hud.volume.get() + 1);
        if ((commands & PlatformerMenuInput.MUTE) != 0) {
            if (hud.volume.get() == 0) {
                volume(previousVolume);
            } else {
                previousVolume = hud.volume.get();
                volume(0);
            }
        }

        if ((commands & PlatformerMenuInput.MAIN_MENU) != 0) {
            mainMenu();
        } else if ((commands & (PlatformerMenuInput.OPTIONS | PlatformerMenuInput.HELP)) != 0
                && (screen == Screen.TITLE || screen == Screen.PAUSED)) {
            hud.menuParent = screen;
            enterScreen((commands & PlatformerMenuInput.OPTIONS) != 0 ? Screen.OPTIONS : Screen.HELP);
        } else if (screen == Screen.OPTIONS || screen == Screen.HELP) {
            if ((commands & (PlatformerMenuInput.BACK | PlatformerMenuInput.PAUSE)) != 0) enterScreen(hud.menuParent);
        } else if (screen == Screen.TITLE) {
            if ((commands & PlatformerMenuInput.CONFIRM) != 0) requestLevel(Trail.RAVINE.mapPath());
        } else if (screen == Screen.FAILED) {
            if ((commands & (PlatformerMenuInput.CONFIRM | PlatformerMenuInput.RESTART)) != 0) {
                requestLevel(requestedPath);
            } else if (current != null && (commands & PlatformerMenuInput.PAUSE) != 0) {
                restart(false);
            }
        } else if ((commands & PlatformerMenuInput.RESTART) != 0) {
            restart(false);
        } else if (screen == Screen.CLEAR && (commands & (PlatformerMenuInput.CONFIRM | PlatformerMenuInput.NEXT)) != 0) {
            requestLevel(current.trail().next().mapPath());
        } else if ((commands & PlatformerMenuInput.CONFIRM) != 0) {
            switch (screen) {
                case PAUSED -> enterScreen(Screen.PLAYING);
                case LOST -> restart(true);
                default -> { return; }
            }
        } else if ((commands & PlatformerMenuInput.PAUSE) != 0) {
            if (screen == Screen.PLAYING) enterScreen(Screen.PAUSED);
            else if (screen == Screen.PAUSED) enterScreen(Screen.PLAYING);
        }
        play(2, .4f);
    }

    private void volume(int value) {
        hud.volume.set(Math.max(0,Math.min(10,value)));
        if(mixer!=null) mixer.masterGain(hud.volume.get()/10f);
        logger.info("Platformer volume="+hud.volume.get());
    }
    private void play(int index,float gain) {
        play(index, gain, 1);
    }

    private void play(int index, float gain, float pitch) {
        if(mixer!=null && effects[index]!=null && effects[index].isLoaded()) {
            mixer.play(index==2?AudioBus.UI:AudioBus.SFX,effects[index].asset(),gain,pitch,0,false);
        }
    }
    private void pauseMusic() {
        if(current!=null && current.music()!=null && current.music().failure()==null) current.music().pause();
        if(retiring!=null && retiring.music()!=null && retiring.music().failure()==null) retiring.music().pause();
    }
    private void resumeMusic() {
        if (systemPaused) return;
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
        if (hud.screen.get() == Screen.PLAYING) simulationClock.resume();
        if (hud.screen.get() == Screen.PLAYING || hud.screen.get() == Screen.ARRIVING || hud.screen.get() == Screen.CLEAR) resumeMusic();
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
        failure=close(ui,failure);
        failure=close(menu,failure); failure=close(controls,failure);
        failure=close(mixer,failure); failure=close(sprites,failure);
        SpriteAtlas actors=current==null?null:current.sharedAtlas();
        Texture actorPage=current==null?null:current.actors()[0].texture();
        int underruns=current==null || current.music()==null?0:current.music().underruns();
        failure=close(assets,failure); failure=close(executor,failure);
        if (exitAfterFrames > 0 && renderedFrames > 0) {
            TextureRegion sky = hud.panorama.get();
            if (sky == null || !sky.texture().isDisposed() || (actors != null && (!actors.isDisposed() || !actorPage.isDisposed()))) {
                failure = append(failure, new FdxException("Platformer finite run did not load/release artwork"));
            }
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
