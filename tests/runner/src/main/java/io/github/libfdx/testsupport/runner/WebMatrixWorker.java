package io.github.libfdx.testsupport.runner;

import com.microsoft.playwright.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fresh browser and local server for one pair; the outer JVM supervisor enforces the hard timeout. */
public final class WebMatrixWorker {
    public static void main(String[] args) throws Exception {
        String variant = System.getProperty("libfdx.test.graphics");
        String directoryProperty = variant.startsWith("wasm-") ? "libfdx.test.autoWebWasmDirectory" : "libfdx.test.autoWebJsDirectory";
        Path directory = Path.of(System.getProperty(directoryProperty)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(directory.resolve("index.html"))) throw new IllegalStateException("Missing web build: " + directory);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/favicon.ico")) {
                exchange.sendResponseHeaders(204, -1); exchange.close(); return;
            }
            Path file = directory.resolve("." + exchange.getRequestURI().getPath()).normalize();
            if (Files.isDirectory(file)) file = file.resolve("index.html");
            if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1); exchange.close(); return;
            }
            String name = file.toString();
            String mime = name.endsWith(".wasm") ? "application/wasm" : name.endsWith(".js") ? "application/javascript"
                    : name.endsWith(".html") ? "text/html" : Files.probeContentType(file);
            exchange.getResponseHeaders().set("Content-Type", mime == null ? "application/octet-stream" : mime);
            exchange.getResponseHeaders().set("Cross-Origin-Opener-Policy", "same-origin");
            exchange.getResponseHeaders().set("Cross-Origin-Embedder-Policy", "require-corp");
            exchange.sendResponseHeaders(200, Files.size(file));
            try (var stream = exchange.getResponseBody()) { Files.copy(file, stream); }
        });
        server.start();
        Path marker = Path.of(System.getProperty("libfdx.test.autoCompletionFile"));
        boolean completed = false;
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
                    if (message.type().equals("error")) errors.add(message.text());
                });
                page.onPageError(error -> { errors.add(error); System.err.println(error); });
                page.onCrash(ignored -> errors.add("Browser page crashed"));
                String api = variant.endsWith("webgpu") ? "webgpu" : "webgl";
                String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/?test="
                        + System.getProperty("libfdx.test.name") + "&graphics=" + api + "&frames=-1&autoChild=true&autoDurationSeconds="
                        + System.getProperty("libfdx.test.autoDurationSeconds", "6");
                StringBuilder optionsQuery = new StringBuilder(url);
                for (String option : List.of("shaderAsync", "shaderLoadingOnly", "shaderPreload", "shaderCount",
                        "shaderSeed", "shaderInvalidIndex", "shaderVerifyPixels")) {
                    String value = System.getProperty("libfdx.test." + option);
                    if (value != null) optionsQuery.append('&').append(option).append('=')
                            .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
                page.navigate(optionsQuery.toString());
                page.waitForCondition(() -> !errors.isEmpty() || Boolean.TRUE.equals(page.evaluate("() => !!window.libfdxAutoReady")));
                if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
                // Wait until the scenario installed input handlers before activating browser audio.
                page.locator("#libfdx-canvas").click(new Locator.ClickOptions().setTimeout(15_000));
                page.waitForCondition(() -> !errors.isEmpty() || Boolean.TRUE.equals(page.evaluate("() => !!window.libfdxAutoResult")),
                        new Page.WaitForConditionOptions().setTimeout(Double.parseDouble(System.getProperty("libfdx.test.autoTimeoutSeconds", "180")) * 1000));
                if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
                String status = String.valueOf(page.evaluate("() => window.libfdxAutoResult.status"));
                if (!status.equals("PASS")) throw new IllegalStateException(String.valueOf(page.evaluate("() => window.libfdxAutoResult.detail")));
                page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(marker + ".png")));
                completed = true;
            }
        } finally {
            server.stop(0);
        }
        if (completed) Files.writeString(marker, "completed\n");
    }
}
