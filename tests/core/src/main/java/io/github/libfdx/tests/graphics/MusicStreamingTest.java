package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.audio.*;
import io.github.libfdx.audio.loaders.AudioAssetLoaders;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.input.*;
import java.nio.ByteBuffer;

/** Actual bounded music loading, mixer crossfade, loop/seek and service suspension. */
public final class MusicStreamingTest extends GraphicsParityTest {
    private static final LoadOp CLEAR = LoadOp.clear(.025f,.035f,.055f,1);
    private final AssetExecutor executor;
    private Audio audio;
    private AudioMixer mixer;
    private DefaultAssetManager assets;
    private AssetLease<Music> first,second;
    private AssetLease<Sound> effect;
    private Music a,b;
    private Input input;
    private InputAdapter activation;
    private SpriteBatch batch;
    private Texture white;
    private int phase=-1;
    private double elapsed;
    private boolean waiting,released;

    public MusicStreamingTest(long frames) { this(frames,null); }
    /** Owns the optional worker executor; music and assets close before it. */
    public MusicStreamingTest(long frames,AssetExecutor executor) { super(frames); this.executor=executor; }
    @Override
    public void create(Fdx fdx) {
        initialize(fdx,"MusicStreamingTest"); audio=fdx.audio();
        if(audio==null || audio.maxMusicStreams()<2) throw new FdxException("Music provider required");
        assets=new DefaultAssetManager(fdx.files(),executor);
        AudioAssetLoaders.register(assets,audio);
        AudioAssetLoaders.registerMusic(assets,audio,MusicBuffering.DEFAULT,executor);
        first=assets.acquire(AssetDescriptor.of("streaming/music-a.wav",Music.class));
        second=assets.acquire(AssetDescriptor.of("streaming/music-b.wav",Music.class));
        effect=assets.acquire(AssetDescriptor.of("audio/tone.wav",Sound.class));
        mixer=new AudioMixer(audio); mixer.busGain(AudioBus.SFX,0);
        input=fdx.input(); activation=new InputAdapter() {
            private void activate() { audio.resume().onFailure(error->logger.error("Music activation failed",error)); }
            @Override
            public boolean pointerDown(PointerEvent event) { activate(); return true; }
            @Override
            public boolean touchDown(TouchEvent event) { activate(); return true; }
            @Override
            public boolean keyDown(KeyEvent event) { activate(); return true; }
        };
        input.addProcessor(activation);
        batch=new SpriteBatch(graphics); white=graphics.device().createTexture(TextureDescriptor.rgba8("Music status",1,1));
        ByteBuffer pixel=ByteBuffer.allocateDirect(4); pixel.putInt(-1).flip(); graphics.device().writeTexture(white,pixel);
        markCreated();
    }
    @Override
    public void render() {
        if(!released) {
            assets.update(3,1_000_000);
            if(assets.lastUpdateTaskCount()>3) throw new FdxException("Music loading exceeded task budget");
            if(first.future().isFailed()) first.future().get();
            if(second.future().isFailed()) second.future().get();
            if(effect.future().isFailed()) effect.future().get();
            if(phase<0 && first.isLoaded() && second.isLoaded() && effect.isLoaded()) {
                if(audio.isSuspended()) {
                    if(!waiting) { waiting=true; logger.info("MusicStreamingTest waiting for gesture: suspended=true"); }
                } else {
                    a=first.asset(); b=second.asset();
                    mixer.music(a,.5f).music(b,.5f); a.pan(-1).looping(true).play(); b.pan(1).looping(true);
                    for(int i=0;i<32;i++) if(mixer.play(AudioBus.SFX,effect.asset(),.002f,1,0,true)==0) {
                        throw new FdxException("Expected 32 SFX voices alongside music");
                    }
                    if(audio.play(effect.asset())!=0) throw new FdxException("Saturated SFX pool accepted a voice");
                    phase=0; logger.info("MusicStreamingTest started: streams=2,voices=32,workerExecutor="+(executor!=null));
                }
            }
            if(phase>=0) {
                double delta=application.deltaTime(); elapsed+=delta; mixer.update(delta);
                if(a.failure()!=null) throw new FdxException("First music failed",a.failure());
                if(b.failure()!=null) throw new FdxException("Second music failed",b.failure());
                if(a.queuedFrames()>16384 || b.queuedFrames()>16384) throw new FdxException("Unbounded music queue");
                if(phase==0 && elapsed>=.8) {
                    if(a.positionFrames()<=0) throw new FdxException("Music media clock did not advance");
                    mixer.crossfade(a,b,1); phase=1; logger.info("MusicStreamingTest crossfade");
                }
                if(phase==1 && elapsed>=2) {
                    if(a.state()!=MusicState.PAUSED || b.state()!=MusicState.PLAYING) throw new FdxException("Crossfade did not finish");
                    phase=2; logger.info("MusicStreamingTest right-only");
                }
                if(phase==2 && elapsed>=2.7) { b.pause(); audio.suspend(); phase=3; logger.info("MusicStreamingTest paused"); }
                if(phase==3 && elapsed>=3.3) {
                    b.loop(48000,144000).seek(96000).pan(-1).play();
                    audio.resume().onSuccess(ignored->logger.info("MusicStreamingTest resumed-left: seekFrame=96000")); phase=4;
                }
                if(phase==4 && elapsed>=4.3) {
                    if(b.positionFrames()<48000 || b.positionFrames()>=144000) throw new FdxException("Loop position escaped interval");
                    mixer.busGain(AudioBus.SFX,.2f); phase=5;
                }
                if(phase==5 && elapsed>=4.8) {
                    int underruns=a.underruns()+b.underruns();
                    mixer.dispose(); assets.dispose(); released=true; phase=6;
                    if(!a.isDisposed() || !b.isDisposed() || audio.activeVoices()!=0) throw new FdxException("Music release retained playback");
                    logger.info("MusicStreamingTest complete: musicDisposed=true,voices=0,underruns="+underruns);
                }
            }
        }
        batch.viewport(framebufferWidth(),framebufferHeight()); batch.begin(CLEAR);
        for(int i=0;i<7;i++) {
            batch.color(i<=phase?.2f:.12f,i<=phase?.7f:.2f,i<=phase?.5f:.3f,1);
            batch.draw(white,-.85f+i*.25f,-.65f,.2f,.1f);
        }
        batch.color(.15f,.25f,.4f,1); batch.draw(white,-.8f,-.1f,1.6f,.15f); batch.draw(white,-.8f,.3f,1.6f,.15f);
        if(a!=null && !released) {
            batch.color(.4f,.75f,1,1); batch.draw(white,-.8f,.3f,a.gain()*3.2f,.15f);
            batch.color(1,.6f,.25f,1); batch.draw(white,-.8f,-.1f,b.gain()*3.2f,.15f);
        }
        batch.end(); finishFrame();
    }
    @Override
    public void dispose() {
        if(input!=null) input.removeProcessor(activation);
        dispose(mixer); dispose(assets); dispose(executor); dispose(batch); dispose(white);
        if(requiresCompletion() && !released) throw new FdxException("Music scenario did not complete");
        verifyDisposed();
    }
}
