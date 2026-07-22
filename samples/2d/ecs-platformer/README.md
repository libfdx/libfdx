# ECS Platformer Example

This sample is a small fixed-level platformer that demonstrates how game code
can use the optional libfdx ECS extension.

The core module keeps gameplay explicit and organized under
`io.github.libfdx.samples.ecs.platformer`:

- `component` holds simple mutable state such as position, velocity, bounds,
  player, solid tile, collectible, hazard, enemy, goal, input, render sprite,
  and level state;
- `system` has one top-level ECS system class per gameplay concern, importing
  ECS support from `io.github.libfdx.ecs.component`,
  `io.github.libfdx.ecs.entity`, and `io.github.libfdx.ecs.system`;
- `input`, `render`, and `world` keep backend input, Kenney texture/capture,
  and level creation separate from the application entry point;
- movement resolves AABB collision against cached solid-tile entity lists;
- collectible, hazard, goal, enemy patrol, camera, and restart behavior are
  handled by separate systems;
- rendering uses `SpriteBatch` with Kenney texture regions only.

The sample includes the CC0 Pixel Platformer asset pack from Kenney under
`assets/samples/ecs-platformer/kenney/pixel-platformer`. `PlatformerTextures`
loads the three packed Kenney sheets directly once during application startup:
tiles, background tiles, and characters. The sample does not package or load
individual sprite PNGs from the Kenney archive; every visual is a
`TextureRegion` selected from those packed sheets. The texture descriptors use
`TextureFilter.NEAREST` so Kenney's pixel art remains sharp when scaled by the
sample renderer.

The sample keeps the full platform module matrix. Desktop, web, desktop C, and
iOS C modules apply the libFDX Gradle plugin directly, keep only plugin target
and asset configuration in their build files, use the plugin-generated
`libfdx_*` tasks, and own their launcher classes. The root build convention
supplies their sample classpaths and selects published or local libFDX
dependencies according to `development.usePublishedLibfdx`. Android remains a
normal Android module for manifest, activity, and asset wiring.

Run the desktop sample from the repository root:

```powershell
.\gradlew.bat :samples:2d:ecs-platformer:platform:desktop:libfdx_desktop_jvm_gl_run
.\gradlew.bat :samples:2d:ecs-platformer:platform:desktop:libfdx_desktop_jvm_wgpu_run
.\gradlew.bat :samples:2d:ecs-platformer:platform:desktop:libfdx_desktop_jvm_vulkan_run
```

Move with A/D or Left/Right. Jump with Space, Up, W, click, or tap. Restart
after game-over or completion with R, Enter, or the jump control.

For visual validation, write a PPM capture during a finite desktop run:

```powershell
.\gradlew.bat "-Dlibfdx.sample.exitAfterFrames=30" "-Dlibfdx.sample.capture=build/reports/ecs-platformer/gl.ppm" :samples:2d:ecs-platformer:platform:desktop:libfdx_desktop_jvm_gl_run
```
