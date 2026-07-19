package io.github.libfdx.ecs.component;

import io.github.libfdx.ecs.World;

public final class ComponentMapper<T extends Component> {
    private final World world;
    private final ComponentStore<T> store;

    public ComponentMapper(World world, ComponentStore<T> store) {
        this.world = world;
        this.store = store;
    }

    public Class<T> type() {
        return store.type();
    }

    public int size() {
        return store.size();
    }

    public int entityAt(int index) {
        return store.entityAt(index);
    }

    public T componentAt(int index) {
        return store.componentAt(index);
    }

    public void add(int entity, T component) {
        world.add(entity, type(), component);
    }

    public T get(int entity) {
        return world.get(entity, type());
    }

    public T require(int entity) {
        return world.require(entity, type());
    }

    public boolean has(int entity) {
        return world.has(entity, type());
    }

    public void remove(int entity) {
        world.remove(entity, type());
    }
}
