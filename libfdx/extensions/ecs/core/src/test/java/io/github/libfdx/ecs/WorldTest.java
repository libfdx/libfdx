package io.github.libfdx.ecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
import io.github.libfdx.ecs.manager.CameraManager;
import io.github.libfdx.ecs.manager.Manager;
import io.github.libfdx.ecs.system.RenderSystem;
import io.github.libfdx.ecs.system.System;
import io.github.libfdx.ecs.system.UiRenderSystem;
import io.github.libfdx.ecs.system.UpdateSystem;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.camera.Camera;
import java.util.ArrayList;
import java.util.List;
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
    void discardsPendingCommandsAndInvalidatesReservedEntityHandles() {
        World world = new World();
        int discardedEntity = world.createEntity();
        world.add(discardedEntity, new NameComponent("discarded"));
        TestManager discardedManager = new TestManager();
        TestSystem discardedSystem = new TestSystem();
        world.addManager(discardedManager, TestManager.class);
        world.addSystem(discardedSystem);

        world.discardCommands();
        world.flushCommands();

        assertFalse(world.isAttached(discardedEntity));
        assertEquals(0, world.entityCount());
        assertEquals(0, world.managerCount());
        assertEquals(0, world.systemCount());
        assertEquals(0, discardedManager.attachCount);
        assertEquals(0, discardedSystem.attachCount);
        assertEquals(0, world.commands().size());

        int replacementEntity = world.createEntity();
        world.flushCommands();

        assertNotEquals(discardedEntity, replacementEntity);
        assertTrue(world.isAttached(replacementEntity));
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
        TestManager manager = world.addManager(new TestManager(), TestManager.class);
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
    void registersManagerOnlyOnceForExplicitType() {
        World world = new World();
        TestManager first = new TestManager();
        TestManager pendingDuplicate = new TestManager();

        assertSame(first, world.addManager(first, TestManagerContract.class));
        assertNull(world.addManager(pendingDuplicate, TestManagerContract.class));
        assertNull(world.getManager(TestManagerContract.class));

        world.flushCommands();

        assertSame(first, world.getManager(TestManagerContract.class));
        assertNull(world.getManager(TestManager.class));

        TestManager attachedDuplicate = new TestManager();
        assertNull(world.addManager(attachedDuplicate, TestManagerContract.class));
        world.flushCommands();

        assertSame(first, world.getManager(TestManagerContract.class));
        assertEquals(1, world.managerCount());
        assertEquals(1, first.attachCount);
        assertEquals(0, first.detachCount);
        assertEquals(0, pendingDuplicate.attachCount);
        assertEquals(0, attachedDuplicate.attachCount);
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
    void dispatchesRetainedPhasesInRegistrationOrderAndAttachesOnce() {
        World world = new World();
        ArrayList<String> calls = new ArrayList<>();
        Camera gameCamera = new Camera();
        Camera uiCamera = new Camera();
        world.addManager(new CameraManager().game(gameCamera).ui(uiCamera), CameraManager.class);
        RecordingPhaseSystemA first = world.addSystem(new RecordingPhaseSystemA("a", calls));
        RecordingPhaseSystemB second = world.addSystem(new RecordingPhaseSystemB("b", calls));
        world.flushCommands();

        assertEquals(2, world.systemCount());
        assertEquals(2, world.updateSystemCount());
        assertEquals(2, world.renderSystemCount());
        assertEquals(2, world.uiRenderSystemCount());
        assertEquals(1, first.attachCount);
        assertEquals(1, second.attachCount);

        TestGraphicsFrame frame = new TestGraphicsFrame();
        TestTextureView color = new TestTextureView();
        TestTextureView depth = new TestTextureView();
        calls.clear();
        world.update(0.25f);
        world.render(frame, color, depth, 640, 360, null);
        world.renderUi(frame, color, depth, 640, 360, null);

        assertEquals(
                List.of("update:a", "update:b", "render:a", "render:b", "ui:a", "ui:b"),
                calls);
        assertEquals(0.25f, first.lastDelta);
        assertSame(frame, first.frame);
        assertSame(color, first.colorTarget);
        assertSame(depth, first.depthTarget);
        assertEquals(640, first.width);
        assertEquals(360, first.height);
        assertSame(gameCamera, first.renderCamera);
        assertSame(uiCamera, first.uiRenderCamera);

        first.setEnabled(false);
        calls.clear();
        world.update(0.5f);
        world.render(frame, color, null, 320, 180, null);
        world.renderUi(frame, color, null, 320, 180, null);

        assertEquals(List.of("update:b", "render:b", "ui:b"), calls);
    }

    @Test
    void cameraOverridesWinAndMissingCamerasRemainNull() {
        TestGraphicsFrame frame = new TestGraphicsFrame();
        TestTextureView target = new TestTextureView();
        Camera managedGame = new Camera();
        Camera managedUi = new Camera();
        Camera gameOverride = new Camera();
        Camera uiOverride = new Camera();
        World managedWorld = new World();
        RecordingPhaseSystemA managed =
                managedWorld.addSystem(new RecordingPhaseSystemA("managed", new ArrayList<>()));
        managedWorld.addManager(
                new CameraManager().game(managedGame).ui(managedUi),
                CameraManager.class);
        managedWorld.flushCommands();

        managedWorld.render(frame, target, null, 10, 20, gameOverride);
        managedWorld.renderUi(frame, target, null, 10, 20, uiOverride);

        assertSame(gameOverride, managed.renderCamera);
        assertSame(uiOverride, managed.uiRenderCamera);

        World cameraFreeWorld = new World();
        RecordingPhaseSystemA cameraFree =
                cameraFreeWorld.addSystem(new RecordingPhaseSystemA("free", new ArrayList<>()));
        cameraFreeWorld.flushCommands();

        cameraFreeWorld.render(frame, target, null, 10, 20, null);
        cameraFreeWorld.renderUi(frame, target, null, 10, 20, null);

        assertNull(cameraFree.renderCamera);
        assertNull(cameraFree.uiRenderCamera);
    }

    @Test
    void resolvesThePhaseCameraOnceBeforeDispatch() {
        World world = new World();
        Camera initial = new Camera();
        Camera replacement = new Camera();
        CameraManager cameras = new CameraManager().game(initial);
        CameraSwitchingPhaseSystem first = world.addSystem(
                new CameraSwitchingPhaseSystem("first", new ArrayList<>(), cameras, replacement));
        RecordingPhaseSystemB second =
                world.addSystem(new RecordingPhaseSystemB("second", new ArrayList<>()));
        world.addManager(cameras, CameraManager.class);
        world.flushCommands();

        world.render(new TestGraphicsFrame(), new TestTextureView(), null, 10, 20, null);

        assertSame(initial, first.renderCamera);
        assertSame(initial, second.renderCamera);
        assertSame(replacement, cameras.game());
    }

    @Test
    void renderValidationAppliesOnlyWhenThePhaseHasSystems() {
        World empty = new World();

        assertDoesNotThrow(() -> empty.render(null, null, null, 0, 0, null));
        assertDoesNotThrow(() -> empty.renderUi(null, null, null, 0, 0, null));

        World world = new World();
        RecordingPhaseSystemA system =
                world.addSystem(new RecordingPhaseSystemA("phase", new ArrayList<>()));
        world.flushCommands();
        system.setEnabled(false);
        TestGraphicsFrame frame = new TestGraphicsFrame();
        TestTextureView target = new TestTextureView();

        assertThrows(IllegalArgumentException.class,
                () -> world.render(null, target, null, 1, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> world.render(frame, null, null, 1, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> world.render(frame, target, null, 0, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> world.renderUi(frame, target, null, 1, -1, null));
    }

    @Test
    void renderingDoesNotFlushCommandsEventsOrAdvanceSimulation() {
        World world = new World();
        RecordingPhaseSystemA system =
                world.addSystem(new RecordingPhaseSystemA("phase", new ArrayList<>()));
        world.flushCommands();
        int[] eventCount = {0};
        world.events().addListener(1, (eventWorld, event) -> eventCount[0]++);
        world.events().dispatch(world.events().obtain(1));
        int pendingEntity = world.createEntity();

        world.render(new TestGraphicsFrame(), new TestTextureView(), null, 16, 9, null);

        assertEquals(0, system.updateCount);
        assertFalse(world.isAttached(pendingEntity));
        assertEquals(1, world.events().queuedCount());
        assertTrue(world.commands().size() > 0);

        world.update(0.1f);

        assertEquals(1, system.updateCount);
        assertTrue(world.isAttached(pendingEntity));
        assertEquals(1, eventCount[0]);
        assertEquals(0, world.events().queuedCount());
        assertEquals(0, world.commands().size());
    }

    @Test
    void rejectsNestedPhasesAndRecoversAfterTheFailure() {
        World world = new World();
        ReentrantRenderSystem system = world.addSystem(new ReentrantRenderSystem());
        world.flushCommands();
        TestGraphicsFrame frame = new TestGraphicsFrame();
        TestTextureView target = new TestTextureView();

        assertThrows(
                IllegalStateException.class,
                () -> world.render(frame, target, null, 16, 9, null));

        system.reenter = false;
        assertDoesNotThrow(() -> world.render(frame, target, null, 16, 9, null));
        assertEquals(2, system.renderCount);
    }

    @Test
    void removalAndClearRemoveEveryPhaseMembershipButDetachOnce() {
        World world = new World();
        RecordingPhaseSystemA first =
                world.addSystem(new RecordingPhaseSystemA("a", new ArrayList<>()));
        RecordingPhaseSystemB second =
                world.addSystem(new RecordingPhaseSystemB("b", new ArrayList<>()));
        world.flushCommands();

        world.removeSystem(RecordingPhaseSystemA.class);
        world.flushCommands();

        assertEquals(1, first.detachCount);
        assertEquals(0, second.detachCount);
        assertEquals(1, world.updateSystemCount());
        assertEquals(1, world.renderSystemCount());
        assertEquals(1, world.uiRenderSystemCount());

        world.clear();
        world.flushCommands();

        assertEquals(1, first.detachCount);
        assertEquals(1, second.detachCount);
        assertEquals(0, world.systemCount());
        assertEquals(0, world.updateSystemCount());
        assertEquals(0, world.renderSystemCount());
        assertEquals(0, world.uiRenderSystemCount());
    }

    @Test
    void clearsEntitiesComponentsManagersSystemsEventsAndLists() {
        World world = new World();
        int entity = world.createEntity();
        world.add(entity, new TransformComponent(1.0f, 2.0f, 0.0f));
        EntityList transforms = world.entities(world.matcher().all(TransformComponent.class));
        TestManager manager = world.addManager(new TestManager(), TestManager.class);
        TestSystem system = world.addSystem(new TestSystem());
        world.events().addListener(1, (eventWorld, event) -> { });
        world.events().dispatch(world.events().obtain(1));
        world.flushCommands();
        assertEquals(1, world.updateSystemCount());
        assertEquals(0, world.renderSystemCount());
        assertEquals(0, world.uiRenderSystemCount());

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
    void clearReleasesComponentCachesAndDetachesPreviouslyCachedLists() {
        World world = new World();
        ComponentMapper<VelocityComponent> oldMapper = world.mapper(VelocityComponent.class);
        EntityList oldList = world.entities(world.matcher().all(VelocityComponent.class));
        int original = world.createEntity();
        oldMapper.add(original, new VelocityComponent(1.0f, 2.0f));
        world.flushCommands();

        assertEquals(1, oldMapper.size());
        assertEquals(1, oldList.size());

        world.clear();
        world.flushCommands();

        ComponentMapper<VelocityComponent> newMapper = world.mapper(VelocityComponent.class);
        EntityList newList = world.entities(world.matcher().all(VelocityComponent.class));
        assertNotSame(oldMapper, newMapper);
        assertEquals(0, oldMapper.size());
        assertTrue(oldList.isEmpty());

        int replacement = world.createEntity();
        newMapper.add(replacement, new VelocityComponent(3.0f, 4.0f));
        world.flushCommands();

        assertEquals(0, oldMapper.size());
        assertTrue(oldList.isEmpty());
        assertEquals(1, newMapper.size());
        assertEquals(1, newList.size());
        assertEquals(replacement, newList.entityAt(0));
    }

    @Test
    void clearContinuesTeardownAndRethrowsAggregatedDetachFailures() {
        World world = new World();
        ArrayList<String> detachOrder = new ArrayList<>();
        RuntimeException systemFailure = new IllegalStateException("system detach failed");
        Error managerFailure = new AssertionError("manager detach failed");
        RecordingDetachSystemA firstSystem =
                new RecordingDetachSystemA("system-a", detachOrder, null);
        RecordingDetachSystemB secondSystem =
                new RecordingDetachSystemB("system-b", detachOrder, systemFailure);
        RecordingDetachManager firstManager =
                new RecordingDetachManager("manager-a", detachOrder, managerFailure);
        RecordingDetachManager secondManager =
                new RecordingDetachManager("manager-b", detachOrder, null);
        int entity = world.createEntity();
        world.add(entity, new TransformComponent());
        EntityList transforms = world.entities(world.matcher().all(TransformComponent.class));
        world.addSystem(firstSystem);
        world.addSystem(secondSystem);
        world.addManager(firstManager, FirstManagerContract.class);
        world.addManager(secondManager, SecondManagerContract.class);
        world.events().dispatch(world.events().obtain(1));
        world.flushCommands();
        assertEquals(0, world.updateSystemCount());
        assertEquals(0, world.renderSystemCount());
        assertEquals(0, world.uiRenderSystemCount());

        world.clear();
        RuntimeException thrown = assertThrows(RuntimeException.class, world::flushCommands);

        assertSame(systemFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(managerFailure, thrown.getSuppressed()[0]);
        assertEquals(4, detachOrder.size());
        assertEquals("system-b", detachOrder.get(0));
        assertEquals("system-a", detachOrder.get(1));
        assertEquals("manager-b", detachOrder.get(2));
        assertEquals("manager-a", detachOrder.get(3));
        assertEquals(1, firstSystem.detachCount);
        assertEquals(1, secondSystem.detachCount);
        assertEquals(1, firstManager.detachCount);
        assertEquals(1, secondManager.detachCount);
        assertEquals(0, world.systemCount());
        assertEquals(0, world.managerCount());
        assertEquals(0, world.entityCount());
        assertFalse(world.isAttached(entity));
        assertTrue(transforms.isEmpty());
        assertEquals(0, world.events().queuedCount());
        assertEquals(0, world.commands().size());

        world.flushCommands();
        assertEquals(1, firstSystem.detachCount);
        assertEquals(1, secondSystem.detachCount);
        assertEquals(1, firstManager.detachCount);
        assertEquals(1, secondManager.detachCount);
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

    interface TestManagerContract extends Manager {
    }

    interface FirstManagerContract extends Manager {
    }

    interface SecondManagerContract extends Manager {
    }

    static final class TestManager implements TestManagerContract {
        int attachCount;
        int detachCount;

        public void onAttach(World world) {
            attachCount++;
        }

        public void onDetach(World world) {
            detachCount++;
        }
    }

    static final class TestSystem implements UpdateSystem {
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

    private static final class RecordingDetachManager
            implements FirstManagerContract, SecondManagerContract {
        private final String name;
        private final ArrayList<String> detachOrder;
        private final Throwable detachFailure;
        int detachCount;

        RecordingDetachManager(String name, ArrayList<String> detachOrder, Throwable detachFailure) {
            this.name = name;
            this.detachOrder = detachOrder;
            this.detachFailure = detachFailure;
        }

        public void onAttach(World world) {
        }

        public void onDetach(World world) {
            detachCount++;
            detachOrder.add(name);
            throwUnchecked(detachFailure);
        }
    }

    private abstract static class RecordingDetachSystem implements System {
        private final String name;
        private final ArrayList<String> detachOrder;
        private final Throwable detachFailure;
        int detachCount;

        RecordingDetachSystem(String name, ArrayList<String> detachOrder, Throwable detachFailure) {
            this.name = name;
            this.detachOrder = detachOrder;
            this.detachFailure = detachFailure;
        }

        public void onAttach(World world) {
        }

        public void onDetach(World world) {
            detachCount++;
            detachOrder.add(name);
            throwUnchecked(detachFailure);
        }

        public boolean isEnabled() {
            return true;
        }

        public void setEnabled(boolean enabled) {
        }
    }

    private static final class RecordingDetachSystemA extends RecordingDetachSystem {
        RecordingDetachSystemA(String name, ArrayList<String> detachOrder, Throwable detachFailure) {
            super(name, detachOrder, detachFailure);
        }
    }

    private static final class RecordingDetachSystemB extends RecordingDetachSystem {
        RecordingDetachSystemB(String name, ArrayList<String> detachOrder, Throwable detachFailure) {
            super(name, detachOrder, detachFailure);
        }
    }

    interface PipelineA extends UpdateSystem {
    }

    interface PipelineB extends UpdateSystem {
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

    private abstract static class RecordingPhaseSystem
            implements UpdateSystem, RenderSystem, UiRenderSystem {
        private final String name;
        private final ArrayList<String> calls;
        private boolean enabled = true;
        int attachCount;
        int detachCount;
        int updateCount;
        float lastDelta;
        World world;
        GraphicsFrame frame;
        TextureView colorTarget;
        TextureView depthTarget;
        int width;
        int height;
        Camera renderCamera;
        Camera uiRenderCamera;

        RecordingPhaseSystem(String name, ArrayList<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        public void onAttach(World world) {
            this.world = world;
            attachCount++;
        }

        public void onDetach(World world) {
            detachCount++;
            this.world = null;
        }

        public void update() {
            updateCount++;
            lastDelta = world.deltaTime();
            calls.add("update:" + name);
        }

        public void render(
                GraphicsFrame frame,
                TextureView colorTarget,
                TextureView depthTarget,
                int width,
                int height,
                Camera camera) {
            this.frame = frame;
            this.colorTarget = colorTarget;
            this.depthTarget = depthTarget;
            this.width = width;
            this.height = height;
            renderCamera = camera;
            calls.add("render:" + name);
        }

        public void renderUi(
                GraphicsFrame frame,
                TextureView colorTarget,
                TextureView depthTarget,
                int width,
                int height,
                Camera camera) {
            uiRenderCamera = camera;
            calls.add("ui:" + name);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private static final class RecordingPhaseSystemA extends RecordingPhaseSystem {
        RecordingPhaseSystemA(String name, ArrayList<String> calls) {
            super(name, calls);
        }
    }

    private static final class RecordingPhaseSystemB extends RecordingPhaseSystem {
        RecordingPhaseSystemB(String name, ArrayList<String> calls) {
            super(name, calls);
        }
    }

    private static final class CameraSwitchingPhaseSystem extends RecordingPhaseSystem {
        private final CameraManager cameras;
        private final Camera replacement;

        CameraSwitchingPhaseSystem(
                String name,
                ArrayList<String> calls,
                CameraManager cameras,
                Camera replacement) {
            super(name, calls);
            this.cameras = cameras;
            this.replacement = replacement;
        }

        @Override
        public void render(
                GraphicsFrame frame,
                TextureView colorTarget,
                TextureView depthTarget,
                int width,
                int height,
                Camera camera) {
            super.render(frame, colorTarget, depthTarget, width, height, camera);
            cameras.game(replacement);
        }
    }

    private static final class ReentrantRenderSystem implements RenderSystem {
        private World world;
        private boolean enabled = true;
        private boolean reenter = true;
        private int renderCount;

        public void onAttach(World world) {
            this.world = world;
        }

        public void onDetach(World world) {
            this.world = null;
        }

        public void render(
                GraphicsFrame frame,
                TextureView colorTarget,
                TextureView depthTarget,
                int width,
                int height,
                Camera camera) {
            renderCount++;
            if (reenter) {
                world.render(frame, colorTarget, depthTarget, width, height, camera);
            }
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private static final class TestGraphicsFrame implements GraphicsFrame {
        private final TextureView color = new TestTextureView();

        public CommandEncoder commandEncoder() {
            return null;
        }

        public FrameBuffer frameBuffer() {
            return null;
        }

        public TextureView colorAttachment() {
            return color;
        }

        public int width() {
            return 1;
        }

        public int height() {
            return 1;
        }

        public ProviderId providerId() {
            return TEST_PROVIDER;
        }

        public <T> T as() {
            return null;
        }
    }

    private static final class TestTextureView implements TextureView {
        public TextureFormat format() {
            return TextureFormat.RGBA8_UNORM;
        }

        public ProviderId providerId() {
            return TEST_PROVIDER;
        }

        public <T> T as() {
            return null;
        }
    }

    private static final ProviderId TEST_PROVIDER = ProviderId.of("test");

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
