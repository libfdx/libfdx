package io.github.libfdx.ecs.tooling.schema;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonCodec;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit persistence and editing metadata for one ECS component type. */
public final class EcsComponentDescriptor<T extends Component> {
    private final String id;
    private final String name;
    private final Class<T> type;
    private final EcsComponentFactory<T> factory;
    private final JsonCodec<T> codec;
    private final boolean persistent;
    private final List<EcsPropertyDescriptor<T>> properties;

    private EcsComponentDescriptor(Builder<T> builder) {
        id = builder.id;
        name = builder.name;
        type = builder.type;
        factory = builder.factory;
        codec = builder.codec;
        persistent = builder.persistent.booleanValue();
        properties = List.copyOf(builder.properties);
    }

    public static <T extends Component> Builder<T> builder(
            String id, String name, Class<T> type, EcsComponentFactory<T> factory) {
        return new Builder<>(id, name, type, factory);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    public boolean persistent() {
        return persistent;
    }

    public int propertyCount() {
        return properties.size();
    }

    public EcsPropertyDescriptor<T> property(int index) {
        return properties.get(index);
    }

    public EcsPropertyDescriptor<T> property(String propertyId) {
        for (int i = 0; i < properties.size(); i++) {
            EcsPropertyDescriptor<T> property = properties.get(i);
            if (property.id().equals(propertyId)) {
                return property;
            }
        }
        return null;
    }

    public T create() {
        T component = factory.create();
        if (component == null) {
            throw new IllegalStateException("Component factory returned null for " + id + ".");
        }
        return type.cast(component);
    }

    public T get(World world, int entity) {
        return world.get(entity, type);
    }

    public void add(World world, int entity, Component component) {
        world.add(entity, type, type.cast(component));
    }

    public T read(Json json, JsonValue value) {
        requirePersistent();
        T component = codec.read(json, value);
        if (component == null) {
            throw new IllegalStateException("Component codec returned null for " + id + ".");
        }
        return type.cast(component);
    }

    public JsonValue write(Json json, Component component) {
        requirePersistent();
        JsonWriter writer = new JsonWriter();
        codec.write(json, writer, type.cast(component));
        return json.read(writer.toString());
    }

    private void requirePersistent() {
        if (!persistent || codec == null) {
            throw new IllegalStateException("Component " + id + " is transient.");
        }
    }

    public static final class Builder<T extends Component> {
        private final String id;
        private final String name;
        private final Class<T> type;
        private final EcsComponentFactory<T> factory;
        private final List<EcsPropertyDescriptor<T>> properties = new ArrayList<>();
        private JsonCodec<T> codec;
        private Boolean persistent;

        private Builder(String id, String name, Class<T> type, EcsComponentFactory<T> factory) {
            this.id = requireText(id, "id");
            this.name = requireText(name, "name");
            if (type == null || factory == null) {
                throw new IllegalArgumentException("type and factory cannot be null.");
            }
            this.type = type;
            this.factory = factory;
        }

        public Builder<T> persistent(JsonCodec<T> codec) {
            if (codec == null) {
                throw new IllegalArgumentException("codec cannot be null.");
            }
            this.codec = codec;
            persistent = Boolean.TRUE;
            return this;
        }

        public Builder<T> transientComponent() {
            codec = null;
            persistent = Boolean.FALSE;
            return this;
        }

        public Builder<T> property(EcsPropertyDescriptor<T> property) {
            if (property == null || property.ownerType() != type) {
                throw new IllegalArgumentException("Property owner must be " + type.getName() + ".");
            }
            properties.add(property);
            return this;
        }

        public EcsComponentDescriptor<T> build() {
            if (persistent == null) {
                throw new IllegalStateException("Component " + id + " must be declared persistent or transient.");
            }
            Set<String> propertyIds = new HashSet<>();
            for (int i = 0; i < properties.size(); i++) {
                if (!propertyIds.add(properties.get(i).id())) {
                    throw new IllegalStateException("Duplicate property id " + properties.get(i).id()
                            + " in component " + id + ".");
                }
            }
            return new EcsComponentDescriptor<>(this);
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return value.trim();
    }
}
