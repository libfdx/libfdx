package io.github.libfdx.ecs.component;

import io.github.libfdx.ecs.World;

import java.util.Arrays;

public final class ComponentStore<T extends Component> {
    private final Class<T> type;
    private int[] entities = new int[8];
    private Object[] components = new Object[8];
    private int[] sparse = new int[8];
    private int size;

    public ComponentStore(Class<T> type) {
        this.type = type;
    }

    public Class<T> type() {
        return type;
    }

    public int size() {
        return size;
    }

    public int entityAt(int index) {
        checkDenseIndex(index);
        return entities[index];
    }

    @SuppressWarnings("unchecked")
    public T componentAt(int index) {
        checkDenseIndex(index);
        return (T) components[index];
    }

    public boolean hasIndex(int entityIndex) {
        return entityIndex < sparse.length && sparse[entityIndex] != 0;
    }

    public boolean has(int entity) {
        return hasIndex(World.entityIndex(entity));
    }

    @SuppressWarnings("unchecked")
    public T get(int entity) {
        int entityIndex = World.entityIndex(entity);
        if (!hasIndex(entityIndex)) {
            return null;
        }
        return (T) components[sparse[entityIndex] - 1];
    }

    public T require(int entity) {
        T component = get(entity);
        if (component == null) {
            throw new IllegalStateException("Missing component " + type.getName() + " for entity " + entity + ".");
        }
        return component;
    }

    public void add(int entity, T component) {
        int entityIndex = World.entityIndex(entity);
        ensureSparseCapacity(entityIndex + 1);
        int sparseValue = sparse[entityIndex];
        if (sparseValue != 0) {
            components[sparseValue - 1] = component;
            return;
        }
        ensureDenseCapacity(size + 1);
        entities[size] = entity;
        components[size] = component;
        sparse[entityIndex] = size + 1;
        size++;
    }

    public boolean remove(int entity) {
        int entityIndex = World.entityIndex(entity);
        if (!hasIndex(entityIndex)) {
            return false;
        }
        int slot = sparse[entityIndex] - 1;
        int last = size - 1;
        int lastEntity = entities[last];
        Object lastComponent = components[last];
        entities[slot] = lastEntity;
        components[slot] = lastComponent;
        sparse[World.entityIndex(lastEntity)] = slot + 1;
        entities[last] = 0;
        components[last] = null;
        sparse[entityIndex] = 0;
        size--;
        return true;
    }

    public void clear() {
        Arrays.fill(entities, 0, size, 0);
        Arrays.fill(components, 0, size, null);
        Arrays.fill(sparse, 0);
        size = 0;
    }

    private void ensureSparseCapacity(int required) {
        if (required <= sparse.length) {
            return;
        }
        int capacity = sparse.length;
        while (capacity < required) {
            capacity *= 2;
        }
        sparse = Arrays.copyOf(sparse, capacity);
    }

    private void ensureDenseCapacity(int required) {
        if (required <= entities.length) {
            return;
        }
        int capacity = entities.length;
        while (capacity < required) {
            capacity *= 2;
        }
        entities = Arrays.copyOf(entities, capacity);
        components = Arrays.copyOf(components, capacity);
    }

    private void checkDenseIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }
}
