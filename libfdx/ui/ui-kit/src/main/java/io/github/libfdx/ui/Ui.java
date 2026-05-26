package io.github.libfdx.ui;

public final class Ui {
    private Ui() {
    }

    public static UiModifier modifier() {
        return UiModifier.none();
    }

    public static UiBooleanState state(boolean value) {
        return new UiBooleanState(value);
    }

    public static UiIntState state(int value) {
        return new UiIntState(value);
    }

    public static UiFloatState state(float value) {
        return new UiFloatState(value);
    }

    public static UiLongState state(long value) {
        return new UiLongState(value);
    }

    public static UiDoubleState state(double value) {
        return new UiDoubleState(value);
    }

    public static <T> UiState<T> state(T value) {
        return new UiState<T>(value);
    }

    public static UiAnimationSpec animation() {
        return UiAnimationSpec.defaultSpec();
    }

    public static UiTransition transition() {
        return UiTransition.create();
    }

    public static UiInsets insets(float all) {
        return UiInsets.of(all);
    }

    public static UiInsets insets(float horizontal, float vertical) {
        return UiInsets.of(horizontal, vertical);
    }

    public static UiInsets insets(float left, float top, float right, float bottom) {
        return UiInsets.of(left, top, right, bottom);
    }

    public static UiSize size(float width, float height) {
        return new UiSize(width, height);
    }

    public static UiColor rgb(float red, float green, float blue) {
        return UiColor.rgb(red, green, blue);
    }

    public static UiColor rgba(float red, float green, float blue, float alpha) {
        return UiColor.rgba(red, green, blue, alpha);
    }

    public static UiColor rgba8888(int rgba) {
        return UiColor.rgba8888(rgba);
    }

    public static UiRange range(float minimum, float maximum) {
        return new UiRange(minimum, maximum);
    }

    public static UiTheme darkTheme() {
        return UiTheme.dark();
    }

    public static UiTheme lightTheme() {
        return UiTheme.light();
    }

    public static UiStyle style() {
        return UiStyle.style();
    }

    public static UiTextStyle textStyle() {
        return UiTextStyle.text();
    }

    public static UiTooltip tooltip(String text) {
        return UiTooltip.text(text);
    }

    public static UiModal modal(String id) {
        return UiModal.modal(id);
    }

    public static UiPopup popup(String id) {
        return UiPopup.popup(id);
    }
}
