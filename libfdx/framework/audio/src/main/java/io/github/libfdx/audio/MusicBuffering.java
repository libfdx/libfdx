package io.github.libfdx.audio;

/** Fixed per-stream buffering. More buffers tolerate longer I/O/render stalls at a memory cost. */
public record MusicBuffering(int framesPerBuffer, int buffers) {
    public static final MusicBuffering DEFAULT = new MusicBuffering(4096, 4);
    public MusicBuffering {
        if (framesPerBuffer < 1 || framesPerBuffer > 65536 || buffers < 2 || buffers > 16) {
            throw new IllegalArgumentException("Music buffering requires 1..65536 frames and 2..16 buffers");
        }
    }
}
