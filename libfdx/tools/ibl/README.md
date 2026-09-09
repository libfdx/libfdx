# Offline environment preparation

This JVM tool converts a 2:1 linear RGB Radiance HDR panorama into a prepared
`.fdxibl` probe. It uses deterministic Hammersley sampling, cosine diffuse
convolution, GGX specular filtering and a correlated Smith BRDF lookup. A
solid-angle-weighted source pyramid reduces aliasing when sampling small lights.
No GPU context or native rendering library is required.

From the repository root:

```powershell
.\gradlew.bat :libfdx:tools:ibl:run '--args=input.hdr output.fdxibl 256 32 64 512'
```

The four optional numbers are specular width, diffuse width, BRDF square size and
integration sample count. Defaults are 256, 32, 64 and 256. Specular widths halve
through a chain ending at 16x8 to retain directional illumination at high roughness;
smaller inputs retain at least two levels. Diffuse/specular images are 2:1 except
when a tiny probe reaches 1x1. Output dimensions must be powers of two. Samples range from 32 to
4096; preparation rejects more than 250 million integration samples. Increase samples
for bright small sources after inspecting the result. The command prints input and
output SHA-256 hashes. It writes a temporary output before replacement and refuses
to replace its source file. Gradle's run task resolves paths from the repository root;
the installed application resolves them from its calling directory.

The reader accepts RGBE raw pixels or scanline RLE in `-Y height +X width` order.
It checks RLE bounds/truncation, accepts only square source pixels, rejects nonlinear
GAMMA metadata and reverses cumulative EXPOSURE/COLORCORR multipliers. Legacy repeated-
pixel encoding, XYZE and other orientations require conversion before loading.
Source files are bounded to 128 MiB and 4096x2048 pixels. HDR values above 65504 must
be reduced before preparation. The encoding and correction fields are defined by
the [Radiance file format](https://radsite.lbl.gov/radiance/refer/filefmts.pdf).

RGB is interpreted in the application's linear working primaries. The tool does
not perform a PRIMARIES color-space conversion or infer a camera exposure from
photographic metadata. Convert the source to linear sRGB/Rec.709 when using the
standard PBR material color convention. For an already decoded source, call
`IblPreparer.prepare(rgb, width, height, specularWidth, diffuseWidth, lutSize, samples)`.
Preparation copies input data and allocates CPU storage; it belongs in build tooling.

The version 1 FDXI file contains eight little-endian 32-bit header words: `FDXI`
magic, version 1, specular width, diffuse width, LUT size, allocated specular level
count (at least two, within the width's mip range), encoding 1 (RGBA16_FLOAT) and reserved zero. Tightly packed half-float RGBA
images follow: all specular levels, diffuse, then LUT. Environment alpha is one;
LUT blue is zero and alpha one. There is no compression or trailing payload.

Runtime loading, resource ownership and approximation limits belong in the
[G3D IBL guide](../../framework/g3d/IBL.md).
