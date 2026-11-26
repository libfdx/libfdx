package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.document.ShaderGraphPreviewAnimation;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphPreviewPlayback;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialInstance;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiTextInputFilter;

import java.util.Objects;

/** Per-document controls and transient material overrides for the surface preview. */
final class ShaderGraphEditorAnimationControls {
    private static final UiModifier ROW = UiModifier.none().fillWidth().gap(4);
    private static final UiModifier FIELD = UiModifier.none().weight(1);
    private final ShaderGraphEditorSession session;
    private final UiBooleanState choosing = Ui.state(false);
    private final UiBooleanState playing = Ui.state(false);
    private final UiState<String> minimum = Ui.state("");
    private final UiState<String> maximum = Ui.state("");
    private final UiState<String> error = Ui.state("");
    private final UiBooleanState timePlaying = Ui.state(false);
    private double timeSeconds;
    private ShaderGraphPreviewPlayback playback;
    private ShaderGraphPreviewAnimation preset;
    private ShaderGraphMaterialInstance material;
    private ShaderGraphLiteral authored;
    private int index = -1;

    ShaderGraphEditorAnimationControls(ShaderGraphEditorSession session) {
        this.session = session;
    }

    void build(UiScope ui) {
        syncPreset();
        var graph = session.document().graph();
        if (graph == null || graph.kind() != ShaderGraphKind.SURFACE) return;
        boolean timed = graph.usesTime();
        if (timed) {
            ui.row(ROW, row -> {
                row.button(timePlaying.get() ? "Pause" : "Play", FIELD, () -> timePlaying.set(!timePlaying.get()));
                row.button("Restart", FIELD, () -> timeSeconds = 0);
            });
        }
        var inputs = graph.parameters();
        boolean eligible = false;
        for (var input : inputs) {
            if (ShaderGraphPreviewAnimation.supports(input)) {
                eligible = true;
                break;
            }
        }
        if (!eligible) return;
        ui.selectableText("Parameter animation");
        ui.button(preset == null ? "Choose parameter" : "Parameter: " + preset.parameter(),
                UiModifier.none().fillWidth(), () -> choosing.set(!choosing.get()));
        if (choosing.get()) {
            for (var input : inputs) {
                if (ShaderGraphPreviewAnimation.supports(input)) {
                    String id = input.id().value();
                    ui.button(id, () -> {
                        if (preset == null || !id.equals(preset.parameter())) {
                            session.previewAnimation(new ShaderGraphPreviewAnimation(id, 0, 1));
                        }
                        choosing.set(false);
                    });
                }
            }
            ui.button("None", () -> {
                session.previewAnimation(null);
                choosing.set(false);
            });
        }
        if (preset == null) return;
        ui.row(ROW, row -> {
            row.selectableText("Min");
            row.textField(FIELD, minimum, UiTextInputFilter.FLOAT, this::applyRange);
            row.selectableText("Max");
            row.textField(FIELD, maximum, UiTextInputFilter.FLOAT, this::applyRange);
        });
        ui.row(ROW, row -> {
            row.button("Apply range", FIELD, this::applyRange);
            String action = playing.get() ? "Stop" : "Play";
            row.button(timed ? action + " range" : action, FIELD, () -> {
                if (playing.get()) stop();
                else if (applyRange()) playing.set(true);
            });
        });
        if (!error.get().isEmpty()) ui.selectableText(error.get());
    }

    private boolean applyRange() {
        try {
            var next = new ShaderGraphPreviewAnimation(preset.parameter(), Float.parseFloat(minimum.get()),
                    Float.parseFloat(maximum.get()));
            if (!next.equals(preset)) session.previewAnimation(next);
            syncPreset();
            error.set("");
            return true;
        } catch (IllegalArgumentException invalid) {
            error.set("Enter finite values with Min below Max.");
            return false;
        }
    }

    private void syncPreset() {
        var next = session.layout().previewAnimation();
        if (Objects.equals(preset, next)) return;
        stop();
        preset = next;
        material = null;
        minimum.set(next == null ? "" : Float.toString(next.min()));
        maximum.set(next == null ? "" : Float.toString(next.max()));
        error.set("");
        playback = next == null ? null : new ShaderGraphPreviewPlayback(next);
    }

    private void stop() {
        playing.set(false);
        if (playback != null) playback.reset();
        if (material != null && index >= 0) material.set(index, authored);
    }

    float update(ShaderGraphMaterialInstance live, float delta) {
        syncPreset();
        if (material != live) {
            if (material != null && index >= 0 && playing.get()) material.set(index, authored);
            material = live;
            index = preset == null ? -1 : live.definition().parameterIndex(preset.parameter());
            if (index >= 0 && !live.value(index).type().equals(playback.advance(0).type())) index = -1;
            authored = index < 0 ? null : live.value(index);
        }
        if (playing.get() && index >= 0) {
            live.set(index, playback.advance(delta));
        }
        if (timePlaying.get()) timeSeconds += delta;
        return (float) timeSeconds;
    }

}
