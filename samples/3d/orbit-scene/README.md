# 3D Orbit Scene

Orbit a lit sculpture with the mouse. The scene uses a perspective camera, three procedural meshes, and a directional light.

Drag with the left mouse button to orbit and scroll to zoom. In `OrbitSceneApplication`, `ModelBuilder` creates the cylinder, torus, and sphere. Each model instance has its own transform. `ModelBatch` draws them with a shared lighting environment. The application disposes its models and detaches the camera controller on shutdown.

## Run from the libFDX checkout

```shell
./gradlew :samples:3d:orbit-scene:platform:desktop:libfdx_desktop_jvm_gl_run
./gradlew :samples:3d:orbit-scene:platform:web:libfdx_web_js_webgl_run
```

In an exported project, the corresponding task paths start with `:platform:desktop:` or `:platform:web:`.
