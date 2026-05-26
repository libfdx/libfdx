package io.github.libfdx.backend.android;

public final class AndroidTextEditorStyle {
    private int panelBackgroundColor = 0xFFF6F8FA;
    private int panelBorderColor = 0xFFBCC4D0;
    private int editorBackgroundColor = 0xFFFFFFFF;
    private int editorBorderColor = 0xFFCCD2DA;
    private int editorTextColor = 0xFF14181E;
    private int editorHintTextColor = 0xFF5C6470;
    private int acceptButtonBackgroundColor = 0xFF20262E;
    private int acceptButtonTextColor = 0xFFFFFFFF;
    private int cancelButtonBackgroundColor = 0xFFE8ECF1;
    private int cancelButtonBorderColor = 0xFFBCC4D0;
    private int cancelButtonTextColor = 0xFF20262E;
    private float editorTextSizeSp = 16.0f;
    private float actionTextSizeSp = 14.0f;
    private float panelCornerRadiusDp = 4.0f;
    private float editorCornerRadiusDp = 3.0f;
    private float actionCornerRadiusDp = 3.0f;
    private float panelPaddingHorizontalDp = 10.0f;
    private float panelPaddingVerticalDp = 8.0f;
    private float editorPaddingHorizontalDp = 10.0f;
    private float editorPaddingVerticalDp = 8.0f;
    private float panelMarginDp = 8.0f;
    private float actionButtonWidthDp = 48.0f;
    private float actionButtonHeightDp = 38.0f;
    private float actionSpacingDp = 6.0f;
    private String acceptText = "OK";
    private String cancelText = "X";

    public int panelBackgroundColor() {
        return panelBackgroundColor;
    }

    public AndroidTextEditorStyle panelBackgroundColor(int panelBackgroundColor) {
        this.panelBackgroundColor = panelBackgroundColor;
        return this;
    }

    public int panelBorderColor() {
        return panelBorderColor;
    }

    public AndroidTextEditorStyle panelBorderColor(int panelBorderColor) {
        this.panelBorderColor = panelBorderColor;
        return this;
    }

    public int editorBackgroundColor() {
        return editorBackgroundColor;
    }

    public AndroidTextEditorStyle editorBackgroundColor(int editorBackgroundColor) {
        this.editorBackgroundColor = editorBackgroundColor;
        return this;
    }

    public int editorBorderColor() {
        return editorBorderColor;
    }

    public AndroidTextEditorStyle editorBorderColor(int editorBorderColor) {
        this.editorBorderColor = editorBorderColor;
        return this;
    }

    public int editorTextColor() {
        return editorTextColor;
    }

    public AndroidTextEditorStyle editorTextColor(int editorTextColor) {
        this.editorTextColor = editorTextColor;
        return this;
    }

    public int editorHintTextColor() {
        return editorHintTextColor;
    }

    public AndroidTextEditorStyle editorHintTextColor(int editorHintTextColor) {
        this.editorHintTextColor = editorHintTextColor;
        return this;
    }

    public int acceptButtonBackgroundColor() {
        return acceptButtonBackgroundColor;
    }

    public AndroidTextEditorStyle acceptButtonBackgroundColor(int acceptButtonBackgroundColor) {
        this.acceptButtonBackgroundColor = acceptButtonBackgroundColor;
        return this;
    }

    public int acceptButtonTextColor() {
        return acceptButtonTextColor;
    }

    public AndroidTextEditorStyle acceptButtonTextColor(int acceptButtonTextColor) {
        this.acceptButtonTextColor = acceptButtonTextColor;
        return this;
    }

    public int cancelButtonBackgroundColor() {
        return cancelButtonBackgroundColor;
    }

    public AndroidTextEditorStyle cancelButtonBackgroundColor(int cancelButtonBackgroundColor) {
        this.cancelButtonBackgroundColor = cancelButtonBackgroundColor;
        return this;
    }

    public int cancelButtonBorderColor() {
        return cancelButtonBorderColor;
    }

    public AndroidTextEditorStyle cancelButtonBorderColor(int cancelButtonBorderColor) {
        this.cancelButtonBorderColor = cancelButtonBorderColor;
        return this;
    }

    public int cancelButtonTextColor() {
        return cancelButtonTextColor;
    }

    public AndroidTextEditorStyle cancelButtonTextColor(int cancelButtonTextColor) {
        this.cancelButtonTextColor = cancelButtonTextColor;
        return this;
    }

    public float editorTextSizeSp() {
        return editorTextSizeSp;
    }

    public AndroidTextEditorStyle editorTextSizeSp(float editorTextSizeSp) {
        this.editorTextSizeSp = positive(editorTextSizeSp, this.editorTextSizeSp);
        return this;
    }

    public float actionTextSizeSp() {
        return actionTextSizeSp;
    }

    public AndroidTextEditorStyle actionTextSizeSp(float actionTextSizeSp) {
        this.actionTextSizeSp = positive(actionTextSizeSp, this.actionTextSizeSp);
        return this;
    }

    public float panelCornerRadiusDp() {
        return panelCornerRadiusDp;
    }

    public AndroidTextEditorStyle panelCornerRadiusDp(float panelCornerRadiusDp) {
        this.panelCornerRadiusDp = nonNegative(panelCornerRadiusDp);
        return this;
    }

    public float editorCornerRadiusDp() {
        return editorCornerRadiusDp;
    }

    public AndroidTextEditorStyle editorCornerRadiusDp(float editorCornerRadiusDp) {
        this.editorCornerRadiusDp = nonNegative(editorCornerRadiusDp);
        return this;
    }

    public float actionCornerRadiusDp() {
        return actionCornerRadiusDp;
    }

    public AndroidTextEditorStyle actionCornerRadiusDp(float actionCornerRadiusDp) {
        this.actionCornerRadiusDp = nonNegative(actionCornerRadiusDp);
        return this;
    }

    public float panelPaddingHorizontalDp() {
        return panelPaddingHorizontalDp;
    }

    public AndroidTextEditorStyle panelPaddingHorizontalDp(float panelPaddingHorizontalDp) {
        this.panelPaddingHorizontalDp = nonNegative(panelPaddingHorizontalDp);
        return this;
    }

    public float panelPaddingVerticalDp() {
        return panelPaddingVerticalDp;
    }

    public AndroidTextEditorStyle panelPaddingVerticalDp(float panelPaddingVerticalDp) {
        this.panelPaddingVerticalDp = nonNegative(panelPaddingVerticalDp);
        return this;
    }

    public float editorPaddingHorizontalDp() {
        return editorPaddingHorizontalDp;
    }

    public AndroidTextEditorStyle editorPaddingHorizontalDp(float editorPaddingHorizontalDp) {
        this.editorPaddingHorizontalDp = nonNegative(editorPaddingHorizontalDp);
        return this;
    }

    public float editorPaddingVerticalDp() {
        return editorPaddingVerticalDp;
    }

    public AndroidTextEditorStyle editorPaddingVerticalDp(float editorPaddingVerticalDp) {
        this.editorPaddingVerticalDp = nonNegative(editorPaddingVerticalDp);
        return this;
    }

    public float panelMarginDp() {
        return panelMarginDp;
    }

    public AndroidTextEditorStyle panelMarginDp(float panelMarginDp) {
        this.panelMarginDp = nonNegative(panelMarginDp);
        return this;
    }

    public float actionButtonWidthDp() {
        return actionButtonWidthDp;
    }

    public AndroidTextEditorStyle actionButtonWidthDp(float actionButtonWidthDp) {
        this.actionButtonWidthDp = positive(actionButtonWidthDp, this.actionButtonWidthDp);
        return this;
    }

    public float actionButtonHeightDp() {
        return actionButtonHeightDp;
    }

    public AndroidTextEditorStyle actionButtonHeightDp(float actionButtonHeightDp) {
        this.actionButtonHeightDp = positive(actionButtonHeightDp, this.actionButtonHeightDp);
        return this;
    }

    public float actionSpacingDp() {
        return actionSpacingDp;
    }

    public AndroidTextEditorStyle actionSpacingDp(float actionSpacingDp) {
        this.actionSpacingDp = nonNegative(actionSpacingDp);
        return this;
    }

    public String acceptText() {
        return acceptText;
    }

    public AndroidTextEditorStyle acceptText(String acceptText) {
        this.acceptText = textOrDefault(acceptText, "OK");
        return this;
    }

    public String cancelText() {
        return cancelText;
    }

    public AndroidTextEditorStyle cancelText(String cancelText) {
        this.cancelText = textOrDefault(cancelText, "X");
        return this;
    }

    private static float nonNegative(float value) {
        return value >= 0.0f ? value : 0.0f;
    }

    private static float positive(float value, float fallback) {
        return value > 0.0f ? value : fallback;
    }

    private static String textOrDefault(String value, String fallback) {
        return value != null && value.length() > 0 ? value : fallback;
    }
}
