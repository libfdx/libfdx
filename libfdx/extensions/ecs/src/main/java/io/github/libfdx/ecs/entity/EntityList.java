package io.github.libfdx.ecs.entity;

import io.github.libfdx.collections.IntArray;
import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.ComponentStore;
import io.github.libfdx.ecs.query.EntityMatcher;

public final class EntityList {
    private final World world;
    private final EntityMatcher matcher;
    private final IntArray entities = new IntArray(8);

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
        return entities.size();
    }

    public boolean isEmpty() {
        return entities.isEmpty();
    }

    public int entityAt(int index) {
        return entities.get(index);
    }

    public void refresh() {
        entities.clear();
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
        entities.add(entity);
    }
}
