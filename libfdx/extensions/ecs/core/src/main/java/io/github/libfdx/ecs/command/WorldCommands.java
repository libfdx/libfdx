package io.github.libfdx.ecs.command;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.ecs.manager.Manager;
import io.github.libfdx.ecs.system.System;

public final class WorldCommands {
    private final World world;
    private final Array<Command> commands = new Array<>();
    private final ObjectMap<Class<? extends Manager>, Manager> pendingManagers = new ObjectMap<>();

    public WorldCommands(World world) {
        this.world = world;
    }

    public int createEntity() {
        int entity = world.reserveEntity();
        commands.add(target -> target.applyCreate(entity));
        return entity;
    }

    public void destroyEntity(int entity) {
        world.requireMutableEntity(entity);
        commands.add(target -> target.applyDestroy(entity));
    }

    public <T extends Component> void add(int entity, T component) {
        if (component == null) {
            throw new IllegalArgumentException("component cannot be null.");
        }
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) component.getClass();
        add(entity, type, component);
    }

    public <T extends Component> void add(int entity, Class<T> type, T component) {
        world.requireMutableEntity(entity);
        world.requireComponentType(type, component);
        commands.add(target -> target.applyAdd(entity, type, component));
    }

    public void remove(int entity, Class<? extends Component> type) {
        world.requireMutableEntity(entity);
        if (type == null) {
            throw new IllegalArgumentException("component type cannot be null.");
        }
        commands.add(target -> target.applyRemove(entity, type));
    }

    public <M extends Manager, T extends M> T addManager(T manager, Class<M> type) {
        if (manager == null) {
            throw new IllegalArgumentException("manager cannot be null.");
        }
        if (type == null) {
            throw new IllegalArgumentException("manager type cannot be null.");
        }
        if (!type.isInstance(manager)) {
            throw new IllegalArgumentException("Manager is not an instance of " + type.getName() + ".");
        }
        if (world.getManager(type) != null || pendingManagers.containsKey(type)) {
            return null;
        }
        pendingManagers.put(type, manager);
        commands.add(target -> {
            target.applyAddManager(type, manager);
            pendingManagers.remove(type);
        });
        return manager;
    }

    public void removeManager(Class<? extends Manager> type) {
        if (type == null) {
            throw new IllegalArgumentException("manager type cannot be null.");
        }
        commands.add(target -> target.applyRemoveManager(type));
    }

    public <T extends System> T addSystem(T system) {
        if (system == null) {
            throw new IllegalArgumentException("system cannot be null.");
        }
        commands.add(target -> target.applyAddSystem(system));
        return system;
    }

    public void removeSystem(Class<? extends System> type) {
        if (type == null) {
            throw new IllegalArgumentException("system type cannot be null.");
        }
        commands.add(target -> target.applyRemoveSystem(type));
    }

    public void clear() {
        commands.add(World::applyClear);
    }

    public int size() {
        return commands.size();
    }

    public void flush() {
        for (int i = 0; i < commands.size(); i++) {
            commands.get(i).apply(world);
        }
        commands.clear();
    }

    public void discard() {
        pendingManagers.clear();
        commands.clear();
    }

    private interface Command {
        void apply(World world);
    }
}
