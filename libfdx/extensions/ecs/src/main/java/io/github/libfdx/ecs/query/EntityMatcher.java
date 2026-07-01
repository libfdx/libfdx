package io.github.libfdx.ecs.query;

import io.github.libfdx.ecs.World;

import java.util.LinkedHashSet;

public final class EntityMatcher {
    private final World world;
    private final LinkedHashSet<Class<?>> allTypes = new LinkedHashSet<>();
    private final LinkedHashSet<Class<?>> oneTypes = new LinkedHashSet<>();
    private final LinkedHashSet<Class<?>> anyTypes = new LinkedHashSet<>();
    private final LinkedHashSet<Class<?>> excludedTypes = new LinkedHashSet<>();
    private boolean frozen;

    public EntityMatcher(World world) {
        this.world = world;
    }

    public EntityMatcher all(Class<?>... types) {
        addTypes(allTypes, types);
        return this;
    }

    public EntityMatcher one(Class<?>... types) {
        addTypes(oneTypes, types);
        return this;
    }

    public EntityMatcher any(Class<?>... types) {
        addTypes(anyTypes, types);
        return this;
    }

    public EntityMatcher exclude(Class<?>... types) {
        addTypes(excludedTypes, types);
        return this;
    }

    public boolean matches(int entity) {
        return world.isAttached(entity) && matchesAttached(entity);
    }

    public void clear() {
        requireMutable();
        allTypes.clear();
        oneTypes.clear();
        anyTypes.clear();
        excludedTypes.clear();
    }

    public boolean matchesAttached(int entity) {
        for (Class<?> type : allTypes) {
            if (!world.hasNow(entity, type)) {
                return false;
            }
        }
        if (!oneTypes.isEmpty()) {
            int count = 0;
            for (Class<?> type : oneTypes) {
                if (world.hasNow(entity, type)) {
                    count++;
                }
            }
            if (count != 1) {
                return false;
            }
        }
        if (!anyTypes.isEmpty()) {
            boolean found = false;
            for (Class<?> type : anyTypes) {
                if (world.hasNow(entity, type)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        for (Class<?> type : excludedTypes) {
            if (world.hasNow(entity, type)) {
                return false;
            }
        }
        return true;
    }

    public Class<?>[] candidateTypes() {
        if (!allTypes.isEmpty()) {
            return allTypes.toArray(Class<?>[]::new);
        }
        return new Class<?>[0];
    }

    public void freeze() {
        frozen = true;
    }

    private void addTypes(LinkedHashSet<Class<?>> target, Class<?>... types) {
        requireMutable();
        if (types == null) {
            return;
        }
        for (Class<?> type : types) {
            if (type == null) {
                throw new IllegalArgumentException("component type cannot be null.");
            }
            target.add(type);
        }
    }

    private void requireMutable() {
        if (frozen) {
            throw new IllegalStateException("Matcher cannot change after an EntityList is created.");
        }
    }
}
