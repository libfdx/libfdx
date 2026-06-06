# libFDX UI Kit Specification

This document defines the implementation contract for `:libfdx:ui:ui-kit`. It keeps detailed UI rules out of `ARCHITECTURE.md` and `COMMON_API.md` while giving implementation work a stable specification.

## Index

1. [Status And Scope](#1-status-and-scope)
2. [Goals](#2-goals)
3. [Mental Model](#3-mental-model)
4. [Public API Shape](#4-public-api-shape)
5. [Composition And State](#5-composition-and-state)
6. [Layout](#6-layout)
7. [Styling And Ninepatch](#7-styling-and-ninepatch)
8. [Animation And Transitions](#8-animation-and-transitions)
9. [Input, Focus, And Events](#9-input-focus-and-events)
10. [Rendering](#10-rendering)
11. [Lifecycle](#11-lifecycle)
12. [Module And Dependency Rules](#12-module-and-dependency-rules)
13. [Feature Coverage Checklist](#13-feature-coverage-checklist)
14. [Implementation Order](#14-implementation-order)

## 1. Status And Scope

`ui-kit` is the built-in libfdx UI solution. It must be easy to learn, portable across backends, and suitable for game UI, menus, HUDs, settings screens, and in-game overlays.

The model is a Compose-inspired declarative API over a retained runtime. User code describes the UI from state. The runtime reconciles that description into persistent nodes, owns layout and input dispatch, and renders through libfdx graphics modules.

## 2. Goals

- Plain Java authoring API. It must not require annotations, reflection-based user binding, a compiler plugin, or generated source.
- Declarative authoring so common UI reads top-down and follows game state.
- Retained runtime so focus, hover, pressed state, text editing, scroll position, animations, and transitions persist naturally.
- Small layout vocabulary: row, column, stack, grid, scroll, spacer, and box/panel primitives.
- First-class ninepatch support for scalable game UI panels and buttons.
- First-class animation support for hover, press, focus, visibility, screen transitions, layout changes, and custom animated values.
- Theme/skin loading is part of the UI kit, and programmatic styling is the baseline authoring path.
- Explicit user-created root. `Fdx` must not grow a `ui()` accessor for `ui-kit`.

## 3. Mental Model

The API looks simple like immediate UI, but it is not immediate-mode internally.

```java
UiRoot root = toolkit.root(fdx.displays().main(), fdx.graphics().main());

UiBooleanState showStats = Ui.state(false);
UiIntState selectedSlot = Ui.state(0);
UiIntState activeTab = Ui.state(0);

root.setContent(ui -> {
    ui.column(Ui.modifier().fill().padding(16).gap(8), column -> {
        column.text("Inventory", Ui.modifier().style("title"));
        column.tabs(activeTab, "Items", "Stats", "Log");
        column.row(Ui.modifier().gap(6), row -> {
            row.button("Equip", () -> inventory.equip(selectedSlot.get()));
            row.checkbox("Show stats", showStats);
        });
        if (showStats.get()) {
            column.text("Damage: " + player.damage());
        }
    });
});
```

The `setContent` lambda is declarative content. It can be re-run when state changes. The runtime reuses retained nodes where identity is stable and updates layout, style, callbacks, and children.

## 4. Public API Shape

All public `ui-kit` types must use the `Ui` prefix. This avoids collisions with names such as `Button`, `Label`, `Style`, or `Table`.

Public surface:

| Type | Purpose |
| --- | --- |
| `UiToolkit` | Factory for roots, themes, shared UI resources, and default renderer setup. |
| `UiRoot` | Owns a retained UI tree, composition, input dispatch, focus, layout, rendering, and disposal. |
| `UiScope` | Declarative builder passed to content lambdas. |
| `UiModifier` | Immutable chain of layout, drawing, input, and behavior options. |
| `UiState<T>`, `UiBooleanState`, `UiIntState`, `UiFloatState`, `UiLongState`, `UiDoubleState` | Explicit observable state used by UI content. Primitive state uses dedicated classes to avoid boxed primitive wrappers. |
| `UiContent` | Functional interface for root content. |
| `UiNode` | Retained node handle for advanced cases, debugging, and custom widgets. |
| `UiTheme` | Defaults for colors, fonts, drawables, spacing, and widget styles. |
| `UiStyle` | Widget style values. |
| `UiDrawable` | Renderable background/foreground value, including colors, images, and ninepatches. |
| `UiNinePatch` | Ninepatch metadata and drawable construction. |
| `UiAnimationSpec`, `UiEasing` | Animation timing, delay, repeat, and interpolation descriptors. |
| `UiTransition`, `UiAnimatable<T>`, `UiFloatAnimatable` | Retained state-driven transitions and custom animated values. Primitive scalar animation uses `UiFloatAnimatable`. |
| `UiTextStyle`, `UiFont` | Text styling and font selection values. |
| `UiLayer`, `UiPopup`, `UiModal`, `UiTooltip` | Overlay, modal, popup, and tooltip support. |
| `UiWindowState` | Retained position, size, and z-order for movable and resizable UI windows. |
| `UiFocusScope`, `UiNavigation` | Keyboard and gamepad focus/navigation rules. |
| `UiListState`, `UiScrollState` | Retained state for scrollable and virtualized content. |
| `UiGesture`, `UiDrag`, `UiDrop` | Gesture, drag source, and drop target support. |
| `UiTextInputFilter` | Text-field input filter for string, integer, and float entry. |
| `UiTextAreaOptions` | Text-area sizing policy, including fixed-height and bounded auto-grow behavior. |
| `UiSize`, `UiInsets`, `UiAlign` | Layout value types. |

Widget methods live mostly on `UiScope` instead of requiring users to instantiate widget classes:

```java
ui.text("Settings");
ui.button("Apply", this::applySettings);
ui.row(Ui.modifier().gap(6), row -> {
    row.checkbox(Ui.modifier().semanticLabel("Fullscreen").size(18, 18), fullscreenState);
    row.text("Fullscreen");
});
ui.slider(volumeState, 0.0f, 1.0f);
ui.progressBar(volumeState, 0.0f, 1.0f);
ui.tabs(activeTab, "Overview", "Audio", "Video");
ui.textField(playerNameState);
ui.textArea(messageState, UiTextAreaOptions.defaults().autoGrow(true).maxHeight(180));
ui.intField(scoreState);
ui.floatField(speedState);
ui.image(textureRegion);
ui.scrollView(Ui.modifier().fill(), scroll -> { ... });
ui.window("Inventory", inventoryWindowState, window -> { ... });
```

Custom widgets are supported without subclassing a large actor hierarchy:

```java
ui.custom("health-bar", Ui.modifier().height(24), ctx -> {
    ctx.measure((constraints) -> constraints.size(constraints.maxWidth(), 24));
    ctx.draw((draw, bounds) -> draw.rect(bounds, healthColor));
});
```

## 5. Composition And State

State is explicit and Java-friendly:

```java
UiState<String> name = Ui.state("Player");
UiBooleanState enabled = Ui.state(true);
```

Calling `set` marks the owning root dirty when the state is observed by that root:

```java
enabled.set(!enabled.get());
```

Rules:

- State ownership is explicit. There is no hidden annotation model.
- The runtime may track state reads during composition, but it must not use reflection.
- Primitive state uses dedicated classes such as `UiBooleanState`, `UiIntState`, `UiFloatState`, `UiLongState`, and `UiDoubleState`, never boxed primitive wrappers inside `UiState<T>`.
- External game state can be read directly, but the user must call `root.requestCompose()` when changing non-`UiState` data that affects UI.
- Reconciliation uses call order for simple static trees and explicit keys for dynamic lists.
- Dynamic lists must support stable keys:

```java
ui.items(inventory.items(), item -> item.id(), (row, item) -> {
    row.button(item.name(), () -> selectedSlot.set(item.slot()));
});
```

Internal widget state, such as scroll position or text cursor position, belongs to retained nodes and survives recomposition when keys are stable.

## 6. Layout

Layout is predictable and smaller than CSS.

Core containers:

- `column` lays children vertically.
- `row` lays children horizontally and vertically centers children in the row content height unless a child requests `fillHeight()`.
- `stack` overlays children.
- `grid` lays children in rows/columns.
- `scroll`/`scrollView` clips overflowing content, tracks viewport and content size, and enables scrolling when the parent layout becomes smaller than its children. Pointer dragging empty scroll-view space scrolls the container when no child widget captures the pointer.
- `panel` provides a styled container.
- `window` creates a movable, resizable retained panel backed by `UiWindowState`.
- `spacer` consumes fixed or weighted space.

`UiModifier` covers common constraints:

```java
Ui.modifier()
    .fill()
    .width(240)
    .height(48)
    .minWidth(120)
    .padding(12)
    .margin(4)
    .align(UiAlign.CENTER)
    .weight(1.0f);
```

Table layout is not the default. Form and inventory helpers are built on row, column, stack, grid, and scroll primitives.

Responsive layout requirements:

- safe-area insets for mobile devices, browser cutouts, console overlays, and TV overscan
- density/UI scale support for desktop, mobile, and high-DPI displays
- `UiRoot.uiScale(...)` for explicit DPI/accessibility scaling, with `autoUiScale(true)` available when a backend reports unscaled display units
- anchor/alignment helpers for HUD corners and center overlays
- min/preferred/max sizing for scalable menus and panels
- aspect-ratio helpers for icons, thumbnails, minimaps, and preview panes
- wrapping rows for inventory/item grids and settings lists
- virtualized list/grid support for large inventories, shops, lobbies, save slots, chat history, and server browsers

## 7. Styling And Ninepatch

Styling supports programmatic definitions and skin files. Programmatic styling is the baseline.

```java
UiTheme theme = UiTheme.light()
    .button(UiStyle.button()
        .background(UiDrawable.ninePatch("ui/button.9.png"))
        .padding(10, 6));
```

Ninepatch support is required:

- Load `.9.png` marker borders.
- Support explicit split and padding values for non-marker texture regions.
- Expose stretch splits, content padding, and minimum size.
- Render as nine quads through `g2d`.
- Allow theme styles to use ninepatches for panels, buttons, text fields, tabs, and scrollbars.

Ninepatch influences preferred/minimum size through content padding and fixed corners, but it is not the layout system.

### 7.1. Text, Fonts, And Localization

Game UI depends heavily on text. The baseline text implementation uses `graphics:g2d` bitmap font layout and rendering. It supports AngelCode BMFont-style `.fnt` bitmap fonts, direct `BitmapFont` instances, and `.ttf`/`.otf` vector font assets rasterized into bitmap atlases through the FreeType path exposed by `g2d`. Family-font descriptors exist, but default system-family rasterization must fail clearly until a backend-specific provider exists. Generated font atlases should be created at the target UI scale, or oversampled, so DPI scaling does not blur text by stretching a small source atlas.

Required text features:

- `UiTextStyle` for font, size, color, line height, alignment, wrapping, ellipsis, and shadow/outline.
- explicit fallback font descriptors through `UiFont`
- `UiFont.bitmap(...)`, `UiFont.bitmapFile(...)`, and `UiFont.freeType(...)` for explicit bitmap and FreeType-backed asset selection
- localization-friendly text values so strings come from game resource bundles instead of being hard-coded
- dynamic text updates without recreating unrelated UI nodes
- multiline text and paragraph layout
- text measurement usable by layout before rendering
- text field cursor, pointer hit selection, drag selection, root-local copy/cut/paste shortcuts, password masking, and validation states
- text area touch behavior supports tap-to-place-caret and drag scrolling for multiline content
- text field input filtering through `UiTextInputFilter`, plus `intField(...)` and `floatField(...)` helpers for numeric input
- platform text-input session requests through `Input.showTextInput(...)`, `Input.updateTextInput(...)`, and `Input.hideTextInput()` when an editable text field or text area gains focus, changes text/selection, or loses focus. UI Kit includes focused text bounds in each request and uses the active text-area caret line for multiline input so mobile backends can keep the edited line visible while the soft keyboard is open. Android backends may use a native editor panel for platform-owned cursor, selection, and IME behavior while syncing accepted edits back into UI Kit. Web backends use a DOM editor panel for browser keyboard and IME integration. Platform editor panels are styled through backend configuration or browser defaults, not through UI Kit widget style.
- `textArea(...)` for multiline written text. Text areas own an internal `UiScrollState` when content exceeds the viewport and can use `UiTextAreaOptions` for fixed height or bounded auto-grow between a minimum and maximum height.

Advanced text requirements:

- rich text spans for color, icons, inline images, and emphasis
- bidirectional text and complex shaping when the font/text stack supports it
- IME composition for web, desktop, Android, and mobile targets

## 8. Animation And Transitions

Animation is part of the core `ui-kit` model. Most game UI needs motion for hover feedback, button press feedback, menus, popups, HUD changes, damage indicators, inventory changes, and screen transitions. Animation is not bolted on as a manual per-frame action system.

Animation state belongs to retained nodes. Recomposition updates animation targets; the retained runtime advances animated values over time and renders the current interpolated state.

Authoring examples:

```java
UiBooleanState inventoryOpen = Ui.state(false);

ui.animatedVisibility(
    inventoryOpen.get(),
    Ui.animation().durationMillis(180).easing(UiEasing.outCubic()).fade().slideY(-12),
    panel -> {
        panel.panel(Ui.modifier().width(360), content -> {
            content.text("Inventory");
        });
    });
```

```java
ui.button("Play",
    Ui.modifier()
        .transition("hover", Ui.transition()
            .durationMillis(90)
            .alpha(0.86f, 1.0f)
            .scale(0.98f, 1.0f)),
    this::play);
```

```java
UiFloatAnimatable dangerPulse = ui.floatAnimatable("danger-pulse", 0.0f);
if (player.lowHealth()) {
    dangerPulse.animateTo(1.0f, Ui.animation().durationMillis(220).repeatReverse());
} else {
    dangerPulse.animateTo(0.0f, Ui.animation().durationMillis(120));
}
ui.text("Low health", Ui.modifier().alpha(0.4f + dangerPulse.get() * 0.6f));
```

```java
ui.panel(Ui.modifier().animateContentSize(), panel -> {
    panel.text("Options");
    if (advanced.get()) {
        panel.slider(volume, 0.0f, 1.0f);
    }
});
```

Required animation targets:

- `alpha`
- `color`
- `background` drawable state where interpolation is possible
- `offsetX` / `offsetY`
- `scaleX` / `scaleY`
- `rotation`
- `width` / `height`
- `padding`
- scroll position

Required built-in transition helpers:

- `animatedVisibility` keeps entering and exiting content alive while its fade and slide animation is running.
- `animateContentSize` animates size changes caused by recomposition or layout.
- `animateItemPlacement` animates keyed list/grid item movement.
- `screenTransition` handles menus and modal screen changes.
- state styles for hover, pressed, disabled, focused, selected, checked, and invalid.

Rules:

- `UiRoot.update(deltaTime)` advances animations before layout/render.
- Animations must be deterministic from delta time and must not depend on wall-clock time directly.
- Recomposition changes animation targets and does not recreate running animation state when keys are stable.
- Removing a node cancels its animations unless an exit transition is active.
- Layout-affecting animation is allowed, but the runtime must bound re-layout to dirty subtrees where feasible.
- Render-only animation such as alpha, tint, offset, and scale must avoid full layout when possible.
- Animation descriptors are immutable or treated as immutable after use.
- Common animation paths must avoid per-frame allocation.
- Themes expose motion tokens for common durations and easing curves, such as fast hover feedback, standard panel open/close, and slow screen transitions.
- A root-level animation scale supports paused, disabled, slow-motion, and fast-forward behavior for debugging, accessibility, and deterministic tests.

Required easing set:

- linear
- in/out quadratic
- in/out cubic
- out back
- out elastic
- step/end for instant state changes

Duration/easing animation is required. Spring animation is an extension point and is not required for the baseline implementation because duration/easing animation is simpler to make deterministic across desktop, web, Android, and native runtimes.

## 9. Input, Focus, And Events

`UiRoot` owns input routing:

- hit testing
- hover and pressed state
- pointer capture while dragging, including slider drags that continue until pointer-up after leaving widget bounds
- keyboard focus
- text input focus
- gamepad/keyboard navigation
- focus scopes and default focused elements
- pointer, mouse, touch, wheel, and pen input where the backend exposes them
- tap, double tap, long press, drag, fling, and pinch gesture hooks
- drag source and drop target routing
- event bubbling/capture only when needed

Common widgets expose simple callbacks or state bindings:

```java
ui.button("Back", screenStack::pop);
ui.slider(volume, 0.0f, 1.0f, audio::setVolume);
ui.tabs(activeTab, "Inventory", "Map", "Quests");
```

Low-level event listeners exist for custom widgets, but they are not the normal way to build UI.

Navigation and accessibility requirements:

- keyboard tab/shift-tab and arrow navigation
- gamepad/TV navigation with explicit neighbor overrides for non-grid UI
- focus traps for modal dialogs
- disabled/read-only/hidden states must affect focus and hit testing correctly
- semantic labels for buttons, checkboxes, sliders, progress bars, tabs, text fields, and text areas
- checkbox labels can be composed explicitly from an icon-only checkbox and separate text when label spacing, styling, or placement needs finer control
- icon-only checkboxes default to `20x20` logical units and can use `UiModifier.size(...)` for a custom visual and hit size
- UI scale and animation-scale controls for accessibility and testing
- sound/haptic hooks exist at the widget/theme level, but actual audio/haptics remain outside `ui-kit`

### 9.1. Layers, Popups, And Screen Flow

Games commonly need multiple UI layers at the same time: HUD, pause menu, modal confirmation, tooltip, toast, controller prompt, and debug overlay. `UiRoot` owns ordered layers so these do not require separate roots by default.

Required layer features:

- base content layer
- overlay layers for HUDs and screen decorations
- modal layers that block input behind them
- popup and tooltip positioning relative to anchors
- toast/notification layer
- focus restoration when a popup/modal closes
- input blocking rules per layer
- z-order and draw order that match hit testing
- movable windows keep retained z-order and move to the front when pressed, dragged, or resized
- popup layer containers are transparent by default; popup content should draw its own panel background and may use `animatedVisibility` for panel motion; popups pass input through unless `UiPopup.blockingInput(true)` is set, while modal layers own scrims and block input by default
- multiple popups or modals in the same layer stack in declaration order; later declarations render and hit-test above earlier declarations

Authoring shape:

```java
ui.modal(confirmDelete.get(), modal -> {
    modal.panel(Ui.modifier().width(320), panel -> {
        panel.text("Delete save?");
        panel.row(row -> {
            row.button("Cancel", () -> confirmDelete.set(false));
            row.button("Delete", this::deleteSave);
        });
    });
});

ui.tooltip(Ui.tooltip("Help").delayMillis(500), tooltip -> {
    tooltip.panel(Ui.modifier().width(220), panel -> {
        panel.text("Changes are saved automatically.");
    });
});
```

Tooltip targets use the hovered node text or semantic label by default. Use
`tooltipTarget(...)` when the tooltip key should be explicit or when the hovered
target has no matching visible text:

```java
ui.textField(Ui.modifier()
        .validationId("profile.name")
        .tooltipTarget("profile.name")
        .semanticLabel("Profile name"), name, UiTextInputFilter.STRING);

ui.tooltip(Ui.tooltip("profile.name").delayMillis(2000), tooltip -> {
    tooltip.panel(Ui.modifier().width(220), panel -> {
        panel.text("Shown after a 2 second hover.");
    });
});
```

## 10. Rendering

Rendering is backend-neutral and uses libfdx rendering helpers:

- Use `graphics:g2d` for batched rectangles, images, ninepatches, and text.
- Use `graphics/api` only where lower-level render target or clipping support is required.
- Do not depend on GL, Vulkan, WGPU, desktop, Android, or web provider classes.
- Keep render state changes predictable and batched where possible.
- Support clipping for scroll panes, text fields, and text areas.

Text rendering uses bitmap/font support available in `g2d` as the baseline. Rich text, markup, shaping, and international text handling are advanced text requirements.

Rendering requirements for complete game UI:

- ordered layers and z-order
- clipping/scissor for scroll panes, text fields, text areas, and masked panels
- opacity groups for animated panels and modal fades
- render transforms for scale, rotation, and offset animations
- ninepatch batching
- icon/image rendering from texture regions and atlases
- cursor/caret and selection rendering for text fields and text areas
- `UiRoot.debugLines(true)` overlays bounds, hit targets, focus, window title bars, and resize handles for screenshots and interactive layout debugging. Edge-touching outlines are inset by one physical pixel so full-screen bounds are not clipped by the render edge.

## 11. Lifecycle

Expected application shape:

```java
public final class MenuScreen implements ApplicationListener {
    private Fdx fdx;
    private UiRoot ui;

    public void create(Fdx fdx) {
        this.fdx = fdx;
        UiToolkit toolkit = new UiToolkit(fdx.files());
        ui = toolkit.root(fdx.displays().main(), fdx.graphics().main());
        ui.setContent(this::content);
    }

    private void content(UiScope ui) {
        ui.column(Ui.modifier().fill().padding(16), column -> {
            column.text("Main Menu");
            column.button("Play", this::play);
        });
    }

    public void resize(int width, int height) {
        ui.resize(width, height);
    }

    public void render() {
        ui.update(fdx.app().deltaTime());
        ui.render();
    }

    public void dispose() {
        ui.dispose();
    }
}
```

Constructor signatures are implementation details. The lifecycle rule is stable: the app creates and owns the UI root explicitly.

## 12. Module And Dependency Rules

Module:

```text
:libfdx:ui:ui-kit
```

Artifact:

```text
io.github.libfdx:ui_kit
```

Primary package:

```text
io.github.libfdx.ui
```

Rules:

- Keep `ui-kit` as one user-facing module. Do not split it into scene graph, layout, and widget artifacts.
- `ui-kit` may depend on `runtime/fdx/core`, display/application runtime APIs, `graphics/api`, `graphics:g2d`, and asset modules needed for fonts, skins, and ninepatches.
- `ui-kit` must not depend on backend modules or provider-specific graphics modules.
- `ui-kit` is not exposed from `Fdx`; users create `UiToolkit`/`UiRoot` explicitly.

## 13. Feature Coverage Checklist

The UI kit is complete enough for normal game UI when it supports these use cases without custom engine-side workarounds:

- main menu, pause menu, options/settings, credits, and confirmation dialogs
- HUD overlays, health bars, cooldowns, minimaps, quest trackers, and notifications
- inventory grids, equipment screens, shops, crafting screens, save/load lists, and server/browser lists
- modal dialogs, popups, dropdowns, tooltips, toasts, tabs, accordions, and collapsible sections
- mouse, touch, keyboard, and gamepad navigation
- animated buttons, panels, screen transitions, list item movement, visibility changes, and HUD value changes
- scroll panes, virtualized large lists/grids, clipping, and item selection
- text labels, multiline paragraphs, localized strings, text fields, text areas, password fields, validation messages, and clipboard behavior
- theme/skin styling, ninepatch panels/buttons/fields, icons, colors, fonts, spacing, and motion tokens
- drag/drop for inventory, equipment, skill bars, and editor-like game screens
- safe areas, UI scaling, high-DPI displays, mobile screens, TV/console overscan, and browser resizing
- deterministic tests for layout, animation, focus, hit testing, and widget state

Non-goals:

- full HTML/CSS compatibility
- full desktop application widget coverage such as native menus, tree tables, dock panels, or spreadsheet controls
- full accessibility parity with native platform UI toolkits
- complex text shaping beyond the capabilities of the active text/font stack

## 14. Implementation Order

1. Create `:libfdx:ui:ui-kit` with public type skeletons, module metadata, and a basic sample.
2. Implement `UiRoot`, retained nodes, composition, explicit `UiState`, and dirty recomposition.
3. Implement row, column, stack, spacer, panel, and basic modifiers.
4. Render rectangles, images, and text through `g2d`.
5. Add pointer input, hit testing, hover, pressed state, and button callbacks.
6. Add the animation clock, `UiAnimationSpec`, `UiEasing`, `UiAnimatable<T>`, and render-only animated modifiers for alpha, color, offset, and scale.
7. Add `animatedVisibility`, `animateContentSize`, and keyed placement animation.
8. Add layer, modal, popup, tooltip, and toast infrastructure.
9. Add theme/style defaults and ninepatch loading/rendering.
10. Add checkbox, slider, progress bar, tabs, scroll pane, text field, text area, focus, keyboard text input, platform text-input session requests, and gamepad navigation.
11. Add dynamic keyed lists, virtualized lists/grids, drag/drop, screen transitions, and theme motion tokens.
12. Add text localization hooks, text validation, platform clipboard integration, and IME composition where backends support it.
13. Add tests and samples for desktop, web, Android, and native variants.
