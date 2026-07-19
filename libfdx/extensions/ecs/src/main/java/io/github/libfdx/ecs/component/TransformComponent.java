package io.github.libfdx.ecs.component;

import io.github.libfdx.ecs.transform.Transform;

/** Default spatial component backed by mutable {@link Transform} values. */
public final class TransformComponent implements Component {
    public final Transform transform;

    public TransformComponent() {
        this(new Transform());
    }

    public TransformComponent(float x, float y, float z) {
        this(new Transform(x, y, z));
    }

    public TransformComponent(Transform transform) {
        if (transform == null) {
            throw new IllegalArgumentException("transform cannot be null.");
        }
        this.transform = transform;
    }

    public TransformComponent set(TransformComponent other) {
        if (other == null) {
            throw new IllegalArgumentException("other cannot be null.");
        }
        transform.set(other.transform);
        return this;
    }

    public TransformComponent copy() {
        return new TransformComponent(transform.copy());
    }
}
