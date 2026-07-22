package io.github.libfdx.ecs.tooling;

/** Identifies why a project world is being rendered. */
public enum EcsRenderPurpose {
    /** Normal game-camera rendering. */
    GAME,
    /** Tool-controlled scene-camera rendering. */
    SCENE
}
