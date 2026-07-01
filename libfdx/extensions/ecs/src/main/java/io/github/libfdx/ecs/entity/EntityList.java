package io.github.libfdx.ecs.entity;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentStore;
import io.github.libfdx.ecs.query.EntityMatcher;

import java.util.Arrays;

public final class EntityList {
    private final World world;
    private final EntityMatcher matcher;
    private int[] entities = new int[8];
    private int size;

    public EntityList(World world, EntityMatcher matcher) {
        this.world = world;
        this.matcher = matcher;
        matcher.freeze();
        refresh();
    }

    public EntityMatcher matcher() {
        return matcher;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int entityAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
        return entities[index];
    }

    public void refresh() {
        size = 0;
        Class<?>[] candidateTypes = matcher.candidateTypes();
        if (candidateTypes.length == 0) {
            world.collectAttachedEntities(this);
            return;
        }
        ComponentStore<?> smallest = world.smallestStore(candidateTypes);
        if (smallest == null) {
            return;
        }
        for (int i = 0; i < smallest.size(); i++) {
            int entity = smallest.entityAt(i);
            if (matcher.matchesAttached(entity)) {
                addNow(entity);
            }
        }
    }

    public void addNow(int entity) {
        if (size == entities.length) {
            entities = Arrays.copyOf(entities, entities.length * 2);
        }
        entities[size++] = entity;
    }
}
