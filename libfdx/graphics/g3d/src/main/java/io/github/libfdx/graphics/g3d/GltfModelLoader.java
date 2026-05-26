package io.github.libfdx.graphics.g3d;

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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class GltfModelLoader implements AssetLoader<Model> {
    private static final int GLB_MAGIC = 0x46546c67;
    private static final int GLB_JSON_CHUNK = 0x4e4f534a;
    private static final int GLB_BIN_CHUNK = 0x004e4942;
    private static final int MODE_TRIANGLES = 4;
    private static final int GLTF_CLAMP_TO_EDGE = 33071;
    private static final int GLTF_MIRRORED_REPEAT = 33648;
    private static final int GLTF_REPEAT = 10497;

    private final GraphicsContext graphics;

    GltfModelLoader(GraphicsContext graphics) {
        this.graphics = graphics;
    }

    @Override
    public Class<Model> type() {
        return Model.class;
    }

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

    private GltfDocument loadDocument(FileHandle file, byte[] bytes) {
        final GltfDocument document = parseDocument(bytes);
        List<Object> buffers = array(document.root, "buffers");
        if (buffers.isEmpty()) {
            document.buffers = new byte[0][];
            return document;
        }
        document.buffers = new byte[buffers.size()][];
        for (int i = 0; i < buffers.size(); i++) {
            Map<String, Object> buffer = object(buffers.get(i), "buffer");
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
                document.buffers[i] = file.parent().child(uri).readBytes().get();
            }
        }
        return document;
    }

    private GltfDocument loadImages(FileHandle file, final GltfDocument document) {
        List<Object> images = array(document.root, "images");
        if (images.isEmpty()) {
            document.images = new ImageData[0];
            return document;
        }
        document.images = new ImageData[images.size()];
        for (int i = 0; i < images.size(); i++) {
            Map<String, Object> image = object(images.get(i), "image");
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
        Map<String, Object> root = null;
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
        GeometryBuilder geometry = new GeometryBuilder();
        List<Object> meshes = array(document.root, "meshes");
        if (meshes.isEmpty()) {
            throw new FdxException("glTF model contains no meshes: " + path);
        }
        List<Object> sceneNodes = sceneNodes(document);
        if (sceneNodes.isEmpty()) {
            for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
                appendMesh(document, geometry, meshIndex, Matrix4.IDENTITY);
            }
        }
        else {
            for (int i = 0; i < sceneNodes.size(); i++) {
                appendNode(document, geometry, integerValue(sceneNodes.get(i), -1), Matrix4.IDENTITY);
            }
        }
        if (geometry.vertexCount() == 0) {
            throw new FdxException("glTF model contains no renderable triangles: " + path);
        }
        boolean singleMaterial = !geometry.mixedMaterials && geometry.material != null;
        GltfMaterial gltfMaterial = singleMaterial ? geometry.material : GltfMaterial.DEFAULT;
        PbrMaterial material = material(path + " material", gltfMaterial)
                .doubleSided(geometry.doubleSided);
        boolean retainSourceData = !usesGpuPbrShader();
        float[] positions = geometry.positions();
        float[] bakedColors = retainSourceData ? geometry.bakedColors() : null;
        float[] bakedPbr = retainSourceData ? geometry.bakedPbr() : null;
        float[] bakedEmissive = retainSourceData ? geometry.bakedEmissive() : null;
        Mesh mesh = Mesh.positionColor3D(graphics, path, positions,
                singleMaterial ? geometry.colors() : geometry.bakedColors(), bakedColors,
                geometry.normals(), geometry.texCoords(), singleMaterial ? geometry.pbr() : geometry.bakedPbr(),
                bakedPbr, singleMaterial ? geometry.emissive() : geometry.bakedEmissive(), bakedEmissive,
                bounds(positions), retainSourceData);
        MeshPart meshPart = new MeshPart(path + " part", mesh, null, 0, mesh.vertexCount());
        return DefaultModel.singleNode(path, meshPart, material);
    }

    private void uploadTextures(String path, GltfDocument document) {
        if (document.images == null || document.images.length == 0 || document.gpuTextures != null) {
            return;
        }
        List<Object> textures = array(document.root, "textures");
        document.gpuTextures = new Texture[textures.size()];
        for (int i = 0; i < textures.size(); i++) {
            Map<String, Object> texture = object(textures.get(i), "texture");
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

    private void appendNode(GltfDocument document, GeometryBuilder geometry, int nodeIndex, Matrix4 parentTransform) {
        if (nodeIndex < 0) {
            return;
        }
        Map<String, Object> node = object(array(document.root, "nodes").get(nodeIndex), "node");
        Matrix4 transform = parentTransform.multiply(nodeTransform(node));
        int meshIndex = integer(node, "mesh", -1);
        if (meshIndex >= 0) {
            appendMesh(document, geometry, meshIndex, transform);
        }
        List<Object> children = array(node, "children");
        for (int i = 0; i < children.size(); i++) {
            appendNode(document, geometry, integerValue(children.get(i), -1), transform);
        }
    }

    private void appendMesh(GltfDocument document, GeometryBuilder geometry, int meshIndex, Matrix4 transform) {
        Map<String, Object> mesh = object(array(document.root, "meshes").get(meshIndex), "mesh");
        List<Object> primitives = array(mesh, "primitives");
        for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
            Map<String, Object> primitive = object(primitives.get(primitiveIndex), "primitive");
            int mode = integer(primitive, "mode", MODE_TRIANGLES);
            if (mode != MODE_TRIANGLES) {
                throw new FdxException("Only glTF triangle primitives are supported");
            }
            Map<String, Object> attributes = object(primitive.get("attributes"), "primitive attributes");
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
            GltfMaterial material = material(document, integer(primitive, "material", -1));
            geometry.material(material);
            geometry.doubleSided |= material.doubleSided;
            int[] indices = primitive.containsKey("indices")
                    ? readIndexAccessor(document, integer(primitive, "indices", -1))
                    : sequence(sourcePositions.length / 3);
            appendPrimitive(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, indices,
                    material, transform);
        }
    }

    private void appendPrimitive(GeometryBuilder geometry, float[] sourcePositions, float[] sourceNormals,
            float[] sourceTexCoords, float[] sourceColors, int[] indices, GltfMaterial material, Matrix4 transform) {
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
                    i0, material, transform, basis);
            appendVertex(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, colorComponents,
                    i1, material, transform, basis);
            appendVertex(geometry, sourcePositions, sourceNormals, sourceTexCoords, sourceColors, colorComponents,
                    i2, material, transform, basis);
        }
    }

    private void appendVertex(GeometryBuilder geometry, float[] sourcePositions, float[] sourceNormals,
            float[] sourceTexCoords, float[] sourceColors, int colorComponents, int index, GltfMaterial material,
            Matrix4 transform, TriangleBasis basis) {
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
                clamp(bakedRoughness, 0.04f, 1.0f), bakedEmissive);
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

    private List<Object> sceneNodes(GltfDocument document) {
        List<Object> scenes = array(document.root, "scenes");
        if (scenes.isEmpty()) {
            return Collections.emptyList();
        }
        int sceneIndex = integer(document.root, "scene", 0);
        if (sceneIndex < 0 || sceneIndex >= scenes.size()) {
            return Collections.emptyList();
        }
        return array(object(scenes.get(sceneIndex), "scene"), "nodes");
    }

    private Matrix4 nodeTransform(Map<String, Object> node) {
        List<Object> matrix = array(node, "matrix");
        if (matrix.size() == Matrix4.VALUE_COUNT) {
            float[] values = new float[Matrix4.VALUE_COUNT];
            for (int i = 0; i < values.length; i++) {
                values[i] = number(matrix.get(i), i % 5 == 0 ? 1.0f : 0.0f);
            }
            return Matrix4.of(values);
        }
        List<Object> translation = array(node, "translation");
        List<Object> rotation = array(node, "rotation");
        List<Object> scale = array(node, "scale");
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
        List<Object> materials = array(document.root, "materials");
        if (materialIndex >= materials.size()) {
            return GltfMaterial.DEFAULT;
        }
        if (document.materials == null) {
            document.materials = new GltfMaterial[materials.size()];
        }
        if (document.materials[materialIndex] != null) {
            return document.materials[materialIndex];
        }
        Map<String, Object> material = object(materials.get(materialIndex), "material");
        Map<String, Object> pbr = object(material.get("pbrMetallicRoughness"), "pbr", true);
        Color baseColor = colorFactor(pbr != null ? array(pbr, "baseColorFactor") : Collections.<Object>emptyList(),
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

    private ImageData textureImage(GltfDocument document, Map<String, Object> textureInfo) {
        if (textureInfo == null) {
            return null;
        }
        int textureIndex = integer(textureInfo, "index", -1);
        List<Object> textures = array(document.root, "textures");
        if (textureIndex < 0 || textureIndex >= textures.size()) {
            return null;
        }
        Map<String, Object> texture = object(textures.get(textureIndex), "texture");
        int source = integer(texture, "source", -1);
        if (source < 0 || document.images == null || source >= document.images.length) {
            return null;
        }
        return document.images[source];
    }

    private Texture texture(GltfDocument document, Map<String, Object> textureInfo) {
        if (textureInfo == null || document.gpuTextures == null) {
            return null;
        }
        int textureIndex = integer(textureInfo, "index", -1);
        if (textureIndex < 0 || textureIndex >= document.gpuTextures.length) {
            return null;
        }
        return document.gpuTextures[textureIndex];
    }

    private TextureWrap wrapS(GltfDocument document, Map<String, Object> texture) {
        return samplerWrap(document, texture, "wrapS");
    }

    private TextureWrap wrapT(GltfDocument document, Map<String, Object> texture) {
        return samplerWrap(document, texture, "wrapT");
    }

    private TextureWrap samplerWrap(GltfDocument document, Map<String, Object> texture, String key) {
        List<Object> samplers = array(document.root, "samplers");
        int samplerIndex = integer(texture, "sampler", -1);
        if (samplerIndex < 0 || samplerIndex >= samplers.size()) {
            return TextureWrap.REPEAT;
        }
        Map<String, Object> sampler = object(samplers.get(samplerIndex), "sampler");
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

    private Color colorFactor(List<Object> values, Color fallback) {
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
        Map<String, Object> accessor = object(array(document.root, "accessors").get(accessorIndex), "accessor");
        int bufferViewIndex = integer(accessor, "bufferView", -1);
        if (bufferViewIndex < 0) {
            throw new FdxException("Sparse or bufferless glTF accessors are not supported");
        }
        Map<String, Object> bufferView = object(array(document.root, "bufferViews").get(bufferViewIndex), "bufferView");
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
        Map<String, Object> bufferView = object(array(document.root, "bufferViews").get(bufferViewIndex), "bufferView");
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
        List<Object> materials = array(document.root, "materials");
        if (materialIndex >= materials.size()) {
            return Color.WHITE;
        }
        Map<String, Object> material = object(materials.get(materialIndex), "material");
        Map<String, Object> pbr = object(material.get("pbrMetallicRoughness"), "pbr", true);
        List<Object> factor = pbr != null ? array(pbr, "baseColorFactor") : Collections.<Object>emptyList();
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root(String json) {
        Object parsed = GltfJson.parse(json);
        if (!(parsed instanceof Map)) {
            throw new FdxException("glTF root must be a JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        return object(value, name, false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name, boolean nullable) {
        if (value == null && nullable) {
            return null;
        }
        if (!(value instanceof Map)) {
            throw new FdxException("glTF " + name + " must be an object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (!(value instanceof List)) {
            throw new FdxException("glTF " + key + " must be an array");
        }
        return (List<Object>) value;
    }

    private static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static int integer(Map<String, Object> object, String key, int fallback) {
        Object value = object.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static int integerValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static float number(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static int[] sequence(int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = i;
        }
        return values;
    }

    private static final class GltfDocument {
        private final Map<String, Object> root;
        private final byte[] binaryChunk;
        private byte[][] buffers;
        private ImageData[] images;
        private Texture[] gpuTextures;
        private GltfMaterial[] materials;

        GltfDocument(Map<String, Object> root, byte[] binaryChunk) {
            this.root = root;
            this.binaryChunk = binaryChunk;
        }
    }

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
        private GltfMaterial material;
        private boolean mixedMaterials;
        private boolean doubleSided;

        void add(Vector3 position, Vector3 normal, float u, float v, Color color, float ao, float metallic,
                float roughness, Color emissiveColor, Color bakedColor, float bakedAo, float bakedMetallic,
                float bakedRoughness, Color bakedEmissiveColor) {
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
    }

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
