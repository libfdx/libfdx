package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiTextAreaOptions;
import io.github.libfdx.ui.UiTextInputFilter;

/** Demonstrates text fields, Unicode input, and text areas. */
public final class UiKitTextFieldsSection implements UiContent {
    public static final String EMOJI_GRIN = new String(Character.toChars(0x1F600));
    public static final String EMOJI_BEAM = new String(Character.toChars(0x1F601));
    public static final String EMOJI_JOY = new String(Character.toChars(0x1F602));

    private final UiState<String> name;
    public final UiState<String> emojiInput = Ui.state("Emoji " + EMOJI_GRIN);
    public final UiState<String> intInput = Ui.state("42");
    public final UiState<String> floatInput = Ui.state("0.75");
    public final UiState<String> messageInput = Ui.state("Line 1 message\nLine 2 with more text\nLine 3 keeps the body scrollable\n"
            + "Line 4 still inside the area\nLine 5 validates clipping\nLine 6 remains reachable");
    public final UiState<String> growingMessageInput = Ui.state("Short message\nsecond line");
    public final UiBooleanState messageReadOnly = Ui.state(false);
    private int textFieldSubmitCount;

    public UiKitTextFieldsSection(UiState<String> name) {
        this.name = name;
    }

    @Override
    public void build(UiScope content) {
        content.text("Text inputs", Ui.modifier().style("title"));
        buildSingleLineInputs(content);
        buildEmojiInput(content);
        buildTextAreas(content);
        buildInputSummary(content);
    }

    private void buildSingleLineInputs(UiScope content) {
        content.text("Inputs", Ui.modifier().style("muted"));
        fieldRow(content, "String", name, UiTextInputFilter.STRING);
        fieldRow(content, "Integer", intInput, UiTextInputFilter.INTEGER);
        fieldRow(content, "Float", floatInput, UiTextInputFilter.FLOAT);
    }

    private void buildEmojiInput(UiScope content) {
        content.text("Unicode " + EMOJI_GRIN + "  " + EMOJI_BEAM + "  " + EMOJI_JOY,
                Ui.modifier().style("section"));
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text("Emoji", Ui.modifier().width(86.0f).style("muted"));
            row.textField(Ui.modifier().fillWidth().weight(1.0f)
                            .semanticLabel("Emoji field")
                            .validationId(UiKitValidationIds.EMOJI_FIELD),
                    emojiInput, UiTextInputFilter.STRING);
        });
    }

    private void buildTextAreas(UiScope content) {
        content.text("Message area", Ui.modifier().style("muted"));
        content.textArea(Ui.modifier().fillWidth().height(92.0f).semanticLabel("Message area").style("text-area"),
                messageInput, UiTextAreaOptions.defaults().readOnly(messageReadOnly.get()).minHeight(92.0f));
        content.text("Auto-grow area", Ui.modifier().style("muted"));
        content.textArea(Ui.modifier().fillWidth().semanticLabel("Auto grow text area").style("text-area"),
                growingMessageInput, UiTextAreaOptions.defaults().autoGrow(true).minHeight(52.0f).maxHeight(118.0f));
    }

    private void buildInputSummary(UiScope content) {
        content.panel(Ui.modifier().fillWidth().padding(10.0f).gap(4.0f).style("tile"), panel -> {
            panel.text("name " + name.get(), Ui.modifier().style("small"));
            panel.text("int " + intInput.get() + "  float " + floatInput.get(), Ui.modifier().style("small"));
            panel.text("clipboard-ready Unicode " + emojiInput.get(), Ui.modifier().style("small"));
            panel.text("message lines " + messageLineCount(), Ui.modifier().style("small"));
        });
    }

    private void fieldRow(UiScope content, String label, UiState<String> state, UiTextInputFilter inputFilter) {
        content.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(86.0f).style("muted"));
            row.textField(Ui.modifier().fillWidth().weight(1.0f).semanticLabel(label + " field"),
                    state, inputFilter, "String".equals(label) ? () -> textFieldSubmitCount++ : null);
        });
    }

    private int messageLineCount() {
        String value = messageInput != null ? messageInput.get() : "";
        int count = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    public int textFieldSubmitCount() {
        return textFieldSubmitCount;
    }

}
