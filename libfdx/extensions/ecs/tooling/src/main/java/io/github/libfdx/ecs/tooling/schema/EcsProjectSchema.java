package io.github.libfdx.ecs.tooling.schema;

import io.github.libfdx.ecs.component.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable explicit schema consumed by scene persistence and external tools. */
public final class EcsProjectSchema {
    private final EcsEntityAdapter entities;
    private final List<EcsComponentDescriptor<?>> components;
    private final Map<String, EcsComponentDescriptor<?>> componentsById;
    private final Map<Class<? extends Component>, EcsComponentDescriptor<?>> componentsByType;
    private final List<EcsEntityPreset> presets;
    private final Map<String, EcsEntityPreset> presetsById;
    private final EcsTransformAdapter transforms;
    private final EcsCameraAdapter cameras;
    private final EcsBoundsAdapter bounds;
    private final EcsAssetAdapter assets;

    private EcsProjectSchema(Builder builder) {
        entities = builder.entities;
        components = List.copyOf(builder.componentsById.values());
        componentsById = Map.copyOf(builder.componentsById);
        componentsByType = Map.copyOf(builder.componentsByType);
        presets = List.copyOf(builder.presetsById.values());
        presetsById = Map.copyOf(builder.presetsById);
        transforms = builder.transforms;
        cameras = builder.cameras;
        bounds = builder.bounds;
        assets = builder.assets;
    }

    public static Builder builder(EcsEntityAdapter entities) {
        return new Builder(entities);
    }

    public EcsEntityAdapter entities() {
        return entities;
    }

    public int componentCount() {
        return components.size();
    }

    public EcsComponentDescriptor<?> component(int index) {
        return components.get(index);
    }

    public EcsComponentDescriptor<?> component(String id) {
        return componentsById.get(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> EcsComponentDescriptor<T> component(Class<T> type) {
        return (EcsComponentDescriptor<T>) componentsByType.get(type);
    }

    public int presetCount() {
        return presets.size();
    }

    public EcsEntityPreset preset(int index) {
        return presets.get(index);
    }

    public EcsEntityPreset preset(String id) {
        return presetsById.get(id);
    }

    public EcsTransformAdapter transforms() {
        return transforms;
    }

    public EcsCameraAdapter cameras() {
        return cameras;
    }

    public EcsBoundsAdapter bounds() {
        return bounds;
    }

    public EcsAssetAdapter assets() {
        return assets;
    }

    public static final class Builder {
        private final EcsEntityAdapter entities;
        private final LinkedHashMap<String, EcsComponentDescriptor<?>> componentsById = new LinkedHashMap<>();
        private final LinkedHashMap<Class<? extends Component>, EcsComponentDescriptor<?>> componentsByType =
                new LinkedHashMap<>();
        private final LinkedHashMap<String, EcsEntityPreset> presetsById = new LinkedHashMap<>();
        private EcsTransformAdapter transforms;
        private EcsCameraAdapter cameras;
        private EcsBoundsAdapter bounds;
        private EcsAssetAdapter assets;

        private Builder(EcsEntityAdapter entities) {
            if (entities == null) {
                throw new IllegalArgumentException("entities cannot be null.");
            }
            this.entities = entities;
        }

        public Builder component(EcsComponentDescriptor<?> descriptor) {
            if (descriptor == null) {
                throw new IllegalArgumentException("descriptor cannot be null.");
            }
            EcsComponentDescriptor<?> previousId = componentsById.putIfAbsent(descriptor.id(), descriptor);
            if (previousId != null) {
                throw new IllegalArgumentException("Duplicate component id: " + descriptor.id());
            }
            EcsComponentDescriptor<?> previousType = componentsByType.putIfAbsent(descriptor.type(), descriptor);
            if (previousType != null) {
                componentsById.remove(descriptor.id());
                throw new IllegalArgumentException("Duplicate component type: " + descriptor.type().getName());
            }
            return this;
        }

        public Builder preset(EcsEntityPreset preset) {
            if (preset == null || isBlank(preset.id()) || isBlank(preset.name())) {
                throw new IllegalArgumentException("Preset id and name cannot be blank.");
            }
            if (presetsById.putIfAbsent(preset.id(), preset) != null) {
                throw new IllegalArgumentException("Duplicate preset id: " + preset.id());
            }
            return this;
        }

        public Builder transforms(EcsTransformAdapter transforms) {
            this.transforms = require(transforms, "transforms");
            return this;
        }

        public Builder cameras(EcsCameraAdapter cameras) {
            this.cameras = require(cameras, "cameras");
            return this;
        }

        public Builder bounds(EcsBoundsAdapter bounds) {
            this.bounds = require(bounds, "bounds");
            return this;
        }

        public Builder assets(EcsAssetAdapter assets) {
            this.assets = require(assets, "assets");
            return this;
        }

        public EcsProjectSchema build() {
            return new EcsProjectSchema(this);
        }

        private static <T> T require(T value, String label) {
            if (value == null) {
                throw new IllegalArgumentException(label + " cannot be null.");
            }
            return value;
        }

        private static boolean isBlank(String value) {
            return value == null || value.trim().length() == 0;
        }
    }
}
