package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.internal.MathAcceleration;

/**
 * Represents a mutable matrix4.
 *
 * <p>Matrix operations mutate this instance and return it for chaining. Create
 * and retain matrices at an ownership boundary, then reuse them with
 * {@code set}, {@code setTo...}, and composition methods. The API intentionally
 * provides no static factories that hide a matrix allocation.</p>
 *
 * @author xpenatan
 */
public final class Matrix4 {
    public static final int VALUE_COUNT = 16;
    /** Shared identity value for read-only use. */
    public static final Matrix4 IDENTITY = new Matrix4();

    private final float[] values = new float[VALUE_COUNT];
    private float[] inversionScratch;

    /**
     * Creates a matrix4.
     */
    public Matrix4() {
        idt();
    }

    /**
     * Creates a matrix4.
     *
     * @param matrix the matrix
     */
    public Matrix4(Matrix4 matrix) {
        set(matrix);
    }

    /**
     * Creates a matrix4.
     *
     * @param values the values
     */
    public Matrix4(float[] values) {
        set(values);
    }

    /**
     * Returns the idt.
     *
     * @return this matrix4 for chaining
     */
    public Matrix4 idt() {
        for (int i = 0; i < VALUE_COUNT; i++) {
            values[i] = 0.0f;
        }
        values[0] = 1.0f;
        values[5] = 1.0f;
        values[10] = 1.0f;
        values[15] = 1.0f;
        return this;
    }

    /**
     * Sets the set and returns this matrix4.
     *
     * @param matrix the matrix
     * @return this matrix4 for chaining
     */
    public Matrix4 set(Matrix4 matrix) {
        if (matrix == null) {
            throw new FdxException("Matrix4 cannot be null");
        }
        System.arraycopy(matrix.values, 0, values, 0, VALUE_COUNT);
        return this;
    }

    /**
     * Sets the set and returns this matrix4.
     *
     * @param source the source value
     * @return this matrix4 for chaining
     */
    public Matrix4 set(float[] source) {
        if (source == null || source.length != VALUE_COUNT) {
            throw new FdxException("Matrix4 requires 16 values");
        }
        System.arraycopy(source, 0, values, 0, VALUE_COUNT);
        return this;
    }

    /**
     * Sets one column without allocating temporary storage.
     *
     * @param column the zero-based column index
     * @param x the first row value
     * @param y the second row value
     * @param z the third row value
     * @param w the fourth row value
     * @return this matrix4 for chaining
     */
    public Matrix4 setColumn(int column, float x, float y, float z, float w) {
        if (column < 0 || column >= 4) {
            throw new FdxException("Matrix4 column must be between 0 and 3");
        }
        int offset = column * 4;
        values[offset] = x;
        values[offset + 1] = y;
        values[offset + 2] = z;
        values[offset + 3] = w;
        return this;
    }

    /**
     * Sets the to translation.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this matrix4 for chaining
     */
    public Matrix4 setToTranslation(float x, float y, float z) {
        idt();
        values[12] = x;
        values[13] = y;
        values[14] = z;
        return this;
    }

    /**
     * Sets the to scale.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this matrix4 for chaining
     */
    public Matrix4 setToScale(float x, float y, float z) {
        idt();
        values[0] = x;
        values[5] = y;
        values[10] = z;
        return this;
    }

    /**
     * Sets the to rotation x.
     *
     * @param radians the radians
     * @return this matrix4 for chaining
     */
    public Matrix4 setToRotationX(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        idt();
        values[5] = cos;
        values[6] = sin;
        values[9] = -sin;
        values[10] = cos;
        return this;
    }

    /**
     * Sets the to rotation y.
     *
     * @param radians the radians
     * @return this matrix4 for chaining
     */
    public Matrix4 setToRotationY(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        idt();
        values[0] = cos;
        values[2] = -sin;
        values[8] = sin;
        values[10] = cos;
        return this;
    }

    /**
     * Sets the to rotation z.
     *
     * @param radians the radians
     * @return this matrix4 for chaining
     */
    public Matrix4 setToRotationZ(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        idt();
        values[0] = cos;
        values[1] = sin;
        values[4] = -sin;
        values[5] = cos;
        return this;
    }

    /**
     * Sets this matrix to an axis-angle rotation.
     *
     * @param axisX the rotation-axis x coordinate
     * @param axisY the rotation-axis y coordinate
     * @param axisZ the rotation-axis z coordinate
     * @param radians the rotation in radians
     * @return this matrix4 for chaining
     */
    public Matrix4 setToRotation(float axisX, float axisY, float axisZ,
            float radians) {
        float length = (float)Math.sqrt(axisX * axisX + axisY * axisY
                + axisZ * axisZ);
        if (length == 0.0f) {
            return idt();
        }
        float inverseLength = 1.0f / length;
        axisX *= inverseLength;
        axisY *= inverseLength;
        axisZ *= inverseLength;
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float oneMinusCos = 1.0f - cos;

        idt();
        values[0] = oneMinusCos * axisX * axisX + cos;
        values[1] = oneMinusCos * axisX * axisY + sin * axisZ;
        values[2] = oneMinusCos * axisX * axisZ - sin * axisY;
        values[4] = oneMinusCos * axisX * axisY - sin * axisZ;
        values[5] = oneMinusCos * axisY * axisY + cos;
        values[6] = oneMinusCos * axisY * axisZ + sin * axisX;
        values[8] = oneMinusCos * axisX * axisZ + sin * axisY;
        values[9] = oneMinusCos * axisY * axisZ - sin * axisX;
        values[10] = oneMinusCos * axisZ * axisZ + cos;
        return this;
    }

    /**
     * Sets the to rotation quaternion.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     * @return this matrix4 for chaining
     */
    public Matrix4 setToRotationQuaternion(float x, float y, float z, float w) {
        float len = (float)Math.sqrt(x * x + y * y + z * z + w * w);
        if (len == 0.0f) {
            return idt();
        }
        float invLen = 1.0f / len;
        x *= invLen;
        y *= invLen;
        z *= invLen;
        w *= invLen;

        float xx = x * x;
        float yy = y * y;
        float zz = z * z;
        float xy = x * y;
        float xz = x * z;
        float yz = y * z;
        float wx = w * x;
        float wy = w * y;
        float wz = w * z;

        idt();
        values[0] = 1.0f - 2.0f * (yy + zz);
        values[1] = 2.0f * (xy + wz);
        values[2] = 2.0f * (xz - wy);
        values[4] = 2.0f * (xy - wz);
        values[5] = 1.0f - 2.0f * (xx + zz);
        values[6] = 2.0f * (yz + wx);
        values[8] = 2.0f * (xz + wy);
        values[9] = 2.0f * (yz - wx);
        values[10] = 1.0f - 2.0f * (xx + yy);
        return this;
    }

    /**
     * Sets the to translation, rotation, and scale.
     *
     * @param translationX the translation x
     * @param translationY the translation y
     * @param translationZ the translation z
     * @param rotationX the rotation quaternion x
     * @param rotationY the rotation quaternion y
     * @param rotationZ the rotation quaternion z
     * @param rotationW the rotation quaternion w
     * @param scaleX the scale x
     * @param scaleY the scale y
     * @param scaleZ the scale z
     * @return this matrix4 for chaining
     */
    public Matrix4 setToTrs(float translationX, float translationY, float translationZ,
            float rotationX, float rotationY, float rotationZ, float rotationW,
            float scaleX, float scaleY, float scaleZ) {
        float len = (float)Math.sqrt(rotationX * rotationX + rotationY * rotationY
                + rotationZ * rotationZ + rotationW * rotationW);
        if (len == 0.0f) {
            rotationX = 0.0f;
            rotationY = 0.0f;
            rotationZ = 0.0f;
            rotationW = 1.0f;
        }
        else {
            float invLen = 1.0f / len;
            rotationX *= invLen;
            rotationY *= invLen;
            rotationZ *= invLen;
            rotationW *= invLen;
        }

        float xx = rotationX * rotationX;
        float yy = rotationY * rotationY;
        float zz = rotationZ * rotationZ;
        float xy = rotationX * rotationY;
        float xz = rotationX * rotationZ;
        float yz = rotationY * rotationZ;
        float wx = rotationW * rotationX;
        float wy = rotationW * rotationY;
        float wz = rotationW * rotationZ;

        values[0] = (1.0f - 2.0f * (yy + zz)) * scaleX;
        values[1] = (2.0f * (xy + wz)) * scaleX;
        values[2] = (2.0f * (xz - wy)) * scaleX;
        values[3] = 0.0f;
        values[4] = (2.0f * (xy - wz)) * scaleY;
        values[5] = (1.0f - 2.0f * (xx + zz)) * scaleY;
        values[6] = (2.0f * (yz + wx)) * scaleY;
        values[7] = 0.0f;
        values[8] = (2.0f * (xz + wy)) * scaleZ;
        values[9] = (2.0f * (yz - wx)) * scaleZ;
        values[10] = (1.0f - 2.0f * (xx + yy)) * scaleZ;
        values[11] = 0.0f;
        values[12] = translationX;
        values[13] = translationY;
        values[14] = translationZ;
        values[15] = 1.0f;
        return this;
    }

    /**
     * Sets the to perspective.
     *
     * @param fieldOfViewDegrees the field of view degrees
     * @param aspectRatio the aspect ratio
     * @param near the near
     * @param far the far
     * @return this matrix4 for chaining
     */
    public Matrix4 setToPerspective(float fieldOfViewDegrees, float aspectRatio, float near, float far) {
        return setToPerspective(fieldOfViewDegrees, aspectRatio, near, far,
                ClipDepthRange.ZERO_TO_ONE);
    }

    /**
     * Sets a perspective projection for an explicit clip depth range.
     *
     * @param fieldOfViewDegrees the vertical field of view in degrees
     * @param aspectRatio the width/height aspect ratio
     * @param near the near plane distance
     * @param far the far plane distance
     * @param clipDepthRange the depth range the target API clips against
     * @return this matrix4 for chaining
     */
    public Matrix4 setToPerspective(float fieldOfViewDegrees, float aspectRatio, float near, float far,
            ClipDepthRange clipDepthRange) {
        if (aspectRatio == 0.0f) {
            throw new FdxException("Perspective camera aspect ratio cannot be zero");
        }
        if (near <= 0.0f || far <= near) {
            throw new FdxException("Perspective camera near/far range is invalid");
        }
        if (clipDepthRange == null) {
            throw new FdxException("Clip depth range cannot be null");
        }
        for (int i = 0; i < VALUE_COUNT; i++) {
            values[i] = 0.0f;
        }
        float f = (float)(1.0 / Math.tan(Math.toRadians(fieldOfViewDegrees) * 0.5));
        values[0] = f / aspectRatio;
        values[5] = f;
        values[11] = -1.0f;
        if (clipDepthRange == ClipDepthRange.ZERO_TO_ONE_REVERSED) {
            // Near maps to 1, far maps to 0.
            values[10] = near / (far - near);
            values[14] = (far * near) / (far - near);
            return this;
        }
        // Built zero-to-one otherwise, because that is what every current API
        // wants; the OpenGL family is then derived from it.
        values[10] = far / (near - far);
        values[14] = (far * near) / (near - far);
        if (clipDepthRange == ClipDepthRange.NEGATIVE_ONE_TO_ONE) {
            toNegativeOneToOneDepth();
        }
        return this;
    }

    /**
     * Sets a perspective projection whose far plane is at infinity.
     *
     * <p>Only meaningful for {@link ClipDepthRange#ZERO_TO_ONE_REVERSED}: near
     * maps to 1 and infinity maps to 0, so nothing is ever clipped by
     * distance. There is no far/near ratio, and therefore none of the
     * degeneracy that ratio causes.</p>
     *
     * <p>The far-plane row of the resulting matrix is identically zero. That is
     * correct - the far plane genuinely does not exist - so frustum extraction
     * must treat an unnormalizable row as an absent constraint rather than an
     * error.</p>
     *
     * @param fieldOfViewDegrees the vertical field of view in degrees
     * @param aspectRatio the width/height aspect ratio
     * @param near the near plane distance
     * @param clipDepthRange must be {@link ClipDepthRange#ZERO_TO_ONE_REVERSED}
     * @return this matrix4 for chaining
     */
    public Matrix4 setToPerspectiveInfinite(float fieldOfViewDegrees, float aspectRatio, float near,
            ClipDepthRange clipDepthRange) {
        if (aspectRatio == 0.0f) {
            throw new FdxException("Perspective camera aspect ratio cannot be zero");
        }
        if (near <= 0.0f || !Float.isFinite(near)) {
            throw new FdxException("Perspective camera near plane is invalid");
        }
        if (clipDepthRange != ClipDepthRange.ZERO_TO_ONE_REVERSED) {
            throw new FdxException(
                    "An infinite far plane requires ZERO_TO_ONE_REVERSED depth");
        }
        for (int i = 0; i < VALUE_COUNT; i++) {
            values[i] = 0.0f;
        }
        float f = (float)(1.0 / Math.tan(Math.toRadians(fieldOfViewDegrees) * 0.5));
        values[0] = f / aspectRatio;
        values[5] = f;
        values[10] = 0.0f;
        values[11] = -1.0f;
        values[14] = near;
        return this;
    }

    /**
     * Sets the to orthographic.
     *
     * @param left the left
     * @param right the right
     * @param bottom the bottom
     * @param top the top
     * @param near the near
     * @param far the far
     * @return this matrix4 for chaining
     */
    public Matrix4 setToOrthographic(float left, float right, float bottom, float top, float near, float far) {
        if (right == left || top == bottom || far == near) {
            throw new FdxException("Orthographic camera range is invalid");
        }
        return setToOrthographic(left, right, bottom, top, near, far,
                ClipDepthRange.ZERO_TO_ONE);
    }

    /**
     * Sets an orthographic projection for an explicit clip depth range.
     *
     * @param left the left plane
     * @param right the right plane
     * @param bottom the bottom plane
     * @param top the top plane
     * @param near the near plane
     * @param far the far plane
     * @param clipDepthRange the depth range the target API clips against
     * @return this matrix4 for chaining
     */
    public Matrix4 setToOrthographic(float left, float right, float bottom, float top, float near, float far,
            ClipDepthRange clipDepthRange) {
        if (clipDepthRange == null) {
            throw new FdxException("Clip depth range cannot be null");
        }
        idt();
        values[0] = 2.0f / (right - left);
        values[5] = 2.0f / (top - bottom);
        values[12] = -(right + left) / (right - left);
        values[13] = -(top + bottom) / (top - bottom);
        if (clipDepthRange == ClipDepthRange.ZERO_TO_ONE_REVERSED) {
            values[10] = 1.0f / (far - near);
            values[14] = far / (far - near);
            return this;
        }
        values[10] = 1.0f / (near - far);
        values[14] = near / (near - far);
        if (clipDepthRange == ClipDepthRange.NEGATIVE_ONE_TO_ONE) {
            toNegativeOneToOneDepth();
        }
        return this;
    }

    /**
     * Rewrites a zero-to-one projection in place as a negative-one-to-one one.
     *
     * <p>Clip depth is remapped by {@code z' = 2z - w}, i.e. row 2 becomes
     * {@code 2 * row2 - row3}. Exact for both perspective and orthographic
     * projections; the result is bit-identical to building the OpenGL form
     * directly.</p>
     *
     * @return this matrix4 for chaining
     */
    public Matrix4 toNegativeOneToOneDepth() {
        values[2] = 2.0f * values[2] - values[3];
        values[6] = 2.0f * values[6] - values[7];
        values[10] = 2.0f * values[10] - values[11];
        values[14] = 2.0f * values[14] - values[15];
        return this;
    }

    /**
     * Sets the to look at.
     *
     * @param eye the eye
     * @param center the center
     * @param up the up
     * @return this matrix4 for chaining
     */
    public Matrix4 setToLookAt(Vector3 eye, Vector3 center, Vector3 up) {
        return setToLookAt(
                eye.x(), eye.y(), eye.z(),
                center.x(), center.y(), center.z(),
                up.x(), up.y(), up.z());
    }

    /**
     * Sets this matrix to a view looking along a direction, without an eye
     * position.
     *
     * <p>Prefer this over {@link #setToLookAt(Vector3, Vector3, Vector3)} when
     * the eye can be far from the origin. That overload derives the forward
     * vector as {@code center - eye}; if a caller builds the centre as
     * {@code eye + direction}, the unit direction falls below one float ULP
     * once {@code |eye|} exceeds roughly 1.7e7, the subtraction returns zero,
     * and the view silently degrades to looking down -Z. Passing the direction
     * straight in keeps every operand at magnitude one, where float is exact.</p>
     *
     * <p>Apply the eye afterwards with {@code translate(-eyeX, -eyeY, -eyeZ)}
     * to obtain the full view matrix.</p>
     *
     * @param directionX the direction x
     * @param directionY the direction y
     * @param directionZ the direction z
     * @param upX the up x
     * @param upY the up y
     * @param upZ the up z
     * @return this matrix4 for chaining
     */
    public Matrix4 setToLookAlong(float directionX, float directionY, float directionZ,
            float upX, float upY, float upZ) {
        return setToLookAt(0.0f, 0.0f, 0.0f,
                directionX, directionY, directionZ, upX, upY, upZ);
    }

    /**
     * Sets the to look at.
     *
     * @param eyeX the eye x
     * @param eyeY the eye y
     * @param eyeZ the eye z
     * @param centerX the center x
     * @param centerY the center y
     * @param centerZ the center z
     * @param upX the up x
     * @param upY the up y
     * @param upZ the up z
     * @return this matrix4 for chaining
     */
    public Matrix4 setToLookAt(float eyeX, float eyeY, float eyeZ, float centerX, float centerY, float centerZ,
            float upX, float upY, float upZ) {
        float forwardX = centerX - eyeX;
        float forwardY = centerY - eyeY;
        float forwardZ = centerZ - eyeZ;
        float forwardLength = (float)Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ);
        if (forwardLength == 0.0f) {
            forwardZ = -1.0f;
        }
        else {
            float invForwardLength = 1.0f / forwardLength;
            forwardX *= invForwardLength;
            forwardY *= invForwardLength;
            forwardZ *= invForwardLength;
        }

        float sideX = forwardY * upZ - forwardZ * upY;
        float sideY = forwardZ * upX - forwardX * upZ;
        float sideZ = forwardX * upY - forwardY * upX;
        float sideLength = (float)Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        if (sideLength == 0.0f) {
            sideX = 1.0f;
            sideY = 0.0f;
            sideZ = 0.0f;
        }
        else {
            float invSideLength = 1.0f / sideLength;
            sideX *= invSideLength;
            sideY *= invSideLength;
            sideZ *= invSideLength;
        }

        float upVectorX = sideY * forwardZ - sideZ * forwardY;
        float upVectorY = sideZ * forwardX - sideX * forwardZ;
        float upVectorZ = sideX * forwardY - sideY * forwardX;

        values[0] = sideX;
        values[4] = sideY;
        values[8] = sideZ;
        values[12] = -(sideX * eyeX + sideY * eyeY + sideZ * eyeZ);
        values[1] = upVectorX;
        values[5] = upVectorY;
        values[9] = upVectorZ;
        values[13] = -(upVectorX * eyeX + upVectorY * eyeY + upVectorZ * eyeZ);
        values[2] = -forwardX;
        values[6] = -forwardY;
        values[10] = -forwardZ;
        values[14] = forwardX * eyeX + forwardY * eyeY + forwardZ * eyeZ;
        values[3] = 0.0f;
        values[7] = 0.0f;
        values[11] = 0.0f;
        values[15] = 1.0f;
        return this;
    }

    /**
     * Post-multiplies this matrix by a translation without allocating a
     * temporary matrix.
     *
     * @param x the x translation
     * @param y the y translation
     * @param z the z translation
     * @return this matrix4 for chaining
     */
    public Matrix4 translate(float x, float y, float z) {
        values[12] += values[0] * x + values[4] * y + values[8] * z;
        values[13] += values[1] * x + values[5] * y + values[9] * z;
        values[14] += values[2] * x + values[6] * y + values[10] * z;
        values[15] += values[3] * x + values[7] * y + values[11] * z;
        return this;
    }

    /**
     * Post-multiplies this matrix by a scale without allocating a temporary
     * matrix.
     *
     * @param x the x scale
     * @param y the y scale
     * @param z the z scale
     * @return this matrix4 for chaining
     */
    public Matrix4 scale(float x, float y, float z) {
        for (int row = 0; row < 4; row++) {
            values[row] *= x;
            values[4 + row] *= y;
            values[8 + row] *= z;
        }
        return this;
    }

    /**
     * Post-multiplies this matrix by an x-axis rotation without allocating a
     * temporary matrix.
     *
     * @param radians the rotation in radians
     * @return this matrix4 for chaining
     */
    public Matrix4 rotateX(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        return mulRotation(1.0f, 0.0f, 0.0f,
                0.0f, cos, sin,
                0.0f, -sin, cos);
    }

    /**
     * Post-multiplies this matrix by a y-axis rotation without allocating a
     * temporary matrix.
     *
     * @param radians the rotation in radians
     * @return this matrix4 for chaining
     */
    public Matrix4 rotateY(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        return mulRotation(cos, 0.0f, -sin,
                0.0f, 1.0f, 0.0f,
                sin, 0.0f, cos);
    }

    /**
     * Post-multiplies this matrix by a z-axis rotation without allocating a
     * temporary matrix.
     *
     * @param radians the rotation in radians
     * @return this matrix4 for chaining
     */
    public Matrix4 rotateZ(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        return mulRotation(cos, sin, 0.0f,
                -sin, cos, 0.0f,
                0.0f, 0.0f, 1.0f);
    }

    /**
     * Post-multiplies this matrix by an axis-angle rotation without allocating
     * a temporary matrix.
     *
     * @param axisX the rotation-axis x coordinate
     * @param axisY the rotation-axis y coordinate
     * @param axisZ the rotation-axis z coordinate
     * @param radians the rotation in radians
     * @return this matrix4 for chaining
     */
    public Matrix4 rotate(float axisX, float axisY, float axisZ,
            float radians) {
        float length = (float)Math.sqrt(axisX * axisX + axisY * axisY
                + axisZ * axisZ);
        if (length == 0.0f) {
            return this;
        }
        float inverseLength = 1.0f / length;
        axisX *= inverseLength;
        axisY *= inverseLength;
        axisZ *= inverseLength;
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float oneMinusCos = 1.0f - cos;
        return mulRotation(
                oneMinusCos * axisX * axisX + cos,
                oneMinusCos * axisX * axisY + sin * axisZ,
                oneMinusCos * axisX * axisZ - sin * axisY,
                oneMinusCos * axisX * axisY - sin * axisZ,
                oneMinusCos * axisY * axisY + cos,
                oneMinusCos * axisY * axisZ + sin * axisX,
                oneMinusCos * axisX * axisZ + sin * axisY,
                oneMinusCos * axisY * axisZ - sin * axisX,
                oneMinusCos * axisZ * axisZ + cos);
    }

    /**
     * Post-multiplies this matrix by a quaternion rotation without allocating
     * a temporary matrix.
     *
     * @param x the quaternion x coordinate
     * @param y the quaternion y coordinate
     * @param z the quaternion z coordinate
     * @param w the quaternion w coordinate
     * @return this matrix4 for chaining
     */
    public Matrix4 rotateQuaternion(float x, float y, float z, float w) {
        float length = (float)Math.sqrt(x * x + y * y + z * z + w * w);
        if (length == 0.0f) {
            return this;
        }
        float inverseLength = 1.0f / length;
        x *= inverseLength;
        y *= inverseLength;
        z *= inverseLength;
        w *= inverseLength;

        float xx = x * x;
        float yy = y * y;
        float zz = z * z;
        float xy = x * y;
        float xz = x * z;
        float yz = y * z;
        float wx = w * x;
        float wy = w * y;
        float wz = w * z;
        return mulRotation(
                1.0f - 2.0f * (yy + zz),
                2.0f * (xy + wz),
                2.0f * (xz - wy),
                2.0f * (xy - wz),
                1.0f - 2.0f * (xx + zz),
                2.0f * (yz + wx),
                2.0f * (xz + wy),
                2.0f * (yz - wx),
                1.0f - 2.0f * (xx + yy));
    }

    /**
     * Multiplies this matrix by the supplied matrix in place.
     *
     * @param other the other
     * @return this matrix4 for chaining
     */
    public Matrix4 multiply(Matrix4 other) {
        return mul(other);
    }

    /**
     * Sets the mul and returns this matrix4.
     *
     * @param other the other
     * @return this matrix4 for chaining
     */
    public Matrix4 mul(Matrix4 other) {
        return setToMul(this, other);
    }

    /**
     * Sets the to mul.
     *
     * @param left the left
     * @param right the right
     * @return this matrix4 for chaining
     */
    public Matrix4 setToMul(Matrix4 left, Matrix4 right) {
        if (MathAcceleration.matrix4Mul(left.values, right.values, values)) {
            return this;
        }
        float m00 = left.values[0] * right.values[0]
                + left.values[4] * right.values[1]
                + left.values[8] * right.values[2]
                + left.values[12] * right.values[3];
        float m01 = left.values[1] * right.values[0]
                + left.values[5] * right.values[1]
                + left.values[9] * right.values[2]
                + left.values[13] * right.values[3];
        float m02 = left.values[2] * right.values[0]
                + left.values[6] * right.values[1]
                + left.values[10] * right.values[2]
                + left.values[14] * right.values[3];
        float m03 = left.values[3] * right.values[0]
                + left.values[7] * right.values[1]
                + left.values[11] * right.values[2]
                + left.values[15] * right.values[3];
        float m10 = left.values[0] * right.values[4]
                + left.values[4] * right.values[5]
                + left.values[8] * right.values[6]
                + left.values[12] * right.values[7];
        float m11 = left.values[1] * right.values[4]
                + left.values[5] * right.values[5]
                + left.values[9] * right.values[6]
                + left.values[13] * right.values[7];
        float m12 = left.values[2] * right.values[4]
                + left.values[6] * right.values[5]
                + left.values[10] * right.values[6]
                + left.values[14] * right.values[7];
        float m13 = left.values[3] * right.values[4]
                + left.values[7] * right.values[5]
                + left.values[11] * right.values[6]
                + left.values[15] * right.values[7];
        float m20 = left.values[0] * right.values[8]
                + left.values[4] * right.values[9]
                + left.values[8] * right.values[10]
                + left.values[12] * right.values[11];
        float m21 = left.values[1] * right.values[8]
                + left.values[5] * right.values[9]
                + left.values[9] * right.values[10]
                + left.values[13] * right.values[11];
        float m22 = left.values[2] * right.values[8]
                + left.values[6] * right.values[9]
                + left.values[10] * right.values[10]
                + left.values[14] * right.values[11];
        float m23 = left.values[3] * right.values[8]
                + left.values[7] * right.values[9]
                + left.values[11] * right.values[10]
                + left.values[15] * right.values[11];
        float m30 = left.values[0] * right.values[12]
                + left.values[4] * right.values[13]
                + left.values[8] * right.values[14]
                + left.values[12] * right.values[15];
        float m31 = left.values[1] * right.values[12]
                + left.values[5] * right.values[13]
                + left.values[9] * right.values[14]
                + left.values[13] * right.values[15];
        float m32 = left.values[2] * right.values[12]
                + left.values[6] * right.values[13]
                + left.values[10] * right.values[14]
                + left.values[14] * right.values[15];
        float m33 = left.values[3] * right.values[12]
                + left.values[7] * right.values[13]
                + left.values[11] * right.values[14]
                + left.values[15] * right.values[15];
        values[0] = m00;
        values[1] = m01;
        values[2] = m02;
        values[3] = m03;
        values[4] = m10;
        values[5] = m11;
        values[6] = m12;
        values[7] = m13;
        values[8] = m20;
        values[9] = m21;
        values[10] = m22;
        values[11] = m23;
        values[12] = m30;
        values[13] = m31;
        values[14] = m32;
        values[15] = m33;
        return this;
    }

    private Matrix4 mulRotation(float r00, float r10, float r20,
            float r01, float r11, float r21,
            float r02, float r12, float r22) {
        float m00 = values[0] * r00 + values[4] * r10
                + values[8] * r20;
        float m01 = values[1] * r00 + values[5] * r10
                + values[9] * r20;
        float m02 = values[2] * r00 + values[6] * r10
                + values[10] * r20;
        float m03 = values[3] * r00 + values[7] * r10
                + values[11] * r20;
        float m10 = values[0] * r01 + values[4] * r11
                + values[8] * r21;
        float m11 = values[1] * r01 + values[5] * r11
                + values[9] * r21;
        float m12 = values[2] * r01 + values[6] * r11
                + values[10] * r21;
        float m13 = values[3] * r01 + values[7] * r11
                + values[11] * r21;
        float m20 = values[0] * r02 + values[4] * r12
                + values[8] * r22;
        float m21 = values[1] * r02 + values[5] * r12
                + values[9] * r22;
        float m22 = values[2] * r02 + values[6] * r12
                + values[10] * r22;
        float m23 = values[3] * r02 + values[7] * r12
                + values[11] * r22;
        values[0] = m00;
        values[1] = m01;
        values[2] = m02;
        values[3] = m03;
        values[4] = m10;
        values[5] = m11;
        values[6] = m12;
        values[7] = m13;
        values[8] = m20;
        values[9] = m21;
        values[10] = m22;
        values[11] = m23;
        return this;
    }

    /**
     * Inverts this matrix.
     *
     * @return this matrix4 for chaining
     * @throws FdxException when this matrix is singular
     */
    public Matrix4 invert() {
        // Keep the calculation in float while expanding the determinant and cofactors directly.
        // This ordering avoids the premature 2x2-cofactor cancellation that can classify valid
        // projection-view matrices with large translations as singular.
        float a00 = values[0];
        float a01 = values[4];
        float a02 = values[8];
        float a03 = values[12];
        float a10 = values[1];
        float a11 = values[5];
        float a12 = values[9];
        float a13 = values[13];
        float a20 = values[2];
        float a21 = values[6];
        float a22 = values[10];
        float a23 = values[14];
        float a30 = values[3];
        float a31 = values[7];
        float a32 = values[11];
        float a33 = values[15];

        float determinant = a30 * a21 * a12 * a03 - a20 * a31 * a12 * a03
                - a30 * a11 * a22 * a03 + a10 * a31 * a22 * a03
                + a20 * a11 * a32 * a03 - a10 * a21 * a32 * a03
                - a30 * a21 * a02 * a13 + a20 * a31 * a02 * a13
                + a30 * a01 * a22 * a13 - a00 * a31 * a22 * a13
                - a20 * a01 * a32 * a13 + a00 * a21 * a32 * a13
                + a30 * a11 * a02 * a23 - a10 * a31 * a02 * a23
                - a30 * a01 * a12 * a23 + a00 * a31 * a12 * a23
                + a10 * a01 * a32 * a23 - a00 * a11 * a32 * a23
                - a20 * a11 * a02 * a33 + a10 * a21 * a02 * a33
                + a20 * a01 * a12 * a33 - a00 * a21 * a12 * a33
                - a10 * a01 * a22 * a33 + a00 * a11 * a22 * a33;
        if (determinant == 0.0f || !Float.isFinite(determinant)) {
            return invertWithPivoting();
        }
        float inverseDeterminant = 1.0f / determinant;

        float r00 = a12 * a23 * a31 - a13 * a22 * a31 + a13 * a21 * a32
                - a11 * a23 * a32 - a12 * a21 * a33 + a11 * a22 * a33;
        float r01 = a03 * a22 * a31 - a02 * a23 * a31 - a03 * a21 * a32
                + a01 * a23 * a32 + a02 * a21 * a33 - a01 * a22 * a33;
        float r02 = a02 * a13 * a31 - a03 * a12 * a31 + a03 * a11 * a32
                - a01 * a13 * a32 - a02 * a11 * a33 + a01 * a12 * a33;
        float r03 = a03 * a12 * a21 - a02 * a13 * a21 - a03 * a11 * a22
                + a01 * a13 * a22 + a02 * a11 * a23 - a01 * a12 * a23;
        float r10 = a13 * a22 * a30 - a12 * a23 * a30 - a13 * a20 * a32
                + a10 * a23 * a32 + a12 * a20 * a33 - a10 * a22 * a33;
        float r11 = a02 * a23 * a30 - a03 * a22 * a30 + a03 * a20 * a32
                - a00 * a23 * a32 - a02 * a20 * a33 + a00 * a22 * a33;
        float r12 = a03 * a12 * a30 - a02 * a13 * a30 - a03 * a10 * a32
                + a00 * a13 * a32 + a02 * a10 * a33 - a00 * a12 * a33;
        float r13 = a02 * a13 * a20 - a03 * a12 * a20 + a03 * a10 * a22
                - a00 * a13 * a22 - a02 * a10 * a23 + a00 * a12 * a23;
        float r20 = a11 * a23 * a30 - a13 * a21 * a30 + a13 * a20 * a31
                - a10 * a23 * a31 - a11 * a20 * a33 + a10 * a21 * a33;
        float r21 = a03 * a21 * a30 - a01 * a23 * a30 - a03 * a20 * a31
                + a00 * a23 * a31 + a01 * a20 * a33 - a00 * a21 * a33;
        float r22 = a01 * a13 * a30 - a03 * a11 * a30 + a03 * a10 * a31
                - a00 * a13 * a31 - a01 * a10 * a33 + a00 * a11 * a33;
        float r23 = a03 * a11 * a20 - a01 * a13 * a20 - a03 * a10 * a21
                + a00 * a13 * a21 + a01 * a10 * a23 - a00 * a11 * a23;
        float r30 = a12 * a21 * a30 - a11 * a22 * a30 - a12 * a20 * a31
                + a10 * a22 * a31 + a11 * a20 * a32 - a10 * a21 * a32;
        float r31 = a01 * a22 * a30 - a02 * a21 * a30 + a02 * a20 * a31
                - a00 * a22 * a31 - a01 * a20 * a32 + a00 * a21 * a32;
        float r32 = a02 * a11 * a30 - a01 * a12 * a30 - a02 * a10 * a31
                + a00 * a12 * a31 + a01 * a10 * a32 - a00 * a11 * a32;
        float r33 = a01 * a12 * a20 - a02 * a11 * a20 + a02 * a10 * a21
                - a00 * a12 * a21 - a01 * a10 * a22 + a00 * a11 * a22;

        values[0] = r00 * inverseDeterminant;
        values[1] = r10 * inverseDeterminant;
        values[2] = r20 * inverseDeterminant;
        values[3] = r30 * inverseDeterminant;
        values[4] = r01 * inverseDeterminant;
        values[5] = r11 * inverseDeterminant;
        values[6] = r21 * inverseDeterminant;
        values[7] = r31 * inverseDeterminant;
        values[8] = r02 * inverseDeterminant;
        values[9] = r12 * inverseDeterminant;
        values[10] = r22 * inverseDeterminant;
        values[11] = r32 * inverseDeterminant;
        values[12] = r03 * inverseDeterminant;
        values[13] = r13 * inverseDeterminant;
        values[14] = r23 * inverseDeterminant;
        values[15] = r33 * inverseDeterminant;
        return this;
    }

    /**
     * Uses float-only Gauss-Jordan elimination when the expanded determinant loses all significant bits. The scratch
     * storage is retained by this mutable matrix so repeated camera updates do not allocate each frame.
     */
    private Matrix4 invertWithPivoting() {
        if (inversionScratch == null) {
            inversionScratch = new float[32];
        }

        for (int row = 0; row < 4; row++) {
            int rowOffset = row * 8;
            for (int column = 0; column < 4; column++) {
                inversionScratch[rowOffset + column] = values[column * 4 + row];
                inversionScratch[rowOffset + 4 + column] = row == column ? 1.0f : 0.0f;
            }
        }

        for (int column = 0; column < 4; column++) {
            int pivotRow = column;
            float pivotMagnitude = Math.abs(inversionScratch[pivotRow * 8 + column]);
            for (int row = column + 1; row < 4; row++) {
                float magnitude = Math.abs(inversionScratch[row * 8 + column]);
                if (magnitude > pivotMagnitude) {
                    pivotRow = row;
                    pivotMagnitude = magnitude;
                }
            }
            if (pivotMagnitude == 0.0f || !Float.isFinite(pivotMagnitude)) {
                throw new FdxException("Matrix4 is not invertible");
            }

            int pivotOffset = pivotRow * 8;
            int columnOffset = column * 8;
            if (pivotRow != column) {
                for (int index = 0; index < 8; index++) {
                    float value = inversionScratch[columnOffset + index];
                    inversionScratch[columnOffset + index] = inversionScratch[pivotOffset + index];
                    inversionScratch[pivotOffset + index] = value;
                }
            }

            float inversePivot = 1.0f / inversionScratch[columnOffset + column];
            if (!Float.isFinite(inversePivot)) {
                throw new FdxException("Matrix4 is not invertible");
            }
            for (int index = 0; index < 8; index++) {
                inversionScratch[columnOffset + index] *= inversePivot;
            }
            inversionScratch[columnOffset + column] = 1.0f;

            for (int row = 0; row < 4; row++) {
                if (row == column) {
                    continue;
                }
                int rowOffset = row * 8;
                float factor = inversionScratch[rowOffset + column];
                if (factor == 0.0f) {
                    continue;
                }
                for (int index = 0; index < 8; index++) {
                    inversionScratch[rowOffset + index] -= factor * inversionScratch[columnOffset + index];
                }
                inversionScratch[rowOffset + column] = 0.0f;
            }
        }

        for (int row = 0; row < 4; row++) {
            int rowOffset = row * 8 + 4;
            for (int column = 0; column < 4; column++) {
                if (!Float.isFinite(inversionScratch[rowOffset + column])) {
                    throw new FdxException("Matrix4 is not invertible");
                }
            }
        }
        for (int row = 0; row < 4; row++) {
            int rowOffset = row * 8 + 4;
            for (int column = 0; column < 4; column++) {
                values[column * 4 + row] = inversionScratch[rowOffset + column];
            }
        }
        return this;
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public float[] values() {
        return values.clone();
    }

    /**
     * Copies the values into the target array.
     *
     * @param target the target values
     * @param offset the target offset
     * @return this matrix4 for chaining
     */
    public Matrix4 copyValues(float[] target, int offset) {
        if (target == null || offset < 0 || target.length - offset < VALUE_COUNT) {
            throw new FdxException("Matrix4 target requires 16 values");
        }
        System.arraycopy(values, 0, target, offset, VALUE_COUNT);
        return this;
    }

    /**
     * Runs the transform position step.
     *
     * @param position the position
     * @return the transform position
     */
    public Vector3 transformPosition(Vector3 position) {
        return transformPosition(position, new Vector3());
    }

    /**
     * Runs the transform position step.
     *
     * @param position the position
     * @param out the out
     * @return the transform position
     */
    public Vector3 transformPosition(Vector3 position, Vector3 out) {
        float x = position.x();
        float y = position.y();
        float z = position.z();
        return out.set(
                values[0] * x + values[4] * y + values[8] * z + values[12],
                values[1] * x + values[5] * y + values[9] * z + values[13],
                values[2] * x + values[6] * y + values[10] * z + values[14]);
    }

    /**
     * Transforms a position using homogeneous coordinates and divides the result by W.
     *
     * @param position the position
     * @return the projectively transformed position
     */
    public Vector3 transformProjective(Vector3 position) {
        return transformProjective(position, new Vector3());
    }

    /**
     * Transforms a position using homogeneous coordinates and divides the result by W.
     *
     * @param position the position
     * @param out the output vector, which may be the input vector
     * @return the output vector
     * @throws FdxException when the transformed W coordinate is zero
     */
    public Vector3 transformProjective(Vector3 position, Vector3 out) {
        float x = position.x();
        float y = position.y();
        float z = position.z();
        float transformedX = values[0] * x + values[4] * y + values[8] * z + values[12];
        float transformedY = values[1] * x + values[5] * y + values[9] * z + values[13];
        float transformedZ = values[2] * x + values[6] * y + values[10] * z + values[14];
        float transformedW = values[3] * x + values[7] * y + values[11] * z + values[15];
        if (transformedW == 0.0f) {
            throw new FdxException("Projective transform produced zero W");
        }
        float inverseW = 1.0f / transformedW;
        return out.set(transformedX * inverseW, transformedY * inverseW, transformedZ * inverseW);
    }

    /**
     * Sets the transform positions and returns this matrix4.
     *
     * @param positions the positions
     * @param offset the offset
     * @param count the count
     * @param stride the stride
     * @return this matrix4 for chaining
     */
    public Matrix4 transformPositions(float[] positions, int offset, int count, int stride) {
        checkTransformArray(positions, offset, count, stride);
        if (count == 0) {
            return this;
        }
        if (MathAcceleration.matrix4TransformPositions(values, positions, offset, count, stride)) {
            return this;
        }
        int index = offset;
        for (int i = 0; i < count; i++) {
            float x = positions[index];
            float y = positions[index + 1];
            float z = positions[index + 2];
            positions[index] = values[0] * x + values[4] * y + values[8] * z + values[12];
            positions[index + 1] = values[1] * x + values[5] * y + values[9] * z + values[13];
            positions[index + 2] = values[2] * x + values[6] * y + values[10] * z + values[14];
            index += stride;
        }
        return this;
    }

    /**
     * Runs the transform direction step.
     *
     * @param direction the direction
     * @return the transform direction
     */
    public Vector3 transformDirection(Vector3 direction) {
        return transformDirection(direction, new Vector3());
    }

    /**
     * Runs the transform direction step.
     *
     * @param direction the direction
     * @param out the out
     * @return the transform direction
     */
    public Vector3 transformDirection(Vector3 direction, Vector3 out) {
        float x = direction.x();
        float y = direction.y();
        float z = direction.z();
        Vector3 transformed = out.set(
                values[0] * x + values[4] * y + values[8] * z,
                values[1] * x + values[5] * y + values[9] * z,
                values[2] * x + values[6] * y + values[10] * z);
        float len = transformed.length();
        if (len == 0.0f) {
            return transformed.set(0.0f, 0.0f, 0.0f);
        }
        float invLen = 1.0f / len;
        return transformed.set(transformed.x() * invLen, transformed.y() * invLen, transformed.z() * invLen);
    }

    private static void checkTransformArray(float[] values, int offset, int count, int stride) {
        if (values == null) {
            throw new FdxException("Transform positions cannot be null");
        }
        if (offset < 0) {
            throw new FdxException("Transform position offset cannot be negative");
        }
        if (count < 0) {
            throw new FdxException("Transform position count cannot be negative");
        }
        if (stride < 3) {
            throw new FdxException("Transform position stride must be at least 3");
        }
        if (count == 0) {
            if (offset > values.length) {
                throw new FdxException("Transform position offset exceeds array length");
            }
            return;
        }
        long lastIndex = (long)offset + ((long)count - 1L) * (long)stride + 2L;
        if (lastIndex >= values.length) {
            throw new FdxException("Transform positions exceed array length");
        }
    }
}
