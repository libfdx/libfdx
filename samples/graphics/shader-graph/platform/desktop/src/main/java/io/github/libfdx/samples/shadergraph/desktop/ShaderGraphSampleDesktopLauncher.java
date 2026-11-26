package io.github.libfdx.samples.shadergraph.desktop;

import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.d3d12.D3D12Provider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleApplication;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleGraphs.Example;
import io.github.libfdx.tools.shadergraph.ShaderGraphDesktopEditor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Selects the app, graph and source configured by the Gradle task or packaged jar. */
public final class ShaderGraphSampleDesktopLauncher {
    public static void main(String[] args) throws IOException {
        Properties defaults = new Properties();
        try (var stream = ShaderGraphSampleDesktopLauncher.class.getResourceAsStream("/libfdx-desktop-launch.properties")) {
            if (stream != null) defaults.load(stream);
        }
        Example example = Example.valueOf(option(defaults, "example", "DISSOLVE"));
        boolean code = "code".equalsIgnoreCase(option(defaults, "graphSource", "asset").trim());
        if (Boolean.parseBoolean(option(defaults, "standalone", "false"))) {
            boolean vsync = Boolean.parseBoolean(option(defaults, "vsync", "true"));
            GraphicsAttachmentProvider provider = switch (option(defaults, "graphics", "wgpu")) {
                case "d3d12" -> new D3D12Provider().vSync(vsync);
                case "vulkan" -> new DesktopVulkanProvider().vSync(vsync);
                case "gl" -> new DesktopOpenGLProvider();
                default -> new WGPUProvider().vSync(vsync);
            };
            var config = new DesktopApplicationConfig().title(example.title).size(960, 640).vSync(vsync)
                    .visible(Boolean.parseBoolean(option(defaults, "visible", "true")))
                    .maximized(Boolean.parseBoolean(option(defaults, "maximized", "false"))).graphics(provider);
            new DesktopApplicationBackend().start(config, new ShaderGraphSampleApplication(example, code,
                    Long.parseLong(option(defaults, "exitAfterFrames", "0"))));
        } else {
            ShaderGraphDesktopEditor.openEffects(args, editorEffects(code), example.ordinal());
        }
    }

    private static List<ShaderGraphDesktopEditor.Effect> editorEffects(boolean code) {
        var effects = new ArrayList<ShaderGraphDesktopEditor.Effect>();
        for (Example entry : Example.values()) {
            String path = code ? entry.asset.replace(".fdxgraph", "-from-code.fdxgraph") : entry.asset;
            effects.add(code ? new ShaderGraphDesktopEditor.Effect(entry.title, entry::build, path, entry.animation)
                    : new ShaderGraphDesktopEditor.Effect(entry.title, path));
        }
        return effects;
    }

    private static String option(Properties defaults, String key, String fallback) {
        return System.getProperty("libfdx.sample." + key, defaults.getProperty(key, fallback));
    }

}
