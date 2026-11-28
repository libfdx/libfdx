# Tidepost – 2D Sprite Movement

Pilot a small mail boat across an illustrated harbor. Deliver to the market quay, lighthouse and shipyard in order by sailing up to the envelope at each pier.

Use WASD or the arrow keys, a gamepad's left stick or D-pad, or hold and drag on the water. A shorter drag or a lighter stick movement gives finer control near the dock. Release to slow down. Select **Start route** to leave the anchorage and **Sail again** to replay.

`SpriteMovementState` handles acceleration, turning, hull clearance and the wake in chart coordinates. The harbor image and collision outlines share those coordinates. When the screen shows only part of the chart, the camera follows the boat and a bearing arrow points toward the next delivery.

Run the desktop sample from the repository root.

```powershell
.\gradlew.bat :samples:2d:sprite-movement:platform:desktop:libfdx_desktop_jvm_gl_run
```

Run the WebGL version in a browser.

```powershell
.\gradlew.bat :samples:2d:sprite-movement:platform:web:libfdx_web_js_webgl_run
```
