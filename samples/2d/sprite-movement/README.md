# 2D Sprite Movement

This sample is a portable ECS project with a controllable sprite, orthographic
camera, editable scene data, and repeated wall tiles. Move with WASD or the
arrow keys.

## Code map

- `SpriteMovementProject` is the single project entry implementation. Its only
  entry method receives `Fdx` and a host-created `World`, then registers the
  project's phase systems and managers, extends the world-owned scene catalog,
  and applies initial scene state.
- `component` contains editable and runtime ECS data.
- `input` converts backend keyboard state into allocation-free movement values.
- `render` contains a core `RenderSystem` that owns the batch and textures,
  renders with the camera resolved by the world, and releases owned resources
  when detached.
- `scene` extends core `SceneManager` with reflection-free component
  descriptors and presets. The world-owned manager handles deterministic scene
  capture and restore for both the standalone launcher and external hosts.
- `system` contains allocation-free `UpdateSystem` gameplay behavior.
- `platform` contains backend-specific launchers that run the project through
  core `EcsApplication`.

Run the desktop GL variant from the repository root:

```powershell
.\gradlew.bat :samples:2d:sprite-movement:platform:desktop:libfdx_desktop_jvm_gl_run
```
