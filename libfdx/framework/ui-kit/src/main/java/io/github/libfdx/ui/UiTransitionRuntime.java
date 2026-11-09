package io.github.libfdx.ui;

import java.util.Objects;

/** Node-owned transition values, advanced independently of composition/layout. */
final class UiTransitionRuntime {
    private String state;
    private UiTransition configuration;
    private final UiFloatAnimatable alpha = new UiFloatAnimatable(1);
    private final UiFloatAnimatable scale = new UiFloatAnimatable(1);

    void configure(String state, UiTransition configuration) {
        boolean first = this.configuration == null;
        boolean stateChanged = !Objects.equals(this.state, state);
        boolean valuesChanged = !first && (different(this.configuration.alphaFrom(), configuration.alphaFrom())
                || different(this.configuration.alphaTo(), configuration.alphaTo())
                || different(this.configuration.scaleFrom(), configuration.scaleFrom())
                || different(this.configuration.scaleTo(), configuration.scaleTo()));
        if (first || stateChanged || valuesChanged) {
            if (first || !alpha.isRunning()) alpha.snapTo(value(configuration.alphaFrom(), 1));
            if (first || !scale.isRunning()) scale.snapTo(value(configuration.scaleFrom(), 1));
            alpha.animateTo(value(configuration.alphaTo(), 1), configuration.spec());
            scale.animateTo(value(configuration.scaleTo(), 1), configuration.spec());
        }
        this.state = state;
        this.configuration = configuration;
    }

    boolean update(float delta) {
        boolean running = alpha.isRunning() || scale.isRunning();
        if (running) { alpha.update(delta); scale.update(delta); }
        return running;
    }
    float alpha() { return Math.max(0, Math.min(1, alpha.get())); }
    float scale() { return Math.max(0, scale.get()); }

    private static boolean different(float a, float b) { return Float.compare(a, b) != 0; }
    private static float value(float value, float fallback) { return Float.isFinite(value) ? value : fallback; }
}
