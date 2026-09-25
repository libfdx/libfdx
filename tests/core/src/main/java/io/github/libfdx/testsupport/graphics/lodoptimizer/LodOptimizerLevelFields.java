package io.github.libfdx.testsupport.graphics.lodoptimizer;

import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiState;

/** Text editing may temporarily be invalid; validation happens before a generation job starts. */
final class LodOptimizerLevelFields {
    final UiState<String> ratio, error, pixels;
    LodOptimizerLevelFields(float ratio,float error,float pixels) {
        this.ratio = Ui.state(Float.toString(ratio));
        this.error = Ui.state(Float.toString(error));
        this.pixels = Ui.state(Float.toString(pixels));
    }
}
