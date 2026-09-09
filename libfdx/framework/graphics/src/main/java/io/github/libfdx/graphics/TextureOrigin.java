package io.github.libfdx.graphics;

/** Position of texture coordinate (0,0) after a render pass. Upload row order is a separate convention. */
public enum TextureOrigin {
    /** Provider has not declared its rendered-image convention. */ UNKNOWN,
    /** v=0 addresses the top row. */ TOP_LEFT,
    /** v=0 addresses the bottom row. */ BOTTOM_LEFT;

    /** Converts top-to-bottom normalized image coordinates to native texture v. */
    public float v(float topDownV) {
        if (this == UNKNOWN) throw new IllegalStateException("Rendered texture origin is unknown");
        return this == BOTTOM_LEFT ? 1 - topDownV : topDownV;
    }
}
