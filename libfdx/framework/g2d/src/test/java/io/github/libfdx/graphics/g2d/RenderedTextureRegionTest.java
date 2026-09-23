package io.github.libfdx.graphics.g2d;

import io.github.libfdx.graphics.*;
import io.github.libfdx.core.FdxException;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RenderedTextureRegionTest {
    @Test
    void renderedRowsFollowDeclaredOriginWhileUploadedRegionsKeepTheirRowOrder() {
        Texture texture=(Texture)Proxy.newProxyInstance(Texture.class.getClassLoader(),new Class[]{Texture.class},
                (p,m,a)->switch(m.getName()) {
                    case "width" -> 64; case "height" -> 32; case "sampleCount" -> 1;
                    case "usage" -> TextureUsage.SAMPLED_RENDER_ATTACHMENT;
                    default -> throw new AssertionError(m);
                });
        TextureRegion upload=new TextureRegion(texture,4,8,12,16);
        assertEquals(.25f,upload.v()); assertEquals(.75f,upload.v2());
        TextureRegion gl=TextureRegion.rendered(texture,TextureOrigin.BOTTOM_LEFT);
        TextureRegion wgpu=TextureRegion.rendered(texture,TextureOrigin.TOP_LEFT);
        assertEquals(1,gl.v()); assertEquals(0,gl.v2());
        assertEquals(0,wgpu.v()); assertEquals(1,wgpu.v2());
        assertThrows(FdxException.class,()->TextureRegion.rendered(texture,TextureOrigin.UNKNOWN));
    }
}
