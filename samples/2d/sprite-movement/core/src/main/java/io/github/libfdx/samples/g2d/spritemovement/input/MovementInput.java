package io.github.libfdx.samples.g2d.spritemovement.input;

/** Allocation-free movement input consumed by the portable application. */
public interface MovementInput {
    float horizontal();

    float vertical();
}
