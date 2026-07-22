# 2D Sprite Movement

This sample is a portable ECS project with a controllable sprite, orthographic
camera, editable scene data, and repeated wall tiles. Move with WASD or the
arrow keys.

## Code map

- `SpriteMovementProject` and `SpriteMovementRuntime` coordinate the project
  lifecycle.
- `component` contains editable and runtime ECS data.
- `input` converts backend keyboard state into allocation-free movement values.
- `render` owns the sample textures.
- `scene` defines the reflection-free editor schema and scene codecs.
- `system` contains ordinary ECS behavior.
- `platform` contains backend-specific launchers only.

Run the desktop GL variant from the repository root:

```powershell
.\gradlew.bat :samples:2d:sprite-movement:platform:desktop:sprite_movement_desktop_gl_run
```
