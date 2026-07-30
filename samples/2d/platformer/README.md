# Platformer Example

This sample is a small fixed-level platformer built with ordinary
application-owned game state and a dedicated renderer.

The core module keeps gameplay explicit and organized under
`io.github.libfdx.samples.g2d.platformer`:

- `PlatformerGame` owns the fixed-capacity sprite arrays and advances input,
  movement, collision, interactions, enemies, restart state, and camera state;
- `PlatformerLevel` constructs the complete fixed level once at startup;
- `PlatformerRenderer` traverses retained sample state and draws it in a
  centered square viewport so the pixel art keeps its intended aspect ratio;
- `input` adapts backend input to the small game-facing input interface;
- `render` owns Kenney textures, rendering, and optional framebuffer capture.

The sample includes the CC0 Pixel Platformer asset pack from Kenney under
`assets/kenney/pixel-platformer`. `PlatformerTextures`
loads the three packed Kenney sheets directly once during application startup:
tiles, background tiles, and characters. The sample does not package or load
individual sprite PNGs from the Kenney archive; every visual is a
`TextureRegion` selected from those packed sheets. The texture descriptors use
`TextureFilter.NEAREST` so Kenney's pixel art remains sharp when scaled by the
sample renderer.

The sample keeps the full platform module matrix. Every launcher module applies
the libFDX Gradle plugin directly, keeps its plugin target configuration beside
the platform launcher, and uses plugin-generated `libfdx_*` tasks. Android
continues to own its manifest, activities, assets, and Android packaging while
the plugin owns its install-and-launch tasks.

Run the desktop sample from the repository root:

```powershell
.\gradlew.bat :samples:2d:platformer:platform:desktop:libfdx_desktop_jvm_gl_run
.\gradlew.bat :samples:2d:platformer:platform:desktop:libfdx_desktop_jvm_wgpu_run
.\gradlew.bat :samples:2d:platformer:platform:desktop:libfdx_desktop_jvm_vulkan_run
```

Move with A/D or Left/Right. Jump with Space, Up, W, or click. Restart
after game-over or completion with R, Enter, or the jump control.

For visual validation, write a PPM capture during a finite desktop run:

```powershell
.\gradlew.bat "-Dlibfdx.sample.exitAfterFrames=30" "-Dlibfdx.sample.capture=build/reports/platformer/gl.ppm" :samples:2d:platformer:platform:desktop:libfdx_desktop_jvm_gl_run
```
