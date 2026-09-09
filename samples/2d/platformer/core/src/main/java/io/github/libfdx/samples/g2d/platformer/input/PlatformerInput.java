package io.github.libfdx.samples.g2d.platformer.input;

public interface PlatformerInput {
    boolean leftDown();

    boolean rightDown();

    boolean jumpDown();

    boolean restartDown();

    /** Optional latched transition for taps occurring between simulation ticks. */
    default boolean consumeJumpPress() { return false; }

    default boolean consumeRestartPress() { return false; }
}
