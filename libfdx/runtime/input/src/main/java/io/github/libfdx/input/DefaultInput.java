package io.github.libfdx.input;

import io.github.libfdx.core.ProviderId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class DefaultInput implements Input {
    private final ProviderId providerId;
    private final InputCapabilities capabilities;
    private final Cursor cursor;
    private final Gamepads gamepads;
    private final TextInputController textInputController;
    private final List<InputProcessor> processors = new ArrayList<InputProcessor>();
    private final Set<Key> keys = EnumSet.noneOf(Key.class);
    private final Set<MouseButton> buttons = EnumSet.noneOf(MouseButton.class);
    private int pointerX;
    private int pointerY;
    private int activeTouches;

    public DefaultInput() {
        this(ProviderId.of("default_input"), DefaultInputCapabilities.desktop(), new DefaultCursor(),
                new DefaultGamepads());
    }

    public DefaultInput(ProviderId providerId, InputCapabilities capabilities, Cursor cursor, Gamepads gamepads) {
        this(providerId, capabilities, cursor, gamepads, null);
    }

    public DefaultInput(ProviderId providerId, InputCapabilities capabilities, Cursor cursor, Gamepads gamepads,
            TextInputController textInputController) {
        this.providerId = providerId != null ? providerId : ProviderId.of("default_input");
        this.capabilities = capabilities != null ? capabilities : DefaultInputCapabilities.none();
        this.cursor = cursor;
        this.gamepads = gamepads;
        this.textInputController = textInputController != null ? textInputController : TextInputController.NONE;
    }

    @Override
    public InputCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public void addProcessor(InputProcessor processor) {
        if (processor != null && !processors.contains(processor)) {
            processors.add(processor);
        }
    }

    @Override
    public void removeProcessor(InputProcessor processor) {
        processors.remove(processor);
    }

    @Override
    public void showTextInput(TextInputRequest request) {
        textInputController.showTextInput(request != null ? request : TextInputRequest.builder().build());
    }

    @Override
    public void updateTextInput(TextInputRequest request) {
        textInputController.updateTextInput(request != null ? request : TextInputRequest.builder().build());
    }

    @Override
    public void hideTextInput() {
        textInputController.hideTextInput();
    }

    @Override
    public boolean isKeyPressed(Key key) {
        return keys.contains(key);
    }

    @Override
    public boolean isMouseButtonPressed(MouseButton button) {
        if (button == MouseButton.LEFT && activeTouches > 0) {
            return true;
        }
        return buttons.contains(button);
    }

    @Override
    public int pointerX() {
        return pointerX;
    }

    @Override
    public int pointerY() {
        return pointerY;
    }

    @Override
    public Cursor cursor() {
        return cursor;
    }

    @Override
    public Gamepads gamepads() {
        return gamepads;
    }

    public boolean dispatchKeyDown(Key key) {
        KeyEvent event = new KeyEvent(System.nanoTime(), key, keys.contains(key));
        keys.add(event.key());
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).keyDown(event) || handled;
        }
        return handled;
    }

    public boolean dispatchKeyUp(Key key) {
        KeyEvent event = new KeyEvent(System.nanoTime(), key, false);
        keys.remove(event.key());
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).keyUp(event) || handled;
        }
        return handled;
    }

    public boolean dispatchPointerDown(MouseButton button, int x, int y) {
        pointerX = x;
        pointerY = y;
        PointerEvent event = PointerEvent.button(System.nanoTime(), button, x, y);
        buttons.add(event.button());
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).pointerDown(event) || handled;
        }
        return handled;
    }

    public boolean dispatchPointerUp(MouseButton button, int x, int y) {
        pointerX = x;
        pointerY = y;
        PointerEvent event = PointerEvent.button(System.nanoTime(), button, x, y);
        buttons.remove(event.button());
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).pointerUp(event) || handled;
        }
        return handled;
    }

    public boolean dispatchPointerMoved(int x, int y) {
        pointerX = x;
        pointerY = y;
        PointerEvent event = PointerEvent.pointer(System.nanoTime(), x, y);
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).pointerMoved(event) || handled;
        }
        return handled;
    }

    public boolean dispatchScrolled(int x, int y, float scrollX, float scrollY) {
        pointerX = x;
        pointerY = y;
        PointerEvent event = PointerEvent.scroll(System.nanoTime(), x, y, scrollX, scrollY);
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).scrolled(event) || handled;
        }
        return handled;
    }

    public boolean dispatchTouchDown(int id, int x, int y, float pressure) {
        pointerX = x;
        pointerY = y;
        activeTouches++;
        TouchEvent event = new TouchEvent(System.nanoTime(), new TouchPoint(id, x, y, pressure));
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).touchDown(event) || handled;
        }
        return handled;
    }

    public boolean dispatchTouchUp(int id, int x, int y, float pressure) {
        pointerX = x;
        pointerY = y;
        if (activeTouches > 0) {
            activeTouches--;
        }
        TouchEvent event = new TouchEvent(System.nanoTime(), new TouchPoint(id, x, y, pressure));
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).touchUp(event) || handled;
        }
        return handled;
    }

    public boolean dispatchTouchMoved(int id, int x, int y, float pressure) {
        pointerX = x;
        pointerY = y;
        TouchEvent event = new TouchEvent(System.nanoTime(), new TouchPoint(id, x, y, pressure));
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).touchMoved(event) || handled;
        }
        return handled;
    }

    public boolean dispatchTextInput(String text) {
        TextInputEvent event = new TextInputEvent(System.nanoTime(), text, false);
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).textInput(event) || handled;
        }
        return handled;
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
