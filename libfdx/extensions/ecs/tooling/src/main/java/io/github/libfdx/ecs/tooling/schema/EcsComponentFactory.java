package io.github.libfdx.ecs.tooling.schema;

import io.github.libfdx.ecs.component.Component;

/** Creates a default component when a tool adds it to an entity. */
@FunctionalInterface
public interface EcsComponentFactory<T extends Component> {
    T create();
}
