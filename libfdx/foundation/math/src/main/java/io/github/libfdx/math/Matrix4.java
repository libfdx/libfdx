package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.internal.MathAcceleration;

public final class Matrix4 {
    public static final int VALUE_COUNT = 16;
    public static final Matrix4 IDENTITY = identity();

    private final float[] values = new float[VALUE_COUNT];

    public Matrix4() {
        idt();
    }

    public Matrix4(Matrix4 matrix) {
        set(matrix);
    }

    public Matrix4(float[] values) {
        set(values);
    }

    public static Matrix4 of(float[] values) {
        return new Matrix4(values);
    }

    public static Matrix4 identity() {
        return new Matrix4();
    }

    public static Matrix4 translation(float x, float y, float z) {
        return new Matrix4().setToTranslation(x, y, z);
    }

    public static Matrix4 scale(float x, float y, float z) {
        return new Matrix4().setToScale(x, y, z);
    }

    public static Matrix4 rotationX(float radians) {
        return new Matrix4().setToRotationX(radians);
    }

    public static Matrix4 rotationY(float radians) {
        return new Matrix4().setToRotationY(radians);
    }

    public static Matrix4 rotationZ(float radians) {
        return new Matrix4().setToRotationZ(radians);
    }

    public static Matrix4 rotationQuaternion(float x, float y, float z, float w) {
        return new Matrix4().setToRotationQuaternion(x, y, z, w);
    }

    public static Matrix4 perspective(float fieldOfViewDegrees, float aspectRatio, float near, float far) {
        return new Matrix4().setToPerspective(fieldOfViewDegrees, aspectRatio, near, far);
    }

    public static Matrix4 orthographic(float left, float right, float bottom, float top, float near, float far) {
        return new Matrix4().setToOrthographic(left, right, bottom, top, near, far);
    }

    public static Matrix4 lookAt(Vector3 eye, Vector3 center, Vector3 up) {
        return new Matrix4().setToLookAt(eye, center, up);
    }

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

    public Matrix4 set(Matrix4 matrix) {
        if (matrix == null) {
            throw new FdxException("Matrix4 cannot be null");
        }
        System.arraycopy(matrix.values, 0, values, 0, VALUE_COUNT);
        return this;
    }

    public Matrix4 set(float[] source) {
        if (source == null || source.length != VALUE_COUNT) {
            throw new FdxException("Matrix4 requires 16 values");
        }
        System.arraycopy(source, 0, values, 0, VALUE_COUNT);
        return this;
    }

    public Matrix4 setToTranslation(float x, float y, float z) {
        idt();
        values[12] = x;
        values[13] = y;
        values[14] = z;
        return this;
    }

    public Matrix4 setToScale(float x, float y, float z) {
        idt();
        values[0] = x;
        values[5] = y;
        values[10] = z;
        return this;
    }

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

    public Matrix4 setToLookAt(Vector3 eye, Vector3 center, Vector3 up) {
        return setToLookAt(
                eye.x(), eye.y(), eye.z(),
                center.x(), center.y(), center.z(),
                up.x(), up.y(), up.z());
    }

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

    public Matrix4 multiply(Matrix4 other) {
        return new Matrix4(this).mul(other);
    }

    public Matrix4 mul(Matrix4 other) {
        return setToMul(this, other);
    }

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

    public float[] values() {
        return values.clone();
    }

    public Vector3 transformPosition(Vector3 position) {
        return transformPosition(position, new Vector3());
    }

    public Vector3 transformPosition(Vector3 position, Vector3 out) {
        float x = position.x();
        float y = position.y();
        float z = position.z();
        return out.set(
                values[0] * x + values[4] * y + values[8] * z + values[12],
                values[1] * x + values[5] * y + values[9] * z + values[13],
                values[2] * x + values[6] * y + values[10] * z + values[14]);
    }

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

    public Vector3 transformDirection(Vector3 direction) {
        return transformDirection(direction, new Vector3());
    }

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
