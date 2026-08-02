package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.IntMap;
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
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureWrap;
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
    private static final int GLTF_CLAMP_TO_EDGE = 33071;
    private static final int GLTF_MIRRORED_REPEAT = 33648;
    private static final int GLTF_REPEAT = 10497;
    private static final ArrayView<JsonValue> EMPTY_JSON_ARRAY = new Array<JsonValue>(0).view();

    private final GraphicsContext graphics;

    GltfModelLoader(GraphicsContext graphics) {
        this.graphics = graphics;
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
        try {
            final GltfDocument document = loadImages(file, loadDocument(file, file.readBytes().get()));
            context.completeOnUpdate(new FdxTask<Model>() {
                @Override
                public Model run() {
                    return buildModel(descriptor.path(), document);
                }
            }).onSuccess(future::complete).onFailure(future::completeExceptionally);
        } catch (Throwable error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    Model loadModelBytes(String path, byte[] bytes) {
        return buildModel(path, loadDocument(null, bytes));
    }

    private GltfDocument loadDocument(FileHandle file, byte[] bytes) {
        final GltfDocument document = parseDocument(bytes);
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
            else {
                if (file == null) {
                    throw new FdxException("External glTF buffers require a file handle");
                }
                document.buffers[i] = file.parent().child(uri).readBytes().get();
            }
        }
        return document;
    }

    private GltfDocument loadImages(FileHandle file, final GltfDocument document) {
        ArrayView<JsonValue> images = array(document.root, "images");
        if (images.isEmpty()) {
            document.images = new ImageData[0];
            return document;
        }
        document.images = new ImageData[images.size()];
        for (int i = 0; i < images.size(); i++) {
            JsonValue image = object(images.get(i), "image");
            String uri = string(image, "uri", null);
            if (uri != null && uri.startsWith("data:")) {
                document.images[i] = ImageAssetLoader.decode(decodeDataUri(uri));
            }
            else if (uri != null && uri.length() > 0) {
                FileHandle imageFile = file.parent().child(uri);
                document.images[i] = ImageAssetLoader.decode(imageFile.path(), imageFile.readBytes().get());
            }
            else {
                int bufferView = integer(image, "bufferView", -1);
                if (bufferView < 0) {
                    throw new FdxException("glTF image has no uri or bufferView");
                }
                document.images[i] = ImageAssetLoader.decode(bufferViewBytes(document, bufferView));
            }
        }
        return document;
    }

    private GltfDocument parseDocument(byte[] bytes) {
        if (bytes.length >= 12 && ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC) {
            return parseGlb(bytes);
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        return new GltfDocument(root(json), null);
    }

    private GltfDocument parseGlb(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        int length = buffer.getInt();
        if (magic != GLB_MAGIC || version != 2 || length > bytes.length) {
            throw new FdxException("Invalid GLB header");
        }
        JsonValue root = null;
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
                root = root(new String(chunk, StandardCharsets.UTF_8).trim());
            }
            else if (chunkType == GLB_BIN_CHUNK) {
                binaryChunk = chunk;
            }
        }
        if (root == null) {
            throw new FdxException("GLB did not contain a JSON chunk");
        }
        return new GltfDocument(root, binaryChunk);
    }

    private Model buildModel(String path, GltfDocument document) {
        uploadTextures(path, document);
        ArrayView<JsonValue> meshes = array(document.root, "meshes");
        if (meshes.isEmpty()) {
            throw new FdxException("glTF model contains no meshes: " + path);
        }
        document.nodeIds = nodeIds(document);
        Array<Skin> loadedSkins = skins(document);
        document.skins = loadedSkins.toArray(new Skin[0]);
        Array<ModelNode> nodes = new Array<ModelNode>();
        Array<Material> materials = new Array<Material>();
        Array<Mesh> meshResources = new Array<Mesh>();
        ArrayView<JsonValue> sceneNodes = sceneNodes(document);
        if (sceneNodes.isEmpty()) {
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
        return new DefaultModel(nodes, materials, animations(document), loadedSkins, meshResources);
    }

    private void uploadTextures(String path, GltfDocument document) {
        if (document.images == null || document.images.length == 0 || document.gpuTextures != null) {
            return;
        }
        ArrayView<JsonValue> textures = array(document.root, "textures");
        document.gpuTextures = new Texture[textures.size()];
        for (int i = 0; i < textures.size(); i++) {
            JsonValue texture = object(textures.get(i), "texture");
            int source = integer(texture, "source", -1);
            if (source < 0 || source >= document.images.length) {
                continue;
            }
            ImageData image = document.images[source];
            if (image == null) {
                continue;
            }
            Texture gpuTexture = graphics.device().createTexture(TextureDescriptor.rgba8(path + " texture " + i,
                    image.width(), image.height()).wrap(wrapS(document, texture), wrapT(document, texture)));
            ByteBuffer rgba = image.rgba().duplicate();
            rgba.clear();
            graphics.device().writeTexture(gpuTexture, rgba);
            document.gpuTextures[i] = gpuTexture;
        }
    }

    private PbrMaterial material(String id, GltfMaterial source) {
        GltfMaterial material = source != null ? source : GltfMaterial.DEFAULT;
        return new PbrMaterial(id)
                .baseColor(material.baseColor)
                .baseColorTexture(material.baseColorTexture)
                .metallicFactor(material.metallicFactor)
                .roughnessFactor(material.roughnessFactor)
                .metallicRoughnessTexture(material.metallicRoughnessTexture)
                .normalTexture(material.normalTexture)
                .occlusionTexture(material.occlusionTexture)
                .emissiveFactor(material.emissiveFactor)
                .emissiveTexture(material.emissiveTexture)
                .alphaMode(material.alphaMode)
                .alphaCutoff(material.alphaCutoff)
                .doubleSided(material.doubleSided);
    }

    private String[] nodeIds(GltfDocument document) {
        ArrayView<JsonValue> nodes = array(document.root, "nodes");
        String[] ids = new String[nodes.size()];
        ObjectMap<String, Integer> used = new ObjectMap<String, Integer>();
        for (int i = 0; i < nodes.size(); i++) {
            JsonValue node = object(nodes.get(i), "node");
            String base = string(node, "name", "");
            if (base.length() == 0) {
                base = "node-" + i;
            }
            String id = base;
            if (used.containsKey(id)) {
                id = base + "-" + i;
            }
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
        float[] values = readFloatAccessor(document, accessorIndex, Matrix4.VALUE_COUNT);
        if (values.length != count * Matrix4.VALUE_COUNT) {
            throw new FdxException("glTF inverseBindMatrices count mismatch");
        }
        for (int i = 0; i < count; i++) {
            float[] matrix = new float[Matrix4.VALUE_COUNT];
            System.arraycopy(values, i * Matrix4.VALUE_COUNT, matrix, 0, Matrix4.VALUE_COUNT);
            matrices[i] = Matrix4.of(matrix);
        }
        return matrices;
    }

    private int[] parentNodes(GltfDocument document) {
        ArrayView<JsonValue> nodes = array(document.root, "nodes");
        int[] parents = new int[nodes.size()];
        Arrays.fill(parents, -1);
        for (int i = 0; i < nodes.size(); i++) {
            ArrayView<JsonValue> children = array(object(nodes.get(i), "node"), "children");
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                int child = integerValue(children.get(childIndex), -1);
                if (child >= 0 && child < parents.length) {
                    parents[child] = i;
                }
            }
        }
        return parents;
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
        ModelNode modelNode = new ModelNode(nodeId(document, nodeIndex)).localTransform(nodeTransform(node));
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
                sourceWeights = readFloatAccessor(document, weightAccessor, 4);
            }
            GltfMaterial material = material(document, integer(primitive, "material", -1));
            geometry.material(material);
            geometry.doubleSided |= material.doubleSided;
            int[] indices = primitive.get("indices") != null
                    ? readIndexAccessor(document, integer(primitive, "indices", -1))
                    : sequence(sourcePositions.length / 3);
            appendPrimitive(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, indices,
                    sourceJoints, sourceWeights, material, Matrix4.IDENTITY);
        PbrMaterial pbrMaterial = material(path + " material " + meshIndex + "." + primitiveIndex, geometry.material)
                .doubleSided(geometry.doubleSided);
        materials.add(pbrMaterial);
        boolean retainSourceData = !usesGpuPbrShader() || geometry.hasSkinning();
        float[] positions = geometry.positions();
        float[] bakedColors = retainSourceData ? geometry.bakedColors() : null;
        float[] bakedPbr = retainSourceData ? geometry.bakedPbr() : null;
        float[] bakedEmissive = retainSourceData ? geometry.bakedEmissive() : null;
        Mesh mesh = Mesh.positionColor3D(graphics, path + " mesh " + meshIndex + "." + primitiveIndex, positions,
                geometry.colors(), bakedColors, geometry.normals(), geometry.texCoords(), geometry.pbr(), bakedPbr,
                geometry.emissive(), bakedEmissive, geometry.hasSkinning() ? geometry.joints() : null,
                geometry.hasSkinning() ? geometry.weights() : null, bounds(positions), retainSourceData);
        meshResources.add(mesh);
        MeshPart meshPart = new MeshPart(path + " part " + meshIndex + "." + primitiveIndex, mesh, null, 0,
                mesh.vertexCount());
        return geometry.hasSkinning()
                ? new ModelNodePart(meshPart, pbrMaterial, skin, geometry.joints(), geometry.weights())
                : new ModelNodePart(meshPart, pbrMaterial);
    }

    private void appendPrimitive(GeometryBuilder geometry, float[] sourcePositions, float[] sourceNormals,
            float[] sourceTexCoords, float[] sourceColors, int[] indices, int[] sourceJoints, float[] sourceWeights,
            GltfMaterial material, Matrix4 transform) {
        int vertexCount = sourcePositions.length / 3;
        int colorComponents = sourceColors != null && sourceColors.length == vertexCount * 3 ? 3 : 4;
        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];
            validateGltfIndex(i0, vertexCount);
            validateGltfIndex(i1, vertexCount);
            validateGltfIndex(i2, vertexCount);
            TriangleBasis basis = triangleBasis(sourcePositions, sourceNormals, sourceTexCoords, i0, i1, i2);
            appendVertex(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, colorComponents,
                    sourceJoints, sourceWeights, i0, material, transform, basis);
            appendVertex(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, colorComponents,
                    sourceJoints, sourceWeights, i1, material, transform, basis);
            appendVertex(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, colorComponents,
                    sourceJoints, sourceWeights, i2, material, transform, basis);
        }
    }

    private void appendVertex(GeometryBuilder geometry, float[] sourcePositions, float[] sourceNormals,
            float[] sourceTexCoords, float[] sourceColors, int colorComponents, int[] sourceJoints,
            float[] sourceWeights, int index, GltfMaterial material, Matrix4 transform, TriangleBasis basis) {
        Vector3 position = position(sourcePositions, index);
        Vector3 normal = sourceNormals != null ? position(sourceNormals, index) : basis.normal;
        float u = 0.0f;
        float v = 0.0f;
        if (sourceTexCoords != null) {
            int texCoordOffset = index * 2;
            u = sourceTexCoords[texCoordOffset];
            v = sourceTexCoords[texCoordOffset + 1];
        }
        if (!usesGpuPbrShader() && material.normalImage != null && sourceTexCoords != null) {
            normal = normalFromTexture(material.normalImage, u, v, normal, basis.tangent, basis.bitangent);
        }
        Color color = material.baseColor;
        if (sourceColors != null) {
            int colorOffset = index * colorComponents;
            Color vertexColor = new Color(sourceColors[colorOffset], sourceColors[colorOffset + 1],
                    sourceColors[colorOffset + 2], colorComponents > 3 ? sourceColors[colorOffset + 3] : 1.0f);
            color = multiply(color, vertexColor);
        }
        Color bakedColor = color;
        if (material.baseColorImage != null && sourceTexCoords != null) {
            bakedColor = multiply(bakedColor, srgbToLinear(sample(material.baseColorImage, u, v)));
        }
        float ao = 1.0f;
        float bakedAo = material.occlusionImage != null && sourceTexCoords != null ? sample(material.occlusionImage, u, v).red()
                : 1.0f;
        float metallic = material.metallicFactor;
        float roughness = material.roughnessFactor;
        float bakedMetallic = metallic;
        float bakedRoughness = roughness;
        if (material.metallicRoughnessImage != null && sourceTexCoords != null) {
            Color mr = sample(material.metallicRoughnessImage, u, v);
            bakedRoughness *= mr.green();
            bakedMetallic *= mr.blue();
        }
        Color emissive = material.emissiveFactor;
        Color bakedEmissive = emissive;
        if (material.emissiveImage != null && sourceTexCoords != null) {
            bakedEmissive = multiply(bakedEmissive, srgbToLinear(sample(material.emissiveImage, u, v)));
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
            return EMPTY_JSON_ARRAY;
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
                int nodeIndex = integer(target, "node", -1);
                if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
                    continue;
                }
                String path = string(target, "path", "");
                JsonValue sampler = object(samplers.get(integer(channel, "sampler", -1)), "animation sampler");
                String interpolation = string(sampler, "interpolation", "LINEAR");
                if (!"LINEAR".equals(interpolation)) {
                    throw new FdxException("Only LINEAR glTF animation interpolation is supported");
                }
                float[] times = readFloatAccessor(document, integer(sampler, "input", -1), 1);
                float[] values = readFloatAccessor(document, integer(sampler, "output", -1),
                        animationComponents(path));
                if (times.length > 0) {
                    duration = Math.max(duration, times[times.length - 1]);
                }
                GltfNodeAnimationBuilder builder = builders.get(nodeIndex);
                if (builder == null) {
                    builder = new GltfNodeAnimationBuilder(nodeId(document, nodeIndex),
                            object(nodes.get(nodeIndex), "node"));
                    builders.put(nodeIndex, builder);
                }
                builder.channel(path, times, values);
            }
            Array<AnimationClip.NodeTransformChannel> nodeChannels =
                    new Array<AnimationClip.NodeTransformChannel>();
            for (GltfNodeAnimationBuilder builder : builders.values()) {
                nodeChannels.add(builder.build());
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

    private Matrix4 nodeTransform(JsonValue node) {
        ArrayView<JsonValue> matrix = array(node, "matrix");
        if (matrix.size() == Matrix4.VALUE_COUNT) {
            float[] values = new float[Matrix4.VALUE_COUNT];
            for (int i = 0; i < values.length; i++) {
                values[i] = number(matrix.get(i), i % 5 == 0 ? 1.0f : 0.0f);
            }
            return Matrix4.of(values);
        }
        ArrayView<JsonValue> translation = array(node, "translation");
        ArrayView<JsonValue> rotation = array(node, "rotation");
        ArrayView<JsonValue> scale = array(node, "scale");
        Matrix4 translationMatrix = translation.size() >= 3
                ? Matrix4.translation(number(translation.get(0), 0.0f), number(translation.get(1), 0.0f),
                number(translation.get(2), 0.0f))
                : Matrix4.IDENTITY;
        Matrix4 rotationMatrix = rotation.size() >= 4
                ? Matrix4.rotationQuaternion(number(rotation.get(0), 0.0f), number(rotation.get(1), 0.0f),
                number(rotation.get(2), 0.0f), number(rotation.get(3), 1.0f))
                : Matrix4.IDENTITY;
        Matrix4 scaleMatrix = scale.size() >= 3
                ? Matrix4.scale(number(scale.get(0), 1.0f), number(scale.get(1), 1.0f), number(scale.get(2), 1.0f))
                : Matrix4.IDENTITY;
        return translationMatrix.multiply(rotationMatrix).multiply(scaleMatrix);
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
                "baseColorTexture", true) : null);
        float metallicFactor = pbr != null ? number(pbr.get("metallicFactor"), 1.0f) : 1.0f;
        float roughnessFactor = pbr != null ? number(pbr.get("roughnessFactor"), 1.0f) : 1.0f;
        ImageData metallicRoughnessImage = textureImage(document, pbr != null ? object(
                pbr.get("metallicRoughnessTexture"), "metallicRoughnessTexture", true) : null);
        Texture metallicRoughnessTexture = texture(document, pbr != null ? object(
                pbr.get("metallicRoughnessTexture"), "metallicRoughnessTexture", true) : null);
        Color emissiveFactor = colorFactor(array(material, "emissiveFactor"), Color.BLACK);
        ImageData emissiveImage = textureImage(document, object(material.get("emissiveTexture"), "emissiveTexture",
                true));
        Texture emissiveTexture = texture(document, object(material.get("emissiveTexture"), "emissiveTexture",
                true));
        ImageData occlusionImage = textureImage(document, object(material.get("occlusionTexture"), "occlusionTexture",
                true));
        Texture occlusionTexture = texture(document, object(material.get("occlusionTexture"), "occlusionTexture",
                true));
        ImageData normalImage = textureImage(document, object(material.get("normalTexture"), "normalTexture", true));
        Texture normalTexture = texture(document, object(material.get("normalTexture"), "normalTexture", true));
        MaterialAlphaMode alphaMode = alphaMode(string(material, "alphaMode", "OPAQUE"));
        float alphaCutoff = number(material.get("alphaCutoff"), 0.5f);
        boolean doubleSided = bool(material, "doubleSided", false);
        document.materials[materialIndex] = new GltfMaterial(baseColor, baseColorImage, baseColorTexture,
                metallicFactor, roughnessFactor, metallicRoughnessImage, metallicRoughnessTexture,
                emissiveFactor, emissiveImage, emissiveTexture, occlusionImage, occlusionTexture,
                normalImage, normalTexture, alphaMode, alphaCutoff, doubleSided);
        return document.materials[materialIndex];
    }

    private ImageData textureImage(GltfDocument document, JsonValue textureInfo) {
        if (textureInfo == null) {
            return null;
        }
        int textureIndex = integer(textureInfo, "index", -1);
        ArrayView<JsonValue> textures = array(document.root, "textures");
        if (textureIndex < 0 || textureIndex >= textures.size()) {
            return null;
        }
        JsonValue texture = object(textures.get(textureIndex), "texture");
        int source = integer(texture, "source", -1);
        if (source < 0 || document.images == null || source >= document.images.length) {
            return null;
        }
        return document.images[source];
    }

    private Texture texture(GltfDocument document, JsonValue textureInfo) {
        if (textureInfo == null || document.gpuTextures == null) {
            return null;
        }
        int textureIndex = integer(textureInfo, "index", -1);
        if (textureIndex < 0 || textureIndex >= document.gpuTextures.length) {
            return null;
        }
        return document.gpuTextures[textureIndex];
    }

    private TextureWrap wrapS(GltfDocument document, JsonValue texture) {
        return samplerWrap(document, texture, "wrapS");
    }

    private TextureWrap wrapT(GltfDocument document, JsonValue texture) {
        return samplerWrap(document, texture, "wrapT");
    }

    private TextureWrap samplerWrap(GltfDocument document, JsonValue texture, String key) {
        ArrayView<JsonValue> samplers = array(document.root, "samplers");
        int samplerIndex = integer(texture, "sampler", -1);
        if (samplerIndex < 0 || samplerIndex >= samplers.size()) {
            return TextureWrap.REPEAT;
        }
        JsonValue sampler = object(samplers.get(samplerIndex), "sampler");
        int wrap = integer(sampler, key, GLTF_REPEAT);
        if (wrap == GLTF_CLAMP_TO_EDGE) {
            return TextureWrap.CLAMP_TO_EDGE;
        }
        if (wrap == GLTF_MIRRORED_REPEAT) {
            return TextureWrap.MIRRORED_REPEAT;
        }
        return TextureWrap.REPEAT;
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
        String providerId = graphics.providerId().value();
        return "gl".equals(providerId) || "gles".equals(providerId) || "webgl".equals(providerId)
                || "wgpu".equals(providerId) || "vulkan".equals(providerId);
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

    private Vector3 normalFromTexture(ImageData image, float u, float v, Vector3 normal, Vector3 tangent,
            Vector3 bitangent) {
        Color sample = sample(image, u, v);
        Vector3 mapped = new Vector3(sample.red() * 2.0f - 1.0f, sample.green() * 2.0f - 1.0f,
                sample.blue() * 2.0f - 1.0f).normalize();
        return tangent.scale(mapped.x()).add(bitangent.scale(mapped.y())).add(normal.normalize().scale(mapped.z()))
                .normalize();
    }

    private Color sample(ImageData image, float u, float v) {
        if (image == null) {
            return Color.WHITE;
        }
        float wrappedU = wrap(u);
        float wrappedV = wrap(v);
        float x = wrappedU * (image.width() - 1);
        float y = wrappedV * (image.height() - 1);
        int x0 = Math.min(image.width() - 1, Math.max(0, (int)Math.floor(x)));
        int y0 = Math.min(image.height() - 1, Math.max(0, (int)Math.floor(y)));
        int x1 = Math.min(image.width() - 1, x0 + 1);
        int y1 = Math.min(image.height() - 1, y0 + 1);
        float tx = x - x0;
        float ty = y - y0;
        ByteBuffer rgba = image.rgba().duplicate();
        Color c00 = texel(rgba, image.width(), x0, y0);
        Color c10 = texel(rgba, image.width(), x1, y0);
        Color c01 = texel(rgba, image.width(), x0, y1);
        Color c11 = texel(rgba, image.width(), x1, y1);
        return lerp(lerp(c00, c10, tx), lerp(c01, c11, tx), ty);
    }

    private Color texel(ByteBuffer rgba, int width, int x, int y) {
        int offset = (y * width + x) * 4;
        return new Color((rgba.get(offset) & 0xff) / 255.0f,
                (rgba.get(offset + 1) & 0xff) / 255.0f,
                (rgba.get(offset + 2) & 0xff) / 255.0f,
                (rgba.get(offset + 3) & 0xff) / 255.0f);
    }

    private Color lerp(Color left, Color right, float t) {
        float inv = 1.0f - t;
        return new Color(left.red() * inv + right.red() * t,
                left.green() * inv + right.green() * t,
                left.blue() * inv + right.blue() * t,
                left.alpha() * inv + right.alpha() * t);
    }

    private float wrap(float value) {
        float wrapped = value - (float)Math.floor(value);
        return wrapped < 0.0f ? wrapped + 1.0f : wrapped;
    }

    private Color multiply(Color left, Color right) {
        return new Color(left.red() * right.red(), left.green() * right.green(), left.blue() * right.blue(),
                left.alpha() * right.alpha());
    }

    private Color srgbToLinear(Color color) {
        return new Color(srgbToLinear(color.red()), srgbToLinear(color.green()), srgbToLinear(color.blue()),
                color.alpha());
    }

    private float srgbToLinear(float value) {
        return (float)Math.pow(Math.max(value, 0.0f), 2.2f);
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

    private float[] readFloatAccessor(GltfDocument document, int accessorIndex, int expectedComponents) {
        Accessor accessor = accessor(document, accessorIndex);
        if (accessor.components != expectedComponents) {
            throw new FdxException("glTF accessor component count mismatch");
        }
        float[] values = new float[accessor.count * accessor.components];
        for (int i = 0; i < accessor.count; i++) {
            int elementOffset = accessor.byteOffset + i * accessor.byteStride;
            for (int c = 0; c < accessor.components; c++) {
                values[i * accessor.components + c] = readComponent(accessor.buffer, elementOffset
                        + c * componentSize(accessor.componentType), accessor.componentType, accessor.normalized);
            }
        }
        return values;
    }

    private float[] readColorAccessor(GltfDocument document, int accessorIndex) {
        Accessor accessor = accessor(document, accessorIndex);
        if (accessor.components != 3 && accessor.components != 4) {
            throw new FdxException("glTF COLOR_0 must be VEC3 or VEC4");
        }
        float[] values = new float[accessor.count * accessor.components];
        for (int i = 0; i < accessor.count; i++) {
            int elementOffset = accessor.byteOffset + i * accessor.byteStride;
            for (int c = 0; c < accessor.components; c++) {
                values[i * accessor.components + c] = readComponent(accessor.buffer, elementOffset
                        + c * componentSize(accessor.componentType), accessor.componentType, true);
            }
        }
        return values;
    }

    private int[] readIntAccessor(GltfDocument document, int accessorIndex, int expectedComponents) {
        Accessor accessor = accessor(document, accessorIndex);
        if (accessor.components != expectedComponents) {
            throw new FdxException("glTF integer accessor component count mismatch");
        }
        int[] values = new int[accessor.count * accessor.components];
        for (int i = 0; i < accessor.count; i++) {
            int elementOffset = accessor.byteOffset + i * accessor.byteStride;
            for (int c = 0; c < accessor.components; c++) {
                values[i * accessor.components + c] = readIndex(accessor.buffer,
                        elementOffset + c * componentSize(accessor.componentType), accessor.componentType);
            }
        }
        return values;
    }

    private int[] readIndexAccessor(GltfDocument document, int accessorIndex) {
        Accessor accessor = accessor(document, accessorIndex);
        if (accessor.components != 1) {
            throw new FdxException("glTF index accessor must be SCALAR");
        }
        int[] values = new int[accessor.count];
        for (int i = 0; i < accessor.count; i++) {
            int offset = accessor.byteOffset + i * accessor.byteStride;
            values[i] = readIndex(accessor.buffer, offset, accessor.componentType);
        }
        return values;
    }

    private Accessor accessor(GltfDocument document, int accessorIndex) {
        JsonValue accessor = object(array(document.root, "accessors").get(accessorIndex), "accessor");
        int bufferViewIndex = integer(accessor, "bufferView", -1);
        if (bufferViewIndex < 0) {
            throw new FdxException("Sparse or bufferless glTF accessors are not supported");
        }
        JsonValue bufferView = object(array(document.root, "bufferViews").get(bufferViewIndex), "bufferView");
        int bufferIndex = integer(bufferView, "buffer", 0);
        byte[] buffer = document.buffers[bufferIndex];
        int componentType = integer(accessor, "componentType", -1);
        int components = components(string(accessor, "type", ""));
        int count = integer(accessor, "count", 0);
        int viewOffset = integer(bufferView, "byteOffset", 0);
        int accessorOffset = integer(accessor, "byteOffset", 0);
        int byteStride = integer(bufferView, "byteStride", componentSize(componentType) * components);
        boolean normalized = bool(accessor, "normalized", false);
        return new Accessor(buffer, viewOffset + accessorOffset, byteStride, componentType, components, count,
                normalized);
    }

    private byte[] bufferViewBytes(GltfDocument document, int bufferViewIndex) {
        JsonValue bufferView = object(array(document.root, "bufferViews").get(bufferViewIndex), "bufferView");
        int bufferIndex = integer(bufferView, "buffer", 0);
        int byteOffset = integer(bufferView, "byteOffset", 0);
        int byteLength = integer(bufferView, "byteLength", -1);
        if (byteLength < 0) {
            throw new FdxException("glTF bufferView byteLength is required");
        }
        byte[] source = document.buffers[bufferIndex];
        if (byteOffset < 0 || byteOffset + byteLength > source.length) {
            throw new FdxException("glTF bufferView range is invalid");
        }
        byte[] result = new byte[byteLength];
        System.arraycopy(source, byteOffset, result, 0, byteLength);
        return result;
    }

    private float readComponent(byte[] bytes, int offset, int componentType, boolean normalized) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (componentType == 5126) {
            return buffer.getFloat(offset);
        }
        if (componentType == 5121) {
            int value = bytes[offset] & 0xff;
            return normalized ? value / 255.0f : value;
        }
        if (componentType == 5123) {
            int value = buffer.getShort(offset) & 0xffff;
            return normalized ? value / 65535.0f : value;
        }
        if (componentType == 5120) {
            int value = bytes[offset];
            return normalized ? Math.max(value / 127.0f, -1.0f) : value;
        }
        if (componentType == 5122) {
            int value = buffer.getShort(offset);
            return normalized ? Math.max(value / 32767.0f, -1.0f) : value;
        }
        throw new FdxException("Unsupported glTF accessor component type: " + componentType);
    }

    private int readIndex(byte[] bytes, int offset, int componentType) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (componentType == 5121) {
            return bytes[offset] & 0xff;
        }
        if (componentType == 5123) {
            return buffer.getShort(offset) & 0xffff;
        }
        if (componentType == 5125) {
            long value = buffer.getInt(offset) & 0xffffffffL;
            if (value > Integer.MAX_VALUE) {
                throw new FdxException("glTF index exceeds Java int range");
            }
            return (int) value;
        }
        throw new FdxException("Unsupported glTF index component type: " + componentType);
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

    private int components(String type) {
        if ("SCALAR".equals(type)) {
            return 1;
        }
        if ("VEC2".equals(type)) {
            return 2;
        }
        if ("VEC3".equals(type)) {
            return 3;
        }
        if ("VEC4".equals(type)) {
            return 4;
        }
        if ("MAT4".equals(type)) {
            return Matrix4.VALUE_COUNT;
        }
        throw new FdxException("Unsupported glTF accessor type: " + type);
    }

    private int componentSize(int componentType) {
        if (componentType == 5120 || componentType == 5121) {
            return 1;
        }
        if (componentType == 5122 || componentType == 5123) {
            return 2;
        }
        if (componentType == 5125 || componentType == 5126) {
            return 4;
        }
        throw new FdxException("Unsupported glTF component type: " + componentType);
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
        JsonValue value = object.get(key);
        return value != null ? value.intValue(fallback) : fallback;
    }

    private static int integerValue(JsonValue value, int fallback) {
        return value != null ? value.intValue(fallback) : fallback;
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
        private GltfMaterial[] materials;
        private String[] nodeIds;
        private Skin[] skins;

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
        private static final GltfMaterial DEFAULT = new GltfMaterial(Color.WHITE, null, null, 1.0f, 1.0f,
                null, null, Color.BLACK, null, null, null, null, null, null, MaterialAlphaMode.OPAQUE, 0.5f,
                false);

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

        GltfMaterial(Color baseColor, ImageData baseColorImage, Texture baseColorTexture, float metallicFactor,
                float roughnessFactor, ImageData metallicRoughnessImage, Texture metallicRoughnessTexture,
                Color emissiveFactor, ImageData emissiveImage, Texture emissiveTexture, ImageData occlusionImage,
                Texture occlusionTexture, ImageData normalImage, Texture normalTexture, MaterialAlphaMode alphaMode,
                float alphaCutoff, boolean doubleSided) {
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
        private final float[] baseTranslation;
        private final float[] baseRotation;
        private final float[] baseScale;
        private float[] translationTimes;
        private float[] translationValues;
        private float[] rotationTimes;
        private float[] rotationValues;
        private float[] scaleTimes;
        private float[] scaleValues;

        GltfNodeAnimationBuilder(String nodeId, JsonValue node) {
            this.nodeId = nodeId;
            baseTranslation = vector(node, "translation", 3, new float[] {0.0f, 0.0f, 0.0f});
            baseRotation = vector(node, "rotation", 4, new float[] {0.0f, 0.0f, 0.0f, 1.0f});
            baseScale = vector(node, "scale", 3, new float[] {1.0f, 1.0f, 1.0f});
        }

        void channel(String path, float[] times, float[] values) {
            if ("translation".equals(path)) {
                translationTimes = times;
                translationValues = values;
            }
            else if ("rotation".equals(path)) {
                rotationTimes = times;
                rotationValues = values;
            }
            else if ("scale".equals(path)) {
                scaleTimes = times;
                scaleValues = values;
            }
        }

        AnimationClip.NodeTransformChannel build() {
            float[] times = unionTimes();
            AnimationClip.TransformKeyframe[] keyframes = new AnimationClip.TransformKeyframe[times.length];
            float[] translation = new float[3];
            float[] rotation = new float[4];
            float[] scale = new float[3];
            for (int i = 0; i < times.length; i++) {
                float time = times[i];
                sample3(translationTimes, translationValues, baseTranslation, time, translation);
                sample4(rotationTimes, rotationValues, baseRotation, time, rotation);
                sample3(scaleTimes, scaleValues, baseScale, time, scale);
                keyframes[i] = AnimationClip.keyframe(time, translation[0], translation[1], translation[2],
                        rotation[0], rotation[1], rotation[2], rotation[3], scale[0], scale[1], scale[2]);
            }
            return AnimationClip.nodeTransform(nodeId, keyframes);
        }

        private float[] unionTimes() {
            FloatList times = new FloatList();
            addTimes(times, translationTimes);
            addTimes(times, rotationTimes);
            addTimes(times, scaleTimes);
            if (times.size() == 0) {
                times.add(0.0f);
            }
            float[] values = times.toArray();
            Arrays.sort(values);
            int uniqueCount = 0;
            for (int i = 0; i < values.length; i++) {
                if (uniqueCount == 0 || values[i] != values[uniqueCount - 1]) {
                    values[uniqueCount++] = values[i];
                }
            }
            return Arrays.copyOf(values, uniqueCount);
        }

        private static void addTimes(FloatList out, float[] values) {
            if (values == null) {
                return;
            }
            for (int i = 0; i < values.length; i++) {
                out.add(values[i]);
            }
        }

        private static void sample3(float[] times, float[] values, float[] fallback, float time, float[] out) {
            if (times == null || values == null || times.length == 0) {
                copy(fallback, out, 3);
                return;
            }
            int index = sampleIndex(times, time);
            if (index < 0) {
                copy(values, out, 3);
                return;
            }
            if (index >= times.length - 1) {
                copy(values, index * 3, out, 3);
                return;
            }
            float alpha = (time - times[index]) / Math.max(times[index + 1] - times[index], 0.000001f);
            int left = index * 3;
            int right = (index + 1) * 3;
            out[0] = mix(values[left], values[right], alpha);
            out[1] = mix(values[left + 1], values[right + 1], alpha);
            out[2] = mix(values[left + 2], values[right + 2], alpha);
        }

        private static void sample4(float[] times, float[] values, float[] fallback, float time, float[] out) {
            if (times == null || values == null || times.length == 0) {
                copy(fallback, out, 4);
                return;
            }
            int index = sampleIndex(times, time);
            if (index < 0) {
                copy(values, out, 4);
                return;
            }
            if (index >= times.length - 1) {
                copy(values, index * 4, out, 4);
                return;
            }
            float alpha = (time - times[index]) / Math.max(times[index + 1] - times[index], 0.000001f);
            slerp(values, index * 4, values, (index + 1) * 4, alpha, out);
        }

        private static int sampleIndex(float[] times, float time) {
            if (time <= times[0]) {
                return -1;
            }
            for (int i = 0; i < times.length - 1; i++) {
                if (time >= times[i] && time <= times[i + 1]) {
                    return i;
                }
            }
            return times.length - 1;
        }

        private static void slerp(float[] left, int leftOffset, float[] right, int rightOffset, float alpha,
                float[] out) {
            float t = Math.max(0.0f, Math.min(1.0f, alpha));
            float qx0 = left[leftOffset];
            float qy0 = left[leftOffset + 1];
            float qz0 = left[leftOffset + 2];
            float qw0 = left[leftOffset + 3];
            float qx1 = right[rightOffset];
            float qy1 = right[rightOffset + 1];
            float qz1 = right[rightOffset + 2];
            float qw1 = right[rightOffset + 3];
            float dot = qx0 * qx1 + qy0 * qy1 + qz0 * qz1 + qw0 * qw1;
            if (dot < 0.0f) {
                dot = -dot;
                qx1 = -qx1;
                qy1 = -qy1;
                qz1 = -qz1;
                qw1 = -qw1;
            }
            if (dot > 0.9995f) {
                out[0] = mix(qx0, qx1, t);
                out[1] = mix(qy0, qy1, t);
                out[2] = mix(qz0, qz1, t);
                out[3] = mix(qw0, qw1, t);
                return;
            }
            float theta0 = (float)Math.acos(dot);
            float theta = theta0 * t;
            float sinTheta = (float)Math.sin(theta);
            float sinTheta0 = (float)Math.sin(theta0);
            float s0 = (float)Math.cos(theta) - dot * sinTheta / sinTheta0;
            float s1 = sinTheta / sinTheta0;
            out[0] = qx0 * s0 + qx1 * s1;
            out[1] = qy0 * s0 + qy1 * s1;
            out[2] = qz0 * s0 + qz1 * s1;
            out[3] = qw0 * s0 + qw1 * s1;
        }

        private static float mix(float left, float right, float alpha) {
            return left + (right - left) * alpha;
        }

        private static void copy(float[] source, float[] out, int count) {
            copy(source, 0, out, count);
        }

        private static void copy(float[] source, int offset, float[] out, int count) {
            System.arraycopy(source, offset, out, 0, count);
        }

        private static float[] vector(JsonValue object, String key, int expectedCount, float[] fallback) {
            ArrayView<JsonValue> values = array(object, key);
            if (values.size() < expectedCount) {
                return fallback.clone();
            }
            float[] result = new float[expectedCount];
            for (int i = 0; i < expectedCount; i++) {
                result[i] = number(values.get(i), fallback[i]);
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

    /**
     * Represents an accessor.
     *
     * @author xpenatan
     */
    private static final class Accessor {
        private final byte[] buffer;
        private final int byteOffset;
        private final int byteStride;
        private final int componentType;
        private final int components;
        private final int count;
        private final boolean normalized;

        Accessor(byte[] buffer, int byteOffset, int byteStride, int componentType, int components, int count,
                boolean normalized) {
            this.buffer = buffer;
            this.byteOffset = byteOffset;
            this.byteStride = byteStride;
            this.componentType = componentType;
            this.components = components;
            this.count = count;
            this.normalized = normalized;
        }
    }
}
