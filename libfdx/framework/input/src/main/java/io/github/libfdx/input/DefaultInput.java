package io.github.libfdx.input;

import io.github.libfdx.core.ProviderId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Provides the default implementation of an input.
 *
 * @author xpenatan
 */
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
    private int pointerScreenX;
    private int pointerScreenY;
    private int activeTouches;

    /**
     * Creates a default input.
     */
    public DefaultInput() {
        this(ProviderId.of("default_input"), DefaultInputCapabilities.desktop(), new DefaultCursor(),
                new DefaultGamepads());
    }

    /**
     * Creates a default input.
     *
     * @param providerId the provider ID
     * @param capabilities the capabilities
     * @param cursor the cursor
     * @param gamepads the gamepads
     */
    public DefaultInput(ProviderId providerId, InputCapabilities capabilities, Cursor cursor, Gamepads gamepads) {
        this(providerId, capabilities, cursor, gamepads, null);
    }

    /**
     * Creates a default input.
     *
     * @param providerId the provider ID
     * @param capabilities the capabilities
     * @param cursor the cursor
     * @param gamepads the gamepads
     * @param textInputController the text input controller
     */
    public DefaultInput(ProviderId providerId, InputCapabilities capabilities, Cursor cursor, Gamepads gamepads,
            TextInputController textInputController) {
        this.providerId = providerId != null ? providerId : ProviderId.of("default_input");
        this.capabilities = capabilities != null ? capabilities : DefaultInputCapabilities.none();
        this.cursor = cursor;
        this.gamepads = gamepads;
        this.textInputController = textInputController != null ? textInputController : TextInputController.NONE;
    }

    /**
     * Returns the capabilities.
     *
     * @return the capabilities
     */
    @Override
    public InputCapabilities capabilities() {
        return capabilities;
    }

    /**
     * Adds the processor.
     *
     * @param processor the processor
     */
    @Override
    public void addProcessor(InputProcessor processor) {
        if (processor != null && !processors.contains(processor)) {
            processors.add(processor);
        }
    }

    /**
     * Removes the processor.
     *
     * @param processor the processor
     */
    @Override
    public void removeProcessor(InputProcessor processor) {
        processors.remove(processor);
    }

    /**
     * Runs the show text input step.
     *
     * @param request the request
     */
    @Override
    public void showTextInput(TextInputRequest request) {
        textInputController.showTextInput(request != null ? request : TextInputRequest.builder().build());
    }

    /**
     * Runs the update text input step.
     *
     * @param request the request
     */
    @Override
    public void updateTextInput(TextInputRequest request) {
        textInputController.updateTextInput(request != null ? request : TextInputRequest.builder().build());
    }

    /**
     * Runs the hide text input step.
     */
    @Override
    public void hideTextInput() {
        textInputController.hideTextInput();
    }

    /**
     * Returns whether key pressed is enabled or true.
     *
     * @param key the key
     * @return true if key pressed is enabled or true; false otherwise
     */
    @Override
    public boolean isKeyPressed(Key key) {
        return keys.contains(key);
    }

    /**
     * Returns whether mouse button pressed is enabled or true.
     *
     * @param button the button
     * @return true if mouse button pressed is enabled or true; false otherwise
     */
    @Override
    public boolean isMouseButtonPressed(MouseButton button) {
        if (button == MouseButton.LEFT && activeTouches > 0) {
            return true;
        }
        return buttons.contains(button);
    }

    /**
     * Returns the pointer x.
     *
     * @return the pointer x
     */
    @Override
    public int pointerX() {
        return pointerX;
    }

    /**
     * Returns the pointer y.
     *
     * @return the pointer y
     */
    @Override
    public int pointerY() {
        return pointerY;
    }

    /**
     * Returns the pointer x position in screen coordinates.
     *
     * @return the screen x position
     */
    @Override
    public int pointerScreenX() {
        return pointerScreenX;
    }

    /**
     * Returns the pointer y position in screen coordinates.
     *
     * @return the screen y position
     */
    @Override
    public int pointerScreenY() {
        return pointerScreenY;
    }

    /**
     * Returns the cursor.
     *
     * @return the cursor
     */
    @Override
    public Cursor cursor() {
        return cursor;
    }

    /**
     * Returns the gamepads.
     *
     * @return the gamepads
     */
    @Override
    public Gamepads gamepads() {
        return gamepads;
    }

    /**
     * Runs the dispatch key down step.
     *
     * @param key the key
     * @return true if dispatch key down succeeds or is active; false otherwise
     */
    public boolean dispatchKeyDown(Key key) {
        KeyEvent event = new KeyEvent(System.nanoTime(), key, keys.contains(key));
        keys.add(event.key());
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).keyDown(event) || handled;
        }
        return handled;
    }

    /**
     * Runs the dispatch key up step.
     *
     * @param key the key
     * @return true if dispatch key up succeeds or is active; false otherwise
     */
    public boolean dispatchKeyUp(Key key) {
        KeyEvent event = new KeyEvent(System.nanoTime(), key, false);
        keys.remove(event.key());
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).keyUp(event) || handled;
        }
        return handled;
    }

    /**
     * Runs the dispatch pointer down step.
     *
     * @param button the button
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if dispatch pointer down succeeds or is active; false otherwise
     */
    public boolean dispatchPointerDown(MouseButton button, int x, int y) {
        return dispatchPointerDown(button, x, y, x, y);
    }

    /**
     * Dispatches a pointer-down event with both window and screen coordinates.
     *
     * @param button the button
     * @param x the window x coordinate
     * @param y the window y coordinate
     * @param screenX the screen x coordinate
     * @param screenY the screen y coordinate
     * @return true when handled
     */
    public boolean dispatchPointerDown(MouseButton button, int x, int y, int screenX, int screenY) {
        pointerX = x;
        pointerY = y;
        pointerScreenX = screenX;
        pointerScreenY = screenY;
        PointerEvent event = PointerEvent.button(System.nanoTime(), button, x, y);
        buttons.add(event.button());
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).pointerDown(event) || handled;
        }
        return handled;
    }

    /**
     * Runs the dispatch pointer up step.
     *
     * @param button the button
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if dispatch pointer up succeeds or is active; false otherwise
     */
    public boolean dispatchPointerUp(MouseButton button, int x, int y) {
        return dispatchPointerUp(button, x, y, x, y);
    }

    /**
     * Dispatches a pointer-up event with both window and screen coordinates.
     *
     * @param button the button
     * @param x the window x coordinate
     * @param y the window y coordinate
     * @param screenX the screen x coordinate
     * @param screenY the screen y coordinate
     * @return true when handled
     */
    public boolean dispatchPointerUp(MouseButton button, int x, int y, int screenX, int screenY) {
        pointerX = x;
        pointerY = y;
        pointerScreenX = screenX;
        pointerScreenY = screenY;
        PointerEvent event = PointerEvent.button(System.nanoTime(), button, x, y);
        buttons.remove(event.button());
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).pointerUp(event) || handled;
        }
        return handled;
    }

    /**
     * Runs the dispatch pointer moved step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if dispatch pointer moved succeeds or is active; false otherwise
     */
    public boolean dispatchPointerMoved(int x, int y) {
        return dispatchPointerMoved(x, y, x, y);
    }

    /**
     * Dispatches a pointer-move event with both window and screen coordinates.
     *
     * @param x the window x coordinate
     * @param y the window y coordinate
     * @param screenX the screen x coordinate
     * @param screenY the screen y coordinate
     * @return true when handled
     */
    public boolean dispatchPointerMoved(int x, int y, int screenX, int screenY) {
        pointerX = x;
        pointerY = y;
        pointerScreenX = screenX;
        pointerScreenY = screenY;
        PointerEvent event = PointerEvent.pointer(System.nanoTime(), x, y);
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).pointerMoved(event) || handled;
        }
        return handled;
    }

    /**
     * Runs the dispatch scrolled step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param scrollX the scroll x
     * @param scrollY the scroll y
     * @return true if dispatch scrolled succeeds or is active; false otherwise
     */
    public boolean dispatchScrolled(int x, int y, float scrollX, float scrollY) {
        return dispatchScrolled(x, y, x, y, scrollX, scrollY);
    }

    /**
     * Dispatches a scroll event with both window and screen coordinates.
     *
     * @param x the window x coordinate
     * @param y the window y coordinate
     * @param screenX the screen x coordinate
     * @param screenY the screen y coordinate
     * @param scrollX the horizontal scroll amount
     * @param scrollY the vertical scroll amount
     * @return true when handled
     */
    public boolean dispatchScrolled(int x, int y, int screenX, int screenY, float scrollX, float scrollY) {
        pointerX = x;
        pointerY = y;
        pointerScreenX = screenX;
        pointerScreenY = screenY;
        PointerEvent event = PointerEvent.scroll(System.nanoTime(), x, y, scrollX, scrollY);
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).scrolled(event) || handled;
        }
        return handled;
    }

    /**
     * Runs the dispatch touch down step.
     *
     * @param id the identifier
     * @param x the x coordinate
     * @param y the y coordinate
     * @param pressure the pressure
     * @return true if dispatch touch down succeeds or is active; false otherwise
     */
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

    /**
     * Runs the dispatch touch up step.
     *
     * @param id the identifier
     * @param x the x coordinate
     * @param y the y coordinate
     * @param pressure the pressure
     * @return true if dispatch touch up succeeds or is active; false otherwise
     */
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

    /**
     * Runs the dispatch touch moved step.
     *
     * @param id the identifier
     * @param x the x coordinate
     * @param y the y coordinate
     * @param pressure the pressure
     * @return true if dispatch touch moved succeeds or is active; false otherwise
     */
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

    /**
     * Runs the dispatch text input step.
     *
     * @param text the text
     * @return true if dispatch text input succeeds or is active; false otherwise
     */
    public boolean dispatchTextInput(String text) {
        TextInputEvent event = new TextInputEvent(System.nanoTime(), text, false);
        boolean handled = false;
        for (int i = 0; i < processors.size(); i++) {
            handled = processors.get(i).textInput(event) || handled;
        }
        return handled;
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
