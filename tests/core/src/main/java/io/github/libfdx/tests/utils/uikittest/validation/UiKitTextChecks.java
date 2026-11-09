package io.github.libfdx.tests.utils.uikittest.validation;

import static io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTextFieldsSection.EMOJI_BEAM;
import static io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTextFieldsSection.EMOJI_GRIN;
import static io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTextFieldsSection.EMOJI_JOY;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_BUTTONS;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_CHECKBOXES;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_SCROLLVIEW;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_TEXT_FIELDS;
import static io.github.libfdx.tests.utils.uikittest.validation.UiKitTestInput.clamp;
import static io.github.libfdx.tests.utils.uikittest.validation.UiKitTestInput.find;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.TextInputRequest;
import io.github.libfdx.input.TextInputType;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;

/** Checks text input, clipboard, selection, and scroll gestures. */
final class UiKitTextChecks {
    private final UiKitShowcase showcase;
    private final UiRoot root;
    private final DefaultInput input;
    private final Display display;
    private final Logger logger;
    private final RecordingTextInputController textInputController;
    private final UiKitTestInput interaction;

    private boolean textInputSessionChecked;
    private boolean textSelectionChecked;
    private boolean textAreaChecked;
    private boolean textInputTouchDragChecked;
    private boolean scrollBodyDragChecked;
    private boolean scrollChildDragChecked;
    private boolean emojiChecked;

    UiKitTextChecks(UiKitShowcase showcase, UiRoot root, DefaultInput input, Display display,
            Logger logger, RecordingTextInputController textInputController, UiKitTestInput interaction) {
        this.showcase = showcase;
        this.root = root;
        this.input = input;
        this.display = display;
        this.logger = logger;
        this.textInputController = textInputController;
        this.interaction = interaction;
    }

    void verifyInputValues() {
        if (!showcase.name().endsWith("!")) {
            throw new FdxException("UiKitTest text input dispatch did not update the text field");
        }
        if (!"427".equals(showcase.textFields().intInput.get())) {
            throw new FdxException("UiKitTest integer text field accepted non-integer input: " + showcase.textFields().intInput.get());
        }
        if (!"0.755".equals(showcase.textFields().floatInput.get())) {
            throw new FdxException("UiKitTest float text field accepted non-float input: " + showcase.textFields().floatInput.get());
        }
    }

    void verifyCoverage() {
        if (!textInputSessionChecked) {
            throw new FdxException("UiKitTest did not validate text input session requests");
        }
        if (!textSelectionChecked) {
            throw new FdxException("UiKitTest did not validate text selection and copy/paste");
        }
        if (!textAreaChecked) {
            throw new FdxException("UiKitTest did not validate text area input, scroll, and auto-grow bounds");
        }
        if (!textInputTouchDragChecked) {
            throw new FdxException("UiKitTest did not validate text input touch drag scrolling");
        }
        if (!scrollBodyDragChecked) {
            throw new FdxException("UiKitTest did not validate scroll view body drag scrolling");
        }
        if (!scrollChildDragChecked) {
            throw new FdxException("UiKitTest did not validate scroll view child drag scrolling");
        }
        if (!emojiChecked) {
            throw new FdxException("UiKitTest did not validate emoji editing and clipboard round trips");
        }
    }

    void validateEmojiAndClipboard() {
        String initial = "Emoji " + EMOJI_GRIN;
        showcase.textFields().emojiInput.set(initial);
        interaction.settleValidationLayout();
        UiNode field = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Emoji field");
        if (field == null) {
            throw new FdxException("UiKitTest could not find the emoji text field");
        }

        clickTextInputAt(field, 1.0f, 0.5f);
        interaction.pressKey(Key.BACKSPACE);
        if (!"Emoji ".equals(showcase.textFields().emojiInput.get())) {
            throw new FdxException("UiKitTest backspace split or failed to remove one emoji code point: "
                    + showcase.textFields().emojiInput.get());
        }
        input.dispatchTextInput(EMOJI_GRIN + EMOJI_BEAM);
        String expected = "Emoji " + EMOJI_GRIN + EMOJI_BEAM;
        if (!expected.equals(showcase.textFields().emojiInput.get())) {
            throw new FdxException("UiKitTest text input did not preserve emoji code points: " + showcase.textFields().emojiInput.get());
        }

        interaction.pressShortcut(Key.A);
        interaction.pressShortcut(Key.C);
        String copied = input.clipboard().getText();
        if (!expected.equals(copied)) {
            throw new FdxException("UiKitTest clipboard did not preserve emoji text: expected='"
                    + expected + "', copied='" + copied + "'");
        }
        input.clipboard().setText("Pasted " + EMOJI_JOY);
        interaction.pressShortcut(Key.V);
        if (!("Pasted " + EMOJI_JOY).equals(showcase.textFields().emojiInput.get())) {
            throw new FdxException("UiKitTest clipboard paste did not replace the selected emoji text: "
                    + showcase.textFields().emojiInput.get());
        }
        logger.info("UiKitTest verified emoji rendering/editing and clipboard round trip");
        emojiChecked = true;
    }

    void validateTextSelectionAndCopyPaste() {
        UiNode stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "String field");
        UiNode intField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Integer field");
        UiNode floatField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Float field");
        if (stringField == null || intField == null || floatField == null) {
            throw new FdxException("UiKitTest could not find text fields for editing validation");
        }
        validateTextInputSession(stringField, intField, floatField);
        stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "String field");
        intField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Integer field");
        floatField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Float field");
        assertMouseDoubleClickCopies(stringField, showcase.name().indexOf("Player") + 2, "Player",
                "single-line field word");
        clickTextInputAt(stringField, 1.0f, 0.5f);
        input.dispatchTextInput("!");
        if (!showcase.name().endsWith("!")) {
            throw new FdxException("UiKitTest text field hit selection did not place the cursor at the end: "
                    + showcase.name());
        }
        interaction.settleValidationLayout();
        stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "String field");
        dragTextInput(stringField, 0.0f, 1.0f);
        interaction.pressShortcut(Key.C);
        clickTextInputAt(stringField, 1.0f, 0.5f);
        interaction.pressShortcut(Key.V);
        if (!showcase.name().contains("Player!Player!")) {
            throw new FdxException("UiKitTest text field copy/paste did not duplicate the selected text: "
                    + showcase.name());
        }
        clickTextInputAt(intField, 1.0f, 0.5f);
        input.dispatchTextInput("abc7");
        clickTextInputAt(floatField, 1.0f, 0.5f);
        input.dispatchTextInput("x.5q");
        stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "String field");
        clickTextInputAt(stringField, 1.0f, 0.5f);
        int submitsBeforeEnter = showcase.textFields().textFieldSubmitCount();
        interaction.pressKey(Key.ENTER);
        if (showcase.textFields().textFieldSubmitCount() != submitsBeforeEnter + 1) {
            throw new FdxException("UiKitTest single-line Enter did not invoke its submit action");
        }
        textSelectionChecked = true;
    }

    void validateTextArea() {
        UiNode messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        UiNode growArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Auto grow text area");
        if (messageArea == null || growArea == null) {
            throw new FdxException("UiKitTest could not find text area nodes");
        }
        if (messageArea.scrollState() == null) {
            throw new FdxException("UiKitTest message text area did not expose internal scroll state");
        }
        float beforeScroll = messageArea.scrollState().y();
        UiRect bounds = messageArea.bounds();
        input.dispatchScrolled(root.displayX(bounds.x() + bounds.width() * 0.5f),
                root.displayY(bounds.y() + bounds.height() * 0.5f), 0.0f, 3.0f);
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        if (messageArea.scrollState().y() <= beforeScroll) {
            throw new FdxException("UiKitTest text area wheel scroll did not update internal scroll state: before="
                    + beforeScroll + ", after=" + messageArea.scrollState().y());
        }
        messageArea.scrollState().scrollTo(0.0f, 0.0f);
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        float beforeTouchDrag = messageArea.scrollState().y();
        if (textInputController != null) {
            textInputController.reset();
        }
        dragTextAreaTouch(messageArea, 0.5f, 0.85f, 0.5f, 0.25f);
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        if (messageArea.scrollState().y() <= beforeTouchDrag + 20.0f) {
            throw new FdxException("UiKitTest text area touch drag did not scroll the text area: before="
                    + beforeTouchDrag + ", after=" + messageArea.scrollState().y());
        }
        if (textInputController != null) {
            if (textInputController.visible() || textInputController.showCount() > 0) {
                throw new FdxException("UiKitTest text area touch drag opened platform text input");
            }
        }
        clickTextInputAt(messageArea, 1.0f, 1.0f);
        if (textInputController != null) {
            textInputController.assertVisible(TextInputType.TEXT, true, "Message area");
            TextInputRequest request = textInputController.lastRequest();
            UiRect areaBounds = messageArea.bounds();
            int areaTop = root.displayY(areaBounds.y());
            int areaMid = root.displayY(areaBounds.y() + areaBounds.height() * 0.5f);
            int areaBottom = root.displayY(areaBounds.y() + areaBounds.height());
            if (request.boundsY() <= areaMid || request.boundsY() >= areaBottom) {
                throw new FdxException("UiKitTest text area platform bounds did not track the lower caret line: "
                        + request.boundsY() + " in " + areaTop + ".." + areaBottom);
            }
            if (request.boundsHeight() >= Math.max(1, areaBottom - areaTop)) {
                throw new FdxException("UiKitTest text area platform bounds used the whole widget instead of a line: "
                        + request.boundsHeight() + " >= " + (areaBottom - areaTop));
            }
        }
        input.dispatchTextInput("\nvalidated text area");
        if (showcase.textFields().messageInput.get().indexOf("validated text area") < 0) {
            throw new FdxException("UiKitTest text area did not accept multiline text input: " + showcase.textFields().messageInput.get());
        }
        String wordBoundaryText = "alpha você !!!  beta";
        showcase.textFields().messageInput.set(wordBoundaryText);
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        messageArea.scrollState().scrollTo(0.0f, 0.0f);
        assertMouseDoubleClickCopies(messageArea, wordBoundaryText.indexOf("alpha") + 2, "alpha",
                "ordinary word");
        assertMouseDoubleClickCopies(messageArea, wordBoundaryText.indexOf("você") + 2, "você",
                "Unicode word");
        assertMouseDoubleClickCopies(messageArea, wordBoundaryText.indexOf("!!!") + 1, "!",
                "punctuation");
        assertMouseDoubleClickCopies(messageArea, wordBoundaryText.indexOf("  "), "  ",
                "whitespace run");
        assertTouchDoubleTapCopies(messageArea, wordBoundaryText.indexOf("beta") + 2, "beta",
                "touch word");
        clickTextInputAt(messageArea, 1.0f, 1.0f);
        String verticalSelectionText = "line-00 selectable value\n"
                + "line-01 selectable value\n"
                + "line-02 selectable value\n"
                + "line-03 selectable value\n"
                + "line-04 selectable value\n"
                + "line-05 selectable value\n"
                + "line-06 selectable value\n"
                + "line-07 selectable value\n"
                + "line-08 selectable value\n"
                + "line-09 selectable value\n"
                + "line-10 selectable value\n"
                + "line-11 selectable value";
        showcase.textFields().messageInput.set(verticalSelectionText);
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        messageArea.scrollState().scrollTo(0.0f, 0.0f);
        dragTextAreaScrollGutter(messageArea, 8.0f, 0.2f, 0.8f);
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        if (messageArea.scrollState().y() <= 0.0f) {
            throw new FdxException("UiKitTest text area scrollbar gutter did not scroll");
        }
        String scrollMarker = "scrollbar-marker";
        input.dispatchTextInput(scrollMarker);
        String afterScrollbarDrag = showcase.textFields().messageInput.get();
        if (afterScrollbarDrag.length() != verticalSelectionText.length() + scrollMarker.length()
                || !verticalSelectionText.equals(afterScrollbarDrag.replace(scrollMarker, ""))) {
            throw new FdxException("UiKitTest near-scrollbar drag became a text selection: "
                    + afterScrollbarDrag);
        }
        showcase.textFields().messageInput.set(verticalSelectionText);
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        float verticalScroll = 5.0f * 20.0f;
        messageArea.scrollState().scrollTo(0.0f, verticalScroll);
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        int scrolledWordOffset = verticalSelectionText.indexOf("line-07 selectable value")
                + "line-07 ".length() + 3;
        assertMouseDoubleClickCopies(messageArea, scrolledWordOffset, "selectable",
                "vertically scrolled word");
        dragTextAreaMouseLine(messageArea, 7, verticalScroll, 20.0f, 10.0f, 8.0f);
        input.dispatchTextInput("replacement-line");
        String expectedVerticalSelection = verticalSelectionText.replace(
                "line-07 selectable value", "replacement-line");
        if (!expectedVerticalSelection.equals(showcase.textFields().messageInput.get())) {
            throw new FdxException("UiKitTest text area pointer selection ignored vertical scroll: "
                    + showcase.textFields().messageInput.get());
        }
        String horizontalText = "horizontal caret validation " + "0123456789".repeat(80);
        showcase.textFields().messageInput.set(horizontalText);
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        messageArea.scrollState().scrollTo(0.0f, 0.0f);
        clickTextInputAt(messageArea, 0.0f, 0.5f);
        interaction.pressKey(Key.HOME);
        for (int i = 0; i < horizontalText.length(); i++) {
            input.dispatchKeyDown(Key.RIGHT);
            input.dispatchKeyUp(Key.RIGHT);
        }
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        if (messageArea.scrollState().x() <= 0.0f) {
            throw new FdxException("UiKitTest text area did not horizontally scroll to keep the right-moving "
                    + "caret visible");
        }
        clickTextInputAt(messageArea, 1.0f, 0.5f);
        input.dispatchTextInput("!");
        String horizontallyEdited = showcase.textFields().messageInput.get();
        int horizontalMarker = horizontallyEdited.indexOf('!');
        if (horizontalMarker < horizontalText.length() - 4
                || !horizontalText.equals(horizontallyEdited.replace("!", ""))) {
            throw new FdxException("UiKitTest text area pointer hit testing ignored horizontal scroll: "
                    + horizontallyEdited);
        }
        showcase.textFields().growingMessageInput.set("Auto grow line 1\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7\nline 8");
        interaction.settleValidationLayout();
        growArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Auto grow text area");
        if (growArea.bounds().height() <= 52.0f || growArea.bounds().height() > 118.5f) {
            throw new FdxException("UiKitTest auto-grow text area did not respect min/max height: "
                    + growArea.bounds().height());
        }
        showcase.textFields().messageReadOnly.set(true);
        interaction.showValidationSection(SECTION_BUTTONS);
        interaction.settleValidationLayout();
        interaction.showValidationSection(SECTION_TEXT_FIELDS);
        interaction.settleValidationLayout();
        messageArea = find(root.rootNode(), UiNodeType.TEXT_AREA, "Message area");
        String readOnlyValue = showcase.textFields().messageInput.get();
        if (textInputController != null) {
            textInputController.reset();
        }
        clickTextInputAt(messageArea, 0.5f, 0.5f);
        input.dispatchTextInput("must-not-edit");
        interaction.pressKey(Key.BACKSPACE);
        interaction.pressKey(Key.DELETE);
        interaction.pressKey(Key.ENTER);
        if (!readOnlyValue.equals(showcase.textFields().messageInput.get())) {
            throw new FdxException("UiKitTest read-only text area accepted an edit: " + showcase.textFields().messageInput.get());
        }
        if (textInputController != null
                && (textInputController.visible() || textInputController.showCount() > 0)) {
            throw new FdxException("UiKitTest read-only text area opened a platform editor");
        }
        showcase.textFields().messageReadOnly.set(false);
        interaction.settleValidationLayout();
        textAreaChecked = true;
    }

    void validateTextInputTouchDrag() {
        if (textInputController == null) {
            textInputTouchDragChecked = true;
            return;
        }
        showcase.scrollView().mediumScroll.scrollTo(0.0f, 0.0f);
        interaction.settleValidationLayout();
        UiNode scroll = find(root.rootNode(), UiNodeType.SCROLL, "Medium scroll body");
        UiNode stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Scroll text field");
        if (scroll == null || stringField == null) {
            throw new FdxException("UiKitTest could not find text input touch drag targets");
        }
        if (!showcase.scrollView().mediumScroll.canScrollY()) {
            throw new FdxException("UiKitTest text input scroll body was not scrollable in validation mode: maxY="
                    + showcase.scrollView().mediumScroll.maxY());
        }
        textInputController.reset();
        UiRect scrollBounds = scroll.bounds();
        UiRect fieldBounds = stringField.bounds();
        int x = root.displayX(fieldBounds.x() + fieldBounds.width() * 0.5f);
        int downY = root.displayY(fieldBounds.y() + fieldBounds.height() * 0.5f);
        int moveY = root.displayY(scrollBounds.y() - 96.0f);
        input.dispatchTouchDown(0, x, downY, 1.0f);
        input.dispatchTouchMoved(0, x, moveY, 1.0f);
        input.dispatchTouchUp(0, x, moveY, 1.0f);
        interaction.settleValidationLayout();
        if (textInputController.visible() || textInputController.showCount() > 0) {
            throw new FdxException("UiKitTest text field touch drag opened platform text input");
        }
        if (showcase.scrollView().mediumScroll.y() <= 20.0f) {
            throw new FdxException("UiKitTest text field touch drag did not scroll the medium scroll body: "
                    + showcase.scrollView().mediumScroll.y());
        }
        showcase.scrollView().mediumScroll.scrollTo(0.0f, 0.0f);
        interaction.showValidationSection(SECTION_BUTTONS);
        interaction.settleValidationLayout();
        interaction.showValidationSection(SECTION_SCROLLVIEW);
        interaction.settleValidationLayout();
        scroll = find(root.rootNode(), UiNodeType.SCROLL, "Medium scroll body");
        stringField = find(root.rootNode(), UiNodeType.TEXT_FIELD, "Scroll text field");
        if (scroll == null || stringField == null) {
            throw new FdxException("UiKitTest could not find scroll text field after touch drag reset");
        }
        UiRect resetScrollBounds = scroll.bounds();
        UiRect resetFieldBounds = stringField.bounds();
        if (resetFieldBounds.y() < resetScrollBounds.y() || resetFieldBounds.bottom() > resetScrollBounds.bottom()) {
            throw new FdxException("UiKitTest scroll text field was not visible after text touch drag reset: field="
                    + resetFieldBounds.y() + ".." + resetFieldBounds.bottom() + ", scroll="
                    + resetScrollBounds.y() + ".." + resetScrollBounds.bottom());
        }
        textInputController.reset();
        touchTextInputAt(stringField, 0.5f, 0.5f);
        textInputController.assertVisible(TextInputType.TEXT, false, "Scroll text field");
        textInputTouchDragChecked = true;
    }

    void validateScrollBodyDrag() {
        showcase.scrollView().mediumScroll.scrollTo(0.0f, 0.0f);
        interaction.settleValidationLayout();
        UiNode scroll = find(root.rootNode(), UiNodeType.SCROLL, "Medium scroll body");
        if (scroll == null) {
            throw new FdxException("UiKitTest could not find the medium scroll body");
        }
        UiRect bounds = scroll.bounds();
        int x = root.displayX(bounds.x() + 12.0f);
        int downY = root.displayY(bounds.y() + 120.0f);
        int moveY = root.displayY(bounds.y() + 36.0f);
        input.dispatchPointerMoved(x, downY);
        input.dispatchPointerDown(MouseButton.LEFT, x, downY);
        input.dispatchPointerMoved(x, moveY);
        input.dispatchPointerUp(MouseButton.LEFT, x, moveY);
        interaction.settleValidationLayout();
        if (showcase.scrollView().mediumScroll.y() <= 20.0f) {
            throw new FdxException("UiKitTest scroll view body drag did not update scroll y: " + showcase.scrollView().mediumScroll.y());
        }
        scrollBodyDragChecked = true;
    }

    void validateScrollChildDrag() {
        showcase.navScroll().scrollTo(0.0f, 0.0f);
        showcase.activeSection().set(SECTION_BUTTONS);
        interaction.settleValidationLayout();
        UiNode nav = find(root.rootNode(), UiNodeType.SCROLL, "Sections nav");
        UiNode checkboxes = find(root.rootNode(), UiNodeType.BUTTON, "Checkboxes");
        if (nav == null || checkboxes == null) {
            throw new FdxException("UiKitTest could not find the sections nav child drag targets");
        }
        if (!showcase.navScroll().canScrollY()) {
            throw new FdxException("UiKitTest sections nav was not scrollable in validation mode: maxY="
                    + showcase.navScroll().maxY());
        }
        try {
            UiRect navBounds = nav.bounds();
            UiRect bounds = checkboxes.bounds();
            int x = root.displayX(bounds.x() + bounds.width() * 0.5f);
            int downY = root.displayY(bounds.y() + bounds.height() * 0.5f);
            int moveY = root.displayY(navBounds.y() + 10.0f);
            input.dispatchPointerMoved(x, downY);
            input.dispatchPointerDown(MouseButton.LEFT, x, downY);
            input.dispatchPointerMoved(x, moveY);
            input.dispatchPointerUp(MouseButton.LEFT, x, moveY);
            interaction.settleValidationLayout();
            if (showcase.activeSection().get() != SECTION_BUTTONS) {
                throw new FdxException("UiKitTest section child drag selected a section instead of scrolling: "
                        + showcase.activeSection().get());
            }
            if (showcase.navScroll().y() <= 20.0f) {
                throw new FdxException("UiKitTest section child drag did not scroll the nav list: " + showcase.navScroll().y());
            }
            showcase.navScroll().scrollTo(0.0f, 0.0f);
            interaction.settleValidationLayout();
            checkboxes = find(root.rootNode(), UiNodeType.BUTTON, "Checkboxes");
            if (checkboxes == null) {
                throw new FdxException("UiKitTest could not find the sections button after nav scroll reset");
            }
            bounds = checkboxes.bounds();
            x = root.displayX(bounds.x() + bounds.width() * 0.5f);
            downY = root.displayY(bounds.y() + bounds.height() * 0.5f);
            input.dispatchPointerMoved(x, downY);
            input.dispatchPointerDown(MouseButton.LEFT, x, downY);
            input.dispatchPointerUp(MouseButton.LEFT, x, downY);
            interaction.settleValidationLayout();
            if (showcase.activeSection().get() != SECTION_CHECKBOXES) {
                throw new FdxException("UiKitTest section tap did not select after child drag validation: "
                        + showcase.activeSection().get());
            }
            scrollChildDragChecked = true;
        } finally {
            showcase.navScroll().scrollTo(0.0f, 0.0f);
            interaction.settleValidationLayout();
        }
    }

    private void validateTextInputSession(UiNode stringField, UiNode intField, UiNode floatField) {
        if (textInputController == null) {
            textInputSessionChecked = true;
            return;
        }
        textInputController.reset();
        clickTextInputAt(stringField, 1.0f, 0.5f);
        if (!textInputController.visible()) {
            UiRect bounds = stringField.bounds();
            throw new FdxException("UiKitTest did not focus String field at bounds "
                    + bounds.x() + "," + bounds.y() + " " + bounds.width() + "x" + bounds.height()
                    + "; focused=" + stringField.focused()
                    + ", displayScale=" + display.contentScale()
                    + ", effectiveScale=" + root.displayX(100.0f) / 100.0f);
        }
        textInputController.assertVisible(TextInputType.TEXT, false, "String field");
        if (textInputController.updateCount() <= 0) {
            throw new FdxException("UiKitTest text field did not update the platform text input selection");
        }
        int showCount = textInputController.showCount();
        textInputController.simulatePlatformHidden();
        clickTextInputAt(stringField, 1.0f, 0.5f);
        if (!textInputController.visible() || textInputController.showCount() <= showCount) {
            throw new FdxException("UiKitTest did not reopen platform text input for focused String field");
        }
        clickTextInputAt(intField, 1.0f, 0.5f);
        textInputController.assertVisible(TextInputType.INTEGER, false, "Integer field");
        clickTextInputAt(floatField, 1.0f, 0.5f);
        textInputController.assertVisible(TextInputType.DECIMAL, false, "Float field");
        interaction.showValidationSection(SECTION_BUTTONS);
        interaction.settleValidationLayout();
        if (textInputController.hideCount() <= 0 || textInputController.visible()) {
            throw new FdxException("UiKitTest text input session was not hidden after leaving text fields");
        }
        interaction.showValidationSection(SECTION_TEXT_FIELDS);
        textInputSessionChecked = true;
    }

    private void clickTextInputAt(UiNode node, float xPercent, float yPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.x() + insideExtent(bounds.width(), xPercent));
        int y = root.displayY(bounds.y() + insideExtent(bounds.height(), yPercent));
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
        interaction.settleValidationLayout();
    }

    private void touchTextInputAt(UiNode node, float xPercent, float yPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.x() + insideExtent(bounds.width(), xPercent));
        int y = root.displayY(bounds.y() + insideExtent(bounds.height(), yPercent));
        input.dispatchTouchDown(0, x, y, 1.0f);
        input.dispatchTouchUp(0, x, y, 1.0f);
        interaction.settleValidationLayout();
    }

    private void assertMouseDoubleClickCopies(UiNode node, int characterOffset, String expected, String label) {
        int[] point = textCharacterPoint(node, characterOffset);
        input.dispatchPointerMoved(point[0], point[1]);
        input.dispatchPointerDown(MouseButton.LEFT, point[0], point[1]);
        input.dispatchPointerUp(MouseButton.LEFT, point[0], point[1]);
        input.dispatchPointerDown(MouseButton.LEFT, point[0], point[1]);
        input.dispatchPointerUp(MouseButton.LEFT, point[0], point[1]);
        interaction.settleValidationLayout();
        interaction.pressShortcut(Key.C);
        String copied = input.clipboard().getText();
        if (!expected.equals(copied)) {
            throw new FdxException("UiKitTest double-click selected the wrong " + label
                    + ": expected='" + expected + "', copied='" + copied + "'");
        }
        logger.info("UiKitTest verified mouse double-click " + label + ": copied='" + copied + "'");
    }

    private void assertTouchDoubleTapCopies(UiNode node, int characterOffset, String expected, String label) {
        int[] point = textCharacterPoint(node, characterOffset);
        input.dispatchTouchDown(0, point[0], point[1], 1.0f);
        input.dispatchTouchUp(0, point[0], point[1], 1.0f);
        input.dispatchTouchDown(0, point[0], point[1], 1.0f);
        input.dispatchTouchUp(0, point[0], point[1], 1.0f);
        interaction.settleValidationLayout();
        interaction.pressShortcut(Key.C);
        String copied = input.clipboard().getText();
        if (!expected.equals(copied)) {
            throw new FdxException("UiKitTest double-tap selected the wrong " + label
                    + ": expected='" + expected + "', copied='" + copied + "'");
        }
        logger.info("UiKitTest verified touch double-tap " + label + ": copied='" + copied + "'");
    }

    private int[] textCharacterPoint(UiNode node, int characterOffset) {
        if (node == null) {
            throw new FdxException("UiKitTest cannot resolve a character point for a null text node");
        }
        UiRect start = root.textCaretBounds(node, characterOffset);
        UiRect end = root.textCaretBounds(node, characterOffset + 1);
        if (start == null || end == null) {
            throw new FdxException("UiKitTest could not resolve caret bounds for text offset " + characterOffset);
        }
        int x = root.displayX((start.x() + end.x()) * 0.5f);
        int y = root.displayY(start.y() + start.height() * 0.5f);
        return new int[] { x, y };
    }

    private void dragTextInput(UiNode node, float startPercent, float endPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int downX = root.displayX(bounds.x() + bounds.width() * clamp(startPercent, 0.0f, 1.0f));
        int upX = root.displayX(bounds.x() + bounds.width() * clamp(endPercent, 0.0f, 1.0f));
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(downX, y);
        input.dispatchPointerDown(MouseButton.LEFT, downX, y);
        input.dispatchPointerMoved(upX, y);
        input.dispatchPointerUp(MouseButton.LEFT, upX, y);
        interaction.settleValidationLayout();
    }

    private void dragTextAreaTouch(UiNode node, float startXPercent, float startYPercent,
            float endXPercent, float endYPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int downX = root.displayX(bounds.x() + bounds.width() * clamp(startXPercent, 0.0f, 1.0f));
        int downY = root.displayY(bounds.y() + bounds.height() * clamp(startYPercent, 0.0f, 1.0f));
        int moveX = root.displayX(bounds.x() + bounds.width() * clamp(endXPercent, 0.0f, 1.0f));
        int moveY = root.displayY(bounds.y() + bounds.height() * clamp(endYPercent, 0.0f, 1.0f));
        input.dispatchTouchDown(0, downX, downY, 1.0f);
        input.dispatchTouchMoved(0, moveX, moveY, 1.0f);
        input.dispatchTouchUp(0, moveX, moveY, 1.0f);
        interaction.settleValidationLayout();
    }

    private void dragTextAreaMouseLine(UiNode node, int lineIndex, float scrollY, float lineHeight,
            float horizontalPadding, float verticalPadding) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        float lineCenterY = bounds.y() + verticalPadding + lineIndex * lineHeight - scrollY + lineHeight * 0.5f;
        int downX = root.displayX(bounds.x() + horizontalPadding);
        int upX = root.displayX(bounds.right() - horizontalPadding - 6.0f);
        int y = root.displayY(lineCenterY);
        input.dispatchPointerMoved(downX, y);
        input.dispatchPointerDown(MouseButton.LEFT, downX, y);
        input.dispatchPointerMoved(upX, y);
        input.dispatchPointerUp(MouseButton.LEFT, upX, y);
        interaction.settleValidationLayout();
    }

    private void dragTextAreaScrollGutter(UiNode node, float rightInset, float startYPercent, float endYPercent) {
        if (node == null) {
            return;
        }
        UiRect bounds = node.bounds();
        int x = root.displayX(bounds.right() - rightInset);
        int downY = root.displayY(bounds.y() + insideExtent(bounds.height(), startYPercent));
        int upY = root.displayY(bounds.y() + insideExtent(bounds.height(), endYPercent));
        input.dispatchPointerMoved(x, downY);
        input.dispatchPointerDown(MouseButton.LEFT, x, downY);
        input.dispatchPointerMoved(x, upY);
        input.dispatchPointerUp(MouseButton.LEFT, x, upY);
        interaction.settleValidationLayout();
    }

    private float insideExtent(float extent, float percent) {
        float clamped = clamp(percent, 0.0f, 1.0f);
        float value = extent * clamped;
        if (clamped >= 1.0f && extent > 1.0f) {
            // Stay inside both the widget and any overlaid parent or nested scrollbar hit gutter.
            return extent - Math.min(28.0f, extent * 0.25f);
        }
        return value;
    }

}
