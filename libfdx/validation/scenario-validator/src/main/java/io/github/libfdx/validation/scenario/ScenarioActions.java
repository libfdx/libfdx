package io.github.libfdx.validation.scenario;

import io.github.libfdx.input.Key;

/**
 * Represents a scenario actions.
 *
 * @author xpenatan
 */
public final class ScenarioActions {
    private ScenarioActions() {
    }

    /**
     * Runs the emit step.
     *
     * @param event the event
     * @return the emit
     */
    public static ScenarioAction emit(String event) {
        return callback("emit(" + event + ")", context -> context.emit(event));
    }

    /**
     * Runs the capture step.
     *
     * @param name the name
     * @return the capture
     */
    public static ScenarioAction capture(String name) {
        return callback("capture(" + name + ")", context -> context.requestCapture(name));
    }

    /**
     * Runs the key step.
     *
     * @param key the key
     * @return the key
     */
    public static KeyAction key(Key key) {
        return new KeyAction(key, 0);
    }

    /**
     * Runs the hold key step.
     *
     * @param key the key
     * @param frames the frames
     * @return the hold key
     */
    public static KeyAction holdKey(Key key, int frames) {
        return key(key).holdFrames(frames);
    }

    /**
     * Runs the click step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return the click
     */
    public static ScenarioAction click(float x, float y) {
        return callback("click(" + x + "," + y + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            context.host().pointer(x, y);
            input.pointerMove(x, y);
            input.pointerDown(x, y);
            input.pointerUp(x, y);
            context.emit("input.pointer.click:" + x + "," + y);
        });
    }

    /**
     * Runs the pointer move step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return the pointer move
     */
    public static ScenarioAction pointerMove(float x, float y) {
        return callback("pointerMove(" + x + "," + y + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            context.host().pointer(x, y);
            input.pointerMove(x, y);
            context.emit("input.pointer.move:" + x + "," + y);
        });
    }

    /**
     * Returns the pointer down.
     *
     * @return the pointer down
     */
    public static ScenarioAction pointerDown() {
        return callback("pointerDown", context -> {
            ScenarioInputDriver input = requireInput(context);
            float x = context.host().pointerX();
            float y = context.host().pointerY();
            input.pointerDown(x, y);
            context.emit("input.pointer.down:" + x + "," + y);
        });
    }

    /**
     * Runs the pointer down step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return the pointer down
     */
    public static ScenarioAction pointerDown(float x, float y) {
        return callback("pointerDown(" + x + "," + y + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            context.host().pointer(x, y);
            input.pointerDown(x, y);
            context.emit("input.pointer.down:" + x + "," + y);
        });
    }

    /**
     * Returns the pointer up.
     *
     * @return the pointer up
     */
    public static ScenarioAction pointerUp() {
        return callback("pointerUp", context -> {
            ScenarioInputDriver input = requireInput(context);
            float x = context.host().pointerX();
            float y = context.host().pointerY();
            input.pointerUp(x, y);
            context.emit("input.pointer.up:" + x + "," + y);
        });
    }

    /**
     * Runs the pointer up step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return the pointer up
     */
    public static ScenarioAction pointerUp(float x, float y) {
        return callback("pointerUp(" + x + "," + y + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            context.host().pointer(x, y);
            input.pointerUp(x, y);
            context.emit("input.pointer.up:" + x + "," + y);
        });
    }

    /**
     * Runs the type step.
     *
     * @param text the text
     * @return the type
     */
    public static ScenarioAction type(String text) {
        return callback("type", context -> {
            ScenarioInputDriver input = requireInput(context);
            input.text(text != null ? text : "");
            context.emit("input.text");
        });
    }

    /**
     * Runs the scroll step.
     *
     * @param amountX the amount x
     * @param amountY the amount y
     * @return the scroll
     */
    public static ScenarioAction scroll(float amountX, float amountY) {
        return callback("scroll(" + amountX + "," + amountY + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            input.scroll(amountX, amountY);
            context.emit("input.scroll:" + amountX + "," + amountY);
        });
    }

    /**
     * Runs the callback step.
     *
     * @param name the name
     * @param callback the callback to invoke
     * @return the callback
     */
    public static ScenarioAction callback(String name, ScenarioCallback callback) {
        return new CallbackAction(name, callback);
    }

    /**
     * Represents a key action.
     *
     * @author xpenatan
     */
    public static final class KeyAction implements ScenarioAction {
        private final Key key;
        private final int holdFrames;

        private KeyAction(Key key, int holdFrames) {
            this.key = key != null ? key : Key.UNKNOWN;
            this.holdFrames = Math.max(0, holdFrames);
        }

        /**
         * Sets the hold frames and returns this key action.
         *
         * @param frames the frames
         * @return this key action for chaining
         */
        public KeyAction holdFrames(int frames) {
            return new KeyAction(key, frames);
        }

        /**
         * Returns the name.
         *
         * @return the name
         */
        @Override
        public String name() {
            return holdFrames > 0 ? "key(" + key + ").holdFrames(" + holdFrames + ")" : "key(" + key + ")";
        }

        /**
         * Runs the perform step.
         *
         * @param context the context
         */
        @Override
        public void perform(ScenarioContext context) {
            ScenarioInputDriver input = requireInput(context);
            input.keyDown(key);
            context.emit("input.key.down:" + key);
            for (int i = 0; i < holdFrames; i++) {
                context.host().advanceFrame(context);
            }
            input.keyUp(key);
            context.emit("input.key.up:" + key);
        }
    }

    /**
     * Represents a callback action.
     *
     * @author xpenatan
     */
    private static final class CallbackAction implements ScenarioAction {
        private final String name;
        private final ScenarioCallback callback;

        CallbackAction(String name, ScenarioCallback callback) {
            if (callback == null) {
                throw new IllegalArgumentException("Scenario action callback cannot be null.");
            }
            this.name = name != null && name.length() > 0 ? name : "action";
            this.callback = callback;
        }

        /**
         * Returns the name.
         *
         * @return the name
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * Runs the perform step.
         *
         * @param context the context
         */
        @Override
        public void perform(ScenarioContext context) {
            callback.run(context);
        }
    }

    private static ScenarioInputDriver requireInput(ScenarioContext context) {
        ScenarioInputDriver input = context.host().inputDriver();
        if (input == null) {
            context.fail("Scenario host does not provide an input driver.");
        }
        return input;
    }
}
