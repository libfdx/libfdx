# 2D Sprite Movement

This sample is an ordinary portable libFDX application with a controllable
sprite, orthographic camera, and repeated wall tiles. Move with WASD or the
arrow keys.

## Code map

- `SpriteMovementApplication` owns the application lifecycle, camera, batch,
  textures, update order, and drawing.
- `SpriteMovementState` owns the player and fixed wall data without a framework
  entity model.
- `input` converts backend keyboard state into allocation-free movement values.
- `render` loads and owns the two sample textures used by the application.
- `platform` contains backend-specific launchers that start the application
  directly.

Run the desktop GL variant from the repository root:

```powershell
.\gradlew.bat :samples:2d:sprite-movement:platform:desktop:libfdx_desktop_jvm_gl_run
```
