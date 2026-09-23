package io.github.libfdx.maps.streaming;

import io.github.libfdx.assets.*;
import io.github.libfdx.core.*;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.maps.TileChunk;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ChunkResidencyTest {
    @Test
    void visibleRequestsPrecedePrefetchAndShareAssetDependencyBudgets() {
        try(var fixture=new Fixture()) {
            fixture.sharedGate=FdxFuture.pending();
            var residency=new ChunkResidency(fixture.manager,fixture::resolve,16,16,16,2);
            residency.window(-1,-1,1,1,1);
            assertEquals(1,residency.update(1,0,0));assertEquals(1,residency.pendingChunks());
            fixture.steps(60);assertEquals("-1,-1",fixture.resolved.getFirst());
            assertEquals(0,residency.update(0,1,0));assertFalse(residency.visibleReady());
            var dependency=fixture.dependency();fixture.sharedGate.complete(dependency);fixture.steps(60);
            assertEquals(0,residency.residentChunks());assertEquals(1,residency.update(0,1,0));
            for(int i=0;i<400;i++){residency.update(2,2,2);fixture.steps(2);assertTrue(residency.pendingChunks()<=2);}
            assertTrue(residency.visibleReady());assertEquals(16,residency.residentChunks());
            assertEquals(List.of("-1,-1","0,-1","-1,0","0,0"),fixture.resolved.subList(0,4));
            assertEquals(16*16*16*5,residency.maxCellBytes());
            var external=fixture.manager.acquire(AssetDescriptor.of("shared",Dependency.class));fixture.steps(60);
            residency.dispose();residency.dispose();assertFalse(dependency.isDisposed());
            assertNull(fixture.manager.find("-1,-1",TileChunk.class));external.dispose();assertTrue(dependency.isDisposed());
        }
    }
    @Test
    void movingWindowEvictsPendingRequestsAndLateCompletionsCannotPublish() {
        try(var fixture=new Fixture()) {
            FdxFuture<TileChunk> delayed=FdxFuture.pending();fixture.delayed.put("0,0",delayed);
            var residency=new ChunkResidency(fixture.manager,fixture::resolve,16,16,1,1);
            residency.window(0,0,16,16,0);residency.update(1,1,1);fixture.steps(40);
            assertEquals(1,residency.pendingChunks());
            residency.window(-16,-16,0,0,0);
            assertEquals(0,residency.update(1,1,0)); // Full capacity: eviction budget is required.
            assertEquals(2,residency.update(1,1,1)); // Cancel old lease, start new request.
            delayed.complete(new TileChunk(0,0,16,16).fill(9));
            fixture.steps(80);residency.update(0,1,0);
            assertTrue(residency.visibleReady());assertEquals(1,residency.residentChunks());
            assertNull(residency.layer().findChunk(0,0));assertEquals(1,residency.layer().tile(-1,-1));
            assertNull(fixture.manager.find("0,0",TileChunk.class));
            for(int i=1;i<=300;i++) {
                int x=i*1_000_000;
                residency.window(x,0,(long)x+16,16,0);residency.update(1,1,1);fixture.steps(40);residency.update(0,1,0);
                assertTrue(residency.visibleReady());assertEquals(1,residency.requestedChunks());
                assertEquals(256,residency.layer().residentCells());assertTrue(residency.layer().indexBucketCount()<=4);
            }
            residency.dispose();fixture.steps(40);
            for(Dependency dependency:fixture.dependencies)assertEquals(1,dependency.disposals);
        }
    }
    @Test
    void failureAndWrongCoordinatesRequireExplicitRetryWithoutDiscardingOtherChunks() {
        try(var fixture=new Fixture()) {
            fixture.delayed.put("1,0",FdxFuture.failed(new FdxException("missing chunk")));
            var residency=new ChunkResidency(fixture.manager,fixture::resolve,16,16,2,2);
            residency.window(0,0,32,16,0);residency.update(2,2,2);fixture.steps(80);residency.update(0,2,0);
            assertEquals(1,residency.failedChunks());assertEquals(1,residency.residentChunks());
            assertEquals("missing chunk",residency.failure(1,0).getMessage());assertFalse(residency.visibleReady());
            assertEquals(0,residency.update(2,2,2));assertEquals(2,fixture.resolved.size());
            TileChunk retained=residency.layer().findChunk(0,0);
            fixture.delayed.put("1,0",FdxFuture.completed(new TileChunk(2,0,16,16)));
            assertEquals(1,residency.retryFailed());residency.update(1,1,0);fixture.steps(80);residency.update(0,1,0);
            assertTrue(residency.failure(1,0).getMessage().contains("grid request"));
            assertSame(retained,residency.layer().findChunk(0,0));assertNull(residency.layer().findChunk(2,0));
            fixture.delayed.remove("1,0");residency.retryFailed();residency.update(1,1,0);fixture.steps(80);residency.update(0,1,0);
            assertTrue(residency.visibleReady());assertEquals(0,residency.failedChunks());
            residency.dispose();
        }
    }
    @Test
    void oversizedWindowsPreserveThePriorSelectionAndManagerClosureRejectsFurtherWork() {
        try(var fixture=new Fixture()) {
            var residency=new ChunkResidency(fixture.manager,fixture::resolve,16,16,1,1);
            residency.window(Integer.MAX_VALUE-15,0,(long)Integer.MAX_VALUE+1,16,0);
            assertThrows(FdxException.class,()->residency.window(-1,-1,1,1,0));
            assertThrows(FdxException.class,()->residency.window(0,0,16,16,1));
            assertThrows(FdxException.class,()->residency.window(0,0,Long.MAX_VALUE,16,0));
            assertThrows(FdxException.class,()->residency.update(-1,0,0));
            residency.update(1,1,1);fixture.steps(80);residency.update(0,1,0);
            assertTrue(residency.visibleReady());assertEquals(Integer.MAX_VALUE-15,residency.layer().chunk(0).x());
            fixture.manager.dispose();
            assertThrows(FdxException.class,()->residency.update(1,1,1));
            residency.dispose();
        }
    }

    @Test
    void resolverCannotReenterResidencyMutationAndFailureCanBeRetried() {
        try(var fixture=new Fixture()) {
            var holder=new ChunkResidency[1];
            boolean[] reenter={true};
            holder[0]=new ChunkResidency(fixture.manager,(x,y)->{
                if(reenter[0])holder[0].dispose();
                return fixture.resolve(x,y);
            },16,16,1,1);
            var residency=holder[0];residency.window(0,0,16,16,0);
            assertEquals(1,residency.update(1,1,1));assertFalse(residency.isDisposed());
            assertEquals(1,residency.failedChunks());assertTrue(residency.failure(0,0).getMessage().contains("callbacks"));
            reenter[0]=false;assertEquals(1,residency.retryFailed());residency.update(1,1,1);
            fixture.steps(60);residency.update(0,1,0);assertTrue(residency.visibleReady());residency.dispose();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Map<String,FdxFuture<TileChunk>> delayed=new HashMap<>();
        final List<String> resolved=new ArrayList<>();
        final List<Dependency> dependencies=new ArrayList<>();
        FdxFuture<Dependency> sharedGate;
        final DefaultAssetManager manager;
        Fixture() {
            FileSystem files=(FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(),new Class[]{FileSystem.class},
                    (p,m,a)->{throw new AssertionError("Procedural fixture has no file I/O: "+m);});
            manager=new DefaultAssetManager(files);
            manager.registerLoader(Dependency.class,new AssetLoader<Dependency>() {
                public Class<Dependency> type(){return Dependency.class;}
                public FdxFuture<Dependency> load(AssetLoadContext context,AssetDescriptor<Dependency> descriptor) {
                    return sharedGate!=null?sharedGate:context.completeOnUpdate(()->dependency());
                }
            });
            manager.registerLoader(TileChunk.class,new AssetLoader<TileChunk>() {
                public Class<TileChunk> type(){return TileChunk.class;}
                public FdxFuture<TileChunk> load(AssetLoadContext context,AssetDescriptor<TileChunk> descriptor) {
                    context.dependency(AssetDescriptor.of("shared",Dependency.class));
                    FdxFuture<TileChunk> wait=delayed.get(descriptor.path());if(wait!=null)return wait;
                    String[] coordinates=descriptor.path().split(",");
                    int x=Integer.parseInt(coordinates[0]),y=Integer.parseInt(coordinates[1]);
                    return context.async(()->new TileChunk(x*16,y*16,16,16).fill(1));
                }
            });
        }
        Dependency dependency(){var value=new Dependency();dependencies.add(value);return value;}
        AssetDescriptor<TileChunk> resolve(int x,int y){String path=x+","+y;resolved.add(path);return AssetDescriptor.of(path,TileChunk.class);}
        void steps(int count){for(int i=0;i<count;i++){manager.update(1,Long.MAX_VALUE);assertTrue(manager.lastUpdateTaskCount()<=1);}}
        public void close(){manager.dispose();}
    }
    private static final class Dependency implements Disposable {
        int disposals;
        public void dispose(){disposals++;}
        public boolean isDisposed(){return disposals>0;}
    }
}
