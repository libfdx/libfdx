package io.github.libfdx.ecs.tooling.scene;

import java.util.List;

/** Immutable decoded representation of one libFDX ECS scene. */
public final class EcsSceneDocument {
    private final String projectId;
    private final String sceneId;
    private final List<EcsSceneEntity> entities;

    public EcsSceneDocument(String projectId, String sceneId, List<EcsSceneEntity> entities) {
        if (isBlank(projectId) || isBlank(sceneId) || entities == null) {
            throw new IllegalArgumentException("projectId, sceneId, and entities are required.");
        }
        this.projectId = projectId.trim();
        this.sceneId = sceneId.trim();
        this.entities = List.copyOf(entities);
    }

    public String projectId() {
        return projectId;
    }

    public String sceneId() {
        return sceneId;
    }

    public int entityCount() {
        return entities.size();
    }

    public EcsSceneEntity entity(int index) {
        return entities.get(index);
    }

    public List<EcsSceneEntity> entities() {
        return entities;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
