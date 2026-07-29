package io.github.libfdx.ecs.schema;

/** Standard property controls understood by descriptor-driven hosts. */
public enum EcsPropertyKind {
    BOOLEAN,
    INTEGER,
    FLOAT,
    TEXT,
    ENUM,
    VECTOR2,
    VECTOR3,
    VECTOR4,
    COLOR3,
    COLOR4,
    ASSET,
    ENTITY_REFERENCE
}
