# Tiled scenes and fixtures

The `TiledMapTest` launcher entry opens an interactive coastal village: an
orthogonal map loaded from `coast.tmj` and its external `tilesets/coast.tsj` atlas.
The scene stays loaded until the test closes.

- Drag or use WASD / arrow keys to pan; scroll to zoom.
- Space pauses atlas animation; P toggles the repeating parallax mist image.
- O toggles the authored landmark bounds. Click a bound to inspect its map name.
- R resets the view, animation, selection, and layer toggles.
- The same toggles are available as buttons beside the scene.

The launcher contains only this tile-related test. Its ID and displayed name
match its Java class, `TiledMapTest`. Importer and streaming regression
tests remain with their owning extensions.

The atlas deliberately has no padding. The shared `TileMapRenderer` protects tile
sampling at fractional camera positions and zoom levels.

Desktop captures can set `libfdx.test.tiled.zoom`, `libfdx.test.tiled.panX`, and
`libfdx.test.tiled.panY` to reproduce a camera position (defaults: 1, 0, 0). Set
`libfdx.test.tiled.linear=true` to check linear filtering instead of nearest.
