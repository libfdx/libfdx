package io.github.libfdx.graphics;

import io.github.libfdx.math.BoundingBox;
import java.nio.ByteBuffer;

/** CPU-only transfer of completed PBR vertex staging. Attributes follow Mesh's retained-source
 * order: position, color, baked color, normal, UV0, UV1, tangent, PBR, baked PBR,
 * emissive, baked emissive, skin weights. Arrays, bounds and native-order packed bytes are
 * transferred to the receiver and must not subsequently be modified by the sender.
 * Contains no graphics resources; retaining it does not retain a device. */
public record PreparedPositionColor3DData(float[][] attributes, int[] joints,
        BoundingBox bounds, ByteBuffer vertices) {
    public PreparedPositionColor3DData {
        if(attributes == null || attributes.length != 12 || bounds == null || vertices == null)
            throw new IllegalArgumentException("Complete mesh staging is required");
    }
}
