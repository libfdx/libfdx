package io.github.libfdx.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JsonTest {
    @Test
    void parsesTypedTree() {
        JsonValue root = new JsonReader().parse("{\"name\":\"Ada\",\"level\":3,\"active\":true,"
                + "\"items\":[\"one\",2,null],\"escaped\":\"line\\ntext\",\"unicode\":\"\\u0041\"}");

        assertTrue(root.isObject());
        assertEquals("Ada", root.requireString("name"));
        assertEquals(3, root.intValue("level", 0));
        assertTrue(root.booleanValue("active", false));
        assertEquals("one", root.require("items").require(0).stringValue());
        assertEquals(2, root.require("items").require(1).intValue());
        assertTrue(root.require("items").require(2).isNull());
        assertEquals("line\ntext", root.requireString("escaped"));
        assertEquals("A", root.requireString("unicode"));
    }

    @Test
    void writesCompactAndPrettyJson() {
        JsonValue root = JsonValue.object()
                .put("name", "Ada")
                .put("level", 3)
                .put("items", JsonValue.array().add("one").add(2).add(JsonValue.nullValue()));

        String compact = JsonWriter.compact(root);
        assertEquals("{\"name\":\"Ada\",\"level\":3,\"items\":[\"one\",2,null]}", compact);
        assertTrue(JsonWriter.pretty(root).indexOf('\n') >= 0);
        assertEquals(2, new JsonReader().parse(compact).require("items").require(1).intValue());
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(RuntimeException.class, () -> new JsonReader().parse("{\"bad\":01}"));
    }

    @Test
    void mapsObjectsThroughManualCodec() {
        Json json = new Json();
        json.register(Player.class, new JsonCodec<Player>() {
            @Override
            public Player read(Json json, JsonValue value) {
                return new Player(value.requireString("name"), value.intValue("level", 1));
            }

            @Override
            public void write(Json json, JsonWriter writer, Player value) {
                writer.object()
                        .name("name").value(value.name)
                        .name("level").value(value.level)
                        .endObject();
            }
        });

        Player player = json.fromJson(Player.class, "{\"name\":\"Ada\",\"level\":4}");

        assertEquals("Ada", player.name);
        assertEquals(4, player.level);
        assertEquals("{\"name\":\"Ada\",\"level\":4}", json.toJson(Player.class, player));
    }

    private static final class Player {
        private final String name;
        private final int level;

        Player(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }
}
