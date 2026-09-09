package io.github.libfdx.maps.tiled;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.IntMap;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.maps.*;

import java.math.BigDecimal;

/** Parsing state belongs to one preparation operation. */
final class TiledReader {
    private final String source;
    private final int limit;
    private long cells;
    private int objectCount, layerCount, chunkCount;
    private long animationFrames;
    private final IntMap<Boolean> layerIds = new IntMap<Boolean>();
    private final IntMap<Boolean> objectIds = new IntMap<Boolean>();
    TiledReader(String source, int limit) { this.source = source; this.limit = limit; }

    Document readMap(byte[] bytes) {
        try {
            JsonValue root = object(new JsonReader().parse(bytes), "map");
            require("map".equals(string(root, "type", "map")), "expected map");
            require("orthogonal".equals(string(root, "orientation", "")), "unsupported orientation");
            boolean infinite = bool(root, "infinite", false);
            int width = infinite ? integer(root, "width", 0) : positive(root, "width");
            int height = infinite ? integer(root, "height", 0) : positive(root, "height");
            require(width >= 0 && height >= 0, "map dimensions must not be negative");
            require(infinite || (long)width * height <= limit, "map dimensions exceed cell limit " + limit);
            TileMap map = infinite ? TileMap.infinite(positive(root, "tilewidth"), positive(root, "tileheight"))
                    : new TileMap(width, height, positive(root, "tilewidth"), positive(root, "tileheight"));
            map.metadata(string(root, "class", ""), properties(root));
            map.parallaxOrigin(number(root, "parallaxoriginx", 0), map.worldHeight() - number(root, "parallaxoriginy", 0));
            String order = string(root, "renderorder", "right-down");
            require(order.equals("right-down") || order.equals("right-up") || order.equals("left-down") || order.equals("left-up"),
                    "unsupported renderorder " + order);
            map.renderOrder(order.startsWith("left"), order.endsWith("down"));
            JsonValue tilesets = array(root.require("tilesets"), "tilesets");
            require(tilesets.size() <= 65536, "too many tilesets");
            Reference[] references = new Reference[tilesets.size()];
            int lastId = 0;
            for (int i = 0; i < references.length; i++) {
                JsonValue value = object(tilesets.require(i), "tileset " + i);
                int first = positive(value, "firstgid");
                require(first <= 0x0fffffff && first > lastId && (i != 0 || first == 1), "invalid tileset firstgid " + first);
                lastId = first;
                String external = string(value, "source", null);
                references[i] = external == null ? new Reference(first, null, atlas(value))
                        : new Reference(first, resolve(source, external), null);
            }
            JsonValue layers = array(root.require("layers"), "layers");
            require(layers.size() <= 65536, "too many layers");
            for (int i = 0; i < layers.size(); i++) {
                MapLayer layer = layer(object(layers.require(i), "layer " + i), map, 0);
                if (layer instanceof TileLayer tiles) { map.addLayer(tiles); }
                else if (layer instanceof ChunkedTileLayer chunks) { map.addChunkedLayer(chunks); }
                else if (layer instanceof ObjectLayer objects) { map.addObjectLayer(objects); }
                else if (layer instanceof ImageLayer image) { map.addImageLayer(image); }
                else { map.addGroupLayer((GroupLayer)layer); }
            }
            return new Document(source, map, references, limit);
        } catch (RuntimeException failure) { throw error(failure.getMessage(), failure); }
    }

    private MapLayer layer(JsonValue layer, TileMap map, int depth) {
        require(++layerCount <= 65536, "too many layers");
        int width = map.width(), height = map.height();
        int id = positive(layer, "id");
        String where = "layer " + id + " (" + string(layer, "name", "") + ")";
        try {
            require(!layerIds.containsKey(id), "duplicate layer ID");
            layerIds.put(id, true);
            rejectLayerFeatures(layer);
            String type = string(layer, "type", "");
            MapLayer output;
            if (type.equals("tilelayer")) {
                require(!present(layer, "encoding") && !present(layer, "compression"),
                        "only uncompressed JSON array data is supported");
                if (map.isInfinite()) {
                    require(!present(layer,"data") && present(layer,"chunks"), "infinite tile layers require chunk arrays without flat data");
                    JsonValue chunks=array(layer.require("chunks"),"chunks");
                    chunkCount+=chunks.size();require(chunkCount<=65536,"too many chunks");
                    ChunkedTileLayer chunkLayer=new ChunkedTileLayer(65536,limit);
                    for(int i=0;i<chunks.size();i++) {
                        TileChunk chunk=chunk(object(chunks.require(i),"chunk "+i));
                        require(chunkLayer.findChunk(chunk.x(),chunk.y())==null,"duplicate chunk origin "+chunk.x()+","+chunk.y());
                        chunkLayer.put(chunk);
                    }
                    output=chunkLayer;
                } else {
                require(integer(layer, "width", -1) == width && integer(layer, "height", -1) == height,
                        "layer dimensions must match finite map");
                require(!present(layer, "chunks"), "finite layers cannot contain chunks");
                JsonValue data = array(layer.require("data"), "tile data");
                require(data.size() == (long)width * height, "tile data count does not match dimensions");
                cells += data.size();
                require(cells <= limit, "total layer cells exceed limit " + limit);
                TileLayer tileLayer = new TileLayer(width, height);
                for (int index = 0; index < data.size(); index++) {
                    long gid = gid(data.require(index));
                    tileLayer.tile(index % width, height - 1 - index / width, (int)(gid & 0x0fffffffL), flags(gid));
                }
                output = tileLayer;
                }
            } else if (type.equals("objectgroup")) {
                JsonValue objects = array(layer.require("objects"), "objects");
                objectCount += objects.size();
                require(objectCount <= 65536, "too many objects");
                MapObject[] values = new MapObject[objects.size()];
                for (int j = 0; j < values.length; j++) { values[j] = mapObject(objects.require(j), map.worldHeight()); }
                String drawOrder = string(layer, "draworder", "topdown");
                require(drawOrder.equals("index") || drawOrder.equals("topdown"), "unsupported object draworder");
                ObjectLayer objectLayer = new ObjectLayer(values, drawOrder.equals("topdown"));
                output = objectLayer;
            } else if (type.equals("imagelayer")) {
                require(!present(layer,"transparentcolor"),"transparentcolor requires preprocessing");
                int imageWidth=integer(layer,"imagewidth",0),imageHeight=integer(layer,"imageheight",0);
                require((!present(layer,"imagewidth") && !present(layer,"imageheight")) || (imageWidth>0 && imageHeight>0),
                        "image dimensions must both be positive when supplied");
                String image=string(layer,"image",null);
                require(image!=null && !image.isEmpty(),"image layer requires a source image");
                output=new ImageLayer(resolve(source,image),imageWidth,imageHeight,0,map.worldHeight(),
                        bool(layer,"repeatx",false),bool(layer,"repeaty",false));
            } else if (type.equals("group")) {
                require(depth < 32, "layer groups exceed maximum depth 32");
                JsonValue children = array(layer.require("layers"), "group layers");
                require(children.size() <= 65536 - layerCount, "too many layers");
                MapLayer[] values = new MapLayer[children.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = layer(object(children.require(i), "group child"), map, depth + 1);
                }
                output = new GroupLayer(values);
            } else {
                throw error("unsupported layer type " + type);
            }
            output.metadata(id, string(layer, "name", ""), string(layer, "class", ""), properties(layer));
            output.visible(bool(layer, "visible", true)).opacity(number(layer, "opacity", 1))
                    .tint(color(string(layer,"tintcolor","#ffffffff")))
                    .offset(number(layer, "offsetx", 0), -number(layer, "offsety", 0))
                    .parallax(number(layer, "parallaxx", 1), number(layer, "parallaxy", 1));
            return output;
        } catch (RuntimeException failure) { throw error(where + ": " + failure.getMessage(), failure); }
    }

    TileChunk readChunk(byte[] bytes) {
        try { return chunk(object(new JsonReader().parse(bytes),"chunk")); }
        catch(RuntimeException failure) { throw error(failure.getMessage(),failure); }
    }

    private TileChunk chunk(JsonValue value) {
        require(!present(value,"encoding") && !present(value,"compression"),"only uncompressed chunk arrays are supported");
        require(present(value,"x") && present(value,"y"),"chunk requires x and y coordinates");
        int x=integer(value,"x",0),tiledY=integer(value,"y",0);
        int width=positive(value,"width"),height=positive(value,"height");
        long y=-(long)tiledY-height;
        require(y>=Integer.MIN_VALUE && y<=Integer.MAX_VALUE,"chunk Y conversion exceeds signed coordinate range");
        JsonValue data=array(value.require("data"),"chunk data");
        require(data.size()==(long)width*height,"chunk data count does not match dimensions");
        cells+=data.size();require(cells<=limit,"total layer cells exceed limit "+limit);
        TileChunk chunk=new TileChunk(x,(int)y,width,height);
        for(int i=0;i<data.size();i++) {
            long gid=gid(data.require(i));
            chunk.tile(i%width,height-1-i/width,(int)(gid&0x0fffffffL),flags(gid));
        }
        return chunk;
    }

    TileAtlas readAtlas(byte[] bytes) {
        try {
            JsonValue root = object(new JsonReader().parse(bytes), "tileset");
            require("tileset".equals(string(root, "type", "tileset")), "expected external tileset");
            return atlas(root);
        } catch (RuntimeException failure) { throw error(failure.getMessage(), failure); }
    }

    private TileAtlas atlas(JsonValue value) {
        require(!present(value, "transparentcolor"), "transparentcolor requires preprocessing");
        require(string(value, "objectalignment", "unspecified").equals("unspecified")
                || string(value, "objectalignment", "").equals("bottomleft"), "only bottom-left tile object alignment is supported");
        require(string(value, "tilerendersize", "tile").equals("tile"), "grid tile render size is unsupported");
        require(string(value, "fillmode", "stretch").equals("stretch"), "unsupported tileset fill mode");
        JsonValue offset = value.get("tileoffset");
        int count = positive(value, "tilecount");
        require(count <= limit, "atlas tile count exceeds limit " + limit);
        JsonValue tiles = value.get("tiles");
        float offsetX = offset == null ? 0 : number(offset, "x", 0), offsetY = offset == null ? 0 : -number(offset, "y", 0);
        TileAtlas atlas;
        if (present(value, "image")) {
            atlas = new TileAtlas(string(value, "name", ""), resolve(source, string(value, "image", "")),
                    positive(value, "imagewidth"), positive(value, "imageheight"), positive(value, "tilewidth"),
                    positive(value, "tileheight"), positive(value, "columns"), count, integer(value, "margin", 0),
                    integer(value, "spacing", 0), offsetX, offsetY, properties(value));
        } else {
            require(integer(value, "columns", 0) >= 0 && integer(value, "margin", 0) == 0 && integer(value, "spacing", 0) == 0,
                    "invalid image collection columns/margin/spacing");
            // Collection columns are editor layout metadata, not an image grid.
            array(tiles, "image collection tiles");
            require(tiles.size() == count, "image collection tilecount must match its existing tiles");
            TileImage[] images = new TileImage[count];
            for (int i = 0; i < count; i++) {
                JsonValue tile = object(tiles.require(i), "image collection tile " + i);
                int id = integer(tile, "id", -1), width = positive(tile, "imagewidth"), height = positive(tile, "imageheight");
                require(id >= 0 && id < 0x0fffffff, "invalid collection local tile ID " + id);
                require(!present(tile, "transparentcolor"), "tile " + id + ": transparentcolor requires preprocessing");
                images[i] = new TileImage(id, resolve(source, string(tile, "image", "")), width, height,
                        integer(tile, "x", 0), integer(tile, "y", 0), integer(tile, "width", width), integer(tile, "height", height));
            }
            atlas = TileAtlas.imageCollection(string(value, "name", ""), positive(value, "tilewidth"),
                    positive(value, "tileheight"), images, offsetX, offsetY, properties(value));
        }
        if (tiles != null) {
            array(tiles, "tile metadata");
            require(tiles.size() <= count, "too many tile metadata entries");
            IntMap<Boolean> ids = new IntMap<Boolean>();
            for (int i = 0; i < tiles.size(); i++) {
                JsonValue tile = object(tiles.require(i), "tile metadata " + i);
                int id = integer(tile, "id", -1);
                require(!ids.containsKey(id), "duplicate tile metadata ID " + id);
                ids.put(id, true);
                require(!present(tile, "objectgroup"), "tile " + id + ": tileset collision groups are unsupported");
                if (!atlas.isImageCollection()) {
                    require(!present(tile, "image") && !present(tile, "x") && !present(tile, "y")
                            && !present(tile, "width") && !present(tile, "height"),
                            "tile " + id + ": per-tile images/crops require an image collection");
                }
                atlas.tileMetadata(id, className(tile), properties(tile));
                JsonValue animation = tile.get("animation");
                if (animation != null) {
                    array(animation, "tile " + id + " animation");
                    animationFrames += animation.size();
                    require(animation.size() > 0 && animation.size() <= 65536 && animationFrames <= limit,
                            "tile " + id + ": invalid or excessive animation frame count");
                    int[] frameIds = new int[animation.size()], durations = new int[animation.size()];
                    for (int frame = 0; frame < frameIds.length; frame++) {
                        JsonValue valueFrame = object(animation.require(frame), "animation frame " + frame);
                        frameIds[frame] = integer(valueFrame, "tileid", -1);
                        durations[frame] = positive(valueFrame, "duration");
                    }
                    atlas.animation(id, new TileAnimation(frameIds, durations));
                }
            }
        }
        return atlas;
    }

    private MapObject mapObject(JsonValue value, float mapHeight) {
        object(value, "object");
        int id = positive(value, "id");
        try {
            require(!objectIds.containsKey(id), "duplicate object ID"); objectIds.put(id, true);
            require(!present(value, "template") && !present(value, "text") && !bool(value, "ellipse", false)
                    && !bool(value, "capsule", false), "unsupported template/text/ellipse/capsule object");
            float x = number(value, "x", 0), y = mapHeight - number(value, "y", 0);
            float width = number(value, "width", 0), height = number(value, "height", 0);
            long gid = present(value, "gid") ? gid(value.require("gid")) : 0;
            boolean point = bool(value, "point", false), polygon = present(value, "polygon"), polyline = present(value, "polyline");
            require((present(value, "gid") ? 1 : 0) + (point ? 1 : 0) + (polygon ? 1 : 0) + (polyline ? 1 : 0) <= 1,
                    "conflicting object shapes");
            MapObject.Shape shape;
            float[] vertices;
            if (present(value, "gid")) {
                require((gid & 0x0fffffffL) != 0 && width > 0 && height > 0, "tile object needs a tile and positive explicit size");
                shape = MapObject.Shape.TILE; vertices = new float[] {0, 0, 0, height, width, height, width, 0};
            } else if (point) {
                shape = MapObject.Shape.POINT; vertices = new float[] {0, 0};
            } else if (polygon || polyline) {
                shape = polygon ? MapObject.Shape.POLYGON : MapObject.Shape.POLYLINE;
                JsonValue points = array(value.require(polygon ? "polygon" : "polyline"), "points");
                require(points.size() >= (polygon ? 3 : 2) && points.size() <= 65536, "invalid point count");
                vertices = new float[points.size() * 2];
                for (int i = 0; i < points.size(); i++) {
                    JsonValue vertex = object(points.require(i), "point");
                    vertices[i * 2] = number(vertex, "x", 0);
                    vertices[i * 2 + 1] = -number(vertex, "y", 0);
                }
            } else {
                shape = MapObject.Shape.RECTANGLE; vertices = new float[] {0, 0, width, 0, width, -height, 0, -height};
            }
            return new MapObject(id, string(value, "name", ""), className(value), shape, x, y, width, height,
                    -number(value, "rotation", 0), bool(value, "visible", true), number(value, "opacity", 1),
                    (int)(gid & 0x0fffffffL), flags(gid), vertices, properties(value));
        } catch (RuntimeException failure) { throw error("object " + id + ": " + failure.getMessage(), failure); }
    }

    private void rejectLayerFeatures(JsonValue layer) {
        require(number(layer, "x", 0) == 0 && number(layer, "y", 0) == 0, "nonzero layer tile origin is unsupported");
        require(string(layer, "mode", "normal").equals("normal") && string(layer, "blendmode", "normal").equals("normal"),
                "non-normal blend mode is unsupported");
    }

    private MapProperties properties(JsonValue parent) {
        MapProperties properties = new MapProperties();
        JsonValue array = parent.get("properties");
        if (array == null) { return properties; }
        array(array, "properties");
        require(array.size() <= 65536, "too many properties");
        for (int i = 0; i < array.size(); i++) {
            JsonValue property = object(array.require(i), "property");
            String name = string(property, "name", null), type = string(property, "type", "string");
            require(name != null && properties.find(name) == null, "missing or duplicate property name");
            require(string(property, "propertytype", "").isEmpty(), "custom property classes/enums are unsupported: " + name);
            JsonValue value = property.require("value");
            MapProperty.Type kind;
            Object data;
            switch (type) {
                case "string": kind = MapProperty.Type.STRING; data = value.stringValue(); break;
                case "file": kind = MapProperty.Type.FILE;
                    String file = value.stringValue(); data = file.isEmpty() ? "" : resolve(source, file); break;
                case "int": kind = MapProperty.Type.INT; data = exact(value); break;
                case "object": kind = MapProperty.Type.OBJECT; long object = exact(value);
                    require(object >= 0 && object <= Integer.MAX_VALUE, "invalid object reference"); data = object; break;
                case "float": kind = MapProperty.Type.FLOAT; data = value.doubleValue(); break;
                case "bool": kind = MapProperty.Type.BOOL; data = value.booleanValue(); break;
                case "color": kind = MapProperty.Type.COLOR; data = color(value.stringValue()); break;
                default: throw error("unsupported property type " + type + " for " + name);
            }
            properties.put(new MapProperty(name, kind, data));
        }
        return properties;
    }

    static String resolve(String source, String reference) {
        String value = reference.replace('\\', '/');
        if (value.isEmpty() || value.startsWith("/") || value.indexOf(':') >= 0) {
            throw new FdxException(source + ": expected a relative internal file reference: " + reference);
        }
        String normalizedSource = source.replace('\\', '/');
        int slash = normalizedSource.lastIndexOf('/');
        String combined = (slash < 0 ? "" : normalizedSource.substring(0, slash + 1)) + value;
        Array<String> parts = new Array<String>(0);
        for (String part : combined.split("/")) {
            if (part.isEmpty() || part.equals(".")) { continue; }
            if (part.equals("..")) {
                if (parts.isEmpty()) { throw new FdxException(source + ": reference escapes internal root: " + reference); }
                parts.removeIndex(parts.size() - 1);
            } else { parts.add(part); }
        }
        if (parts.isEmpty()) { throw new FdxException(source + ": empty resolved file reference"); }
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) { path.append('/'); } path.append(parts.get(i)); }
        return path.toString();
    }
    private static int color(String value) {
        if (!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) { throw new FdxException("Invalid Tiled color: " + value); }
        long bits = Long.parseLong(value.substring(1), 16);
        return value.length() == 7 ? (int)(bits << 8 | 255) : (int)(bits << 8 | bits >>> 24);
    }
    private static int flags(long gid) {
        return ((gid & 0x80000000L) != 0 ? TileTransform.FLIP_X : 0)
                | ((gid & 0x40000000L) != 0 ? TileTransform.FLIP_Y : 0)
                | ((gid & 0x20000000L) != 0 ? TileTransform.DIAGONAL : 0);
    }
    private static long gid(JsonValue value) {
        long gid = exact(value);
        if (gid < 0 || gid > 0xffffffffL) { throw new FdxException("GID must be an unsigned 32-bit integer"); }
        return gid;
    }
    private static long exact(JsonValue value) { return new BigDecimal(value.numberLiteral()).longValueExact(); }
    private int positive(JsonValue parent, String field) {
        int value = integer(parent, field, -1); require(value > 0, field + " must be positive"); return value;
    }
    private static int integer(JsonValue parent, String field, int fallback) {
        JsonValue value = parent.get(field);
        if (value == null) { return fallback; }
        long number = exact(value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) { throw new FdxException(field + " is outside integer range"); }
        return (int)number;
    }
    private static float number(JsonValue parent, String field, float fallback) {
        JsonValue value = parent.get(field);
        float number = value == null ? fallback : value.floatValue();
        if (!Float.isFinite(number)) { throw new FdxException(field + " must be finite"); }
        return number;
    }
    private static String string(JsonValue parent, String field, String fallback) {
        JsonValue value = parent.get(field); return value == null ? fallback : value.stringValue();
    }
    private static boolean bool(JsonValue parent, String field, boolean fallback) {
        JsonValue value = parent.get(field); return value == null ? fallback : value.booleanValue();
    }
    private static String className(JsonValue value) { return string(value, "class", string(value, "type", "")); }
    private static boolean present(JsonValue parent, String field) { return parent.get(field) != null; }
    private static JsonValue object(JsonValue value, String where) {
        if (value == null || !value.isObject()) { throw new FdxException(where + " must be an object"); } return value;
    }
    private static JsonValue array(JsonValue value, String where) {
        if (value == null || !value.isArray()) { throw new FdxException(where + " must be a JSON array"); } return value;
    }
    private void require(boolean condition, String message) { if (!condition) { throw error(message); } }
    private FdxException error(String message) { return new FdxException(source + ": " + message); }
    private FdxException error(String message, Throwable cause) {
        return message != null && message.startsWith(source + ":") && cause instanceof FdxException
                ? (FdxException)cause : new FdxException(source + ": " + message, cause);
    }
    static final class Reference {
        final int firstId;
        final String source;
        final TileAtlas embedded;
        Reference(int firstId, String source, TileAtlas embedded) {
            this.firstId = firstId; this.source = source; this.embedded = embedded;
        }
    }
    static final class Document {
        final String source;
        final TileMap map;
        final Reference[] references;
        final int limit;
        Document(String source, TileMap map, Reference[] references, int limit) {
            this.source = source; this.map = map; this.references = references; this.limit = limit;
        }
        TileMap assemble(Array<FdxFuture<TileAtlas>> atlases) {
            try {
                long totalAtlasTiles = 0, totalAnimationFrames = 0;
                for (int i = 0; i < references.length; i++) {
                    TileAtlas atlas = atlases.get(i).get(); totalAtlasTiles += atlas.tileCount();
                    if (totalAtlasTiles > limit) { throw new FdxException("total atlas tiles exceed limit " + limit); }
                    totalAnimationFrames += atlas.animationFrameCount();
                    if (totalAnimationFrames > limit) { throw new FdxException("total animation frames exceed limit " + limit); }
                    if ((long)references[i].firstId + atlas.localIdLimit() - 1 > 0x0fffffff) {
                        throw new FdxException("tileset global ID range exceeds the unflagged Tiled range");
                    }
                    map.addAtlas(references[i].firstId, atlas);
                }
                for (int i = 0; i < map.mapLayerCount(); i++) { validateLayer(map.mapLayer(i)); }
                return map;
            } catch (RuntimeException failure) { throw new FdxException(source + ": " + failure.getMessage(), failure); }
        }
        private void validateLayer(MapLayer layer) {
            if (layer instanceof TileLayer tiles) {
                for (int y = 0; y < tiles.height(); y++) {
                    for (int x = 0; x < tiles.width(); x++) {
                        int id = tiles.tile(x, y);
                        if (id != 0 && map.findAtlas(id) < 0) {
                            throw new FdxException("layer " + layer.id() + " cell " + x + "," + y + ": unmapped tile ID " + id);
                        }
                    }
                }
            } else if (layer instanceof ChunkedTileLayer chunks) {
                for(int i=0;i<chunks.chunkCount();i++) {
                    TileChunk chunk=chunks.chunk(i);
                    for(int y=0;y<chunk.height();y++) for(int x=0;x<chunk.width();x++) {
                        int id=chunk.tile(x,y);
                        if(id!=0 && map.findAtlas(id)<0)throw new FdxException("layer "+layer.id()+" chunk cell "
                                +((long)chunk.x()+x)+","+((long)chunk.y()+y)+": unmapped tile ID "+id);
                    }
                }
            } else if (layer instanceof ObjectLayer objects) {
                for (int j = 0; j < objects.objectCount(); j++) {
                    validate(objects.object(j).tileId(), "object " + objects.object(j).id());
                }
            } else if (layer instanceof GroupLayer group) {
                for (int i = 0; i < group.layerCount(); i++) { validateLayer(group.layer(i)); }
            }
        }
        private void validate(int id, String where) {
            if (id != 0 && map.findAtlas(id) < 0) { throw new FdxException(where + ": unmapped tile ID " + id); }
        }
    }
}
