package io.github.libfdx.input;

/**
 * Represents a text input request.
 *
 * @author xpenatan
 */
public final class TextInputRequest {
    private final String text;
    private final int selectionStart;
    private final int selectionEnd;
    private final boolean multiline;
    private final boolean password;
    private final boolean readOnly;
    private final TextInputType type;
    private final boolean hasBounds;
    private final int boundsX;
    private final int boundsY;
    private final int boundsWidth;
    private final int boundsHeight;

    private TextInputRequest(Builder builder) {
        this.text = builder.text != null ? builder.text : "";
        int length = this.text.length();
        this.selectionStart = clamp(builder.selectionStart, 0, length);
        this.selectionEnd = clamp(builder.selectionEnd, 0, length);
        this.multiline = builder.multiline;
        this.password = builder.password;
        this.readOnly = builder.readOnly;
        this.type = builder.type != null ? builder.type : TextInputType.TEXT;
        this.hasBounds = builder.hasBounds && builder.boundsWidth > 0 && builder.boundsHeight > 0;
        this.boundsX = builder.boundsX;
        this.boundsY = builder.boundsY;
        this.boundsWidth = Math.max(0, builder.boundsWidth);
        this.boundsHeight = Math.max(0, builder.boundsHeight);
    }

    /**
     * Returns the builder.
     *
     * @return the created value
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the text.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * Returns the selection start.
     *
     * @return the selection start
     */
    public int selectionStart() {
        return selectionStart;
    }

    /**
     * Returns the selection end.
     *
     * @return the selection end
     */
    public int selectionEnd() {
        return selectionEnd;
    }

    /**
     * Returns the multiline.
     *
     * @return true if multiline succeeds or is active; false otherwise
     */
    public boolean multiline() {
        return multiline;
    }

    /**
     * Returns the password.
     *
     * @return true if password succeeds or is active; false otherwise
     */
    public boolean password() {
        return password;
    }

    /**
     * Returns the read only.
     *
     * @return true if read only succeeds or is active; false otherwise
     */
    public boolean readOnly() {
        return readOnly;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public TextInputType type() {
        return type;
    }

    /**
     * Returns whether this instance has bounds.
     *
     * @return true if this instance has bounds; false otherwise
     */
    public boolean hasBounds() {
        return hasBounds;
    }

    /**
     * Returns the bounds x.
     *
     * @return the bounds x
     */
    public int boundsX() {
        return boundsX;
    }

    /**
     * Returns the bounds y.
     *
     * @return the bounds y
     */
    public int boundsY() {
        return boundsY;
    }

    /**
     * Returns the bounds width.
     *
     * @return the bounds width
     */
    public int boundsWidth() {
        return boundsWidth;
    }

    /**
     * Returns the bounds height.
     *
     * @return the bounds height
     */
    public int boundsHeight() {
        return boundsHeight;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Builds value instances and related output.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private String text = "";
        private int selectionStart;
        private int selectionEnd;
        private boolean multiline;
        private boolean password;
        private boolean readOnly;
        private TextInputType type = TextInputType.TEXT;
        private boolean hasBounds;
        private int boundsX;
        private int boundsY;
        private int boundsWidth;
        private int boundsHeight;

        private Builder() {
        }

        /**
         * Sets the text and returns this builder.
         *
         * @param text the text
         * @return this builder for chaining
         */
        public Builder text(String text) {
            this.text = text != null ? text : "";
            return this;
        }

        /**
         * Sets the selection and returns this builder.
         *
         * @param selectionStart the selection start
         * @param selectionEnd the selection end
         * @return this builder for chaining
         */
        public Builder selection(int selectionStart, int selectionEnd) {
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            return this;
        }

        /**
         * Sets the multiline and returns this builder.
         *
         * @param multiline the multiline
         * @return this builder for chaining
         */
        public Builder multiline(boolean multiline) {
            this.multiline = multiline;
            return this;
        }

        /**
         * Sets the password and returns this builder.
         *
         * @param password the password
         * @return this builder for chaining
         */
        public Builder password(boolean password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the read only and returns this builder.
         *
         * @param readOnly the read only
         * @return this builder for chaining
         */
        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        /**
         * Sets the type and returns this builder.
         *
         * @param type the expected Java type
         * @return this builder for chaining
         */
        public Builder type(TextInputType type) {
            this.type = type != null ? type : TextInputType.TEXT;
            return this;
        }

        /**
         * Sets the bounds and returns this builder.
         *
         * @param x the x coordinate
         * @param y the y coordinate
         * @param width the width in pixels
         * @param height the height in pixels
         * @return this builder for chaining
         */
        public Builder bounds(int x, int y, int width, int height) {
            this.hasBounds = width > 0 && height > 0;
            this.boundsX = x;
            this.boundsY = y;
            this.boundsWidth = Math.max(0, width);
            this.boundsHeight = Math.max(0, height);
            return this;
        }

        /**
         * Returns the build.
         *
         * @return the created value
         */
        public TextInputRequest build() {
            return new TextInputRequest(this);
        }
    }
}
