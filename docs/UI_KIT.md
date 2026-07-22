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

Scroll views retain scroll state and clip overflow. Dynamic lists preserve
keyed item identity. Windows retain position, size, and z-order. Safe-area
insets and parent constraints keep content inside the active display.

`UiRoot.uiScale(...)` scales logical units. `autoUiScale(true)` multiplies that
scale by `Display.contentScale()`; applications should not apply the same
display scaling twice.

Themes provide reusable colors, spacing, fonts, drawables, widget states, and
motion values. `UiDrawable` supports colors, textures/regions, and nine-patch
drawing. Nine-patch splits and content padding affect both measurement and
drawing rather than creating another layout system.

## Text And Input

Text measurement/rendering uses bitmap-font data. FreeType sources are
rasterized into cached atlases during loading, not per frame. Generate or load
fonts at a suitable effective UI scale to avoid blurry upscaling.

Text fields retain cursor, selection, filtering, masking, validation, and
scroll state. Focused editors use the platform text-input session exposed by
`Input`; native/DOM editor presentation remains backend-owned.

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
renderer, font, clipping, or input changes require the affected provider or
platform scope described in
[Contributing](../CONTRIBUTING.md#visual-and-graphics-changes).

Use stable `validationId` values when authoring reusable flows with the
[scenario validator](../libfdx/extensions/scenario_validator/README.md#ui-selection).
