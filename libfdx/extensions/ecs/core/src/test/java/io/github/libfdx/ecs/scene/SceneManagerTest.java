package io.github.libfdx.ecs.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.ecs.component.GameComponent;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.component.UiComponent;
import io.github.libfdx.ecs.manager.Manager;
import io.github.libfdx.ecs.schema.EcsAssetAdapter;
import io.github.libfdx.ecs.schema.EcsComponentDescriptor;
import io.github.libfdx.ecs.schema.EcsEntityPreset;
import io.github.libfdx.ecs.schema.EcsPropertyDescriptor;
import io.github.libfdx.ecs.schema.EcsTransformAdapter;
import io.github.libfdx.ecs.system.System;
import io.github.libfdx.ecs.transform.Transform;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonCodec;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SceneManagerTest {
    private static final String PROJECT_ID = "sample.basic";

    @Test
    void zeroConfigurationWorldOwnsStableMetadataAndPersistsCoreComponents() {
        World source = new World();
        SceneManager scenes = source.scenes();

        assertSame(scenes, source.scenes());
        assertEquals(0, source.managerCount());
        assertNotNull(scenes.component(TransformComponent.class));
        assertNotNull(scenes.component(GameComponent.class));
        assertNotNull(scenes.component(UiComponent.class));

        int root = scenes.create("Root");
        int child = scenes.create("Child");
        long rootId = scenes.id(root);
        long childId = scenes.id(child);
        source.add(root, new GameComponent());
        source.add(child, new UiComponent());
        TransformComponent transform = new TransformComponent(2.0f, 3.0f, 4.0f);
        transform.transform.rotation().set(0.0f, 0.0f, 0.0f, 2.0f);
        source.add(child, transform);
        source.flushCommands();
        scenes.parent(child, root);

        String encoded = scenes.write("main");
        assertTrue(encoded.contains("\"type\": \"libfdx.game\""));
        assertTrue(encoded.contains("\"type\": \"libfdx.transform\""));
        assertTrue(encoded.contains("\"type\": \"libfdx.ui\""));

        World target = new World();
        target.scenes().apply(target.scenes().read(encoded));

        assertEquals(2, target.entityCount());
        int loadedRoot = target.scenes().find(rootId);
        int loadedChild = target.scenes().find(childId);
        assertNotEquals(0, loadedRoot);
        assertNotEquals(0, loadedChild);
        assertEquals("Root", target.scenes().name(loadedRoot));
        assertEquals(rootId, target.scenes().parentId(loadedChild));
        assertTrue(target.has(loadedRoot, GameComponent.class));
        assertTrue(target.has(loadedChild, UiComponent.class));
        assertEquals(1.0f, target.require(loadedChild, TransformComponent.class).transform.rotation().w());
        assertEquals(encoded, target.scenes().write("main"));
    }

    @Test
    void reservationDiscardDestroyAndHierarchyKeepMetadataConsistent() {
        World world = new World();
        SceneManager scenes = world.scenes();

        int discarded = world.createEntity();
        long discardedId = scenes.id(discarded);
        assertEquals(discarded, scenes.find(discardedId));
        world.discardCommands();
        assertEquals(0, scenes.find(discardedId));
        assertThrows(IllegalStateException.class, () -> scenes.id(discarded));

        int root = scenes.create("Root");
        int child = scenes.create("Child");
        int grandchild = scenes.create("Grandchild");
        world.flushCommands();
        assertTrue(scenes.id(root) > discardedId);
        scenes.parent(child, root);
        scenes.parent(grandchild, child);
        assertThrows(IllegalArgumentException.class, () -> scenes.parent(root, root));
        assertThrows(IllegalArgumentException.class, () -> scenes.parent(root, grandchild));
        assertThrows(IllegalStateException.class, () -> scenes.parent(root, discarded));

        long rootId = scenes.id(root);
        world.destroyEntity(root);
        world.flushCommands();

        assertEquals(0, scenes.find(rootId));
        assertEquals(0L, scenes.parentId(child));
        int replacement = scenes.create("Replacement");
        assertTrue(scenes.id(replacement) > scenes.id(grandchild));
        assertNotEquals(root, replacement);
    }

    @Test
    void clearPreservesManagerIdentityButResetsProjectCatalogAndIds() {
        World world = new World();
        SceneManager scenes = world.scenes();
        EcsTransformAdapter customTransforms = new EcsTransformAdapter() {
            public Transform transform(World owner, int entity) {
                return null;
            }

            public void add(World owner, int entity) {
            }
        };
        scenes.projectId(PROJECT_ID)
                .component(valueDescriptor())
                .transientComponent(TransientComponent.class)
                .preset(new EcsEntityPreset() {
                    public String id() {
                        return "sample.empty";
                    }

                    public String name() {
                        return "Empty";
                    }

                    public void populate(World owner, int entity) {
                    }
                })
                .transforms(customTransforms)
                .assets(ASSETS);
        world.addManager(new CountingManager(), CountingManager.class);
        int entity = scenes.create("Before clear");
        world.add(entity, new ValueComponent());
        world.flushCommands();

        assertEquals(1, world.managerCount());
        assertEquals(PROJECT_ID, scenes.projectId());
        assertSame(customTransforms, scenes.transforms());
        assertNotNull(scenes.component(ValueComponent.class));
        assertNotNull(scenes.preset("sample.empty"));

        world.clear();
        world.flushCommands();

        assertSame(scenes, world.scenes());
        assertEquals(0, world.managerCount());
        assertEquals(0, world.entityCount());
        assertEquals(SceneManager.DEFAULT_PROJECT_ID, scenes.projectId());
        assertNull(scenes.component(ValueComponent.class));
        assertNull(scenes.preset("sample.empty"));
        assertNull(scenes.assets());
        assertNotNull(scenes.component(TransformComponent.class));
        assertNotNull(scenes.component(GameComponent.class));
        assertNotNull(scenes.component(UiComponent.class));
        assertNotNull(scenes.transforms());
        assertFalse(scenes.transforms() == customTransforms);

        int afterClear = scenes.create("After clear");
        assertEquals(1L, scenes.id(afterClear));
    }

    @Test
    void customDescriptorsRoundTripDeterministicallyAndPreserveWorldSystemsAndManagers() {
        World source = configuredWorld();
        SceneManager sourceScenes = source.scenes();
        int child = sourceScenes.create(2L, "Child");
        int root = sourceScenes.create(1L, "Root");
        source.add(child, new ValueComponent(7, 1L, "sprites/player.png", true, 4.0f, 5.0f));
        TransformComponent transform = new TransformComponent(2.0f, 3.0f, 4.0f);
        transform.transform.rotation().set(0.0f, 0.0f, 0.0f, 2.0f);
        source.add(child, transform);
        source.add(root, new ValueComponent(3, 0L, "tiles/wall.png", false, 1.0f, 2.0f));
        source.flushCommands();
        sourceScenes.parent(child, root);

        String encoded = sourceScenes.write("main");

        assertTrue(encoded.indexOf("\"id\": 1") < encoded.indexOf("\"id\": 2"));
        int childEntityOffset = encoded.indexOf("\"id\": 2");
        String childEntityJson = encoded.substring(childEntityOffset);
        assertTrue(childEntityJson.indexOf("sample.transform") < childEntityJson.indexOf("sample.value"));
        EcsSceneDocument decoded = sourceScenes.read(encoded);
        assertEquals(2, decoded.entityCount());

        World target = configuredWorld();
        CountingSystem system = target.addSystem(new CountingSystem());
        CountingManager manager = target.addManager(new CountingManager(), CountingManager.class);
        int old = target.scenes().create(99L, "Old");
        target.add(old, new ValueComponent(1, 0L, "tiles/wall.png", true, 0.0f, 0.0f));
        target.flushCommands();

        target.scenes().apply(decoded);

        assertSame(system, target.getSystem(CountingSystem.class));
        assertSame(manager, target.getManager(CountingManager.class));
        assertEquals(2, target.entityCount());
        int loadedChild = target.scenes().find(2L);
        ValueComponent value = target.require(loadedChild, ValueComponent.class);
        assertEquals(7, value.score);
        assertEquals(1L, value.targetId);
        assertEquals("sprites/player.png", value.asset);
        assertEquals(1L, target.scenes().parentId(loadedChild));
        TransformComponent loadedTransform = target.require(loadedChild, TransformComponent.class);
        assertEquals(1.0f, loadedTransform.transform.rotation().w());
        assertEquals(encoded, target.scenes().write("main"));
    }

    @Test
    void captureRejectsUndeclaredCustomComponentsAndSkipsExplicitTransientTypes() {
        World world = new World();
        int entity = world.scenes().create("Transient");
        world.add(entity, new TransientComponent());
        world.flushCommands();

        EcsSceneException failure =
                assertThrows(EcsSceneException.class, () -> world.scenes().capture("main"));
        assertTrue(failure.getMessage().contains(TransientComponent.class.getName()));

        world.scenes().transientComponent(TransientComponent.class);
        String encoded = world.scenes().write("main");
        assertFalse(encoded.contains(TransientComponent.class.getName()));
        assertEquals(1, world.scenes().read(encoded).entityCount());
    }

    @Test
    void captureValidatesLivePersistentReferencesAndAssets() {
        World world = configuredWorld();
        int entity = world.scenes().create(1L, "Invalid");
        ValueComponent value = new ValueComponent(
                1, 99L, "tiles/wall.png", true, 0.0f, 0.0f);
        world.add(entity, value);
        world.flushCommands();

        EcsSceneException missingReference =
                assertThrows(EcsSceneException.class, () -> world.scenes().capture("main"));
        assertTrue(missingReference.getMessage().contains("references missing entity 99"));

        value.targetId = 0L;
        value.asset = "/outside.png";
        EcsSceneException invalidAsset =
                assertThrows(EcsSceneException.class, () -> world.scenes().capture("main"));
        assertTrue(invalidAsset.getMessage().contains("sample.value.asset"));
    }

    @Test
    void captureSkipsPropertyValidationForTransientDescriptors() {
        World world = new World();
        world.scenes().component(EcsComponentDescriptor.builder(
                        "sample.transient-reference",
                        "Transient Reference",
                        TransientReferenceComponent.class,
                        TransientReferenceComponent::new)
                .transientComponent()
                .property(EcsPropertyDescriptor.entityReferenceProperty(
                        "target",
                        "Target",
                        TransientReferenceComponent.class,
                        new EcsPropertyDescriptor.LongAccessor<>() {
                            public long get(TransientReferenceComponent value) {
                                return value.targetId;
                            }

                            public void set(TransientReferenceComponent value, long targetId) {
                                value.targetId = targetId;
                            }
                        }))
                .build());
        int entity = world.scenes().create("Transient");
        world.add(entity, new TransientReferenceComponent(99L));
        world.flushCommands();

        String encoded = world.scenes().write("main");

        assertFalse(encoded.contains("sample.transient-reference"));
        assertEquals(1, world.scenes().read(encoded).entityCount());
    }

    @Test
    void rejectsUnknownTypesMissingReferencesAndHierarchyCyclesBeforeMutation() {
        World world = configuredWorld();
        int entity = world.scenes().create(1L, "Original");
        world.add(entity, new ValueComponent(1, 0L, "tiles/wall.png", true, 0.0f, 0.0f));
        world.flushCommands();
        String valid = world.scenes().write("main");

        assertThrows(EcsSceneException.class,
                () -> world.scenes().read(valid.replace("sample.value", "sample.unknown")));
        assertThrows(EcsSceneException.class,
                () -> world.scenes().read(valid.replace("\"target\": 0", "\"target\": 99")));

        EcsSceneDocument unknown = new EcsSceneDocument(PROJECT_ID, "unknown", List.of(
                new EcsSceneEntity(1L, "Unknown", 0L, List.of(
                        new EcsSceneComponent("sample.unknown", JsonValue.object())))));
        assertThrows(EcsSceneException.class, () -> world.scenes().write(unknown));

        EcsSceneDocument cycle = new EcsSceneDocument(PROJECT_ID, "cycle", List.of(
                new EcsSceneEntity(1L, "A", 2L, List.of()),
                new EcsSceneEntity(2L, "B", 1L, List.of())));
        assertThrows(EcsSceneException.class, () -> world.scenes().write(cycle));
        assertEquals(1, world.entityCount());
        assertEquals(1L, world.scenes().id(entity));
        assertEquals("Original", world.scenes().name(entity));
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

    private static World configuredWorld() {
        World world = new World();
        world.scenes()
                .projectId(PROJECT_ID)
                .component(valueDescriptor())
                .component(EcsComponentDescriptor.builder(
                                "sample.transform", "Transform", TransformComponent.class, TransformComponent::new)
                        .persistent(new TransformComponentJsonCodec())
                        .build())
                .assets(ASSETS);
        return world;
    }

    private static EcsComponentDescriptor<ValueComponent> valueDescriptor() {
        return EcsComponentDescriptor.builder(
                        "sample.value", "Value", ValueComponent.class, ValueComponent::new)
                .persistent(new ValueCodec())
                .property(EcsPropertyDescriptor.booleanProperty(
                        "visible", "Visible", ValueComponent.class, new EcsPropertyDescriptor.BooleanAccessor<>() {
                            public boolean get(ValueComponent value) {
                                return value.visible;
                            }

                            public void set(ValueComponent value, boolean visible) {
                                value.visible = visible;
                            }
                        }))
                .property(EcsPropertyDescriptor.integerProperty(
                        "score", "Score", ValueComponent.class, new EcsPropertyDescriptor.IntAccessor<>() {
                            public int get(ValueComponent value) {
                                return value.score;
                            }

                            public void set(ValueComponent value, int score) {
                                value.score = score;
                            }
                        }))
                .property(EcsPropertyDescriptor.vectorProperty(
                        "position", "Position", ValueComponent.class, 2,
                        new EcsPropertyDescriptor.FloatAccessor<>() {
                            public float get(ValueComponent value, int element) {
                                return element == 0 ? value.x : value.y;
                            }

                            public void set(ValueComponent value, int element, float number) {
                                if (element == 0) {
                                    value.x = number;
                                } else {
                                    value.y = number;
                                }
                            }
                        }))
                .property(EcsPropertyDescriptor.assetProperty(
                        "asset", "Asset", ValueComponent.class, new EcsPropertyDescriptor.TextAccessor<>() {
                            public String get(ValueComponent value) {
                                return value.asset;
                            }

                            public void set(ValueComponent value, String asset) {
                                value.asset = asset;
                            }
                        }))
                .property(EcsPropertyDescriptor.entityReferenceProperty(
                        "target", "Target", ValueComponent.class, new EcsPropertyDescriptor.LongAccessor<>() {
                            public long get(ValueComponent value) {
                                return value.targetId;
                            }

                            public void set(ValueComponent value, long target) {
                                value.targetId = target;
                            }
                        }))
                .build();
    }

    private static final EcsAssetAdapter ASSETS = new EcsAssetAdapter() {
        @Override
        public String normalize(String path) {
            if (path == null || path.isBlank() || path.startsWith("/")
                    || path.contains("../") || path.contains("\\..")) {
                throw new IllegalArgumentException("asset must be project-relative.");
            }
            return path.replace('\\', '/');
        }

        @Override
        public boolean accepts(String path) {
            return path.endsWith(".png");
        }
    };

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

    static final class TransientComponent implements Component {
    }

    static final class TransientReferenceComponent implements Component {
        long targetId;

        TransientReferenceComponent() {
        }

        TransientReferenceComponent(long targetId) {
            this.targetId = targetId;
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    static final class CountingManager implements Manager {
        public void onAttach(World world) {
        }

        public void onDetach(World world) {
        }
    }
}
