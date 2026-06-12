package io.github.libfdx.backend.android;

/**
 * Stores style values for an android text editor.
 *
 * @author xpenatan
 */
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

    /**
     * Returns the panel background color.
     *
     * @return the panel background color
     */
    public int panelBackgroundColor() {
        return panelBackgroundColor;
    }

    /**
     * Sets the panel background color and returns this android text editor style.
     *
     * @param panelBackgroundColor the panel background color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle panelBackgroundColor(int panelBackgroundColor) {
        this.panelBackgroundColor = panelBackgroundColor;
        return this;
    }

    /**
     * Returns the panel border color.
     *
     * @return the panel border color
     */
    public int panelBorderColor() {
        return panelBorderColor;
    }

    /**
     * Sets the panel border color and returns this android text editor style.
     *
     * @param panelBorderColor the panel border color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle panelBorderColor(int panelBorderColor) {
        this.panelBorderColor = panelBorderColor;
        return this;
    }

    /**
     * Returns the editor background color.
     *
     * @return the editor background color
     */
    public int editorBackgroundColor() {
        return editorBackgroundColor;
    }

    /**
     * Sets the editor background color and returns this android text editor style.
     *
     * @param editorBackgroundColor the editor background color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle editorBackgroundColor(int editorBackgroundColor) {
        this.editorBackgroundColor = editorBackgroundColor;
        return this;
    }

    /**
     * Returns the editor border color.
     *
     * @return the editor border color
     */
    public int editorBorderColor() {
        return editorBorderColor;
    }

    /**
     * Sets the editor border color and returns this android text editor style.
     *
     * @param editorBorderColor the editor border color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle editorBorderColor(int editorBorderColor) {
        this.editorBorderColor = editorBorderColor;
        return this;
    }

    /**
     * Returns the editor text color.
     *
     * @return the editor text color
     */
    public int editorTextColor() {
        return editorTextColor;
    }

    /**
     * Sets the editor text color and returns this android text editor style.
     *
     * @param editorTextColor the editor text color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle editorTextColor(int editorTextColor) {
        this.editorTextColor = editorTextColor;
        return this;
    }

    /**
     * Returns the editor hint text color.
     *
     * @return the editor hint text color
     */
    public int editorHintTextColor() {
        return editorHintTextColor;
    }

    /**
     * Sets the editor hint text color and returns this android text editor style.
     *
     * @param editorHintTextColor the editor hint text color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle editorHintTextColor(int editorHintTextColor) {
        this.editorHintTextColor = editorHintTextColor;
        return this;
    }

    /**
     * Returns the accept button background color.
     *
     * @return the accept button background color
     */
    public int acceptButtonBackgroundColor() {
        return acceptButtonBackgroundColor;
    }

    /**
     * Sets the accept button background color and returns this android text editor style.
     *
     * @param acceptButtonBackgroundColor the accept button background color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle acceptButtonBackgroundColor(int acceptButtonBackgroundColor) {
        this.acceptButtonBackgroundColor = acceptButtonBackgroundColor;
        return this;
    }

    /**
     * Returns the accept button text color.
     *
     * @return the accept button text color
     */
    public int acceptButtonTextColor() {
        return acceptButtonTextColor;
    }

    /**
     * Sets the accept button text color and returns this android text editor style.
     *
     * @param acceptButtonTextColor the accept button text color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle acceptButtonTextColor(int acceptButtonTextColor) {
        this.acceptButtonTextColor = acceptButtonTextColor;
        return this;
    }

    /**
     * Returns whether this instance can cel button background color.
     *
     * @return the cancel button background color
     */
    public int cancelButtonBackgroundColor() {
        return cancelButtonBackgroundColor;
    }

    /**
     * Returns whether this instance can cel button background color.
     *
     * @param cancelButtonBackgroundColor the cancel button background color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle cancelButtonBackgroundColor(int cancelButtonBackgroundColor) {
        this.cancelButtonBackgroundColor = cancelButtonBackgroundColor;
        return this;
    }

    /**
     * Returns whether this instance can cel button border color.
     *
     * @return the cancel button border color
     */
    public int cancelButtonBorderColor() {
        return cancelButtonBorderColor;
    }

    /**
     * Returns whether this instance can cel button border color.
     *
     * @param cancelButtonBorderColor the cancel button border color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle cancelButtonBorderColor(int cancelButtonBorderColor) {
        this.cancelButtonBorderColor = cancelButtonBorderColor;
        return this;
    }

    /**
     * Returns whether this instance can cel button text color.
     *
     * @return the cancel button text color
     */
    public int cancelButtonTextColor() {
        return cancelButtonTextColor;
    }

    /**
     * Returns whether this instance can cel button text color.
     *
     * @param cancelButtonTextColor the cancel button text color
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle cancelButtonTextColor(int cancelButtonTextColor) {
        this.cancelButtonTextColor = cancelButtonTextColor;
        return this;
    }

    /**
     * Returns the editor text size sp.
     *
     * @return the editor text size sp
     */
    public float editorTextSizeSp() {
        return editorTextSizeSp;
    }

    /**
     * Sets the editor text size sp and returns this android text editor style.
     *
     * @param editorTextSizeSp the editor text size sp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle editorTextSizeSp(float editorTextSizeSp) {
        this.editorTextSizeSp = positive(editorTextSizeSp, this.editorTextSizeSp);
        return this;
    }

    /**
     * Returns the action text size sp.
     *
     * @return the action text size sp
     */
    public float actionTextSizeSp() {
        return actionTextSizeSp;
    }

    /**
     * Sets the action text size sp and returns this android text editor style.
     *
     * @param actionTextSizeSp the action text size sp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle actionTextSizeSp(float actionTextSizeSp) {
        this.actionTextSizeSp = positive(actionTextSizeSp, this.actionTextSizeSp);
        return this;
    }

    /**
     * Returns the panel corner radius dp.
     *
     * @return the panel corner radius dp
     */
    public float panelCornerRadiusDp() {
        return panelCornerRadiusDp;
    }

    /**
     * Sets the panel corner radius dp and returns this android text editor style.
     *
     * @param panelCornerRadiusDp the panel corner radius dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle panelCornerRadiusDp(float panelCornerRadiusDp) {
        this.panelCornerRadiusDp = nonNegative(panelCornerRadiusDp);
        return this;
    }

    /**
     * Returns the editor corner radius dp.
     *
     * @return the editor corner radius dp
     */
    public float editorCornerRadiusDp() {
        return editorCornerRadiusDp;
    }

    /**
     * Sets the editor corner radius dp and returns this android text editor style.
     *
     * @param editorCornerRadiusDp the editor corner radius dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle editorCornerRadiusDp(float editorCornerRadiusDp) {
        this.editorCornerRadiusDp = nonNegative(editorCornerRadiusDp);
        return this;
    }

    /**
     * Returns the action corner radius dp.
     *
     * @return the action corner radius dp
     */
    public float actionCornerRadiusDp() {
        return actionCornerRadiusDp;
    }

    /**
     * Sets the action corner radius dp and returns this android text editor style.
     *
     * @param actionCornerRadiusDp the action corner radius dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle actionCornerRadiusDp(float actionCornerRadiusDp) {
        this.actionCornerRadiusDp = nonNegative(actionCornerRadiusDp);
        return this;
    }

    /**
     * Returns the panel padding horizontal dp.
     *
     * @return the panel padding horizontal dp
     */
    public float panelPaddingHorizontalDp() {
        return panelPaddingHorizontalDp;
    }

    /**
     * Sets the panel padding horizontal dp and returns this android text editor style.
     *
     * @param panelPaddingHorizontalDp the panel padding horizontal dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle panelPaddingHorizontalDp(float panelPaddingHorizontalDp) {
        this.panelPaddingHorizontalDp = nonNegative(panelPaddingHorizontalDp);
        return this;
    }

    /**
     * Returns the panel padding vertical dp.
     *
     * @return the panel padding vertical dp
     */
    public float panelPaddingVerticalDp() {
        return panelPaddingVerticalDp;
    }

    /**
     * Sets the panel padding vertical dp and returns this android text editor style.
     *
     * @param panelPaddingVerticalDp the panel padding vertical dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle panelPaddingVerticalDp(float panelPaddingVerticalDp) {
        this.panelPaddingVerticalDp = nonNegative(panelPaddingVerticalDp);
        return this;
    }

    /**
     * Returns the editor padding horizontal dp.
     *
     * @return the editor padding horizontal dp
     */
    public float editorPaddingHorizontalDp() {
        return editorPaddingHorizontalDp;
    }

    /**
     * Sets the editor padding horizontal dp and returns this android text editor style.
     *
     * @param editorPaddingHorizontalDp the editor padding horizontal dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle editorPaddingHorizontalDp(float editorPaddingHorizontalDp) {
        this.editorPaddingHorizontalDp = nonNegative(editorPaddingHorizontalDp);
        return this;
    }

    /**
     * Returns the editor padding vertical dp.
     *
     * @return the editor padding vertical dp
     */
    public float editorPaddingVerticalDp() {
        return editorPaddingVerticalDp;
    }

    /**
     * Sets the editor padding vertical dp and returns this android text editor style.
     *
     * @param editorPaddingVerticalDp the editor padding vertical dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle editorPaddingVerticalDp(float editorPaddingVerticalDp) {
        this.editorPaddingVerticalDp = nonNegative(editorPaddingVerticalDp);
        return this;
    }

    /**
     * Returns the panel margin dp.
     *
     * @return the panel margin dp
     */
    public float panelMarginDp() {
        return panelMarginDp;
    }

    /**
     * Sets the panel margin dp and returns this android text editor style.
     *
     * @param panelMarginDp the panel margin dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle panelMarginDp(float panelMarginDp) {
        this.panelMarginDp = nonNegative(panelMarginDp);
        return this;
    }

    /**
     * Returns the action button width dp.
     *
     * @return the action button width dp
     */
    public float actionButtonWidthDp() {
        return actionButtonWidthDp;
    }

    /**
     * Sets the action button width dp and returns this android text editor style.
     *
     * @param actionButtonWidthDp the action button width dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle actionButtonWidthDp(float actionButtonWidthDp) {
        this.actionButtonWidthDp = positive(actionButtonWidthDp, this.actionButtonWidthDp);
        return this;
    }

    /**
     * Returns the action button height dp.
     *
     * @return the action button height dp
     */
    public float actionButtonHeightDp() {
        return actionButtonHeightDp;
    }

    /**
     * Sets the action button height dp and returns this android text editor style.
     *
     * @param actionButtonHeightDp the action button height dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle actionButtonHeightDp(float actionButtonHeightDp) {
        this.actionButtonHeightDp = positive(actionButtonHeightDp, this.actionButtonHeightDp);
        return this;
    }

    /**
     * Returns the action spacing dp.
     *
     * @return the action spacing dp
     */
    public float actionSpacingDp() {
        return actionSpacingDp;
    }

    /**
     * Sets the action spacing dp and returns this android text editor style.
     *
     * @param actionSpacingDp the action spacing dp
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle actionSpacingDp(float actionSpacingDp) {
        this.actionSpacingDp = nonNegative(actionSpacingDp);
        return this;
    }

    /**
     * Returns the accept text.
     *
     * @return the accept text
     */
    public String acceptText() {
        return acceptText;
    }

    /**
     * Sets the accept text and returns this android text editor style.
     *
     * @param acceptText the accept text
     * @return this android text editor style for chaining
     */
    public AndroidTextEditorStyle acceptText(String acceptText) {
        this.acceptText = textOrDefault(acceptText, "OK");
        return this;
    }

    /**
     * Returns whether this instance can cel text.
     *
     * @return the cancel text
     */
    public String cancelText() {
        return cancelText;
    }

    /**
     * Returns whether this instance can cel text.
     *
     * @param cancelText the cancel text
     * @return this android text editor style for chaining
     */
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
