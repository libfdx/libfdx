package io.github.libfdx.assets.loaders;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AtlasDataTest {
    @Test void roundTripRetainsOriginalGeometryEscapingAndCopiesMembership() {
        AtlasData.Page[] pages={new AtlasData.Page("hero-0.png",64,32)};
        String name="walk/\u00f1\"\\pose";
        AtlasData.Sprite[] sprites={new AtlasData.Sprite(name,0,3,5,12,16,32,24,2,1,.25f,0)};
        AtlasData data=new AtlasData(pages,sprites); pages[0]=null; sprites[0]=null;
        AtlasData parsed=parse(data.encode());
        assertEquals(data.encode(),parsed.encode()); assertEquals(0,parsed.indexOf(name));
        assertEquals(1,new JsonReader().parse(data.encode()).require("version").intValue());
        assertEquals(-1,parsed.indexOf("missing")); assertEquals(2,parsed.sprite(0).trimX());
        assertEquals(1,parsed.sprite(0).trimY()); assertEquals(.25f,parsed.sprite(0).pivotX());
    }
    @Test void acceptsReorderedAndUnknownPropertiesAtEveryLevel() {
        AtlasData data=parse("""
                {
                  "editor": {"revision": 3},
                  "sprites": [{"pivotY": 0.5, "pivotX": 0.25, "trimY": 0, "trimX": 0,
                    "originalHeight": 2, "originalWidth": 2, "height": 2, "width": 2,
                    "y": 0, "x": 0, "page": 0, "name": "hero", "tags": ["idle"]}],
                  "pages": [{"height": 8, "width": 8, "image": "page.png", "checksum": "future"}],
                  "alpha": "straight", "version": 1
                }
                """);
        assertEquals("hero",data.sprite(0).name());
        assertEquals(.25f,data.sprite(0).pivotX());
        assertEquals(data.encode(),parse(data.encode()).encode());
    }
    @Test void rejectsInvalidJsonRequiredFieldsAndOldText() {
        for(String json:new String[]{"{}", "[]", "null", "{", "libfdx-atlas 1 straight\n",
                "{\"version\":1,\"alpha\":\"straight\",\"pages\":{},\"sprites\":[]}",
                "{\"version\":1,\"alpha\":\"straight\",\"pages\":[],\"sprites\":[{}]}"}) {
            assertThrows(FdxException.class,() -> parse(json),json);
        }
        for(JsonValue value:new JsonValue[]{JsonValue.value(2),JsonValue.value("1"),JsonValue.value(1.5),JsonValue.nullValue()}) {
            JsonValue root=valid(); root.put("version",value);
            assertThrows(FdxException.class,() -> parse(JsonWriter.compact(root)));
        }
        JsonValue root=valid().put("alpha","premultiplied");
        assertThrows(FdxException.class,() -> parse(JsonWriter.compact(root)));
        assertThrows(FdxException.class,() -> AtlasData.parse(new byte[AtlasData.MAX_BYTES+1]));
    }
    @Test void rejectsUnsafePathsInvalidCropReferencesAndNumericCoercion() {
        for(String path:new String[]{"../page.png","/page.png","a//b.png","a/./b.png","C:page.png","a\\b.png"}) {
            JsonValue root=valid(); root.require("pages").require(0).put("image",path);
            assertThrows(FdxException.class,() -> parse(JsonWriter.compact(root)));
        }
        for(String field:new String[]{"x","page","trimX","width"}) {
            JsonValue root=valid(); root.require("sprites").require(0).put(field,Integer.MAX_VALUE);
            assertThrows(FdxException.class,() -> parse(JsonWriter.compact(root)),field);
        }
        for(JsonValue value:new JsonValue[]{JsonValue.value(1.5),JsonValue.value(4294967296L),JsonValue.value("0"),JsonValue.nullValue()}) {
            JsonValue root=valid(); root.require("sprites").require(0).put("page",value);
            assertThrows(FdxException.class,() -> parse(JsonWriter.compact(root)));
        }
        for(JsonValue value:new JsonValue[]{JsonValue.value(-.1),JsonValue.value(1.1),JsonValue.value("NaN")}) {
            JsonValue root=valid(); root.require("sprites").require(0).put("pivotX",value);
            assertThrows(FdxException.class,() -> parse(JsonWriter.compact(root)));
        }
        JsonValue root=valid(); root.require("sprites").add(root.require("sprites").require(0));
        assertThrows(FdxException.class,() -> parse(JsonWriter.compact(root)));
    }
    @Test void retainsMembershipAndPixelLimits() {
        JsonValue root=valid(); JsonValue pages=JsonValue.array();
        for(int i=0;i<=AtlasData.MAX_PAGES;i++) pages.add(root.require("pages").require(0));
        root.put("pages",pages);
        assertThrows(FdxException.class,() -> parse(JsonWriter.compact(root)));
        JsonValue oversized=valid(); oversized.require("pages").require(0).put("width",16384).put("height",16384);
        assertThrows(FdxException.class,() -> parse(JsonWriter.compact(oversized)));
    }
    private static AtlasData parse(String json) { return AtlasData.parse(json.getBytes(StandardCharsets.UTF_8)); }
    private static JsonValue valid() {
        return new JsonReader().parse("""
                {"version":1,"alpha":"straight","pages":[{"image":"page.png","width":8,"height":8}],
                 "sprites":[{"name":"hero","page":0,"x":0,"y":0,"width":2,"height":2,
                   "originalWidth":2,"originalHeight":2,"trimX":0,"trimY":0,"pivotX":0.5,"pivotY":0.5}]}
                """);
    }
}
