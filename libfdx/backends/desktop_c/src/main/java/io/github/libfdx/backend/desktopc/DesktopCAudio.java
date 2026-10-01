package io.github.libfdx.backend.desktopc;

import static io.github.libfdx.backend.desktopc.DesktopCOpenAL.*;

import io.github.libfdx.audio.BufferedMusic;
import io.github.libfdx.audio.MusicBuffering;
import io.github.libfdx.audio.PcmData;
import io.github.libfdx.audio.PcmStream;
import io.github.libfdx.audio.PooledAudio;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Desktop native playback with preallocated sources. Native mixing continues when the application
 * thread is busy. This service binds its own thread-local context before operations; matching
 * provider IDs do not permit sharing sounds between services. Requires AL_SOFT_direct_channels so
 * stereo pan bypasses speaker virtualization, including HRTF, for both sounds and music.
 */
final class DesktopCAudio extends PooledAudio {
    /** Logical provider identity. */
    public static final ProviderId ID = ProviderId.of("openal_audio");

    private final Thread owner = Thread.currentThread();
    private long device, context;
    private final int[] sources;
    private final boolean[] stereo;
    private final boolean[] started;
    private int[] pair;

    DesktopCAudio(int maxVoices) {
        super(maxVoices);
        sources = new int[maxVoices * 2];
        stereo = new boolean[maxVoices];
        started = new boolean[maxVoices];
        try {
            device = openDevice();
            if (device == 0) throw new FdxException("OpenAL output device could not be opened");
            context = createContext(device);
            if (context == 0 || !makeCurrent(context))
                throw new FdxException("OpenAL context creation failed");
            pair = new int[2];
            for (int i = 0; i < sources.length; i++) {
                sources[i] = alGenSources();
                checkError("source allocation");
                directChannels(sources[i]);
            }
        } catch (RuntimeException | Error error) {
            closeDevice();
            throw error;
        }
    }

    @Override
    public ProviderId providerId() {
        return ID;
    }

    /** Returns this explicit provider view while live. */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T as() {
        checkLive();
        return (T) this;
    }

    @Override
    protected void checkDevice() {
        if (Thread.currentThread() != owner)
            throw new FdxException("Audio must be used on its creating application thread");
        if (!makeCurrent(context)) throw new FdxException("OpenAL context binding failed");
    }

    private static void directChannels(int source) {
        // Stereo buffers alone still represent virtual speakers under HRTF. Route
        // their already-panned samples to the matching physical output channels.
        alSourcei(source, AL_DIRECT_CHANNELS_SOFT, AL_TRUE);
        checkError("direct channel routing");
    }

    @Override
    protected Object upload(PcmData pcm) {
        // Two stereo buffers keep panning independent of speaker layout/spatializer.
        // Each buffer carries only one output channel; mono uses the same input twice.
        Buffer buffer = new Buffer(pcm.channels() == 2);
        ByteBuffer staging =
                ByteBuffer.allocateDirect(Math.multiplyExact(pcm.frames(), 4))
                        .order(ByteOrder.nativeOrder());
        try {
            for (int channel = 0; channel < 2; channel++) {
                int id = alGenBuffers();
                buffer.ids[channel] = id;
                checkError("buffer allocation");
                staging.clear();
                for (int frame = 0; frame < pcm.frames(); frame++) {
                    short sample = pcm.sample(frame, pcm.channels() == 1 ? 0 : channel);
                    staging.putShort(channel == 0 ? sample : (short) 0);
                    staging.putShort(channel == 1 ? sample : (short) 0);
                }
                staging.flip();
                alBufferData(id, AL_FORMAT_STEREO16, staging, pcm.sampleRate());
                checkError("PCM upload");
            }
            return buffer;
        } catch (RuntimeException | Error error) {
            release(buffer);
            throw error;
        }
    }

    @Override
    protected void release(Object resource) {
        Buffer buffer = (Buffer) resource;
        for (int id : buffer.ids) if (id != 0) alDeleteBuffers(id);
        checkError("buffer release");
    }

    private int[] pair(int slot) {
        pair[0] = sources[slot * 2];
        pair[1] = sources[slot * 2 + 1];
        return pair;
    }

    @Override
    protected void start(
            int slot,
            Object resource,
            float gain,
            float pitch,
            float pan,
            boolean loop,
            boolean paused) {
        Buffer buffer = (Buffer) resource;
        stereo[slot] = buffer.stereo;
        try {
            for (int channel = 0; channel < 2; channel++) {
                int source = sources[slot * 2 + channel];
                alSourcei(source, AL_BUFFER, buffer.ids[channel]);
                alSourcei(source, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
            }
            parametersSlot(slot, gain, pitch, pan);
            started[slot] = false;
            if (!paused) resumeSlot(slot);
        } catch (RuntimeException | Error error) {
            stopSlot(slot);
            throw error;
        }
    }

    @Override
    protected void stopSlot(int slot) {
        alSourceStopv(pair(slot));
        alSourcei(sources[slot * 2], AL_BUFFER, 0);
        alSourcei(sources[slot * 2 + 1], AL_BUFFER, 0);
        started[slot] = false;
        checkError("voice stop");
    }

    @Override
    protected void pauseSlot(int slot) {
        alSourcePausev(pair(slot));
        checkError("voice pause");
    }

    @Override
    protected void resumeSlot(int slot) {
        alSourcePlayv(pair(slot));
        started[slot] = true;
        checkError("voice resume");
    }

    @Override
    protected void parametersSlot(int slot, float gain, float pitch, float pan) {
        float left =
                stereo[slot] ? Math.min(1, 1 - pan) : (float) Math.cos((pan + 1) * Math.PI * 0.25);
        float right =
                stereo[slot] ? Math.min(1, 1 + pan) : (float) Math.sin((pan + 1) * Math.PI * 0.25);
        alSourcef(sources[slot * 2], AL_GAIN, gain * left);
        alSourcef(sources[slot * 2 + 1], AL_GAIN, gain * right);
        alSourcef(sources[slot * 2], AL_PITCH, pitch);
        alSourcef(sources[slot * 2 + 1], AL_PITCH, pitch);
        checkError("voice parameters");
    }

    @Override
    protected boolean finished(int slot) {
        return started[slot] && alGetSourcei(sources[slot * 2], AL_SOURCE_STATE) == AL_STOPPED;
    }

    @Override
    protected FdxFuture<Void> activate() {
        return FdxFuture.completed(null);
    }

    @Override
    protected boolean platformSuspended() {
        return false;
    }

    @Override
    protected boolean supportsMusic() {
        return true;
    }

    @Override
    protected BufferedMusic openMusic(PcmStream source, MusicBuffering buffering) {
        return new NativeMusic(source, buffering);
    }

    private final class NativeMusic extends BufferedMusic {
        private final int[] streamSources = new int[2];
        private final int[] buffers;
        private ByteBuffer staging;
        private int[] streamPair;
        private int tail;

        NativeMusic(PcmStream source, MusicBuffering buffering) {
            super(DesktopCAudio.this, source, buffering);
            buffers = new int[buffering.buffers() * 2];
            try {
                staging =
                        ByteBuffer.allocateDirect(buffering.framesPerBuffer() * 4)
                                .order(ByteOrder.nativeOrder());
                streamPair = new int[2];
                for (int i = 0; i < 2; i++) {
                    streamSources[i] = alGenSources();
                    checkError("music source allocation");
                    directChannels(streamSources[i]);
                    streamPair[i] = streamSources[i];
                }
                for (int i = 0; i < buffers.length; i++) {
                    buffers[i] = alGenBuffers();
                    checkError("music buffer allocation");
                }
                parameters(1, 0);
            } catch (RuntimeException | Error error) {
                try {
                    closePlayback();
                } catch (RuntimeException | Error close) {
                    error.addSuppressed(close);
                }
                throw error;
            }
        }

        @Override
        protected void enqueue(short[] samples, int frames) {
            for (int channel = 0; channel < 2; channel++) {
                staging.clear();
                for (int frame = 0; frame < frames; frame++) {
                    short sample = samples[frame * channels() + (channels() == 1 ? 0 : channel)];
                    staging.putShort(channel == 0 ? sample : (short) 0);
                    staging.putShort(channel == 1 ? sample : (short) 0);
                }
                staging.flip();
                alBufferData(
                        buffers[tail * 2 + channel], AL_FORMAT_STEREO16, staging, sampleRate());
                checkError("music PCM upload");
            }
            try {
                for (int c = 0; c < 2; c++) {
                    alSourceQueueBuffers(streamSources[c], buffers[tail * 2 + c]);
                }
                checkError("music buffer queue");
                tail = (tail + 1) % (buffers.length / 2);
            } catch (RuntimeException | Error error) {
                try {
                    clearPlayback();
                } catch (RuntimeException | Error clear) {
                    error.addSuppressed(clear);
                }
                throw error;
            }
        }

        @Override
        protected int processed() {
            int count =
                    Math.min(
                            alGetSourcei(streamSources[0], AL_BUFFERS_PROCESSED),
                            alGetSourcei(streamSources[1], AL_BUFFERS_PROCESSED));
            for (int i = 0; i < count; i++) {
                alSourceUnqueueBuffers(streamSources[0]);
                alSourceUnqueueBuffers(streamSources[1]);
            }
            // STOPPED sources report newly queued buffers as processed until restarted.
            // Return an empty queue to INITIAL while the portable layer prebuffers.
            if (count > 0 && alGetSourcei(streamSources[0], AL_BUFFERS_QUEUED) == 0) {
                alSourceRewindv(streamPair);
            }
            checkError("music completed buffers");
            return count;
        }

        @Override
        protected int sampleOffset() {
            int offset = alGetSourcei(streamSources[0], AL_SAMPLE_OFFSET);
            checkError("music position");
            return offset;
        }

        @Override
        protected void startPlayback() {
            if (alGetSourcei(streamSources[0], AL_SOURCE_STATE) != AL_PLAYING) {
                alSourcePlayv(streamPair);
            }
            checkError("music play");
        }

        @Override
        protected void pausePlayback() {
            if (alGetSourcei(streamSources[0], AL_SOURCE_STATE) == AL_PLAYING) {
                alSourcePausev(streamPair);
            }
            checkError("music pause");
        }

        @Override
        protected void clearPlayback() {
            for (int source : streamSources) {
                if (source != 0) {
                    alSourceStop(source);
                    alSourcei(source, AL_BUFFER, 0);
                    alSourceRewind(source);
                }
            }
            tail = 0;
            checkError("music queue clear");
        }

        @Override
        protected void parameters(float gain, float pan) {
            float left =
                    channels() == 2
                            ? Math.min(1, 1 - pan)
                            : (float) Math.cos((pan + 1) * Math.PI * 0.25);
            float right =
                    channels() == 2
                            ? Math.min(1, 1 + pan)
                            : (float) Math.sin((pan + 1) * Math.PI * 0.25);
            alSourcef(streamSources[0], AL_GAIN, gain * left);
            alSourcef(streamSources[1], AL_GAIN, gain * right);
            checkError("music parameters");
        }

        @Override
        protected void closePlayback() {
            for (int i = 0; i < streamSources.length; i++) {
                if (streamSources[i] != 0) {
                    alDeleteSources(streamSources[i]);
                    streamSources[i] = 0;
                }
            }
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != 0) {
                    alDeleteBuffers(buffers[i]);
                    buffers[i] = 0;
                }
            }
            if (staging != null) {
                staging = null;
            }
            if (streamPair != null) {
                streamPair = null;
            }
            checkError("music release");
        }
    }

    @Override
    protected void closeDevice() {
        if (context != 0) {
            makeCurrent(context);
            for (int source : sources) if (source != 0) alDeleteSources(source);
            makeCurrent(0);
            destroyContext(context);
            context = 0;
        }
        if (device != 0) {
            closeOutput(device);
            device = 0;
        }
        pair = null;
    }

    private static void checkError(String operation) {
        int error = alGetError();
        if (error != AL_NO_ERROR)
            throw new FdxException("OpenAL " + operation + " failed: " + error);
    }

    private static final class Buffer {
        final int[] ids = new int[2];
        final boolean stereo;

        Buffer(boolean stereo) {
            this.stereo = stereo;
        }
    }
}
