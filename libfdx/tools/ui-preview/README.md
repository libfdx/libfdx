# UI Preview

UI Preview opens an existing UI Kit composition in a standalone desktop window.
A design root shows hover, press, and temporary slider drag feedback while leaving
bound state and input callbacks untouched.

## Open a composition

Put a typed preview entry in your existing desktop module. Its factory can
return a `UiContent` instance or a composition method reference.

```java
public final class MenuPreview implements UiPreviewEntry {
    @Override
    public UiContent create(UiPreviewContext context) {
        return new MainMenu();
    }

}
```

Give the entry a non-private zero-argument constructor and inherit its
`main(String[])` method. Use `create` for sample state and assets. The plugin
puts the preview API on `compileOnly`, so game code can use the underlying
composition independently of the entry.

Enable discovery and source watching in the same module's Gradle build:

```kotlin
libfdx {
    assets(rootProject.layout.projectDirectory.dir("assets"))
    uiPreview {}
}
```

The plugin supplies the `tools_ui_preview` API on `compileOnly` and the SDK,
desktop backend, and default OpenGL provider on the preview runtime classpath.

Run `:yourExistingModule:libfdx_ui_preview_run`. It compiles normal main sources,
opens the preview, and returns while a separate supervisor watches for edits.
Ordinary Java edits compile with a warm JDK compiler and reload application
compositions in the existing window. Registered assets reload without compilation.
Gradle handles changes that need dependency or generated-output rebuilding.
Main sources/resources in runtime project dependencies are watched too, so shared UI can stay in an
existing core module. Additional inputs can be added with `uiPreview.watch.from(...)`.
Enable editor autosave to refresh while you edit. The watcher sees each change
when the editor writes it to disk.
The project must have its normal Gradle wrapper jar and a Java 25 toolchain
(configurable through `uiPreview.javaLanguageVersion`).

Close the preview window or run `:yourExistingModule:libfdx_ui_preview_stop` to
stop the supervisor and its preview processes. Only one preview supervisor runs
per module. Run/stop tasks require Gradle's normal execution mode; they do not
support the configuration cache.

## Preview hierarchy

The watcher discovers concrete `UiPreviewEntry` classes in compiled project
outputs, including project dependency jars. The window docks a **Preview hierarchy**
and preview controls to the left of the canvas, including for a single target. Click a
chevron to expand or collapse its branch. Click a label to preview that target.
The active preview stays open when its branch collapses. F4 and Shift+F4 move
through the visible targets. The hierarchy and canvas have independent UI
roots. Captures contain only the canvas.

The first entry alphabetically opens by default. To choose a starting entry,
pass its fully qualified class name with
`-Dlibfdx.uiPreview.target=com.example.desktop.MenuPreview`. Override
`previewChildren()` to declare named child factories. Nest further
`UiPreviewTarget` children under any target. Use unique, stable IDs within the
entry. Declare factories without loading their resources. The runner calls only
the selected factory and creates a fresh canvas session when switching.
Entries with matching window, graphics, asset and capture settings share the
current window. Their themes, UI scale and input mode apply to each canvas.
Refresh restores the same entry and child ID while they remain available.

The **Debug lines** checkbox in the dock shows UI Kit layout bounds over the
canvas and dock. It stays selected across target changes and F5 resets. Watched
source refreshes restore the setting from `debug-lines` in the module's
`build/ui-preview/session` folder. This overlay does not alter the previewed
component's state. Turning it off restores the component's own debug setting.

Projects with a custom preview host can still set `uiPreview.mainClass` to
launch an ordinary Java main that calls `UiPreview.open` or `UiPreview.openTargets`.

## Refresh and failure behavior

The initial `libfdx_ui_preview_prepare` build resolves dependencies, compiles the
project, and records its Java compiler options and classpaths. The supervisor
snapshots mutable runtime files and discovers entries from class-file metadata.
Quick compilation writes only private preview outputs; it does not overwrite
Gradle class directories or jars. Compiler-only mutable inputs are copied too,
so cached compiler archive handles do not lock normal build outputs.

For known main Java files with supported compiler settings, a persistent JDK
compiler applies the prepared source/target or release options and encoding.
Method bodies, constructors, styles, and private fields/helpers can change.
Application classes load through a fresh owned classloader that shares the SDK's
API types. A candidate canvas loads its resources and renders before replacing
its predecessor. The window, selected target, hierarchy, Debug lines setting,
and pause controls remain in place. The canvas gets fresh application state and
resources, including new constructor and static initialization values. Retired
roots, assets, owned resources, and classloaders close after replacement.

Automatic refresh preserves the canvas's horizontal and vertical scroll offsets,
including nested scroll areas and virtual lists. Matching uses UI node identity;
replacing or moving a scroll container can change its identity and reset it.
Offsets apply before the updated frame is shown and
clamp to the new content size. Automatic worker replacements carry these offsets
for the same entry and target too. F5 and selecting another target still start a
fresh canvas at its default scroll position. Other application state is recreated.
Custom hosts using `UiPreview.attach` own their view state and its restoration.

Files under registered asset roots reload the composition directly. Changes to
shared SDK/framework classes can use direct compilation followed by a replacement
worker, because those API types remain shared with the host. Changed window-level
settings, class-loading incompatibilities, and custom hosts using `UiPreview.attach`
also use replacement workers.
The previous worker remains open until the replacement draws its first frame.

New/deleted Java source files, removed class outputs, visible declarations,
inlined constants, build settings, ordinary resources, annotation processors,
modular/multi-release outputs, and unsupported compiler options use a finite
Gradle prepare build. This recompiles dependents and regenerates outputs instead of mixing old consumers
with a changed API. Build properties and offline mode from the launch invocation
are preserved; `uiPreview.buildArguments` supplies additional Gradle arguments.
Set `uiPreview.fastReload.set(false)` to use Gradle and worker replacement for
every edit.

The previous canvas stays available during compilation and resource preparation.
Compile or composition errors retain it; fix the source and save again to retry.
Edits during compilation are coalesced and the latest files are compiled before
publication. The dock reports progress, failures, and the detected-edit-to-frame
refresh time. Full diagnostics stay in `build.log` or the worker's `preview.log`.
F5 resets the current canvas without compiling.

Native file notifications trigger source scans, with a periodic recovery scan.
Scans compare file paths, sizes, and modification times. Files changed by tools
that preserve both size and timestamp need an updated timestamp or another
watched edit. Generated build output directories are excluded from the default
source roots. Custom inputs that cause recursive rebuilds should not be added
to `watch`.

Logs and process status live under the owning module's `build/ui-preview/session`:
`watch.log`, `build.log`, `status.properties`, and each worker's `preview.log`.
Status includes the successful `refresh` count, `refreshMode` (`java-reload`,
`java-restart`, `assets`, or `gradle`), `compileMillis`, and `refreshMillis`.
The latest failed worker snapshot is retained for diagnosis; retired successful
snapshots are removed after their processes exit. If the running preview itself
crashes after startup, the supervisor waits for another edit to retry.

## Host controls and resources

`UiPreviewConfig` configures dimensions, title, theme, UI scale, asset roots,
and an explicit graphics provider. The default is desktop OpenGL. A custom
provider's runtime dependencies can be supplied through
`uiPreview.runtimeClasspath.from(...)`; they must include its desktop platform
implementation and native libraries.

Use the context overload for assets and disposable session resources:

```java
UiPreview.open(new UiPreviewConfig(960, 640), context -> {
    InventoryPanel panel = new InventoryPanel();
    context.assets().load(AssetDescriptor.of("icons.png", Texture.class))
        .future().onSuccess(texture -> panel.icon(new TextureRegion(texture)));
    return panel;
});
```

The runner creates the window, input router, and `UiRoot`, advances session asset
loads before composition/rendering, and calls setup once per start/reset on the
application thread. Use `context.root()` to configure the host root's theme or
layout. The host installs the returned composition and owns the root. Factories
should construct fresh state and register disposable resources with
`context.own(resource)`. On reset or close, the runner
disposes the root, then explicitly owned resources in reverse order, then the
session asset manager. Assets loaded by that manager already belong to it.
Files and graphics are borrowed backend services; the context is invalid after
its session ends. Configured themes and their external resources remain borrowed.
Asset directories remain live filesystem roots; refresh reloads their assets in
the new session.

F5 resets the session, F6 pauses UI delta time, F7 advances one 1/60-second step,
and F8 captures a PNG. Display content scaling is automatic. Use `uiScale` for
an additional multiplier.

Hovering and holding a control show its UI styles or a preview outline when
the theme supplies no matching style. Dragging a slider moves its thumb while
held and restores the bound value on release. Button actions,
checkbox and tab changes, scrolling, text editing, and custom input callbacks
require full interaction. Setup and composition still execute to render the UI.

Use `new UiPreviewConfig(960, 640).interactive(true)` to test normal UI actions,
or `interactive(false)` for a static view. Host shortcuts work in all three modes.

For capture jobs, use `UiPreviewConfig.capture(path).exitAfterFrames(count)`.
The equivalent launch properties are `-Dlibfdx.uiPreview.capture=...` and
`-Dlibfdx.uiPreview.frames=3`; `{generation}` in the capture path expands to the
worker generation. These properties are forwarded by the Gradle run task and
preserved across refreshes. Capture requires provider framebuffer readback.

## Standalone showcase example

The standalone picker discovers `UiKitTestPreview`, which reuses the UI Kit
showcase and its sections. Open the standalone window with:

```powershell
./gradlew :tests:platform:desktop:libfdx_ui_preview_run
```

**UI Kit Test** opens the full showcase. Select one of its child sections to
open that demo on its own. Both the full showcase and isolated demos accept
normal input. Save changes to refresh the selected canvas. Set
`-Dlibfdx.uiPreview.section=Sliders` to start on the isolated Sliders demo.

The watcher also supports a project-owned host through `UiPreview.attach`.
Pass its existing listener and a predicate that reports the first usable frame.
