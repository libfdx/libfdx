package io.github.libfdx.ecs.tooling.schema;

/** Standard property controls understood by schema-driven tools. */
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
