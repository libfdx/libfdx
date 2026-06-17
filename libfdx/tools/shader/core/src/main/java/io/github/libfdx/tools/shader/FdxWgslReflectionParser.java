package io.github.libfdx.tools.shader;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ShaderAttribute;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderReflection;
import io.github.libfdx.graphics.VertexFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the shader metadata libFDX needs from WGSL built-in shader sources.
 *
 * @author xpenatan
 */
final class FdxWgslReflectionParser {
    private static final Pattern RESOURCE_PATTERN = Pattern.compile("@group\\s*\\(\\s*(\\d+)\\s*\\)\\s*"
            + "@binding\\s*\\(\\s*(\\d+)\\s*\\)\\s*var\\s*(?:<\\s*([^>]*)\\s*>)?\\s+"
            + "([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([^;]+);");
    private static final Pattern VERTEX_INPUT_STRUCT_PATTERN = Pattern.compile("struct\\s+VertexInput\\s*\\{(.*?)\\};",
            Pattern.DOTALL);
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("@location\\s*\\(\\s*(\\d+)\\s*\\)\\s*"
            + "([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([^,;]+)[,;]");

    private FdxWgslReflectionParser() {
    }

    static ShaderReflection parse(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new FdxException("WGSL source cannot be empty");
        }
        String cleanSource = stripComments(source);
        List<ShaderBinding> bindings = parseBindings(cleanSource);
        List<ShaderAttribute> attributes = parseVertexAttributes(cleanSource);
        bindings.sort(Comparator.comparingInt(ShaderBinding::group).thenComparingInt(ShaderBinding::binding));
        attributes.sort(Comparator.comparingInt(ShaderAttribute::location));
        return ShaderReflection.of(
                bindings.toArray(new ShaderBinding[0]),
                attributes.toArray(new ShaderAttribute[0]));
    }

    private static List<ShaderBinding> parseBindings(String source) {
        List<ShaderBinding> bindings = new ArrayList<>();
        Matcher matcher = RESOURCE_PATTERN.matcher(source);
        while (matcher.find()) {
            int group = Integer.parseInt(matcher.group(1));
            int binding = Integer.parseInt(matcher.group(2));
            String addressSpace = matcher.group(3);
            String name = matcher.group(4);
            String type = matcher.group(5);
            bindings.add(ShaderBinding.of(group, binding, name, bindingType(addressSpace, type)));
        }
        return bindings;
    }

    private static List<ShaderAttribute> parseVertexAttributes(String source) {
        List<ShaderAttribute> attributes = new ArrayList<>();
        Matcher structMatcher = VERTEX_INPUT_STRUCT_PATTERN.matcher(source);
        if (!structMatcher.find()) {
            return attributes;
        }
        Matcher attributeMatcher = ATTRIBUTE_PATTERN.matcher(structMatcher.group(1));
        while (attributeMatcher.find()) {
            int location = Integer.parseInt(attributeMatcher.group(1));
            String name = attributeMatcher.group(2);
            String type = attributeMatcher.group(3);
            attributes.add(ShaderAttribute.of(location, name, vertexFormat(type)));
        }
        return attributes;
    }

    private static ShaderBindingType bindingType(String addressSpace, String type) {
        String normalizedAddressSpace = normalize(addressSpace);
        String normalizedType = normalize(type);
        if (normalizedAddressSpace.startsWith("uniform")) {
            return ShaderBindingType.UNIFORM_BUFFER;
        }
        if (normalizedAddressSpace.startsWith("storage")) {
            return ShaderBindingType.STORAGE_BUFFER;
        }
        if (normalizedType.startsWith("texture_storage")) {
            return ShaderBindingType.STORAGE_TEXTURE;
        }
        if (normalizedType.startsWith("texture_")) {
            return ShaderBindingType.TEXTURE;
        }
        if (normalizedType.startsWith("sampler")) {
            return ShaderBindingType.SAMPLER;
        }
        return ShaderBindingType.UNKNOWN;
    }

    private static VertexFormat vertexFormat(String type) {
        String normalized = normalize(type);
        if ("f32".equals(normalized)) {
            return VertexFormat.FLOAT32;
        }
        if ("vec2f".equals(normalized) || "vec2<f32>".equals(normalized)) {
            return VertexFormat.FLOAT32X2;
        }
        if ("vec3f".equals(normalized) || "vec3<f32>".equals(normalized)) {
            return VertexFormat.FLOAT32X3;
        }
        if ("vec4f".equals(normalized) || "vec4<f32>".equals(normalized)) {
            return VertexFormat.FLOAT32X4;
        }
        throw new FdxException("Unsupported WGSL vertex attribute type for reflection: " + type.trim());
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(" ", "")
                .replace("\t", "")
                .replace("\r", "")
                .replace("\n", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
