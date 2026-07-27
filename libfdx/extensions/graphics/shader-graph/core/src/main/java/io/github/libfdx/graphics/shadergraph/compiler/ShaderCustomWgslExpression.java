package io.github.libfdx.graphics.shadergraph.compiler;

import java.util.Set;

/**
 * Validation and expansion for the deliberately small custom-WGSL escape
 * hatch. It accepts one expression, not declarations or statements.
 */
final class ShaderCustomWgslExpression {
    private static final int MAX_LENGTH = 2048;
    private static final Set<String> ALLOWED = Set.of(
            "abs", "acos", "all", "any", "arrayLength", "asin", "atan",
            "atan2", "ceil", "clamp", "cos", "countLeadingZeros",
            "countOneBits", "countTrailingZeros", "cross", "degrees",
            "determinant", "distance", "dot", "exp", "exp2", "extractBits",
            "faceForward", "firstLeadingBit", "firstTrailingBit", "floor",
            "fma", "fract", "frexp", "insertBits", "inverseSqrt", "ldexp",
            "length", "log", "log2", "max", "min", "mix", "modf",
            "normalize", "pow", "quantizeToF16", "radians", "reflect",
            "refract", "reverseBits", "round", "select", "sign", "sin",
            "smoothstep", "sqrt", "step", "tan", "transpose", "trunc",
            "textureDimensions", "textureGather", "textureGatherCompare",
            "textureLoad", "textureNumLayers", "textureNumLevels",
            "textureNumSamples", "textureSample", "textureSampleBias",
            "textureSampleCompare", "textureSampleCompareLevel",
            "textureSampleGrad", "textureSampleLevel",
            "bool", "f16", "f32", "i32", "u32",
            "vec2", "vec3", "vec4",
            "mat2x2", "mat2x3", "mat2x4",
            "mat3x2", "mat3x3", "mat3x4",
            "mat4x2", "mat4x3", "mat4x4",
            "true", "false");

    private ShaderCustomWgslExpression() {
    }

    static String normalize(String source, int argumentCount) {
        if (source == null) {
            throw new IllegalArgumentException("Custom WGSL expression cannot be null");
        }
        String expression = source.trim();
        if (expression.isEmpty() || expression.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Custom WGSL expression must contain 1.." + MAX_LENGTH
                            + " characters");
        }
        for (int i = 0; i < expression.length(); i++) {
            char character = expression.charAt(i);
            if (Character.isISOControl(character)
                    && !Character.isWhitespace(character)
                    || character == ';' || character == '{'
                    || character == '}' || character == '@'
                    || character == '\\' || character == '`') {
                throw new IllegalArgumentException(
                        "Custom WGSL accepts expressions only");
            }
        }
        if (expression.contains("//") || expression.contains("/*")
                || expression.contains("*/")) {
            throw new IllegalArgumentException(
                    "Custom WGSL comments are not allowed");
        }

        StringBuilder expanded = new StringBuilder(expression.length() + 16);
        for (int i = 0; i < expression.length();) {
            char character = expression.charAt(i);
            if (character != '$') {
                expanded.append(character);
                i++;
                continue;
            }
            int firstDigit = i + 1;
            int end = firstDigit;
            while (end < expression.length()
                    && Character.isDigit(expression.charAt(end))) {
                end++;
            }
            if (end == firstDigit) {
                throw new IllegalArgumentException(
                        "Custom WGSL placeholders use $0, $1, and so on");
            }
            int index = Integer.parseInt(expression.substring(firstDigit, end));
            if (index < 0 || index >= argumentCount) {
                throw new IllegalArgumentException(
                        "Custom WGSL placeholder is out of range: $" + index);
            }
            expanded.append("fdx_arg").append(index);
            i = end;
        }
        validateIdentifiers(expanded.toString(), argumentCount);
        return expanded.toString();
    }

    static String emit(String source, String[] arguments) {
        String normalized = normalize(source, arguments.length);
        String result = normalized;
        for (int i = arguments.length - 1; i >= 0; i--) {
            result = result.replace("fdx_arg" + i, '(' + arguments[i] + ')');
        }
        return result;
    }

    private static void validateIdentifiers(String expression,
            int argumentCount) {
        for (int i = 0; i < expression.length();) {
            char character = expression.charAt(i);
            if (!Character.isLetter(character) && character != '_') {
                i++;
                continue;
            }
            int start = i++;
            while (i < expression.length()) {
                char next = expression.charAt(i);
                if (!Character.isLetterOrDigit(next) && next != '_') {
                    break;
                }
                i++;
            }
            String identifier = expression.substring(start, i);
            if (start > 0 && expression.charAt(start - 1) == '.'
                    && swizzle(identifier)) {
                continue;
            }
            if (argument(identifier, argumentCount)
                    || ALLOWED.contains(identifier)
                    || numericSuffix(expression, start, identifier)) {
                continue;
            }
            throw new IllegalArgumentException(
                    "Custom WGSL identifier is not declared or allowed: "
                            + identifier);
        }
    }

    private static boolean argument(String identifier, int count) {
        if (!identifier.startsWith("fdx_arg")) {
            return false;
        }
        try {
            int index = Integer.parseInt(identifier.substring(7));
            return index >= 0 && index < count;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean numericSuffix(String expression, int start,
            String identifier) {
        return start > 0 && Character.isDigit(expression.charAt(start - 1))
                && ("u".equals(identifier) || "i".equals(identifier)
                        || "f".equals(identifier) || "h".equals(identifier));
    }

    private static boolean swizzle(String identifier) {
        if (identifier.isEmpty() || identifier.length() > 4) {
            return false;
        }
        for (int i = 0; i < identifier.length(); i++) {
            if ("xyzwrgba".indexOf(identifier.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
