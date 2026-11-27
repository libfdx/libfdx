package io.github.libfdx.tests.utils.runner;

import com.microsoft.playwright.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fresh browser for one pair; the outer JVM supervisor enforces the hard timeout. */
public final class WebMatrixWorker {
    public static void main(String[] args) throws Exception {
        String variant = System.getProperty("libfdx.test.graphics");
        String testName = System.getProperty("libfdx.test.name");
        String serverUrl = System.getProperty(variant.startsWith("wasm-") ? "libfdx.test.autoWebWasmUrl" : "libfdx.test.autoWebJsUrl");
        Path marker = Path.of(System.getProperty("libfdx.test.autoCompletionFile"));
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(Boolean.parseBoolean(System.getProperty("libfdx.test.autoWebHeadless", "false")));
            String channel = System.getProperty("libfdx.test.autoWebChannel", "");
            String executable = System.getProperty("libfdx.test.autoWebExecutable", "");
            if (!channel.isBlank()) options.setChannel(channel);
            if (!executable.isBlank()) options.setExecutablePath(Path.of(executable));
            try (Browser browser = playwright.chromium().launch(options)) {
                System.out.println("Browser Chromium " + browser.version() + "; variant=" + variant);
                Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 800));
                page.setDefaultTimeout(Double.parseDouble(System.getProperty("libfdx.test.autoTimeoutSeconds", "180")) * 1000);
                List<String> errors = new ArrayList<>();
                page.onConsoleMessage(message -> {
                    System.out.println("[browser " + message.type() + "] " + message.text());
                    if (message.type().equals("error") && !expectedResourceError(message, testName, serverUrl)) {
                        errors.add(message.text() + " at " + message.location());
                    }
                });
                page.onPageError(error -> { errors.add(error); System.err.println(error); });
                page.onCrash(ignored -> errors.add("Browser page crashed"));
                boolean concurrentGltf = "ConcurrentGltfLoadingTest".equals(testName);
                boolean defaultPbr = concurrentGltf || List.of("GltfLoadingTest", "ModelBatchTest").contains(testName);
                if (defaultPbr) page.addInitScript("""
                        window.libfdxPbrWorkers={started:0,results:0,terminated:0};
                        window.libfdxImageWorkers={started:0,decoded:0,mipmapped:0,terminated:0};
                        window.libfdxWorkerBlobs={started:0,revoked:0,live:new Set()};
                        const revokeUrl=URL.revokeObjectURL;
                        URL.revokeObjectURL=function(url) {
                            const blobs=window.libfdxWorkerBlobs;
                            if(blobs.live.delete(url))blobs.revoked++;
                            revokeUrl.call(URL,url);
                        };
                        const NativeWorker=window.Worker;
                        window.Worker=class extends NativeWorker {
                            constructor(url,options) {
                                super(url,options);
                                if(String(url).includes('#libfdx-')) {
                                    if(!String(url).startsWith('blob:'))throw new Error('Expected application Blob worker');
                                    window.libfdxWorkerBlobs.started++;
                                    window.libfdxWorkerBlobs.live.add(String(url).split('#')[0]);
                                }
                                this.pbr=String(url).endsWith('#libfdx-pbr');
                                this.image=String(url).endsWith('#libfdx-assets');
                                if(this.image) {
                                    window.libfdxImageWorkers.started++;
                                    this.jobs=new Map();
                                    this.addEventListener('message',event=>{
                                        const m=event.data;
                                        if(m && !m.error && Array.isArray(m.levels) && this.jobs.has(m.id)) {
                                            window.libfdxImageWorkers[this.jobs.get(m.id)?'decoded':'mipmapped']++;
                                            this.jobs.delete(m.id);
                                        }
                                    });
                                }
                                if(this.pbr) {
                                    window.libfdxPbrWorkers.started++;
                                    this.addEventListener('message',event=>{
                                        if(event.data && Array.isArray(event.data.variants) && event.data.variants.length===8)
                                            window.libfdxPbrWorkers.results++;
                                    });
                                }
                            }
                            postMessage(message,...args) {
                                if(this.image) this.jobs.set(message.id,message.decode);
                                super.postMessage(message,...args);
                            }
                            terminate() {
                                if(this.pbr) {window.libfdxPbrWorkers.terminated++;this.pbr=false;}
                                if(this.image) {window.libfdxImageWorkers.terminated++;this.image=false;}
                                super.terminate();
                            }
                        };
                        """);
                String api = variant.endsWith("webgpu") ? "webgpu" : "webgl";
                String url = serverUrl + "/?test="
                        + testName + "&graphics=" + api + "&frames=-1&autoChild=true&autoDurationSeconds="
                        + System.getProperty("libfdx.test.autoDurationSeconds", "6");
                StringBuilder optionsQuery = new StringBuilder(url);
                for (String option : List.of("shaderAsync", "shaderLoadingOnly", "shaderPreload", "shaderCount",
                        "shaderSeed", "shaderInvalidIndex", "shaderVerifyPixels", "optimizerAnimated")) {
                    String value = System.getProperty("libfdx.test." + option);
                    if (value != null) optionsQuery.append('&').append(option).append('=')
                            .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
                page.navigate(optionsQuery.toString());
                page.waitForCondition(() -> !errors.isEmpty() || Boolean.TRUE.equals(page.evaluate("() => !!window.libfdxAutoReady")));
                if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
                long readyTime = System.nanoTime();
                // Wait until the scenario installed input handlers before activating browser audio.
                page.locator("#libfdx-canvas").click(new Locator.ClickOptions().setTimeout(15_000));
                if (concurrentGltf) {
                    int inputSequence = 0;
                    double maxInputMillis = 0;
                    while (!Boolean.TRUE.equals(page.evaluate("() => !!window.libfdxConcurrentGltfResult"))) {
                        if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
                        if (System.nanoTime() - readyTime > 120_000_000_000L)
                            throw new IllegalStateException("Concurrent model loading did not complete");
                        long inputStart = System.nanoTime();
                        page.mouse().move(100 + inputSequence % 40 * 20, 80 + inputSequence % 30 * 10);
                        maxInputMillis = Math.max(maxInputMillis, (System.nanoTime() - inputStart) / 1e6);
                        inputSequence++;
                        page.waitForTimeout(50);
                    }
                    page.evaluate("ms => window.libfdxConcurrentGltfResult.maxInputRoundTripMs=ms", maxInputMillis);
                }
                double captureSeconds = Double.parseDouble(System.getProperty("libfdx.test.autoCaptureSeconds", "0"));
                if (captureSeconds > 0) {
                    double remaining = captureSeconds * 1000 - (System.nanoTime() - readyTime) / 1e6;
                    if (remaining > 0) page.waitForTimeout(remaining);
                    page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(marker + ".running.png")));
                }
                page.waitForCondition(() -> !errors.isEmpty() || Boolean.TRUE.equals(page.evaluate("() => !!window.libfdxAutoResult")),
                        new Page.WaitForConditionOptions().setTimeout(Double.parseDouble(System.getProperty("libfdx.test.autoTimeoutSeconds", "180")) * 1000));
                if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
                String status = String.valueOf(page.evaluate("() => window.libfdxAutoResult.status"));
                if (!status.equals("PASS")) throw new IllegalStateException(String.valueOf(page.evaluate("() => window.libfdxAutoResult.detail")));
                if (defaultPbr) {
                    Object counts = page.evaluate("() => window.libfdxPbrWorkers");
                    if (!Boolean.TRUE.equals(page.evaluate("() => {const w=window.libfdxPbrWorkers;return w.started===1 && w.results===1 && w.terminated===1;}")))
                        throw new IllegalStateException("Default PBR worker was not used and disposed: " + counts);
                    System.out.println("DEFAULT_PBR_WORKER_PASS " + counts);
                    Object images = page.evaluate("() => window.libfdxImageWorkers");
                    // Ducky explicitly requests mip filtering; the helmet's sampler has no mip filter.
                    boolean needsMipmaps = concurrentGltf || "GltfLoadingTest".equals(testName);
                    if (!Boolean.TRUE.equals(page.evaluate("needsMips => {const w=window.libfdxImageWorkers;return w.started===1 && w.decoded>0 && (!needsMips || w.mipmapped>0) && w.terminated===1;}", needsMipmaps)))
                        throw new IllegalStateException("Default image/mipmap worker was not shared, used and disposed: " + images);
                    System.out.println("DEFAULT_IMAGE_WORKER_PASS " + images);
                    if (!Boolean.TRUE.equals(page.evaluate("() => {const b=window.libfdxWorkerBlobs;return b.started>=3 && b.revoked===b.started && b.live.size===0;}")))
                        throw new IllegalStateException("Default worker Blob URLs were not released");
                    System.out.println("WORKER_BLOB_CLEANUP_PASS " + page.evaluate("() => {const b=window.libfdxWorkerBlobs;return {started:b.started,revoked:b.revoked,live:b.live.size};}"));
                }
                if (concurrentGltf) {
                    if (!Boolean.TRUE.equals(page.evaluate("() => {const r=window.libfdxConcurrentGltfResult;return r && r.models===12 && r.peakRoots===12 && r.sharedDependencies>0;}")))
                        throw new IllegalStateException("Concurrent glTF assertions did not complete");
                    System.out.println("CONCURRENT_GLTF_PASS " + page.evaluate("() => window.libfdxConcurrentGltfResult"));
                    double frameLimit = Double.parseDouble(System.getProperty("libfdx.test.maxLoadingFrameMs", "100"));
                    double updateLimit = Double.parseDouble(System.getProperty("libfdx.test.maxAssetUpdateMs", "25"));
                    if (!Boolean.TRUE.equals(page.evaluate("limits => {const r=window.libfdxConcurrentGltfResult;"
                            + "return r.inputEvents>=5 && r.preparationInputEvents>=2 && r.maxInputRoundTripMs<=limits[0]"
                            + " && r.maxLoadingFrameGapMs<=limits[0] && r.maxAssetUpdateMs<=limits[1];}",
                            List.of(frameLimit, updateLimit))))
                        throw new IllegalStateException("Loading responsiveness exceeded limits (frame=" + frameLimit
                                + "ms, asset update=" + updateLimit + "ms), or input did not progress");
                }
                page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(marker + ".png")));
            }
        }
        Files.writeString(marker, "completed\n");
    }

    private static boolean expectedResourceError(ConsoleMessage message, String test, String serverUrl) {
        // These fixtures exercise the application's handling of specific HTTP failures.
        return switch (test) {
            case "FileStreamingTest" -> resourceError(message, serverUrl + "/assets/streaming/empty.bin", 416);
            case "ModelLodOptimizerTest" -> resourceError(message, serverUrl + "/assets/gltf-animation/missing.glb", 404);
            default -> false;
        };
    }

    private static boolean resourceError(ConsoleMessage message, String url, int status) {
        return message.location().startsWith(url + ":")
                && message.text().startsWith("Failed to load resource: the server responded with a status of " + status + " ");
    }

}
