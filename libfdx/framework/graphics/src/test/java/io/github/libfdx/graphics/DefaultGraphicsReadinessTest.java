package io.github.libfdx.graphics;

import io.github.libfdx.math.ClipDepthRange;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefaultGraphicsReadinessTest {
    @Test
    void asynchronousDeviceIsNotQueriedUntilReady() {
        ClipDepthRange previous=ClipDepthRange.getDefault();
        boolean[] ready={false};
        int[] queries={0};
        GraphicsDevice device=(GraphicsDevice)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{GraphicsDevice.class},(proxy,method,args)-> {
                    if(method.getName().equals("capabilities"))
                        return GraphicsCapabilities.builder()
                                .profile(io.github.libfdx.graphics.shader.ShaderProfile.PORTABLE_WEBGPU)
                                .clipDepthRange(ClipDepthRange.ZERO_TO_ONE).build();
                    throw new AssertionError(method);
                });
        GraphicsContext context=(GraphicsContext)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{GraphicsContext.class,GraphicsAttachmentReadiness.class},(proxy,method,args)-> {
                    if(method.getName().equals("isReady"))return ready[0];
                    if(method.getName().equals("device")) {
                        assertTrue(ready[0],"Device access before asynchronous initialization");
                        queries[0]++;return device;
                    }
                    throw new AssertionError(method);
                });
        try {
            ClipDepthRange.setDefault(ClipDepthRange.NEGATIVE_ONE_TO_ONE);
            DefaultGraphics graphics=new DefaultGraphics(context);
            assertSame(context,graphics.main());
            assertEquals(0,queries[0]);
            ready[0]=true;
            graphics.main();
            assertEquals(ClipDepthRange.ZERO_TO_ONE,ClipDepthRange.getDefault());
            graphics.main();
            assertEquals(1,queries[0]);
        } finally { ClipDepthRange.setDefault(previous); }
    }
}
