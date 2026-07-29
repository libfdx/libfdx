package io.github.libfdx.samples.g2d.spritemovement.scene;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.scene.SceneManager;
import io.github.libfdx.ecs.scene.TransformComponentJsonCodec;
import io.github.libfdx.ecs.schema.EcsAssetAdapter;
import io.github.libfdx.ecs.schema.EcsBoundsAdapter;
import io.github.libfdx.ecs.schema.EcsComponentDescriptor;
import io.github.libfdx.ecs.schema.EcsEntityPreset;
import io.github.libfdx.ecs.schema.EcsPropertyDescriptor;
import io.github.libfdx.ecs.schema.EcsTransformAdapter;
import io.github.libfdx.ecs.transform.Transform;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonCodec;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject;
import io.github.libfdx.samples.g2d.spritemovement.component.Camera2DComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.PlayerControlComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.Sprite2DComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.WallComponent;

/** Optional project-specific descriptors and helpers layered onto {@link SceneManager}. */
public final class SpriteMovementScenes {
    public static final String CAMERA_TYPE = "sprite-movement.camera-2d";
    public static final String PLAYER_CONTROL_TYPE = "sprite-movement.player-control";
    public static final String SPRITE_TYPE = "sprite-movement.sprite-2d";
    public static final String TRANSFORM_TYPE = "sprite-movement.transform";
    public static final String WALL_TYPE = "sprite-movement.wall";

    private static final EcsAssetAdapter ASSETS = new SpriteMovementAssetAdapter();
    private static final EcsTransformAdapter TRANSFORMS = new SceneTransformAdapter();
    private static final EcsBoundsAdapter BOUNDS = new SceneBoundsAdapter();

    private SpriteMovementScenes() {
    }

    public static void configure(SceneManager scenes) {
        if (scenes == null) {
            throw new IllegalArgumentException("scenes cannot be null.");
        }
        scenes.component(cameraDescriptor())
                .component(playerControlDescriptor())
                .component(spriteDescriptor())
                .component(transformDescriptor())
                .component(wallDescriptor())
                .preset(new ScenePreset("player", "Player", ScenePreset.PLAYER))
                .preset(new ScenePreset("sprite", "Sprite", ScenePreset.SPRITE))
                .preset(new ScenePreset("wall", "Wall", ScenePreset.WALL))
                .preset(new ScenePreset("camera-2d", "2D Camera", ScenePreset.CAMERA))
                .transforms(TRANSFORMS)
                .bounds(BOUNDS)
                .assets(ASSETS);
    }

    public static int findByPersistentId(World world, long id) {
        return world.scenes().find(id);
    }

    public static String normalizeAsset(String path) {
        String normalized = normalizeRelativePath(path, "asset path");
        if (normalized.startsWith("assets/")) {
            normalized = normalized.substring("assets/".length());
        }
        if (!ASSETS.accepts(normalized)) {
            throw new IllegalArgumentException("Unsupported Sprite Movement asset path: " + path);
        }
        return normalized;
    }

    private static String normalizeRelativePath(String path, String label) {
        if (path == null || path.trim().length() == 0) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException(label + " must be project-relative: " + path);
        }
        String[] parts = normalized.split("/", -1);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() == 0 || ".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException(label + " contains an invalid path segment: " + path);
            }
            if (i > 0) {
                result.append('/');
            }
            result.append(part);
        }
        return result.toString();
    }

    public static Camera updateCamera(World world, int entity, int width, int height) {
        Camera2DComponent component = world.get(entity, Camera2DComponent.class);
        if (component == null) {
            return null;
        }
        TransformComponent transformComponent = world.get(entity, TransformComponent.class);
        Transform transform = transformComponent != null ? transformComponent.transform : null;
        float viewportHeight = Math.max(0.01f, component.viewportHeight);
        float aspect = height > 0 ? Math.max(1, width) / (float) height : 1.0f;
        float near = Math.max(0.0001f, component.near);
        float far = Math.max(near + 0.0001f, component.far);
        float x = transform != null ? transform.x() : 0.0f;
        float y = transform != null ? transform.y() : 0.0f;
        float z = transform != null ? transform.z() : 10.0f;
        return component.camera
                .projection(CameraProjection.ORTHOGRAPHIC)
                .viewport(viewportHeight * aspect, viewportHeight)
                .position(x, y, z)
                .direction(0.0f, 0.0f, -1.0f)
                .up(0.0f, 1.0f, 0.0f)
                .nearFar(near, far)
                .update();
    }

    private static EcsComponentDescriptor<TransformComponent> transformDescriptor() {
        return EcsComponentDescriptor.builder(
                        TRANSFORM_TYPE, "Transform", TransformComponent.class, TransformComponent::new)
                .persistent(new TransformComponentJsonCodec())
                .property(EcsPropertyDescriptor.vectorProperty(
                        "position", "Position", TransformComponent.class, 3,
                        new EcsPropertyDescriptor.FloatAccessor<TransformComponent>() {
                            @Override
                            public float get(TransformComponent component, int element) {
                                return switch (element) {
                                    case 0 -> component.transform.x();
                                    case 1 -> component.transform.y();
                                    default -> component.transform.z();
                                };
                            }

                            @Override
                            public void set(TransformComponent component, int element, float value) {
                                float x = element == 0 ? value : component.transform.x();
                                float y = element == 1 ? value : component.transform.y();
                                float z = element == 2 ? value : component.transform.z();
                                component.transform.position(x, y, z);
                            }
                        }))
                .property(EcsPropertyDescriptor.vectorProperty(
                        "rotation", "Rotation", TransformComponent.class, 4,
                        new EcsPropertyDescriptor.FloatAccessor<TransformComponent>() {
                            @Override
                            public float get(TransformComponent component, int element) {
                                return switch (element) {
                                    case 0 -> component.transform.rotation().x();
                                    case 1 -> component.transform.rotation().y();
                                    case 2 -> component.transform.rotation().z();
                                    default -> component.transform.rotation().w();
                                };
                            }

                            @Override
                            public void set(TransformComponent component, int element, float value) {
                                float x = component.transform.rotation().x();
                                float y = component.transform.rotation().y();
                                float z = component.transform.rotation().z();
                                float w = component.transform.rotation().w();
                                switch (element) {
                                    case 0 -> x = value;
                                    case 1 -> y = value;
                                    case 2 -> z = value;
                                    default -> w = value;
                                }
                                component.transform.rotation(x, y, z, w);
                            }
                        }))
                .property(EcsPropertyDescriptor.vectorProperty(
                        "scale", "Scale", TransformComponent.class, 3,
                        new EcsPropertyDescriptor.FloatAccessor<TransformComponent>() {
                            @Override
                            public float get(TransformComponent component, int element) {
                                return switch (element) {
                                    case 0 -> component.transform.scaleX();
                                    case 1 -> component.transform.scaleY();
                                    default -> component.transform.scaleZ();
                                };
                            }

                            @Override
                            public void set(TransformComponent component, int element, float value) {
                                float x = element == 0 ? value : component.transform.scaleX();
                                float y = element == 1 ? value : component.transform.scaleY();
                                float z = element == 2 ? value : component.transform.scaleZ();
                                component.transform.scale(x, y, z);
                            }
                        }))
                .build();
    }

    private static EcsComponentDescriptor<Sprite2DComponent> spriteDescriptor() {
        return EcsComponentDescriptor.builder(
                        SPRITE_TYPE, "2D Sprite", Sprite2DComponent.class, Sprite2DComponent::new)
                .persistent(new SpriteCodec())
                .property(EcsPropertyDescriptor.assetProperty(
                        "asset", "Asset", Sprite2DComponent.class,
                        new EcsPropertyDescriptor.TextAccessor<Sprite2DComponent>() {
                            @Override
                            public String get(Sprite2DComponent component) {
                                return component.assetPath;
                            }

                            @Override
                            public void set(Sprite2DComponent component, String value) {
                                component.assetPath = normalizeAsset(value);
                            }
                        }))
                .property(EcsPropertyDescriptor.vectorProperty(
                        "size", "Size", Sprite2DComponent.class, 2,
                        new EcsPropertyDescriptor.FloatAccessor<Sprite2DComponent>() {
                            @Override
                            public float get(Sprite2DComponent component, int element) {
                                return element == 0 ? component.width : component.height;
                            }

                            @Override
                            public void set(Sprite2DComponent component, int element, float value) {
                                if (element == 0) {
                                    component.width = Math.max(0.01f, value);
                                } else {
                                    component.height = Math.max(0.01f, value);
                                }
                            }
                        }))
                .property(EcsPropertyDescriptor.colorProperty(
                        "tint", "Tint", Sprite2DComponent.class, 4,
                        new EcsPropertyDescriptor.FloatAccessor<Sprite2DComponent>() {
                            @Override
                            public float get(Sprite2DComponent component, int element) {
                                return switch (element) {
                                    case 0 -> component.red;
                                    case 1 -> component.green;
                                    case 2 -> component.blue;
                                    default -> component.alpha;
                                };
                            }

                            @Override
                            public void set(Sprite2DComponent component, int element, float value) {
                                float clamped = Sprite2DComponent.clamp(value);
                                switch (element) {
                                    case 0 -> component.red = clamped;
                                    case 1 -> component.green = clamped;
                                    case 2 -> component.blue = clamped;
                                    default -> component.alpha = clamped;
                                }
                            }
                        }))
                .build();
    }

    private static EcsComponentDescriptor<Camera2DComponent> cameraDescriptor() {
        return EcsComponentDescriptor.builder(
                        CAMERA_TYPE, "2D Camera", Camera2DComponent.class, Camera2DComponent::new)
                .persistent(new CameraCodec())
                .property(EcsPropertyDescriptor.booleanProperty(
                        "primary", "Primary", Camera2DComponent.class,
                        new EcsPropertyDescriptor.BooleanAccessor<Camera2DComponent>() {
                            @Override
                            public boolean get(Camera2DComponent component) {
                                return component.primary;
                            }

                            @Override
                            public void set(Camera2DComponent component, boolean value) {
                                component.primary = value;
                            }
                        }))
                .property(cameraFloat("viewport-height", "Viewport Height", 0))
                .property(cameraFloat("near", "Near", 1))
                .property(cameraFloat("far", "Far", 2))
                .build();
    }

    private static EcsPropertyDescriptor<Camera2DComponent> cameraFloat(String id, String name, int field) {
        return EcsPropertyDescriptor.floatProperty(
                id, name, Camera2DComponent.class,
                new EcsPropertyDescriptor.FloatAccessor<Camera2DComponent>() {
                    @Override
                    public float get(Camera2DComponent component, int element) {
                        return switch (field) {
                            case 0 -> component.viewportHeight;
                            case 1 -> component.near;
                            default -> component.far;
                        };
                    }

                    @Override
                    public void set(Camera2DComponent component, int element, float value) {
                        switch (field) {
                            case 0 -> component.viewportHeight = Math.max(0.01f, value);
                            case 1 -> component.near = Math.max(0.0001f, value);
                            default -> component.far = Math.max(component.near + 0.0001f, value);
                        }
                    }
                });
    }

    private static EcsComponentDescriptor<PlayerControlComponent> playerControlDescriptor() {
        return EcsComponentDescriptor.builder(
                        PLAYER_CONTROL_TYPE, "Player Control", PlayerControlComponent.class,
                        PlayerControlComponent::new)
                .persistent(new PlayerControlCodec())
                .property(EcsPropertyDescriptor.floatProperty(
                        "speed", "Speed", PlayerControlComponent.class,
                        new EcsPropertyDescriptor.FloatAccessor<PlayerControlComponent>() {
                            @Override
                            public float get(PlayerControlComponent component, int element) {
                                return component.speed;
                            }

                            @Override
                            public void set(PlayerControlComponent component, int element, float value) {
                                component.speed = Math.max(0.0f, value);
                            }
                        }))
                .build();
    }

    private static EcsComponentDescriptor<WallComponent> wallDescriptor() {
        return EcsComponentDescriptor.builder(WALL_TYPE, "Wall", WallComponent.class, WallComponent::new)
                .persistent(new WallCodec())
                .build();
    }

    private static JsonValue requireArray(JsonValue object, String name, int size) {
        JsonValue value = object.require(name);
        if (!value.isArray() || value.size() != size) {
            throw new IllegalArgumentException(name + " must contain " + size + " numbers.");
        }
        return value;
    }

    private static final class SceneTransformAdapter implements EcsTransformAdapter {
        @Override
        public Transform transform(World world, int entity) {
            TransformComponent component = world.get(entity, TransformComponent.class);
            return component != null ? component.transform : null;
        }

        @Override
        public void add(World world, int entity) {
            if (world.get(entity, TransformComponent.class) == null) {
                world.add(entity, new TransformComponent());
            }
        }
    }

    private static final class SceneBoundsAdapter implements EcsBoundsAdapter {
        @Override
        public boolean bounds(World world, int entity, BoundingBox out) {
            TransformComponent transform = world.get(entity, TransformComponent.class);
            Sprite2DComponent sprite = world.get(entity, Sprite2DComponent.class);
            if (transform == null || sprite == null || out == null) {
                return false;
            }
            float halfWidth = Math.abs(sprite.width * transform.transform.scaleX()) * 0.5f;
            float halfHeight = Math.abs(sprite.height * transform.transform.scaleY()) * 0.5f;
            float x = transform.transform.x();
            float y = transform.transform.y();
            float z = transform.transform.z();
            out.min().set(x - halfWidth, y - halfHeight, z - 0.01f);
            out.max().set(x + halfWidth, y + halfHeight, z + 0.01f);
            return true;
        }
    }

    private static final class SpriteMovementAssetAdapter implements EcsAssetAdapter {
        @Override
        public String normalize(String projectRelativePath) {
            return normalizeAsset(projectRelativePath);
        }

        @Override
        public boolean accepts(String projectRelativePath) {
            if (projectRelativePath == null) {
                return false;
            }
            String path = projectRelativePath.replace('\\', '/').toLowerCase();
            return !path.startsWith("/") && !path.contains("../") && !path.contains("/../")
                    && path.endsWith(".png");
        }
    }

    private static final class ScenePreset implements EcsEntityPreset {
        static final int PLAYER = 0;
        static final int SPRITE = 1;
        static final int WALL = 2;
        static final int CAMERA = 3;

        private final String id;
        private final String name;
        private final int kind;

        ScenePreset(String id, String name, int kind) {
            this.id = id;
            this.name = name;
            this.kind = kind;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void populate(World world, int entity) {
            TRANSFORMS.add(world, entity);
            if (kind == CAMERA) {
                world.add(entity, new Camera2DComponent(false));
                return;
            }
            String asset = kind == WALL
                    ? SpriteMovementProject.WALL_TILE
                    : SpriteMovementProject.PLAYER_SPRITE;
            world.add(entity, new Sprite2DComponent(asset, 1.0f, 1.0f));
            if (kind == PLAYER) {
                world.add(entity, new PlayerControlComponent());
            } else if (kind == WALL) {
                world.add(entity, new WallComponent());
            }
        }
    }

    private static final class SpriteCodec implements JsonCodec<Sprite2DComponent> {
        @Override
        public Sprite2DComponent read(Json json, JsonValue value) {
            JsonValue size = requireArray(value, "size", 2);
            JsonValue tint = requireArray(value, "tint", 4);
            return new Sprite2DComponent(
                    value.require("asset").stringValue(),
                    size.require(0).floatValue(),
                    size.require(1).floatValue())
                    .tint(
                            tint.require(0).floatValue(),
                            tint.require(1).floatValue(),
                            tint.require(2).floatValue(),
                            tint.require(3).floatValue());
        }

        @Override
        public void write(Json json, JsonWriter writer, Sprite2DComponent component) {
            writer.object()
                    .name("asset").value(normalizeAsset(component.assetPath))
                    .name("size").array().value(component.width).value(component.height).endArray()
                    .name("tint").array()
                        .value(component.red).value(component.green).value(component.blue).value(component.alpha)
                    .endArray()
                    .endObject();
        }
    }

    private static final class CameraCodec implements JsonCodec<Camera2DComponent> {
        @Override
        public Camera2DComponent read(Json json, JsonValue value) {
            Camera2DComponent camera = new Camera2DComponent(value.require("primary").booleanValue());
            camera.viewportHeight = Math.max(0.01f, value.require("viewportHeight").floatValue());
            camera.near = Math.max(0.0001f, value.require("near").floatValue());
            camera.far = Math.max(camera.near + 0.0001f, value.require("far").floatValue());
            return camera;
        }

        @Override
        public void write(Json json, JsonWriter writer, Camera2DComponent component) {
            writer.object()
                    .name("primary").value(component.primary)
                    .name("viewportHeight").value(component.viewportHeight)
                    .name("near").value(component.near)
                    .name("far").value(component.far)
                    .endObject();
        }
    }

    private static final class PlayerControlCodec implements JsonCodec<PlayerControlComponent> {
        @Override
        public PlayerControlComponent read(Json json, JsonValue value) {
            return new PlayerControlComponent(value.require("speed").floatValue());
        }

        @Override
        public void write(Json json, JsonWriter writer, PlayerControlComponent component) {
            writer.object().name("speed").value(component.speed).endObject();
        }
    }

    private static final class WallCodec implements JsonCodec<WallComponent> {
        @Override
        public WallComponent read(Json json, JsonValue value) {
            if (!value.isObject()) {
                throw new IllegalArgumentException("Wall data must be an object.");
            }
            return new WallComponent();
        }

        @Override
        public void write(Json json, JsonWriter writer, WallComponent component) {
            writer.object().endObject();
        }
    }
}
