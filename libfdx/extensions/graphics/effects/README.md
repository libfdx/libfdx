# Lighting and post-processing

This optional module composes opaque scenes through common graphics resources.
The application owns its effects, targets and resize/disposal order. It borrows
the device, input textures and active frame; nothing is registered in `Fdx`.

## Scene composition

`Lighting2D` multiplies linear albedo by ambient light and up to 16 point lights.
A light uses world position, height above the scene, radius, linear RGB and an
intensity multiplier. Falloff is squared radial attenuation multiplied by a
Lambert diffuse term. This is a bounded artistic lighting model; it does not
include occlusion shadows, specular response or physical intensity units.

Call `bounds(x, y, width, height)` to map world coordinates across the destination
viewport, with x right and y up. Configure slots with `light(...)`, then enable
the desired leading slots with `lightCount(count)`. Unconfigured slots are dark.
An optional normal texture maps the same viewport: RGB stores world +X/+Y/+Z as
`(normal + 1) / 2`. Declare its row origin separately. It must use a linear format.
Without normals, the surface faces +Z.

The caller begins and ends the destination pass and chooses viewport/scissor.
`Lighting2D.draw` writes linear RGB and alpha one. Use an sRGB attachment for LDR
storage or a supported float attachment for HDR. Existing sprites, particles,
outlines and fog can be rendered into a scene before post-processing; transparency
within that scene must already be composited against its opaque background.
Use [TextureBlitter](../../../framework/g2d/README.md#offscreen-composition) for
transparent layer composition.

## Post-processing and quality

`PostProcessor` runs these selected operations in order:

1. Extract RGB above a linear per-channel bloom threshold, then blur reduced-size
   persistent targets using separable Gaussian passes.
2. Add bloom, apply exposure once, then apply the selected tone mapping.
   `REINHARD` uses `color / (1 + color)`; `NONE` clips output to the display range.
3. Optionally smooth high-contrast edges with five samples in display color.
   This can soften thin scene features; it is spatial filtering, with no geometry
   coverage or temporal history.
4. Present at the caller's viewport/scissor with the correct destination transfer.

| Preset | Scene size | Scene/bloom storage | Bloom size | Blur pairs | Edge filter |
| --- | --- | --- | --- | --- | --- |
| LOW | 75% per axis | RGBA8 sRGB | disabled | 0 | off |
| BALANCED | full | RGBA8 sRGB | quarter per axis | 1 | on |
| HIGH | full | RGBA16 float | half per axis | 2 | on |

Scene size is applied explicitly by the caller using `sceneDimension`. Bloom sizes
are relative to presentation dimensions, with a minimum of one pixel. LDR storage
clips values above one; sRGB storage retains more dark-color precision than linear
eight-bit storage. HDR preserves a wider scene range before tone mapping.

Constructors request the exact preset and fail before allocating targets if required
format/render/filter/blend capabilities or a known rendered origin are missing.
`EffectQuality.bestSupported(capabilities)` is an explicit opt-in fallback selector;
compare its result with the requested preset to show the choice. It selects supported
features, not the fastest configuration for a device. Depth, MSAA and application
memory budgets are additional scene requirements.

For example, with the usual `io.github.libfdx.graphics.*` and
`io.github.libfdx.graphics.effects.*` imports:

```java
EffectQuality quality = EffectQuality.bestSupported(graphics.device().capabilities());
OffscreenTarget scene = new OffscreenTarget(graphics.device(),
        quality.sceneFormat(), null, 1, TextureFilter.LINEAR);
PostProcessor effects = new PostProcessor(graphics.device(), quality);
RenderPassDescriptor presentation = new RenderPassDescriptor()
        .colorLoadOp(LoadOp.clear(0, 0, 0, 1));

// Setup/resize, outside active passes. Defer while the window has zero dimensions.
scene.resize(quality.sceneDimension(width), quality.sceneDimension(height));
effects.resize(width, height);

// Inside the active frame:
GraphicsFrame frame = graphics.currentFrame();
RenderPass scenePass = scene.begin(frame, true);
// Draw the opaque scene in linear RGB; end the pass before processing.
scenePass.end();
ColorEncoding encoding = scene.color().format().isSrgb()
        ? ColorEncoding.SRGB : ColorEncoding.LINEAR;
effects.process(frame, scene.color(), scene.origin(), encoding);

RenderPass presentationPass = frame.commandEncoder().beginRenderPass(
        presentation.colorAttachment(frame.colorAttachment()));
// Set viewport/scissor, then:
effects.present(presentationPass);
// Draw UI with its own display-color semantics.
presentationPass.end();

// On application disposal, after all passes end:
effects.dispose();
scene.dispose();
```

A [forward 3D graph](../../../framework/g3d/README.md) can supply the scene instead.
Use `Environment.clearToneMapping()` when `PostProcessor` owns exposure/tone
mapping, and choose an sRGB or float graph target so the PBR renderer outputs linear
RGB. Render UI after presentation so scene exposure does not affect it.

## Color and lifetime contract

`ColorEncoding` describes stored input RGB. SRGB bytes in UNORM textures decode in
the shader; an sRGB texture decodes in hardware. Declaring an sRGB texture LINEAR is
an error. Non-color normal data never decodes. `TextureOrigin` describes image rows
independently of color encoding; unknown origins fail clearly.

`PostProcessor.color()` borrows its final **SRGB-encoded RGBA8_UNORM** texture, with
alpha one. Use `present(pass)` for attachment-aware transfer: normalized presentation
attachments receive encoded RGB; sRGB and float attachments receive linear RGB.
Passing this output to another effect requires `ColorEncoding.SRGB`.
The output is available after a successful `process`, until resize or disposal.
An incomplete rendering failure invalidates the current output; an allocation
failure during resize preserves the prior valid output.

Resize allocates replacement targets before publishing them. Equal dimensions
reuse storage. End all passes before resize/disposal; old borrowed handles become
invalid after successful replacement. Input textures must not alias the processor's
owned targets or any active writing attachment. Dispose is idempotent and releases
owned resources even if another cleanup operation fails. No input texture is owned
or disposed by an effect.

`estimatedBytes()` reports unpadded intermediate target storage; add the caller-owned
scene separately. Driver metadata/alignment, shaders and retired in-flight allocations
are outside that estimate. `passCount()` reports the current processing passes and
excludes the scene and final presentation. Zero bloom strength skips its passes while
retaining allocations. Effect setters, shader parameters and targets are reused;
first use of a new destination layout may compile/cache a pipeline.

The [graphics capability contract](../../../framework/graphics/README.md) distinguishes
renderability, filtering and blending. WGPU's half-float declaration follows the
[WebGPU format capabilities](https://www.w3.org/TR/webgpu/#texture-format-caps);
32-bit float filtering/blending is not inferred from that declaration.

## Rendered verification

The `effects` test scenario shows identical flat and normal-mapped scenes under each
preset. The highest unsupported preset is reported and explicitly replaced. The
scenario exercises repeated resize and logs target estimates plus CPU recording
durations after warm-up. Those times exclude GPU execution/presentation and cannot
establish a GPU speedup; there is currently no common asynchronous GPU timing API.

The independent [capture verifier](../../../../tests/core/src/test/fixtures/effects/verify-capture.py)
reconstructs upload texels, lighting, intermediate quantization, bloom, tone mapping
and edge decisions without using provider images as its expected result:

```text
python tests/core/src/test/fixtures/effects/verify-capture.py <capture.ppm-or-png>
python tests/core/src/test/fixtures/effects/verify-capture.py <capture.ppm-or-png> --hdr
```

It checks 497,664 effect pixels and all 116,736 margins at 960×640. All but at most
16 pixels per panel must match within 3/255. Isolated edge-filter threshold decisions
may differ by at most 8/255. CPU contract tests cover unsupported formats, partial
allocation, atomic replacement, borrowed resources and recording/cleanup failures.
