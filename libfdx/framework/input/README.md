# Input actions

`Input` remains the backend-owned source of events, raw state, and gamepads.
Applications own `InputActions` contexts and their stable `InputAction` handles.
Configure named actions and immutable bindings, then attach the context explicitly.
All configuration, polling, event handling, and disposal belong to the application
thread. Action polling and transition handling reuse fixed storage.

```java
InputActions controls = new InputActions(input);
InputAction move = controls.define("move", 0.1f);
InputAction jump = controls.define("jump");
controls.bind(move, InputBinding.key(Key.A, -1));
controls.bind(move, InputBinding.key(Key.D));
controls.bind(move, InputBinding.axis(-1, GamepadAxis.LEFT_X, 0.2f, 1));
controls.bind(jump, InputBinding.key(Key.SPACE));
controls.bind(jump, InputBinding.button(-1, GamepadButton.SOUTH, 1));
input.addProcessor(controls);

// Every render iteration, including frames with no simulation tick:
controls.surface(windowWidth, windowHeight); // logical window dimensions
controls.update();
// During a simulation tick:
float direction = move.value();
if (jump.consumePressed()) player.jump();
```

Bindings sum and clamp to [-1,1]; opposite digital directions cancel. An action
is down when its absolute value reaches its threshold. Press/release transitions
latch until consumed or `clearTransitions()`, so a tap between simulation ticks
survives. Multiple transitions of the same kind coalesce. Clear transitions only
after their consumers run. Backend key repeat never creates another action press.

Keyboard/mouse activation follows admitted events. Polling only reconciles missing
releases; it never resurrects a press consumed by UI. Gamepads are polled using
standard mappings. Index -1 selects the strongest connected control; equal
magnitudes retain connection order. Stick axes are [-1,1], positive Y down, and
triggers are [0,1]. Axial dead zones rescale the remaining range; vector/radial
stick processing can be layered over these scalar actions.

`InputBinding.touch(x,y,width,height,scale)` uses normalized logical window bounds,
with a top-left origin and exclusive right/bottom edges. Update `surface()` on
resize, using logical dimensions rather than framebuffer pixels. Sixteen admitted
touch IDs are tracked independently; extra touches are rejected without evicting
active fingers. A drag can enter or leave a region. Touch starts consumed by an
earlier router child do not enter the action context. Touch bounds define input
only; applications render the corresponding controls.

`reset()` and disabling a context clear held state and latches without synthesizing
releases. Call reset on focus loss. Use `enabled(false)` for modal UI or paused
gameplay, and re-enable when it closes. Held gamepad controls must return to neutral
before activating again. Repeated key-down events after reset also remain ignored
until a fresh physical press. Backend focus notifications remain platform-specific.

`exportBindings()` returns versioned text using action and enum names. Store that
text through application-owned preferences/files, then `importBindings()` after
defining the same named actions. Import replaces bindings atomically and rejects
unknown actions, invalid parameters, unsupported versions, oversized text, or
capacity overflow. Thresholds remain part of the application's action definitions.
Configuration changes reset held state and transitions. Direct `clearBindings` and
`bind` calls support settings interfaces. Serialization allocates only when called.

## UI routing

`DefaultInput` broadcasts to its independent processors and combines their handled
results. For ordered consumption, register one `InputRouter` and put UI before
gameplay inside it:

```java
InputRouter router = new InputRouter();
input.addProcessor(router);
uiRoot.input(input, router); // app-owned UI root; adds its handler first
router.add(controls);
```

Handled presses, moves, scroll, and text stop traversal. Key, pointer, and touch
releases still visit every child to clear held state. A router borrows its children;
configuration must happen outside dispatch. Disable gameplay contexts explicitly
when UI captures controller input, since gamepad polling has no event to consume.
Disposing a UI root removes only its own handler. Disposing actions resets them
and detaches direct registration; remove a routed context from its router too.

## Backend gamepads

Desktop JVM polls GLFW's standard gamepad mappings with reusable native state and
owns its joystick callback. The browser polls the standard Gamepad API for indices
0–15. Unsupported mappings stay unexposed. Device objects remain stable while
connected; connection changes notify listeners on the application thread. Retained
disconnected devices read neutral values. Remove listeners during application
disposal; backend shutdown disconnects remaining devices. A supported API does not
imply a connected device or access permitted by browser policy.

GLFW mappings and browser standard mappings are translated to common button names;
GLFW trigger ranges are normalized. See the [GLFW input guide](https://www.glfw.org/docs/latest/input_guide.html#gamepad)
and [Gamepad specification](https://www.w3.org/TR/gamepad/). Browsers may require
secure contexts, permission policy, and a user gesture before exposing controllers.
Browser-provided snapshots may allocate; libFDX reuses Java state between hotplug
events. Other backends retain their existing capabilities; these polling paths do
not add Android/native-C/iOS controller support.

The `input-actions` scenario demonstrates A/D movement, Space/mouse/touch/controller
jump, F1 context gating, F2 persistent rebinding to J, and UI consumption of Q.
The platformer uses these actions and consumes latched presses during fixed steps.
