package io.github.libfdx.testsupport;

import io.github.libfdx.tests.graphics.AnimationIntegrationTest;
import io.github.libfdx.tests.graphics.AudioPlaybackTest;
import io.github.libfdx.tests.graphics.CascadedShadowMap3DTest;
import io.github.libfdx.tests.graphics.EffectsTest;
import io.github.libfdx.tests.graphics.FileStreamingTest;
import io.github.libfdx.tests.graphics.FrustumCullingTest;
import io.github.libfdx.tests.graphics.GltfAnimationTest;
import io.github.libfdx.tests.graphics.GltfMaterialsTest;
import io.github.libfdx.tests.graphics.IblLightingTest;
import io.github.libfdx.tests.graphics.InputActionsTest;
import io.github.libfdx.tests.graphics.MusicStreamingTest;
import io.github.libfdx.tests.graphics.OffscreenCompositionTest;
import io.github.libfdx.tests.graphics.PixelColorTest;
import io.github.libfdx.tests.graphics.SceneShowcaseTest;
import io.github.libfdx.tests.graphics.ShadowMap3DTest;
import io.github.libfdx.tests.graphics.ShadowQualityTest;
import io.github.libfdx.tests.graphics.SpriteBatchTest;
import io.github.libfdx.tests.graphics.TextureMipsTest;
import io.github.libfdx.tests.graphics.TexturePackerTest;

import io.github.libfdx.tests.StorageTest;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.tests.graphics.Billboard3DTest;
import io.github.libfdx.tests.graphics.AssetLoadingTest;
import io.github.libfdx.tests.graphics.GltfLoadingTest;
import io.github.libfdx.tests.graphics.TiledMapTest;
import io.github.libfdx.tests.graphics.CameraControllersShowcaseTest;
import io.github.libfdx.tests.graphics.CircleTest;
import io.github.libfdx.tests.graphics.ComputeBufferTest;
import io.github.libfdx.tests.graphics.DepthPreserveTest;
import io.github.libfdx.tests.graphics.DynamicTextureTest;
import io.github.libfdx.tests.graphics.FarWorldCameraTest;
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
import io.github.libfdx.tests.graphics.ShadingModels3DTest;
import io.github.libfdx.tests.graphics.ShaderTest;
import io.github.libfdx.tests.graphics.MultipleShadersTest;
import io.github.libfdx.tests.graphics.ShaderPreloadingTest;
import io.github.libfdx.tests.graphics.ShadowShaderPreparationTest;
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
import io.github.libfdx.tests.graphics.TextureTest;
import io.github.libfdx.tests.graphics.Transparency3DTest;
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
        private final Class<? extends ApplicationListener> testClass;
        private final String category;
        private final String description;
        private final int defaultWidth;
        private final int defaultHeight;
        private final TestFactory factory;
        private final GraphicsFeature[] requiredFeatures;

        private TestDescriptor(Class<? extends ApplicationListener> testClass, String description, String category, int defaultWidth, int defaultHeight,
                TestFactory factory, GraphicsFeature[] requiredFeatures) {
            this.name = testClass.getSimpleName();
            this.testClass = testClass;
            this.category = category;
            this.description = description;
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
         * Returns the executable class name.
         *
         * @return the display name
         */
        public String displayName() {
            return name;
        }

        /** Returns the short purpose shown in the test chooser. */
        public String description() {
            return description;
        }

        /** Returns the test category. */
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

        /** Whether this scenario needs an audio service supplied by its platform launcher. */
        public boolean requiresAudio() {
            return testClass == AudioPlaybackTest.class
                    || testClass == MusicStreamingTest.class;
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

    public static final String SELECTOR_NAME = "selector";
    public static final String AUTO_TEST_NAME = "auto";
    private static final TestDescriptor[] TESTS = {
            descriptor(TriangleTest.class,
                    "Draws a triangle to check basic shape rendering.", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new TriangleTest(exitAfterFrames);
                }
            }),
            descriptor(SquareTest.class,
                    "Draws a square to check basic shape rendering.", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SquareTest(exitAfterFrames);
                }
            }),
            descriptor(CircleTest.class,
                    "Draws a circle to check curved shape rendering.", "Shapes", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new CircleTest(exitAfterFrames);
                }
            }),
            descriptor(TextureTest.class,
                    "Loads and draws a texture to check image rendering.", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new TextureTest(exitAfterFrames);
                }
            }),
            descriptor(AssetLoadingTest.class,
                    "Checks shared asset loading, upload budgets and scoped release.", "Runtime", 640, 480,
                    AssetLoadingTest::new),
            descriptor(GltfLoadingTest.class,
                    "Loads a glTF by path and waits for external buffers and images.", "Runtime", 960, 640,
                    GltfLoadingTest::new),
            descriptor(AudioPlaybackTest.class,
                    "Exercises WAV playback, pitch, panning and sound lifetimes.", "Runtime", 640, 480,
                    AudioPlaybackTest::new),
            descriptor(MusicStreamingTest.class,
                    "Checks streamed music, crossfades, looping and seeking.", "Runtime", 640, 480,
                    MusicStreamingTest::new),
            descriptor(InputActionsTest.class,
                    "Exercises input routing, action bindings and saved rebinding.", "Runtime", 640, 480,
                    InputActionsTest::new),
            descriptor(TiledMapTest.class,
                    "Explores an imported Tiled map to check layers and map rendering.", "Graphics 2D", 1120, 720,
                    TiledMapTest::new),
            descriptor(FileStreamingTest.class,
                    "Checks bounded file reads and asynchronous asset loading.", "Graphics 2D", 640, 480,
                    FileStreamingTest::new),
            descriptor(FrustumCullingTest.class,
                    "Compares culling on and off to check visibility and draw savings.", "Graphics 3D", 1100, 760,
                    FrustumCullingTest::new),
            descriptor(GltfAnimationTest.class,
                    "Compares imported glTF interpolation with reference poses.", "Graphics 3D", 640, 480,
                    GltfAnimationTest::new),
            descriptor(AnimationIntegrationTest.class,
                    "Compares CPU and GPU skinning, animation events and bounds.", "Graphics 3D", 640, 480,
                    AnimationIntegrationTest::new),
            descriptor(GltfMaterialsTest.class,
                    "Compares imported material UVs and normals with baked references.", "Graphics 3D", 640, 480,
                    GltfMaterialsTest::new),
            descriptor(IblLightingTest.class,
                    "Shows HDR environment lighting across metals and rough surfaces.", "Graphics 3D", 640, 480,
                    IblLightingTest::new),
            descriptor(TextureMipsTest.class,
                    "Checks mip levels, filtering and texture rewrites after drawing.", "Graphics", 640, 480,
                    TextureMipsTest::new),
            descriptor(OffscreenCompositionTest.class,
                    "Checks layered render targets, transparency, depth and resizing.", "Graphics", 640, 480,
                    OffscreenCompositionTest::new),
            descriptor(EffectsTest.class,
                    "Compares lighting, bloom and HDR at different effect budgets.", "Graphics", 960, 640,
                    EffectsTest::new),
            descriptor(TexturePackerTest.class,
                    "Checks atlas trimming, sprite appearance and shared-texture batching.", "Graphics 2D", 640, 480,
                    TexturePackerTest::new),
            descriptor(PixelColorTest.class,
                    "Compares pixel colors and color encodings, including sRGB output.", "Graphics 2D", 650, 490,
                    PixelColorTest::new),
            descriptor(SpriteBatchTest.class,
                    "Renders a layered harbor to check tinted texture-region sprites.", "Graphics 2D", 640, 480,
                    SpriteBatchTest::new),
            descriptor(Outline2DTest.class,
                    "Shows sprite outlines to check 2D edge highlighting.", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Outline2DTest(exitAfterFrames);
                }
            }),
            descriptor(Fog2DTest.class,
                    "Shows atmospheric mist over a courtyard without exploration memory.", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Fog2DTest(exitAfterFrames);
                }
            }),
            descriptor(FogOfWar2DTest.class,
                    "Checks persistent exploration and fog coverage over 2D scenery.", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new FogOfWar2DTest(exitAfterFrames);
                }
            }),
            descriptor(Particles2DTest.class,
                    "Renders density-field particles to check volumetric effects in 2D.", "Graphics 2D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Particles2DTest(exitAfterFrames);
                }
            }),
            descriptor(ModelBatchTest.class,
                    "Renders a lit model to check 3D materials and model batching.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ModelBatchTest(exitAfterFrames);
                }
            }),
            descriptor(SkinnedModelBatchTest.class,
                    "Animates a skinned mesh to check deformation and model rendering.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SkinnedModelBatchTest(exitAfterFrames);
                }
            }),
            descriptor(Outline3DTest.class,
                    "Checks screen-space outlines around 3D scene edges.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Outline3DTest(exitAfterFrames);
                }
            }),
            descriptor(Fog3DTest.class,
                    "Shows distance fog while navigating solid 3D scenery.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Fog3DTest(exitAfterFrames);
                }
            }),
            descriptor(FogOfWar3DTest.class,
                    "Checks persistent exploration and fog coverage over 3D surfaces.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new FogOfWar3DTest(exitAfterFrames);
                }
            }),
            descriptor(Skybox3DTest.class,
                    "Renders a procedural skybox to check the scene background.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Skybox3DTest(exitAfterFrames);
                }
            }),
            descriptor(Billboard3DTest.class,
                    "Checks camera-facing billboards in a 3D scene.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Billboard3DTest(exitAfterFrames);
                }
            }),
            descriptor(Particles3DTest.class,
                    "Renders density-field particles to check volumetric effects in 3D.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Particles3DTest(exitAfterFrames);
                }
            }),
            descriptor(ShadingModels3DTest.class,
                    "Compares PBR, reduced lighting influence and unlit materials.",
                    "Graphics 3D", 800, 600, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShadingModels3DTest(exitAfterFrames);
                }
            }),
            descriptor(Transparency3DTest.class,
                    "Checks overlapping glass and opaque geometry for draw-order errors.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new Transparency3DTest(exitAfterFrames);
                }
            }, GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS, GraphicsFeature.ALPHA_BLEND_CONTROL),
            descriptor(PointLight3DTest.class,
                    "Shows point lighting to check illumination from a local source.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new PointLight3DTest(exitAfterFrames);
                }
            }),
            descriptor(SpotLight3DTest.class,
                    "Shows spotlight cone falloff on curved and flat surfaces.", "Graphics 3D", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SpotLight3DTest(exitAfterFrames);
                }
            }),
            descriptor(SceneShowcaseTest.class,
                    "Combines lighting, materials and motion in an interactive gallery.", "Graphics 3D", 960, 640,
                    SceneShowcaseTest::new),
            descriptor(ShadowQualityTest.class,
                    "Compares redrawn and cached shadows to check reuse and quality.", "Graphics 3D", 1280, 800,
                    ShadowQualityTest::new),
            descriptor(ShadowMap3DTest.class,
                    "Checks directional shadow maps on a 3D scene.", "Graphics 3D", 640, 480,
                    ShadowMap3DTest::new),
            descriptor(CascadedShadowMap3DTest.class,
                    "Checks cascaded directional shadows across viewing distances.", "Graphics 3D", 1280, 720,
                    CascadedShadowMap3DTest::new),
            descriptor(CameraControllersShowcaseTest.class,
                    "Compares reusable camera controls in one interactive scene.", "Graphics 3D", 1280, 720, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new CameraControllersShowcaseTest(exitAfterFrames);
                }
            }),
            descriptor(FarWorldCameraTest.class,
                    "Exposes floating-point movement precision far from the origin.", "Graphics 3D", 900, 650, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new FarWorldCameraTest(exitAfterFrames);
                }
            }),
            descriptor(ReadbackTest.class,
                    "Checks GPU pixel readback and captured image contents.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ReadbackTest(exitAfterFrames);
                }
            }),
            descriptor(ComputeBufferTest.class,
                    "Executes compute work and verifies the resulting buffer data.", "Graphics", 640, 480,
                    new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new ComputeBufferTest(exitAfterFrames);
                        }
                    }, GraphicsFeature.COMPUTE),
            descriptor(RenderTargetCompatibilityTest.class,
                    "Checks attachment, multisample, resolve and pipeline compatibility.",
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
            descriptor(StorageTest.class,
                    "Checks the runtime storage service and persisted values.", "Runtime", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new StorageTest(exitAfterFrames);
                }
            }),
            descriptor(ShaderTest.class,
                    "Checks runtime shader creation and rendering.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShaderTest(exitAfterFrames);
                }
            }),
            descriptor(MultipleShadersTest.class,
                    "Creates, times and draws 128 distinct runtime shader programs.", "Graphics", 960, 720, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new MultipleShadersTest(exitAfterFrames);
                }
            }),
            descriptor(ShaderPreloadingTest.class,
                    "Preloads HUD/world shaders, streams content, and captures a runtime material miss.",
                    "Graphics", 960, 640, ShaderPreloadingTest::new),
            descriptor(ShadowShaderPreparationTest.class,
                    "Shares async forward/shadow readiness while a new caster is prepared.",
                    "Graphics", 960, 640, ShadowShaderPreparationTest::new),
            descriptor(ShaderGraphProgramTest.class,
                    "Renders graph-generated shader stages with color and depth outputs.",
                    "Graphics", 640, 480, new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new ShaderGraphProgramTest(exitAfterFrames);
                        }
                    }),
            descriptor(ShaderGraphComputeTest.class,
                    "Checks graph-generated compute, atomics and storage textures.",
                    "Graphics", 640, 480, new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new ShaderGraphComputeTest(exitAfterFrames);
                        }
                    }, GraphicsFeature.COMPUTE,
                    GraphicsFeature.STORAGE_BUFFERS,
                    GraphicsFeature.STORAGE_TEXTURES,
                    GraphicsFeature.ATOMICS),
            descriptor(ShaderGraphTechniqueTest.class,
                    "Checks multi-pass shader graphs, fallback, caching and reload.",
                    "Graphics", 640, 480, new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new ShaderGraphTechniqueTest(exitAfterFrames);
                        }
                    }, GraphicsFeature.EXPLICIT_DEPTH_STENCIL_ATTACHMENTS,
                    GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE),
            descriptor(ShaderSceneTest.class,
                    "Renders a WGSL ray-marched scene with shadows and tiled ground.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShaderSceneTest(exitAfterFrames);
                }
            }),
            descriptor(MeshBasicTest.class,
                    "Checks raw mesh rendering across graphics providers.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new MeshBasicTest(exitAfterFrames);
                }
            }),
            descriptor(InstancingBasicTest.class,
                    "Checks per-instance vertex data when drawing repeated geometry.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new InstancingBasicTest(exitAfterFrames);
                }
            }),
            descriptor(ScissorViewportTest.class,
                    "Checks moving clipping and viewport bounds for rendering leaks.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ScissorViewportTest(exitAfterFrames);
                }
            }),
            descriptor(RenderTargetChainTest.class,
                    "Checks chained offscreen passes across graphics providers.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new RenderTargetChainTest(exitAfterFrames);
                }
            }),
            descriptor(RecordedResourceRewriteTest.class,
                    "Checks that later writes preserve already-recorded draw resources.", "Graphics", 640, 480,
                    new TestFactory() {
                        @Override
                        public ApplicationListener create(long exitAfterFrames) {
                            return new RecordedResourceRewriteTest(exitAfterFrames);
                        }
                    }),
            descriptor(DynamicTextureTest.class,
                    "Checks per-frame texture uploads across graphics providers.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new DynamicTextureTest(exitAfterFrames);
                }
            }),
            descriptor(DepthPreserveTest.class,
                    "Checks that depth survives between rendering passes.", "Graphics", 640, 480, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new DepthPreserveTest(exitAfterFrames);
                }
            }),
            descriptor(SpriteBatchStressTest.class,
                    "Stresses sprite batching with a repeatable rendering workload.", "Graphics", 1280, 720, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new SpriteBatchStressTest(exitAfterFrames);
                }
            }),
            descriptor(UiKitTest.class,
                    "Exercises UI widgets, layout, text editing and input behavior.", "UI", 1440, 1000, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new UiKitTest(exitAfterFrames);
                }
            }),
            descriptor(UiCustomSurfaceVisualTest.class,
                    "Checks custom UI surfaces, clipping, lines and paths.", "UI", 960, 600, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new UiCustomSurfaceVisualTest(exitAfterFrames);
                }
            }),
            descriptor(ShaderGraphEditorVisualTest.class,
                    "Exercises the visual shader graph editor and its rendering.", "UI", 1440, 900, new TestFactory() {
                @Override
                public ApplicationListener create(long exitAfterFrames) {
                    return new ShaderGraphEditorVisualTest(exitAfterFrames);
                }
            })
    };

    /** Returns the default executable test class name. */
    public static String defaultTestName() {
        return UiKitTest.class.getSimpleName();
    }

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
            return defaultTestName();
        }
        String trimmed = name.trim();
        for (TestDescriptor test : TESTS) {
            if (test.name.equalsIgnoreCase(trimmed)) return test.name;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static TestDescriptor descriptor(Class<? extends ApplicationListener> testClass, String description, String category, int defaultWidth,
            int defaultHeight, TestFactory factory, GraphicsFeature... requiredFeatures) {
        return new TestDescriptor(testClass, description, category, defaultWidth, defaultHeight, factory,
                requiredFeatures);
    }
}
