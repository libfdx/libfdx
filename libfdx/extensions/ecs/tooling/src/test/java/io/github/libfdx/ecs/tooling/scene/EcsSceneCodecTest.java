package io.github.libfdx.ecs.tooling.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.collections.IntArray;
import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.system.System;
import io.github.libfdx.ecs.tooling.EcsProject;
import io.github.libfdx.ecs.tooling.EcsProjectRuntime;
import io.github.libfdx.ecs.tooling.schema.EcsAssetAdapter;
import io.github.libfdx.ecs.tooling.schema.EcsComponentDescriptor;
import io.github.libfdx.ecs.tooling.schema.EcsEntityAdapter;
import io.github.libfdx.ecs.tooling.schema.EcsProjectSchema;
import io.github.libfdx.ecs.tooling.schema.EcsPropertyDescriptor;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonCodec;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;
import org.junit.jupiter.api.Test;

final class EcsSceneCodecTest {
    private static final String PROJECT_ID = "sample.basic";

    @Test
    void roundTripsDeterministicallyAndPreservesWorldSystems() {
        EcsProjectSchema schema = schema();
        EcsSceneCodec codec = new EcsSceneCodec(project(schema));
        World source = new World();
        int child = METADATA.create(source, 2L, "Child");
        int root = METADATA.create(source, 1L, "Root");
        source.add(child, new ValueComponent(7, 1L, "sprites/player.png", true, 4.0f, 5.0f));
        TransformComponent transform = new TransformComponent(2.0f, 3.0f, 4.0f);
        transform.transform.rotation().set(0.0f, 0.0f, 0.0f, 2.0f);
        source.add(child, transform);
        source.add(root, new ValueComponent(3, 0L, "tiles/wall.png", false, 1.0f, 2.0f));
        source.flushCommands();
        METADATA.parentId(source, child, 1L);

        String encoded = codec.write(source, "main");

        assertTrue(encoded.indexOf("\"id\": 1") < encoded.indexOf("\"id\": 2"));
        int childEntityOffset = encoded.indexOf("\"id\": 2");
        String childEntityJson = encoded.substring(childEntityOffset);
        assertTrue(childEntityJson.indexOf("sample.transform") < childEntityJson.indexOf("sample.value"));
        EcsSceneDocument decoded = codec.read(encoded);
        assertEquals(2, decoded.entityCount());

        World target = new World();
        CountingSystem system = target.addSystem(new CountingSystem());
        int old = METADATA.create(target, 99L, "Old");
        target.add(old, new ValueComponent(1, 0L, "tiles/wall.png", true, 0.0f, 0.0f));
        target.flushCommands();

        codec.apply(target, decoded);

        assertSame(system, target.getSystem(CountingSystem.class));
        assertEquals(2, target.entityCount());
        int loadedChild = findById(target, 2L);
        ValueComponent value = target.require(loadedChild, ValueComponent.class);
        assertEquals(7, value.score);
        assertEquals(1L, value.targetId);
        assertEquals("sprites/player.png", value.asset);
        assertEquals(1L, METADATA.parentId(target, loadedChild));
        TransformComponent loadedTransform = target.require(loadedChild, TransformComponent.class);
        assertEquals(1.0f, loadedTransform.transform.rotation().w());
        assertEquals(encoded, codec.write(target, "main"));
    }

    @Test
    void rejectsUnknownTypesMissingReferencesAndHierarchyCyclesBeforeMutation() {
        EcsSceneCodec codec = new EcsSceneCodec(project(schema()));
        World world = new World();
        int entity = METADATA.create(world, 1L, "Original");
        world.add(entity, new ValueComponent(1, 0L, "tiles/wall.png", true, 0.0f, 0.0f));
        world.flushCommands();
        String valid = codec.write(world, "main");

        assertThrows(EcsSceneException.class,
                () -> codec.read(valid.replace("sample.value", "sample.unknown")));
        assertThrows(EcsSceneException.class,
                () -> codec.read(valid.replace("\"target\": 0", "\"target\": 99")));

        EcsSceneDocument cycle = new EcsSceneDocument(PROJECT_ID, "cycle", java.util.List.of(
                new EcsSceneEntity(1L, "A", 2L, java.util.List.of()),
                new EcsSceneEntity(2L, "B", 1L, java.util.List.of())));
        assertThrows(EcsSceneException.class, () -> codec.write(cycle));
        assertEquals(1, world.entityCount());
        assertEquals(1L, METADATA.persistentId(world, entity));
    }

    @Test
    void exposesPrimitivePropertyAccessWithoutBoxingContracts() {
        EcsComponentDescriptor<ValueComponent> descriptor = valueDescriptor();
        ValueComponent component = descriptor.create();

        descriptor.property("visible").booleanValue(component, true);
        descriptor.property("score").intValue(component, 12);
        descriptor.property("position").floatValue(component, 0, 8.0f);
        descriptor.property("position").floatValue(component, 1, 9.0f);
        descriptor.property("asset").textValue(component, "sprites/player.png");
        descriptor.property("target").entityReference(component, 44L);

        assertTrue(descriptor.property("visible").booleanValue(component));
        assertEquals(12, descriptor.property("score").intValue(component));
        assertEquals(8.0f, descriptor.property("position").floatValue(component, 0));
        assertEquals(9.0f, descriptor.property("position").floatValue(component, 1));
        assertEquals("sprites/player.png", descriptor.property("asset").textValue(component));
        assertEquals(44L, descriptor.property("target").entityReference(component));
    }

    private static EcsProjectSchema schema() {
        return EcsProjectSchema.builder(METADATA)
                .component(EcsComponentDescriptor.builder(
                                "sample.metadata", "Metadata", MetadataComponent.class, MetadataComponent::new)
                        .transientComponent()
                        .build())
                .component(valueDescriptor())
                .component(EcsComponentDescriptor.builder(
                                "sample.transform", "Transform", TransformComponent.class, TransformComponent::new)
                        .persistent(new TransformComponentJsonCodec())
                        .build())
                .assets(new EcsAssetAdapter() {
                    @Override
                    public String normalize(String path) {
                        return EcsProject.normalizeRelativePath(path, "asset");
                    }

                    @Override
                    public boolean accepts(String path) {
                        return path.endsWith(".png");
                    }
                })
                .build();
    }

    private static EcsProject project(EcsProjectSchema schema) {
        return new EcsProject(PROJECT_ID, "Basic", "assets", "scenes/main.fdxscene") {
            @Override
            public EcsProjectSchema schema() {
                return schema;
            }

            @Override
            public EcsProjectRuntime createRuntime() {
                throw new UnsupportedOperationException("Scene codec tests do not create a runtime.");
            }
        };
    }

    private static EcsComponentDescriptor<ValueComponent> valueDescriptor() {
        return EcsComponentDescriptor.builder(
                        "sample.value", "Value", ValueComponent.class, ValueComponent::new)
                .persistent(new ValueCodec())
                .property(EcsPropertyDescriptor.booleanProperty(
                        "visible", "Visible", ValueComponent.class, new EcsPropertyDescriptor.BooleanAccessor<>() {
                            public boolean get(ValueComponent value) { return value.visible; }
                            public void set(ValueComponent value, boolean visible) { value.visible = visible; }
                        }))
                .property(EcsPropertyDescriptor.integerProperty(
                        "score", "Score", ValueComponent.class, new EcsPropertyDescriptor.IntAccessor<>() {
                            public int get(ValueComponent value) { return value.score; }
                            public void set(ValueComponent value, int score) { value.score = score; }
                        }))
                .property(EcsPropertyDescriptor.vectorProperty(
                        "position", "Position", ValueComponent.class, 2,
                        new EcsPropertyDescriptor.FloatAccessor<>() {
                            public float get(ValueComponent value, int element) {
                                return element == 0 ? value.x : value.y;
                            }
                            public void set(ValueComponent value, int element, float number) {
                                if (element == 0) value.x = number; else value.y = number;
                            }
                        }))
                .property(EcsPropertyDescriptor.assetProperty(
                        "asset", "Asset", ValueComponent.class, new EcsPropertyDescriptor.TextAccessor<>() {
                            public String get(ValueComponent value) { return value.asset; }
                            public void set(ValueComponent value, String asset) { value.asset = asset; }
                        }))
                .property(EcsPropertyDescriptor.entityReferenceProperty(
                        "target", "Target", ValueComponent.class, new EcsPropertyDescriptor.LongAccessor<>() {
                            public long get(ValueComponent value) { return value.targetId; }
                            public void set(ValueComponent value, long target) { value.targetId = target; }
                        }))
                .build();
    }

    private static int findById(World world, long id) {
        IntArray entities = world.collectEntities(new IntArray());
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (METADATA.persistentId(world, entity) == id) {
                return entity;
            }
        }
        throw new AssertionError("Missing entity " + id);
    }

    private static final EcsEntityAdapter METADATA = new EcsEntityAdapter() {
        @Override
        public int create(World world, long id, String name) {
            int entity = world.createEntity();
            world.add(entity, new MetadataComponent(id, name, 0L));
            return entity;
        }

        @Override
        public long persistentId(World world, int entity) {
            return world.require(entity, MetadataComponent.class).id;
        }

        @Override
        public String name(World world, int entity) {
            return world.require(entity, MetadataComponent.class).name;
        }

        @Override
        public void name(World world, int entity, String name) {
            world.require(entity, MetadataComponent.class).name = name;
        }

        @Override
        public long parentId(World world, int entity) {
            return world.require(entity, MetadataComponent.class).parentId;
        }

        @Override
        public void parentId(World world, int entity, long parentId) {
            world.require(entity, MetadataComponent.class).parentId = parentId;
        }
    };

    static final class MetadataComponent implements Component {
        long id;
        String name = "";
        long parentId;

        MetadataComponent() {
        }

        MetadataComponent(long id, String name, long parentId) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
        }
    }

    static final class ValueComponent implements Component {
        int score;
        long targetId;
        String asset = "tiles/wall.png";
        boolean visible;
        float x;
        float y;

        ValueComponent() {
        }

        ValueComponent(int score, long targetId, String asset, boolean visible, float x, float y) {
            this.score = score;
            this.targetId = targetId;
            this.asset = asset;
            this.visible = visible;
            this.x = x;
            this.y = y;
        }
    }

    static final class ValueCodec implements JsonCodec<ValueComponent> {
        @Override
        public ValueComponent read(Json json, JsonValue value) {
            JsonValue position = value.require("position");
            return new ValueComponent(
                    value.require("score").intValue(),
                    value.require("target").longValue(),
                    value.require("asset").stringValue(),
                    value.require("visible").booleanValue(),
                    position.require(0).floatValue(),
                    position.require(1).floatValue());
        }

        @Override
        public void write(Json json, JsonWriter writer, ValueComponent value) {
            writer.object()
                    .name("score").value(value.score)
                    .name("target").value(value.targetId)
                    .name("asset").value(value.asset)
                    .name("visible").value(value.visible)
                    .name("position").array().value(value.x).value(value.y).endArray()
                    .endObject();
        }
    }

    static final class CountingSystem implements System {
        boolean enabled = true;

        public void onAttach(World world) {
        }

        public void onDetach(World world) {
        }

        public void update() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
