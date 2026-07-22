package io.github.libfdx.ecs.tooling.schema;

/** Validates and normalizes project-relative asset references used by typed properties. */
public interface EcsAssetAdapter {
    String normalize(String projectRelativePath);

    boolean accepts(String projectRelativePath);
}
