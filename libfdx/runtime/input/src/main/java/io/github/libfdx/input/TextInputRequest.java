package io.github.libfdx.input;

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

    public static Builder builder() {
        return new Builder();
    }

    public String text() {
        return text;
    }

    public int selectionStart() {
        return selectionStart;
    }

    public int selectionEnd() {
        return selectionEnd;
    }

    public boolean multiline() {
        return multiline;
    }

    public boolean password() {
        return password;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public TextInputType type() {
        return type;
    }

    public boolean hasBounds() {
        return hasBounds;
    }

    public int boundsX() {
        return boundsX;
    }

    public int boundsY() {
        return boundsY;
    }

    public int boundsWidth() {
        return boundsWidth;
    }

    public int boundsHeight() {
        return boundsHeight;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

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

        public Builder text(String text) {
            this.text = text != null ? text : "";
            return this;
        }

        public Builder selection(int selectionStart, int selectionEnd) {
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            return this;
        }

        public Builder multiline(boolean multiline) {
            this.multiline = multiline;
            return this;
        }

        public Builder password(boolean password) {
            this.password = password;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder type(TextInputType type) {
            this.type = type != null ? type : TextInputType.TEXT;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.hasBounds = width > 0 && height > 0;
            this.boundsX = x;
            this.boundsY = y;
            this.boundsWidth = Math.max(0, width);
            this.boundsHeight = Math.max(0, height);
            return this;
        }

        public TextInputRequest build() {
            return new TextInputRequest(this);
        }
    }
}
