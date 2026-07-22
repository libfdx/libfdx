package io.github.libfdx.ecs.tooling.scene;

import io.github.libfdx.core.FdxException;

/** Signals invalid or unappliable ECS scene data. */
public final class EcsSceneException extends FdxException {
    public EcsSceneException(String message) {
        super(message);
    }

    public EcsSceneException(String message, Throwable cause) {
        super(message, cause);
    }
}
