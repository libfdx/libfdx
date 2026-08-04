package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class AnimationControllerTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void controllerAppliesInterpolatedNodeTransform() {
        DefaultModelInstance instance = new DefaultModelInstance(model());
        AnimationClip clip = moveArmClip();

        new AnimationController(instance).play(clip, false).time(1.0f);

        assertTranslation(instance.nodeTransform("arm"), 0.0f, 2.0f, 0.0f);
        assertTranslation(instance.nodeWorldTransform("arm"), 1.0f, 2.0f, 0.0f);
    }

    @Test
    void loopingWrapsAndNonLoopingClamps() {
        DefaultModelInstance loopingInstance = new DefaultModelInstance(model());
        AnimationController looping = new AnimationController(loopingInstance).play(moveArmClip(), true);

        looping.time(2.5f);

        assertEquals(0.5f, looping.timeSeconds(), EPSILON);
        assertTranslation(loopingInstance.nodeTransform("arm"), 0.0f, 1.0f, 0.0f);

        DefaultModelInstance clampedInstance = new DefaultModelInstance(model());
        AnimationController clamped = new AnimationController(clampedInstance).play(moveArmClip(), false);

        clamped.time(9.0f);

        assertEquals(2.0f, clamped.timeSeconds(), EPSILON);
        assertTranslation(clampedInstance.nodeTransform("arm"), 0.0f, 4.0f, 0.0f);
    }

    @Test
    void instanceNodeTransformsDoNotMutateSharedModel() {
        DefaultModel model = model();
        DefaultModelInstance animated = new DefaultModelInstance(model);
        DefaultModelInstance untouched = new DefaultModelInstance(model);

        new AnimationController(animated).play(moveArmClip(), false).time(1.5f);

        assertTranslation(animated.nodeTransform("arm"), 0.0f, 3.0f, 0.0f);
        assertTranslation(untouched.nodeTransform("arm"), 0.0f, 1.0f, 0.0f);
        assertTranslation(model.nodes().get(0).children().get(0).localTransform(), 0.0f, 1.0f, 0.0f);
    }

    @Test
    void skinningPaletteUsesAnimatedModelSpaceNodeTransforms() {
        DefaultModelInstance instance = new DefaultModelInstance(model())
                .transform(Matrix4.translation(10.0f, 0.0f, 0.0f));
        SkinningPalette palette = new SkinningPalette(skin());

        palette.update(instance);

        assertTranslation(palette.boneMatrix(0), 0.0f, 0.0f, 0.0f);

        new AnimationController(instance).play(moveArmClip(), false).time(1.5f);
        palette.update(instance);

        assertTranslation(palette.boneMatrix(0), 0.0f, 2.0f, 0.0f);
        assertTranslation(instance.nodeWorldTransform("arm"), 11.0f, 3.0f, 0.0f);

        float[] values = new float[Matrix4.VALUE_COUNT];
        palette.copyValues(values);

        assertEquals(2.0f, values[13], EPSILON);
    }

    @Test
    void directInstanceTransformMutationUpdatesQueriesAndRenderables() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        DefaultModel model = skinnedModel(graphics);
        try {
            DefaultModelInstance instance = new DefaultModelInstance(model);

            instance.transform().setToTrs(
                    2.0f, 3.0f, 4.0f,
                    0.0f, 0.0f, 0.0f, 1.0f,
                    1.0f, 1.0f, 1.0f);

            assertTranslation(instance.nodeWorldTransform("root"),
                    3.0f, 3.0f, 4.0f);

            instance.transform().setToTrs(
                    5.0f, 6.0f, 7.0f,
                    0.0f, 0.0f, 0.0f, 1.0f,
                    1.0f, 1.0f, 1.0f);
            DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
            instance.collectRenderables(queue);

            assertEquals(2, queue.size());
            assertTranslation(queue.get(0).worldTransform(),
                    6.0f, 6.0f, 7.0f);
            assertTranslation(queue.get(1).worldTransform(),
                    6.0f, 6.0f, 7.0f);
        }
        finally {
            model.dispose();
        }
    }

    @Test
    void skinningPaletteUsesParentedBoneModelTransforms() {
        DefaultModelInstance instance = new DefaultModelInstance(hierarchicalModel());
        AnimationClip clip = new AnimationClip("rotate-shoulder", 1.0f, new AnimationClip.NodeTransformChannel[] {
                AnimationClip.nodeTransform("shoulder",
                        zRotationKeyframe(0.0f, 1.0f, 0.0f),
                        zRotationKeyframe(1.0f, 1.0f, 90.0f))
        });

        new AnimationController(instance).play(clip, false).time(1.0f);

        assertTranslation(instance.nodeModelTransform("wrist"), -1.0f, 1.0f, 0.0f);

        SkinningPalette palette = new SkinningPalette(hierarchicalSkin()).update(instance);
        Vector3 skinnedBindWrist = palette.boneMatrix(1).transformPosition(new Vector3(0.0f, 2.0f, 0.0f));
        assertPosition(skinnedBindWrist, -1.0f, 1.0f, 0.0f);
    }

    @Test
    void cpuSkinningUpdaterWritesSkinnedPbrVertices() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        Mesh mesh = Mesh.positionColor3D(graphics, "skinned",
                new float[] {0.0f, 0.0f, 0.0f},
                new float[] {1.0f, 0.5f, 0.25f, 1.0f},
                new float[] {0.0f, 1.0f, 0.0f},
                new float[] {0.25f, 0.75f},
                new float[] {1.0f, 0.0f, 0.5f},
                new float[] {0.0f, 0.1f, 0.2f},
                BoundingBox.empty());
        DefaultModelInstance instance = new DefaultModelInstance(model());
        new AnimationController(instance).play(moveArmClip(), false).time(1.5f);
        SkinningPalette palette = new SkinningPalette(skin()).update(instance);

        new CpuSkinningMeshUpdater(graphics, mesh,
                new int[] {0, 0, 0, 0},
                new float[] {1.0f, 0.0f, 0.0f, 0.0f}).update(palette);

        float[] written = graphics.device().lastFloats();
        assertEquals(0.0f, written[0], EPSILON);
        assertEquals(2.0f, written[1], EPSILON);
        assertEquals(0.0f, written[2], EPSILON);
        assertEquals(0.0f, written[3], EPSILON);
        assertEquals(1.0f, written[4], EPSILON);
        assertEquals(0.0f, written[5], EPSILON);
        assertEquals(0.25f, written[6], EPSILON);
        assertEquals(0.75f, written[7], EPSILON);
        assertEquals(1.0f, written[8], EPSILON);
        assertEquals(0.5f, written[9], EPSILON);
        assertEquals(0.25f, written[10], EPSILON);
        assertEquals(1.0f, written[11], EPSILON);
    }

    @Test
    void cpuSkinnedModelAnimatorUpdatesAllSkinnedParts() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        DefaultModel model = skinnedModel(graphics);
        DefaultModelInstance instance = new DefaultModelInstance(model);
        CpuSkinnedModelAnimator animator = new CpuSkinnedModelAnimator(graphics, instance);

        assertEquals(1, animator.skinCount());
        assertEquals(2, animator.skinnedPartCount());

        animator.play(moveArmClip(), false);
        graphics.device().clearWrites();
        animator.time(1.5f);

        assertEquals(1.5f, animator.controller().timeSeconds(), EPSILON);
        assertEquals(2, graphics.device().writeCount());
        assertEquals(2.0f, graphics.device().writtenFloats(0)[1], EPSILON);
        assertEquals(4.0f, graphics.device().writtenFloats(1)[1], EPSILON);
    }

    @Test
    void pbrShaderUploadsSkinningPaletteForSkinnedRenderable() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        Skin skin = skin();
        PbrMaterial material = new PbrMaterial("skin-material");
        Mesh mesh = skinnedGpuMesh(graphics, "gpu-skinned");
        ModelNode root = new ModelNode("root").addPart(new ModelNodePart(
                new MeshPart("gpu-part", mesh, null, 0, mesh.vertexCount()), material, skin,
                new int[] {0, 0, 0, 0}, new float[] {1.0f, 0.0f, 0.0f, 0.0f}));
        root.addChild(new ModelNode("arm").localTransform(Matrix4.translation(0.0f, 1.0f, 0.0f)));
        Array<ModelNode> nodes = new Array<ModelNode>();
        nodes.add(root);
        Array<Material> materials = new Array<Material>();
        materials.add(material);
        Array<Skin> skins = new Array<Skin>();
        skins.add(skin);
        Array<Mesh> meshes = new Array<Mesh>();
        meshes.add(mesh);
        DefaultModel model = new DefaultModel(nodes, materials, new Array<AnimationClip>(0), skins, meshes);
        DefaultModelInstance instance = new DefaultModelInstance(model);
        new AnimationController(instance).play(moveArmClip(), false).time(1.5f);
        DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
        instance.collectRenderables(queue);
        Renderable3D renderable = queue.get(0);
        assertNotNull(renderable.skinningPalette());

        PbrShaderProvider provider = new PbrShaderProvider(graphics, new PbrShaderConfig().maxBones(1));
        Shader3D shader = provider.shader(renderable, new RenderContext3D(graphics,
                new Camera().viewport(64.0f, 64.0f).update(), new Environment3D(), null, new FakeRenderPass()));
        FakeRenderPass pass = new FakeRenderPass();
        RenderContext3D context = new RenderContext3D(graphics, new Camera().viewport(64.0f, 64.0f).update(),
                new Environment3D(), null, pass);

        shader.begin(context);
        shader.render(renderable);
        shader.end();

        assertEquals(Mesh.PBR_SKINNED_LAYOUT, graphics.device().lastPipelineDescriptor().vertexLayout());
        assertEquals(1.0f, pass.skinningBoneCount, EPSILON);
        assertNotNull(pass.bone0);
        assertEquals(2.0f, pass.bone0[13], EPSILON);
        assertEquals(1, pass.drawCalls);

        provider.dispose();
    }

    private static AnimationClip moveArmClip() {
        return new AnimationClip("move-arm", 2.0f, new AnimationClip.NodeTransformChannel[] {
                AnimationClip.nodeTransform("arm",
                        AnimationClip.keyframe(0.0f, 0.0f, 0.0f, 0.0f),
                        AnimationClip.keyframe(2.0f, 0.0f, 4.0f, 0.0f))
        });
    }

    private static DefaultModel model() {
        ModelNode root = new ModelNode("root").localTransform(Matrix4.translation(1.0f, 0.0f, 0.0f));
        root.addChild(new ModelNode("arm").localTransform(Matrix4.translation(0.0f, 1.0f, 0.0f)));

        Array<ModelNode> nodes = new Array<ModelNode>();
        nodes.add(root);
        return new DefaultModel(nodes, new Array<Material>(0),
                new Array<AnimationClip>(0), new Array<Mesh>(0));
    }

    private static DefaultModel hierarchicalModel() {
        ModelNode root = new ModelNode("root");
        ModelNode shoulder = new ModelNode("shoulder").localTransform(Matrix4.translation(0.0f, 1.0f, 0.0f));
        ModelNode wrist = new ModelNode("wrist").localTransform(Matrix4.translation(0.0f, 1.0f, 0.0f));
        root.addChild(shoulder);
        shoulder.addChild(wrist);

        Array<ModelNode> nodes = new Array<ModelNode>();
        nodes.add(root);
        Array<Skin> skins = new Array<Skin>();
        skins.add(hierarchicalSkin());
        return new DefaultModel(nodes, new Array<Material>(0),
                new Array<AnimationClip>(0), skins, new Array<Mesh>(0));
    }

    private static DefaultModel skinnedModel(FakeGraphicsContext graphics) {
        Skin skin = skin();
        PbrMaterial material = new PbrMaterial("skin-material");
        Mesh firstMesh = skinnedMesh(graphics, "skinned-a", 0.0f);
        Mesh secondMesh = skinnedMesh(graphics, "skinned-b", 2.0f);
        ModelNode root = new ModelNode("root").localTransform(Matrix4.translation(1.0f, 0.0f, 0.0f))
                .addPart(new ModelNodePart(new MeshPart("part-a", firstMesh, null, 0, firstMesh.vertexCount()),
                        material, skin, new int[] {0, 0, 0, 0}, new float[] {1.0f, 0.0f, 0.0f, 0.0f}))
                .addPart(new ModelNodePart(new MeshPart("part-b", secondMesh, null, 0, secondMesh.vertexCount()),
                        material, skin, new int[] {0, 0, 0, 0}, new float[] {1.0f, 0.0f, 0.0f, 0.0f}));
        root.addChild(new ModelNode("arm").localTransform(Matrix4.translation(0.0f, 1.0f, 0.0f)));

        Array<ModelNode> nodes = new Array<ModelNode>();
        nodes.add(root);
        Array<Material> materials = new Array<Material>();
        materials.add(material);
        Array<Skin> skins = new Array<Skin>();
        skins.add(skin);
        Array<Mesh> meshes = new Array<Mesh>();
        meshes.add(firstMesh);
        meshes.add(secondMesh);
        return new DefaultModel(nodes, materials, new Array<AnimationClip>(0), skins, meshes);
    }

    private static Mesh skinnedMesh(FakeGraphicsContext graphics, String id, float sourceY) {
        return Mesh.positionColor3D(graphics, id,
                new float[] {0.0f, sourceY, 0.0f},
                new float[] {1.0f, 0.5f, 0.25f, 1.0f},
                new float[] {0.0f, 1.0f, 0.0f},
                new float[] {0.25f, 0.75f},
                new float[] {1.0f, 0.0f, 0.5f},
                new float[] {0.0f, 0.1f, 0.2f},
                BoundingBox.empty());
    }

    private static Mesh skinnedGpuMesh(FakeGraphicsContext graphics, String id) {
        return Mesh.positionColor3D(graphics, id,
                new float[] {0.0f, 0.0f, 0.0f},
                new float[] {1.0f, 0.5f, 0.25f, 1.0f},
                null,
                new float[] {0.0f, 1.0f, 0.0f},
                new float[] {0.25f, 0.75f},
                new float[] {1.0f, 0.0f, 0.5f},
                null,
                new float[] {0.0f, 0.1f, 0.2f},
                null,
                new int[] {0, 0, 0, 0},
                new float[] {1.0f, 0.0f, 0.0f, 0.0f},
                BoundingBox.empty(),
                true);
    }

    private static Skin skin() {
        Array<Bone> bones = new Array<Bone>();
        bones.add(new Bone("arm", -1, Matrix4.translation(-1.0f, -1.0f, 0.0f)));
        return new Skin("arm-skin", new Skeleton(bones));
    }

    private static Skin hierarchicalSkin() {
        Array<Bone> bones = new Array<Bone>();
        bones.add(new Bone("shoulder", -1, Matrix4.translation(0.0f, -1.0f, 0.0f)));
        bones.add(new Bone("wrist", 0, Matrix4.translation(0.0f, -2.0f, 0.0f)));
        return new Skin("arm-chain-skin", new Skeleton(bones));
    }

    private static AnimationClip.TransformKeyframe zRotationKeyframe(float timeSeconds, float y, float degrees) {
        float radians = (float)Math.toRadians(degrees);
        float half = radians * 0.5f;
        return AnimationClip.keyframe(timeSeconds, 0.0f, y, 0.0f,
                0.0f, 0.0f, (float)Math.sin(half), (float)Math.cos(half),
                1.0f, 1.0f, 1.0f);
    }

    private static void assertTranslation(Matrix4 matrix, float x, float y, float z) {
        float[] values = matrix.values();
        assertEquals(x, values[12], EPSILON);
        assertEquals(y, values[13], EPSILON);
        assertEquals(z, values[14], EPSILON);
    }

    private static void assertPosition(Vector3 position, float x, float y, float z) {
        assertEquals(x, position.x(), EPSILON);
        assertEquals(y, position.y(), EPSILON);
        assertEquals(z, position.z(), EPSILON);
    }

    private static final class FakeGraphicsContext implements GraphicsContext {
        private static final ProviderId DEFAULT_PROVIDER_ID = ProviderId.of("test");
        private final ProviderId providerId;
        private final FakeGraphicsDevice device = new FakeGraphicsDevice();

        FakeGraphicsContext() {
            this(DEFAULT_PROVIDER_ID);
        }

        FakeGraphicsContext(ProviderId providerId) {
            this.providerId = providerId != null ? providerId : DEFAULT_PROVIDER_ID;
        }

        @Override
        public FakeGraphicsDevice device() {
            return device;
        }

        @Override
        public TextureFormat surfaceFormat() {
            return TextureFormat.RGBA8_UNORM;
        }

        @Override
        public GraphicsFrame currentFrame() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear(float red, float green, float blue, float alpha) {
        }

        @Override
        public ProviderId providerId() {
            return providerId;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeGraphicsDevice implements GraphicsDevice {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-device");
        private final ArrayList<ByteBuffer> writes = new ArrayList<ByteBuffer>();
        private ByteBuffer lastWrite;
        private RenderPipelineDescriptor lastPipelineDescriptor;

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            return new FakeBuffer(descriptor.size(), descriptor.usage());
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            ByteBuffer duplicate = data.duplicate().order(ByteOrder.nativeOrder());
            lastWrite = ByteBuffer.allocateDirect(duplicate.remaining()).order(ByteOrder.nativeOrder());
            lastWrite.put(duplicate);
            lastWrite.flip();
            writes.add(lastWrite);
        }

        float[] lastFloats() {
            FloatBuffer floats = lastWrite.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
            float[] out = new float[floats.remaining()];
            floats.get(out);
            return out;
        }

        float[] writtenFloats(int index) {
            FloatBuffer floats = writes.get(index).duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
            float[] out = new float[floats.remaining()];
            floats.get(out);
            return out;
        }

        int writeCount() {
            return writes.size();
        }

        void clearWrites() {
            writes.clear();
            lastWrite = null;
        }

        RenderPipelineDescriptor lastPipelineDescriptor() {
            return lastPipelineDescriptor;
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            return new FakeTexture(descriptor.width(), descriptor.height(), descriptor.format(), descriptor.usage());
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            return new FakeShaderModule(descriptor.language());
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            lastPipelineDescriptor = descriptor;
            return new FakeRenderPipeline(descriptor);
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeBuffer implements Buffer {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-buffer");
        private final int size;
        private final BufferUsage usage;
        private boolean disposed;

        FakeBuffer(int size, BufferUsage usage) {
            this.size = size;
            this.usage = usage;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public BufferUsage usage() {
            return usage;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeTexture implements Texture {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-texture");
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private boolean disposed;

        FakeTexture(int width, int height, TextureFormat format, TextureUsage usage) {
            this.width = width;
            this.height = height;
            this.format = format;
            this.usage = usage;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public TextureFormat format() {
            return format;
        }

        @Override
        public TextureUsage usage() {
            return usage;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeShaderModule implements ShaderModule {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-shader-module");
        private final ShaderLanguage language;
        private boolean disposed;

        FakeShaderModule(ShaderLanguage language) {
            this.language = language;
        }

        @Override
        public ShaderLanguage language() {
            return language;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeRenderPipeline implements RenderPipeline {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-pipeline");
        private final RenderPipelineDescriptor descriptor;
        private boolean disposed;

        FakeRenderPipeline(RenderPipelineDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        RenderPipelineDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public RenderTargetLayout targetLayout() {
            return descriptor.renderTargetLayout();
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }
    }

    private static final class FakeRenderPass implements RenderPass {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-pass");
        private float skinningBoneCount;
        private float[] bone0;
        private int drawCalls;

        @Override
        public RenderPassCompatibility compatibility() {
            return RenderPassCompatibility.of(
                    RenderTargetLayout.color(
                            TextureFormat.RGBA8_UNORM),
                    64, 64);
        }

        @Override
        public void setPipeline(RenderPipeline pipeline) {
            FakeRenderPipeline fake = pipeline.as();
            assertNotNull(fake.descriptor());
        }

        @Override
        public void setVertexBuffer(Buffer buffer) {
        }

        @Override
        public void setIndexBuffer(Buffer buffer) {
        }

        @Override
        public void setTexture(int slot, Texture texture) {
        }

        @Override
        public void setTextureBinding(int group, int binding, Texture texture) {
        }

        @Override
        public void setTextureSamplerBinding(int group, int binding, Texture texture) {
        }

        @Override
        public void setParameterBlock(int group, int binding, ShaderParameterBlock block) {
            ShaderParameterHandle skinning = block.layout().findHandle("skinningParams");
            if (skinning == null) {
                return;
            }
            ByteBuffer data = block.readOnlyData().order(ByteOrder.nativeOrder());
            skinningBoneCount = data.getFloat(skinning.byteOffsetInt());
            ShaderParameterHandle firstBone = block.layout()
                    .requireArrayElementHandle("boneMatrices", 0);
            bone0 = new float[16];
            int offset = firstBone.byteOffsetInt();
            int stride = firstBone.matrixStrideInt();
            for (int column = 0; column < 4; column++) {
                for (int row = 0; row < 4; row++) {
                    bone0[column * 4 + row] = data.getFloat(
                            offset + column * stride + row * Float.BYTES);
                }
            }
        }

        @Override
        public void setUniform1i(String name, int value) {
        }

        @Override
        public void setUniform1i(ShaderParameterHandle parameter, int value) {
        }

        @Override
        public void setUniform1f(String name, float value) {
        }

        @Override
        public void setUniform1f(ShaderParameterHandle parameter, float value) {
        }

        @Override
        public void setUniform3f(String name, float x, float y, float z) {
        }

        @Override
        public void setUniform3f(ShaderParameterHandle parameter, float x, float y, float z) {
        }

        @Override
        public void setUniform4f(String name, float x, float y, float z, float w) {
            if ("u_skinningParams".equals(name)) {
                skinningBoneCount = x;
            }
        }

        @Override
        public void setUniform4f(ShaderParameterHandle parameter, float x, float y, float z, float w) {
            if ("skinningParams".equals(parameter.path())) {
                skinningBoneCount = x;
            }
        }

        @Override
        public void setUniformMatrix4(String name, float[] values) {
            if ("u_bone0".equals(name)) {
                bone0 = values.clone();
            }
        }

        @Override
        public void setUniformMatrix4(ShaderParameterHandle parameter, float[] values) {
            if ("boneMatrices[0]".equals(parameter.path())) {
                bone0 = values.clone();
            }
        }

        @Override
        public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
            drawCalls++;
        }

        @Override
        public void end() {
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }
    }
}
