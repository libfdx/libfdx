package io.github.libfdx.audio.loaders;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.audio.Audio;
import io.github.libfdx.audio.PcmData;
import io.github.libfdx.audio.Sound;
import io.github.libfdx.audio.Music;
import io.github.libfdx.audio.MusicBuffering;
import io.github.libfdx.core.FdxFuture;

/** Dependency-aware WAV preparation and application-thread sound creation. */
public final class AudioAssetLoaders {
    /** Default decoded limit: 30 seconds at 48 kHz (frame count, independent of source rate). */
    public static final int DEFAULT_MAX_FRAMES = 1_440_000;
    private AudioAssetLoaders() { }
    /**
     * Registers streamed WAV Music. The executor is borrowed for subsequent decode
     * reads and must outlive the assets; null uses cooperative reads. Open/header
     * preparation and native creation obey the manager's budget. Buffer configuration
     * is fixed for this registration. A cached Music is one shared playback instance,
     * including position and controls; use createMusic with separate streams for
     * independent playback of the same track. It has no cached whole-track dependency.
     */
    public static void registerMusic(AssetManager assets, Audio audio, MusicBuffering buffering, AssetExecutor executor) {
        if (assets == null || audio == null || audio.isDisposed() || buffering == null || audio.maxMusicStreams() == 0) {
            throw new IllegalArgumentException("Live manager and streaming audio provider required");
        }
        assets.registerLoader(Music.class,new AssetLoader<Music>() {
            @Override public Class<Music> type() { return Music.class; }
            @Override public FdxFuture<Music> load(AssetLoadContext context,AssetDescriptor<Music> descriptor) {
                var file = context.files().internal(descriptor.path());
                FdxFuture<Music> result = FdxFuture.pending();
                context.asyncFuture(() -> WavStream.openFile(file,buffering.framesPerBuffer(),executor)).onSuccess(pcm -> {
                    try {
                        context.completeOnUpdate(() -> audio.createMusic(pcm,buffering))
                                .onSuccess(result::complete).onFailure(error -> {
                                    try { pcm.dispose(); } finally { result.completeExceptionally(error); }
                                });
                    } catch (RuntimeException | Error error) {
                        try { pcm.dispose(); } finally { result.completeExceptionally(error); }
                    }
                }).onFailure(result::completeExceptionally);
                return result;
            }
        });
    }
    /** Registers loaders. The manager borrows audio and must be disposed before it. */
    public static void register(AssetManager assets, Audio audio) { register(assets, audio, DEFAULT_MAX_FRAMES); }
    /**
     * Registers bounded PCM and sound loaders. One Audio domain per manager; registering
     * replacements while entries remain live is rejected by the manager. Encoded file
     * acquisition still uses readBytes; the frame bound limits decoded allocation only.
     */
    public static void register(AssetManager assets, Audio audio, int maxFrames) {
        if (assets == null || audio == null || audio.isDisposed() || maxFrames < 1) {
            throw new IllegalArgumentException("Live assets/audio and positive maxFrames required");
        }
        assets.registerLoader(PcmData.class, new AssetLoader<PcmData>() {
            @Override public Class<PcmData> type() { return PcmData.class; }
            @Override public FdxFuture<PcmData> load(AssetLoadContext context, AssetDescriptor<PcmData> descriptor) {
                FdxFuture<PcmData> result = FdxFuture.pending();
                context.readBytes(context.files().internal(descriptor.path())).onSuccess(bytes -> {
                    try {
                        context.async(() -> WavDecoder.decode(bytes, maxFrames))
                                .onSuccess(result::complete).onFailure(result::completeExceptionally);
                    } catch (RuntimeException | Error error) { result.completeExceptionally(error); }
                }).onFailure(result::completeExceptionally);
                return result;
            }
        });
        assets.registerLoader(Sound.class, new AssetLoader<Sound>() {
            @Override public Class<Sound> type() { return Sound.class; }
            @Override public FdxFuture<Sound> load(AssetLoadContext context, AssetDescriptor<Sound> descriptor) {
                FdxFuture<PcmData> pcm = context.dependency(AssetDescriptor.of(descriptor.path(), PcmData.class));
                return context.completeOnUpdate(() -> audio.createSound(pcm.get()));
            }
        });
    }
}
