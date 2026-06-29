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
        int length = value().length();
        this.cursor = Math.max(0, Math.min(length, cursor));
        this.selectionStart = this.cursor;
        this.selectionEnd = this.cursor;
    }

    void select(int anchor, int cursor) {
        int length = value().length();
        this.selectionStart = Math.max(0, Math.min(length, anchor));
        this.selectionEnd = Math.max(0, Math.min(length, cursor));
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
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (!multiline && (character == '\n' || character == '\r')) {
                continue;
            }
            if (character == '\r') {
                continue;
            }
            next.insert(at + inserted, character);
            if (accepts(next.toString())) {
                inserted++;
            } else {
                next.deleteCharAt(at + inserted);
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
        int at = Math.max(0, Math.min(cursor, value.length()));
        state.set(value.substring(0, at - 1) + value.substring(at));
        cursor(at - 1);
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
        int at = Math.max(0, Math.min(cursor, value.length()));
        state.set(value.substring(0, at) + value.substring(at + 1));
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
        int length = value().length();
        int next = Math.max(0, Math.min(length, cursor));
        if (extendSelection) {
            int anchor = hasSelection() ? selectionStart : previous;
            select(anchor, next);
        } else {
            cursor(next);
        }
    }

    private void clampSelection() {
        int length = value().length();
        cursor = Math.max(0, Math.min(length, cursor));
        selectionStart = Math.max(0, Math.min(length, selectionStart));
        selectionEnd = Math.max(0, Math.min(length, selectionEnd));
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
