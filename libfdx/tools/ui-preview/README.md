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
Saving Java sources, resources, or registered asset files triggers a debounced
Gradle rebuild and replacement preview process. Main sources/resources in
runtime project dependencies are watched too, so shared UI can stay in an
existing core module. Additional inputs can be added with `uiPreview.watch.from(...)`.
Enable editor autosave to refresh while you edit. The watcher sees each change
when the editor writes it to disk.
The project must have its normal Gradle wrapper jar and a Java 25 toolchain
(configurable through `uiPreview.javaLanguageVersion`).

Close the preview window or run `:yourExistingModule:libfdx_ui_preview_stop` to
stop the supervisor and its preview processes. Only one preview supervisor runs
per module. Run/stop tasks require Gradle's normal execution mode; they do not
support the configuration cache.

## Switch targets

The watcher discovers concrete `UiPreviewEntry` classes in compiled project
outputs, including project dependency jars. Each successful rebuild updates
the list. With multiple entries, click **Choose class** in the lower-right
corner and click an entry name. F4 moves forward and Shift+F4 moves back.

The first entry alphabetically opens by default. To choose a starting entry,
pass its fully qualified class name with
`-Dlibfdx.uiPreview.target=com.example.desktop.MenuPreview`. Refresh keeps that
selection while the class remains available. If it was removed, the first
remaining entry opens.

Projects with a custom preview host can still set `uiPreview.mainClass` to
launch an ordinary Java main that calls `UiPreview.open` or `UiPreview.openTargets`.

## Refresh and failure behavior

The supervisor invokes a finite `libfdx_ui_preview_prepare` build, snapshots
mutable classpath outputs, and reads class-file metadata to find entries. The
Java 25 launcher invokes the selected entry's inherited instance main. A worker
executes from its snapshot while later builds update the original class
directories and jars. Switching entries uses the last successful snapshot and
replaces the worker after the new entry draws its first frame.

The previous window remains active during compilation and until a replacement
renders its first UI frame. A compile error, setup failure, or startup timeout
keeps the previous preview; save another edit after fixing the error to retry.
Edits arriving during compilation are queued for another build. Each successful
refresh starts a new process and resets UI state. F5 resets the UI session in
the current process; it does not compile sources. Gradle build properties and
offline mode from the launch invocation are preserved for automatic builds;
additional Gradle arguments can be set with `uiPreview.buildArguments`.

Source watching polls file paths, sizes, and modification times. Files changed
by tools that preserve both size and timestamp need an updated timestamp or
another watched edit. Generated build output directories are excluded from the
default source roots. Custom inputs that cause recursive rebuilds should not be
added to `watch`.

Logs and process status live under the owning module's `build/ui-preview/session`:
`watch.log`, `build.log`, `status.properties`, and each worker's `preview.log`.
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

The standalone picker discovers `UiKitTestPreview` and
`ShaderGraphEditorPreview` independently. They reuse the showcase and editor
compositions. Open the standalone window with:

```powershell
./gradlew :tests:platform:desktop:libfdx_ui_preview_run
```

In `UiKitTestPreview`, Page Up and Page Down browse showcase sections and the
mouse wheel scrolls the current section. Set `-Dlibfdx.uiPreview.section=Sliders`
to open the slider section.

The watcher also supports a project-owned host through `UiPreview.attach`.
Pass its existing listener and a predicate that reports the first usable frame.
