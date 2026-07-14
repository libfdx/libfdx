# libFDX UI Kit

UI Kit is libFDX's plain-Java game UI toolkit. It combines declarative content
with a retained runtime: application code describes the current UI, while
`UiRoot` preserves nodes, focus, hover/press state, editing state, scroll
positions, windows, and animations.

This guide owns UI concepts and behavior. Exact methods and overloads belong to
Java source/Javadocs; cross-module ownership rules are in
[COMMON_API.md](COMMON_API.md#12-ui-kit) and
[ARCHITECTURE.md](ARCHITECTURE.md).

## Topics

- [1. Ownership and Lifecycle](#1-ownership-and-lifecycle)
- [2. Composition and State](#2-composition-and-state)
- [3. Layout](#3-layout)
- [4. Styling, Images, and Nine-Patch](#4-styling-images-and-nine-patch)
- [5. Text and Editing](#5-text-and-editing)
- [6. Animation](#6-animation)
- [7. Input, Focus, and Layers](#7-input-focus-and-layers)
- [8. Rendering and Performance](#8-rendering-and-performance)
- [9. Module Boundary and Validation](#9-module-boundary-and-validation)

## 1. Ownership and Lifecycle

Application code creates and owns each UI root. UI Kit is not a backend service
and does not add `ui()` to `Fdx`. A backend may use a private preload root, but
that root is disposed before the game listener starts.

The normal lifecycle is:

```java
public final class Menu extends ApplicationAdapter {
    private Fdx fdx;
    private UiRoot root;
    private final UiBooleanState showStats = Ui.state(false);

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        root = new UiToolkit(fdx.files())
            .root(fdx.displays().main(), fdx.graphics().main());
        root.setContent(ui -> ui.column(
            Ui.modifier().fill().padding(16).gap(8),
            column -> {
                column.text("Inventory");
                column.checkbox("Show stats", showStats);
                if (showStats.get()) {
                    column.text("Damage: " + playerDamage());
                }
            }));
    }

    @Override
    public void resize(int width, int height) {
        root.resize(width, height);
    }

    @Override
    public void render() {
        root.update(fdx.app().deltaTime());
        root.render();
    }

    @Override
    public void dispose() {
        root.dispose();
    }
}
```

The application also owns referenced textures, fonts, and themes unless an
explicit UI resource owner accepted them. Disposing a root releases its owned
render/runtime resources but not arbitrary borrowed game assets.

## 2. Composition and State

`setContent` installs a declarative content lambda. The runtime may execute it
again when observed UI state changes. Reconciliation updates retained nodes
instead of recreating interaction state.

- Simple static trees use call order for identity.
- Dynamic collections use stable keys.
- Internal widget state survives while identity stays stable.
- Removing a node releases its retained state unless an exit transition keeps
  it alive temporarily.
- External game state can be read directly, but changing it requires
  `requestCompose()` when no observed `UiState` caused recomposition.

State is explicit. There are no annotations, generated bindings, reflection, or
compiler plugins.

- Reference values use `UiState<T>`.
- Primitive values use `UiBooleanState`, `UiIntState`, `UiFloatState`,
  `UiLongState`, and `UiDoubleState`.
- Never use boxed primitive state such as `UiState<Boolean>`.
- State mutation marks observing roots dirty; it does not force unrelated roots
  to recompose.

Cache reusable state outside content lambdas. Creating state while describing
content without a stable retaining mechanism loses its intended ownership.

## 3. Layout

UI Kit intentionally uses a compact layout vocabulary:

| Container | Behavior |
| --- | --- |
| Column | Vertical flow |
| Row | Horizontal flow with normal cross-axis alignment |
| Stack | Overlapping children |
| Grid | Rows and columns |
| Scroll/scroll view | Clipped content with retained scroll state |
| Panel | Styled container |
| Window | Movable/resizable retained panel |
| Spacer | Fixed or weighted space |

Modifiers express fill/wrap/fixed size, min/preferred/max constraints, padding,
gap, weight, alignment, aspect ratio, offset, clipping, drawing, input, and
semantics. Layout is constraint-based and predictable; it is not CSS.

Important behavior:

- rows and columns measure children before allocating weighted remaining space;
- scroll containers track viewport and content size and clip overflow;
- dragging unclaimed scroll-view space scrolls the container;
- `UiScrollState` can make a vertical range visible without moving content that
  is already visible;
- dynamic/virtualized lists preserve keyed item identity;
- movable windows retain position, size, and z-order and move to the front on
  relevant press/drag/resize interaction;
- safe-area insets and parent constraints bound content on mobile, browser,
  console/TV, and resizable desktop targets.

`UiRoot.uiScale(...)` scales logical units for DPI and accessibility.
`autoUiScale(true)` multiplies the root scale by `Display.contentScale()`. Do not
apply both backend pixel scaling and UI content scaling manually.

## 4. Styling, Images, and Nine-Patch

Programmatic themes/styles are the baseline. Themes provide reusable colors,
spacing, fonts, drawables, widget states, and motion tokens. A modifier may
select or override style without changing widget identity.

`UiDrawable` covers color, texture/region, and nine-patch drawing. Nine-patch
data may come from `.9.png` marker borders or explicit split/padding metadata.

- Stretch splits and content padding are distinct.
- Fixed corners and padding contribute to minimum/preferred size.
- Rendering uses nine batched quads through g2d.
- Panels, buttons, fields, tabs, and scrollbars can use the same theme-level
  nine-patch vocabulary.

Nine-patch affects measurement and drawing; it is not a separate layout system.

## 5. Text and Editing

Text measurement and rendering use g2d bitmap-font data. UI fonts can reference
a `BitmapFont`, an AngelCode-style `.fnt` asset, or a `.ttf`/`.otf` FreeType
source rasterized into a bitmap atlas during loading.

- Generate atlases at the effective UI scale or oversample them to avoid blurry
  upscaling.
- System-family descriptors fail clearly until a backend-specific family-font
  provider exists.
- Layout measures text before drawing and supports wrapping, alignment,
  multiline content, ellipsis, line height, and style effects supported by the
  active font stack.
- Localization belongs in application resource lookup; UI content accepts the
  resulting text without owning localization storage.

Text fields retain cursor, selection, filtering, masking, and validation state.
Text areas add multiline layout and internal scrolling, with fixed-height or
bounded auto-grow policy.

When an editor gains focus, changes selection/text, or loses focus, UI Kit uses
the platform text-input session through `Input`. Focused bounds and the active
text-area caret line are supplied so mobile/browser editors can keep edited
content visible. Native/DOM editor panels remain backend-owned and are styled by
backend configuration or platform defaults, not widget styles.

Copy, cut, paste, pointer selection, drag selection, tap-to-place-caret, and
multiline drag scrolling remain root-local UI behavior where supported by input
capabilities.

## 6. Animation

Animation state belongs to retained nodes. Recomposition changes targets;
`UiRoot.update(deltaTime)` advances current values before layout/rendering.

- Timing is deterministic from supplied delta time, never direct wall-clock
  time.
- Stable keys retain running animation state.
- Exit transitions keep removed content alive until exit completes.
- Layout-affecting animation dirties the smallest practical subtree.
- Render-only alpha, tint, offset, scale, and rotation avoid relayout.
- Common animation paths do not allocate per frame.
- Descriptors are immutable or treated as immutable after use.
- A root animation scale supports pause/disable, slow motion, fast-forward,
  accessibility, and deterministic tests.

Duration/easing animation is the portable baseline. Visibility, content-size,
keyed placement, screen, and widget-state transitions build on the same retained
clock. Springs are optional rather than required portable semantics.

## 7. Input, Focus, and Layers

`UiRoot` owns hit testing and event routing for the inputs exposed by the active
backend. This includes hover/press, pointer capture, wheel/touch scrolling,
keyboard/text focus, navigation, gestures, and drag/drop.

- Pointer capture continues a drag until release even outside original bounds.
- Disabled, read-only, and hidden state affects both focus and hit testing.
- Tab/Shift-Tab, arrows, and gamepad navigation follow focus scopes and explicit
  neighbor overrides.
- Modals trap focus and block input behind their layer.
- Semantic labels are separate from displayed text and validation IDs.
- Audio/haptic hooks may be emitted by UI; actual audio/haptic services remain
  outside UI Kit.

A root normally owns one ordered layer stack: base content, overlays/HUD,
popups/tooltips, modals/scrims, toasts, and debug content.

- Draw order and hit-test order agree.
- Later declarations in one layer appear above earlier declarations.
- Popups pass input through by default unless configured as blocking.
- Modals block by default and own their scrim.
- Closing a popup/modal restores focus where possible.
- Tooltips can use hovered node text/semantic label or an explicit
  `tooltipTarget` key. A delayed tooltip requests composition when its delay
  expires.

Prefer layers within one root over independent roots unless the UI truly belongs
to another display/context or lifecycle.

## 8. Rendering and Performance

UI Kit renders with g2d for rectangles, images, nine-patches, glyphs, and custom
draw hooks. It uses common graphics only for lower-level clipping/targets and
never imports GL, Vulkan, WGPU, or backend classes.

- Rendering respects layer order, clipping/scissor, opacity, transforms, and
  the same z-order used by input.
- Text fields/areas draw caret and selection within their clip.
- Batches, vertex data, glyph layout storage, hit-test data, and event objects
  are reused in steady state.
- Composition/layout/draw work is limited to dirty state where practical.
- No Java objects are allocated in normal per-frame UI update/render paths.
- `debugLines(true)` may overlay bounds, hit targets, focus, title bars, and
  resize handles; full-screen edge outlines remain visible rather than clipping
  at the framebuffer edge.

## 9. Module Boundary and Validation

UI Kit remains one user-facing module. It may depend on core runtime contracts,
application/display/input, common graphics, g2d, and asset support needed for
fonts/skins. It does not depend on backends or provider-specific graphics.

Low-level retained nodes are an advanced customization/debugging surface. The
normal authoring API remains `UiScope` plus state and modifiers; avoid a second
parallel actor/widget hierarchy.

For UI changes, test the focused widget or layout first and inspect a rendered
frame. Shared renderer/text changes require the affected provider matrix. The
full protocol and relevant built-in scenarios are in
[TESTING.md](TESTING.md#4-visual-and-graphics-validation).
