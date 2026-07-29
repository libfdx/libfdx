package io.github.libfdx.ecs.scene;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.ecs.component.GameComponent;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.component.UiComponent;
import io.github.libfdx.ecs.schema.EcsAssetAdapter;
import io.github.libfdx.ecs.schema.EcsBoundsAdapter;
import io.github.libfdx.ecs.schema.EcsComponentDescriptor;
import io.github.libfdx.ecs.schema.EcsEntityPreset;
import io.github.libfdx.ecs.schema.EcsTransformAdapter;
import io.github.libfdx.ecs.transform.Transform;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonCodec;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * World-owned scene identity, catalog, and persistence service.
 *
 * <p>Every {@link World} constructs exactly one manager and exposes it through
 * {@link World#scenes()}. The manager is intrinsic: it is not registered as an
 * ordinary ECS manager and cannot be removed or replaced. Scene capture and
 * application are explicit structural safe-point operations, not frame-loop
 * operations.</p>
 */
public final class SceneManager {
    /** Project ID used until a project or host supplies a manifest-backed ID. */
    public static final String DEFAULT_PROJECT_ID = "default";

    private static final String TRANSFORM_ID = "libfdx.transform";
    private static final String GAME_ID = "libfdx.game";
    private static final String UI_ID = "libfdx.ui";

    private final World.SceneAccess sceneAccess;
    private final World world;
    private final SceneCodec codec;
    private final ArrayList<EcsComponentDescriptor<?>> components = new ArrayList<>();
    private final Map<String, EcsComponentDescriptor<?>> componentsById = new LinkedHashMap<>();
    private final Map<Class<? extends Component>, EcsComponentDescriptor<?>> componentsByType = new HashMap<>();
    private final Set<Class<? extends Component>> transientTypes = new HashSet<>();
    private final ArrayList<EcsEntityPreset> presets = new ArrayList<>();
    private final Map<String, EcsEntityPreset> presetsById = new LinkedHashMap<>();

    private int[] metadataHandles = new int[16];
    private long[] entityIds = new long[16];
    private long[] parentIds = new long[16];
    private String[] names = new String[16];
    private long nextEntityId = 1L;
    private String projectId = DEFAULT_PROJECT_ID;
    private EcsTransformAdapter transforms;
    private EcsBoundsAdapter bounds;
    private EcsAssetAdapter assets;

    /**
     * @hidden Creates the intrinsic service for a world.
     *
     * <p>Applications obtain the existing instance from {@link World#scenes()}
     * instead of constructing another one.</p>
     *
     * @param sceneAccess unforgeable access token owned by the world
     */
    public SceneManager(World.SceneAccess sceneAccess) {
        if (sceneAccess == null) {
            throw new IllegalArgumentException("sceneAccess cannot be null.");
        }
        this.sceneAccess = sceneAccess;
        world = sceneAccess.world();
        codec = new SceneCodec(this);
        resetCatalog();
    }

    public World world() {
        return world;
    }

    public String projectId() {
        return projectId;
    }

    public SceneManager projectId(String projectId) {
        this.projectId = requireText(projectId, "projectId");
        return this;
    }

    /** Returns the stable positive scene ID assigned to an attached or reserved entity. */
    public long id(int entity) {
        return entityIds[metadataIndex(entity)];
    }

    /** Finds an attached or reserved entity by stable ID, or returns zero. */
    public int find(long id) {
        if (id <= 0L) {
            return 0;
        }
        for (int i = 0; i < metadataHandles.length; i++) {
            if (metadataHandles[i] != 0 && entityIds[i] == id) {
                return metadataHandles[i];
            }
        }
        return 0;
    }

    /** Creates a reserved entity with a manager-assigned stable ID. */
    public int create(String name) {
        int entity = world.createEntity();
        name(entity, name);
        return entity;
    }

    /**
     * Creates a reserved entity with an explicit stable ID.
     *
     * <p>This is primarily useful when reconstructing imported or generated
     * scene data. Normal project code should use {@link #create(String)}.</p>
     */
    public int create(long id, String name) {
        requirePositiveId(id);
        if (find(id) != 0) {
            throw new IllegalArgumentException("Entity ID is already in use: " + id);
        }
        int entity = world.createEntity();
        int index = metadataIndex(entity);
        entityIds[index] = id;
        if (id >= nextEntityId) {
            nextEntityId = id == Long.MAX_VALUE ? Long.MIN_VALUE : id + 1L;
        }
        name(entity, name);
        return entity;
    }

    public String name(int entity) {
        String name = names[metadataIndex(entity)];
        return name == null ? "" : name;
    }

    public SceneManager name(int entity, String name) {
        names[metadataIndex(entity)] = name == null ? "" : name;
        return this;
    }

    /** Returns the stable parent ID, or zero for a root entity. */
    public long parentId(int entity) {
        return parentIds[metadataIndex(entity)];
    }

    /** Returns the current parent entity handle, or zero for a root entity. */
    public int parent(int entity) {
        return find(parentId(entity));
    }

    /**
     * Sets an entity's parent by world handle.
     *
     * @param entity child entity
     * @param parent parent entity in the same world, or zero to make a root
     */
    public SceneManager parent(int entity, int parent) {
        int childIndex = metadataIndex(entity);
        if (parent == 0) {
            parentIds[childIndex] = 0L;
            return this;
        }
        int parentIndex = metadataIndex(parent);
        if (entity == parent) {
            throw new IllegalArgumentException("An entity cannot parent itself.");
        }
        long childId = entityIds[childIndex];
        long current = entityIds[parentIndex];
        while (current != 0L) {
            if (current == childId) {
                throw new IllegalArgumentException("Entity hierarchy cannot contain a cycle.");
            }
            int currentEntity = find(current);
            if (currentEntity == 0) {
                throw new IllegalStateException("Parent hierarchy references missing entity " + current + ".");
            }
            current = parentIds[World.entityIndex(currentEntity)];
        }
        parentIds[childIndex] = entityIds[parentIndex];
        return this;
    }

    public int componentCount() {
        return components.size();
    }

    public EcsComponentDescriptor<?> component(int index) {
        return components.get(index);
    }

    public EcsComponentDescriptor<?> component(String id) {
        return id == null ? null : componentsById.get(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> EcsComponentDescriptor<T> component(Class<T> type) {
        return type == null ? null : (EcsComponentDescriptor<T>) componentsByType.get(type);
    }

    /**
     * Registers a persistent or transient component descriptor.
     *
     * <p>A descriptor for an already described Java type replaces the previous
     * descriptor. This allows a project to assign its own stable ID and richer
     * properties to a core component. A stable ID cannot describe two Java
     * types.</p>
     */
    public SceneManager component(EcsComponentDescriptor<?> descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null.");
        }
        EcsComponentDescriptor<?> idOwner = componentsById.get(descriptor.id());
        if (idOwner != null && idOwner.type() != descriptor.type()) {
            throw new IllegalArgumentException("Duplicate component id: " + descriptor.id());
        }

        EcsComponentDescriptor<?> previous = componentsByType.get(descriptor.type());
        if (previous == null) {
            components.add(descriptor);
        } else {
            int index = components.indexOf(previous);
            components.set(index, descriptor);
            componentsById.remove(previous.id());
        }
        componentsById.put(descriptor.id(), descriptor);
        componentsByType.put(descriptor.type(), descriptor);
        transientTypes.remove(descriptor.type());
        return this;
    }

    /**
     * Explicitly excludes a custom component type from scene persistence.
     *
     * <p>Any existing descriptor for the type is removed. Transient types are
     * omitted during capture instead of being treated as an undeclared type.</p>
     */
    public SceneManager transientComponent(Class<? extends Component> type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null.");
        }
        EcsComponentDescriptor<?> previous = componentsByType.remove(type);
        if (previous != null) {
            components.remove(previous);
            componentsById.remove(previous.id());
        }
        transientTypes.add(type);
        return this;
    }

    public int presetCount() {
        return presets.size();
    }

    public EcsEntityPreset preset(int index) {
        return presets.get(index);
    }

    public EcsEntityPreset preset(String id) {
        return id == null ? null : presetsById.get(id);
    }

    public SceneManager preset(EcsEntityPreset preset) {
        if (preset == null || isBlank(preset.id()) || isBlank(preset.name())) {
            throw new IllegalArgumentException("Preset id and name cannot be blank.");
        }
        if (presetsById.putIfAbsent(preset.id(), preset) != null) {
            throw new IllegalArgumentException("Duplicate preset id: " + preset.id());
        }
        presets.add(preset);
        return this;
    }

    public EcsTransformAdapter transforms() {
        return transforms;
    }

    public SceneManager transforms(EcsTransformAdapter transforms) {
        if (transforms == null) {
            throw new IllegalArgumentException("transforms cannot be null.");
        }
        this.transforms = transforms;
        return this;
    }

    public EcsBoundsAdapter bounds() {
        return bounds;
    }

    public SceneManager bounds(EcsBoundsAdapter bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds cannot be null.");
        }
        this.bounds = bounds;
        return this;
    }

    public EcsAssetAdapter assets() {
        return assets;
    }

    public SceneManager assets(EcsAssetAdapter assets) {
        if (assets == null) {
            throw new IllegalArgumentException("assets cannot be null.");
        }
        this.assets = assets;
        return this;
    }

    /** Captures every attached entity at an ECS structural safe point. */
    public EcsSceneDocument capture(String sceneId) {
        return codec.capture(sceneId);
    }

    public String write(String sceneId) {
        return codec.write(sceneId);
    }

    public String write(EcsSceneDocument document) {
        return codec.write(document);
    }

    public EcsSceneDocument read(String text) {
        return codec.read(text);
    }

    public void apply(EcsSceneDocument document) {
        codec.apply(document);
    }

    public void apply(String text) {
        codec.apply(text);
    }

    /** @hidden World lifecycle hook. Applications must not call this method. */
    public void entityReserved(World.SceneAccess sceneAccess, int entity) {
        requireSceneAccess(sceneAccess);
        world.requireMutableEntity(entity);
        int index = World.entityIndex(entity);
        ensureMetadataCapacity(index + 1);
        if (metadataHandles[index] != 0) {
            throw new IllegalStateException("Entity already has scene metadata: " + entity + ".");
        }
        if (nextEntityId <= 0L) {
            throw new IllegalStateException("Scene entity ID space is exhausted.");
        }
        metadataHandles[index] = entity;
        entityIds[index] = nextEntityId++;
        parentIds[index] = 0L;
        names[index] = "";
    }

    /** @hidden World lifecycle hook. Applications must not call this method. */
    public void entityRemoved(World.SceneAccess sceneAccess, int entity) {
        requireSceneAccess(sceneAccess);
        if (isMutable(entity)) {
            throw new IllegalStateException("Entity must be detached before scene metadata is removed.");
        }
        int index = World.entityIndex(entity);
        if (index < 0 || index >= metadataHandles.length || metadataHandles[index] != entity) {
            return;
        }
        long removedId = entityIds[index];
        for (int i = 0; i < metadataHandles.length; i++) {
            if (metadataHandles[i] != 0 && parentIds[i] == removedId) {
                parentIds[i] = 0L;
            }
        }
        metadataHandles[index] = 0;
        entityIds[index] = 0L;
        parentIds[index] = 0L;
        names[index] = null;
    }

    /** @hidden World lifecycle hook. Applications must not call this method. */
    public void worldCleared(World.SceneAccess sceneAccess) {
        requireSceneAccess(sceneAccess);
        for (int i = 0; i < metadataHandles.length; i++) {
            if (metadataHandles[i] != 0 && isMutable(metadataHandles[i])) {
                throw new IllegalStateException("World must be empty before scene state is reset.");
            }
        }
        Arrays.fill(metadataHandles, 0);
        Arrays.fill(entityIds, 0L);
        Arrays.fill(parentIds, 0L);
        Arrays.fill(names, null);
        nextEntityId = 1L;
        projectId = DEFAULT_PROJECT_ID;
        resetCatalog();
    }

    boolean transientType(Class<? extends Component> type) {
        return transientTypes.contains(type);
    }

    private void requireSceneAccess(World.SceneAccess sceneAccess) {
        if (sceneAccess != this.sceneAccess) {
            throw new IllegalArgumentException("Scene lifecycle access belongs to another world.");
        }
    }

    private int metadataIndex(int entity) {
        world.requireMutableEntity(entity);
        int index = World.entityIndex(entity);
        if (index < 0 || index >= metadataHandles.length || metadataHandles[index] != entity) {
            throw new IllegalStateException("Entity has no scene metadata: " + entity + ".");
        }
        return index;
    }

    private boolean isMutable(int entity) {
        try {
            world.requireMutableEntity(entity);
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private void resetCatalog() {
        components.clear();
        componentsById.clear();
        componentsByType.clear();
        transientTypes.clear();
        presets.clear();
        presetsById.clear();
        bounds = null;
        assets = null;
        transforms = CoreTransforms.INSTANCE;
        component(EcsComponentDescriptor.builder(
                        TRANSFORM_ID,
                        "Transform",
                        TransformComponent.class,
                        TransformComponent::new)
                .persistent(new TransformComponentJsonCodec())
                .build());
        component(EcsComponentDescriptor.builder(
                        GAME_ID,
                        "Game",
                        GameComponent.class,
                        GameComponent::new)
                .persistent(new EmptyComponentCodec<>(GameComponent::new))
                .build());
        component(EcsComponentDescriptor.builder(
                        UI_ID,
                        "UI",
                        UiComponent.class,
                        UiComponent::new)
                .persistent(new EmptyComponentCodec<>(UiComponent::new))
                .build());
    }

    private void ensureMetadataCapacity(int required) {
        if (required <= metadataHandles.length) {
            return;
        }
        int capacity = metadataHandles.length;
        while (capacity < required) {
            capacity *= 2;
        }
        metadataHandles = Arrays.copyOf(metadataHandles, capacity);
        entityIds = Arrays.copyOf(entityIds, capacity);
        parentIds = Arrays.copyOf(parentIds, capacity);
        names = Arrays.copyOf(names, capacity);
    }

    private static void requirePositiveId(long id) {
        if (id <= 0L) {
            throw new IllegalArgumentException("Entity ID must be positive.");
        }
    }

    private static String requireText(String value, String label) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private enum CoreTransforms implements EcsTransformAdapter {
        INSTANCE;

        @Override
        public Transform transform(World world, int entity) {
            TransformComponent component = world.get(entity, TransformComponent.class);
            return component == null ? null : component.transform;
        }

        @Override
        public void add(World world, int entity) {
            if (!world.hasNow(entity, TransformComponent.class)) {
                world.add(entity, new TransformComponent());
            }
        }
    }

    private static final class EmptyComponentCodec<T extends Component> implements JsonCodec<T> {
        private final Factory<T> factory;

        EmptyComponentCodec(Factory<T> factory) {
            this.factory = factory;
        }

        @Override
        public T read(Json json, JsonValue value) {
            if (value == null || !value.isObject()) {
                throw new IllegalArgumentException("Marker component data must be a JSON object.");
            }
            return factory.create();
        }

        @Override
        public void write(Json json, JsonWriter writer, T value) {
            if (value == null) {
                throw new IllegalArgumentException("component cannot be null.");
            }
            writer.object().endObject();
        }
    }

    @FunctionalInterface
    private interface Factory<T> {
        T create();
    }
}
