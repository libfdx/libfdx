package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.core.FdxException;

import java.util.Arrays;

/**
 * Deterministic bounded expansion helpers for boolean static switches.
 */
public final class ShaderGraphVariantExpansion {
    private ShaderGraphVariantExpansion() {
    }

    public static ShaderGraphVariant[] booleans(ShaderGraphProgram program,
            int maximumVariants, String... switchParameterIds) {
        if (program == null || switchParameterIds == null
                || maximumVariants <= 0) {
            throw new FdxException(
                    "Static variant expansion arguments are invalid");
        }
        ShaderGraphId[] ids = new ShaderGraphId[switchParameterIds.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = ShaderGraphId.of(switchParameterIds[i]);
        }
        Arrays.sort(ids);
        for (int i = 1; i < ids.length; i++) {
            if (ids[i - 1].equals(ids[i])) {
                throw new FdxException(
                        "Duplicate static switch " + ids[i]);
            }
        }
        if (ids.length >= Integer.SIZE - 1) {
            throw new FdxException(
                    "Static switch expansion is too large");
        }
        int count = 1 << ids.length;
        if (count > maximumVariants
                || count > ShaderGraphTechnique.HARD_MAX_VARIANTS) {
            throw new FdxException("Static switches expand to " + count
                    + " variants, limit is " + Math.min(maximumVariants,
                            ShaderGraphTechnique.HARD_MAX_VARIANTS));
        }
        ShaderGraphVariant[] result = new ShaderGraphVariant[count];
        for (int mask = 0; mask < count; mask++) {
            ShaderGraphStaticValue[] values =
                    new ShaderGraphStaticValue[ids.length];
            for (int i = 0; i < ids.length; i++) {
                values[i] = ShaderGraphStaticValue.bool(
                        ids[i].value(), (mask & (1 << i)) != 0);
            }
            result[mask] = ShaderGraphVariant.builder(key(mask, ids.length),
                            program)
                    .staticValues(values)
                    .build();
        }
        return result;
    }

    public static String key(boolean... values) {
        if (values == null) {
            throw new FdxException(
                    "Static variant key values cannot be null");
        }
        int mask = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                if (i >= Integer.SIZE - 1) {
                    throw new FdxException(
                            "Static variant key is too large");
                }
                mask |= 1 << i;
            }
        }
        return key(mask, values.length);
    }

    private static String key(int mask, int count) {
        if (mask == 0) {
            return ShaderGraphVariant.DEFAULT_KEY;
        }
        StringBuilder key = new StringBuilder(count + 2).append("v-");
        for (int i = 0; i < count; i++) {
            key.append((mask & (1 << i)) != 0 ? '1' : '0');
        }
        return key.toString();
    }
}
