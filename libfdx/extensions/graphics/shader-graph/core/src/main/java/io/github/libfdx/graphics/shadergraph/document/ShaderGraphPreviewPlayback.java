package io.github.libfdx.graphics.shadergraph.document;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;

/** A forward-only six-second loop over a preview range. Each player owns its clock and cached material values. */
public final class ShaderGraphPreviewPlayback {
    private static final int PERIOD_SECONDS = 6;
    private final ShaderGraphLiteral[] samples = new ShaderGraphLiteral[360];
    private double elapsed;

    public ShaderGraphPreviewPlayback(ShaderGraphPreviewAnimation preset) {
        double span = (double) preset.max() - preset.min();
        for (int i = 0; i < samples.length; i++) {
            double t = (double) i / samples.length;
            samples[i] = ShaderGraphLiteral.f32((float) (preset.min() + t * span));
        }
    }

    /** Moves toward the maximum and wraps to the minimum. A zero delta returns the current value. */
    public ShaderGraphLiteral advance(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("Playback delta must be finite and nonnegative");
        }
        elapsed = (elapsed + deltaSeconds % PERIOD_SECONDS) % PERIOD_SECONDS;
        return samples[(int) (elapsed * samples.length / PERIOD_SECONDS)];
    }

    public void reset() {
        elapsed = 0;
    }

}
