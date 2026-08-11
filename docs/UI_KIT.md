# libFDX UI Kit

UI Kit is a plain-Java game UI toolkit with declarative content and a retained
runtime. Application code describes the current UI while `UiRoot` preserves
nodes, focus, hover/press state, text editing, scrolling, windows, and
animations.

Exact declarations belong to source/Javadocs. Cross-module lifecycle and
performance rules are in [Common API](COMMON_API.md).

## Ownership And Lifecycle

Application code creates and owns each UI root. UI is not a service on `Fdx`.
Referenced textures, fonts, and themes remain application-owned unless an
explicit resource owner accepts them.

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

Disposing a root releases its owned runtime/render resources but not arbitrary
borrowed game assets.

## Composition And State

`setContent` installs a content lambda. The runtime executes it again when
observed UI state changes and reconciles the result with retained nodes.

- Static trees use call order for identity; dynamic collections use stable
  keys.
- Internal widget state survives while identity remains stable.
- Removing a node releases retained state after any exit transition.
- External game-state changes call `requestCompose()` when no observed UI state
  triggered recomposition.

Reference values use `UiState<T>`. Primitive values use dedicated
`UiBooleanState`, `UiIntState`, `UiFloatState`, `UiLongState`, and
`UiDoubleState`; do not use boxed primitive state such as `UiState<Boolean>`.
Reusable state is created outside content lambdas unless a stable retaining
mechanism owns it.

UI Kit uses ordinary Java: no annotations, reflection, generated bindings, or
compiler plugin is required.

## Layout, Style, And Drawing

Columns, rows, stacks, grids, scroll views, panels, windows, and spacers form a
small constraint-based layout vocabulary. Modifiers express size constraints,
padding/gaps, weight, alignment, aspect ratio, offset, clipping, drawing,
input, and semantics.

Rows and columns shrink fixed and weighted children down to their effective
minimums when their requested sizes do not fit. If even the combined minimums
cannot fit, the remaining space is distributed proportionally without
producing negative sizes or escaping the parent. Scroll containers are the
explicit exception: they retain overflow so it remains reachable. Dynamic
lists preserve keyed item identity. Windows retain position, size, and
z-order. Safe-area insets and parent constraints keep content inside the active
display.

`UiRoot` automatically derives its render scale from the framebuffer-to-logical
display-size ratio, so platform DPI scaling works without application setup while
layout and pointer coordinates remain in logical display units. This also avoids
applying Windows bitmap scaling twice when the framebuffer is already expressed in
logical pixels. `uiScale(...)` applies an additional application-selected scale.
Applications that already convert UI units to framebuffer pixels can opt out with
`autoUiScale(false)`. The root observes live logical-size, framebuffer-size, and
render-scale changes, including moving a desktop window between monitors, and
relayouts even when the platform does not emit a logical window resize.

Themes provide reusable colors, spacing, fonts, drawables, widget states, and
motion values. `UiDrawable` supports colors, textures/regions, and nine-patch
drawing. Nine-patch splits and content padding affect both measurement and
drawing rather than creating another layout system.

Named theme styles are appropriate for reusable visual roles. A one-off
element can receive a complete `UiStyle` directly:

```java
UiStyle warning = UiStyle.button()
    .background(UiDrawable.color(UiColor.rgba8888(0xb6378cff)))
    .foreground(UiDrawable.color(UiColor.rgba8888(0xffe66dff)));

ui.button("Delete", Ui.modifier().style(warning), this::delete);
```

The inline style takes precedence over the widget's named/default theme style.
Applying a named style later replaces it. Built-in control rendering uses each
style's background, foreground, text, padding, state variants, and minimum
size, so a theme does not need renderer-specific color constants.

### Custom Surfaces

`UiScope.custom(...)` supports retained, provider-neutral custom surfaces.
`UiCustomContext` can install measurement, drawing, and input callbacks without
introducing a dependency on a platform UI system:

```java
private final UiPath connection = new UiPath();

ui.custom("node-canvas",
    Ui.modifier().fill().focusable(true).clip(),
    custom -> {
        custom.draw((draw, bounds) -> {
            connection.clear()
                .moveTo(bounds.x() + 40, bounds.y() + 80)
                .cubicTo(
                    bounds.x() + 140, bounds.y() + 80,
                    bounds.right() - 140, bounds.bottom() - 80,
                    bounds.right() - 40, bounds.bottom() - 80);
            draw.path(connection, 3, UiColor.rgba8888(0x7dd3fcff));
        });
        custom.input(canvasInput);
    });
```

`UiDrawContext` draws rectangles, images, text, lines, and retained `UiPath`
line/quadratic/cubic segments in UI-root coordinates. `UiModifier.clip()`
clips drawing, descendants, and hit testing to the node bounds. Build and retain
paths outside the draw callback when practical; a pre-sized or warmed path can
be cleared and rebuilt without steady-state allocation.

Interactive surfaces implement `UiSurfaceInput`. A pointer callback returns
`CAPTURE` to keep receiving that pointer outside the node, `RELEASE` to end
capture, `HANDLED`, or `IGNORED`. Pointer-up ends capture automatically, and
node removal/root disposal delivers `CANCEL`. A focusable surface receives
key-down, text-input, and focus-change callbacks. `UiPointerEvent` is reused by
the root and is valid only during its callback; handlers must copy any values
they need later rather than retaining the event object.

The optional shader-graph editor is one consumer of custom surfaces. UI Kit
provides node-canvas editing convenience only; graph documents, compilation,
runtime loading, and direct Java authoring remain headless. See
[Shaders](SHADERS.md#shader-graph-boundary) for that ownership boundary.

## Widgets

Containers and text can be combined with buttons, checkboxes, toggle switches,
radio-button groups, sliders, determinate progress bars, indeterminate loading
bars and spinners, dividers, tabs, text fields, multiline text areas, images,
disclosure/collapse panels, virtual lists, popups, tooltips, modals, and movable/resizable
windows.

Switches and collapse bars use `UiBooleanState`. Radio buttons share a
`UiIntState` and select their declared value. A radio group contributes one
Tab stop; arrow keys move and select within the group. Collapse headers are
full-row controls operated by click, Enter, or Space, and their bodies are
composed only while expanded. Loading indicators use `UiRoot` elapsed time and
therefore respect deterministic update and animation timing.

## Text And Input

Text measurement/rendering uses bitmap-font data. FreeType sources are
rasterized into cached atlases during loading, not per frame. Generate or load
fonts at a suitable effective UI scale to avoid blurry upscaling.

UI Kit bundles one licensed TrueType font and uses it when a text style does not
select another font. Applications can reuse that same resource at another size
without copying it into their own assets:

```java
UiFont compact = UiFonts.defaultFont(13);
```

The shared resource lives in the published UI-kit artifact under the reserved
`libfdx-assets/` namespace. Desktop classpath loading and libFDX web/native
packaging expose that namespace through the normal internal file system. The
font license is shipped beside the font. Backends without runtime FreeType
support continue to use UI Kit's built-in bitmap fallback.

Text measurement, wrapping, truncation, hit testing, selection, caret movement,
insertion, backspace, and delete operate on Unicode code points. Supplementary
characters such as emoji are never split into isolated UTF-16 surrogates.
FreeType atlases remain finite: the selected font must contain the glyph and
the application must include it in the configured character set. The
convenience method below retains the normal localized Latin set:

```java
String emoji = new String(Character.toChars(0x1f600));
UiFont font = UiFont.freeType("fonts/ui.ttf", 18)
    .addCharacters(emoji);
```

The current atlas path renders a font's outline/monochrome glyph. It does not
promise color-font emoji layers.

Text fields retain cursor, selection, filtering, masking, validation, and
scroll state. Focused editors use the platform text-input session exposed by
`Input`; native/DOM editor presentation remains backend-owned.
Single-line fields can provide a submit action through the
`textField(modifier, state, inputFilter, submitAction)` overload; Enter invokes
that action while multiline text areas continue to insert a newline.
`UiTextAreaOptions.readOnly(true)` keeps output text scrollable and selectable
for copying without opening a platform editor or allowing mutations. Ctrl+C,
Ctrl+X, and Ctrl+V use `Input.clipboard()`.

Clipboard behavior is backend-owned:

- JVM desktop uses the operating-system clipboard, with GLFW available for a
  headless desktop process. It retains the most recent local value if another
  process temporarily prevents system access.
- Desktop C uses the GLFW system clipboard with explicit UTF-8 conversion,
  bounded access retries, and the same last-value fallback during contention.
- Android uses `ClipboardManager` and preserves the last local value when
  lifecycle or privacy restrictions temporarily deny clipboard access.
- Web mirrors programmatic writes to `navigator.clipboard` and keeps an
  immediate synchronous cache. Browser reads remain asynchronous and
  permission-gated; the backend's native DOM editor is the authoritative
  interactive copy/paste path.
- iOS C and PSP currently use `MemoryClipboard`; PSP has no general platform
  text clipboard. Applications on a backend without a system bridge still get
  deterministic in-process copy/paste.

Multiline text, caret, hit testing, and selection highlights all use the same
resolved font line height so their geometry remains aligned after scrolling. A
primary-button double-click or touch double-tap selects the text unit under the
pointer in the shared UI Kit input path. Unicode letters and combining marks
remain in one word; whitespace runs are selected together; punctuation is
selected independently.

`UiRoot` owns hit testing and event routing for pointer/touch, wheel scrolling,
keyboard/text focus, navigation, gestures, and drag/drop. Pointer capture keeps
a drag active until release. Disabled, hidden, modal, and read-only state affect
focus and hit testing consistently.

One root normally owns an ordered stack of base content, overlays, popups,
modals, toasts, and debug content. Draw and hit-test order agree. Prefer layers
inside one root unless content truly belongs to another display, graphics
context, or lifecycle.

## Animation And Performance

Animation state belongs to retained nodes. Recomposition changes targets;
`UiRoot.update(deltaTime)` advances deterministic time before layout and
rendering. Stable keys retain animations, and exit transitions may keep removed
content alive until completion.

Layout-affecting animation dirties the smallest practical subtree. Render-only
alpha, tint, offset, scale, and rotation avoid relayout. UI scale and animation
scale support deterministic tests and accessibility behavior.

UI rendering uses common graphics/g2d and does not import provider or backend
classes. Batches, vertex data, glyph layout, hit-test storage, and event objects
are reused in steady state. Normal update/layout/render paths do not allocate
Java objects per frame.

## Validation

Test a focused widget or layout first and inspect a rendered frame. Shared
renderer, font, clipping, or input changes require validation on each affected
provider or platform under the same layout, assets, input, timing, and frame
conditions.

Use stable `validationId` values when authoring reusable flows with the
[scenario validator](../libfdx/extensions/scenario_validator/README.md#ui-selection).

The repository `ui` test exposes every built-in widget in its section list. Its
automation covers constrained bounds, control activation, loading-indicator
sizes, collapse composition, theme changes, Unicode editing, and an emoji
clipboard round trip. The same screen includes Graphite, Porcelain, Cobalt,
Aubergine, and High Contrast theme previews plus inline-style examples.
