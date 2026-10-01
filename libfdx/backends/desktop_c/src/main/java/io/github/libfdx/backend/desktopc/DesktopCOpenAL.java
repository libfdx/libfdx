package io.github.libfdx.backend.desktopc;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

import java.nio.ByteBuffer;

/** Native OpenAL operations. The playback service owns all handles. */
@Include("libfdx_desktop_audio.h")
final class DesktopCOpenAL {
    static final int AL_FALSE = 0;
    static final int AL_TRUE = 1;
    static final int AL_NO_ERROR = 0;
    static final int AL_PITCH = 0x1003;
    static final int AL_LOOPING = 0x1007;
    static final int AL_BUFFER = 0x1009;
    static final int AL_GAIN = 0x100A;
    static final int AL_SOURCE_STATE = 0x1010;
    static final int AL_PLAYING = 0x1012;
    static final int AL_STOPPED = 0x1014;
    static final int AL_BUFFERS_QUEUED = 0x1015;
    static final int AL_BUFFERS_PROCESSED = 0x1016;
    static final int AL_SAMPLE_OFFSET = 0x1025;
    static final int AL_FORMAT_STEREO16 = 0x1103;
    static final int AL_DIRECT_CHANNELS_SOFT = 0x1033;

    private DesktopCOpenAL() {}

    static long openDevice() {

        return native_openDevice();
    }

    @Import(name = "fdx_c_al_open")
    private static native long native_openDevice();

    static long createContext(long device) {

        return native_createContext(device);
    }

    @Import(name = "fdx_c_al_context")
    private static native long native_createContext(long device);

    static boolean makeCurrent(long context) {

        return native_makeCurrent(context);
    }

    @Import(name = "fdx_c_al_current")
    private static native boolean native_makeCurrent(long context);

    static void destroyContext(long context) {

        native_destroyContext(context);
    }

    @Import(name = "fdx_c_al_destroy_context")
    private static native void native_destroyContext(long context);

    static void closeOutput(long device) {

        native_closeOutput(device);
    }

    @Import(name = "fdx_c_al_close")
    private static native void native_closeOutput(long device);

    static int alGenSources() {

        return native_alGenSources();
    }

    @Import(name = "fdx_c_al_gen_source")
    private static native int native_alGenSources();

    static void alDeleteSources(int source) {

        native_alDeleteSources(source);
    }

    @Import(name = "fdx_c_al_delete_source")
    private static native void native_alDeleteSources(int source);

    static int alGenBuffers() {

        return native_alGenBuffers();
    }

    @Import(name = "fdx_c_al_gen_buffer")
    private static native int native_alGenBuffers();

    static void alDeleteBuffers(int buffer) {

        native_alDeleteBuffers(buffer);
    }

    @Import(name = "fdx_c_al_delete_buffer")
    private static native void native_alDeleteBuffers(int buffer);

    static void alSourcei(int source, int property, int value) {

        native_alSourcei(source, property, value);
    }

    @Import(name = "fdx_c_al_source_i")
    private static native void native_alSourcei(int source, int property, int value);

    static void alSourcef(int source, int property, float value) {

        native_alSourcef(source, property, value);
    }

    @Import(name = "fdx_c_al_source_f")
    private static native void native_alSourcef(int source, int property, float value);

    static int alGetSourcei(int source, int property) {

        return native_alGetSourcei(source, property);
    }

    @Import(name = "fdx_c_al_get_source")
    private static native int native_alGetSourcei(int source, int property);

    static int alGetError() {

        return native_alGetError();
    }

    @Import(name = "fdx_c_al_error")
    private static native int native_alGetError();

    static void alBufferData(int buffer, int format, ByteBuffer pcm, int rate) {
        bufferData(buffer, format, pcm, pcm.remaining(), rate);
    }

    private static void bufferData(
            int buffer, int format, ByteBuffer pcm, int bytes, int rate) {

        native_bufferData(buffer, format, pcm, pcm == null ? 0 : pcm.remaining(), bytes, rate);
    }

    @Import(name = "fdx_c_al_buffer_data")
    private static native void native_bufferData(int buffer, int format, ByteBuffer pcm, int pcmLength, int bytes, int rate);

    static void alSourcePlayv(int[] sources) {

        native_alSourcePlayv(sources == null ? Address.fromLong(0) : Address.ofData(sources), sources == null ? 0 : sources.length);
    }

    @Import(name = "fdx_c_al_play")
    private static native void native_alSourcePlayv(Address sourcesData, int sourcesLength);

    static void alSourcePausev(int[] sources) {

        native_alSourcePausev(sources == null ? Address.fromLong(0) : Address.ofData(sources), sources == null ? 0 : sources.length);
    }

    @Import(name = "fdx_c_al_pause")
    private static native void native_alSourcePausev(Address sourcesData, int sourcesLength);

    static void alSourceStopv(int[] sources) {

        native_alSourceStopv(sources == null ? Address.fromLong(0) : Address.ofData(sources), sources == null ? 0 : sources.length);
    }

    @Import(name = "fdx_c_al_stop")
    private static native void native_alSourceStopv(Address sourcesData, int sourcesLength);

    static void alSourceRewindv(int[] sources) {

        native_alSourceRewindv(sources == null ? Address.fromLong(0) : Address.ofData(sources), sources == null ? 0 : sources.length);
    }

    @Import(name = "fdx_c_al_rewind")
    private static native void native_alSourceRewindv(Address sourcesData, int sourcesLength);

    static void alSourceStop(int source) {

        native_alSourceStop(source);
    }

    @Import(name = "fdx_c_al_stop_one")
    private static native void native_alSourceStop(int source);

    static void alSourceRewind(int source) {

        native_alSourceRewind(source);
    }

    @Import(name = "fdx_c_al_rewind_one")
    private static native void native_alSourceRewind(int source);

    static void alSourceQueueBuffers(int source, int buffer) {

        native_alSourceQueueBuffers(source, buffer);
    }

    @Import(name = "fdx_c_al_queue")
    private static native void native_alSourceQueueBuffers(int source, int buffer);

    static int alSourceUnqueueBuffers(int source) {

        return native_alSourceUnqueueBuffers(source);
    }

    @Import(name = "fdx_c_al_unqueue")
    private static native int native_alSourceUnqueueBuffers(int source);
}
