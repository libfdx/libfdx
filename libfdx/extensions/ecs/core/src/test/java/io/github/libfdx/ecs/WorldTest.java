package io.github.libfdx.ecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.collections.IntArray;
import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.ecs.component.ComponentMapper;
import io.github.libfdx.ecs.component.GameComponent;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.component.UiComponent;
import io.github.libfdx.ecs.entity.EntityList;
import io.github.libfdx.ecs.event.Event;
import io.github.libfdx.ecs.manager.Manager;
import io.github.libfdx.ecs.system.System;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class WorldTest {
    @Test
    void createsEntitiesAndComponentsThroughDeferredCommands() {
        World world = new World();

        int entity = world.createEntity();
        world.add(entity, new TransformComponent(1.0f, 2.0f, 0.0f));
        world.add(entity, new VelocityComponent(3.0f, 4.0f));

        assertFalse(world.isAttached(entity));
        assertEquals(0, world.entityCount());

        world.flushCommands();

        assertTrue(world.isAttached(entity));
        assertEquals(1, world.entityCount());
        assertEquals(1.0f, world.require(entity, TransformComponent.class).transform.x());
        assertEquals(4.0f, world.require(entity, VelocityComponent.class).y);
    }

    @Test
    void reusesEntityIndexWithDifferentHandleAfterDestroy() {
        World world = new World();
        int first = world.createEntity();
        world.flushCommands();

        world.destroyEntity(first);
        world.flushCommands();

        int second = world.createEntity();
        world.flushCommands();

        assertFalse(world.isAttached(first));
        assertTrue(world.isAttached(second));
        assertNotEquals(first, second);
    }

    @Test
    void collectsEntitiesAndExposesComponentTypesWithoutAllocatingResults() {
        World world = new World();
        int first = world.createEntity();
        int second = world.createEntity();
        world.add(first, new TransformComponent());
        world.add(first, new VelocityComponent(1.0f, 2.0f));
        world.add(second, new NameComponent("second"));
        world.flushCommands();

        IntArray entities = new IntArray(4);
        assertSame(entities, world.collectEntities(entities));
        assertEquals(2, entities.size());
        assertEquals(first, entities.get(0));
        assertEquals(second, entities.get(1));
        assertEquals(2, world.componentTypeCount(first));
        assertEquals(TransformComponent.class, world.componentType(first, 0));
        assertEquals(VelocityComponent.class, world.componentType(first, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> world.componentType(first, 2));

        world.destroyEntity(first);
        world.flushCommands();
        world.collectEntities(entities);

        assertEquals(1, entities.size());
        assertEquals(second, entities.get(0));
        assertThrows(IllegalStateException.class, () -> world.componentTypeCount(first));
    }

    @Test
    void returnsStableMappersAndIteratesDenseComponents() {
        World world = new World();
        ComponentMapper<TransformComponent> firstMapper = world.mapper(TransformComponent.class);
        ComponentMapper<TransformComponent> secondMapper = world.mapper(TransformComponent.class);
        int a = world.createEntity();
        int b = world.createEntity();
        world.add(a, new TransformComponent(1.0f, 0.0f, 0.0f));
        world.add(b, new TransformComponent(2.0f, 0.0f, 0.0f));
        world.flushCommands();

        assertSame(firstMapper, secondMapper);
        assertEquals(2, firstMapper.size());
        assertEquals(a, firstMapper.entityAt(0));
        assertEquals(1.0f, firstMapper.componentAt(0).transform.x());
        assertEquals(b, firstMapper.entityAt(1));
    }

    @Test
    void updatesEntityListsForAllOneAnyAndExcludeMatchers() {
        World world = new World();
        int moving = world.createEntity();
        int named = world.createEntity();
        int ui = world.createEntity();
        world.add(moving, new TransformComponent(0.0f, 0.0f, 0.0f));
        world.add(moving, new VelocityComponent(1.0f, 0.0f));
        world.add(named, new TransformComponent(0.0f, 0.0f, 0.0f));
        world.add(named, new NameComponent("box"));
        world.add(ui, new UiLayoutComponent());
        world.add(ui, new UiRenderableComponent());
        world.add(ui, new EditorOnlyComponent());

        EntityList movingEntities = world.entities(
            world.matcher().all(TransformComponent.class, VelocityComponent.class)
        );
        EntityList exactlyOneIdentity = world.entities(
            world.matcher().one(NameComponent.class, UiLayoutComponent.class)
        );
        EntityList anyRenderable = world.entities(
            world.matcher().any(NameComponent.class, VelocityComponent.class)
        );
        EntityList runtimeUi = world.entities(
            world.matcher().all(UiLayoutComponent.class, UiRenderableComponent.class)
                .exclude(EditorOnlyComponent.class)
        );

        world.flushCommands();

        assertEquals(1, movingEntities.size());
        assertEquals(moving, movingEntities.entityAt(0));
        assertEquals(2, exactlyOneIdentity.size());
        assertEquals(2, anyRenderable.size());
        assertTrue(runtimeUi.isEmpty());

        world.remove(moving, VelocityComponent.class);
        world.flushCommands();

        assertTrue(movingEntities.isEmpty());
    }

    @Test
    void routesSameNamedEntitiesByGameAndUiComponents() {
        World world = new World();
        int game = world.createEntity();
        int ui = world.createEntity();
        world.add(game, new NameComponent("Shared Name"));
        world.add(game, new TransformComponent(0.0f, 0.0f, 0.0f));
        world.add(game, new GameComponent());
        world.add(ui, new NameComponent("Shared Name"));
        world.add(ui, new TransformComponent(0.0f, 0.0f, 0.0f));
        world.add(ui, new UiComponent());

        EntityList gameEntities = world.entities(world.matcher().all(GameComponent.class));
        EntityList uiEntities = world.entities(world.matcher().all(UiComponent.class));
        world.flushCommands();

        assertEquals(1, gameEntities.size());
        assertEquals(1, uiEntities.size());
        assertTrue(contains(gameEntities, game));
        assertFalse(contains(gameEntities, ui));
        assertFalse(contains(uiEntities, game));
        assertTrue(contains(uiEntities, ui));

        world.remove(game, GameComponent.class);
        world.add(game, new UiComponent());
        world.remove(ui, UiComponent.class);
        world.add(ui, new GameComponent());
        world.flushCommands();

        assertEquals(1, gameEntities.size());
        assertEquals(1, uiEntities.size());
        assertTrue(contains(gameEntities, ui));
        assertFalse(contains(gameEntities, game));
        assertTrue(contains(uiEntities, game));
        assertFalse(contains(uiEntities, ui));
    }

    @Test
    void dispatchesEventsWithRegisteredOneShotAndProcessedCallbacks() {
        World world = new World();
        ArrayList<String> calls = new ArrayList<>();
        world.events().addListener(7, (eventWorld, event) -> {
            calls.add("registered:" + event.intValue());
            eventWorld.events().dispatch(eventWorld.events().obtain(8).intValue(2));
        });
        world.events().addListener(8, (eventWorld, event) -> calls.add("next:" + event.intValue()));

        Event event = world.events().obtain(7).intValue(1);
        world.events().dispatch(event, (eventWorld, received) -> calls.add("one:" + received.intValue()),
            () -> calls.add("processed"));
        world.events().flush();

        assertEquals(1, world.events().queuedCount());
        assertEquals("registered:1", calls.get(0));
        assertEquals("one:1", calls.get(1));
        assertEquals("processed", calls.get(2));

        world.events().flush();

        assertEquals(0, world.events().queuedCount());
        assertEquals("next:2", calls.get(3));
    }

    @Test
    void attachesManagersAndSystemsOnFlushAndUpdatesEnabledSystemsOnly() {
        World world = new World();
        TestManager manager = world.addManager(new TestManager());
        TestSystem system = world.addSystem(new TestSystem());

        assertNull(world.getManager(TestManager.class));
        assertNull(world.getSystem(TestSystem.class));

        world.flushCommands();

        assertSame(manager, world.getManager(TestManager.class));
        assertSame(system, world.getSystem(TestSystem.class));
        assertEquals(1, manager.attachCount);
        assertEquals(1, system.attachCount);

        world.update(0.5f);

        assertEquals(1, system.updateCount);
        assertEquals(0.5f, system.lastDelta);

        system.setEnabled(false);
        world.update(0.25f);

        assertEquals(1, system.updateCount);

        world.removeSystem(TestSystem.class);
        world.removeManager(TestManager.class);
        world.flushCommands();

        assertEquals(1, system.detachCount);
        assertEquals(1, manager.detachCount);
    }

    @Test
    void typedUpdateRunsOnlyMatchingSystems() {
        World world = new World();
        TypedSystemA systemA = world.addSystem(new TypedSystemA());
        TypedSystemB systemB = world.addSystem(new TypedSystemB());
        world.flushCommands();

        world.update(0.1f, PipelineA.class);

        assertEquals(1, systemA.updateCount);
        assertEquals(0, systemB.updateCount);

        world.update(0.2f, PipelineB.class);

        assertEquals(1, systemA.updateCount);
        assertEquals(1, systemB.updateCount);
    }

    @Test
    void clearsEntitiesComponentsManagersSystemsEventsAndLists() {
        World world = new World();
        int entity = world.createEntity();
        world.add(entity, new TransformComponent(1.0f, 2.0f, 0.0f));
        EntityList transforms = world.entities(world.matcher().all(TransformComponent.class));
        TestManager manager = world.addManager(new TestManager());
        TestSystem system = world.addSystem(new TestSystem());
        world.events().addListener(1, (eventWorld, event) -> { });
        world.events().dispatch(world.events().obtain(1));
        world.flushCommands();

        world.clear();
        world.flushCommands();

        assertFalse(world.isAttached(entity));
        assertEquals(0, world.entityCount());
        assertTrue(transforms.isEmpty());
        assertNull(world.getManager(TestManager.class));
        assertNull(world.getSystem(TestSystem.class));
        assertEquals(1, manager.detachCount);
        assertEquals(1, system.detachCount);
        assertEquals(0, world.events().queuedCount());
    }

    @Test
    void rejectsReadsForDetachedEntities() {
        World world = new World();

        assertThrows(IllegalStateException.class, () -> world.get(123, TransformComponent.class));
    }

    static final class VelocityComponent implements Component {
        float x;
        float y;

        VelocityComponent(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class NameComponent implements Component {
        final String value;

        NameComponent(String value) {
            this.value = value;
        }
    }

    static final class UiLayoutComponent implements Component {
    }

    static final class UiRenderableComponent implements Component {
    }

    static final class EditorOnlyComponent implements Component {
    }

    private static boolean contains(EntityList entities, int expected) {
        for (int i = 0; i < entities.size(); i++) {
            if (entities.entityAt(i) == expected) {
                return true;
            }
        }
        return false;
    }

    static final class TestManager implements Manager {
        int attachCount;
        int detachCount;

        public void onAttach(World world) {
            attachCount++;
        }

        public void onDetach(World world) {
            detachCount++;
        }
    }

    static final class TestSystem implements System {
        boolean enabled = true;
        int attachCount;
        int detachCount;
        int updateCount;
        float lastDelta;
        World world;

        public void onAttach(World world) {
            this.world = world;
            attachCount++;
        }

        public void onDetach(World world) {
            this.world = null;
            detachCount++;
        }

        public void update() {
            updateCount++;
            lastDelta = world.deltaTime();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    interface PipelineA extends System {
    }

    interface PipelineB extends System {
    }

    static final class TypedSystemA implements PipelineA {
        int updateCount;

        public void onAttach(World world) {
        }

        public void onDetach(World world) {
        }

        public void update() {
            updateCount++;
        }

        public boolean isEnabled() {
            return true;
        }

        public void setEnabled(boolean enabled) {
        }
    }

    static final class TypedSystemB implements PipelineB {
        int updateCount;

        public void onAttach(World world) {
        }

        public void onDetach(World world) {
        }

        public void update() {
            updateCount++;
        }

        public boolean isEnabled() {
            return true;
        }

        public void setEnabled(boolean enabled) {
        }
    }
}
