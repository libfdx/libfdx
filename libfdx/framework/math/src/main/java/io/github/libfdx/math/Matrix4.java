package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.internal.MathAcceleration;

/**
 * Represents a matrix4.
 *
 * @author xpenatan
 */
public final class Matrix4 {
    public static final int VALUE_COUNT = 16;
    public static final Matrix4 IDENTITY = identity();

    private final float[] values = new float[VALUE_COUNT];

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
     * Creates a matrix4 from the supplied values.
     *
     * @param values the values
     * @return a new matrix4
     */
    public static Matrix4 of(float[] values) {
        return new Matrix4(values);
    }

    /**
     * Creates a matrix4.
     *
     * @return a new matrix4
     */
    public static Matrix4 identity() {
        return new Matrix4();
    }

    /**
     * Creates a matrix4.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return a new matrix4
     */
    public static Matrix4 translation(float x, float y, float z) {
        return new Matrix4().setToTranslation(x, y, z);
    }

    /**
     * Creates a matrix4.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return a new matrix4
     */
    public static Matrix4 scale(float x, float y, float z) {
        return new Matrix4().setToScale(x, y, z);
    }

    /**
     * Creates a matrix4.
     *
     * @param radians the radians
     * @return a new matrix4
     */
    public static Matrix4 rotationX(float radians) {
        return new Matrix4().setToRotationX(radians);
    }

    /**
     * Creates a matrix4.
     *
     * @param radians the radians
     * @return a new matrix4
     */
    public static Matrix4 rotationY(float radians) {
        return new Matrix4().setToRotationY(radians);
    }

    /**
     * Creates a matrix4.
     *
     * @param radians the radians
     * @return a new matrix4
     */
    public static Matrix4 rotationZ(float radians) {
        return new Matrix4().setToRotationZ(radians);
    }

    /**
     * Creates a matrix4.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     * @return a new matrix4
     */
    public static Matrix4 rotationQuaternion(float x, float y, float z, float w) {
        return new Matrix4().setToRotationQuaternion(x, y, z, w);
    }

    /**
     * Creates a translation, rotation, and scale matrix.
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
     * @return a new matrix4
     */
    public static Matrix4 trs(float translationX, float translationY, float translationZ,
            float rotationX, float rotationY, float rotationZ, float rotationW,
            float scaleX, float scaleY, float scaleZ) {
        return new Matrix4().setToTrs(translationX, translationY, translationZ,
                rotationX, rotationY, rotationZ, rotationW, scaleX, scaleY, scaleZ);
    }

    /**
     * Creates a matrix4.
     *
     * @param fieldOfViewDegrees the field of view degrees
     * @param aspectRatio the aspect ratio
     * @param near the near
     * @param far the far
     * @return a new matrix4
     */
    public static Matrix4 perspective(float fieldOfViewDegrees, float aspectRatio, float near, float far) {
        return new Matrix4().setToPerspective(fieldOfViewDegrees, aspectRatio, near, far);
    }

    /**
     * Creates a matrix4.
     *
     * @param left the left
     * @param right the right
     * @param bottom the bottom
     * @param top the top
     * @param near the near
     * @param far the far
     * @return a new matrix4
     */
    public static Matrix4 orthographic(float left, float right, float bottom, float top, float near, float far) {
        return new Matrix4().setToOrthographic(left, right, bottom, top, near, far);
    }

    /**
     * Creates a matrix4.
     *
     * @param eye the eye
     * @param center the center
     * @param up the up
     * @return a new matrix4
     */
    public static Matrix4 lookAt(Vector3 eye, Vector3 center, Vector3 up) {
        return new Matrix4().setToLookAt(eye, center, up);
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
        if (aspectRatio == 0.0f) {
            throw new FdxException("Perspective camera aspect ratio cannot be zero");
        }
        if (near <= 0.0f || far <= near) {
            throw new FdxException("Perspective camera near/far range is invalid");
        }
        for (int i = 0; i < VALUE_COUNT; i++) {
            values[i] = 0.0f;
        }
        float f = (float)(1.0 / Math.tan(Math.toRadians(fieldOfViewDegrees) * 0.5));
        values[0] = f / aspectRatio;
        values[5] = f;
        values[10] = (far + near) / (near - far);
        values[11] = -1.0f;
        values[14] = (2.0f * far * near) / (near - far);
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
        idt();
        values[0] = 2.0f / (right - left);
        values[5] = 2.0f / (top - bottom);
        values[10] = -2.0f / (far - near);
        values[12] = -(right + left) / (right - left);
        values[13] = -(top + bottom) / (top - bottom);
        values[14] = -(far + near) / (far - near);
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
     * Sets the multiply and returns this matrix4.
     *
     * @param other the other
     * @return this matrix4 for chaining
     */
    public Matrix4 multiply(Matrix4 other) {
        return new Matrix4(this).mul(other);
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

    /**
     * Inverts this matrix.
     *
     * @return this matrix4 for chaining
     * @throws FdxException when this matrix is singular
     */
    public Matrix4 invert() {
        float a00 = values[0];
        float a01 = values[1];
        float a02 = values[2];
        float a03 = values[3];
        float a10 = values[4];
        float a11 = values[5];
        float a12 = values[6];
        float a13 = values[7];
        float a20 = values[8];
        float a21 = values[9];
        float a22 = values[10];
        float a23 = values[11];
        float a30 = values[12];
        float a31 = values[13];
        float a32 = values[14];
        float a33 = values[15];

        float b00 = a00 * a11 - a01 * a10;
        float b01 = a00 * a12 - a02 * a10;
        float b02 = a00 * a13 - a03 * a10;
        float b03 = a01 * a12 - a02 * a11;
        float b04 = a01 * a13 - a03 * a11;
        float b05 = a02 * a13 - a03 * a12;
        float b06 = a20 * a31 - a21 * a30;
        float b07 = a20 * a32 - a22 * a30;
        float b08 = a20 * a33 - a23 * a30;
        float b09 = a21 * a32 - a22 * a31;
        float b10 = a21 * a33 - a23 * a31;
        float b11 = a22 * a33 - a23 * a32;

        float determinant = b00 * b11 - b01 * b10 + b02 * b09
                + b03 * b08 - b04 * b07 + b05 * b06;
        if (determinant == 0.0f || !Float.isFinite(determinant)) {
            throw new FdxException("Matrix4 is not invertible");
        }
        float inverseDeterminant = 1.0f / determinant;

        values[0] = (a11 * b11 - a12 * b10 + a13 * b09) * inverseDeterminant;
        values[1] = (a02 * b10 - a01 * b11 - a03 * b09) * inverseDeterminant;
        values[2] = (a31 * b05 - a32 * b04 + a33 * b03) * inverseDeterminant;
        values[3] = (a22 * b04 - a21 * b05 - a23 * b03) * inverseDeterminant;
        values[4] = (a12 * b08 - a10 * b11 - a13 * b07) * inverseDeterminant;
        values[5] = (a00 * b11 - a02 * b08 + a03 * b07) * inverseDeterminant;
        values[6] = (a32 * b02 - a30 * b05 - a33 * b01) * inverseDeterminant;
        values[7] = (a20 * b05 - a22 * b02 + a23 * b01) * inverseDeterminant;
        values[8] = (a10 * b10 - a11 * b08 + a13 * b06) * inverseDeterminant;
        values[9] = (a01 * b08 - a00 * b10 - a03 * b06) * inverseDeterminant;
        values[10] = (a30 * b04 - a31 * b02 + a33 * b00) * inverseDeterminant;
        values[11] = (a21 * b02 - a20 * b04 - a23 * b00) * inverseDeterminant;
        values[12] = (a11 * b07 - a10 * b09 - a12 * b06) * inverseDeterminant;
        values[13] = (a00 * b09 - a01 * b07 + a02 * b06) * inverseDeterminant;
        values[14] = (a31 * b01 - a30 * b03 - a32 * b00) * inverseDeterminant;
        values[15] = (a20 * b03 - a21 * b01 + a22 * b00) * inverseDeterminant;
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
