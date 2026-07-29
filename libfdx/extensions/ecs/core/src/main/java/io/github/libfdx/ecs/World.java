package io.github.libfdx.ecs;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.IntArray;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.ecs.command.WorldCommands;
import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.component.ComponentStore;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.ecs.event.EventDispatcher;
import io.github.libfdx.ecs.manager.CameraManager;
import io.github.libfdx.ecs.manager.Manager;
import io.github.libfdx.ecs.query.EntityMatcher;
import io.github.libfdx.ecs.scene.SceneManager;
import io.github.libfdx.ecs.system.RenderSystem;
import io.github.libfdx.ecs.system.System;
import io.github.libfdx.ecs.system.UiRenderSystem;
import io.github.libfdx.ecs.system.UpdateSystem;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.camera.Camera;
import java.util.Arrays;

public final class World {
    private static final int INDEX_BITS = 20;
    private static final int INDEX_MASK = (1 << INDEX_BITS) - 1;

    private int[] generations = new int[16];
    private boolean[] attached = new boolean[16];
    private boolean[] reserved = new boolean[16];
    private int[] freeIndices = new int[16];
    private int freeCount;
    private int nextIndex;
    private int pendingCreateIndex = -1;
    private int entityCount;
    private float deltaTime;
    private boolean processingSystems;
    private boolean flushingCommands;

    private final ObjectMap<Class<? extends Component>, ComponentStore<?>> stores = new ObjectMap<>();
    private final ObjectMap<Class<? extends Component>, ComponentMapper<?>> mappers = new ObjectMap<>();
    private final Array<Class<? extends Component>[]> componentTypes = new Array<>();
    private final Array<EntityList> entityLists = new Array<>();
    private final ObjectMap<Class<?>, Manager> managers = new ObjectMap<>();
    private final ObjectMap<Class<?>, System> systems = new ObjectMap<>();
    private final Array<Manager> managerOrder = new Array<>();
    private final Array<System> systemOrder = new Array<>();
    private final Array<UpdateSystem> updateSystemOrder = new Array<>();
    private final Array<RenderSystem> renderSystemOrder = new Array<>();
    private final Array<UiRenderSystem> uiRenderSystemOrder = new Array<>();
    private final WorldCommands commands = new WorldCommands(this);
    private final EventDispatcher events = new EventDispatcher(this);
    private final SceneAccess sceneAccess = new SceneAccess(this);
    private final SceneManager scenes = new SceneManager(sceneAccess);

    public int createEntity() {
        return commands.createEntity();
    }

    public void destroyEntity(int entity) {
        commands.destroyEntity(entity);
    }

    public boolean isAttached(int entity) {
        if (entity == 0) {
            return false;
        }
        int index = entityIndex(entity);
        return index < attached.length
            && attached[index]
            && generations[index] == entityGeneration(entity);
    }

    public int entityCount() {
        return entityCount;
    }

    /**
     * Replaces the supplied reusable array with every currently attached entity.
     *
     * @param out destination storage owned by the caller
     * @return the supplied destination
     */
    public IntArray collectEntities(IntArray out) {
        if (out == null) {
            throw new IllegalArgumentException("out cannot be null.");
        }
        out.clear();
        out.ensureCapacity(entityCount);
        for (int index = 0; index < nextIndex; index++) {
            if (attached[index]) {
                out.add(entityHandle(index));
            }
        }
        return out;
    }

    /**
     * Returns the number of component types currently attached to an entity.
     *
     * @param entity the attached entity
     * @return the number of attached component types
     */
    public int componentTypeCount(int entity) {
        requireReadableEntity(entity);
        return componentTypes(entityIndex(entity)).length;
    }

    /**
     * Returns an attached component type without allocating an intermediate collection.
     *
     * @param entity the attached entity
     * @param index the component-type index
     * @return the attached component type
     */
    public Class<? extends Component> componentType(int entity, int index) {
        requireReadableEntity(entity);
        Class<? extends Component>[] types = componentTypes(entityIndex(entity));
        if (index < 0 || index >= types.length) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + types.length);
        }
        return types[index];
    }

    /**
     * Queues a complete world teardown.
     *
     * <p>After the command is flushed, previously obtained component mappers
     * and entity lists are detached from this world's caches. Callers that
     * continue using the cleared world must obtain new instances.</p>
     */
    public void clear() {
        commands.clear();
    }

    public <T extends Component> void add(int entity, T component) {
        commands.add(entity, component);
    }

    public <T extends Component> void add(int entity, Class<T> type, T component) {
        commands.add(entity, type, component);
    }

    public <T extends Component> T get(int entity, Class<T> type) {
        requireReadableEntity(entity);
        return store(type).get(entity);
    }

    public <T extends Component> T require(int entity, Class<T> type) {
        T component = get(entity, type);
        if (component == null) {
            throw new IllegalStateException("Missing component " + type.getName() + " for entity " + entity + ".");
        }
        return component;
    }

    public <T extends Component> boolean has(int entity, Class<T> type) {
        requireReadableEntity(entity);
        return hasNow(entity, type);
    }

    public void remove(int entity, Class<? extends Component> type) {
        commands.remove(entity, type);
    }

    public <T extends Component> ComponentMapper<T> mapper(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("component type cannot be null.");
        }
        @SuppressWarnings("unchecked")
        ComponentMapper<T> mapper = (ComponentMapper<T>) mappers.get(type);
        if (mapper != null) {
            return mapper;
        }
        ComponentStore<T> store = store(type);
        mapper = new ComponentMapper<>(this, store);
        mappers.put(type, mapper);
        return mapper;
    }

    public EntityMatcher matcher() {
        return new EntityMatcher(this);
    }

    public EntityList entities(EntityMatcher matcher) {
        if (matcher == null) {
            throw new IllegalArgumentException("matcher cannot be null.");
        }
        EntityList list = new EntityList(this, matcher);
        entityLists.add(list);
        return list;
    }

    public EventDispatcher events() {
        return events;
    }

    public WorldCommands commands() {
        return commands;
    }

    /**
     * Returns this world's intrinsic scene identity and persistence service.
     *
     * <p>The same non-null instance remains available for the complete world
     * lifetime and is not included in the ordinary manager registry.</p>
     */
    public SceneManager scenes() {
        return scenes;
    }

    public void flushCommands() {
        if (flushingCommands) {
            throw new IllegalStateException("Command flushing is already active.");
        }
        flushingCommands = true;
        try {
            commands.flush();
            refreshEntityLists();
        } finally {
            flushingCommands = false;
        }
    }

    /**
     * Discards every pending command without applying it.
     *
     * <p>Reserved entity handles from discarded create commands are invalidated
     * and made available for reuse. This is intended for host rollback paths
     * that must abandon partially queued initialization before clearing a
     * world.</p>
     */
    public void discardCommands() {
        if (flushingCommands) {
            throw new IllegalStateException("Command flushing is already active.");
        }
        commands.discard();
        releaseReservedEntities();
    }

    /**
     * Queues a manager for registration under an explicit type.
     *
     * <p>A type can have at most one live or pending manager. A duplicate does
     * not replace the first registration.</p>
     *
     * @param manager the manager instance
     * @param type the type used for lookup and removal
     * @return the supplied manager, or null when either argument is null or the
     * type is already registered or pending
     */
    public <M extends Manager, T extends M> T addManager(T manager, Class<M> type) {
        if (manager != null && type != null) {
            return commands.addManager(manager, type);
        }
        return null;
    }

    public <T extends Manager> T getManager(Class<T> type) {
        if (type == null) {
            return null;
        }
        return type.cast(managers.get(type));
    }

    public void removeManager(Class<? extends Manager> type) {
        commands.removeManager(type);
    }

    public int managerCount() {
        return managers.size();
    }

    public <T extends System> T addSystem(T system) {
        return commands.addSystem(system);
    }

    public <T extends System> T getSystem(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("system type cannot be null.");
        }
        return type.cast(systems.get(type));
    }

    public void removeSystem(Class<? extends System> type) {
        commands.removeSystem(type);
    }

    public int systemCount() {
        return systems.size();
    }

    public int updateSystemCount() {
        return updateSystemOrder.size();
    }

    public int renderSystemCount() {
        return renderSystemOrder.size();
    }

    public int uiRenderSystemCount() {
        return uiRenderSystemOrder.size();
    }

    public float deltaTime() {
        return deltaTime;
    }

    public void update(float deltaTime) {
        update(deltaTime, UpdateSystem.class);
    }

    public void update(float deltaTime, Class<? extends UpdateSystem> systemType) {
        if (systemType == null) {
            throw new IllegalArgumentException("system type cannot be null.");
        }
        beginSystemPhase();
        this.deltaTime = deltaTime;
        try {
            flushCommands();
            events.flush();
            for (int i = 0; i < updateSystemOrder.size(); i++) {
                UpdateSystem system = updateSystemOrder.get(i);
                if (systemType.isInstance(system) && system.isEnabled()) {
                    system.update();
                }
            }
            flushCommands();
            events.flush();
        } finally {
            processingSystems = false;
        }
    }

    /**
     * Invokes enabled game render systems in registration order.
     *
     * <p>The frame and targets are borrowed for this call and are never
     * retained. Rendering does not flush commands or events and does not
     * advance simulation.</p>
     *
     * @param frame active borrowed graphics frame
     * @param colorTarget borrowed color target
     * @param depthTarget optional borrowed depth target
     * @param width target width in pixels
     * @param height target height in pixels
     * @param cameraOverride optional host camera that overrides the world's
     * game camera
     */
    public void render(
            GraphicsFrame frame,
            TextureView colorTarget,
            TextureView depthTarget,
            int width,
            int height,
            Camera cameraOverride) {
        if (renderSystemOrder.isEmpty()) {
            return;
        }
        requireRenderTarget(frame, colorTarget, width, height);
        beginSystemPhase();
        try {
            Camera camera = resolveCamera(cameraOverride, false);
            for (int i = 0; i < renderSystemOrder.size(); i++) {
                RenderSystem system = renderSystemOrder.get(i);
                if (system.isEnabled()) {
                    system.render(frame, colorTarget, depthTarget, width, height, camera);
                }
            }
        } finally {
            processingSystems = false;
        }
    }

    /**
     * Invokes enabled UI render systems in registration order.
     *
     * <p>The frame and targets are borrowed for this call and are never
     * retained. Rendering does not flush commands or events and does not
     * advance simulation.</p>
     *
     * @param frame active borrowed graphics frame
     * @param colorTarget borrowed color target
     * @param depthTarget optional borrowed depth target
     * @param width target width in pixels
     * @param height target height in pixels
     * @param cameraOverride optional host camera that overrides the world's UI
     * camera
     */
    public void renderUi(
            GraphicsFrame frame,
            TextureView colorTarget,
            TextureView depthTarget,
            int width,
            int height,
            Camera cameraOverride) {
        if (uiRenderSystemOrder.isEmpty()) {
            return;
        }
        requireRenderTarget(frame, colorTarget, width, height);
        beginSystemPhase();
        try {
            Camera camera = resolveCamera(cameraOverride, true);
            for (int i = 0; i < uiRenderSystemOrder.size(); i++) {
                UiRenderSystem system = uiRenderSystemOrder.get(i);
                if (system.isEnabled()) {
                    system.renderUi(frame, colorTarget, depthTarget, width, height, camera);
                }
            }
        } finally {
            processingSystems = false;
        }
    }

    public static int entityIndex(int entity) {
        return (entity & INDEX_MASK) - 1;
    }

    static int entityGeneration(int entity) {
        return entity >>> INDEX_BITS;
    }

    public int reserveEntity() {
        int index;
        if (freeCount > 0) {
            index = freeIndices[--freeCount];
        } else {
            index = nextIndex++;
        }
        ensureEntityCapacity(index + 1);
        if (generations[index] == 0) {
            generations[index] = 1;
        }
        reserved[index] = true;
        pendingCreateIndex = index;
        int entity = entityHandle(index);
        try {
            scenes.entityReserved(sceneAccess, entity);
            return entity;
        } catch (RuntimeException | Error failure) {
            reserved[index] = false;
            pendingCreateIndex = -1;
            ensureFreeCapacity(freeCount + 1);
            freeIndices[freeCount++] = index;
            throw failure;
        }
    }

    public void applyCreate(int entity) {
        int index = entityIndex(entity);
        if (index < 0
            || index >= reserved.length
            || !reserved[index]
            || generations[index] != entityGeneration(entity)) {
            throw new IllegalStateException("Missing reserved entity for create command.");
        }
        reserved[index] = false;
        attached[index] = true;
        entityCount++;
        if (pendingCreateIndex == index) {
            pendingCreateIndex = -1;
        }
    }

    public void applyDestroy(int entity) {
        if (!isAttached(entity)) {
            return;
        }
        int index = entityIndex(entity);
        Class<? extends Component>[] types = componentTypes(index);
        for (int i = 0; i < types.length; i++) {
            ComponentStore<?> store = stores.get(types[i]);
            if (store != null) {
                store.remove(entity);
            }
        }
        setComponentTypes(index, componentTypeArray(0));
        attached[index] = false;
        reserved[index] = false;
        scenes.entityRemoved(sceneAccess, entity);
        generations[index]++;
        if (generations[index] == 0) {
            generations[index] = 1;
        }
        ensureFreeCapacity(freeCount + 1);
        freeIndices[freeCount++] = index;
        entityCount--;
    }

    public <T extends Component> void applyAdd(int entity, Class<T> type, T component) {
        requireAttachedForApply(entity);
        ComponentStore<T> store = store(type);
        boolean alreadyHad = store.has(entity);
        store.add(entity, component);
        if (!alreadyHad) {
            addComponentType(entityIndex(entity), type);
        }
    }

    public void applyRemove(int entity, Class<? extends Component> type) {
        requireAttachedForApply(entity);
        ComponentStore<?> store = stores.get(type);
        if (store != null && store.remove(entity)) {
            removeComponentType(entityIndex(entity), type);
        }
    }

    public void applyAddManager(Class<? extends Manager> type, Manager manager) {
        if (managers.containsKey(type)) {
            return;
        }
        managers.put(type, manager);
        managerOrder.add(manager);
        manager.onAttach(this);
    }

    public void applyRemoveManager(Class<? extends Manager> type) {
        Manager manager = managers.remove(type);
        if (manager != null) {
            managerOrder.removeValue(manager);
            manager.onDetach(this);
        }
    }

    public void applyAddSystem(System system) {
        Class<?> type = system.getClass();
        System previous = systems.put(type, system);
        if (previous != null) {
            systemOrder.removeValue(previous);
            removeFromSystemPhases(previous);
            previous.onDetach(this);
        }
        systemOrder.add(system);
        addToSystemPhases(system);
        system.onAttach(this);
    }

    public void applyRemoveSystem(Class<? extends System> type) {
        System system = systems.remove(type);
        if (system != null) {
            systemOrder.removeValue(system);
            removeFromSystemPhases(system);
            system.onDetach(this);
        }
    }

    public void applyClear() {
        commands.discard();
        events.clear();
        Throwable failure = null;
        for (int i = systemOrder.size() - 1; i >= 0; i--) {
            try {
                systemOrder.get(i).onDetach(this);
            } catch (RuntimeException | Error detachFailure) {
                failure = aggregateFailure(failure, detachFailure);
            }
        }
        systems.clear();
        systemOrder.clear();
        updateSystemOrder.clear();
        renderSystemOrder.clear();
        uiRenderSystemOrder.clear();
        for (int i = managerOrder.size() - 1; i >= 0; i--) {
            try {
                managerOrder.get(i).onDetach(this);
            } catch (RuntimeException | Error detachFailure) {
                failure = aggregateFailure(failure, detachFailure);
            }
        }
        managers.clear();
        managerOrder.clear();
        for (ComponentStore<?> store : stores.values()) {
            store.clear();
        }
        Arrays.fill(attached, false);
        Arrays.fill(reserved, false);
        Arrays.fill(generations, 1);
        componentTypes.clear();
        freeCount = 0;
        nextIndex = 0;
        pendingCreateIndex = -1;
        entityCount = 0;
        scenes.worldCleared(sceneAccess);
        refreshEntityLists();
        entityLists.clear();
        mappers.clear();
        stores.clear();
        events.clear();
        commands.discard();
        rethrowFailure(failure);
    }

    public void requireMutableEntity(int entity) {
        if (entity == 0) {
            throw new IllegalArgumentException("entity cannot be 0.");
        }
        int index = entityIndex(entity);
        if (index < generations.length
            && generations[index] == entityGeneration(entity)
            && (attached[index] || reserved[index])) {
            return;
        }
        throw new IllegalStateException("Entity is not attached or reserved: " + entity + ".");
    }

    void requireReadableEntity(int entity) {
        if (!isAttached(entity)) {
            throw new IllegalStateException("Entity is not attached: " + entity + ".");
        }
    }

    public <T extends Component> void requireComponentType(Class<T> type, T component) {
        if (type == null) {
            throw new IllegalArgumentException("component type cannot be null.");
        }
        if (component == null) {
            throw new IllegalArgumentException("component cannot be null.");
        }
        if (!type.isInstance(component)) {
            throw new IllegalArgumentException("Component is not an instance of " + type.getName() + ".");
        }
    }

    public boolean hasNow(int entity, Class<? extends Component> type) {
        if (type == null) {
            return false;
        }
        ComponentStore<?> store = stores.get(type);
        return store != null && store.has(entity);
    }

    public ComponentStore<?> smallestStore(Class<? extends Component>[] types) {
        ComponentStore<?> smallest = null;
        for (int i = 0; i < types.length; i++) {
            ComponentStore<?> store = stores.get(types[i]);
            if (store == null || store.size() == 0) {
                return null;
            }
            if (smallest == null || store.size() < smallest.size()) {
                smallest = store;
            }
        }
        return smallest;
    }

    public void collectAttachedEntities(EntityList list) {
        for (int index = 0; index < nextIndex; index++) {
            if (attached[index]) {
                int entity = entityHandle(index);
                if (list.matcher().matchesAttached(entity)) {
                    list.addNow(entity);
                }
            }
        }
    }

    private void requireAttachedForApply(int entity) {
        if (!isAttached(entity)) {
            throw new IllegalStateException("Entity is not attached: " + entity + ".");
        }
    }

    private void beginSystemPhase() {
        if (processingSystems) {
            throw new IllegalStateException("A World system phase is already active.");
        }
        processingSystems = true;
    }

    private static void requireRenderTarget(
            GraphicsFrame frame,
            TextureView colorTarget,
            int width,
            int height) {
        if (frame == null || colorTarget == null) {
            throw new IllegalArgumentException("frame and colorTarget cannot be null.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Render dimensions must be positive.");
        }
    }

    private Camera resolveCamera(Camera cameraOverride, boolean ui) {
        if (cameraOverride != null) {
            return cameraOverride;
        }
        CameraManager cameras = getManager(CameraManager.class);
        if (cameras == null) {
            return null;
        }
        return ui ? cameras.ui() : cameras.game();
    }

    private void addToSystemPhases(System system) {
        if (system instanceof UpdateSystem updateSystem) {
            updateSystemOrder.add(updateSystem);
        }
        if (system instanceof RenderSystem renderSystem) {
            renderSystemOrder.add(renderSystem);
        }
        if (system instanceof UiRenderSystem uiRenderSystem) {
            uiRenderSystemOrder.add(uiRenderSystem);
        }
    }

    private void removeFromSystemPhases(System system) {
        if (system instanceof UpdateSystem updateSystem) {
            updateSystemOrder.removeValue(updateSystem);
        }
        if (system instanceof RenderSystem renderSystem) {
            renderSystemOrder.removeValue(renderSystem);
        }
        if (system instanceof UiRenderSystem uiRenderSystem) {
            uiRenderSystemOrder.removeValue(uiRenderSystem);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> ComponentStore<T> store(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("component type cannot be null.");
        }
        ComponentStore<?> existing = stores.get(type);
        if (existing != null) {
            return (ComponentStore<T>) existing;
        }
        ComponentStore<T> created = new ComponentStore<>(type);
        stores.put(type, created);
        return created;
    }

    private int entityHandle(int index) {
        return (generations[index] << INDEX_BITS) | (index + 1);
    }

    private void ensureEntityCapacity(int required) {
        if (required <= generations.length) {
            return;
        }
        int old = generations.length;
        int capacity = old;
        while (capacity < required) {
            capacity *= 2;
        }
        generations = Arrays.copyOf(generations, capacity);
        attached = Arrays.copyOf(attached, capacity);
        reserved = Arrays.copyOf(reserved, capacity);
        Arrays.fill(generations, old, capacity, 1);
    }

    private void ensureFreeCapacity(int required) {
        if (required <= freeIndices.length) {
            return;
        }
        int capacity = freeIndices.length;
        while (capacity < required) {
            capacity *= 2;
        }
        freeIndices = Arrays.copyOf(freeIndices, capacity);
    }

    private void releaseReservedEntities() {
        for (int index = 0; index < nextIndex; index++) {
            if (!reserved[index]) {
                continue;
            }
            reserved[index] = false;
            scenes.entityRemoved(sceneAccess, entityHandle(index));
            generations[index]++;
            if (generations[index] == 0) {
                generations[index] = 1;
            }
            ensureFreeCapacity(freeCount + 1);
            freeIndices[freeCount++] = index;
        }
        pendingCreateIndex = -1;
    }

    private Class<? extends Component>[] componentTypes(int index) {
        if (index >= componentTypes.size()) {
            return componentTypeArray(0);
        }
        return componentTypes.get(index);
    }

    private void addComponentType(int index, Class<? extends Component> type) {
        Class<? extends Component>[] types = componentTypes(index);
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) {
                return;
            }
        }
        Class<? extends Component>[] updated = Arrays.copyOf(types, types.length + 1);
        updated[types.length] = type;
        setComponentTypes(index, updated);
    }

    private void removeComponentType(int index, Class<? extends Component> type) {
        Class<? extends Component>[] types = componentTypes(index);
        for (int i = 0; i < types.length; i++) {
            if (types[i] == type) {
                Class<? extends Component>[] updated = componentTypeArray(types.length - 1);
                java.lang.System.arraycopy(types, 0, updated, 0, i);
                java.lang.System.arraycopy(types, i + 1, updated, i, types.length - i - 1);
                setComponentTypes(index, updated);
                return;
            }
        }
    }

    private void setComponentTypes(int index, Class<? extends Component>[] types) {
        while (componentTypes.size() <= index) {
            componentTypes.add(componentTypeArray(0));
        }
        componentTypes.set(index, types);
    }

    private void refreshEntityLists() {
        for (int i = 0; i < entityLists.size(); i++) {
            entityLists.get(i).refresh();
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Component>[] componentTypeArray(int length) {
        return (Class<? extends Component>[]) new Class<?>[length];
    }

    private static Throwable aggregateFailure(Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    /**
     * @hidden Unforgeable ownership token used to synchronize the intrinsic
     * scene service with this world's entity lifecycle.
     */
    public static final class SceneAccess {
        private final World world;

        private SceneAccess(World world) {
            this.world = world;
        }

        /** @hidden Returns the world that owns this token. */
        public World world() {
            return world;
        }
    }
}
