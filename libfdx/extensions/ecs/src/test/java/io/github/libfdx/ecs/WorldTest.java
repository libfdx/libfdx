package io.github.libfdx.ecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.ecs.component.ComponentMapper;
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
        world.add(entity, new Transform(1.0f, 2.0f));
        world.add(entity, new Velocity(3.0f, 4.0f));

        assertFalse(world.isAttached(entity));
        assertEquals(0, world.entityCount());

        world.flushCommands();

        assertTrue(world.isAttached(entity));
        assertEquals(1, world.entityCount());
        assertEquals(1.0f, world.require(entity, Transform.class).x);
        assertEquals(4.0f, world.require(entity, Velocity.class).y);
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
    void returnsStableMappersAndIteratesDenseComponents() {
        World world = new World();
        ComponentMapper<Transform> firstMapper = world.mapper(Transform.class);
        ComponentMapper<Transform> secondMapper = world.mapper(Transform.class);
        int a = world.createEntity();
        int b = world.createEntity();
        world.add(a, new Transform(1.0f, 0.0f));
        world.add(b, new Transform(2.0f, 0.0f));
        world.flushCommands();

        assertSame(firstMapper, secondMapper);
        assertEquals(2, firstMapper.size());
        assertEquals(a, firstMapper.entityAt(0));
        assertEquals(1.0f, firstMapper.componentAt(0).x);
        assertEquals(b, firstMapper.entityAt(1));
    }

    @Test
    void updatesEntityListsForAllOneAnyAndExcludeMatchers() {
        World world = new World();
        int moving = world.createEntity();
        int named = world.createEntity();
        int ui = world.createEntity();
        world.add(moving, new Transform(0.0f, 0.0f));
        world.add(moving, new Velocity(1.0f, 0.0f));
        world.add(named, new Transform(0.0f, 0.0f));
        world.add(named, new Name("box"));
        world.add(ui, new UiEntity());
        world.add(ui, new EditorOnly());

        EntityList movingEntities = world.entities(world.matcher().all(Transform.class, Velocity.class));
        EntityList exactlyOneIdentity = world.entities(world.matcher().one(Name.class, UiEntity.class));
        EntityList anyRenderable = world.entities(world.matcher().any(Name.class, Velocity.class));
        EntityList runtimeUi = world.entities(world.matcher().all(UiEntity.class).exclude(EditorOnly.class));

        world.flushCommands();

        assertEquals(1, movingEntities.size());
        assertEquals(moving, movingEntities.entityAt(0));
        assertEquals(2, exactlyOneIdentity.size());
        assertEquals(2, anyRenderable.size());
        assertTrue(runtimeUi.isEmpty());

        world.remove(moving, Velocity.class);
        world.flushCommands();

        assertTrue(movingEntities.isEmpty());
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
        world.add(entity, new Transform(1.0f, 2.0f));
        EntityList transforms = world.entities(world.matcher().all(Transform.class));
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

        assertThrows(IllegalStateException.class, () -> world.get(123, Transform.class));
    }

    static final class Transform {
        float x;
        float y;

        Transform(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class Velocity {
        float x;
        float y;

        Velocity(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class Name {
        final String value;

        Name(String value) {
            this.value = value;
        }
    }

    static final class UiEntity {
    }

    static final class EditorOnly {
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
