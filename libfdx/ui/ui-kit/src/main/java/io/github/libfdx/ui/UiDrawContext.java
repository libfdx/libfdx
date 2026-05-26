package io.github.libfdx.ui;

public interface UiDrawContext {
    void rect(UiRect bounds, UiColor color);

    void text(String text, UiRect bounds, UiTextStyle style);
}
