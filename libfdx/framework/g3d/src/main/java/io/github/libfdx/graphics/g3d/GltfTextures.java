package io.github.libfdx.graphics.g3d;

import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.TextureMipmapPreparer;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureMipmapFilter;
import io.github.libfdx.graphics.TextureMipmaps;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.math.Color;
import io.github.libfdx.graphics.ColorTransfer;
import java.nio.ByteBuffer;

/** CPU texture preparation, followed by an explicit graphics-thread upload. */
final class GltfTextures {
    static final int DATA = 0, COLOR = 1, COLOR_ALPHA = 2;
    private static final int ROLE_COUNT = 3;
    private final int[] sources;
    private final Sampler[] samplers;
    private final boolean[] used;
    private final ByteBuffer[][] levels;
    private ImageData[] images;
    private int preparing;
    private TextureMipmaps.Rgba8Preparation mipmaps;

    GltfTextures(JsonValue root) {
        JsonValue textureArray = root.get("textures"), samplerArray = root.get("samplers");
        Sampler[] definitions = new Sampler[size(samplerArray)];
        for (int i = 0; i < definitions.length; i++) definitions[i] = sampler(samplerArray.require(i));
        int count = size(textureArray), imageCount = size(root.get("images"));
        sources = new int[count];
        samplers = new Sampler[count];
        used = new boolean[Math.multiplyExact(count, ROLE_COUNT)];
        levels = new ByteBuffer[used.length][];
        for (int i = 0; i < count; i++) {
            JsonValue texture = textureArray.require(i);
            sources[i] = integer(texture, "source", -1);
            if (texture.get("source") != null && (sources[i] < 0 || sources[i] >= imageCount))
                throw new FdxException("glTF texture source outside image range: " + i);
            int sampler = integer(texture, "sampler", -1);
            if (texture.get("sampler") != null && (sampler < 0 || sampler >= definitions.length))
                throw new FdxException("glTF texture sampler outside range: " + i);
            samplers[i] = sampler < 0 ? sampler(JsonValue.object()) : definitions[sampler];
        }
        JsonValue materials = root.get("materials");
        for (int i = 0; i < size(materials); i++) {
            JsonValue material = materials.require(i), pbr = material.get("pbrMetallicRoughness");
            normalScale(material);
            occlusionStrength(material);
            if (pbr != null) {
                use(pbr.get("baseColorTexture"), colorRole(material));
                use(pbr.get("metallicRoughnessTexture"), DATA);
            }
            use(material.get("emissiveTexture"), COLOR);
            use(material.get("normalTexture"), DATA);
            use(material.get("occlusionTexture"), DATA);
        }
    }

    static int colorRole(JsonValue material) {
        JsonValue mode = material.get("alphaMode");
        return mode == null || "OPAQUE".equals(mode.stringValue()) ? COLOR : COLOR_ALPHA;
    }

    private void use(JsonValue info, int role) {
        if (info == null) return;
        coordinates(info);
        int texture = index(info);
        if (sources[texture] < 0)
            throw new FdxException("Referenced glTF texture has no supported image source: " + texture);
        used[texture * ROLE_COUNT + role] = true;
    }

    /** Runs on the asset executor; GPU resources are not touched. */
    void prepare(ImageData[] images) {
        while (!prepareStep(images, 4096)) { }
    }

    boolean prepareStep(ImageData[] images, int pixels) {
        this.images = images;
        while (preparing < used.length && !used[preparing]) preparing++;
        if (preparing < used.length) {
            int i = preparing;
            ImageData image = images[sources[i / ROLE_COUNT]];
            if (image == null) throw new FdxException("glTF texture image is not ready");
            int role = i % ROLE_COUNT;
            if (samplers[i / ROLE_COUNT].mip == TextureMipmapFilter.NONE) {
                levels[i] = new ByteBuffer[] { image.rgba() }; preparing++;
            } else {
                if (mipmaps == null) mipmaps = TextureMipmaps.prepareRgba8(image.rgba(), image.width(), image.height(),
                        role != DATA, role == COLOR_ALPHA);
                if (mipmaps.step(pixels)) { levels[i] = mipmaps.result(); mipmaps = null; preparing++; }
            }
        }
        return preparing == used.length;
    }

    Texture[] allocateHandles() { return new Texture[used.length]; }

    FdxFuture<Void> prepareAsync(AssetLoadContext context, ImageData[] images, TextureMipmapPreparer strategy) {
        this.images = images;
        FdxFuture<Void> result = FdxFuture.pending();
        prepareNext(context, strategy, result);
        return result;
    }

    private void prepareNext(AssetLoadContext context, TextureMipmapPreparer strategy, FdxFuture<Void> result) {
        try {
            while (preparing < used.length && !used[preparing]) preparing++;
            if (preparing == used.length) { result.complete(null); return; }
            int i = preparing;
            ImageData image = images[sources[i / ROLE_COUNT]];
            context.asyncFuture(() -> samplers[i / ROLE_COUNT].mip == TextureMipmapFilter.NONE
                    ? FdxFuture.completed(new ByteBuffer[] { image.rgba() })
                    : strategy.prepare(image.rgba(), image.width(), image.height(), i % ROLE_COUNT != DATA,
                            i % ROLE_COUNT == COLOR_ALPHA)).onSuccess(chain -> {
                        levels[i] = chain; preparing++;
                        prepareNext(context, strategy, result);
                    }).onFailure(result::completeExceptionally);
        } catch (RuntimeException | Error failure) { result.completeExceptionally(failure); }
    }

    /** Caller owns every non-null result immediately, including when a later upload fails. */
    void upload(GraphicsDevice device, String path, Texture[] result) {
        for (int i = 0; i < used.length; i++) {
            upload(device, path, result, i);
        }
    }

    /** Uploads at most one texture so the asset queue can yield between resources. */
    void upload(GraphicsDevice device, String path, Texture[] result, int i) {
        if (!used[i]) return;
        ImageData image = images[sources[i / ROLE_COUNT]];
        Sampler sampler = samplers[i / ROLE_COUNT];
        TextureFormat format = i % ROLE_COUNT != DATA
                && device.capabilities().supportsColorFormat(TextureFormat.RGBA8_UNORM_SRGB)
                ? TextureFormat.RGBA8_UNORM_SRGB : TextureFormat.RGBA8_UNORM;
        TextureDescriptor descriptor = TextureDescriptor.rgba8(path + " texture " + i / ROLE_COUNT
                + " role " + i % ROLE_COUNT, image.width(), image.height()).format(format)
                .mipLevelCount(levels[i].length).filters(sampler.min, sampler.mag, sampler.mip)
                .wrap(sampler.wrapS, sampler.wrapT);
        result[i] = device.createTexture(descriptor);
        device.writeTextureMipLevels(result[i], levels[i]);
        levels[i] = null; // Release prepared chains after upload; the image remains a managed dependency.
    }

    ImageData image(JsonValue info) { return info == null ? null : images[sources[index(info)]]; }
    Binding binding(JsonValue info) {
        return info == null ? null : new Binding(image(info), coordinates(info), samplers[index(info)]);
    }

    static TextureCoordinates coordinates(JsonValue info) {
        if (info == null) return TextureCoordinates.UV0;
        int set = integer(info, "texCoord", 0);
        if (set < 0) throw new FdxException("glTF texCoord must be nonnegative");
        JsonValue extensions = info.get("extensions");
        JsonValue transform = extensions == null ? null : extensions.get("KHR_texture_transform");
        if (transform == null) {
            if (set > 1) throw new FdxException("Only glTF TEXCOORD_0 and TEXCOORD_1 are supported");
            return set == 0 ? TextureCoordinates.UV0 : TextureCoordinates.UV1;
        }
        set = integer(transform, "texCoord", set);
        try {
            return new TextureCoordinates(set, component(transform, "offset", 0, 0), component(transform, "offset", 1, 0),
                    number(transform, "rotation", 0), component(transform, "scale", 0, 1), component(transform, "scale", 1, 1));
        } catch (IllegalArgumentException error) {
            throw new FdxException("Invalid glTF texture transform: " + error.getMessage(), error);
        }
    }
    private static float component(JsonValue object, String field, int component, float fallback) {
        JsonValue array = object.get(field);
        if (array == null) return fallback;
        if (size(array) != 2) throw new FdxException("glTF texture " + field + " requires two components");
        float value = array.require(component).floatValue();
        if (!Float.isFinite(value)) throw new FdxException("glTF texture transform must be finite");
        return value;
    }
    static float normalScale(JsonValue material) {
        return number(material.get("normalTexture"), "scale", 1);
    }
    static float occlusionStrength(JsonValue material) {
        float value = number(material.get("occlusionTexture"), "strength", 1);
        if (value < 0 || value > 1) throw new FdxException("glTF occlusion strength must be in [0, 1]");
        return value;
    }
    private static float number(JsonValue object, String field, float fallback) {
        if (object == null || object.get(field) == null) return fallback;
        float value = object.get(field).floatValue();
        if (!Float.isFinite(value)) throw new FdxException("glTF " + field + " must be finite");
        return value;
    }

    /** Vertex-baking fallback uses level zero and the authored magnification filter. */
    static final class Binding {
        final ImageData image;
        final TextureCoordinates coordinates;
        private final Sampler sampler;
        Binding(ImageData image, TextureCoordinates coordinates, Sampler sampler) {
            this.image = image; this.coordinates = coordinates; this.sampler = sampler;
        }
        Color sample(float u0, float v0, float u1, float v1, boolean srgb) {
            float u = coordinates.set() == 0 ? u0 : u1, v = coordinates.set() == 0 ? v0 : v1;
            float transformedU = coordinates.u(u, v), transformedV = coordinates.v(u, v);
            double x = wrap(transformedU, sampler.wrapS) * image.width();
            double y = wrap(transformedV, sampler.wrapT) * image.height();
            ByteBuffer bytes = image.rgba();
            if (sampler.mag == TextureFilter.NEAREST) return texel(bytes, (int)Math.floor(x), (int)Math.floor(y), srgb);
            x -= .5; y -= .5;
            int ix = (int)Math.floor(x), iy = (int)Math.floor(y);
            Color a = texel(bytes, ix, iy, srgb), b = texel(bytes, ix+1, iy, srgb);
            Color c = texel(bytes, ix, iy+1, srgb), d = texel(bytes, ix+1, iy+1, srgb);
            float tx = (float)(x-ix), ty = (float)(y-iy);
            return new Color(blend(a.red(), b.red(), c.red(), d.red(), tx, ty),
                    blend(a.green(), b.green(), c.green(), d.green(), tx, ty),
                    blend(a.blue(), b.blue(), c.blue(), d.blue(), tx, ty),
                    blend(a.alpha(), b.alpha(), c.alpha(), d.alpha(), tx, ty));
        }
        private Color texel(ByteBuffer bytes, int x, int y, boolean srgb) {
            int offset = (address(y, image.height(), sampler.wrapT) * image.width()
                    + address(x, image.width(), sampler.wrapS)) * 4;
            float r = (bytes.get(offset)&255)/255f, g = (bytes.get(offset+1)&255)/255f,
                    b = (bytes.get(offset+2)&255)/255f, a = (bytes.get(offset+3)&255)/255f;
            return srgb ? new Color(ColorTransfer.srgbToLinear(r), ColorTransfer.srgbToLinear(g), ColorTransfer.srgbToLinear(b), a)
                    : new Color(r, g, b, a);
        }
        private static int address(int value, int size, TextureWrap mode) {
            // Mirroring has already reduced UV to [0,1]; its edge taps clamp to the mirrored edge.
            return mode == TextureWrap.REPEAT ? Math.floorMod(value, size) : Math.max(0, Math.min(size-1, value));
        }
        private static double wrap(float value, TextureWrap mode) {
            if (!Float.isFinite(value)) throw new FdxException("glTF transformed UV is not finite");
            if (mode == TextureWrap.CLAMP_TO_EDGE) return Math.max(0, Math.min(1, value));
            double cell = Math.floor((double)value), fraction = value-cell;
            return mode == TextureWrap.MIRRORED_REPEAT && cell % 2 != 0 ? 1-fraction : fraction;
        }
        private static float blend(float a, float b, float c, float d, float x, float y) {
            return (a*(1-x)+b*x)*(1-y)+(c*(1-x)+d*x)*y;
        }
    }
    Texture texture(Texture[] textures, JsonValue info, int role) {
        return info == null ? null : textures[index(info) * ROLE_COUNT + role];
    }
    private int index(JsonValue info) {
        int index = integer(info, "index", -1);
        if (index < 0 || index >= sources.length)
            throw new FdxException("glTF textureInfo index outside range: " + index);
        return index;
    }

    private static Sampler sampler(JsonValue value) {
        int min = integer(value, "minFilter", 9729), mag = integer(value, "magFilter", 9729);
        TextureFilter minFilter = switch (min) {
            case 9728, 9984, 9986 -> TextureFilter.NEAREST;
            case 9729, 9985, 9987 -> TextureFilter.LINEAR;
            default -> throw new FdxException("Invalid glTF minFilter: " + min);
        };
        TextureMipmapFilter mip = switch (min) {
            case 9984, 9985 -> TextureMipmapFilter.NEAREST;
            case 9986, 9987 -> TextureMipmapFilter.LINEAR;
            default -> TextureMipmapFilter.NONE;
        };
        TextureFilter magFilter = switch (mag) {
            case 9728 -> TextureFilter.NEAREST;
            case 9729 -> TextureFilter.LINEAR;
            default -> throw new FdxException("Invalid glTF magFilter: " + mag);
        };
        return new Sampler(minFilter, magFilter, mip,
                wrap(integer(value, "wrapS", 10497)), wrap(integer(value, "wrapT", 10497)));
    }
    private static TextureWrap wrap(int value) {
        return switch (value) {
            case 10497 -> TextureWrap.REPEAT;
            case 33071 -> TextureWrap.CLAMP_TO_EDGE;
            case 33648 -> TextureWrap.MIRRORED_REPEAT;
            default -> throw new FdxException("Invalid glTF wrap mode: " + value);
        };
    }
    private static int size(JsonValue array) { return array == null ? 0 : array.arrayValues().size(); }
    private static int integer(JsonValue value, String key, int fallback) {
        return GltfAccessors.integer(value, key, fallback);
    }
    private record Sampler(TextureFilter min, TextureFilter mag, TextureMipmapFilter mip,
                           TextureWrap wrapS, TextureWrap wrapT) { }
}
