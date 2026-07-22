package io.github.libfdx.samples.multiplayer.webrtc.web;

import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;

/**
 * Launches the WebRTC multiplayer 2D web JS entry point.
 *
 * @author xpenatan
 */
public final class MultiplayerWebRtcWebJsLauncher {
    private MultiplayerWebRtcWebJsLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean webgpu = MultiplayerWebRtcWebLauncherSupport.webGpuRequested(args);
        GraphicsAttachmentProvider graphics = webgpu ? new WebWGPUProvider() : new WebGLProvider();
        MultiplayerWebRtcWebLauncherSupport.start("JS", webgpu, graphics);
    }
}
