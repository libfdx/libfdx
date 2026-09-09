package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.*;
import io.github.libfdx.assets.loaders.AtlasData;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.*;
import io.github.libfdx.graphics.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SpriteAtlasTest {
    private final Map<String,FdxFuture<byte[]>> reads=new HashMap<>();
    private final Map<String,Integer> readCounts=new HashMap<>();
    private final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
    private final Thread applicationThread=Thread.currentThread();
    private int uploads,disposals;
    private boolean badDimensions;

    @Test void delayedPagesAndWorkerPreparationRespectBudgetAndSharedScopeOwnership() throws Exception {
        DefaultAssetManager manager=manager();
        AssetScope a=manager.createScope(),b=manager.createScope();
        var descriptor=TextureLoadOptions.PIXEL_ART.descriptor("sprites/test.atlas.json",SpriteAtlas.class);
        var first=a.load(descriptor); var second=b.load(descriptor);
        pump(manager); assertEquals(0,uploads); assertFalse(first.isLoaded());
        reads.get("sprites/test.atlas.json").complete(metadata()); pump(manager);
        assertTrue(reads.containsKey("sprites/page-0.png")); assertTrue(reads.containsKey("sprites/page-1.png"));
        reads.get("sprites/page-0.png").complete(png()); pump(manager);
        assertEquals(1,uploads); assertFalse(first.future().isDone());
        reads.get("sprites/page-1.png").complete(png()); assertEquals(1,uploads); pump(manager);
        assertTrue(first.isLoaded()); assertEquals(2,uploads); assertSame(first.asset(),second.asset());
        assertEquals(1,readCounts.get("sprites/test.atlas.json"));
        SpriteAtlas atlas=second.asset(); AtlasRegion sprite=atlas.find("hero");
        assertNotNull(sprite); assertNull(atlas.find("missing"));
        a.dispose(); assertEquals(0,disposals); assertFalse(atlas.isDisposed());
        b.dispose(); assertEquals(2,disposals); assertTrue(atlas.isDisposed());
        assertThrows(FdxException.class,sprite::region);
        manager.dispose(); assertEquals(2,disposals);
    }
    @Test void pageFailureAndBindingFailureReleaseAlreadyLoadedDependencies() throws Exception {
        for(boolean dimensions:new boolean[]{false,true}) {
            badDimensions=dimensions; uploads=disposals=0; reads.clear(); tasks.clear();
            DefaultAssetManager manager=manager(); var lease=manager.acquire(AssetDescriptor.of("sprites/test.atlas.json",SpriteAtlas.class));
            pump(manager); reads.get("sprites/test.atlas.json").complete(metadata()); pump(manager);
            reads.get("sprites/page-0.png").complete(png()); pump(manager);
            if(dimensions) reads.get("sprites/page-1.png").complete(png());
            else reads.get("sprites/page-1.png").completeExceptionally(new FdxException("missing page"));
            pump(manager); assertTrue(lease.future().isFailed());
            assertEquals(dimensions ? 2 : 1,disposals);
            manager.dispose(); assertEquals(uploads,disposals);
        }
    }
    @Test void trimmedQuadRotatesAndMirrorsAboutOriginalImagePivotWithoutOwningPages() {
        int[] disposed={0};
        Texture texture=proxy(Texture.class,(name,args) -> switch(name) {
            case "width","height" -> 32; case "isDisposed" -> false;
            case "dispose" -> { disposed[0]++; yield null; } default -> throw new UnsupportedOperationException(name);
        });
        AtlasData data=new AtlasData(new AtlasData.Page[]{new AtlasData.Page("page.png",32,32)},
                new AtlasData.Sprite[]{new AtlasData.Sprite("hero",0,1,1,8,10,20,30,3,4,.25f,.5f)});
        SpriteAtlas atlas=new SpriteAtlas(data,new Texture[]{texture});
        Object[][] drawn={null}; Batch2D batch=proxy(Batch2D.class,(name,args) -> { drawn[0]=args; return null; });
        atlas.find("hero").draw(batch,100,200,-2,3,45);
        assertArrayEquals(new Object[]{atlas.find("hero").region(),104f,167f,-16f,30f,-4f,33f,45f},drawn[0]);
        atlas.dispose(); assertEquals(0,disposed[0]);
    }
    private DefaultAssetManager manager() {
        AssetExecutor executor=new AssetExecutor() {
            public boolean submit(Runnable task) { if(tasks.size()==2) return false; tasks.add(task); return true; }
            public void dispose() { } public boolean isDisposed() { return false; }
        };
        FileSystem files=proxy(FileSystem.class,(name,args) -> {
            String path=(String)args[0];
            return proxy(FileHandle.class,(method,ignored) -> {
                if(method.equals("readBytes")) { readCounts.merge(path,1,Integer::sum); return reads.computeIfAbsent(path,p -> FdxFuture.pending()); }
                throw new UnsupportedOperationException(method);
            });
        });
        GraphicsDevice device=proxy(GraphicsDevice.class,(name,args) -> {
            assertSame(applicationThread,Thread.currentThread());
            if(name.equals("writeTexture")) { uploads++; return null; }
            if(name.equals("createTexture")) {
                TextureDescriptor descriptor=(TextureDescriptor)args[0];
                boolean[] closed={false};
                return proxy(Texture.class,(method,ignored) -> switch(method) {
                    case "width","height" -> badDimensions ? 1 : 2;
                    case "isDisposed" -> closed[0];
                    case "dispose" -> { assertFalse(closed[0]); closed[0]=true; disposals++; yield null; }
                    default -> throw new UnsupportedOperationException(method+": "+descriptor);
                });
            }
            throw new UnsupportedOperationException(name);
        });
        DefaultAssetManager manager=new DefaultAssetManager(files,executor);
        G2DAssetLoaders.register(manager,proxy(GraphicsContext.class,(name,args) -> device)); return manager;
    }
    private void pump(DefaultAssetManager manager) throws Exception {
        for(int i=0;i<100;i++) {
            manager.update(1,Long.MAX_VALUE); assertTrue(manager.lastUpdateTaskCount()<=1);
            while(!tasks.isEmpty()) {
                Thread worker=new Thread(tasks.remove()); worker.start(); worker.join();
            }
        }
    }
    private static byte[] metadata() {
        return """
                {"version":1,"alpha":"straight",
                 "pages":[{"image":"page-0.png","width":2,"height":2},{"image":"page-1.png","width":2,"height":2}],
                 "sprites":[{"name":"hero","page":0,"x":0,"y":0,"width":2,"height":2,
                   "originalWidth":4,"originalHeight":6,"trimX":1,"trimY":3,"pivotX":0.25,"pivotY":0.5}]}
                """.getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] png() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB),"png",out); return out.toByteArray();
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type,Call call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args) -> call.run(method.getName(),args));
    }
    private interface Call { Object run(String name,Object[] args); }
}
