# Animation diagnostics

Original libFDX solid drone geometry and analytic curves, available under the repository
license. The drones use lit metallic-roughness materials and flat face normals.
The `GltfAnimationTest` scenario compares imported animation and sparse geometry with
an independently constructed drone and analytic transforms on every frame.
The reference mesh is owned by `AnimationDroneGeometry` in the test-support package.
Three labeled interpolation stations each show the imported pose above its analytic
reference, with identical perspective framing and a shared four-second timeline.
Static waypoints, a sampled translation path, and a rotation dial provide motion cues.
The HUD loads the default TrueType font with fallback disabled. Interactive playback uses elapsed time; bounded validation runs
visit exact key boundaries, their neighbors, and clamped endpoints deterministically.

Left: STEP translation. Center: cubic translation, independent STEP scale and LINEAR
quaternion rotation. Right: cubic quaternion skin animation, including preserved
quaternion signs and nonzero tangents. Every position accessor has a zero base with
sparse replacements. The first two files share an external BIN; the third is GLB.
