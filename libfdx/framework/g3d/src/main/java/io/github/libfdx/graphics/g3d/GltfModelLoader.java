package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.IntMap;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.assets.loaders.ImageDecoder;
import io.github.libfdx.graphics.TextureMipmapPreparer;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Loads gltf model data.
 *
 * @author xpenatan
 */
final class GltfModelLoader implements AssetLoader<Model> {
    private static final int GLB_MAGIC = 0x46546c67;
    private static final int GLB_JSON_CHUNK = 0x4e4f534a;
    private static final int GLB_BIN_CHUNK = 0x004e4942;
    private static final int MODE_TRIANGLES = 4;
    private static final ArrayView<JsonValue> EMPTY_JSON_ARRAY = new Array<JsonValue>(0).view();

    private final GraphicsContext graphics;
    private final boolean gpuPbr;
    private final ImageDecoder decoder;
    private final TextureMipmapPreparer mipmaps;

    GltfModelLoader(GraphicsContext graphics) {
        this.graphics = graphics;
        this.decoder = null;
        this.mipmaps = null;
        this.gpuPbr = PbrShaderProvider.usesGpuPbrShader(graphics.providerId().value());
    }

    GltfModelLoader(GraphicsContext graphics, ImageDecoder decoder, TextureMipmapPreparer mipmaps) {
        this.graphics = graphics;
        this.decoder = java.util.Objects.requireNonNull(decoder);
        this.mipmaps = mipmaps;
        // Capture the provider choice on the application thread; preparation never touches graphics.
        this.gpuPbr = PbrShaderProvider.usesGpuPbrShader(graphics.providerId().value());
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    @Override
    public Class<Model> type() {
        return Model.class;
    }

    /**
     * Loads the requested resource.
     *
     * @param context the context
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public FdxFuture<Model> load(final AssetLoadContext context, final AssetDescriptor<Model> descriptor) {
        final FileHandle file = context.files().internal(descriptor.path());
        final FdxFuture<Model> future = FdxFuture.pending();
        context.readBytes(file).onSuccess(bytes -> context.async(() -> documentSource(bytes))
                .onSuccess(source -> {
                    JsonReader reader = new JsonReader().begin(source.json);
                    context.asyncSteps(() -> reader.step(8192)).onSuccess(ignored ->
                            context.async(() -> prepareDocument(new GltfDocument(reader.result(), source.binary)))
                                    .onSuccess(document -> resolveDependencies(context, file, descriptor.path(), document, future))
                                    .onFailure(future::completeExceptionally)).onFailure(future::completeExceptionally);
                }).onFailure(future::completeExceptionally)).onFailure(future::completeExceptionally);
        return future;
    }

    Model loadModelBytes(String path, byte[] bytes) {
        GltfDocument document = prepareDocument(bytes);
        for (byte[] buffer : document.buffers) {
            if (buffer == null) {
                throw new FdxException("External glTF buffers require a file handle");
            }
        }
        return buildModel(path, decodeEmbeddedImages(document));
    }

    private GltfDocument prepareDocument(byte[] bytes) {
        DocumentSource source = documentSource(bytes);
        return prepareDocument(new GltfDocument(root(source.json), source.binary));
    }

    private GltfDocument prepareDocument(GltfDocument document) {
        if (!document.root.isObject()) throw new FdxException("glTF root must be an object");
        document.parents = GltfValidation.document(document.root);
        document.textures = new GltfTextures(document.root);
        ArrayView<JsonValue> buffers = array(document.root, "buffers");
        if (buffers.isEmpty()) {
            document.buffers = new byte[0][];
            return document;
        }
        document.buffers = new byte[buffers.size()][];
        for (int i = 0; i < buffers.size(); i++) {
            JsonValue buffer = object(buffers.get(i), "buffer");
            String uri = string(buffer, "uri", null);
            if (uri == null || uri.length() == 0) {
                if (document.binaryChunk == null) {
                    throw new FdxException("glTF buffer " + i + " has no uri and no GLB binary chunk");
                }
                document.buffers[i] = document.binaryChunk;
            }
            else if (uri.startsWith("data:")) {
                document.buffers[i] = decodeDataUri(uri);
            }
            // External buffers remain unresolved until their managed dependencies load.
        }
        return document;
    }

    private void resolveDependencies(AssetLoadContext context, FileHandle file, String path,
            GltfDocument document, FdxFuture<Model> result) {
        try {
            Array<FdxFuture<?>> pending = new Array<FdxFuture<?>>();
            ArrayView<JsonValue> buffers = array(document.root, "buffers");
            for (int i = 0; i < document.buffers.length; i++) {
                if (document.buffers[i] == null) {
                    final int index = i;
                    String uri = string(object(buffers.get(i), "buffer"), "uri", null);
                    FdxFuture<byte[]> dependency = context.dependency(
                            AssetDescriptor.of(file.parent().child(uri).path(), byte[].class));
                    dependency.onSuccess(bytes -> document.buffers[index] = bytes);
                    pending.add(dependency);
                }
            }
            ArrayView<JsonValue> images = array(document.root, "images");
            document.images = new ImageData[images.size()];
            for (int i = 0; i < images.size(); i++) {
                String uri = string(object(images.get(i), "image"), "uri", null);
                if (uri != null && uri.length() > 0 && !uri.startsWith("data:")) {
                    final int index = i;
                    FdxFuture<ImageData> dependency = context.dependency(
                            AssetDescriptor.of(file.parent().child(uri).path(), ImageData.class));
                    dependency.onSuccess(image -> document.images[index] = image);
                    pending.add(dependency);
                }
            }
            all(pending).onSuccess(ignored -> {
                try {
                    StructurePreparation structure = new StructurePreparation(document);
                    context.asyncSteps(structure::step).onSuccess(prepared ->
                            prepareImages(context, path, document, 0, result)).onFailure(result::completeExceptionally);
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            }).onFailure(result::completeExceptionally);
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
    }

    private FdxFuture<Void> all(Array<FdxFuture<?>> futures) {
        if (futures.isEmpty()) {
            return FdxFuture.completed(null);
        }
        FdxFuture<Void> result = FdxFuture.pending();
        int[] remaining = {futures.size()};
        for (int i = 0; i < futures.size(); i++) {
            futures.get(i).onSuccess(ignored -> {
                if (--remaining[0] == 0) {
                    result.complete(null);
                }
            }).onFailure(result::completeExceptionally);
        }
        return result;
    }

    private GltfDocument decodeEmbeddedImages(final GltfDocument document) {
        prepareStructure(document);
        ArrayView<JsonValue> images = array(document.root, "images");
        if (document.images == null) document.images = new ImageData[images.size()];
        for (int i = 0; i < images.size(); i++) {
            byte[] bytes = embeddedImage(document, images.get(i));
            if (bytes != null) document.images[i] = ImageAssetLoader.decode(bytes);
            else if (document.images[i] == null) throw new FdxException("External glTF image dependency is not ready");
        }
        document.textures.prepare(document.images);
        return prepareGeometry(document);
    }

    private GltfDocument prepareStructure(GltfDocument document) {
        StructurePreparation work = new StructurePreparation(document);
        while (!work.step()) { }
        return document;
    }

    private final class StructurePreparation {
        final GltfDocument document;
        int stage;
        GeometryValidation validation;
        StructurePreparation(GltfDocument document) { this.document = document; }
        boolean step() {
            switch (stage++) {
                case 0 -> document.accessors = new GltfAccessors(document.root, document.buffers);
                case 1 -> {
                    if (validation == null) validation = new GeometryValidation(document);
                    if (!validation.step()) stage--;
                }
                case 2 -> document.nodeIds = nodeIds(document);
                case 3 -> { document.preparedSkins = skins(document); document.skins = document.preparedSkins.toArray(new Skin[0]); }
                case 4 -> validateSkinInfluences(document);
                case 5 -> document.animations = animations(document);
                default -> { return true; }
            }
            return false;
        }
    }

    private byte[] embeddedImage(GltfDocument document, JsonValue image) {
        String uri = string(image, "uri", null);
        if (uri != null && uri.startsWith("data:")) return decodeDataUri(uri);
        if (uri != null && !uri.isEmpty()) return null;
        int view = integer(image, "bufferView", -1);
        if (view < 0) throw new FdxException("glTF image has no uri or bufferView");
        return bufferViewBytes(document, view);
    }

    private void prepareImages(AssetLoadContext context, String path, GltfDocument document, int index,
            FdxFuture<Model> result) {
        try {
            ArrayView<JsonValue> images = array(document.root, "images");
            if (index < images.size()) {
                ImageDecoder selected = decoder != null ? decoder : ImageDecoder.platformDefault(context);
                context.asyncFuture(() -> {
                    byte[] bytes = embeddedImage(document, images.get(index));
                    if (bytes != null) return selected.decodeAsync(null, bytes);
                    if (document.images[index] == null) throw new FdxException("External glTF image dependency is not ready");
                    return FdxFuture.completed(document.images[index]);
                }).onSuccess(image -> {
                    document.images[index] = image;
                    prepareImages(context, path, document, index + 1, result);
                }).onFailure(result::completeExceptionally);
            } else {
                GeometryPreparation geometry = new GeometryPreparation(document);
                TextureMipmapPreparer selected = decoder == null ? G3DAssetLoaders.defaultMipmaps(context) : mipmaps;
                (selected == null ? context.asyncSteps(() -> document.textures.prepareStep(document.images, 65536))
                        : document.textures.prepareAsync(context, document.images, selected))
                        .onSuccess(ignored -> context.asyncSteps(geometry::step)
                                .onSuccess(done -> new Upload(context, path, document, result).next())
                                .onFailure(result::completeExceptionally))
                        .onFailure(result::completeExceptionally);
            }
        } catch (RuntimeException | Error failure) { result.completeExceptionally(failure); }
    }

    private GltfDocument prepareGeometry(GltfDocument document) {
        GeometryPreparation work = new GeometryPreparation(document);
        while (!work.step()) { }
        return document;
    }

    private final class GeometryPreparation {
        final GltfDocument document;
        final ArrayView<JsonValue> meshes;
        int mesh, primitive;
        PrimitiveWork work;
        boolean geometryComplete;
        GeometryPreparation(GltfDocument document) {
            this.document = document; meshes = array(document.root, "meshes");
            document.geometry = new GeometryBuilder[meshes.size()][];
        }
        boolean step() {
            if (mesh == meshes.size()) { document.materials = null; return true; }
            ArrayView<JsonValue> primitives = array(meshes.get(mesh), "primitives");
            if (document.geometry[mesh] == null) document.geometry[mesh] = new GeometryBuilder[primitives.size()];
            if (primitive == primitives.size()) { mesh++; primitive = 0; return false; }
            if (work == null) { work = preparePrimitive(document, primitives.get(primitive)); return false; }
            if (!geometryComplete) { geometryComplete = work.step(); return false; }
            if (work.geometry.preparedMesh == null) {
                work.geometry.preparedMesh = prepareMesh(work.geometry);
                return false;
            }
            if (work.geometry.preparedMesh.step(1024)) {
                document.geometry[mesh][primitive++] = work.geometry;
                work = null;
                geometryComplete = false;
            }
            return false;
        }
    }

    private DocumentSource documentSource(byte[] bytes) {
        if (bytes.length >= 12 && ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC) {
            return parseGlb(bytes);
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        return new DocumentSource(json, null);
    }

    private DocumentSource parseGlb(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        int length = buffer.getInt();
        if (magic != GLB_MAGIC || version != 2 || length > bytes.length) {
            throw new FdxException("Invalid GLB header");
        }
        String root = null;
        byte[] binaryChunk = null;
        while (buffer.position() + 8 <= length) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (chunkLength < 0 || buffer.position() + chunkLength > length) {
                throw new FdxException("Invalid GLB chunk length");
            }
            byte[] chunk = new byte[chunkLength];
            buffer.get(chunk);
            if (chunkType == GLB_JSON_CHUNK) {
                root = new String(chunk, StandardCharsets.UTF_8).trim();
            }
            else if (chunkType == GLB_BIN_CHUNK) {
                binaryChunk = chunk;
            }
        }
        if (root == null) {
            throw new FdxException("GLB did not contain a JSON chunk");
        }
        return new DocumentSource(root, binaryChunk);
    }

    private record DocumentSource(String json, byte[] binary) { }

    private Model buildModel(String path, GltfDocument document) {
        Array<Mesh> meshResources = new Array<Mesh>();
        try {
            return buildModel(path, document, meshResources);
        } catch (RuntimeException | Error error) {
            for (int i = 0; i < meshResources.size(); i++) {
                disposeAfterFailure(meshResources.get(i), error);
            }
            if (document.gpuTextures != null) {
                for (Texture texture : document.gpuTextures) {
                    disposeAfterFailure(texture, error);
                }
            }
            throw error;
        }
    }

    private static void disposeAfterFailure(Disposable resource, Throwable error) {
        if (resource != null) {
            try {
                resource.dispose();
            } catch (RuntimeException | Error cleanupError) {
                if (cleanupError != error) {
                    error.addSuppressed(cleanupError);
                }
            }
        }
    }

    private Model buildModel(String path, GltfDocument document, Array<Mesh> meshResources) {
        if (document.gpuTextures == null) uploadTextures(path, document);
        ArrayView<JsonValue> meshes = array(document.root, "meshes");
        if (meshes.isEmpty()) {
            throw new FdxException("glTF model contains no meshes: " + path);
        }
        Array<Skin> loadedSkins = document.preparedSkins;
        Array<ModelNode> nodes = new Array<ModelNode>();
        Array<Material> materials = new Array<Material>();
        ArrayView<JsonValue> sceneNodes = sceneNodes(document);
        if (array(document.root, "nodes").isEmpty() && array(document.root, "scenes").isEmpty()) {
            for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
                ModelNode node = new ModelNode(path + " mesh " + meshIndex);
                appendMeshParts(path, document, node, meshIndex, null, materials, meshResources);
                nodes.add(node);
            }
        }
        else {
            for (int i = 0; i < sceneNodes.size(); i++) {
                ModelNode node = modelNode(path, document, integerValue(sceneNodes.get(i), -1),
                        materials, meshResources);
                if (node != null) {
                    nodes.add(node);
                }
            }
        }
        if (meshResources.isEmpty()) {
            throw new FdxException("glTF model contains no renderable triangles: " + path);
        }
        Array<Disposable> ownedResources = new Array<Disposable>();
        if (document.gpuTextures != null) {
            for (Texture texture : document.gpuTextures) {
                if (texture != null) {
                    ownedResources.add(texture);
                }
            }
        }
        return new DefaultModel(nodes, materials, document.animations,
                loadedSkins, meshResources, ownedResources);
    }

    private void uploadTextures(String path, GltfDocument document) {
        document.gpuTextures = document.textures.allocateHandles();
        document.textures.upload(graphics.device(), path, document.gpuTextures);
    }

    private Material material(String id, GltfMaterial source) {
        GltfMaterial material = source != null ? source : GltfMaterial.DEFAULT;
        return new Material(id)
                .shadingModel(material.shadingModel)
                .set(MaterialAttributes.baseColor(material.baseColor))
                .set(textureAttribute(MaterialAttributes.BASE_COLOR_TEXTURE, material.baseColorTexture, material, 0))
                .set(PbrAttributes.metallicFactor(
                        material.metallicFactor))
                .set(PbrAttributes.roughnessFactor(
                        material.roughnessFactor))
                .set(textureAttribute(PbrAttributes.METALLIC_ROUGHNESS_TEXTURE, material.metallicRoughnessTexture, material, 1))
                .set(textureAttribute(MaterialAttributes.NORMAL_TEXTURE, material.normalTexture, material, 2))
                .set(textureAttribute(PbrAttributes.OCCLUSION_TEXTURE, material.occlusionTexture, material, 3))
                .set(MaterialAttributes.emissiveColor(material.emissiveFactor))
                .set(textureAttribute(MaterialAttributes.EMISSIVE_TEXTURE, material.emissiveTexture, material, 4))
                .set(PbrAttributes.normalScale(material.normalScale))
                .set(PbrAttributes.occlusionStrength(material.occlusionStrength))
                .alphaMode(material.alphaMode)
                .set(MaterialAttributes.alphaCutoff(material.alphaCutoff))
                .doubleSided(material.doubleSided);
    }

    private TextureMaterialAttribute textureAttribute(MaterialAttributeType<TextureMaterialAttribute> type,
            Texture texture, GltfMaterial material, int slot) {
        return new TextureMaterialAttribute(type, texture, material.slots[slot] == null
                ? TextureCoordinates.UV0 : material.slots[slot].coordinates);
    }

    private static final String[] GEOMETRY_SEMANTICS = {
            "POSITION", "NORMAL", "TANGENT", "TEXCOORD_0", "TEXCOORD_1", "WEIGHTS_0", "JOINTS_0", "COLOR_0", "INDICES"};

    /** Decode, validate and range-check at most one accessor batch per executor step. */
    private final class GeometryValidation {
        final GltfDocument document;
        final ArrayView<JsonValue> meshes;
        int mesh, primitive, semantic, indexCursor;
        GeometryValidation(GltfDocument document) { this.document = document; meshes = array(document.root, "meshes"); }
        boolean step() {
            if (mesh == meshes.size()) return true;
            ArrayView<JsonValue> primitives = array(meshes.get(mesh), "primitives");
            if (primitive == primitives.size()) { mesh++; primitive = 0; return false; }
            JsonValue value = primitives.get(primitive);
            JsonValue attributes = object(value.get("attributes"), "primitive attributes");
            int count = document.accessors.count(integer(attributes, "POSITION", -1));
            if (semantic < GEOMETRY_SEMANTICS.length) {
                String name = GEOMETRY_SEMANTICS[semantic];
                int index = "INDICES".equals(name) ? integer(value, "indices", -1) : integer(attributes, name, -1);
                if (index < 0 && ("INDICES".equals(name) ? value.get("indices") : attributes.get(name)) != null)
                    throw new FdxException("glTF accessor index outside range: " + index);
                if (index >= 0) {
                    if (!document.accessors.prepareStep(index, name, 1024)) return false;
                    if (!"INDICES".equals(name) && document.accessors.count(index) != count)
                        throw new FdxException("glTF " + name + " count differs from POSITION");
                }
                semantic++;
                return false;
            }
            if (value.get("indices") != null) {
                int[] indices = readIndexAccessor(document, integer(value, "indices", -1));
                if (indices.length % 3 != 0) throw new FdxException("glTF triangle index count must be a multiple of three");
                int end = indexCursor + Math.min(3072, indices.length - indexCursor);
                for (; indexCursor < end; indexCursor++) validateGltfIndex(indices[indexCursor], count);
                if (indexCursor < indices.length) return false;
            } else if (count % 3 != 0) throw new FdxException("glTF unindexed triangles require a multiple of three vertices");
            validatePrimitive(document, value);
            primitive++; semantic = 0; indexCursor = 0;
            return false;
        }
    }

    private void validatePrimitive(GltfDocument document, JsonValue primitive) {
        ArrayView<JsonValue> materials = array(document.root, "materials");
        if (integer(primitive, "mode", MODE_TRIANGLES) != MODE_TRIANGLES)
            throw new FdxException("Only glTF triangle primitives are supported");
        if (!array(primitive, "targets").isEmpty()) throw new FdxException("glTF morph targets are unsupported");
        JsonValue attributes = object(primitive.get("attributes"), "primitive attributes");
        if (attributes.get("JOINTS_1") != null || attributes.get("WEIGHTS_1") != null)
            throw new FdxException("glTF skinning supports one four-weight joint set");
        if (attributes.get("TANGENT") != null && attributes.get("NORMAL") == null)
            throw new FdxException("glTF supplied tangents require normals");
        if ((attributes.get("JOINTS_0") == null) != (attributes.get("WEIGHTS_0") == null))
            throw new FdxException("glTF skinning requires both JOINTS_0 and WEIGHTS_0");
        int materialIndex = integer(primitive, "material", -1);
        if (materialIndex < -1 || primitive.get("material") != null && materialIndex < 0 || materialIndex >= materials.size())
            throw new FdxException("glTF material index outside range");
        if (materialIndex < 0) return;
        JsonValue material = materials.get(materialIndex), pbr = material.get("pbrMetallicRoughness");
        JsonValue[] slots = {pbr == null ? null : pbr.get("baseColorTexture"),
                pbr == null ? null : pbr.get("metallicRoughnessTexture"), material.get("normalTexture"),
                material.get("occlusionTexture"), material.get("emissiveTexture")};
        for (JsonValue slot : slots) if (slot != null
                && attributes.get("TEXCOORD_" + GltfTextures.coordinates(slot).set()) == null)
            throw new FdxException("glTF material references a missing texture coordinate set");
    }

    /** Holds partial graphics ownership across budgeted update steps, including cancellation. */
    private final class Upload {
        private final AssetLoadContext context;
        private final String path;
        private final GltfDocument document;
        private final FdxFuture<Model> result;
        private int texture;
        private boolean handedOff;
        private ModelAssembly assembly;

        Upload(AssetLoadContext context, String path, GltfDocument document, FdxFuture<Model> result) {
            this.context = context;
            this.path = path;
            this.document = document;
            this.result = result;
            document.gpuTextures = document.textures.allocateHandles();
            result.onFailure(error -> {
                if (!handedOff) {
                    handedOff = true;
                    for (Texture resource : document.gpuTextures) disposeAfterFailure(resource, error);
                    if (assembly != null) disposeAfterFailure(assembly.upload, error);
                    if (assembly != null) for (int i = 0; i < assembly.meshResources.size(); i++)
                        disposeAfterFailure(assembly.meshResources.get(i), error);
                }
            });
        }

        void next() {
            try {
                if (texture < document.gpuTextures.length) {
                    int index = texture++;
                    context.completeOnUpdate(() -> {
                        document.textures.upload(graphics.device(), path, document.gpuTextures, index);
                        return null;
                    }).onSuccess(ignored -> next()).onFailure(result::completeExceptionally);
                } else if (assembly == null || !assembly.done) {
                    if (assembly == null) assembly = new ModelAssembly(path, document);
                    context.completeOnUpdate(() -> { assembly.step(); return null; })
                            .onSuccess(ignored -> next()).onFailure(result::completeExceptionally);
                } else {
                    context.completeOnUpdate(() -> {
                        Model model = assembly.result();
                        handedOff = true;
                        return model;
                    }).onSuccess(result::complete).onFailure(result::completeExceptionally);
                }
            } catch (RuntimeException | Error error) {
                result.completeExceptionally(error);
            }
        }
    }

    /** One node or primitive per graphics-thread step; Upload retains ownership until publication. */
    private final class ModelAssembly {
        final String path;
        final GltfDocument document;
        final Array<ModelNode> nodes = new Array<>();
        final Array<Material> materials = new Array<>();
        final Array<Mesh> meshResources = new Array<>();
        final Array<AssemblyNode> stack = new Array<>();
        final ArrayView<JsonValue> roots;
        final boolean implicitNodes;
        int root;
        boolean done;
        Mesh.PositionColor3DUpload upload;
        ModelAssembly(String path, GltfDocument document) {
            this.path = path; this.document = document;
            implicitNodes = array(document.root, "nodes").isEmpty() && array(document.root, "scenes").isEmpty();
            roots = implicitNodes ? array(document.root, "meshes") : sceneNodes(document);
        }
        void step() {
            if (stack.isEmpty()) {
                if (root == roots.size()) { done = true; return; }
                AssemblyNode next = implicitNodes ? new AssemblyNode(root, null, null)
                        : node(integerValue(roots.get(root), -1));
                root++;
                if (next != null) { nodes.add(next.node); stack.add(next); }
                return;
            }
            AssemblyNode current = stack.peek();
            if (current.primitive < current.primitives.size()) {
                int index = current.primitive;
                if (upload == null) {
                    upload = document.geometry[current.mesh][index].preparedMesh.beginUpload(graphics,
                            path + " mesh " + current.mesh + "." + index);
                    return;
                }
                if (!upload.step(256 * 1024)) return;
                Mesh mesh = upload.take();
                meshResources.add(mesh);
                upload.dispose(); upload = null;
                current.node.addPart(modelNodePart(path, document, current.mesh, index,
                        current.primitives.get(index), current.skin, materials, mesh));
                current.primitive++;
            } else if (current.child < current.children.size()) {
                AssemblyNode child = node(integerValue(current.children.get(current.child++), -1));
                if (child != null) { current.node.addChild(child.node); stack.add(child); }
            } else stack.pop();
        }
        AssemblyNode node(int index) {
            if (index < 0) return null;
            JsonValue source = array(document.root, "nodes").get(index);
            ModelNode node = new ModelNode(nodeId(document, index));
            nodeTransform(source, node.localTransform());
            return new AssemblyNode(integer(source, "mesh", -1), source, node);
        }
        Model result() {
            if (meshResources.isEmpty()) throw new FdxException("glTF model contains no renderable triangles: " + path);
            Array<Disposable> owned = new Array<>();
            for (Texture texture : document.gpuTextures) if (texture != null) owned.add(texture);
            return new DefaultModel(nodes, materials, document.animations, document.preparedSkins, meshResources, owned);
        }
        final class AssemblyNode {
            final ModelNode node;
            final int mesh;
            final Skin skin;
            final ArrayView<JsonValue> primitives, children;
            int primitive, child;
            AssemblyNode(int mesh, JsonValue source, ModelNode node) {
                this.mesh = mesh;
                this.node = node != null ? node : new ModelNode(path + " mesh " + mesh);
                skin = source == null ? null : skin(document, integer(source, "skin", -1));
                primitives = mesh < 0 ? EMPTY_JSON_ARRAY : array(array(document.root, "meshes").get(mesh), "primitives");
                children = source == null ? EMPTY_JSON_ARRAY : array(source, "children");
            }
        }
    }

    private void validateSkinInfluences(GltfDocument document) {
        ArrayView<JsonValue> nodes = array(document.root, "nodes"), meshes = array(document.root, "meshes");
        for (int n = 0; n < nodes.size(); n++) {
            JsonValue node = nodes.get(n);
            int skinIndex = integer(node, "skin", -1);
            if (skinIndex < 0) continue;
            int jointCount = document.skins[skinIndex].skeleton().bones().size();
            ArrayView<JsonValue> primitives = array(meshes.get(integer(node, "mesh", -1)), "primitives");
            for (int p = 0; p < primitives.size(); p++) {
                JsonValue attributes = primitives.get(p).require("attributes");
                int[] joints = document.accessors.joints(integer(attributes, "JOINTS_0", -1));
                float[] weights = document.accessors.skinWeights(integer(attributes, "WEIGHTS_0", -1));
                for (int v = 0; v < joints.length; v += 4) for (int j = 0; j < 4; j++) {
                    if (joints[v+j] < 0 || joints[v+j] >= jointCount)
                        throw new FdxException("glTF node " + n + " joint index outside skin range");
                    if (weights[v+j] > 0) for (int k = 0; k < j; k++)
                        if (weights[v+k] > 0 && joints[v+k] == joints[v+j])
                            throw new FdxException("glTF vertex repeats a weighted joint");
                }
            }
        }
    }

    private String[] nodeIds(GltfDocument document) {
        ArrayView<JsonValue> nodes = array(document.root, "nodes");
        String[] ids = new String[nodes.size()];
        ObjectMap<String, Integer> used = new ObjectMap<String, Integer>();
        for (int i = 0; i < nodes.size(); i++) {
            JsonValue node = object(nodes.get(i), "node");
            String base = string(node, "name", "").trim();
            if (base.length() == 0) {
                base = "node-" + i;
            }
            String id = base;
            int suffix = i;
            while (used.containsKey(id)) id = base + "-" + suffix++;
            used.put(id, i);
            ids[i] = id;
        }
        return ids;
    }

    private String nodeId(GltfDocument document, int nodeIndex) {
        if (document.nodeIds == null || nodeIndex < 0 || nodeIndex >= document.nodeIds.length) {
            return "node-" + nodeIndex;
        }
        return document.nodeIds[nodeIndex];
    }

    private Skin skin(GltfDocument document, int skinIndex) {
        if (document.skins == null || skinIndex < 0 || skinIndex >= document.skins.length) {
            return null;
        }
        return document.skins[skinIndex];
    }

    private Array<Skin> skins(GltfDocument document) {
        Array<Skin> result = new Array<Skin>();
        ArrayView<JsonValue> skins = array(document.root, "skins");
        int[] parentNodes = parentNodes(document);
        for (int skinIndex = 0; skinIndex < skins.size(); skinIndex++) {
            JsonValue skin = object(skins.get(skinIndex), "skin");
            ArrayView<JsonValue> joints = array(skin, "joints");
            Matrix4[] inverseBindMatrices = inverseBindMatrices(document, integer(skin, "inverseBindMatrices", -1),
                    joints.size());
            Array<Bone> bones = new Array<Bone>();
            for (int jointIndex = 0; jointIndex < joints.size(); jointIndex++) {
                int nodeIndex = integerValue(joints.get(jointIndex), -1);
                int parentIndex = indexOf(joints, parentNodes, nodeIndex);
                bones.add(new Bone(nodeId(document, nodeIndex), parentIndex, inverseBindMatrices[jointIndex]));
            }
            String id = string(skin, "name", "skin-" + skinIndex);
            result.add(new Skin(id, new Skeleton(bones)));
        }
        return result;
    }

    private Matrix4[] inverseBindMatrices(GltfDocument document, int accessorIndex, int count) {
        Matrix4[] matrices = new Matrix4[count];
        if (accessorIndex < 0) {
            for (int i = 0; i < matrices.length; i++) {
                matrices[i] = Matrix4.IDENTITY;
            }
            return matrices;
        }
        float[] values = document.accessors.inverseBindMatrices(accessorIndex, count);
        for (int i = 0; i < count; i++) {
            float[] matrix = new float[Matrix4.VALUE_COUNT];
            System.arraycopy(values, i * Matrix4.VALUE_COUNT, matrix, 0, Matrix4.VALUE_COUNT);
            matrices[i] = new Matrix4(matrix);
        }
        return matrices;
    }

    private int[] parentNodes(GltfDocument document) {
        return document.parents;
    }

    private int indexOf(ArrayView<JsonValue> joints, int[] parentNodes, int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= parentNodes.length) {
            return -1;
        }
        int parentNode = parentNodes[nodeIndex];
        for (int i = 0; i < joints.size(); i++) {
            if (integerValue(joints.get(i), -1) == parentNode) {
                return i;
            }
        }
        return -1;
    }

    private ModelNode modelNode(String path, GltfDocument document, int nodeIndex, Array<Material> materials,
            Array<Mesh> meshResources) {
        if (nodeIndex < 0) {
            return null;
        }
        JsonValue node = object(array(document.root, "nodes").get(nodeIndex), "node");
        ModelNode modelNode = new ModelNode(nodeId(document, nodeIndex));
        nodeTransform(node, modelNode.localTransform());
        int meshIndex = integer(node, "mesh", -1);
        if (meshIndex >= 0) {
            appendMeshParts(path, document, modelNode, meshIndex, skin(document, integer(node, "skin", -1)),
                    materials, meshResources);
        }
        ArrayView<JsonValue> children = array(node, "children");
        for (int i = 0; i < children.size(); i++) {
            ModelNode child = modelNode(path, document, integerValue(children.get(i), -1), materials, meshResources);
            if (child != null) {
                modelNode.addChild(child);
            }
        }
        return modelNode;
    }

    private void appendMeshParts(String path, GltfDocument document, ModelNode node, int meshIndex, Skin skin,
            Array<Material> materials, Array<Mesh> meshResources) {
        JsonValue mesh = object(array(document.root, "meshes").get(meshIndex), "mesh");
        ArrayView<JsonValue> primitives = array(mesh, "primitives");
        for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
            JsonValue primitive = object(primitives.get(primitiveIndex), "primitive");
            node.addPart(modelNodePart(path, document, meshIndex, primitiveIndex, primitive, skin, materials,
                    meshResources));
        }
    }

    private ModelNodePart modelNodePart(String path, GltfDocument document, int meshIndex, int primitiveIndex,
            JsonValue primitive, Skin skin, Array<Material> materials, Array<Mesh> meshResources) {
        GeometryBuilder geometry = document.geometry[meshIndex][primitiveIndex];
        Mesh mesh = geometry.preparedMesh.upload(graphics, path + " mesh " + meshIndex + "." + primitiveIndex);
        meshResources.add(mesh);
        return modelNodePart(path, document, meshIndex, primitiveIndex, primitive, skin, materials, mesh);
    }

    private ModelNodePart modelNodePart(String path, GltfDocument document, int meshIndex, int primitiveIndex,
            JsonValue primitive, Skin skin, Array<Material> materials, Mesh mesh) {
        GeometryBuilder geometry = document.geometry[meshIndex][primitiveIndex];
        Material pbrMaterial = material(path + " material " + meshIndex + "." + primitiveIndex,
                material(document, integer(primitive, "material", -1))).doubleSided(geometry.doubleSided);
        materials.add(pbrMaterial);
        MeshPart meshPart = new MeshPart(path + " part " + meshIndex + "." + primitiveIndex, mesh, null, 0,
                mesh.vertexCount());
        return geometry.hasSkinning()
                ? new ModelNodePart(meshPart, pbrMaterial, skin, geometry.joints(), geometry.weights())
                : new ModelNodePart(meshPart, pbrMaterial);
    }

    private Mesh.PositionColor3DPreparation prepareMesh(GeometryBuilder geometry) {
        boolean retainSourceData = !gpuPbr || geometry.hasSkinning();
        float[] positions = geometry.positions();
        float[] bakedColors = retainSourceData ? geometry.bakedColors() : null;
        float[] bakedPbr = retainSourceData ? geometry.bakedPbr() : null;
        float[] bakedEmissive = retainSourceData ? geometry.bakedEmissive() : null;
        return Mesh.preparePositionColor3D(positions,
                geometry.colors(), bakedColors, geometry.normals(), geometry.texCoords(), geometry.pbr(), bakedPbr,
                geometry.emissive(), bakedEmissive, geometry.hasSkinning() ? geometry.joints() : null,
                geometry.hasSkinning() ? geometry.weights() : null, bounds(positions), retainSourceData,
                geometry.extended ? geometry.texCoords1.toArray() : null,
                geometry.extended ? geometry.tangents.toArray() : null);
    }

    private PrimitiveWork preparePrimitive(GltfDocument document, JsonValue primitive) {
        GeometryBuilder geometry = new GeometryBuilder();
        int mode = integer(primitive, "mode", MODE_TRIANGLES);
        if (mode != MODE_TRIANGLES) {
            throw new FdxException("Only glTF triangle primitives are supported");
        }
        JsonValue attributes = object(primitive.get("attributes"), "primitive attributes");
        int positionAccessor = integer(attributes, "POSITION", -1);
        if (positionAccessor < 0) {
            throw new FdxException("glTF primitive is missing POSITION");
        }
        float[] sourcePositions = readFloatAccessor(document, positionAccessor, 3);
        float[] sourceNormals = null;
        int normalAccessor = integer(attributes, "NORMAL", -1);
        if (normalAccessor >= 0) {
            sourceNormals = readFloatAccessor(document, normalAccessor, 3);
        }
        float[] sourceTexCoords = null;
        int texCoordAccessor = integer(attributes, "TEXCOORD_0", -1);
        if (texCoordAccessor >= 0) {
            sourceTexCoords = readFloatAccessor(document, texCoordAccessor, 2);
        }
        int uv1Accessor = integer(attributes, "TEXCOORD_1", -1);
        float[] sourceTexCoords1 = uv1Accessor < 0 ? null : readFloatAccessor(document, uv1Accessor, 2);
        int tangentAccessor = integer(attributes, "TANGENT", -1);
        float[] sourceTangents = tangentAccessor < 0 ? null : readFloatAccessor(document, tangentAccessor, 4);
        float[] sourceColors = null;
        int colorAccessor = integer(attributes, "COLOR_0", -1);
        if (colorAccessor >= 0) {
            sourceColors = readColorAccessor(document, colorAccessor);
        }
        int[] sourceJoints = null;
        float[] sourceWeights = null;
        int jointAccessor = integer(attributes, "JOINTS_0", -1);
        int weightAccessor = integer(attributes, "WEIGHTS_0", -1);
        if (jointAccessor >= 0 || weightAccessor >= 0) {
            if (jointAccessor < 0 || weightAccessor < 0) {
                throw new FdxException("glTF skinning requires both JOINTS_0 and WEIGHTS_0");
            }
            sourceJoints = readIntAccessor(document, jointAccessor, 4);
            sourceWeights = document.accessors.skinWeights(weightAccessor);
        }
        GltfMaterial material = material(document, integer(primitive, "material", -1));
        geometry.extended = sourceTexCoords1 != null || sourceTangents != null || material.normalImage != null;
        geometry.material(material);
        geometry.doubleSided |= material.doubleSided;
        int[] indices = primitive.get("indices") != null
                ? readIndexAccessor(document, integer(primitive, "indices", -1))
                : sequence(sourcePositions.length / 3);
        return new PrimitiveWork(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, indices,
                sourceJoints, sourceWeights, material, sourceTexCoords1, sourceTangents);
    }

    private final class PrimitiveWork {
        final GeometryBuilder geometry;
        final float[] positions, normals, uv, colors, weights, uv1, tangents;
        final int[] indices, joints;
        final GltfMaterial material;
        int index;
        PrimitiveWork(GeometryBuilder geometry, float[] positions, float[] normals, float[] uv, float[] colors,
                int[] indices, int[] joints, float[] weights, GltfMaterial material, float[] uv1, float[] tangents) {
            this.geometry = geometry; this.positions = positions; this.normals = normals; this.uv = uv;
            this.colors = colors; this.indices = indices; this.joints = joints; this.weights = weights;
            this.material = material; this.uv1 = uv1; this.tangents = tangents;
        }
        boolean step() {
            int end = Math.min(indices.length, index + 768); // At most 256 triangles per cooperative step.
            appendPrimitive(geometry, positions, normals, uv, colors, indices, joints, weights, material,
                    Matrix4.IDENTITY, uv1, tangents, index, end);
            index = end;
            return index == indices.length;
        }
    }

    private void appendPrimitive(GeometryBuilder geometry, float[] sourcePositions, float[] sourceNormals,
            float[] sourceTexCoords, float[] sourceColors, int[] indices, int[] sourceJoints, float[] sourceWeights,
            GltfMaterial material, Matrix4 transform, float[] sourceTexCoords1, float[] sourceTangents, int start, int end) {
        int vertexCount = sourcePositions.length / 3;
        int colorComponents = sourceColors != null && sourceColors.length == vertexCount * 3 ? 3 : 4;
        for (int i = start; i < end; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];
            validateGltfIndex(i0, vertexCount);
            validateGltfIndex(i1, vertexCount);
            validateGltfIndex(i2, vertexCount);
            float[] normalUv = material.slots[2] != null && material.slots[2].coordinates.set() == 1
                    ? sourceTexCoords1 : sourceTexCoords;
            TriangleBasis basis = triangleBasis(sourcePositions, sourceNormals, normalUv, i0, i1, i2);
            appendVertex(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, colorComponents,
                    sourceJoints, sourceWeights, i0, material, transform, basis, sourceTexCoords1, sourceTangents);
            appendVertex(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, colorComponents,
                    sourceJoints, sourceWeights, i1, material, transform, basis, sourceTexCoords1, sourceTangents);
            appendVertex(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, colorComponents,
                    sourceJoints, sourceWeights, i2, material, transform, basis, sourceTexCoords1, sourceTangents);
        }
    }

    private void appendVertex(GeometryBuilder geometry, float[] sourcePositions, float[] sourceNormals,
            float[] sourceTexCoords, float[] sourceColors, int colorComponents, int[] sourceJoints,
            float[] sourceWeights, int index, GltfMaterial material, Matrix4 transform, TriangleBasis basis,
            float[] sourceTexCoords1, float[] sourceTangents) {
        Vector3 position = position(sourcePositions, index);
        Vector3 normal = sourceNormals != null ? position(sourceNormals, index) : basis.normal;
        float u = 0.0f;
        float v = 0.0f;
        if (sourceTexCoords != null) {
            int texCoordOffset = index * 2;
            u = sourceTexCoords[texCoordOffset];
            v = sourceTexCoords[texCoordOffset + 1];
        }
        float u1 = sourceTexCoords1 == null ? 0 : sourceTexCoords1[index*2];
        float v1 = sourceTexCoords1 == null ? 0 : sourceTexCoords1[index*2+1];
        Vector3 tangent = sourceTangents == null ? basis.tangent
                : new Vector3(sourceTangents[index*4], sourceTangents[index*4+1], sourceTangents[index*4+2]);
        tangent = tangent.subtract(normal.scale(tangent.dot(normal))).normalize();
        if (tangent.dot(tangent) < .000001f) {
            Vector3 axis = Math.abs(normal.x()) < .9f ? Vector3.X : Vector3.Y;
            tangent = axis.subtract(normal.scale(axis.dot(normal))).normalize();
        }
        float handedness = sourceTangents == null ? (normal.cross(tangent).dot(basis.bitangent) < 0 ? -1 : 1)
                : sourceTangents[index*4+3];
        if (geometry.extended) {
            geometry.texCoords1.add(u1); geometry.texCoords1.add(v1);
            geometry.tangents.add(tangent.x()); geometry.tangents.add(tangent.y()); geometry.tangents.add(tangent.z());
            geometry.tangents.add(sourceTangents != null || material.normalImage != null ? handedness : 0);
        }
        boolean bakeTextures = !usesGpuPbrShader();
        if (bakeTextures && material.normalImage != null) {
            Color sample = material.slots[2].sample(u, v, u1, v1, false);
            Vector3 mapped = new Vector3((sample.red()*2-1)*material.normalScale,
                    (sample.green()*2-1)*material.normalScale, sample.blue()*2-1);
            normal = tangent.scale(mapped.x()).add(normal.cross(tangent).scale(handedness*mapped.y()))
                    .add(normal.scale(mapped.z())).normalize();
        }
        Color color = material.baseColor;
        if (sourceColors != null) {
            int colorOffset = index * colorComponents;
            Color vertexColor = new Color(sourceColors[colorOffset], sourceColors[colorOffset + 1],
                    sourceColors[colorOffset + 2], colorComponents > 3 ? sourceColors[colorOffset + 3] : 1.0f);
            color = multiply(color, vertexColor);
        }
        Color bakedColor = color;
        if (bakeTextures && material.baseColorImage != null) {
            bakedColor = multiply(bakedColor, material.slots[0].sample(u, v, u1, v1, true));
        }
        float ao = 1.0f;
        float bakedAo = bakeTextures && material.occlusionImage != null
                ? 1 + material.occlusionStrength * (material.slots[3].sample(u, v, u1, v1, false).red()-1)
                : 1.0f;
        float metallic = material.metallicFactor;
        float roughness = material.roughnessFactor;
        float bakedMetallic = metallic;
        float bakedRoughness = roughness;
        if (bakeTextures && material.metallicRoughnessImage != null) {
            Color mr = material.slots[1].sample(u, v, u1, v1, false);
            bakedRoughness *= mr.green();
            bakedMetallic *= mr.blue();
        }
        Color emissive = material.emissiveFactor;
        Color bakedEmissive = emissive;
        if (bakeTextures && material.emissiveImage != null) {
            bakedEmissive = multiply(bakedEmissive, material.slots[4].sample(u, v, u1, v1, true));
        }

        Vector3 transformedPosition = transform.transformPosition(position);
        Vector3 transformedNormal = transform.transformDirection(normal);
        geometry.add(transformedPosition, transformedNormal, u, v, color,
                clamp(ao, 0.0f, 1.0f), clamp(metallic, 0.0f, 1.0f), clamp(roughness, 0.04f, 1.0f),
                emissive, bakedColor, clamp(bakedAo, 0.0f, 1.0f), clamp(bakedMetallic, 0.0f, 1.0f),
                clamp(bakedRoughness, 0.04f, 1.0f), bakedEmissive, sourceJoints, sourceWeights, index);
    }

    private void validateGltfIndex(int index, int vertexCount) {
        if (index < 0 || index >= vertexCount) {
            throw new FdxException("glTF index out of range: " + index);
        }
    }

    private TriangleBasis triangleBasis(float[] positions, float[] normals, float[] texCoords, int i0, int i1,
            int i2) {
        Vector3 p0 = position(positions, i0);
        Vector3 p1 = position(positions, i1);
        Vector3 p2 = position(positions, i2);
        Vector3 faceNormal = p1.subtract(p0).cross(p2.subtract(p0)).normalize();
        Vector3 n0 = normals != null ? position(normals, i0).normalize() : faceNormal;
        Vector3 tangent = Vector3.X;
        Vector3 bitangent = n0.cross(tangent).normalize();
        if (texCoords != null) {
            int uv0 = i0 * 2;
            int uv1 = i1 * 2;
            int uv2 = i2 * 2;
            float du1 = texCoords[uv1] - texCoords[uv0];
            float dv1 = texCoords[uv1 + 1] - texCoords[uv0 + 1];
            float du2 = texCoords[uv2] - texCoords[uv0];
            float dv2 = texCoords[uv2 + 1] - texCoords[uv0 + 1];
            float denominator = du1 * dv2 - du2 * dv1;
            if (Math.abs(denominator) > 0.000001f) {
                float inv = 1.0f / denominator;
                tangent = p1.subtract(p0).scale(dv2).subtract(p2.subtract(p0).scale(dv1)).scale(inv).normalize();
                bitangent = p2.subtract(p0).scale(du1).subtract(p1.subtract(p0).scale(du2)).scale(inv).normalize();
            }
        }
        return new TriangleBasis(n0, tangent, bitangent);
    }

    private ArrayView<JsonValue> sceneNodes(GltfDocument document) {
        ArrayView<JsonValue> scenes = array(document.root, "scenes");
        if (scenes.isEmpty()) {
            JsonValue roots = JsonValue.array();
            for (int i = 0; i < document.parents.length; i++) if (document.parents[i] < 0) roots.add(i);
            return roots.arrayValues();
        }
        int sceneIndex = integer(document.root, "scene", 0);
        if (sceneIndex < 0 || sceneIndex >= scenes.size()) {
            return EMPTY_JSON_ARRAY;
        }
        return array(object(scenes.get(sceneIndex), "scene"), "nodes");
    }

    private Array<AnimationClip> animations(GltfDocument document) {
        Array<AnimationClip> result = new Array<AnimationClip>();
        ArrayView<JsonValue> animations = array(document.root, "animations");
        ArrayView<JsonValue> nodes = array(document.root, "nodes");
        for (int animationIndex = 0; animationIndex < animations.size(); animationIndex++) {
            JsonValue animation = object(animations.get(animationIndex), "animation");
            ArrayView<JsonValue> samplers = array(animation, "samplers");
            ArrayView<JsonValue> channels = array(animation, "channels");
            IntMap<GltfNodeAnimationBuilder> builders = new IntMap<GltfNodeAnimationBuilder>();
            float duration = 0.0f;
            for (int channelIndex = 0; channelIndex < channels.size(); channelIndex++) {
                JsonValue channel = object(channels.get(channelIndex), "animation channel");
                JsonValue target = object(channel.get("target"), "animation target");
                if (target.get("node") == null) {
                    continue;
                }
                int nodeIndex = integer(target, "node", -1);
                if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
                    throw new FdxException("glTF animation target node outside range: " + nodeIndex);
                }
                String path = string(target, "path", "");
                int samplerIndex = integer(channel, "sampler", -1);
                if (samplerIndex < 0 || samplerIndex >= samplers.size()) {
                    throw new FdxException("glTF animation sampler outside range: " + samplerIndex);
                }
                JsonValue sampler = object(samplers.get(samplerIndex), "animation sampler");
                AnimationSampler.Interpolation interpolation = switch (string(sampler, "interpolation", "LINEAR")) {
                    case "LINEAR" -> AnimationSampler.Interpolation.LINEAR;
                    case "STEP" -> AnimationSampler.Interpolation.STEP;
                    case "CUBICSPLINE" -> AnimationSampler.Interpolation.CUBICSPLINE;
                    default -> throw new FdxException("Unsupported glTF animation interpolation");
                };
                float[] times = document.accessors.animationValues(integer(sampler, "input", -1), "SCALAR");
                float[] values = document.accessors.animationValues(integer(sampler, "output", -1),
                        animationComponents(path) == 4 ? "VEC4" : "VEC3");
                AnimationSampler track = new AnimationSampler("rotation".equals(path), interpolation, times, values);
                duration = Math.max(duration, track.lastTime());
                GltfNodeAnimationBuilder builder = builders.get(nodeIndex);
                if (builder == null) {
                    builder = new GltfNodeAnimationBuilder(nodeId(document, nodeIndex),
                            object(nodes.get(nodeIndex), "node"));
                    builders.put(nodeIndex, builder);
                }
                builder.channel(path, track);
            }
            Array<AnimationClip.NodeTransformChannel> nodeChannels =
                    new Array<AnimationClip.NodeTransformChannel>();
            ObjectIterator<GltfNodeAnimationBuilder> iterator = builders.values().iterator();
            while (iterator.hasNext()) {
                nodeChannels.add(iterator.next().build());
            }
            String id = string(animation, "name", "animation-" + animationIndex);
            result.add(new AnimationClip(id, duration,
                    nodeChannels.toArray(new AnimationClip.NodeTransformChannel[0])));
        }
        return result;
    }

    private int animationComponents(String path) {
        if ("translation".equals(path) || "scale".equals(path)) {
            return 3;
        }
        if ("rotation".equals(path)) {
            return 4;
        }
        throw new FdxException("Unsupported glTF animation target path: " + path);
    }

    private Matrix4 nodeTransform(JsonValue node, Matrix4 out) {
        ArrayView<JsonValue> matrix = array(node, "matrix");
        if (matrix.size() == Matrix4.VALUE_COUNT) {
            float[] values = new float[Matrix4.VALUE_COUNT];
            for (int i = 0; i < values.length; i++) {
                values[i] = number(matrix.get(i), i % 5 == 0 ? 1.0f : 0.0f);
            }
            return out.set(values);
        }
        ArrayView<JsonValue> translation = array(node, "translation");
        ArrayView<JsonValue> rotation = array(node, "rotation");
        ArrayView<JsonValue> scale = array(node, "scale");
        float translationX = translation.size() >= 3
                ? number(translation.get(0), 0.0f) : 0.0f;
        float translationY = translation.size() >= 3
                ? number(translation.get(1), 0.0f) : 0.0f;
        float translationZ = translation.size() >= 3
                ? number(translation.get(2), 0.0f) : 0.0f;
        float rotationX = rotation.size() >= 4
                ? number(rotation.get(0), 0.0f) : 0.0f;
        float rotationY = rotation.size() >= 4
                ? number(rotation.get(1), 0.0f) : 0.0f;
        float rotationZ = rotation.size() >= 4
                ? number(rotation.get(2), 0.0f) : 0.0f;
        float rotationW = rotation.size() >= 4
                ? number(rotation.get(3), 1.0f) : 1.0f;
        float scaleX = scale.size() >= 3
                ? number(scale.get(0), 1.0f) : 1.0f;
        float scaleY = scale.size() >= 3
                ? number(scale.get(1), 1.0f) : 1.0f;
        float scaleZ = scale.size() >= 3
                ? number(scale.get(2), 1.0f) : 1.0f;
        return out.setToTrs(translationX, translationY,
                translationZ, rotationX, rotationY, rotationZ, rotationW,
                scaleX, scaleY, scaleZ);
    }

    private GltfMaterial material(GltfDocument document, int materialIndex) {
        if (materialIndex < 0) {
            return GltfMaterial.DEFAULT;
        }
        ArrayView<JsonValue> materials = array(document.root, "materials");
        if (materialIndex >= materials.size()) {
            return GltfMaterial.DEFAULT;
        }
        if (document.materials == null) {
            document.materials = new GltfMaterial[materials.size()];
        }
        if (document.materials[materialIndex] != null) {
            return document.materials[materialIndex];
        }
        JsonValue material = object(materials.get(materialIndex), "material");
        JsonValue pbr = object(material.get("pbrMetallicRoughness"), "pbr", true);
        Color baseColor = colorFactor(pbr != null ? array(pbr, "baseColorFactor") : EMPTY_JSON_ARRAY,
                Color.WHITE);
        ImageData baseColorImage = textureImage(document, pbr != null ? object(pbr.get("baseColorTexture"),
                "baseColorTexture", true) : null);
        Texture baseColorTexture = texture(document, pbr != null ? object(pbr.get("baseColorTexture"),
                "baseColorTexture", true) : null, GltfTextures.colorRole(material));
        float metallicFactor = pbr != null ? number(pbr.get("metallicFactor"), 1.0f) : 1.0f;
        float roughnessFactor = pbr != null ? number(pbr.get("roughnessFactor"), 1.0f) : 1.0f;
        ImageData metallicRoughnessImage = textureImage(document, pbr != null ? object(
                pbr.get("metallicRoughnessTexture"), "metallicRoughnessTexture", true) : null);
        Texture metallicRoughnessTexture = texture(document, pbr != null ? object(
                pbr.get("metallicRoughnessTexture"), "metallicRoughnessTexture", true) : null, GltfTextures.DATA);
        Color emissiveFactor = colorFactor(array(material, "emissiveFactor"), Color.BLACK);
        ImageData emissiveImage = textureImage(document, object(material.get("emissiveTexture"), "emissiveTexture",
                true));
        Texture emissiveTexture = texture(document, object(material.get("emissiveTexture"), "emissiveTexture",
                true), GltfTextures.COLOR);
        ImageData occlusionImage = textureImage(document, object(material.get("occlusionTexture"), "occlusionTexture",
                true));
        Texture occlusionTexture = texture(document, object(material.get("occlusionTexture"), "occlusionTexture",
                true), GltfTextures.DATA);
        ImageData normalImage = textureImage(document, object(material.get("normalTexture"), "normalTexture", true));
        Texture normalTexture = texture(document, object(material.get("normalTexture"), "normalTexture", true),
                GltfTextures.DATA);
        MaterialAlphaMode alphaMode = alphaMode(string(material, "alphaMode", "OPAQUE"));
        float alphaCutoff = number(material.get("alphaCutoff"), 0.5f);
        boolean doubleSided = bool(material, "doubleSided", false);
        JsonValue extensions = object(material.get("extensions"),
                "material extensions", true);
        ShadingModel shadingModel = extensions != null
                && extensions.get("KHR_materials_unlit") != null
                        ? ShadingModel.UNLIT : ShadingModel.PBR;
        document.materials[materialIndex] = new GltfMaterial(baseColor, baseColorImage, baseColorTexture,
                metallicFactor, roughnessFactor, metallicRoughnessImage, metallicRoughnessTexture,
                emissiveFactor, emissiveImage, emissiveTexture, occlusionImage, occlusionTexture,
                normalImage, normalTexture, alphaMode, alphaCutoff, doubleSided,
                shadingModel);
        GltfMaterial prepared = document.materials[materialIndex];
        prepared.slots[0] = document.textures.binding(pbr == null ? null : pbr.get("baseColorTexture"));
        prepared.slots[1] = document.textures.binding(pbr == null ? null : pbr.get("metallicRoughnessTexture"));
        prepared.slots[2] = document.textures.binding(material.get("normalTexture"));
        prepared.slots[3] = document.textures.binding(material.get("occlusionTexture"));
        prepared.slots[4] = document.textures.binding(material.get("emissiveTexture"));
        prepared.normalScale = GltfTextures.normalScale(material);
        prepared.occlusionStrength = GltfTextures.occlusionStrength(material);
        return document.materials[materialIndex];
    }

    private ImageData textureImage(GltfDocument document, JsonValue textureInfo) {
        return document.textures.image(textureInfo);
    }

    private Texture texture(GltfDocument document, JsonValue textureInfo, int role) {
        return document.gpuTextures == null ? null : document.textures.texture(document.gpuTextures, textureInfo, role);
    }

    private MaterialAlphaMode alphaMode(String value) {
        if ("BLEND".equals(value)) {
            return MaterialAlphaMode.BLEND;
        }
        if ("MASK".equals(value)) {
            return MaterialAlphaMode.MASK;
        }
        return MaterialAlphaMode.OPAQUE;
    }

    private boolean usesGpuPbrShader() {
        return gpuPbr;
    }

    private Color colorFactor(ArrayView<JsonValue> values, Color fallback) {
        if (values.size() >= 4) {
            return new Color(number(values.get(0), fallback.red()), number(values.get(1), fallback.green()),
                    number(values.get(2), fallback.blue()), number(values.get(3), fallback.alpha()));
        }
        if (values.size() >= 3) {
            return new Color(number(values.get(0), fallback.red()), number(values.get(1), fallback.green()),
                    number(values.get(2), fallback.blue()), fallback.alpha());
        }
        return fallback;
    }

    private Vector3 position(float[] values, int index) {
        int offset = index * 3;
        return new Vector3(values[offset], values[offset + 1], values[offset + 2]);
    }

    private Color multiply(Color left, Color right) {
        return new Color(left.red() * right.red(), left.green() * right.green(), left.blue() * right.blue(),
                left.alpha() * right.alpha());
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private BoundingBox bounds(float[] positions) {
        float minX = positions[0];
        float minY = positions[1];
        float minZ = positions[2];
        float maxX = minX;
        float maxY = minY;
        float maxZ = minZ;
        for (int i = 3; i < positions.length; i += 3) {
            minX = Math.min(minX, positions[i]);
            minY = Math.min(minY, positions[i + 1]);
            minZ = Math.min(minZ, positions[i + 2]);
            maxX = Math.max(maxX, positions[i]);
            maxY = Math.max(maxY, positions[i + 1]);
            maxZ = Math.max(maxZ, positions[i + 2]);
        }
        return BoundingBox.of(new Vector3(minX, minY, minZ), new Vector3(maxX, maxY, maxZ));
    }

    private float[] readFloatAccessor(GltfDocument document, int index, int components) {
        return document.accessors.floats(index, components);
    }

    private float[] readColorAccessor(GltfDocument document, int index) {
        return document.accessors.colors(index);
    }

    private int[] readIntAccessor(GltfDocument document, int index, int components) {
        return document.accessors.integers(index, components);
    }

    private int[] readIndexAccessor(GltfDocument document, int index) {
        return document.accessors.integers(index, 1);
    }

    private byte[] bufferViewBytes(GltfDocument document, int index) {
        return document.accessors.viewBytes(index);
    }

    private Color materialColor(GltfDocument document, int materialIndex) {
        if (materialIndex < 0) {
            return Color.WHITE;
        }
        ArrayView<JsonValue> materials = array(document.root, "materials");
        if (materialIndex >= materials.size()) {
            return Color.WHITE;
        }
        JsonValue material = object(materials.get(materialIndex), "material");
        JsonValue pbr = object(material.get("pbrMetallicRoughness"), "pbr", true);
        ArrayView<JsonValue> factor = pbr != null ? array(pbr, "baseColorFactor") : EMPTY_JSON_ARRAY;
        if (factor.size() >= 4) {
            return new Color(number(factor.get(0), 1.0f), number(factor.get(1), 1.0f),
                    number(factor.get(2), 1.0f), number(factor.get(3), 1.0f));
        }
        return Color.WHITE;
    }

    private static byte[] decodeDataUri(String uri) {
        int comma = uri.indexOf(',');
        if (comma < 0 || uri.indexOf(";base64") < 0) {
            throw new FdxException("Only base64 glTF data URIs are supported");
        }
        return Base64.getDecoder().decode(uri.substring(comma + 1));
    }

    private static JsonValue root(String json) {
        JsonValue parsed = new JsonReader().parse(json);
        if (!parsed.isObject()) {
            throw new FdxException("glTF root must be a JSON object");
        }
        return parsed;
    }

    private static JsonValue object(JsonValue value, String name) {
        return object(value, name, false);
    }

    private static JsonValue object(JsonValue value, String name, boolean nullable) {
        if ((value == null || value.isNull()) && nullable) {
            return null;
        }
        if (value == null || !value.isObject()) {
            throw new FdxException("glTF " + name + " must be an object");
        }
        return value;
    }

    private static ArrayView<JsonValue> array(JsonValue object, String key) {
        JsonValue value = object.get(key);
        if (value == null || value.isNull()) {
            return EMPTY_JSON_ARRAY;
        }
        if (!value.isArray()) {
            throw new FdxException("glTF " + key + " must be an array");
        }
        return value.arrayValues();
    }

    private static String string(JsonValue object, String key, String fallback) {
        JsonValue value = object.get(key);
        return value != null ? value.stringValue(fallback) : fallback;
    }

    private static int integer(JsonValue object, String key, int fallback) {
        return GltfAccessors.integer(object,key,fallback);
    }

    private static int integerValue(JsonValue value, int fallback) {
        return value != null ? GltfAccessors.integerValue(value, "index") : fallback;
    }

    private static boolean bool(JsonValue object, String key, boolean fallback) {
        JsonValue value = object.get(key);
        return value != null ? value.booleanValue(fallback) : fallback;
    }

    private static float number(JsonValue value, float fallback) {
        return value != null ? value.floatValue(fallback) : fallback;
    }

    private static int[] sequence(int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = i;
        }
        return values;
    }

    /**
     * Represents a gltf document.
     *
     * @author xpenatan
     */
    private static final class GltfDocument {
        private final JsonValue root;
        private final byte[] binaryChunk;
        private byte[][] buffers;
        private ImageData[] images;
        private Texture[] gpuTextures;
        private GltfTextures textures;
        private GltfMaterial[] materials;
        private String[] nodeIds;
        private Skin[] skins;
        private Array<Skin> preparedSkins;
        private int[] parents;
        private GltfAccessors accessors;
        private Array<AnimationClip> animations;
        private GeometryBuilder[][] geometry;

        GltfDocument(JsonValue root, byte[] binaryChunk) {
            this.root = root;
            this.binaryChunk = binaryChunk;
        }
    }

    /**
     * Represents a gltf material.
     *
     * @author xpenatan
     */
    private static final class GltfMaterial {
        private final GltfTextures.Binding[] slots = new GltfTextures.Binding[5];
        private float normalScale = 1, occlusionStrength = 1;
        private static final GltfMaterial DEFAULT = new GltfMaterial(Color.WHITE, null, null, 1.0f, 1.0f,
                null, null, Color.BLACK, null, null, null, null, null, null, MaterialAlphaMode.OPAQUE, 0.5f,
                false, ShadingModel.PBR);

        private final Color baseColor;
        private final ImageData baseColorImage;
        private final Texture baseColorTexture;
        private final float metallicFactor;
        private final float roughnessFactor;
        private final ImageData metallicRoughnessImage;
        private final Texture metallicRoughnessTexture;
        private final Color emissiveFactor;
        private final ImageData emissiveImage;
        private final Texture emissiveTexture;
        private final ImageData occlusionImage;
        private final Texture occlusionTexture;
        private final ImageData normalImage;
        private final Texture normalTexture;
        private final MaterialAlphaMode alphaMode;
        private final float alphaCutoff;
        private final boolean doubleSided;
        private final ShadingModel shadingModel;

        GltfMaterial(Color baseColor, ImageData baseColorImage, Texture baseColorTexture, float metallicFactor,
                float roughnessFactor, ImageData metallicRoughnessImage, Texture metallicRoughnessTexture,
                Color emissiveFactor, ImageData emissiveImage, Texture emissiveTexture, ImageData occlusionImage,
                Texture occlusionTexture, ImageData normalImage, Texture normalTexture, MaterialAlphaMode alphaMode,
                float alphaCutoff, boolean doubleSided,
                ShadingModel shadingModel) {
            this.baseColor = baseColor != null ? baseColor : Color.WHITE;
            this.baseColorImage = baseColorImage;
            this.baseColorTexture = baseColorTexture;
            this.metallicFactor = metallicFactor;
            this.roughnessFactor = roughnessFactor;
            this.metallicRoughnessImage = metallicRoughnessImage;
            this.metallicRoughnessTexture = metallicRoughnessTexture;
            this.emissiveFactor = emissiveFactor != null ? emissiveFactor : Color.BLACK;
            this.emissiveImage = emissiveImage;
            this.emissiveTexture = emissiveTexture;
            this.occlusionImage = occlusionImage;
            this.occlusionTexture = occlusionTexture;
            this.normalImage = normalImage;
            this.normalTexture = normalTexture;
            this.alphaMode = alphaMode != null ? alphaMode : MaterialAlphaMode.OPAQUE;
            this.alphaCutoff = alphaCutoff;
            this.doubleSided = doubleSided;
            this.shadingModel = shadingModel != null
                    ? shadingModel : ShadingModel.PBR;
        }
    }

    /**
     * Represents a triangle basis.
     *
     * @author xpenatan
     */
    private static final class TriangleBasis {
        private final Vector3 normal;
        private final Vector3 tangent;
        private final Vector3 bitangent;

        TriangleBasis(Vector3 normal, Vector3 tangent, Vector3 bitangent) {
            this.normal = normal != null ? normal : Vector3.Z;
            this.tangent = tangent != null ? tangent : Vector3.X;
            this.bitangent = bitangent != null ? bitangent : Vector3.Y;
        }
    }

    /**
     * Builds one imported node animation channel.
     *
     * @author xpenatan
     */
    private static final class GltfNodeAnimationBuilder {
        private final String nodeId;
        private final AnimationClip.TransformKeyframe defaults;
        private AnimationSampler translation;
        private AnimationSampler rotation;
        private AnimationSampler scale;

        GltfNodeAnimationBuilder(String nodeId, JsonValue node) {
            this.nodeId = nodeId;
            if (node.get("matrix") != null) {
                throw new FdxException("Animated glTF nodes must use TRS, not matrix: " + nodeId);
            }
            float[] t = vector(node, "translation", new float[] {0, 0, 0});
            float[] r = vector(node, "rotation", new float[] {0, 0, 0, 1});
            float[] s = vector(node, "scale", new float[] {1, 1, 1});
            defaults = AnimationClip.keyframe(0, t[0], t[1], t[2], r[0], r[1], r[2], r[3], s[0], s[1], s[2]);
        }

        void channel(String path, AnimationSampler sampler) {
            AnimationSampler previous = switch (path) {
                case "translation" -> translation;
                case "rotation" -> rotation;
                case "scale" -> scale;
                default -> throw new FdxException("Unsupported glTF animation target path: " + path);
            };
            if (previous != null) {
                throw new FdxException("Duplicate glTF animation target: " + nodeId + "." + path);
            }
            switch (path) {
                case "translation" -> translation = sampler;
                case "rotation" -> rotation = sampler;
                case "scale" -> scale = sampler;
            }
        }

        AnimationClip.NodeTransformChannel build() {
            return AnimationClip.sampledTransform(nodeId, defaults, translation, rotation, scale);
        }

        private static float[] vector(JsonValue object, String key, float[] fallback) {
            if (object.get(key) == null) {
                return fallback;
            }
            ArrayView<JsonValue> values = array(object, key);
            if (values.size() != fallback.length) {
                throw new FdxException("glTF node " + key + " component count mismatch");
            }
            float[] result = new float[fallback.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = values.get(i).floatValue();
            }
            return result;
        }
    }

    /**
     * Builds geometry instances and related output.
     *
     * @author xpenatan
     */
    private static final class GeometryBuilder {
        private Mesh.PositionColor3DPreparation preparedMesh;
        private boolean extended;
        private final FloatList texCoords1 = new FloatList();
        private final FloatList tangents = new FloatList();
        private final FloatList positions = new FloatList();
        private final FloatList normals = new FloatList();
        private final FloatList texCoords = new FloatList();
        private final FloatList colors = new FloatList();
        private final FloatList bakedColors = new FloatList();
        private final FloatList pbr = new FloatList();
        private final FloatList bakedPbr = new FloatList();
        private final FloatList emissive = new FloatList();
        private final FloatList bakedEmissive = new FloatList();
        private final IntList joints = new IntList();
        private final FloatList weights = new FloatList();
        private GltfMaterial material;
        private boolean mixedMaterials;
        private boolean doubleSided;
        private boolean hasSkinning;

        void add(Vector3 position, Vector3 normal, float u, float v, Color color, float ao, float metallic,
                float roughness, Color emissiveColor, Color bakedColor, float bakedAo, float bakedMetallic,
                float bakedRoughness, Color bakedEmissiveColor, int[] sourceJoints, float[] sourceWeights,
                int sourceIndex) {
            positions.add(position.x());
            positions.add(position.y());
            positions.add(position.z());
            Vector3 safeNormal = normal != null ? normal.normalize() : Vector3.Z;
            normals.add(safeNormal.x());
            normals.add(safeNormal.y());
            normals.add(safeNormal.z());
            texCoords.add(u);
            texCoords.add(v);
            Color safeColor = color != null ? color : Color.WHITE;
            colors.add(safeColor.red());
            colors.add(safeColor.green());
            colors.add(safeColor.blue());
            colors.add(safeColor.alpha());
            Color safeBakedColor = bakedColor != null ? bakedColor : safeColor;
            bakedColors.add(safeBakedColor.red());
            bakedColors.add(safeBakedColor.green());
            bakedColors.add(safeBakedColor.blue());
            bakedColors.add(safeBakedColor.alpha());
            pbr.add(ao);
            pbr.add(metallic);
            pbr.add(roughness);
            bakedPbr.add(bakedAo);
            bakedPbr.add(bakedMetallic);
            bakedPbr.add(bakedRoughness);
            Color safeEmissive = emissiveColor != null ? emissiveColor : Color.BLACK;
            emissive.add(safeEmissive.red());
            emissive.add(safeEmissive.green());
            emissive.add(safeEmissive.blue());
            Color safeBakedEmissive = bakedEmissiveColor != null ? bakedEmissiveColor : safeEmissive;
            bakedEmissive.add(safeBakedEmissive.red());
            bakedEmissive.add(safeBakedEmissive.green());
            bakedEmissive.add(safeBakedEmissive.blue());
            if (sourceJoints != null && sourceWeights != null) {
                int influenceOffset = sourceIndex * 4;
                joints.add(sourceJoints[influenceOffset]);
                joints.add(sourceJoints[influenceOffset + 1]);
                joints.add(sourceJoints[influenceOffset + 2]);
                joints.add(sourceJoints[influenceOffset + 3]);
                weights.add(sourceWeights[influenceOffset]);
                weights.add(sourceWeights[influenceOffset + 1]);
                weights.add(sourceWeights[influenceOffset + 2]);
                weights.add(sourceWeights[influenceOffset + 3]);
                hasSkinning = true;
            }
        }

        void material(GltfMaterial material) {
            if (material == null) {
                return;
            }
            if (this.material == null) {
                this.material = material;
            }
            else if (this.material != material) {
                mixedMaterials = true;
            }
        }

        int vertexCount() {
            return positions.size() / 3;
        }

        float[] positions() {
            return positions.toArray();
        }

        float[] normals() {
            return normals.toArray();
        }

        float[] texCoords() {
            return texCoords.toArray();
        }

        float[] colors() {
            return colors.toArray();
        }

        float[] bakedColors() {
            return bakedColors.toArray();
        }

        float[] pbr() {
            return pbr.toArray();
        }

        float[] bakedPbr() {
            return bakedPbr.toArray();
        }

        float[] emissive() {
            return emissive.toArray();
        }

        float[] bakedEmissive() {
            return bakedEmissive.toArray();
        }

        boolean hasSkinning() {
            return hasSkinning;
        }

        int[] joints() {
            return joints.toArray();
        }

        float[] weights() {
            return weights.toArray();
        }
    }

    /**
     * Represents an int list.
     *
     * @author xpenatan
     */
    private static final class IntList {
        private int[] values = new int[64];
        private int size;

        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    /**
     * Represents a float list.
     *
     * @author xpenatan
     */
    private static final class FloatList {
        private float[] values = new float[64];
        private int size;

        void add(float value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        int size() {
            return size;
        }

        float[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

}
