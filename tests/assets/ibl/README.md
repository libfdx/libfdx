# Original IBL fixtures

All assets here are generated for libFDX and use the repository license. There are
no third-party images. Regenerate the analytic fixtures and original studio HDR:

```powershell
python tests/core/src/test/fixtures/ibl/generate.py
.\gradlew.bat :libfdx:tools:ibl:run '--args=tests/assets/ibl/studio.hdr tests/assets/ibl/studio.fdxibl 256 32 64 512'
```

`controls.fdxibl` contains deliberately labeled numeric levels, diffuse directions
and BRDF coefficients. It tests runtime sampling independently of the convolution
tool. `authored.gltf` contains twenty constant-normal PBR panels; `reference.gltf`
contains independently calculated linear colors in unlit panels. `cases.json`
records materials, normals and expected colors. Together they cover metallic and
dielectric surfaces, roughness-level interpolation, rotated directions, the longitude
seam, poles, grazing angles and radiance above one. These analytic assets remain
numerical fixtures; they are not the interactive material studio.

CPU preparation tests separately check constant energy, analytic diffuse irradiance
and independent hemisphere integration of the BRDF.

`studio.hdr` is an original two-light studio with overhead fill and a warm floor.
`studio.fdxibl` is its prepared 256/32/64, 512-sample probe.
`IblLightingTest` uses this probe to light a perspective scene with copper and
ceramic spheres, increasing roughness from left to right, a plinth and a matte
floor. Ambient and direct lights are disabled. Its controls toggle IBL, rotate the
probe and change intensity; right-drag orbits the camera and scrolling zooms.
Rendering uses the full framebuffer, with camera framing adjusted to the window
aspect ratio. This scene demonstrates the split-sum response; it is not an
independent numerical ground-truth comparison or a paired-image checker input.
