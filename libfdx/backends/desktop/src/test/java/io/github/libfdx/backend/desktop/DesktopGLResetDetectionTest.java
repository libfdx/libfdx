package io.github.libfdx.backend.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.ARBRobustness;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.KHRRobustness;
import static org.junit.jupiter.api.Assertions.*;

/** Native smoke test, not an induced GPU reset. Requires the desktop driver. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativePreparationLifecycle", matches = "true")
@Timeout(20)
class DesktopGLResetDetectionTest {
    @Test
    void nativeResetQueriesAndFreshContextsPreserveOrdinaryGlErrors() {
        for (int session = 0; session < 3; session++) {
            new DesktopApplicationBackend().start(new DesktopApplicationConfig().size(64, 64).visible(false)
                    .vSync(false).graphics(new DesktopOpenGLProvider()), new ApplicationAdapter() {
                private Fdx fdx;
                @Override
                public void create(Fdx fdx) {
                    this.fdx = fdx;
                    var caps = GL.getCapabilities();
                    assertTrue(caps.OpenGL45 || caps.GL_KHR_robustness || caps.GL_ARB_robustness,
                            "Native reset query is unavailable; this machine cannot validate the route");
                    int strategy = GL11.glGetInteger(KHRRobustness.GL_RESET_NOTIFICATION_STRATEGY);
                    assertEquals(KHRRobustness.GL_LOSE_CONTEXT_ON_RESET, strategy);
                    assertEquals(GLFW.GLFW_LOSE_CONTEXT_ON_RESET,
                            GLFW.glfwGetWindowAttrib(GLFW.glfwGetCurrentContext(), GLFW.GLFW_CONTEXT_ROBUSTNESS));
                    DesktopGLApi api = new DesktopGLApi(1);
                    assertFalse(api.isContextLost());
                    assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
                    GL11.glEnable(-1); // Ordinary, non-destructive invalid-enum error.
                    assertFalse(api.isContextLost());
                    assertEquals(GL11.GL_INVALID_ENUM, GL11.glGetError());
                    if (caps.GL_ARB_robustness) assertEquals(0, ARBRobustness.glGetGraphicsResetStatusARB());
                    assertEquals(0, GL11.glGetError());
                    boolean safeLossExtension = false;
                    for (int index = 0; index < GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS); index++) {
                        safeLossExtension |= "GL_ANGLE_lose_context".equals(GL30.glGetStringi(GL11.GL_EXTENSIONS, index));
                    }
                    System.out.println("GL_RESET_QUERY_PASS version=" + GL11.glGetString(GL11.GL_VERSION)
                            + " strategy=0x" + Integer.toHexString(strategy)
                            + " ANGLE_lose_context=" + safeLossExtension
                            + " KHR=" + caps.GL_KHR_robustness + " ARB=" + caps.GL_ARB_robustness);
                    api.closeShaderPreparation();
                }
                @Override
                public void render() { fdx.app().requestExit(); }
            });
        }
    }
}
