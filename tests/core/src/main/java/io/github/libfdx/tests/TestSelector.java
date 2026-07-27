package io.github.libfdx.tests;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.tests.graphics.Billboard3DTest;
import io.github.libfdx.tests.graphics.CameraControllersShowcaseTest;
import io.github.libfdx.tests.graphics.CircleTest;
import io.github.libfdx.tests.graphics.ComputeBufferTest;
import io.github.libfdx.tests.graphics.DepthPreserveTest;
import io.github.libfdx.tests.graphics.DynamicTextureTest;
import io.github.libfdx.tests.graphics.Fog2DTest;
import io.github.libfdx.tests.graphics.Fog3DTest;
import io.github.libfdx.tests.graphics.FogOfWar2DTest;
import io.github.libfdx.tests.graphics.FogOfWar3DTest;
import io.github.libfdx.tests.graphics.InstancingBasicTest;
import io.github.libfdx.tests.graphics.MeshBasicTest;
import io.github.libfdx.tests.graphics.ModelBatchTest;
import io.github.libfdx.tests.graphics.Outline2DTest;
import io.github.libfdx.tests.graphics.Outline3DTest;
import io.github.libfdx.tests.graphics.Particles2DTest;
import io.github.libfdx.tests.graphics.Particles3DTest;
import io.github.libfdx.tests.graphics.PointLight3DTest;
import io.github.libfdx.tests.graphics.ReadbackTest;
import io.github.libfdx.tests.graphics.RecordedResourceRewriteTest;
import io.github.libfdx.tests.graphics.RenderTargetChainTest;
import io.github.libfdx.tests.graphics.RenderTargetCompatibilityTest;
import io.github.libfdx.tests.graphics.ScissorViewportTest;
import io.github.libfdx.tests.graphics.ShadowMap3DTest;
import io.github.libfdx.tests.graphics.ShaderRuntimeTest;
import io.github.libfdx.tests.graphics.ShaderGraphProgramTest;
import io.github.libfdx.tests.graphics.ShaderGraphComputeTest;
import io.github.libfdx.tests.graphics.ShaderGraphEditorVisualTest;
import io.github.libfdx.tests.graphics.ShaderGraphTechniqueTest;
import io.github.libfdx.tests.graphics.ShaderSceneTest;
import io.github.libfdx.tests.graphics.Skybox3DTest;
import io.github.libfdx.tests.graphics.SkinnedModelBatchTest;
import io.github.libfdx.tests.graphics.SpotLight3DTest;
import io.github.libfdx.tests.graphics.SquareTest;
import io.github.libfdx.tests.graphics.SpriteBatchStressTest;
import io.github.libfdx.tests.graphics.SpriteBatchTest;
import io.github.libfdx.tests.graphics.TextureTest;
import io.github.libfdx.tests.graphics.TileMapRuntimeTest;
import io.github.libfdx.tests.graphics.TriangleTest;
import io.github.libfdx.tests.graphics.UiCustomSurfaceVisualTest;
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
        private final GraphicsFeature[] requiredFeatures;

        private TestDescriptor(String name, String displayName, String category, int defaultWidth, int defaultHeight,
                TestFactory factory, GraphicsFeature[] requiredFeatures) {
            this.name = name;
            this.displayName = displayName;
            this.category = category;
            this.defaultWidth = defaultWidth;
            this.defaultHeight = defaultHeight;
            this.factory = factory;
            this.requiredFeatures = requiredFeatures.clone();
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

        /**
         * Returns whether the supplied graphics capabilities can run this test.
         *
         * @param capabilities the graphics capabilities
         * @return true when every required feature is supported
         */
        public boolean supports(GraphicsCapabilities capabilities) {
            if (capabilities == null) {
                return false;
            }
            for (int i = 0; i < requiredFeatures.length; i++) {
                if (!capabilities.supports(requiredFeatures[i])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns a comma-separated description of unsupported required features.
         *
         * @param capabilities the graphics capabilities
         * @return unsupported feature names, or an empty string
         */
        public String unsupportedFeatures(GraphicsCapabilities capabilities) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < requiredFeatures.length; i++) {
                GraphicsFeature feature = requiredFeatures[i];
                if (capabilities != null && capabilities.supports(feature)) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(feature.name());
            }
            return builder.toString();
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
    private static final String COMPUTE_BUFFER = "compute-buffer";
    private static final String STORAGE_RUNTIME = "storage-runtime";
    private static final String SHADER_RUNTIME = "shader-runtime";
    private static final String SHADER_GRAPH_PROGRAM = "shader-graph-program";
    private static final String SHADER_GRAPH_SPRITE = "shader-graph-sprite";
    private static final String SHADER_GRAPH_COMPUTE = "shader-graph-compute";
    private static final String SHADER_GRAPH_TECHNIQUE = "shader-graph-technique";
    private static final String SHADER_SCENE = "shader-scene";
    private static final String MESH_BASIC = "mesh-basic";
    private static final String INSTANCING_BASIC = "instancing-basic";
    private static final String SCISSOR_VIEWPORT = "scissor-viewport";
    private static final String RENDER_TARGET_CHAIN = "render-target-chain";
    private static final String RENDER_TARGET_COMPATIBILITY = "render-target-compatibility";
    private static final String RECORDED_RESOURCE_REWRITE = "recorded-resource-rewrite";
    private static final String DYNAMIC_TEXTURE = "dynamic-texture";
    private static final String DEPTH_PRESERVE = "depth-preserve";
    private static final String SPRITE_BATCH_STRESS = "sprite-batch-stress";
    private static final String UI = "ui";
    private static final String UI_CUSTOM_SURFACE = "ui-custom-surface";
    private static final String SHADER_GRAPH_EDITOR = "shader-graph-editor";
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
            descriptor(SHADER_GRAPH_SPRITE, "Shader graph sprite batch",
                    "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SpriteBatchTest(exitAfterFrames, true);
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
            descriptor(COMPUTE_BUFFER, "Compute buffer", "Graphics", 640, 480,
                    new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new ComputeBufferTest(exitAfterFrames);
                        }
                    }, GraphicsFeature.COMPUTE),
            descriptor(RENDER_TARGET_COMPATIBILITY, "Render target compatibility",
                     "Graphics", 640, 480, new TestFactory() {
                         @Override
                         public ApplicationListener create(long exitAfterFrames) {
                             return new RenderTargetCompatibilityTest(exitAfterFrames);
                         }
                     }, GraphicsFeature.MULTIPLE_COLOR_ATTACHMENTS,
                     GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS,
                     GraphicsFeature.MULTISAMPLE,
                     GraphicsFeature.RESOLVE_ATTACHMENTS,
                     GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE),
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
            descriptor(SHADER_GRAPH_PROGRAM, "Shader graph program",
                    "Graphics", 640, 480, new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new ShaderGraphProgramTest(exitAfterFrames);
                        }
                    }),
            descriptor(SHADER_GRAPH_COMPUTE, "Shader graph compute",
                    "Graphics", 640, 480, new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new ShaderGraphComputeTest(exitAfterFrames);
                        }
                    }, GraphicsFeature.COMPUTE,
                    GraphicsFeature.STORAGE_BUFFERS,
                    GraphicsFeature.STORAGE_TEXTURES,
                    GraphicsFeature.ATOMICS),
            descriptor(SHADER_GRAPH_TECHNIQUE, "Shader graph technique",
                    "Graphics", 640, 480, new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new ShaderGraphTechniqueTest(exitAfterFrames);
                        }
                    }, GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS,
                    GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE),
            descriptor(SHADER_SCENE, "Shader scene", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShaderSceneTest(exitAfterFrames);
                }
            }),
            descriptor(MESH_BASIC, "Mesh basic", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new MeshBasicTest(exitAfterFrames);
                }
            }),
            descriptor(INSTANCING_BASIC, "Instancing basic", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new InstancingBasicTest(exitAfterFrames);
                }
            }),
            descriptor(SCISSOR_VIEWPORT, "Scissor viewport", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ScissorViewportTest(exitAfterFrames);
                }
            }),
            descriptor(RENDER_TARGET_CHAIN, "Render target chain", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new RenderTargetChainTest(exitAfterFrames);
                }
            }),
            descriptor(RECORDED_RESOURCE_REWRITE, "Recorded resource rewrite", "Graphics", 640, 480,
                    new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new RecordedResourceRewriteTest(exitAfterFrames);
                        }
                    }),
            descriptor(DYNAMIC_TEXTURE, "Dynamic texture", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new DynamicTextureTest(exitAfterFrames);
                }
            }),
            descriptor(DEPTH_PRESERVE, "Depth preserve", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new DepthPreserveTest(exitAfterFrames);
                }
            }),
            descriptor(SPRITE_BATCH_STRESS, "Sprite batch stress", "Graphics", 1280, 720, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SpriteBatchStressTest(exitAfterFrames);
                }
            }),
            descriptor(UI, "UI kit", "UI", 1440, 1000, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new UiKitTest(exitAfterFrames);
                }
            }),
            descriptor(UI_CUSTOM_SURFACE, "UI custom surface", "UI", 960, 600, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new UiCustomSurfaceVisualTest(exitAfterFrames);
                }
            }),
            descriptor(SHADER_GRAPH_EDITOR, "Shader graph editor", "UI", 1440, 900, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShaderGraphEditorVisualTest(exitAfterFrames);
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
            int defaultHeight, TestFactory factory, GraphicsFeature... requiredFeatures) {
        return new TestDescriptor(name, displayName, category, defaultWidth, defaultHeight, factory,
                requiredFeatures);
    }
}
