package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.audio.*;
import io.github.libfdx.audio.loaders.AudioAssetLoaders;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;
import io.github.libfdx.input.*;

/** Interactive WAV player; bounded runs exercise pool saturation and shared resource lifetime. */
public final class AudioPlaybackTest extends GraphicsParityTest {
    private static final LoadOp CLEAR = LoadOp.clear(.025f, .035f, .055f, 1);
    private final AssetExecutor executor;
    private Audio audio;
    private DefaultAssetManager assets;
    private AssetScope firstScope, secondScope;
    private AssetLease<Sound> first, second;
    private Sound sound;
    private Input input;
    private InputAdapter activationInput;
    private ShowcaseHud hud;
    private ShowcaseFont font;
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("Audio player")
            .colorLoadOp(CLEAR).colorStoreOp(StoreOp.store());
    private final float[] waveform = new float[480];
    private String sourceLabel = "LOADING AUDIO/TONE.WAV";
    private String settingsLabel = "VOLUME 25%     PITCH 1.0X";
    private String voiceLabel = "0 VOICES";
    private int displayedVoices = -1;
    private float gain = .25f, pitch = 1, pan;
    private boolean prepared, activationFailed, stereo;

    private long firstVoice;
    private int frame, startedFrame = -1;
    private boolean released, waitingLogged;

    public AudioPlaybackTest(long frames) { this(frames, null); }
    /** Takes ownership of the optional worker executor. */
    public AudioPlaybackTest(long frames, AssetExecutor executor) { super(frames); this.executor = executor; }
    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "AudioPlaybackTest"); audio = fdx.audio();
        if (audio == null) throw new FdxException("AudioPlaybackTest requires an explicit audio provider");
        assets = new DefaultAssetManager(fdx.files(), executor);
        AudioAssetLoaders.register(assets, audio);
        G2DAssetLoaders.register(assets, graphics);
        font = new ShowcaseFont(assets.createScope(), false);
        hud = new ShowcaseHud(graphics);
        firstScope = assets.createScope(); secondScope = assets.createScope();
        first = firstScope.load(AssetDescriptor.of("audio/tone.wav", Sound.class));
        second = secondScope.load(AssetDescriptor.of("audio/tone.wav", Sound.class));
        first.future().onFailure(error -> { if (!assets.isDisposed()) throw new FdxException("WAV loading failed", error); });
        input = fdx.input();
        activationInput = new InputAdapter() {
            @Override
            public boolean pointerDown(PointerEvent event) { click(event.x(), event.y()); return true; }
            @Override
            public boolean touchDown(TouchEvent event) { click(event.point().x(), event.point().y()); return true; }
            @Override
            public boolean keyDown(KeyEvent event) {
                if (event.repeat()) return false;
                if (requiresCompletion()) { activate(); return true; }
                switch (event.key()) {
                    case SPACE: togglePlayback(); break;
                    case S: audio.stop(firstVoice); break;
                    case LEFT: pan = Math.max(-1, pan - .25f); parameters(); break;
                    case RIGHT: pan = Math.min(1, pan + .25f); parameters(); break;
                    case UP: gain = Math.min(1, gain + .05f); parameters(); break;
                    case DOWN: gain = Math.max(0, gain - .05f); parameters(); break;
                    case P: cyclePitch(); break;
                    default: return false;
                }
                return true;
            }
        };
        input.addProcessor(activationInput);
        markCreated();
    }
    private void activate() {
        audio.resume().onSuccess(ignored -> logger.info("AudioPlaybackTest gesture activation complete"))
                .onFailure(error -> { activationFailed = true; logger.error("Audio activation failed", error); });
    }
    @Override
    public void render() {
        assets.update(3, 1_000_000);
        if (assets.lastUpdateTaskCount() > 3) throw new FdxException("Audio asset budget exceeded");
        hud.font(font.poll());
        if (!prepared && first.isLoaded() && second.isLoaded()) {
            sound = first.asset();
            if (sound != second.asset()) throw new FdxException("Scopes duplicated the sound");
            PcmData pcm = assets.find("audio/tone.wav", PcmData.class);
            stereo = pcm.channels() == 2;
            int window = Math.min(pcm.frames(), pcm.sampleRate() / 100);
            for (int i = 0; i < waveform.length; i++) {
                waveform[i] = pcm.sample(i * (window - 1) / (waveform.length - 1), 0) / 32768f;
            }
            sourceLabel = "TONE.WAV / " + pcm.sampleRate() + " HZ / " + (pcm.channels() == 1 ? "MONO" : "STEREO");
            prepared = true;
        }
        if (requiresCompletion() && prepared && hud.hasFont() && startedFrame < 0) {
            if (audio.isSuspended()) {
                if (!waitingLogged) { logger.info("AudioPlaybackTest waiting for gesture: suspended=true"); waitingLogged = true; }
            } else {
                sound = first.asset();
                if (sound != second.asset()) throw new FdxException("Scopes duplicated the sound");
                for (int i = 0; i < 32; i++) {
                    long voice = audio.play(sound, .02f, 1, -1, true);
                    if (voice == 0) throw new FdxException("Expected 32 simultaneous voices");
                    if (i == 0) firstVoice = voice;
                }
                if (audio.play(sound) != 0) throw new FdxException("Pool saturation did not reject a voice");
                firstScope.dispose();
                if (sound.isDisposed()) throw new FdxException("First scope disposed the shared sound");
                startedFrame = frame;
                logger.info("AudioPlaybackTest started: voices=32, saturated=true, sharedSound=true");
            }
        }
        if (requiresCompletion() && startedFrame >= 0) {
            int phase = frame - startedFrame;
            if (phase == 30) { audio.pause(firstVoice); audio.parameters(firstVoice, .02f, 2, 1); audio.resume(firstVoice); }
            if (phase == 45) { audio.suspend(); logger.info("AudioPlaybackTest suspended: voices=" + audio.activeVoices()); }
            if (phase == 100) audio.resume().onSuccess(ignored -> logger.info("AudioPlaybackTest resumed"));
            if (phase == 115) {
                audio.stop(firstVoice); long replacement = audio.play(sound, .02f, 1, 1, true);
                if (replacement == 0 || audio.stop(firstVoice)) throw new FdxException("Stale voice affected replacement");
            }
            if (phase == 140) {
                secondScope.dispose(); released = true;
                if (!sound.isDisposed() || audio.activeVoices() != 0 || assets.find("audio/tone.wav", PcmData.class) != null) {
                    throw new FdxException("Final scope retained sound/PCM/voices");
                }
                logger.info("AudioPlaybackTest complete: voices=0, soundDisposed=true, pcmReleased=true");
            }
        }
        drawScene();
        frame++;
        if (hud.hasFont()) finishFrame();
    }

    private void togglePlayback() {
        if (!prepared) { activate(); return; }
        activationFailed = false;
        if (audio.isSuspended()) {
            audio.resume().onSuccess(ignored -> { if (!assets.isDisposed()) playOrPause(); })
                    .onFailure(error -> { activationFailed = true; logger.error("Audio activation failed", error); });
        } else playOrPause();
    }

    private void playOrPause() {
        switch (audio.state(firstVoice)) {
            case PLAYING: audio.pause(firstVoice); break;
            case PAUSED: audio.resume(firstVoice); break;
            case STOPPED:
                firstVoice = audio.play(sound, gain, pitch, pan, true);
                if (firstVoice == Audio.NO_VOICE) throw new FdxException("Audio player voice pool exhausted");
                break;
        }
    }

    private void parameters() {
        audio.parameters(firstVoice, gain, pitch, pan);
        settingsLabel = "VOLUME " + Math.round(gain * 100) + "%     PITCH " + pitch + "X";
    }

    private void cyclePitch() {
        pitch = pitch == .5f ? 1 : pitch == 1 ? 2 : .5f;
        parameters();
    }

    private void click(int px, int py) {
        if (requiresCompletion()) { activate(); return; }
        float scale = Math.min(display.width() / 960f, display.height() / 640f);
        float x = (px - (display.width() - 960 * scale) / 2) / scale;
        float y = (py - (display.height() - 640 * scale) / 2) / scale;
        if (y >= 398 && y <= 446) {
            if (x >= 48 && x < 218) togglePlayback();
            else if (x >= 230 && x < 350) audio.stop(firstVoice);
            else if (x >= 362 && x < 532) cyclePitch();
        }
        if (y >= 484 && y <= 524) {
            if (x >= 48 && x <= 448) { pan = (x - 48) / 200 - 1; parameters(); }
            if (x >= 520 && x <= 912) { gain = (x - 520) / 392; parameters(); }
        }
    }

    private float channelGain(int channel) {
        if (stereo) return Math.min(1, 1 + (channel == 0 ? -pan : pan));
        double angle = (pan + 1) * Math.PI * .25;
        return (float)(channel == 0 ? Math.cos(angle) : Math.sin(angle));
    }

    private void drawScene() {
        int width = framebufferWidth(), height = framebufferHeight();
        float scale = Math.min(width / 960f, height / 640f);
        GraphicsFrame current = graphics.currentFrame();
        RenderPass pass = current.commandEncoder().beginRenderPass(screen.colorAttachment(current.colorAttachment()));
        hud.begin(pass, width, height, scale, (width - 960 * scale) / 2, (height - 640 * scale) / 2);
        VoiceState state = audio.state(firstVoice);
        boolean playing = state == VoiceState.PLAYING && !audio.isSuspended();
        String status = !prepared ? "LOADING" : activationFailed ? "ACTIVATION FAILED" : released ? "CHECKS PASSED"
                : audio.isSuspended() ? "OUTPUT SUSPENDED" : playing ? "PLAYING" : state == VoiceState.PAUSED ? "PAUSED" : "READY / STOPPED";
        int voices = audio.activeVoices();
        if (voices != displayedVoices) { displayedVoices = voices; voiceLabel = voices + " / " + audio.maxVoices() + " VOICES"; }
        hud.text("LIBFDX / AUDIO LAB", 32, 24, 1.1f, .35f, .85f, .74f);
        hud.text("HEAR THE DIFFERENCE.", 32, 54, 2.8f, .91f, .96f, 1);
        hud.text(requiresCompletion() ? "AUTOMATED / PLAYBACK AND RESOURCE LIFETIME CHECKS" : "PLAY A LOOP. CHANGE ITS PITCH. MOVE IT BETWEEN YOUR SPEAKERS.", 34, 102, 1, .54f, .65f, .73f);
        hud.rect(32, 142, 896, 232, .055f, .09f, .13f, 1);
        hud.text("01 / SOURCE WAVEFORM", 48, 160, 1.15f, .65f, .76f, .82f);
        hud.text(sourceLabel, 48, 188, 1, .4f, .61f, .7f);
        for (int i = 0; i <= 8; i++) hud.rect(48 + i * 70, 218, 1, 124, .1f, .16f, .2f, 1);
        hud.rect(48, 280, 560, 1, .2f, .3f, .35f, 1);
        // Static source samples, not an invented spectrum or a device playback cursor.
        for (int i = 1; i < waveform.length; i++) {
            float a = 280 - waveform[i - 1] * 58, b = 280 - waveform[i] * 58;
            hud.rect(48 + i * 560f / waveform.length, Math.min(a, b), 1.6f, Math.max(1.6f, Math.abs(b - a)), .32f, .87f, .73f, 1);
        }
        hud.text("FIRST 10 MS / SOURCE AMPLITUDE", 48, 350, .85f, .4f, .61f, .7f);
        hud.rect(636, 160, 1, 194, .14f, .22f, .27f, 1);
        hud.text(status, 656, 168, 1.1f, .5f, .9f, .77f);
        hud.text(voiceLabel, 656, 196, 1, .65f, .76f, .82f);
        hud.text("STEREO GAIN", 656, 238, .95f, .65f, .76f, .82f);
        for (int channel = 0; channel < 2; channel++) {
            float level = playing && !requiresCompletion() ? gain * channelGain(channel) : 0;
            hud.text(channel == 0 ? "L" : "R", 656, 268 + channel * 30, 1, .65f, .76f, .82f);
            hud.rect(678, 266 + channel * 30, 226, 12, .1f, .18f, .23f, 1);
            hud.rect(678, 266 + channel * 30, 226 * level, 12, .32f, .87f, .73f, 1);
        }
        hud.text("SET GAIN / NOT A LIVE METER", 656, 334, .8f, .4f, .61f, .7f);
        if (!requiresCompletion()) {
            hud.button(playing ? "PAUSE / SPACE" : "PLAY / SPACE", 48, 398, 170, 48, playing);
            hud.button("STOP / S", 230, 398, 120, 48, false);
            hud.button("PITCH / P", 362, 398, 170, 48, false);
            hud.text(settingsLabel, 554, 414, 1, .72f, .81f, .87f);
            hud.text("PAN / LEFT AND RIGHT ARROWS", 48, 472, 1, .65f, .76f, .82f);
            hud.text("VOLUME / UP AND DOWN ARROWS", 520, 472, 1, .65f, .76f, .82f);
            hud.rect(48, 504, 400, 4, .14f, .24f, .3f, 1);
            hud.rect(244 + pan * 200, 496, 8, 20, .32f, .87f, .73f, 1);
            hud.rect(520, 504, 392, 4, .14f, .24f, .3f, 1);
            hud.rect(520, 504, 392 * gain, 4, .32f, .87f, .73f, 1);
            hud.rect(516 + gain * 392, 496, 8, 20, .32f, .87f, .73f, 1);
            hud.text("LEFT", 48, 534, .85f, .4f, .61f, .7f);
            hud.text("CENTER", 228, 534, .85f, .4f, .61f, .7f);
            hud.text("RIGHT", 418, 534, .85f, .4f, .61f, .7f);
            hud.text("0%", 520, 534, .85f, .4f, .61f, .7f);
            hud.text("100%", 882, 534, .85f, .4f, .61f, .7f);
        } else {
            hud.text(released ? "PASS / SHARED SOUND, VOICE POOL, STALE HANDLE, FINAL RELEASE" : "RUNNING / SATURATION > PAUSE > SUSPEND > RESUME > RELEASE", 48, 412, 1.1f, .72f, .81f, .87f);
        }
        hud.text("HEADPHONES MAKE THE LEFT / RIGHT COMPARISON EASIER TO HEAR.", 48, 590, 1, .4f, .61f, .7f);
        hud.end();
        pass.end();
    }
    @Override
    public void dispose() {
        if (input != null) input.removeProcessor(activationInput);
        dispose(hud); dispose(font); dispose(assets); dispose(executor);
        if (requiresCompletion() && !released) throw new FdxException("Audio scenario did not complete activation/playback/release");
        verifyDisposed();
    }
}
