package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.collections.Array;
import io.github.libfdx.graphics.g3d.ColorMaterialAttribute;
import io.github.libfdx.graphics.g3d.FloatMaterialAttribute;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAlphaMode;
import io.github.libfdx.graphics.g3d.MaterialAttribute;
import io.github.libfdx.graphics.g3d.ShadingModel;
import io.github.libfdx.graphics.g3d.TextureMaterialAttribute;

/** Exact conservative snapshot of standard material values; captured outside rendering. */
final class LodMaterialState {
    private final Material material;
    private final ShadingModel shading;
    private final MaterialAlphaMode alpha;
    private final boolean doubleSided;
    private final Array<MaterialAttribute> attributes = new Array<>();
    private final int[] colorBits;
    private boolean supported = true;

    LodMaterialState(Material material) {
        this.material = material;
        shading = material.shadingModel(); alpha = material.alphaMode(); doubleSided = material.doubleSided();
        colorBits = new int[material.attributes().size() * 4];
        var iterator = material.attributes().values().iterator();
        while (iterator.hasNext()) {
            MaterialAttribute attribute = iterator.next();
            int offset = attributes.size() * 4;
            attributes.add(attribute);
            if (attribute instanceof ColorMaterialAttribute color) {
                var value = color.value();
                colorBits[offset] = Float.floatToIntBits(value.red());
                colorBits[offset + 1] = Float.floatToIntBits(value.green());
                colorBits[offset + 2] = Float.floatToIntBits(value.blue());
                colorBits[offset + 3] = Float.floatToIntBits(value.alpha());
            } else if (!(attribute instanceof FloatMaterialAttribute) && !(attribute instanceof TextureMaterialAttribute)) {
                supported = false;
            }
        }
    }

    boolean unchanged() {
        if (!supported || material.shadingModel() != shading || material.alphaMode() != alpha
                || material.doubleSided() != doubleSided || material.shaderProvider() != null
                || material.shaderBinding() != null || material.attributes().size() != attributes.size()) return false;
        for (int i = 0; i < attributes.size(); i++) {
            MaterialAttribute attribute = attributes.get(i);
            if (material.attributes().get(attribute.type()) != attribute) return false;
            if (attribute instanceof ColorMaterialAttribute color) {
                var value = color.value(); int offset = i * 4;
                if (colorBits[offset] != Float.floatToIntBits(value.red())
                        || colorBits[offset + 1] != Float.floatToIntBits(value.green())
                        || colorBits[offset + 2] != Float.floatToIntBits(value.blue())
                        || colorBits[offset + 3] != Float.floatToIntBits(value.alpha())) return false;
            }
        }
        return true;
    }
}
