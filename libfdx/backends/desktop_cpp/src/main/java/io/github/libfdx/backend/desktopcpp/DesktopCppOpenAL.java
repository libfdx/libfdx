package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

import java.nio.ByteBuffer;

/** Native OpenAL operations. The playback service owns all handles. */
@NativeInclude("libfdx_jnative.hpp")
final class DesktopCppOpenAL {
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

    private DesktopCppOpenAL() {}

    @NativeImport("fdx_cpp_al_open")
    static native long openDevice();

    @NativeImport("fdx_cpp_al_context")
    static native long createContext(long device);

    @NativeImport("fdx_cpp_al_current")
    static native boolean makeCurrent(long context);

    @NativeImport("fdx_cpp_al_destroy_context")
    static native void destroyContext(long context);

    @NativeImport("fdx_cpp_al_close")
    static native void closeOutput(long device);

    @NativeImport("fdx_cpp_al_gen_source")
    static native int alGenSources();

    @NativeImport("fdx_cpp_al_delete_source")
    static native void alDeleteSources(int source);

    @NativeImport("fdx_cpp_al_gen_buffer")
    static native int alGenBuffers();

    @NativeImport("fdx_cpp_al_delete_buffer")
    static native void alDeleteBuffers(int buffer);

    @NativeImport("fdx_cpp_al_source_i")
    static native void alSourcei(int source, int property, int value);

    @NativeImport("fdx_cpp_al_source_f")
    static native void alSourcef(int source, int property, float value);

    @NativeImport("fdx_cpp_al_get_source")
    static native int alGetSourcei(int source, int property);

    @NativeImport("fdx_cpp_al_error")
    static native int alGetError();

    static void alBufferData(int buffer, int format, ByteBuffer pcm, int rate) {
        bufferData(buffer, format, pcm, pcm.remaining(), rate);
    }

    @NativeImport("fdx_cpp_al_buffer_data")
    private static native void bufferData(
            int buffer, int format, ByteBuffer pcm, int bytes, int rate);

    @NativeImport("fdx_cpp_al_play")
    static native void alSourcePlayv(int[] sources);

    @NativeImport("fdx_cpp_al_pause")
    static native void alSourcePausev(int[] sources);

    @NativeImport("fdx_cpp_al_stop")
    static native void alSourceStopv(int[] sources);

    @NativeImport("fdx_cpp_al_rewind")
    static native void alSourceRewindv(int[] sources);

    @NativeImport("fdx_cpp_al_stop_one")
    static native void alSourceStop(int source);

    @NativeImport("fdx_cpp_al_rewind_one")
    static native void alSourceRewind(int source);

    @NativeImport("fdx_cpp_al_queue")
    static native void alSourceQueueBuffers(int source, int buffer);

    @NativeImport("fdx_cpp_al_unqueue")
    static native int alSourceUnqueueBuffers(int source);
}
