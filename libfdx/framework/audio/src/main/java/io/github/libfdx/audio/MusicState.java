package io.github.libfdx.audio;

/** Stream playback state, including non-audible buffering and terminal I/O failure. */
public enum MusicState { STOPPED, BUFFERING, PLAYING, PAUSED, ENDED, FAILED, DISPOSED }
