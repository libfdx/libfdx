package io.github.libfdx.graphics.effects;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;

/** Explicit scene-resolution and effect budgets. Scene sizing is a recommendation applied by the caller.
 * LOW: 3/4 scene resolution, no bloom/AA. BALANCED: full scene, quarter-size bloom, one blur pair, edge AA.
 * HIGH: full HDR scene, half-size bloom, two blur pairs, edge AA. LDR scene/bloom targets use sRGB storage
 * to retain dark-color precision while shaders operate in linear RGB. No automatic provider substitution. */
public enum EffectQuality {
    LOW(TextureFormat.RGBA8_UNORM_SRGB, .75f, 4, 0, false),
    BALANCED(TextureFormat.RGBA8_UNORM_SRGB, 1, 4, 1, true),
    HIGH(TextureFormat.RGBA16_FLOAT, 1, 2, 2, true);

    private final TextureFormat sceneFormat;
    private final float sceneScale;
    private final int bloomDivisor, blurPairs;
    private final boolean edgeAntialiasing;
    EffectQuality(TextureFormat format, float scale, int divisor, int pairs, boolean aa) {
        sceneFormat = format; sceneScale = scale; bloomDivisor = divisor; blurPairs = pairs; edgeAntialiasing = aa;
    }
    public TextureFormat sceneFormat() { return sceneFormat; }
    public float sceneScale() { return sceneScale; }
    public int bloomDivisor() { return bloomDivisor; }
    public int blurPairs() { return blurPairs; }
    public boolean edgeAntialiasing() { return edgeAntialiasing; }
    public int sceneDimension(int presentationPixels) {
        if (presentationPixels <= 0) throw new FdxException("Presentation size must be positive");
        return Math.max(1, (int)(presentationPixels * sceneScale));
    }
    /** Checks texture, blend, pipeline and orientation requirements before allocating resources.
     * Additional scene requirements such as explicit depth or MSAA remain the caller's responsibility. */
    public boolean supported(GraphicsCapabilities caps) {
        return caps != null && caps.renderedTextureOrigin() != TextureOrigin.UNKNOWN
                && caps.supportsColorFormat(sceneFormat) && caps.supportsColorFiltering(sceneFormat)
                && caps.supportsColorBlending(sceneFormat) && caps.supportsSampleCount(sceneFormat, 1)
                && caps.supportsColorFormat(TextureFormat.RGBA8_UNORM)
                && caps.supportsColorFiltering(TextureFormat.RGBA8_UNORM)
                && (caps.supports(GraphicsFeature.ALPHA_BLEND_CONTROL)
                    || caps.supports(GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE));
    }
    public void require(GraphicsCapabilities caps) {
        if (!supported(caps)) throw new FdxException("Effect quality " + this
                + " requires renderable/filterable/blendable " + sceneFormat + ", RGBA8 presentation and a known rendered origin");
    }
    /** Explicit opt-in selection. Returns the highest supported budget, or fails if even LOW is unavailable.
     * Compare the result with the desired preset to report a fallback to users. */
    public static EffectQuality bestSupported(GraphicsCapabilities caps) {
        if (HIGH.supported(caps)) return HIGH;
        if (BALANCED.supported(caps)) return BALANCED;
        LOW.require(caps); return LOW;
    }
}
