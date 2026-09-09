package io.github.libfdx.graphics;

/** Standard piecewise sRGB transfer functions for RGB only; alpha is always linear. */
public final class ColorTransfer {
    private ColorTransfer() { }
    /** Decodes finite sRGB RGB. Negative values clamp to zero; values above one remain extended. */
    public static float srgbToLinear(float value) {
        float c = Math.max(0, value);
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055) / 1.055, 2.4);
    }
    /** Encodes finite linear RGB. Negative values clamp to zero; HDR values are not tone mapped/clamped. */
    public static float linearToSrgb(float value) {
        float c = Math.max(0, value);
        return c <= 0.0031308f ? c * 12.92f : (float) (1.055 * Math.pow(c, 1.0 / 2.4) - 0.055);
    }
}
