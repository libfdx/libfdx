package io.github.libfdx.tests;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.tests.graphics.Billboard3DTest;
import io.github.libfdx.tests.graphics.CameraControllersShowcaseTest;
import io.github.libfdx.tests.graphics.CircleTest;
import io.github.libfdx.tests.graphics.Fog2DTest;
import io.github.libfdx.tests.graphics.Fog3DTest;
import io.github.libfdx.tests.graphics.FogOfWar2DTest;
import io.github.libfdx.tests.graphics.FogOfWar3DTest;
import io.github.libfdx.tests.graphics.ModelBatchTest;
import io.github.libfdx.tests.graphics.Outline2DTest;
import io.github.libfdx.tests.graphics.Outline3DTest;
import io.github.libfdx.tests.graphics.Particles2DTest;
import io.github.libfdx.tests.graphics.Particles3DTest;
import io.github.libfdx.tests.graphics.PointLight3DTest;
import io.github.libfdx.tests.graphics.ReadbackTest;
import io.github.libfdx.tests.graphics.ShadowMap3DTest;
import io.github.libfdx.tests.graphics.ShaderRuntimeTest;
import io.github.libfdx.tests.graphics.ShaderSceneTest;
import io.github.libfdx.tests.graphics.Skybox3DTest;
import io.github.libfdx.tests.graphics.SkinnedModelBatchTest;
import io.github.libfdx.tests.graphics.SpotLight3DTest;
import io.github.libfdx.tests.graphics.SquareTest;
import io.github.libfdx.tests.graphics.SpriteBatchTest;
import io.github.libfdx.tests.graphics.TextureTest;
import io.github.libfdx.tests.graphics.TileMapRuntimeTest;
import io.github.libfdx.tests.graphics.TriangleTest;
import io.github.libfdx.tests.ui.UiKitTest;

import java.util.Locale;

/**
 * Represents a test selector.
 *
 * @author xpenatan
 */
public final class TestSelector {
    /**
     * Defines the factory contract for test instances.
     *
     * @author xpenatan
     */
    public interface TestFactory {
        /**
         * Creates a value.
         *
         * @param exitAfterFrames the exit after frames
         * @return the created value
         */
        ApplicationListener create(long exitAfterFrames);
    }

    /**
     * Describes the values used to create or identify a test.
     *
     * @author xpenatan
     */
    public static final class TestDescriptor {
        private final String name;
        private final String displayName;
        private final String category;
        private final int defaultWidth;
        private final int defaultHeight;
        private final TestFactory factory;

        private TestDescriptor(String name, String displayName, String category, int defaultWidth, int defaultHeight,
                TestFactory factory) {
            this.name = name;
            this.displayName = displayName;
            this.category = category;
            this.defaultWidth = defaultWidth;
            this.defaultHeight = defaultHeight;
            this.factory = factory;
        }

        /**
         * Returns the name.
         *
         * @return the name
         */
        public String name() {
            return name;
        }

        /**
         * Returns the display name.
         *
         * @return the display name
         */
        public String displayName() {
            return displayName;
        }

        /**
         * Returns the category.
         *
         * @return the category
         */
        public String category() {
            return category;
        }

        /**
         * Returns the default width.
         *
         * @return the default width
         */
        public int defaultWidth() {
            return defaultWidth;
        }

        /**
         * Returns the default height.
         *
         * @return the default height
         */
        public int defaultHeight() {
            return defaultHeight;
        }

        ApplicationListener create(long exitAfterFrames) {
            return factory.create(exitAfterFrames);
        }
    }

    private static final String TRIANGLE = "triangle";
    private static final String SQUARE = "square";
    private static final String CIRCLE = "circle";
    private static final String TEXTURE = "texture";
    private static final String SPRITE = "sprite";
    private static final String OUTLINE_2D = "outline-2d";
    private static final String FOG_2D = "fog-2d";
    private static final String FOG_OF_WAR_2D = "fog-of-war-2d";
    private static final String PARTICLES_2D = "particles-2d";
    private static final String TILE_MAP = "tile-map";
    private static final String MODEL = "model";
    private static final String MODEL_SKINNING = "model-skinning";
    private static final String OUTLINE_3D = "outline-3d";
    private static final String FOG_3D = "fog-3d";
    private static final String FOG_OF_WAR_3D = "fog-of-war-3d";
    private static final String SKYBOX_3D = "skybox-3d";
    private static final String BILLBOARD_3D = "billboard-3d";
    private static final String PARTICLES_3D = "particles-3d";
    private static final String POINT_LIGHT_3D = "point-light-3d";
    private static final String SPOT_LIGHT_3D = "spot-light-3d";
    private static final String SHADOW_MAP_3D = "shadow-map-3d";
    private static final String CASCADE_SHADOW_MAP_3D = "cascade-shadow-map-3d";
    private static final String CAMERA_CONTROLLERS = "camera-controllers";
    private static final String READBACK = "readback";
    private static final String STORAGE_RUNTIME = "storage-runtime";
    private static final String SHADER_RUNTIME = "shader-runtime";
    private static final String SHADER_SCENE = "shader-scene";
    private static final String UI = "ui";
    public static final String SELECTOR_NAME = "selector";
    public static final String AUTO_TEST_NAME = "auto";
    public static final String DEFAULT_TEST_NAME = UI;
    private static final TestDescriptor[] TESTS = {
            descriptor(TRIANGLE, "Triangle", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new TriangleTest(exitAfterFrames);
                }
            }),
            descriptor(SQUARE, "Square", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SquareTest(exitAfterFrames);
                }
            }),
            descriptor(CIRCLE, "Circle", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new CircleTest(exitAfterFrames);
                }
            }),
            descriptor(TEXTURE, "Texture", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new TextureTest(exitAfterFrames);
                }
            }),
            descriptor(SPRITE, "Sprite batch", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SpriteBatchTest(exitAfterFrames);
                }
            }),
            descriptor(OUTLINE_2D, "Outline 2D", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Outline2DTest(exitAfterFrames);
                }
            }),
            descriptor(FOG_2D, "Fog 2D", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Fog2DTest(exitAfterFrames);
                }
            }),
            descriptor(FOG_OF_WAR_2D, "Fog of war 2D", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new FogOfWar2DTest(exitAfterFrames);
                }
            }),
            descriptor(PARTICLES_2D, "Particles 2D", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Particles2DTest(exitAfterFrames);
                }
            }),
            descriptor(TILE_MAP, "Tile map", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new TileMapRuntimeTest(exitAfterFrames);
                }
            }),
            descriptor(MODEL, "Model batch", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ModelBatchTest(exitAfterFrames);
                }
            }),
            descriptor(MODEL_SKINNING, "Model skinning", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SkinnedModelBatchTest(exitAfterFrames);
                }
            }),
            descriptor(OUTLINE_3D, "Outline 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Outline3DTest(exitAfterFrames);
                }
            }),
            descriptor(FOG_3D, "Fog 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Fog3DTest(exitAfterFrames);
                }
            }),
            descriptor(FOG_OF_WAR_3D, "Fog of war 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new FogOfWar3DTest(exitAfterFrames);
                }
            }),
            descriptor(SKYBOX_3D, "Skybox 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Skybox3DTest(exitAfterFrames);
                }
            }),
            descriptor(BILLBOARD_3D, "Billboard 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Billboard3DTest(exitAfterFrames);
                }
            }),
            descriptor(PARTICLES_3D, "Particles 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Particles3DTest(exitAfterFrames);
                }
            }),
            descriptor(POINT_LIGHT_3D, "Point light 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new PointLight3DTest(exitAfterFrames);
                }
            }),
            descriptor(SPOT_LIGHT_3D, "Spot light 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SpotLight3DTest(exitAfterFrames);
                }
            }),
            descriptor(SHADOW_MAP_3D, "Shadow map 3D", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShadowMap3DTest(exitAfterFrames);
                }
            }),
            descriptor(CASCADE_SHADOW_MAP_3D, "Cascade shadow map 3D", "Graphics 3D", 1280, 720, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShadowMap3DTest(exitAfterFrames, true);
                }
            }),
            descriptor(CAMERA_CONTROLLERS, "Camera controllers", "Graphics 3D", 1280, 720, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new CameraControllersShowcaseTest(exitAfterFrames);
                }
            }),
            descriptor(READBACK, "Readback", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ReadbackTest(exitAfterFrames);
                }
            }),
            descriptor(STORAGE_RUNTIME, "Storage runtime", "Runtime", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new StorageRuntimeTest(exitAfterFrames);
                }
            }),
            descriptor(SHADER_RUNTIME, "Shader runtime", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShaderRuntimeTest(exitAfterFrames);
                }
            }),
            descriptor(SHADER_SCENE, "Shader scene", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShaderSceneTest(exitAfterFrames);
                }
            }),
            descriptor(UI, "UI kit", "UI", 1440, 1000, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new UiKitTest(exitAfterFrames);
                }
            })
    };

    private TestSelector() {
    }

    /**
     * Creates a value.
     *
     * @param name the name
     * @param exitAfterFrames the exit after frames
     * @return the created value
     */
    public static ApplicationListener create(String name, long exitAfterFrames) {
        TestDescriptor descriptor = descriptor(name);
        if (descriptor != null) {
            return descriptor.create(exitAfterFrames);
        }
        throw new FdxException("Unknown test '" + name + "'. Available tests: " + availableTests());
    }

    /**
     * Returns the descriptors.
     *
     * @return the descriptors
     */
    public static TestDescriptor[] descriptors() {
        return TESTS.clone();
    }

    /**
     * Runs the descriptor step.
     *
     * @param name the name
     * @return the descriptor
     */
    public static TestDescriptor descriptor(String name) {
        String testName = normalize(name);
        for (int i = 0; i < TESTS.length; i++) {
            if (TESTS[i].name.equals(testName)) {
                return TESTS[i];
            }
        }
        return null;
    }

    /**
     * Runs the contains step.
     *
     * @param name the name
     * @return true if contains succeeds or is active; false otherwise
     */
    public static boolean contains(String name) {
        return descriptor(name) != null;
    }

    /**
     * Returns the test names.
     *
     * @return the test names
     */
    public static String[] testNames() {
        String[] names = new String[TESTS.length];
        for (int i = 0; i < TESTS.length; i++) {
            names[i] = TESTS[i].name;
        }
        return names;
    }

    /**
     * Returns the available tests.
     *
     * @return the available tests
     */
    public static String availableTests() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < TESTS.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(TESTS[i].name);
        }
        return builder.toString();
    }

    /**
     * Runs the default width step.
     *
     * @param name the name
     * @return the default width
     */
    public static int defaultWidth(String name) {
        TestDescriptor descriptor = descriptor(name);
        return descriptor != null ? descriptor.defaultWidth() : 640;
    }

    /**
     * Runs the default height step.
     *
     * @param name the name
     * @return the default height
     */
    public static int defaultHeight(String name) {
        TestDescriptor descriptor = descriptor(name);
        return descriptor != null ? descriptor.defaultHeight() : 480;
    }

    /**
     * Runs the normalize step.
     *
     * @param name the name
     * @return the normalize
     */
    public static String normalize(String name) {
        if (name == null || name.trim().length() == 0) {
            return DEFAULT_TEST_NAME;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static TestDescriptor descriptor(String name, String displayName, String category, int defaultWidth,
            int defaultHeight, TestFactory factory) {
        return new TestDescriptor(name, displayName, category, defaultWidth, defaultHeight, factory);
    }
}
