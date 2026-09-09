# Platformer Example

A complete two-level platformer built from Tiled JSON maps, application-owned
simulation state, budgeted asset loading, pixel art, and optional audio.

Run from the repository root:

```powershell
.\gradlew.bat :samples:2d:platformer:platform:desktop:libfdx_desktop_jvm_gl_run
.\gradlew.bat :samples:2d:platformer:platform:web:libfdx_web_js_webgl_run
```

The desktop launcher enables OpenAL; set `-Dlibfdx.sample.audio=false` for silent
play. The web launcher enables Web Audio, activated by the first key, pointer, or
touch gesture. Launchers without an audio provider run silently and display
"AUDIO UNAVAILABLE". Discover other generated launch tasks through Gradle task
help; a build or package alone does not establish runtime parity.

## Controls

| Action | Keyboard | Other input |
| --- | --- | --- |
| Move | A/D or Left/Right | Left stick, D-pad, on-screen arrows |
| Jump | Space, W, or Up | South gamepad button, on-screen jump, scene click |
| Pause / resume | P or Escape | Start button, MENU |
| Restart / retry failed level | R or Enter | RESTART in the menu |
| Next level | N | East gamepad button, NEXT TRAIL |
| Volume | Q / E | Minus / plus in the menu |
| Mute / restore volume | M | Mute button |

Pause, loading, and menu input suppress gameplay actions. A held control must
return to neutral before it can move the player after resume. Independent touch
contacts allow moving and jumping together. The menu and controls use the same
integer viewport placement as rendering, including high-DPI displays and
letterboxing. Windows smaller than 300 by 180 framebuffer pixels crop at 1x.

## Level format and ownership

The two maps live in `assets/levels` and can be opened as ordinary Tiled JSON
maps. See the [Tiled importer](../../../libfdx/extensions/maps/tiled/README.md)
for the supported format subset. The sample uses 18-pixel tiles, a 300 by 180
logical view, and 150 source pixels per simulation unit.

`PlatformerLevel.create(input, map)` converts hidden object layers to fixed
simulation arrays without mutating the cached map. Unrotated rectangles with
class/type `solid` become collision surfaces. Point objects tagged `player`,
`coin`, `hazard`, `enemy`, or `goal` become gameplay entities. Their integer
`region` property selects a zero-based sprite across the three Kenney sheets.
Enemies also define `minX`, `maxX`, and `speed` in source pixels and seconds.
Exactly one player and goal are required. Gameplay objects must fit the level,
use no parallax, and retain the 180-pixel level height. Validation errors identify
the object. Map file properties `next` and `music` resolve relative to the map;
`title` supplies the HUD label.

`PlatformerApplication` owns its asset manager, scopes, mixer, input router,
batch, font, and simulation. It borrows backend graphics and audio roots.
One global scope holds UI textures and sound effects. Each level has its own
scope; its texture and music dependencies finish before the level becomes
active. The update queue has a four-task / one-millisecond soft budget.
Desktop file/decode work uses an owned two-worker executor. GPU publication
stays on the application thread.

A replacement loads while the previous level remains available. Successful
publication resets the simulation and crossfades music for 0.6 seconds before
releasing the previous scope. Shared Kenney textures remain alive. A failed
replacement shows a retry message; R retries, and P returns to the previous
level's pause menu. Music uses bounded PCM WAV streaming and the mixer controls
master, music, effects, and UI gain. Pause stops the simulation clock and music;
resume drops accumulated pause time. Shutdown closes scopes before the desktop
executor, after stopping playback.

`AuthoredPlatformerRenderer` draws parallax image/cloud layers, terrain,
animated gameplay sprites, and a bitmap HUD. `PlatformerGame` runs at 60 fixed
steps per second with at most eight catch-up steps. Legacy fixed-level and
renderer helpers remain as small CPU test fixtures.

## Assets and validation

Kenney's Pixel Platformer sheets are CC0; their original license is in
[the asset pack](assets/kenney/pixel-platformer/License.txt). Generated skies,
the bitmap font sheet, level layouts, and synthesized WAV files use the
repository license. Reproduce them from the repository root with Python 3:

```powershell
python samples/2d/platformer/tools/generate-assets.py .
.\gradlew.bat :samples:2d:platformer:core:test
```

For a bounded desktop capture:

```powershell
.\gradlew.bat "-Dlibfdx.sample.exitAfterFrames=240" "-Dlibfdx.sample.captureFrame=160" "-Dlibfdx.sample.capture=build/reports/platformer/gl.ppm" :samples:2d:platformer:platform:desktop:libfdx_desktop_jvm_gl_run
```

The core tests cover authored data conversion, contacts, restart, input routing,
viewport mapping, and simulation timing. Actual rendering and audio activation
require running the corresponding launcher; controller emulation is not a
physical-controller test.
