package io.github.libfdx.physics.box2d;

import com.github.xpenatan.box2d.B2Rot;
import com.github.xpenatan.box2d.B2Transform;
import com.github.xpenatan.box2d.B2Vec2;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector2;

/**
 * Allocation-free conversions between Box2D and libfdx math types.
 * <p>
 * Every method writes into a caller-owned output object and returns that same object for chaining.
 */
public final class FdxBox2DConverter {

    private FdxBox2DConverter() {
    }

    /** Converts a Box2D vector into a reusable libfdx vector. */
    public static Vector2 toFdx(B2Vec2 source, Vector2 out) {
        requireNonNull(source, "source");
        requireNonNull(out, "out");
        return out.set(source.GetX(), source.GetY());
    }

    /** Converts a libfdx vector into a reusable Box2D vector. */
    public static B2Vec2 toBox2D(Vector2 source, B2Vec2 out) {
        requireNonNull(source, "source");
        requireNonNull(out, "out");
        out.Set(source.x(), source.y());
        return out;
    }

    /** Converts a rigid Box2D transform into a reusable libfdx matrix. */
    public static Matrix4 toFdx(B2Transform source, Matrix4 out) {
        requireNonNull(source, "source");
        requireNonNull(out, "out");
        B2Vec2 position = source.GetPosition();
        B2Rot rotation = source.GetRotation();
        float halfAngle = rotation.GetAngle() * 0.5f;
        return out.setToTrs(
                position.GetX(), position.GetY(), 0.0f,
                0.0f, 0.0f, (float)Math.sin(halfAngle), (float)Math.cos(halfAngle),
                1.0f, 1.0f, 1.0f);
    }

    /** Writes a libfdx position and rotation angle into a reusable Box2D transform. */
    public static B2Transform toBox2D(Vector2 position, float rotationRadians, B2Transform out) {
        requireNonNull(position, "position");
        requireNonNull(out, "out");
        out.GetPosition().Set(position.x(), position.y());
        out.GetRotation().Set(rotationRadians);
        return out;
    }

    private static void requireNonNull(Object value, String name) {
        if(value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }
}
