package io.github.libfdx.ecs.schema;

/** Validates and normalizes project-relative asset references used by core scene properties. */
public interface EcsAssetAdapter {
    String normalize(String projectRelativePath);

    boolean accepts(String projectRelativePath);
}
