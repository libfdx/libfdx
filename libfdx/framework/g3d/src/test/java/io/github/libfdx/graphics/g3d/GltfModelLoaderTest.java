package io.github.libfdx.graphics.g3d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.assets.AssetHandle;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class GltfModelLoaderTest {
    private static final float EPSILON = 0.0001f;
    private final Matrix4 matrixOut = new Matrix4();

    @Test void multiplePrimitivesYieldBetweenUploadsAndCleanUpCancellationAndFailure() {
        for (int outcome = 0; outcome < 3; outcome++) {
            JsonValue root = new JsonReader().parse(skinnedGltf());
            JsonValue primitives = root.require("meshes").require(0).require("primitives");
            String primitive = primitives.require(0).toJson();
            for (int i = 1; i < 6; i++) primitives.add(new JsonReader().parse(primitive));
            Map<String,FdxFuture<byte[]>> reads = Map.of("many.gltf", FdxFuture.completed(root.toJson().getBytes(StandardCharsets.UTF_8)));
            FileSystem files = (FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(), new Class<?>[]{FileSystem.class},
                    (proxy, method, args) -> file((String)args[0], reads, new HashMap<>()));
            var graphics = new FakeGraphicsContext();
            var manager = new DefaultAssetManager(files);
            G3DAssetLoaders.register(manager, graphics);
            try {
                var handle = manager.load(AssetDescriptor.of("many.gltf", Model.class));
                int previousBuffers = 0;
                for (int step = 0; step < 1000 && !handle.future().isDone(); step++) {
                    manager.update(1, Long.MAX_VALUE);
                    int buffers = graphics.device.buffers.size();
                    assertTrue(buffers - previousBuffers <= 1, "A finalization step uploaded multiple primitives");
                    previousBuffers = buffers;
                    if (buffers == 2 && outcome == 1) manager.unload("many.gltf");
                    if (buffers == 2 && outcome == 2) graphics.device.failBufferWrite = true;
                }
                if (outcome == 0) assertEquals(6, handle.future().get().nodes().get(0).parts().size());
                else {
                    assertTrue(handle.future().isFailed());
                    assertTrue(graphics.device.buffers.stream().allMatch(FakeBuffer::isDisposed));
                }
            } finally { manager.dispose(); }
            assertTrue(graphics.device.buffers.stream().allMatch(FakeBuffer::isDisposed));
        }
    }

    @Test void injectedImageAndMipmapJobsGateGpuCreationAndRespectFailureAndCancellation() {
        for (int outcome = 0; outcome < 3; outcome++) {
            JsonValue root = new JsonReader().parse(skinnedGltf());
            root.put("images", JsonValue.array().add(JsonValue.object().put("uri", "data:image/png;base64,AA==")));
            root.put("samplers", JsonValue.array().add(JsonValue.object().put("minFilter", 9987)));
            root.put("textures", JsonValue.array().add(JsonValue.object().put("source", 0).put("sampler", 0)));
            root.put("materials", JsonValue.array().add(JsonValue.object().put("pbrMetallicRoughness",
                    JsonValue.object().put("baseColorTexture", JsonValue.object().put("index", 0)))));
            root.require("meshes").require(0).require("primitives").require(0).put("material", 0);
            Map<String,FdxFuture<byte[]>> reads = Map.of("worker.gltf", FdxFuture.completed(root.toJson().getBytes(StandardCharsets.UTF_8)));
            FileSystem files = (FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(),new Class<?>[]{FileSystem.class},
                    (proxy,method,args) -> file((String)args[0],reads,new HashMap<>()));
            var manager = new DefaultAssetManager(files);
            var graphics = new FakeGraphicsContext();
            FdxFuture<ImageData> decoded = FdxFuture.pending();
            FdxFuture<ByteBuffer[]> prepared = FdxFuture.pending();
            int[] calls = {0};
            G3DAssetLoaders.register(manager, graphics, (path,bytes) -> decoded, (source,width,height,srgb,alpha) -> {
                calls[0]++; assertEquals(2,width); assertEquals(2,height); assertTrue(srgb); assertFalse(alpha);
                return prepared;
            });
            try {
                var handle = manager.load(AssetDescriptor.of("worker.gltf",Model.class));
                manager.update(); assertFalse(handle.future().isDone());
                ImageData image = new ImageData(2,2,ByteBuffer.allocateDirect(16));
                decoded.complete(image); manager.update();
                assertEquals(1,calls[0]); assertEquals(0,graphics.device.texturesCreated);
                assertTrue(graphics.device.buffers.isEmpty()); assertFalse(handle.future().isDone());
                if (outcome == 2) manager.dispose();
                if (outcome == 1) prepared.completeExceptionally(new FdxException("worker rejected image"));
                else prepared.complete(io.github.libfdx.graphics.TextureMipmaps.rgba8(image.rgba(),2,2,true,false));
                if (outcome != 2) manager.update();
                if (outcome == 0) { assertNotNull(handle.future().get()); assertEquals(1,graphics.device.texturesCreated); }
                else { assertTrue(handle.future().isFailed()); assertEquals(0,graphics.device.texturesCreated); assertTrue(graphics.device.buffers.isEmpty()); }
            } finally { manager.dispose(); }
        }
    }

    @Test void largePrimitiveCompletesThroughBudgetedUpdatesWithoutLosingTriangles() {
        JsonValue root=new JsonReader().parse(skinnedGltf());
        int triangles=5000;
        ByteBuffer indices=ByteBuffer.allocate(triangles*6).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<triangles;i++)indices.putShort((short)0).putShort((short)1).putShort((short)2);
        int buffer=root.require("buffers").arrayValues().size();
        root.require("buffers").add(JsonValue.object().put("byteLength",indices.capacity())
                .put("uri","data:application/octet-stream;base64,"+Base64.getEncoder().encodeToString(indices.array())));
        int view=root.require("bufferViews").arrayValues().size();
        root.require("bufferViews").add(JsonValue.object().put("buffer",buffer).put("byteLength",indices.capacity()));
        int accessor=root.require("accessors").arrayValues().size();
        root.require("accessors").add(JsonValue.object().put("bufferView",view).put("componentType",5123)
                .put("count",triangles*3).put("type","SCALAR"));
        root.require("meshes").require(0).require("primitives").require(0).put("indices",accessor);
        Map<String,FdxFuture<byte[]>> reads=Map.of("large.gltf",FdxFuture.completed(root.toJson().getBytes(StandardCharsets.UTF_8)));
        Map<String,Integer> counts=new HashMap<>();
        FileSystem files=(FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(),new Class<?>[]{FileSystem.class},
                (proxy,method,args)->file((String)args[0],reads,counts));
        DefaultAssetManager manager=new DefaultAssetManager(files);
        G3DAssetLoaders.register(manager,new FakeGraphicsContext());
        try {
            AssetHandle<Model> handle=manager.load(AssetDescriptor.of("large.gltf",Model.class));
            int frames=0;
            while(!handle.future().isDone() && frames++<1000) {
                manager.update(1,Long.MAX_VALUE);assertTrue(manager.lastUpdateTaskCount()<=1);
            }
            Mesh mesh=handle.future().get().nodes().get(0).parts().get(0).meshPart().mesh();
            assertEquals(triangles*3,mesh.vertexCount());assertTrue(frames>triangles/256);
            float[] positions=mesh.sourcePositions();
            for(int i=9;i<positions.length;i++)assertEquals(positions[i%9],positions[i]);
        } finally {manager.dispose();}
    }

    @Test
    void pendingWorkerPreparationLeavesFrameUpdatesFreeAndUploadsOnOwnerThread() throws Exception {
        var tasks = new java.util.ArrayDeque<Runnable>();
        AssetExecutor executor = new AssetExecutor() {
            private boolean disposed;
            @Override public boolean submit(Runnable task) { tasks.add(task); return true; }
            @Override public void dispose() { disposed = true; }
            @Override public boolean isDisposed() { return disposed; }
        };
        Map<String, FdxFuture<byte[]>> reads = Map.of("model.gltf",
                FdxFuture.completed(skinnedGltf().getBytes(StandardCharsets.UTF_8)));
        Map<String, Integer> counts = new HashMap<>();
        FileSystem files = (FileSystem) Proxy.newProxyInstance(FileSystem.class.getClassLoader(),
                new Class<?>[] {FileSystem.class}, (proxy, method, args) -> file((String) args[0], reads, counts));
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        DefaultAssetManager manager = new DefaultAssetManager(files, executor);
        G3DAssetLoaders.register(manager, graphics);
        try {
            AssetHandle<Model> model = manager.load(AssetDescriptor.of("model.gltf", Model.class));
            manager.update(1, Long.MAX_VALUE);
            assertFalse(tasks.isEmpty());
            // Leave the worker stalled: the application must remain free to advance frames.
            for (int frame = 0; frame < 20; frame++) {
                assertFalse(manager.update(4, 1_000_000L));
                assertTrue(graphics.device.buffers.isEmpty());
            }
            for (int step = 0; step < 100 && !model.future().isDone(); step++) {
                Runnable work = tasks.poll();
                if (work != null) {
                    Thread worker = new Thread(work, "gltf-test-worker");
                    worker.start();
                    worker.join(5000);
                    assertFalse(worker.isAlive(), "CPU preparation did not return");
                }
                manager.update(1, Long.MAX_VALUE);
            }
            Model loaded = model.future().get();
            assertNotNull(loaded);
            assertFalse(graphics.device.buffers.isEmpty());
            assertEquals(3, loaded.nodes().get(0).parts().get(0).meshPart().mesh().vertexCount());
        } finally {
            manager.dispose();
            executor.dispose();
        }
        assertTrue(graphics.device.buffers.stream().allMatch(FakeBuffer::isDisposed));
    }

    @Test void malformedTextureCoordinateAndTangentAttributesFailBeforeGpuAllocation() {
        for (int invalid = 0; invalid < 4; invalid++) {
            JsonValue root = new JsonReader().parse(skinnedGltf());
            JsonValue primitive = root.require("meshes").require(0).require("primitives").require(0);
            JsonValue attributes = primitive.require("attributes");
            if (invalid == 0) attributes.put("TEXCOORD_0", floatAccessor(root, "VEC2", 0,0, 1,0));
            if (invalid == 1) attributes.put("TANGENT", floatAccessor(root, "VEC4", 1,0,0,0, 1,0,0,1, 1,0,0,1));
            if (invalid == 2) attributes.put("NORMAL", floatAccessor(root, "VEC3", 0,0,0, 0,0,1, 0,0,1));
            if (invalid == 3) {
                root.put("images", JsonValue.array().add(JsonValue.object().put("uri", "data:image/png;base64,AA==")));
                root.put("textures", JsonValue.array().add(JsonValue.object().put("source", 0)));
                root.put("materials", JsonValue.array().add(JsonValue.object().put("emissiveTexture",
                        JsonValue.object().put("index", 0).put("texCoord", 1))));
                primitive.put("material", 0);
            }
            FakeGraphicsContext graphics = new FakeGraphicsContext();
            assertThrows(FdxException.class, () -> new GltfModelLoader(graphics).loadModelBytes("invalid.gltf", root.toJson().getBytes(StandardCharsets.UTF_8)));
            assertEquals(0, graphics.device.buffers.size());
            assertEquals(0, graphics.device.texturesCreated);
        }
    }

    @Test void importsSecondaryUvsAndSuppliedMirroredTangentsWithoutChangingCompactMeshes() {
        JsonValue root = new JsonReader().parse(skinnedGltf());
        JsonValue attributes = root.require("meshes").require(0).require("primitives").require(0).require("attributes");
        attributes.put("TEXCOORD_1", floatAccessor(root, "VEC2", .2f,.3f, .4f,.5f, .6f,.7f));
        attributes.put("TANGENT", floatAccessor(root, "VEC4", 1,0,0,-1, 1,0,0,-1, 1,0,0,-1));
        Model model = new GltfModelLoader(new FakeGraphicsContext()).loadModelBytes("tangents.gltf", root.toJson().getBytes(StandardCharsets.UTF_8));
        try {
            Mesh mesh = model.nodes().get(0).parts().get(0).meshPart().mesh();
            assertSame(Mesh.PBR_TEXTURED_SKINNED_LAYOUT, mesh.vertexLayout());
            assertArrayEquals(new float[] {.2f,.3f, .4f,.5f, .6f,.7f}, mesh.sourceTexCoords1());
            assertArrayEquals(new float[] {1,0,0,-1, 1,0,0,-1, 1,0,0,-1}, mesh.sourceTangents());
        } finally { model.dispose(); }
    }

    @Test void requiredExtensionsAndVersionFailBeforeGpuAllocationWhileUnknownOptionalExtensionsAreIgnored() {
        var graphics=new FakeGraphicsContext();var loader=new GltfModelLoader(graphics);
        for(String field:new String[]{
                "\"extensionsUsed\":[\"VENDOR_unknown\"],\"extensionsRequired\":[\"VENDOR_unknown\"],",
                "\"extensionsRequired\":[\"KHR_materials_unlit\"],",
                "\"extensionsUsed\":[\"KHR_materials_unlit\",\"KHR_materials_unlit\"],"}) {
            String invalid=skinnedGltf().replace("\"asset\":",field+"\"asset\":");
            assertThrows(FdxException.class,()->loader.loadModelBytes("required.gltf",invalid.getBytes(StandardCharsets.UTF_8)));
        }
        assertThrows(FdxException.class,()->loader.loadModelBytes("version.gltf",skinnedGltf().replace("\"version\":\"2.0\"","\"version\":\"1.0\"").getBytes(StandardCharsets.UTF_8)));
        assertEquals(0,graphics.device.buffers.size());assertEquals(0,graphics.device.texturesCreated);
        String supported=skinnedGltf().replace("\"asset\":","\"extensionsUsed\":[\"KHR_materials_unlit\",\"VENDOR_optional\"],\"extensionsRequired\":[\"KHR_materials_unlit\"],\"asset\":");
        Model model=loader.loadModelBytes("optional.gltf",supported.getBytes(StandardCharsets.UTF_8));model.dispose();
    }

    @Test void sparsePositionAccessorWithNoBaseProducesTheSameSkinnedPrimitive() {
        var root=new io.github.libfdx.json.JsonReader().parse(skinnedGltf());
        root.require("buffers").add(io.github.libfdx.json.JsonValue.object().put("byteLength",3).put("uri","data:application/octet-stream;base64,AAEC"));
        int indexView=root.require("bufferViews").arrayValues().size();
        root.require("bufferViews").add(io.github.libfdx.json.JsonValue.object().put("buffer",1).put("byteLength",3));
        var replacement=io.github.libfdx.json.JsonValue.object().put("count",3).put("componentType",5126).put("type","VEC3")
                .put("sparse",io.github.libfdx.json.JsonValue.object().put("count",3)
                        .put("indices",io.github.libfdx.json.JsonValue.object().put("bufferView",indexView).put("componentType",5121))
                        .put("values",io.github.libfdx.json.JsonValue.object().put("bufferView",0)));
        var accessors=io.github.libfdx.json.JsonValue.array();accessors.add(replacement);
        for(int i=1;i<root.require("accessors").arrayValues().size();i++)accessors.add(root.require("accessors").require(i));
        root.put("accessors",accessors);
        var loader=new GltfModelLoader(new FakeGraphicsContext());
        Model dense=loader.loadModelBytes("dense.gltf",skinnedGltf().getBytes(StandardCharsets.UTF_8));
        Model sparse=loader.loadModelBytes("sparse.gltf",root.toJson().getBytes(StandardCharsets.UTF_8));
        try {
            assertArrayEquals(dense.nodes().get(0).parts().get(0).meshPart().mesh().sourcePositions(),
                    sparse.nodes().get(0).parts().get(0).meshPart().mesh().sourcePositions());
        } finally {dense.dispose();sparse.dispose();}
    }

    @Test
    void delayedRootAndSharedExternalBuffersAndImagesFinishBeforeModelUpload() {
        Map<String, FdxFuture<byte[]>> reads = new HashMap<>();
        Map<String, Integer> readCounts = new HashMap<>();
        for (String path : List.of("models/A.gltf", "models/B.gltf", "models/shared.bin", "models/shared.png")) {
            reads.put(path, FdxFuture.pending());
        }
        FileSystem files = (FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(),
                new Class<?>[] {FileSystem.class}, (proxy, method, args) -> file((String)args[0], reads, readCounts));
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        DefaultAssetManager manager = new DefaultAssetManager(files);
        G3DAssetLoaders.register(manager, graphics);
        manager.registerLoader(ImageData.class, new AssetLoader<ImageData>() {
            @Override public Class<ImageData> type() { return ImageData.class; }
            @Override public FdxFuture<ImageData> load(AssetLoadContext context, AssetDescriptor<ImageData> descriptor) {
                FdxFuture<ImageData> result = FdxFuture.pending();
                context.readBytes(context.files().internal(descriptor.path())).onSuccess(bytes ->
                        result.complete(new ImageData(1, 1, ByteBuffer.allocateDirect(4))))
                        .onFailure(result::completeExceptionally);
                return result;
            }
        });
        try {
            AssetHandle<Model> a = manager.load(AssetDescriptor.of("models/A.gltf", Model.class));
            AssetHandle<Model> b = manager.load(AssetDescriptor.of("models/B.gltf", Model.class));
            assertFalse(manager.update());
            assertEquals(0, graphics.device.buffers.size());
            String documentText = skinnedGltf().replace("data:application/octet-stream;base64," + binaryGltfData().base64,
                    "shared.bin").replace("\"asset\":", "\"images\":[{\"uri\":\"shared.png\"}],"
                            + "\"textures\":[{\"source\":0}],\"asset\":");
            JsonValue documentRoot = new JsonReader().parse(documentText);
            documentRoot.put("materials", JsonValue.array().add(JsonValue.object()
                    .put("emissiveTexture", JsonValue.object().put("index", 0))));
            JsonValue primitive = documentRoot.require("meshes").require(0).require("primitives").require(0);
            primitive.put("material", 0);
            primitive.require("attributes").put("TEXCOORD_0", floatAccessor(documentRoot, "VEC2", 0,0, 1,0, 0,1));
            String document = documentRoot.toJson();
            reads.get("models/A.gltf").complete(document.getBytes(StandardCharsets.UTF_8));
            reads.get("models/B.gltf").complete(document.getBytes(StandardCharsets.UTF_8));
            assertFalse(manager.update());
            assertEquals(1, readCounts.get("models/shared.bin"));
            assertEquals(1, readCounts.get("models/shared.png"));
            reads.get("models/shared.bin").complete(Base64.getDecoder().decode(binaryGltfData().base64));
            assertFalse(manager.update());
            assertFalse(a.isLoaded());
            assertFalse(b.isLoaded());
            assertEquals(0, graphics.device.buffers.size());
            assertEquals(0, graphics.device.texturesCreated);
            reads.get("models/shared.png").complete(new byte[0]);
            for (int i = 0; i < 100 && !manager.update(1, Long.MAX_VALUE); i++) {
                assertEquals(1, manager.lastUpdateTaskCount());
            }
            assertDoesNotThrow(() -> a.future().get());
            assertTrue(a.isLoaded());
            assertTrue(b.isLoaded());
            assertEquals(2, graphics.device.texturesCreated);
            manager.unload("models/A.gltf");
            assertNotNull(manager.find("models/shared.bin", byte[].class));
            assertNotNull(manager.find("models/shared.png", ImageData.class));
            assertEquals(1, graphics.device.texturesDisposed);
            manager.unload("models/B.gltf");
            assertNull(manager.find("models/shared.bin", byte[].class));
            assertNull(manager.find("models/shared.png", ImageData.class));
            assertEquals(2, graphics.device.texturesDisposed);
            graphics.device.failTextureWrite = true;
            AssetHandle<Model> failed = manager.load(AssetDescriptor.of("models/A.gltf", Model.class));
            assertTrue(manager.update());
            assertTrue(failed.future().isFailed());
            assertEquals(3, graphics.device.texturesCreated);
            assertEquals(3, graphics.device.texturesDisposed);
            // Cancel between texture upload and model publication: partial GPU resources are owned.
            manager.unload("models/A.gltf");
            graphics.device.failTextureWrite = false;
            AssetHandle<Model> cancelled = manager.load(AssetDescriptor.of("models/A.gltf", Model.class));
            for (int step = 0; step < 100 && graphics.device.texturesCreated < 4; step++) {
                manager.update(1, Long.MAX_VALUE);
            }
            assertEquals(4, graphics.device.texturesCreated);
            assertFalse(cancelled.isLoaded());
            manager.unload("models/A.gltf");
            assertTrue(cancelled.future().isFailed());
            assertEquals(4, graphics.device.texturesDisposed);
        } finally {
            manager.dispose();
        }
    }

    @Test
    void failedMeshUploadReleasesItsPartiallyCreatedBuffer() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        graphics.device.failBufferWrite = true;
        assertThrows(FdxException.class, () -> new GltfModelLoader(graphics)
                .loadModelBytes("embedded.gltf", skinnedGltf().getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, graphics.device.buffers.size());
        assertTrue(graphics.device.buffers.get(0).isDisposed());
    }

    @Test
    void invalidLaterPrimitiveFailsBeforeAnyMeshUpload() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        JsonValue root = new JsonReader().parse(skinnedGltf());
        // Validate every primitive before uploading even the first mesh.
        root.require("meshes").require(0).require("primitives").add(JsonValue.object().put("mode", 1));
        String invalid = root.toJson();
        assertThrows(RuntimeException.class, () -> new GltfModelLoader(graphics)
                .loadModelBytes("invalid.gltf", invalid.getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, graphics.device.buffers.size());
        assertEquals(0, graphics.device.texturesCreated);
    }

    @Test void invalidSkinsAndHierarchyFailBeforeGpuCreation() {
        List<java.util.function.Consumer<JsonValue>> corruptions = List.of(
                root -> root.require("nodes").require(1).put("children", JsonValue.array().add(0)),
                root -> root.require("nodes").require(0).put("children", JsonValue.array().add(.5)),
                root -> root.require("skins").require(0).put("joints", JsonValue.array().add(9)),
                root -> root.require("skins").require(0).put("inverseBindMatrices", floatAccessor(root,"VEC4",1,0,0,1)),
                root -> root.require("skins").require(0).put("inverseBindMatrices",
                        floatAccessor(root,"MAT4",1,0,0,.1f,0,1,0,0,0,0,1,0,0,0,0,1)),
                root -> root.require("meshes").require(0).require("primitives").require(0).require("attributes")
                        .put("WEIGHTS_0", floatAccessor(root,"VEC4", -1,2,0,0, 1,0,0,0, 1,0,0,0)),
                root -> root.require("meshes").require(0).require("primitives").require(0).require("attributes")
                        .put("WEIGHTS_0", floatAccessor(root,"VEC4", 0,0,0,0, 1,0,0,0, 1,0,0,0)),
                root -> root.require("meshes").require(0).require("primitives").require(0).require("attributes")
                        .put("WEIGHTS_0", floatAccessor(root,"VEC4", .5f,.5f,0,0, 1,0,0,0, 1,0,0,0)),
                root -> {
                    JsonValue buffer=root.require("buffers").require(0); String uri=buffer.require("uri").stringValue();
                    byte[] bytes=java.util.Base64.getDecoder().decode(uri.substring(uri.indexOf(',')+1));
                    bytes[96]=1;
                    buffer.put("uri","data:application/octet-stream;base64,"+java.util.Base64.getEncoder().encodeToString(bytes));
                });
        for (var corrupt : corruptions) {
            JsonValue root = new JsonReader().parse(skinnedGltf()); corrupt.accept(root);
            FakeGraphicsContext graphics = new FakeGraphicsContext();
            assertThrows(FdxException.class, () -> new GltfModelLoader(graphics).loadModelBytes("bad-skin.gltf",root.toJson().getBytes(StandardCharsets.UTF_8)));
            assertEquals(0, graphics.device.buffers.size()); assertEquals(0, graphics.device.texturesCreated);
        }
    }

    @Test void normalizesWeightsSeparatelyFromSharedColorAccessorAndAcceptsExtraInverseBinds() {
        JsonValue root = new JsonReader().parse(skinnedGltf());
        JsonValue attributes = root.require("meshes").require(0).require("primitives").require(0).require("attributes");
        int weights = floatAccessor(root,"VEC4", .5f,0,0,0, .5f,0,0,0, .5f,0,0,0);
        attributes.put("WEIGHTS_0",weights).put("COLOR_0",weights);
        root.require("skins").require(0).put("inverseBindMatrices",floatAccessor(root,"MAT4",
                1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1, 1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1));
        Model model = new GltfModelLoader(new FakeGraphicsContext()).loadModelBytes("weights.gltf", root.toJson().getBytes(StandardCharsets.UTF_8));
        try {
            Mesh mesh = model.nodes().get(0).parts().get(0).meshPart().mesh();
            assertEquals(1, mesh.sourceWeights()[0]); assertEquals(.5f, mesh.sourceColors()[0]);
            assertEquals(1, model.skins().get(0).skeleton().bones().size());
        } finally { model.dispose(); }
    }

    @Test void importsForestWithoutScenesAndDisambiguatesCollidingNodeNames() {
        JsonValue root = new JsonReader().parse(skinnedGltf());
        root.put("scenes", JsonValue.array());
        // Rebuild the root without its optional scene selector.
        String source = root.toJson().replace("\"scene\":0,", "").replace(",\"scene\":0", "");
        root = new JsonReader().parse(source);
        root.require("nodes").require(0).put("name", "joint").put("children", JsonValue.array().add(1).add(2));
        root.require("nodes").require(1).put("name", "joint-2");
        root.require("nodes").add(JsonValue.object().put("name", " joint "));
        Model model = new GltfModelLoader(new FakeGraphicsContext()).loadModelBytes("forest.gltf",root.toJson().getBytes(StandardCharsets.UTF_8));
        try {
            DefaultModelInstance instance = new DefaultModelInstance(model);
            assertTrue(instance.hasNode("joint")); assertTrue(instance.hasNode("joint-2")); assertTrue(instance.hasNode("joint-3"));
            new AnimationController(instance).play(model.animations().get(0),false).time(.5f);
            assertTranslation(instance.copyNodeTransform("joint-2",matrixOut),0,2,0);
        } finally { model.dispose(); }
    }

    @Test void importsMixedStepAndCubicTracksWithoutResamplingTheirIndependentTimelines() {
        JsonValue root = new JsonReader().parse(skinnedGltf());
        int times = floatAccessor(root, "SCALAR", 1, 3);
        int values = floatAccessor(root, "VEC3", 0,0,0, 0,1,0, 0,2,0, 0,6,0, 0,9,0, 0,0,0);
        int scaleTimes = floatAccessor(root, "SCALAR", 0, 2, 4);
        int scales = floatAccessor(root, "VEC3", 1,1,1, 2,3,4, 1,1,1);
        JsonValue animation = root.require("animations").require(0);
        animation.put("samplers", JsonValue.array()
                .add(JsonValue.object().put("input", times).put("output", values).put("interpolation", "CUBICSPLINE"))
                .add(JsonValue.object().put("input", scaleTimes).put("output", scales).put("interpolation", "STEP")));
        animation.require("channels").add(JsonValue.object().put("sampler", 1)
                .put("target", JsonValue.object().put("node", 1).put("path", "scale")));
        Model model = new GltfModelLoader(new FakeGraphicsContext()).loadModelBytes("mixed.gltf", root.toJson().getBytes(StandardCharsets.UTF_8));
        try {
            AnimationClip clip = model.animations().get(0);
            assertEquals(4, clip.durationSeconds());
            var channels = clip.nodeTransformChannels();
            assertEquals(1, channels.length);
            assertEquals(2, channels[0].translationSampler().keyCount());
            assertEquals(3, channels[0].scaleSampler().keyCount());
            var instance = new DefaultModelInstance(model);
            var controller = new AnimationController(instance).play(clip, false);
            controller.time(1.5f);
            assertTranslation(instance.copyNodeTransform("joint", matrixOut), 0, 2.25f, 0);
            assertEquals(1, matrixOut.values()[0]);
            controller.time(2);
            assertTranslation(instance.copyNodeTransform("joint", matrixOut), 0, 4, 0);
            assertEquals(2, matrixOut.values()[0]);
            assertEquals(3, matrixOut.values()[5]);
            assertEquals(4, matrixOut.values()[10]);
        } finally { model.dispose(); }
    }

    @Test void malformedAnimationDeclarationsFailDuringCpuPreparation() {
        List<java.util.function.Consumer<JsonValue>> corruptions = List.of(
                root -> root.require("animations").require(0).require("samplers").require(0).put("input", 99),
                root -> root.require("animations").require(0).require("samplers").require(0).put("interpolation", "CUBICSPLINE"),
                root -> root.require("animations").require(0).require("channels").require(0).put("sampler", -1),
                root -> root.require("animations").require(0).require("channels").require(0).require("target").put("node", 9),
                root -> root.require("animations").require(0).require("channels").add(root.require("animations").require(0).require("channels").require(0)),
                root -> root.require("accessors").require(6).put("componentType", 5123),
                root -> root.require("animations").require(0).require("samplers").require(0).put("input", floatAccessor(root, "SCALAR", 1, 1)),
                root -> root.require("nodes").require(1).put("matrix", JsonValue.array()),
                root -> root.require("nodes").require(1).put("rotation", JsonValue.array().add(0).add(0).add(0).add(0)));
        for (var corrupt : corruptions) {
            JsonValue root = new JsonReader().parse(skinnedGltf());
            corrupt.accept(root);
            FakeGraphicsContext graphics = new FakeGraphicsContext();
            assertThrows(FdxException.class, () -> new GltfModelLoader(graphics).loadModelBytes("bad-animation.gltf", root.toJson().getBytes(StandardCharsets.UTF_8)));
            assertEquals(0, graphics.device.buffers.size());
            assertEquals(0, graphics.device.texturesCreated);
        }
    }

    private static int floatAccessor(JsonValue root, String type, float... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) buffer.putFloat(value);
        int bufferIndex = root.require("buffers").arrayValues().size();
        int viewIndex = root.require("bufferViews").arrayValues().size();
        int accessorIndex = root.require("accessors").arrayValues().size();
        root.require("buffers").add(JsonValue.object().put("byteLength", buffer.capacity())
                .put("uri", "data:application/octet-stream;base64,"+Base64.getEncoder().encodeToString(buffer.array())));
        root.require("bufferViews").add(JsonValue.object().put("buffer", bufferIndex).put("byteLength", buffer.capacity()));
        root.require("accessors").add(JsonValue.object().put("bufferView", viewIndex).put("componentType", 5126)
                .put("count", values.length/switch(type) { case "SCALAR" -> 1; case "VEC2" -> 2;
                    case "VEC3" -> 3; case "VEC4" -> 4; case "MAT4" -> 16; default -> throw new IllegalArgumentException(type); })
                .put("type", type));
        return accessorIndex;
    }

    private static FileHandle file(String path, Map<String, FdxFuture<byte[]>> reads, Map<String, Integer> counts) {
        return (FileHandle)Proxy.newProxyInstance(FileHandle.class.getClassLoader(), new Class<?>[] {FileHandle.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "path" -> path;
                    case "parent" -> file(path.substring(0, path.lastIndexOf('/')), reads, counts);
                    case "child" -> file(path + "/" + args[0], reads, counts);
                    case "readBytes" -> { counts.merge(path, 1, Integer::sum); yield reads.get(path); }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void loadsHierarchySkinWeightsAndLinearAnimation() {
        Model model = new GltfModelLoader(new FakeGraphicsContext())
                .loadModelBytes("embedded.gltf", skinnedGltf().getBytes(StandardCharsets.UTF_8));

        assertEquals(1, model.nodes().size());
        ModelNode root = model.nodes().get(0);
        assertEquals("meshNode", root.id());
        assertTranslation(root.localTransform(), 0.0f, 0.0f, 0.0f);
        assertEquals(1, root.children().size());
        assertEquals("joint", root.children().get(0).id());
        assertEquals(1, root.parts().size());

        ModelNodePart part = root.parts().get(0);
        assertNotNull(part.skin());
        assertEquals(12, part.joints().length);
        assertEquals(12, part.weights().length);
        assertEquals(0, part.joints()[0]);
        assertEquals(1.0f, part.weights()[0], EPSILON);
        assertEquals(Mesh.PBR_SKINNED_LAYOUT, part.meshPart().mesh().vertexLayout());
        assertNotNull(part.meshPart().mesh().sourcePositions());
        assertEquals(12, part.meshPart().mesh().sourceJoints().length);
        assertEquals(12, part.meshPart().mesh().sourceWeights().length);
        assertEquals(9, part.meshPart().mesh().sourcePositions().length);

        assertEquals(1, model.skins().size());
        Skin skin = model.skins().get(0);
        assertEquals("skin0", skin.id());
        assertEquals(1, skin.skeleton().bones().size());
        Bone bone = skin.skeleton().bones().get(0);
        assertEquals("joint", bone.id());
        assertEquals(-1, bone.parentIndex());
        assertTranslation(bone.inverseBindTransform(), 0.0f, -1.0f, 0.0f);

        assertEquals(1, model.animations().size());
        DefaultModelInstance instance = new DefaultModelInstance(model);
        new AnimationController(instance).play(model.animations().get(0), false).time(0.5f);
        assertTranslation(instance.copyNodeTransform("joint", matrixOut),
                0.0f, 2.0f, 0.0f);

        SkinningPalette palette = new SkinningPalette(skin).update(instance);
        assertTranslation(palette.copyBoneMatrix(0, matrixOut),
                0.0f, 1.0f, 0.0f);
    }

    private static String skinnedGltf() {
        BinaryGltfData data = binaryGltfData();
        return "{"
                + "\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + data.base64 + "\",\"byteLength\":"
                + data.byteLength + "}],"
                + "\"bufferViews\":["
                + "{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":36,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":72,\"byteLength\":24},"
                + "{\"buffer\":0,\"byteOffset\":96,\"byteLength\":24},"
                + "{\"buffer\":0,\"byteOffset\":120,\"byteLength\":48},"
                + "{\"buffer\":0,\"byteOffset\":168,\"byteLength\":64},"
                + "{\"buffer\":0,\"byteOffset\":232,\"byteLength\":8},"
                + "{\"buffer\":0,\"byteOffset\":240,\"byteLength\":24}"
                + "],"
                + "\"accessors\":["
                + "{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":2,\"componentType\":5126,\"count\":3,\"type\":\"VEC2\"},"
                + "{\"bufferView\":3,\"componentType\":5123,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":4,\"componentType\":5126,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":5,\"componentType\":5126,\"count\":1,\"type\":\"MAT4\"},"
                + "{\"bufferView\":6,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":7,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"}"
                + "],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1,"
                + "\"TEXCOORD_0\":2,\"JOINTS_0\":3,\"WEIGHTS_0\":4}}]}],"
                + "\"nodes\":["
                + "{\"name\":\"meshNode\",\"mesh\":0,\"skin\":0,\"children\":[1]},"
                + "{\"name\":\"joint\",\"translation\":[0,1,0]}"
                + "],"
                + "\"skins\":[{\"name\":\"skin0\",\"joints\":[1],\"inverseBindMatrices\":5}],"
                + "\"animations\":[{\"name\":\"moveJoint\",\"samplers\":[{\"input\":6,\"output\":7,"
                + "\"interpolation\":\"LINEAR\"}],\"channels\":[{\"sampler\":0,"
                + "\"target\":{\"node\":1,\"path\":\"translation\"}}]}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";
    }

    private static BinaryGltfData binaryGltfData() {
        ByteBuffer buffer = ByteBuffer.allocate(264).order(ByteOrder.LITTLE_ENDIAN);
        putFloats(buffer,
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);
        putFloats(buffer,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f);
        putFloats(buffer,
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f);
        putShorts(buffer,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0);
        putFloats(buffer,
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f);
        putFloats(buffer, new Matrix4().setToTranslation(0.0f, -1.0f, 0.0f).values());
        putFloats(buffer, 0.0f, 1.0f);
        putFloats(buffer,
                0.0f, 1.0f, 0.0f,
                0.0f, 3.0f, 0.0f);
        byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        return new BinaryGltfData(Base64.getEncoder().encodeToString(bytes), bytes.length);
    }

    private static void putFloats(ByteBuffer buffer, float... values) {
        for (int i = 0; i < values.length; i++) {
            buffer.putFloat(values[i]);
        }
    }

    private static void putShorts(ByteBuffer buffer, int... values) {
        for (int i = 0; i < values.length; i++) {
            buffer.putShort((short)values[i]);
        }
    }

    private static void assertTranslation(Matrix4 matrix, float x, float y, float z) {
        float[] values = matrix.values();
        assertEquals(x, values[12], EPSILON);
        assertEquals(y, values[13], EPSILON);
        assertEquals(z, values[14], EPSILON);
    }

    private static final class BinaryGltfData {
        private final String base64;
        private final int byteLength;

        BinaryGltfData(String base64, int byteLength) {
            this.base64 = base64;
            this.byteLength = byteLength;
        }
    }

    private static final class FakeGraphicsContext implements GraphicsContext {
        private final Thread applicationThread = Thread.currentThread();
        private static final ProviderId PROVIDER_ID = ProviderId.of("test");
        private final FakeGraphicsDevice device = new FakeGraphicsDevice();

        @Override
        public FakeGraphicsDevice device() {
            assertSame(applicationThread, Thread.currentThread());
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
            assertSame(applicationThread, Thread.currentThread());
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeGraphicsDevice implements GraphicsDevice {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-device");
        private final Thread applicationThread = Thread.currentThread();
        private final List<FakeBuffer> buffers = new ArrayList<>();
        private int texturesCreated;
        private int texturesDisposed;
        private boolean failBufferWrite;
        private boolean failTextureWrite;

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            assertSame(applicationThread, Thread.currentThread());
            FakeBuffer buffer = new FakeBuffer(descriptor.size(), descriptor.usage());
            buffers.add(buffer);
            return buffer;
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            assertSame(applicationThread, Thread.currentThread());
            if (failBufferWrite) { throw new FdxException("buffer upload failed"); }
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            assertSame(applicationThread, Thread.currentThread());
            texturesCreated++;
            return (Texture)Proxy.newProxyInstance(Texture.class.getClassLoader(), new Class<?>[] {Texture.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "width" -> descriptor.width();
                        case "height" -> descriptor.height();
                        case "mipLevelCount" -> descriptor.mipLevelCount();
                        case "dispose" -> { texturesDisposed++; yield null; }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
            assertSame(applicationThread, Thread.currentThread());
            if (failTextureWrite) { throw new FdxException("texture upload failed"); }
        }

        @Override
        public void writeTextureMipLevels(Texture texture, ByteBuffer... levels) {
            assertEquals(texture.mipLevelCount(), levels.length);
            writeTexture(texture, levels[0]);
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            throw new UnsupportedOperationException();
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
}
