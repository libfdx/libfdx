package io.github.libfdx.storage;

import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonCodec;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class StorageTest {
    @TempDir
    Path temp;

    @Test
    void storesValuesAcrossInstances() {
        Storage storage = storage(temp);
        JsonValue profile = JsonValue.object()
                .put("name", "Ada")
                .put("level", 3);

        storage.local("settings")
                .load()
                .putString("name", "Ada")
                .putInt("volume", 80)
                .putLong("score", 123456789L)
                .putFloat("scale", 1.5f)
                .putDouble("gamma", 2.2)
                .putBoolean("fullscreen", true)
                .putBytes("blob", new byte[] { 1, 2, 3 })
                .putJson("profile", profile)
                .flush();

        KeyValueStore reopened = storage(temp).local("settings").load();
        assertEquals("Ada", reopened.getString("name", ""));
        assertEquals(80, reopened.getInt("volume", 0));
        assertEquals(123456789L, reopened.getLong("score", 0L));
        assertEquals(1.5f, reopened.getFloat("scale", 0.0f), 0.0001f);
        assertEquals(2.2, reopened.getDouble("gamma", 0.0), 0.0001);
        assertTrue(reopened.getBoolean("fullscreen", false));
        assertArrayEquals(new byte[] { 1, 2, 3 }, reopened.getBytes("blob", null));
        assertEquals("Ada", reopened.getJson("profile", JsonValue.object()).stringValue("name", ""));
        assertFalse(reopened.dirty());
    }

    @Test
    void removesAndClearsValues() {
        KeyValueStore store = storage(temp).local("settings").load();
        store.putString("a", "one")
                .putString("b", "two")
                .remove("a")
                .flush();

        KeyValueStore reopened = storage(temp).local("settings").load();
        assertFalse(reopened.contains("a"));
        assertTrue(reopened.contains("b"));

        reopened.clear().flush();
        assertEquals(0, storage(temp).local("settings").load().keys().length);
    }

    @Test
    void storesTypedJsonWithExplicitCodec() {
        Json json = new Json().register(Player.class, new JsonCodec<Player>() {
            @Override
            public Player read(Json json, JsonValue value) {
                return new Player(value.stringValue("name", ""), value.intValue("level", 1));
            }

            @Override
            public void write(Json json, JsonWriter writer, Player value) {
                writer.object()
                        .name("name").value(value.name)
                        .name("level").value(value.level)
                        .endObject();
            }
        });

        storage(temp).local("save")
                .load()
                .putJson("player", Player.class, json, new Player("Ada", 7))
                .flush();

        Player player = storage(temp).local("save").load().getJson("player", Player.class, json, null);
        assertEquals("Ada", player.name);
        assertEquals(7, player.level);
    }

    @Test
    void codecTransformsPersistedBytes() throws Exception {
        StorageCodec codec = new XorCodec((byte) 0x5A);
        storage(temp).local("secret", codec)
                .load()
                .putString("token", "plain-text")
                .flush();

        Path storedFile = temp.resolve("local").resolve("storage").resolve("secret.json");
        String raw = Files.readString(storedFile);
        assertFalse(raw.contains("plain-text"));

        KeyValueStore reopened = storage(temp).local("secret", codec).load();
        assertEquals("plain-text", reopened.getString("token", ""));
    }

    private static Storage storage(Path root) {
        return new DefaultStorage(new DefaultFileSystem(root.resolve("local").toFile(),
                root.resolve("external").toFile(), root.resolve("cache").toFile()));
    }

    private static final class Player {
        private final String name;
        private final int level;

        private Player(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }

    private static final class XorCodec implements StorageCodec {
        private final byte key;

        private XorCodec(byte key) {
            this.key = key;
        }

        @Override
        public byte[] encode(byte[] bytes) {
            return transform(bytes);
        }

        @Override
        public byte[] decode(byte[] bytes) {
            return transform(bytes);
        }

        private byte[] transform(byte[] bytes) {
            byte[] output = StorageCodecs.copy(bytes);
            for (int i = 0; i < output.length; i++) {
                output[i] = (byte)(output[i] ^ key);
            }
            return output;
        }
    }
}
