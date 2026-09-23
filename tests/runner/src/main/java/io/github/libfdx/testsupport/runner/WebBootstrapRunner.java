package io.github.libfdx.testsupport.runner;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.concurrent.Executors;

/** Real generated-page startup checks, including downloads that remain pending after Java main returns. */
public final class WebBootstrapRunner {
    public static void main(String[] args) throws Exception {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)
                     .setChannel(System.getProperty("libfdx.test.autoWebChannel", "chrome")))) {
            for (String target : List.of("Js", "Wasm")) {
                Path root = Path.of(System.getProperty("libfdx.test.autoWeb" + target + "Directory")).toAbsolutePath();
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                    server.setExecutor(executor);
                    server.createContext("/nested/game/", exchange -> {
                        String relative = exchange.getRequestURI().getPath().substring("/nested/game/".length());
                        Path file = root.resolve(relative.isEmpty() ? "index.html" : relative).normalize();
                        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                            exchange.sendResponseHeaders(404, -1); exchange.close(); return;
                        }
                        String mime = relative.endsWith(".wasm") ? "application/wasm"
                                : relative.endsWith(".js") ? "application/javascript" : Files.probeContentType(file);
                        exchange.getResponseHeaders().set("Content-Type", mime == null ? "application/octet-stream" : mime);
                        exchange.sendResponseHeaders(200, Files.size(file));
                        try (var stream = exchange.getResponseBody()) { Files.copy(file, stream); }
                    });
                    server.createContext("/favicon.ico", exchange -> { exchange.sendResponseHeaders(204, -1); exchange.close(); });
                    server.start();
                    try {
                        for (String graphics : List.of("webgl", "webgpu")) {
                            for (String scenario : List.of("delayed-script", "delayed-wasm", "dispose", "missing-script",
                                    "invalid-script", "missing-wasm", "invalid-wasm", "missing-app", "invalid-app",
                                    "runtime-error", "runtime-rejection")) {
                                run(browser, root, target.equals("Wasm"), graphics, scenario,
                                        "http://127.0.0.1:" + server.getAddress().getPort() + "/nested/game/");
                            }
                        }
                    } finally { server.stop(0); }
                }
            }
        }
    }

    private static void run(Browser browser, Path root, boolean wasm, String graphics, String scenario, String url) throws Exception {
        String html = Files.readString(root.resolve("index.html"));
        if (html.contains("libfdx-bootstrap-data") || html.contains("runtimeBase") || html.contains("application/json")
                || html.contains("scripts/")) throw new AssertionError("Framework metadata leaked into index.html");
        String label = (wasm ? "wasm" : "js") + "-" + graphics + " " + scenario;
        System.out.println("BOOTSTRAP_START " + label);
        try (BrowserContext context = browser.newContext(); Page page = context.newPage()) {
            page.setDefaultTimeout(30_000);
            List<String> requests = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            List<Route> held = new ArrayList<>();
            page.onRequest(request -> requests.add(request.url()));
            page.onPageError(errors::add);
            page.addInitScript("""
                    window.bootstrapFrames=0;window.nativeStarts=0;window.nativeReady=false;
                    requestAnimationFrame(function tick(){window.bootstrapFrames++;requestAnimationFrame(tick);});
                    """);
            String nativeScript = Files.readString(root.resolve("scripts/fdx.js")) + """
                    \nvar originalFdxModule=FdxModule;
                    FdxModule=function(options){window.nativeStarts++;return originalFdxModule(options).then(function(module){
                        window.nativeReady=true;return module;
                    });};
                    """;
            page.route("**/scripts/fdx.js", route -> {
                if (scenario.equals("missing-script")) route.fulfill(new Route.FulfillOptions().setStatus(404));
                else if (scenario.equals("invalid-script")) route.fulfill(new Route.FulfillOptions().setBody("/* no FdxModule */").setContentType("application/javascript"));
                else if (scenario.equals("delayed-script")) held.add(route);
                else route.fulfill(new Route.FulfillOptions().setBody(nativeScript).setContentType("application/javascript"));
            });
            page.route("**/scripts/fdx.wasm", route -> {
                if (scenario.equals("missing-wasm")) route.fulfill(new Route.FulfillOptions().setStatus(404));
                else if (scenario.equals("invalid-wasm")) route.fulfill(new Route.FulfillOptions().setBody("invalid wasm").setContentType("application/wasm"));
                else if (scenario.equals("delayed-wasm") || scenario.equals("dispose")) held.add(route);
                else route.resume();
            });
            if (scenario.equals("missing-app") || scenario.equals("invalid-app")) {
                String app = wasm ? (scenario.equals("missing-app") ? "app.wasm-runtime.js" : "app.wasm") : "app.js";
                page.route("**/" + app, route -> route.fulfill(new Route.FulfillOptions()
                        .setStatus(scenario.equals("missing-app") ? 404 : 200)
                        .setContentType(wasm && app.endsWith(".wasm") ? "application/wasm" : "application/javascript")
                        .setBody(scenario.equals("missing-app") ? "" : "invalid application")));
            }
            page.navigate(url + "?bootstrapProbe=true&graphics=" + graphics,
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            boolean pending = scenario.startsWith("delayed-") || scenario.equals("dispose");
            if (pending) {
                page.waitForCondition(() -> !held.isEmpty()
                        && Boolean.TRUE.equals(page.evaluate("() => window.libfdxBootstrapProbe?.startReturned===1")));
                int initialFrames = ((Number) page.evaluate("window.bootstrapFrames")).intValue();
                page.waitForFunction("initial => window.bootstrapFrames > initial+5", initialFrames);
                require(page, "() => !window.nativeReady && window.libfdxBootstrapProbe.preload===0 && window.libfdxBootstrapProbe.created===0", "Listeners created before native runtime");
                if (scenario.equals("dispose")) page.evaluate("window.libfdxBootstrapProbe.dispose()");
                Route route = held.getFirst();
                if (scenario.equals("delayed-script")) route.fulfill(new Route.FulfillOptions().setBody(nativeScript).setContentType("application/javascript"));
                else route.resume();
                page.waitForFunction("() => window.nativeReady");
                if (scenario.equals("dispose")) {
                    int readyFrames = ((Number) page.evaluate("window.bootstrapFrames")).intValue();
                    page.waitForFunction("initial => window.bootstrapFrames > initial+10", readyFrames);
                    require(page, "() => window.libfdxBootstrapProbe.disposed===1 && window.libfdxBootstrapProbe.preload===0 && window.libfdxBootstrapProbe.created===0", "Late listener creation after disposal");
                } else {
                    page.waitForFunction("() => window.libfdxBootstrapProbe.created===1");
                    checkCompiledMetadata(page, root);
                    require(page, "() => window.libfdxBootstrapProbe.preload===1", "Preload listener count");
                    page.evaluate("window.libfdxBootstrapProbe.dispose()");
                }
                require(page, "() => window.nativeStarts===1", "Native initialization count");
                if (requests.stream().filter(request -> request.endsWith("/scripts/fdx.wasm")).count() != 1)
                    throw new AssertionError("Native Wasm must be fetched exactly once");
                if (!errors.isEmpty()) throw new AssertionError(errors.toString());
            } else if (scenario.startsWith("runtime-")) {
                page.waitForFunction("() => window.libfdxBootstrapProbe?.created===1");
                if (scenario.equals("runtime-error")) {
                    page.evaluate("() => { setTimeout(() => { throw new Error('runtime error probe'); }, 0); }");
                } else {
                    page.evaluate("() => { Promise.reject(new Error('runtime rejection probe')); }");
                }
                page.waitForFunction("() => document.getElementById('libfdx-error')?.textContent.includes('probe')");
                require(page, "() => document.querySelectorAll('#libfdx-error').length===1", "Duplicate error overlay");
                page.evaluate("window.libfdxBootstrapProbe.dispose()");
            } else {
                if (scenario.endsWith("-app")) {
                    // The backend cannot display an overlay when the application never executes.
                    page.waitForCondition(() -> !errors.isEmpty());
                    require(page, "() => !document.getElementById('libfdx-error')", "Page owns runtime error handling");
                } else {
                    page.waitForFunction("() => !!document.getElementById('libfdx-error')?.textContent");
                    String detail = page.locator("#libfdx-error").textContent();
                    if (!detail.contains("Web runtime startup failed:") || !detail.contains("fdx."))
                        throw new AssertionError("Startup failure lost its actionable cause: " + detail);
                }
                require(page, "() => !window.libfdxBootstrapProbe || (window.libfdxBootstrapProbe.preload===0 && window.libfdxBootstrapProbe.created===0)", "Listener created after startup failure");
                if (scenario.equals("missing-script")) {
                    Path captures = Path.of("build/web-bootstrap");
                    Files.createDirectories(captures);
                    page.screenshot(new Page.ScreenshotOptions().setPath(captures.resolve(label.replace(' ', '-') + ".png")));
                }
            }
            require(page, "() => typeof window.libfdxStartupError==='undefined'", "HTML startup error global remains");
            require(page, "() => window.libfdxPublishedAssets===undefined && window.libfdxShaderCompilerIdentity===undefined"
                    + " && window.libfdxRuntimeBaseUrl===undefined", "Legacy page configuration remains");
            if (requests.stream().anyMatch(request -> request.contains("fdx-loader.js"))) throw new AssertionError("Legacy loader requested");
            if (!scenario.endsWith("-app")) {
                int application = indexOf(requests, wasm ? "/app.wasm" : "/app.js");
                int nativeRequest = indexOf(requests, "/scripts/fdx.js");
                if (application < 0 || nativeRequest <= application) throw new AssertionError("Application must load before native runtime: " + requests);
            }
            System.out.println("BOOTSTRAP_PASS " + label);
        }
    }

    private static void checkCompiledMetadata(Page page, Path root) throws Exception {
        var expected = new java.util.TreeMap<String, Long>();
        Path assetsRoot = root.resolve("assets");
        try (var files = Files.walk(assetsRoot)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                expected.put(assetsRoot.relativize(file).toString().replace('\\', '/'), Files.size(file));
            }
        }
        Map<?, ?> actual = (Map<?, ?>) page.evaluate("window.libfdxBootstrapProbe.assets");
        if (!expected.keySet().equals(actual.keySet())) throw new AssertionError("Compiled and packaged asset paths differ");
        for (var entry : expected.entrySet()) {
            if (((Number) actual.get(entry.getKey())).longValue() != entry.getValue())
                throw new AssertionError("Compiled asset size differs: " + entry.getKey());
        }
        StringBuilder identity = new StringBuilder("fdx-web-fdxr2-v1");
        for (String file : List.of("fdx.js", "fdx.wasm")) {
            identity.append(':').append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(root.resolve("scripts").resolve(file)))));
        }
        if (!identity.toString().equals(page.evaluate("window.libfdxBootstrapProbe.compilerIdentity")))
            throw new AssertionError("Compiled compiler fingerprint differs from packaged binaries");
    }

    private static int indexOf(List<String> requests, String suffix) {
        for (int i = 0; i < requests.size(); i++) if (requests.get(i).endsWith(suffix)) return i;
        return -1;
    }

    private static void require(Page page, String condition, String message) {
        if (!Boolean.TRUE.equals(page.evaluate(condition))) throw new AssertionError(message);
    }
}
