# Shader graph sample

Run the same three surface graphs in a standalone libFDX application or in the shader editor. Both applications accept the saved assets or build the graphs in Java.

## Run the standalone example

Use JDK 25 and run from the repository root. These tasks launch a game window on Direct3D 12.

| Graph source | Gradle command |
| --- | --- |
| Saved files | `.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_d3d12_standalone_run` |
| Java builders | `.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_d3d12_standalone_code_run` |

The browser and Android launchers run the standalone gallery with the same model and saved graph files.

| Target | Gradle command |
| --- | --- |
| WebGL 2 | `.\gradlew.bat :samples:graphics:shader-graph:platform:web:libfdx_web_js_webgl2_run` |
| WebGPU | `.\gradlew.bat :samples:graphics:shader-graph:platform:web:libfdx_web_js_webgpu_run` |
| Android OpenGL ES | `.\gradlew.bat :samples:graphics:shader-graph:platform:android:libfdx_android_gles_run` |

For the web tasks, open the localhost URL printed by Gradle. The Android task installs and opens the gallery on a connected device or emulator.

Tap the model view to cycle effects on a touchscreen. On a keyboard, press **1**, **2**, or **3** to select an effect, or use the arrow keys to cycle. **Space** pauses or resumes shader time. The crystal holds a fixed pose. The window title shows the active effect and source.

| Key | Surface | What the graph does |
| --- | --- | --- |
| **1** | [Dissolve and rim](assets/shaders/surface-effects.fdxgraph) | Noise drives an alpha mask and an orange edge. Fresnel adds cyan emission at grazing angles. |
| **2** | [Molten basalt](assets/shaders/molten-basalt.fdxgraph) | Bright lava flows through fixed cracks between dark basalt plates. Increasing `heat` widens the openings. |
| **3** | [Vortex portal](assets/shaders/vortex-portal.fdxgraph) | Two luminous streams spiral around a dark eye. Time spins the streams and sparks. Adjust `orbit_phase` for a starting offset or `twist` to wind the arms tighter. |

To start directly on basalt or vortex, use the `_standalone_basalt_run` or `_standalone_vortex_run` task for your provider. Each has a `_code_run` variant.

This task opens the Java-authored portal in the standalone game.

```powershell
.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_d3d12_standalone_vortex_code_run
```

The [standalone application](core/src/main/java/io/github/libfdx/samples/shadergraph/ShaderGraphSampleApplication.java) loads the bundled crystal with libFDX's glTF loader. Both graph sources produce a `StandardPbrTechnique` used by the same `ModelBatch`.

```java
var fromFile = ShaderGraphSampleApplication.loadSurface(graphics, files.internal("shaders/molten-basalt.fdxgraph"));
var fromJava = StandardPbrTechnique.create(graphics, ShaderGraphSampleGraphs.moltenBasalt());
```

File mode prefers the editor's saved copy under `assets/shaders` and accepts a compatible embedded shader cache. Java mode calls the same static builders as the editor. The standalone jars contain the renderer and assets and run independently of the editor.

## Run the editor

The default task opens **Dissolve and rim** in the editor. Select **Molten basalt** or **Vortex portal** in **Effects** at the top of the right sidebar to switch the graph, inspector and live preview in the same window. Switching keeps each effect's unsaved edits, undo history and chosen preview model for the current session. **Save** and **Save with compiled cache** write only the selected effect.

Use **Open effect…** to add your own `.fdxgraph` files alongside the sample effects. To start with an empty editor, run `.\gradlew.bat :libfdx:tools:shader-graph-editor:run`.

| Graph source | Gradle command |
| --- | --- |
| Saved file | `.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_d3d12_run` |
| Java builder | `.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_d3d12_code_run` |

To open Molten basalt or Vortex portal directly from its file, run the corresponding task.

```powershell
.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_d3d12_basalt_run
.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:libfdx_desktop_jvm_d3d12_vortex_run
```

Use `gl`, `wgpu` or `vulkan` in place of `d3d12` for the other desktop providers. The `_editor_run` tasks also start on the saved dissolve graph. The `_basalt_run`, `_vortex_run`, `_basalt_code_run` and `_vortex_code_run` tasks change the initial selection. All three effects use the source chosen by the task.

## Supply a graph

The [desktop launcher](platform/desktop/src/main/java/io/github/libfdx/samples/shadergraph/desktop/ShaderGraphSampleDesktopLauncher.java) passes its named effects to `ShaderGraphDesktopEditor.openEffects(args, effects, initialIndex)`. The editor loads each file or calls its Java builder on first selection. To open a single graph directly, use either of these entry points.

```java
ShaderGraphDesktopEditor.openFile(args, "shaders/surface-effects.fdxgraph");
```

File mode opens the editable [surface-effects.fdxgraph](assets/shaders/surface-effects.fdxgraph) when available and otherwise reads the bundled asset. It restores the saved node layout and preview animation range.

```java
ShaderGraphDesktopEditor.openGraph(args, ShaderGraphSampleGraphs.codeAuthoredSurface(),
        "shaders/surface-effects-from-code.fdxgraph", "threshold");
```

Java mode builds a fresh graph in memory on each launch. **Save** writes its edits to a separate `-from-code.fdxgraph` file.

## Preview an animation

Each surface includes an **Inputs – Time** node. **Play** advances shader time in seconds, **Pause** holds the current frame, and **Restart** returns time to zero. The model stays still until you select **Rotate**.

To change the lava speed, select the `cycles_per_second` Float constant and choose **Apply constant** after editing its value. At the default speed, a bright crest travels through the stationary fissures once every six seconds. The `flow_phase` material parameter offsets the crest by a fraction of a cycle. Save the graph to use the edit in the file-based game.

**Parameter animation** lets you audition a material value independently. **Play range** sweeps the saved range and **Stop range** restores the authored value. The sample presets are `threshold` for dissolve (0.05–0.80), `flow_phase` for basalt (0–1), and `orbit_phase` for the portal (0–1). The game plays each graph from its authored time input. To try a simple mask, right-click empty canvas and search for **Time**. Drag its **Seconds** output into empty canvas, type **Fraction**, and choose **Fraction (Value)** to create and wire the node. Then drag Fraction’s output socket into **Alpha** on the **Material Output** node. **Frame Output** locates it in a large graph. **Play** previews the mask and **Undo** restores the previous connection.

## Explore dissolve and rim in the editor

- **Dissolve.** In the [Java graph](core/src/main/java/io/github/libfdx/samples/shadergraph/ShaderGraphSampleGraphs.java), fixed noise and surface height delay the burn at each point. **Time → Divide → Fraction** advances the burn front. Smoothstep drives alpha and orange emission marks the surviving edge. Select **Sphere**, then **Play** to watch the surface break up. Edit `threshold` under **Material defaults** to change how much of the mesh dissolves.
- **Rim highlight.** Fresnel uses the world-space normal and view direction to brighten grazing angles. Select **Sphere** and set `threshold` to `-0.2` to see the complete rim. Increase `rim_power` to narrow it or `rim_strength` to brighten it.
- **Custom geometry.** Use **Load model…** for a glTF 2.0 `.gltf` or `.glb`. Keep external buffers and textures beside the model at their referenced paths. The editor fits the model and applies the current graph across its mesh parts.

Drag from an output to an input to connect nodes. **Fit** frames the graph. The preview updates after shader edits and keeps the last working result after a failed compilation. **Save** stores the graph and node layout. **Save with compiled cache** also embeds portable WGSL that the standalone application can reuse on any graphics provider.

## Regenerate and verify

The generation task writes all three Java-authored graphs under the core module’s `build/generated-shader-graphs` directory. The verification task checks file/Java parity for both portable shader profiles, exercises the compiled cache, runs the editor tests, and opens a finite WGPU editor session.

```powershell
.\gradlew.bat :samples:graphics:shader-graph:core:shader_graph_sample_generate_asset
.\gradlew.bat :samples:graphics:shader-graph:platform:desktop:shader_graph_sample_verify_reload
```
