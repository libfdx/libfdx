package io.github.libfdx.graphics.g3d;

/**
 * Immutable per-texture UV selection and affine transform. UV sets are zero or one.
 * Applies scale, then counterclockwise rotation in UV space, then translation.
 * The material's legacy shared UV offset is added after this transform.
 */
public final class TextureCoordinates {
    public static final TextureCoordinates UV0 = new TextureCoordinates(0, 0, 0, 0, 1, 1);
    public static final TextureCoordinates UV1 = new TextureCoordinates(1, 0, 0, 0, 1, 1);
    private final int set;
    private final float m00, m01, m02, m10, m11, m12;

    public TextureCoordinates(int set, float offsetU, float offsetV, float rotationRadians,
                              float scaleU, float scaleV) {
        if (set < 0 || set > 1) throw new IllegalArgumentException("Only UV sets 0 and 1 are supported");
        if (!Float.isFinite(offsetU) || !Float.isFinite(offsetV) || !Float.isFinite(rotationRadians)
                || !Float.isFinite(scaleU) || !Float.isFinite(scaleV))
            throw new IllegalArgumentException("Texture transforms must be finite");
        float c = (float)Math.cos(rotationRadians), s = (float)Math.sin(rotationRadians);
        this.set = set;
        m00 = c * scaleU; m01 = -s * scaleV; m02 = offsetU;
        m10 = s * scaleU; m11 = c * scaleV; m12 = offsetV;
    }
    public int set() { return set; }
    public float m00() { return m00; }
    public float m01() { return m01; }
    public float m02() { return m02; }
    public float m10() { return m10; }
    public float m11() { return m11; }
    public float m12() { return m12; }
    public float u(float u, float v) { return m00 * u + m01 * v + m02; }
    public float v(float u, float v) { return m10 * u + m11 * v + m12; }
}
