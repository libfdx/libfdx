package io.github.libfdx.validation.scenario;

import io.github.libfdx.input.Key;

public final class ScenarioActions {
    private ScenarioActions() {
    }

    public static ScenarioAction emit(String event) {
        return callback("emit(" + event + ")", context -> context.emit(event));
    }

    public static ScenarioAction capture(String name) {
        return callback("capture(" + name + ")", context -> context.requestCapture(name));
    }

    public static KeyAction key(Key key) {
        return new KeyAction(key, 0);
    }

    public static KeyAction holdKey(Key key, int frames) {
        return key(key).holdFrames(frames);
    }

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

    public static ScenarioAction pointerMove(float x, float y) {
        return callback("pointerMove(" + x + "," + y + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            context.host().pointer(x, y);
            input.pointerMove(x, y);
            context.emit("input.pointer.move:" + x + "," + y);
        });
    }

    public static ScenarioAction pointerDown() {
        return callback("pointerDown", context -> {
            ScenarioInputDriver input = requireInput(context);
            float x = context.host().pointerX();
            float y = context.host().pointerY();
            input.pointerDown(x, y);
            context.emit("input.pointer.down:" + x + "," + y);
        });
    }

    public static ScenarioAction pointerDown(float x, float y) {
        return callback("pointerDown(" + x + "," + y + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            context.host().pointer(x, y);
            input.pointerDown(x, y);
            context.emit("input.pointer.down:" + x + "," + y);
        });
    }

    public static ScenarioAction pointerUp() {
        return callback("pointerUp", context -> {
            ScenarioInputDriver input = requireInput(context);
            float x = context.host().pointerX();
            float y = context.host().pointerY();
            input.pointerUp(x, y);
            context.emit("input.pointer.up:" + x + "," + y);
        });
    }

    public static ScenarioAction pointerUp(float x, float y) {
        return callback("pointerUp(" + x + "," + y + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            context.host().pointer(x, y);
            input.pointerUp(x, y);
            context.emit("input.pointer.up:" + x + "," + y);
        });
    }

    public static ScenarioAction type(String text) {
        return callback("type", context -> {
            ScenarioInputDriver input = requireInput(context);
            input.text(text != null ? text : "");
            context.emit("input.text");
        });
    }

    public static ScenarioAction scroll(float amountX, float amountY) {
        return callback("scroll(" + amountX + "," + amountY + ")", context -> {
            ScenarioInputDriver input = requireInput(context);
            input.scroll(amountX, amountY);
            context.emit("input.scroll:" + amountX + "," + amountY);
        });
    }

    public static ScenarioAction callback(String name, ScenarioCallback callback) {
        return new CallbackAction(name, callback);
    }

    public static final class KeyAction implements ScenarioAction {
        private final Key key;
        private final int holdFrames;

        private KeyAction(Key key, int holdFrames) {
            this.key = key != null ? key : Key.UNKNOWN;
            this.holdFrames = Math.max(0, holdFrames);
        }

        public KeyAction holdFrames(int frames) {
            return new KeyAction(key, frames);
        }

        @Override
        public String name() {
            return holdFrames > 0 ? "key(" + key + ").holdFrames(" + holdFrames + ")" : "key(" + key + ")";
        }

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

        @Override
        public String name() {
            return name;
        }

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
