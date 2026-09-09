# TexturePacker

The standalone JVM atlas library packs PNG sources into deterministic PNG pages and UTF-8
`.atlas.json` metadata using the portable JSON module. Runtime loading lives in [G2D](../../framework/g2d/README.md#sprite-atlases);
the library has no dependency on Gradle or a graphics provider. Call it explicitly
from a Java program or an editor, and keep the resulting files in the application's
assets folder. Application builds package those files without running the packer.

```java
import io.github.libfdx.tools.texturepacker.TexturePacker;
import io.github.libfdx.tools.texturepacker.AtlasSpec;
import java.nio.file.Path;

var spec = new AtlasSpec(
    Path.of("tests/assets/atlas-source"), Path.of("tests/assets/atlas"),
    "test", "", 64, 2, 1, true, 1, true, .5f, .5f, 64);
Path metadata = TexturePacker.generate(spec);
```

This writes `test.atlas.json` and its PNG pages directly into `tests/assets/atlas`.
The empty `assetPath` selects the output directory itself; a nonempty value adds
a subdirectory. `AtlasSpec.defaults(source, output, name)` uses the `sprites`
subdirectory and default packing settings. Source and output directories must
not overlap, so source sprites and packed assets can live in sibling folders.
The call runs synchronously and returns the metadata path; I/O failures propagate
to the caller. Serialize calls that write the same atlas. The module requires
Java 25 and uses JVM image codecs; it is an authoring dependency, not a dependency
of the portable application runtime.

Source names are PNG paths relative to the source directory, without their
extension. `player/idle.png` becomes `player/idle`. An optional neighboring
`idle.sprite.properties` can set `name`, `pivotX`, and `pivotY`. Pivots are normalized
in the original image, with bottom-left `(0,0)` and top-right `(1,1)`. Defaults are
centered. Unknown properties and duplicate names, including case collisions,
fail before publication.

Generated pages use raw RGBA8 PNG. The runtime's portable decoder preserves their
transparent RGB, including when a browser fetches pages after startup, using the
[PNG filtering and stream rules](https://www.w3.org/TR/png-3/#9Filters).

Trimming removes fully transparent borders and retains `trimMargin` source pixels
around visible content where available. The default margin of one preserves the
linear-filtered silhouette when scaling or rotating. Zero is suitable for strict
nearest sampling but can change filtered edges. Fully transparent sprites become
one transparent pixel while retaining their original dimensions and pivot.

RGB bleed fills transparent pixels from the nearest visible color using stable
Manhattan-distance ordering; it preserves alpha. Extrusion copies edge texels
outside each region, and padding adds transparent separation beyond extrusion.
Defaults use one extruded pixel and two padding pixels. Input/output alpha remains
straight. Trimming, bleeding, and extrusion never premultiply colors. Mipmaps and
packing rotation are not supported; these borders do not guarantee isolation
under arbitrary mipmapped or anisotropic sampling.

Packing uses height/width/name ordering and first-fit shelves, with power-of-two
page extents. Source dimensions are checked before decoding; total source pixels
are limited to 16M. Output is bounded to 64M pixels and the configured page count.
`maxPageSize` is a power of two from 8 to 4096; sprites plus borders must fit.
This is a preparation-time memory bound, not a streaming image decoder.

Identical inputs, settings, tool classpath, and JVM codec version produce identical
bytes. The tool validates inputs and layout before writing staged pages, then
publishes metadata last. A mid-publication I/O failure can leave mixed versions;
stop consumers while rebuilding rather than treating this as atomic hot reload.
Only page names owned by the previous valid metadata are removed. Keep output
outside the source directory. When changing an atlas name or asset subdirectory,
remove the old atlas files explicitly; unrelated files are preserved.

`AtlasData` is the canonical reader/writer for version-one JSON metadata. Its
declaration defines bounds and coordinate conventions. The document has required
`version` (currently `1`), `alpha` (`"straight"`), `pages`, and `sprites` properties.
Each page has `image`, `width`, and `height`. Each sprite has `name`, `page`, `x`,
`y`, `width`, `height`, `originalWidth`, `originalHeight`, `trimX`, `trimY`, `pivotX`,
and `pivotY`. Page indices are zero-based and image paths are relative to the
metadata file. Integer fields must contain exact 32-bit integers.

Object property order does not matter. Readers ignore unknown properties at the
root, page, and sprite levels; adding optional metadata does not require a version
change. Unknown properties are not retained when re-encoding `AtlasData`. Required
fields remain type checked, and incompatible semantics require a new version.
Only JSON is supported; the former tab-delimited format is not accepted.
Metadata limits apply after whole-file acquisition; large or untrusted downloads
still need application-level limits.
