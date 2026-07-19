package io.github.libfdx.ecs.query;

import io.github.libfdx.collections.Array;
import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.Component;

public final class EntityMatcher {
    private final World world;
    private final Array<Class<? extends Component>> allTypes = new Array<>();
    private final Array<Class<? extends Component>> oneTypes = new Array<>();
    private final Array<Class<? extends Component>> anyTypes = new Array<>();
    private final Array<Class<? extends Component>> excludedTypes = new Array<>();
    private boolean frozen;

    public EntityMatcher(World world) {
        this.world = world;
    }

    @SafeVarargs
    public final EntityMatcher all(Class<? extends Component>... types) {
        addTypes(allTypes, types);
        return this;
    }

    @SafeVarargs
    public final EntityMatcher one(Class<? extends Component>... types) {
        addTypes(oneTypes, types);
        return this;
    }

    @SafeVarargs
    public final EntityMatcher any(Class<? extends Component>... types) {
        addTypes(anyTypes, types);
        return this;
    }

    @SafeVarargs
    public final EntityMatcher exclude(Class<? extends Component>... types) {
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
        for (int i = 0; i < allTypes.size(); i++) {
            if (!world.hasNow(entity, allTypes.get(i))) {
                return false;
            }
        }
        if (!oneTypes.isEmpty()) {
            int count = 0;
            for (int i = 0; i < oneTypes.size(); i++) {
                if (world.hasNow(entity, oneTypes.get(i))) {
                    count++;
                }
            }
            if (count != 1) {
                return false;
            }
        }
        if (!anyTypes.isEmpty()) {
            boolean found = false;
            for (int i = 0; i < anyTypes.size(); i++) {
                if (world.hasNow(entity, anyTypes.get(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        for (int i = 0; i < excludedTypes.size(); i++) {
            if (world.hasNow(entity, excludedTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    public Class<? extends Component>[] candidateTypes() {
        Class<? extends Component>[] result = componentTypeArray(allTypes.size());
        for (int i = 0; i < allTypes.size(); i++) {
            result[i] = allTypes.get(i);
        }
        return result;
    }

    public void freeze() {
        frozen = true;
    }

    private void addTypes(
        Array<Class<? extends Component>> target,
        Class<? extends Component>[] types
    ) {
        requireMutable();
        if (types == null) {
            return;
        }
        for (Class<? extends Component> type : types) {
            if (type == null) {
                throw new IllegalArgumentException("component type cannot be null.");
            }
            if (!Component.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(type.getName() + " does not implement Component.");
            }
            if (!target.contains(type)) {
                target.add(type);
            }
        }
    }

    private void requireMutable() {
        if (frozen) {
            throw new IllegalStateException("Matcher cannot change after an EntityList is created.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Component>[] componentTypeArray(int length) {
        return (Class<? extends Component>[]) new Class<?>[length];
    }
}
