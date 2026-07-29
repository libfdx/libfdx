package io.github.libfdx.ecs.scene;

import io.github.libfdx.collections.IntArray;
import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.ecs.schema.EcsAssetAdapter;
import io.github.libfdx.ecs.schema.EcsComponentDescriptor;
import io.github.libfdx.ecs.schema.EcsPropertyDescriptor;
import io.github.libfdx.ecs.schema.EcsPropertyKind;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Internal deterministic scene capture, validation, and application implementation. */
final class SceneCodec {
    private final SceneManager manager;
    private final World world;
    private final Json json = new Json();
    private final IntArray entityBuffer = new IntArray();

    SceneCodec(SceneManager manager) {
        this.manager = manager;
        world = manager.world();
    }

    EcsSceneDocument capture(String sceneId) {
        if (isBlank(sceneId)) {
            throw new IllegalArgumentException("sceneId cannot be blank.");
        }
        world.flushCommands();
        world.collectEntities(entityBuffer);

        List<EcsSceneEntity> entities = new ArrayList<>(entityBuffer.size());
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < entityBuffer.size(); i++) {
            int entity = entityBuffer.get(i);
            long id = manager.id(entity);
            if (id <= 0L || !ids.add(id)) {
                throw new EcsSceneException("Scene entity IDs must be unique and positive: " + id);
            }
            List<EcsSceneComponent> components = new ArrayList<>();
            int componentCount = world.componentTypeCount(entity);
            for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
                Class<? extends Component> type = world.componentType(entity, componentIndex);
                EcsComponentDescriptor<?> descriptor = manager.component(type);
                if (descriptor == null) {
                    if (manager.transientType(type)) {
                        continue;
                    }
                    throw new EcsSceneException("Unregistered component type on entity " + id + ": "
                            + type.getName());
                }
                if (!descriptor.persistent()) {
                    continue;
                }
                Component component = descriptor.get(world, entity);
                if (component == null) {
                    throw new EcsSceneException("Missing registered component " + descriptor.id()
                            + " on entity " + id);
                }
                components.add(new EcsSceneComponent(descriptor.id(), descriptor.write(json, component)));
            }
            components.sort(Comparator.comparing(EcsSceneComponent::typeId));
            entities.add(new EcsSceneEntity(id, manager.name(entity), manager.parentId(entity), components));
        }
        entities.sort(Comparator.comparingLong(EcsSceneEntity::id));
        EcsSceneDocument document = new EcsSceneDocument(manager.projectId(), sceneId, entities);
        validateStructure(document);
        validateLiveProperties(document);
        return document;
    }

    String write(String sceneId) {
        return write(capture(sceneId));
    }

    String write(EcsSceneDocument document) {
        prepare(document);
        JsonValue root = JsonValue.object()
                .put("format", EcsSceneFormat.FORMAT)
                .put("version", EcsSceneFormat.VERSION)
                .put("project", document.projectId())
                .put("scene", document.sceneId());
        JsonValue entities = JsonValue.array();
        for (int i = 0; i < document.entityCount(); i++) {
            EcsSceneEntity entity = document.entity(i);
            JsonValue value = JsonValue.object()
                    .put("id", entity.id())
                    .put("name", entity.name())
                    .put("parent", entity.parentId());
            JsonValue components = JsonValue.array();
            for (int componentIndex = 0; componentIndex < entity.componentCount(); componentIndex++) {
                EcsSceneComponent component = entity.component(componentIndex);
                components.add(JsonValue.object()
                        .put("type", component.typeId())
                        .put("data", component.data()));
            }
            value.put("components", components);
            entities.add(value);
        }
        root.put("entities", entities);
        return json.writePretty(root) + "\n";
    }

    EcsSceneDocument read(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null.");
        }
        try {
            JsonValue root = json.read(text);
            if (!root.isObject()) {
                throw new EcsSceneException("Scene root must be a JSON object.");
            }
            String format = root.require("format").stringValue();
            int version = root.require("version").intValue();
            if (!EcsSceneFormat.FORMAT.equals(format)) {
                throw new EcsSceneException("Unsupported scene format: " + format);
            }
            if (version != EcsSceneFormat.VERSION) {
                throw new EcsSceneException("Unsupported scene version: " + version);
            }
            String projectId = root.require("project").stringValue();
            String sceneId = root.require("scene").stringValue();
            JsonValue entityValues = root.require("entities");
            if (!entityValues.isArray()) {
                throw new EcsSceneException("Scene entities must be an array.");
            }
            List<EcsSceneEntity> entities = new ArrayList<>(entityValues.size());
            for (int i = 0; i < entityValues.size(); i++) {
                JsonValue entityValue = entityValues.require(i);
                if (!entityValue.isObject()) {
                    throw new EcsSceneException("Scene entity at index " + i + " must be an object.");
                }
                long id = entityValue.require("id").longValue();
                String name = entityValue.require("name").stringValue();
                long parentId = entityValue.require("parent").longValue();
                JsonValue componentValues = entityValue.require("components");
                if (!componentValues.isArray()) {
                    throw new EcsSceneException("Entity " + id + " components must be an array.");
                }
                List<EcsSceneComponent> components = new ArrayList<>(componentValues.size());
                for (int componentIndex = 0; componentIndex < componentValues.size(); componentIndex++) {
                    JsonValue component = componentValues.require(componentIndex);
                    if (!component.isObject()) {
                        throw new EcsSceneException("Component at entity " + id + " index " + componentIndex
                                + " must be an object.");
                    }
                    components.add(new EcsSceneComponent(
                            component.require("type").stringValue(),
                            component.require("data")));
                }
                entities.add(new EcsSceneEntity(id, name, parentId, components));
            }
            EcsSceneDocument document = new EcsSceneDocument(projectId, sceneId, entities);
            validateStructure(document);
            prepare(document);
            return document;
        } catch (EcsSceneException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EcsSceneException("Invalid scene document.", failure);
        }
    }

    void apply(EcsSceneDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document cannot be null.");
        }
        PreparedScene target = prepare(document);
        EcsSceneDocument previousDocument = capture(document.sceneId() + ".rollback");
        PreparedScene previous = prepare(previousDocument);
        try {
            applyPrepared(target);
        } catch (RuntimeException failure) {
            try {
                world.discardCommands();
            } catch (RuntimeException | Error discardFailure) {
                failure.addSuppressed(discardFailure);
            }
            try {
                applyPrepared(previous);
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw new EcsSceneException("Could not apply scene " + document.sceneId() + ".", failure);
        }
    }

    void apply(String text) {
        apply(read(text));
    }

    private PreparedScene prepare(EcsSceneDocument document) {
        validateStructure(document);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < document.entityCount(); i++) {
            ids.add(document.entity(i).id());
        }

        List<PreparedEntity> entities = new ArrayList<>(document.entityCount());
        EcsAssetAdapter assets = manager.assets();
        for (int i = 0; i < document.entityCount(); i++) {
            EcsSceneEntity entity = document.entity(i);
            List<PreparedComponent> components = new ArrayList<>(entity.componentCount());
            for (int componentIndex = 0; componentIndex < entity.componentCount(); componentIndex++) {
                EcsSceneComponent serialized = entity.component(componentIndex);
                EcsComponentDescriptor<?> descriptor = manager.component(serialized.typeId());
                if (descriptor == null) {
                    throw new EcsSceneException("Unknown component type " + serialized.typeId()
                            + " on entity " + entity.id());
                }
                if (!descriptor.persistent()) {
                    throw new EcsSceneException("Transient component " + serialized.typeId()
                            + " cannot appear in a scene.");
                }
                Component component;
                try {
                    component = descriptor.read(json, serialized.data());
                } catch (RuntimeException failure) {
                    throw new EcsSceneException("Could not decode component " + serialized.typeId()
                            + " on entity " + entity.id(), failure);
                }
                validateProperties(descriptor, component, ids, assets, entity.id());
                components.add(new PreparedComponent(descriptor, component));
            }
            entities.add(new PreparedEntity(entity.id(), entity.name(), entity.parentId(), components));
        }
        entities.sort(Comparator.comparingLong(value -> value.id));
        return new PreparedScene(entities);
    }

    private void applyPrepared(PreparedScene scene) {
        clearEntities();
        int[] handles = new int[scene.entities.size()];
        for (int i = 0; i < scene.entities.size(); i++) {
            PreparedEntity entity = scene.entities.get(i);
            int handle = manager.create(entity.id, entity.name);
            handles[i] = handle;
            for (int componentIndex = 0; componentIndex < entity.components.size(); componentIndex++) {
                PreparedComponent component = entity.components.get(componentIndex);
                component.descriptor.add(world, handle, component.component);
            }
        }
        world.flushCommands();
        for (int i = 0; i < scene.entities.size(); i++) {
            PreparedEntity entity = scene.entities.get(i);
            int handle = handles[i];
            if (manager.id(handle) != entity.id) {
                throw new EcsSceneException("Scene manager did not preserve scene ID " + entity.id);
            }
            manager.name(handle, entity.name);
            if (entity.parentId != 0L) {
                int parent = manager.find(entity.parentId);
                if (parent == 0) {
                    throw new EcsSceneException("Missing parent " + entity.parentId + " for entity " + entity.id);
                }
                manager.parent(handle, parent);
            }
        }
        world.flushCommands();
    }

    private void clearEntities() {
        world.flushCommands();
        world.collectEntities(entityBuffer);
        for (int i = 0; i < entityBuffer.size(); i++) {
            world.destroyEntity(entityBuffer.get(i));
        }
        world.flushCommands();
    }

    private void validateStructure(EcsSceneDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document cannot be null.");
        }
        if (!manager.projectId().equals(document.projectId())) {
            throw new EcsSceneException("Scene project " + document.projectId()
                    + " does not match " + manager.projectId() + ".");
        }
        Map<Long, EcsSceneEntity> entitiesById = new HashMap<>();
        for (int i = 0; i < document.entityCount(); i++) {
            EcsSceneEntity entity = document.entity(i);
            if (entitiesById.put(entity.id(), entity) != null) {
                throw new EcsSceneException("Duplicate entity ID: " + entity.id());
            }
            Set<String> componentIds = new HashSet<>();
            for (int componentIndex = 0; componentIndex < entity.componentCount(); componentIndex++) {
                String componentId = entity.component(componentIndex).typeId();
                if (!componentIds.add(componentId)) {
                    throw new EcsSceneException("Duplicate component " + componentId + " on entity " + entity.id());
                }
            }
        }
        for (int i = 0; i < document.entityCount(); i++) {
            EcsSceneEntity entity = document.entity(i);
            if (entity.parentId() != 0L && !entitiesById.containsKey(entity.parentId())) {
                throw new EcsSceneException("Entity " + entity.id() + " has missing parent " + entity.parentId());
            }
            Set<Long> path = new HashSet<>();
            long current = entity.id();
            while (current != 0L) {
                if (!path.add(current)) {
                    throw new EcsSceneException("Entity hierarchy contains a cycle at " + current);
                }
                EcsSceneEntity currentEntity = entitiesById.get(current);
                current = currentEntity == null ? 0L : currentEntity.parentId();
            }
        }
    }

    private void validateLiveProperties(EcsSceneDocument document) {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < document.entityCount(); i++) {
            ids.add(document.entity(i).id());
        }
        EcsAssetAdapter assets = manager.assets();
        for (int i = 0; i < entityBuffer.size(); i++) {
            int entity = entityBuffer.get(i);
            long entityId = manager.id(entity);
            int componentCount = world.componentTypeCount(entity);
            for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
                EcsComponentDescriptor<?> descriptor =
                        manager.component(world.componentType(entity, componentIndex));
                if (descriptor == null || !descriptor.persistent()) {
                    continue;
                }
                Component component = descriptor.get(world, entity);
                validateProperties(descriptor, component, ids, assets, entityId);
            }
        }
    }

    private static void validateProperties(
            EcsComponentDescriptor<?> descriptor,
            Component component,
            Set<Long> ids,
            EcsAssetAdapter assets,
            long entityId) {
        for (int propertyIndex = 0; propertyIndex < descriptor.propertyCount(); propertyIndex++) {
            EcsPropertyDescriptor<?> property = descriptor.property(propertyIndex);
            if (property.kind() == EcsPropertyKind.ENTITY_REFERENCE) {
                long reference;
                try {
                    reference = property.entityReference(component);
                } catch (RuntimeException failure) {
                    throw propertyFailure(descriptor, property, entityId, failure);
                }
                if (reference != 0L && !ids.contains(reference)) {
                    throw new EcsSceneException("Property " + descriptor.id() + "." + property.id()
                            + " on entity " + entityId + " references missing entity " + reference);
                }
            } else if (property.kind() == EcsPropertyKind.ASSET && assets != null) {
                String path;
                try {
                    path = property.textValue(component);
                } catch (RuntimeException failure) {
                    throw propertyFailure(descriptor, property, entityId, failure);
                }
                if (path == null || path.length() == 0) {
                    continue;
                }
                String normalized;
                try {
                    normalized = assets.normalize(path);
                    if (!assets.accepts(normalized)) {
                        throw new EcsSceneException("Unsupported asset path " + path + " for property "
                                + descriptor.id() + "." + property.id() + " on entity " + entityId);
                    }
                } catch (EcsSceneException failure) {
                    throw failure;
                } catch (RuntimeException failure) {
                    throw new EcsSceneException("Invalid asset path " + path + " for property "
                            + descriptor.id() + "." + property.id() + " on entity " + entityId, failure);
                }
            }
        }
    }

    private static EcsSceneException propertyFailure(
            EcsComponentDescriptor<?> descriptor,
            EcsPropertyDescriptor<?> property,
            long entityId,
            RuntimeException failure) {
        return new EcsSceneException("Could not validate property " + descriptor.id() + "."
                + property.id() + " on entity " + entityId, failure);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static final class PreparedScene {
        final List<PreparedEntity> entities;

        PreparedScene(List<PreparedEntity> entities) {
            this.entities = entities;
        }
    }

    private static final class PreparedEntity {
        final long id;
        final String name;
        final long parentId;
        final List<PreparedComponent> components;

        PreparedEntity(long id, String name, long parentId, List<PreparedComponent> components) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
            this.components = components;
        }
    }

    private static final class PreparedComponent {
        final EcsComponentDescriptor<?> descriptor;
        final Component component;

        PreparedComponent(EcsComponentDescriptor<?> descriptor, Component component) {
            this.descriptor = descriptor;
            this.component = component;
        }
    }
}
