package io.github.libfdx.ui;

/**
 * Represents an ui text field model.
 *
 * @author xpenatan
 */
final class UiTextFieldModel {
    private UiState<String> state;
    private int cursor;
    private int selectionStart;
    private int selectionEnd;
    private boolean password;
    private boolean readOnly;
    private boolean valid = true;
    private boolean multiline;
    private UiTextAreaOptions textAreaOptions = UiTextAreaOptions.defaults();
    private UiScrollState scrollState;
    private UiTextInputFilter inputFilter = UiTextInputFilter.STRING;
    private Runnable submitAction;

    UiTextFieldModel(UiState<String> state) {
        this.state = state;
        String value = value();
        this.cursor = value.length();
        this.selectionStart = cursor;
        this.selectionEnd = cursor;
    }

    UiState<String> state() {
        return state;
    }

    void state(UiState<String> state) {
        this.state = state;
        clampSelection();
    }

    String value() {
        String value = state != null ? state.get() : "";
        return value != null ? value : "";
    }

    int cursor() {
        return cursor;
    }

    void cursor(int cursor) {
        String value = value();
        this.cursor = codePointBoundary(value, cursor);
        this.selectionStart = this.cursor;
        this.selectionEnd = this.cursor;
    }

    void select(int anchor, int cursor) {
        String value = value();
        this.selectionStart = codePointBoundary(value, anchor);
        this.selectionEnd = codePointBoundary(value, cursor);
        this.cursor = this.selectionEnd;
    }

    void selectAll() {
        this.selectionStart = 0;
        this.selectionEnd = value().length();
        this.cursor = this.selectionEnd;
    }

    boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    int selectionMin() {
        return Math.min(selectionStart, selectionEnd);
    }

    int selectionMax() {
        return Math.max(selectionStart, selectionEnd);
    }

    String selectedText() {
        if (!hasSelection()) {
            return "";
        }
        String value = value();
        return value.substring(selectionMin(), selectionMax());
    }

    int selectionStart() {
        return selectionStart;
    }

    int selectionEnd() {
        return selectionEnd;
    }

    boolean password() {
        return password;
    }

    void password(boolean password) {
        this.password = password;
    }

    boolean readOnly() {
        return readOnly;
    }

    void readOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    boolean valid() {
        return valid;
    }

    void valid(boolean valid) {
        this.valid = valid;
    }

    UiTextInputFilter inputFilter() {
        return inputFilter;
    }

    void inputFilter(UiTextInputFilter inputFilter) {
        this.inputFilter = inputFilter != null ? inputFilter : UiTextInputFilter.STRING;
    }

    void submitAction(Runnable submitAction) {
        this.submitAction = submitAction;
    }

    boolean submit() {
        if (submitAction == null) {
            return false;
        }
        submitAction.run();
        return true;
    }

    boolean multiline() {
        return multiline;
    }

    void multiline(boolean multiline) {
        this.multiline = multiline;
    }

    UiTextAreaOptions textAreaOptions() {
        return textAreaOptions;
    }

    void textAreaOptions(UiTextAreaOptions textAreaOptions) {
        this.textAreaOptions = textAreaOptions != null ? textAreaOptions : UiTextAreaOptions.defaults();
    }

    UiScrollState scrollState() {
        return scrollState;
    }

    void scrollState(UiScrollState scrollState) {
        this.scrollState = scrollState;
    }

    void insert(String text) {
        if (readOnly || state == null || text == null || text.length() == 0) {
            return;
        }
        String value = value();
        int at = selectionMin();
        int deleteTo = selectionMax();
        StringBuilder next = new StringBuilder(value);
        if (deleteTo > at) {
            next.delete(at, deleteTo);
        }
        int inserted = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            int codePointLength = Character.charCount(codePoint);
            i += codePointLength;
            if (!multiline && (codePoint == '\n' || codePoint == '\r')) {
                continue;
            }
            if (codePoint == '\r') {
                continue;
            }
            String characters = new String(Character.toChars(codePoint));
            next.insert(at + inserted, characters);
            if (accepts(next.toString())) {
                inserted += codePointLength;
            } else {
                next.delete(at + inserted, at + inserted + codePointLength);
            }
        }
        if (inserted > 0 || deleteTo > at) {
            state.set(next.toString());
            cursor(at + inserted);
        }
    }

    void backspace() {
        if (readOnly || state == null) {
            return;
        }
        String value = value();
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        if (cursor <= 0 || value.length() == 0) {
            return;
        }
        int at = codePointBoundary(value, cursor);
        int previous = value.offsetByCodePoints(at, -1);
        state.set(value.substring(0, previous) + value.substring(at));
        cursor(previous);
    }

    void delete() {
        if (readOnly || state == null) {
            return;
        }
        String value = value();
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        if (cursor >= value.length()) {
            return;
        }
        int at = codePointBoundary(value, cursor);
        int next = value.offsetByCodePoints(at, 1);
        state.set(value.substring(0, at) + value.substring(next));
        cursor(at);
    }

    void deleteSelection() {
        if (readOnly || state == null || !hasSelection()) {
            return;
        }
        String value = value();
        int start = selectionMin();
        int end = selectionMax();
        state.set(value.substring(0, start) + value.substring(end));
        cursor(start);
    }

    void moveCursor(int cursor, boolean extendSelection) {
        int previous = this.cursor;
        int next = codePointBoundary(value(), cursor);
        if (extendSelection) {
            int anchor = hasSelection() ? selectionStart : previous;
            select(anchor, next);
        } else {
            cursor(next);
        }
    }

    int previousCursor() {
        String value = value();
        int at = codePointBoundary(value, cursor);
        return at > 0 ? value.offsetByCodePoints(at, -1) : 0;
    }

    int nextCursor() {
        String value = value();
        int at = codePointBoundary(value, cursor);
        return at < value.length() ? value.offsetByCodePoints(at, 1) : value.length();
    }

    private void clampSelection() {
        String value = value();
        cursor = codePointBoundary(value, cursor);
        selectionStart = codePointBoundary(value, selectionStart);
        selectionEnd = codePointBoundary(value, selectionEnd);
    }

    private int codePointBoundary(String text, int offset) {
        int length = text != null ? text.length() : 0;
        int value = Math.max(0, Math.min(length, offset));
        if (value > 0 && value < length
                && Character.isLowSurrogate(text.charAt(value))
                && Character.isHighSurrogate(text.charAt(value - 1))) {
            value--;
        }
        return value;
    }

    private boolean accepts(String value) {
        if (inputFilter == UiTextInputFilter.INTEGER) {
            return acceptsInteger(value);
        }
        if (inputFilter == UiTextInputFilter.FLOAT) {
            return acceptsFloat(value);
        }
        return true;
    }

    private boolean acceptsInteger(String value) {
        if (value.length() == 0 || "-".equals(value)) {
            return true;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) {
            return true;
        }
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean acceptsFloat(String value) {
        if (value.length() == 0 || "-".equals(value) || ".".equals(value) || "-.".equals(value)) {
            return true;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        boolean decimalSeen = false;
        boolean digitSeen = false;
        for (int i = start; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '.') {
                if (decimalSeen) {
                    return false;
                }
                decimalSeen = true;
            } else if (Character.isDigit(character)) {
                digitSeen = true;
            } else {
                return false;
            }
        }
        return digitSeen || decimalSeen;
    }
}
