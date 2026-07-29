package io.github.libfdx.physics.box3d;

import com.github.xpenatan.box3d.B3Quat;
import com.github.xpenatan.box3d.B3Transform;
import com.github.xpenatan.box3d.B3Vec3;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Quaternion;
import io.github.libfdx.math.Vector3;

/**
 * Allocation-free conversions between Box3D and libFDX math types.
 * <p>
 * Every method writes into a caller-owned output object and returns that same object for chaining.
 */
public final class FdxBox3DConverter {

    private FdxBox3DConverter() {
    }

    /** Converts a Box3D vector into a reusable libFDX vector. */
    public static Vector3 toFdx(B3Vec3 source, Vector3 out) {
        requireNonNull(source, "source");
        requireNonNull(out, "out");
        return out.set(source.GetX(), source.GetY(), source.GetZ());
    }

    /** Converts a libFDX vector into a reusable Box3D vector. */
    public static B3Vec3 toBox3D(Vector3 source, B3Vec3 out) {
        requireNonNull(source, "source");
        requireNonNull(out, "out");
        out.Set(source.x(), source.y(), source.z());
        return out;
    }

    /** Converts a Box3D quaternion into a reusable libFDX quaternion. */
    public static Quaternion toFdx(B3Quat source, Quaternion out) {
        requireNonNull(source, "source");
        requireNonNull(out, "out");
        B3Vec3 vector = source.GetV();
        return out.set(vector.GetX(), vector.GetY(), vector.GetZ(), source.GetS());
    }

    /** Converts a libFDX quaternion into a reusable Box3D quaternion. */
    public static B3Quat toBox3D(Quaternion source, B3Quat out) {
        requireNonNull(source, "source");
        requireNonNull(out, "out");
        out.Set(source.x(), source.y(), source.z(), source.w());
        return out;
    }

    /** Converts a rigid Box3D transform into a reusable libFDX matrix. */
    public static Matrix4 toFdx(B3Transform source, Matrix4 out) {
        requireNonNull(source, "source");
        requireNonNull(out, "out");
        B3Vec3 position = source.GetP();
        B3Quat rotation = source.GetQ();
        B3Vec3 vector = rotation.GetV();
        return out.setToTrs(
                position.GetX(), position.GetY(), position.GetZ(),
                vector.GetX(), vector.GetY(), vector.GetZ(), rotation.GetS(),
                1.0f, 1.0f, 1.0f);
    }

    /** Writes a libFDX position and rotation into a reusable Box3D transform. */
    public static B3Transform toBox3D(Vector3 position, Quaternion rotation, B3Transform out) {
        requireNonNull(position, "position");
        requireNonNull(rotation, "rotation");
        requireNonNull(out, "out");
        out.GetP().Set(position.x(), position.y(), position.z());
        out.GetQ().Set(rotation.x(), rotation.y(), rotation.z(), rotation.w());
        return out;
    }

    private static void requireNonNull(Object value, String name) {
        if(value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }
}
