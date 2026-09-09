package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;

/** Portable exact vertex input metadata used by recipe factories. Encoding/decoding belongs to
 * capture discovery/import, never a steady-state draw. Format version follows the recipe factory. */
public final class ShaderPreloadVertexLayouts {
    private ShaderPreloadVertexLayouts() { }
    public static String encode(VertexLayout[] layouts) {
        StringBuilder result = new StringBuilder();
        for (VertexLayout layout : layouts) {
            if (!result.isEmpty()) result.append('|');
            result.append(layout.arrayStride()).append(',').append(layout.stepMode());
            for (int i = 0; i < layout.attributeCount(); i++) {
                VertexAttribute attribute = layout.attribute(i);
                result.append(';').append(attribute.location()).append(',').append(attribute.format()).append(',').append(attribute.offset());
            }
        }
        return result.toString();
    }
    public static VertexLayout[] decode(String value) {
        if (value == null || value.length() > 65536) throw new FdxException("Invalid captured vertex layout size");
        if (value.isEmpty()) return new VertexLayout[0];
        String[] encoded = value.split("\\|", -1);
        if (encoded.length > 32) throw new FdxException("Too many captured vertex buffers");
        VertexLayout[] layouts = new VertexLayout[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            String[] parts = encoded[i].split(";", -1);
            if (parts.length < 2 || parts.length > 65) throw new FdxException("Invalid captured vertex attributes");
            String[] header = parts[0].split(",", -1);
            if (header.length != 2) throw new FdxException("Invalid captured vertex header");
            VertexAttribute[] attributes = new VertexAttribute[parts.length - 1];
            for (int a = 1; a < parts.length; a++) {
                String[] fields = parts[a].split(",", -1);
                if (fields.length != 3) throw new FdxException("Invalid captured vertex attribute");
                attributes[a - 1] = VertexAttribute.of(Integer.parseInt(fields[0]), VertexFormat.valueOf(fields[1]), Integer.parseInt(fields[2]));
            }
            layouts[i] = VertexLayout.of(Integer.parseInt(header[0]), VertexStepMode.valueOf(header[1]), attributes);
        }
        return layouts;
    }
}
