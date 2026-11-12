package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** External build/preview supervisor. Launches ordinary Java main classes; never loads user classes. */
public final class UiPreviewWatch {
    private final Path modelFile;
    private final Function<PreviewLaunch, List<String>> buildCommand;
    private final List<String> workerJvmArguments;
    private PreviewLaunch launch;
    private Process build;
    private Worker current;
    private Worker candidate;
    private long generation;
    private boolean pending;
    private long changedAt;
    private Map<Path, Long> observed;
    private final Set<Path> dirty = new HashSet<>();
    private final ExecutorService compilation = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("ui-preview-compiler").factory());
    private PreviewJavaCompiler compiler;
    private final PreviewJavaOutputs outputs;
    private Future<PreviewJavaCompiler.Result> javaBuild;
    private Future<?> warmup;
    private Path javaBuildDirectory;
    private long revision;
    private long reloadRequest;
    private long reloadStarted;
    private long refreshStarted;
    private long refresh;
    private long compileMillis;
    private long refreshMillis;
    private String refreshMode = "";
    private boolean forceGradle;
    private PreviewSourceEvents sourceEvents;
    private long nextSourceScan;
    private volatile boolean stopping;
    private Path failedDirectory;
    private boolean cleaned;
    private String lastEntryRequest = "";
    private String requestedEntry = "";
    private boolean switchPending;

    private record Worker(Process process, Path directory, long generation, long started, String requestedTarget,
                          List<String> classpath, List<String> entries, String main,
                          PreviewSessionFiles.EntrySelection restoredSelection, List<String> applicationClasspath) { }

    UiPreviewWatch(Path modelFile, Function<PreviewLaunch, List<String>> buildCommand,
                   List<String> workerJvmArguments) throws IOException {
        this.modelFile = modelFile.toAbsolutePath().normalize();
        this.buildCommand = buildCommand;
        this.workerJvmArguments = List.copyOf(workerJvmArguments);
        launch = new PreviewLaunch(this.modelFile);
        compiler = new PreviewJavaCompiler(launch);
        outputs = new PreviewJavaOutputs(launch.session.resolve("java-output"));
    }

    /**
     * Starts the watcher from an existing desktop launcher. JVM options selected by the
     * caller apply to each replacement worker, after the configured options.
     *
     * @param modelFile the launch metadata prepared by Gradle
     * @param workerJvmArguments additional JVM options for every worker
     * @return the running watcher process
     * @throws IOException if the watcher cannot be started
     */
    public static Process start(Path modelFile, List<String> workerJvmArguments) throws IOException {
        Path model = modelFile.toAbsolutePath().normalize();
        PreviewLaunch launch = new PreviewLaunch(model);
        Files.createDirectories(launch.session);
        Path controller = launch.session.resolve("controller.jar");
        try (var channel = FileChannel.open(launch.session.resolve("controller.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("A UI preview is already running for this module");
            Files.copy(launch.controllerClasspath(), controller, StandardCopyOption.REPLACE_EXISTING);
        }

        List<String> command = new ArrayList<>(List.of(launch.java, "-classpath", controller.toString(),
                UiPreviewWatch.class.getName(), model.toString()));
        command.addAll(workerJvmArguments);
        return new ProcessBuilder(command).directory(launch.projectDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(launch.session.resolve("watch.log").toFile()))
                .start();
    }

    /** Tool entry point. The first argument is launch metadata, followed by optional worker JVM options. */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Usage: UiPreviewWatch <launch.properties> [worker JVM options]");
        UiPreviewWatch watch = new UiPreviewWatch(Path.of(args[0]), PreviewLaunch::buildCommand,
                Arrays.asList(args).subList(1, args.length));
        Files.createDirectories(watch.launch.session);
        try (var channel = FileChannel.open(watch.launch.session.resolve("controller.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("A UI preview is already running for this module");
            Thread shutdown = new Thread(() -> { watch.stopping = true; watch.cleanup(); }, "ui-preview-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try { watch.run(); }
            finally { watch.stopping = true; watch.cleanup(); Runtime.getRuntime().removeShutdownHook(shutdown); }
        }
    }

    void run() throws Exception {
        Files.deleteIfExists(launch.session.resolve("stop.request"));
        outputs.clear();
        observed = PreviewSources.scan(launch.strings("watch"));
        openSourceEvents();
        PreviewJavaCompiler initialCompiler = compiler;
        warmup = compilation.submit(() -> { initialCompiler.warm(); return null; });
        Files.writeString(launch.session.resolve("controller.ready"), Long.toString(ProcessHandle.current().pid()));
        status("starting", "Starting preview");
        startCandidate(null);
        while (!stopping && !Files.exists(launch.session.resolve("stop.request"))) {
            if (current != null && !current.process.isAlive()) {
                int exit = current.process.exitValue();
                if (exit == 0) break; // Closing the preview also closes the watcher.
                retainFailure(current); current = null;
                status("runtime-failed", "Preview exited " + exit + "; edit a source file to retry; see " + failedDirectory.resolve("preview.log"));
            }
            pollSources();
            readEntryRequest();
            pollCandidate();
            pollJavaBuild();
            pollReload();
            if (build != null && !build.isAlive()) {
                int exit = build.exitValue(); build = null;
                if (exit != 0) status("build-failed", "Compilation failed (exit " + exit + "); previous preview retained; see build.log");
                else if (pending) {
                    forceGradle = true;
                    status("queued", "Newer edits pending; compiling latest sources");
                }
                else {
                    launch = new PreviewLaunch(modelFile);
                    openSourceEvents();
                    if (warmup != null) {
                        try { warmup.get(); }
                        catch (java.util.concurrent.ExecutionException failure) { System.err.println(failure.getCause()); }
                    }
                    compiler.close();
                    compiler = new PreviewJavaCompiler(launch);
                    outputs.clear();
                    dirty.clear();
                    forceGradle = false;
                    PreviewJavaCompiler preparedCompiler = compiler;
                    warmup = compilation.submit(() -> { preparedCompiler.warm(); return null; });
                    startCandidate(null);
                }
            }
            if (pending && build == null && javaBuild == null && reloadRequest == 0 && candidate == null
                    && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - changedAt) >= launch.milliseconds("debounceMillis", 300)) {
                pending = false;
                refreshStarted = changedAt;
                startBuild();
            }
            if (switchPending && !pending && build == null && javaBuild == null && reloadRequest == 0
                    && candidate == null && current != null) {
                startCandidate(current);
            }
            Thread.sleep(launch.milliseconds("pollMillis", 300));
        }
        status("stopped", "Preview closed");
    }

    private void openSourceEvents() throws IOException {
        if (sourceEvents != null) sourceEvents.close();
        try { sourceEvents = new PreviewSourceEvents(launch.strings("watch")); }
        catch (IOException failure) {
            sourceEvents = null;
            System.err.println("[ui-preview] Native source notifications unavailable; polling: " + failure);
        }
    }

    private void pollSources() throws IOException {
        if (sourceEvents != null && !sourceEvents.changed() && System.nanoTime() < nextSourceScan) return;
        nextSourceScan = System.nanoTime() + 2_000_000_000L;
        Map<Path, Long> next = PreviewSources.scan(launch.strings("watch"));
        Set<Path> changes = PreviewSources.changes(observed, next);
        if (!changes.isEmpty()) {
            dirty.addAll(changes);
            observed = next; pending = true; changedAt = System.nanoTime();
        }
    }

    private void startBuild() throws IOException {
        boolean assetsOnly = !dirty.isEmpty() && dirty.stream().allMatch(path -> launch.strings("assets").stream()
                .map(Path::of).map(p -> p.toAbsolutePath().normalize()).anyMatch(path::startsWith));
        if (!forceGradle && assetsOnly && canReload()) {
            refreshMode = "assets";
            compileMillis = 0;
            dirty.clear();
            revision++;
            requestCompositionReload();
        } else if (!forceGradle && compiler.supports(dirty)) {
            refreshMode = "java";
            javaBuildDirectory = launch.session.resolve("compile-" + ProcessHandle.current().pid() + "-" + (++revision));
            List<String> classpath = new ArrayList<>(outputs.classpath());
            classpath.addAll(current == null ? launch.strings("classpath") : current.classpath);
            Set<Path> changed = Set.copyOf(dirty);
            javaBuild = compilation.submit(() -> compiler.compile(changed, classpath, javaBuildDirectory));
            status("compiling", "Compiling " + changed.size() + " changed Java file(s) directly");
        } else {
            refreshMode = "gradle";
            build = new ProcessBuilder(buildCommand.apply(launch)).directory(launch.projectDirectory.toFile())
                    .redirectErrorStream(true).redirectOutput(launch.session.resolve("build.log").toFile()).start();
            status("building", "Rebuilding project inputs with Gradle; current preview remains active");
        }
    }

    private void pollJavaBuild() throws Exception {
        if (javaBuild == null || !javaBuild.isDone()) return;
        try {
            PreviewJavaCompiler.Result result;
            try { result = javaBuild.get(); }
            catch (java.util.concurrent.ExecutionException failure) {
                forceGradle = true;
                pending = true;
                status("queued", "Direct compiler unavailable; using Gradle: " + failure.getCause());
                return;
            }
            if (pending) return; // dirty includes all in-flight edits until a successful latest compilation.
            compileMillis = result.milliseconds();
            Files.writeString(launch.session.resolve("build.log"), result.diagnostics());
            if (result.state() == PreviewJavaCompiler.State.GRADLE) {
                forceGradle = true; pending = true;
                status("queued", result.diagnostics());
            } else if (result.state() == PreviewJavaCompiler.State.FAILED) {
                status("build-failed", "Java compilation failed; previous preview retained; see build.log");
            } else {
                outputs.accept(result);
                compiler.accept(result);
                dirty.clear();
                if (canReload() && result.outputs().keySet().stream().allMatch(PreviewJavaCompiler.Module::reloadable)) {
                    refreshMode = "java-reload";
                    requestCompositionReload();
                } else {
                    refreshMode = "java-restart";
                    startCandidate(null);
                }
            }
        } finally {
            javaBuild = null;
            PreviewSnapshot.deleteOwned(launch.session, javaBuildDirectory);
            javaBuildDirectory = null;
        }
    }

    private boolean canReload() {
        return current != null && Boolean.parseBoolean(launch.values.getProperty("fastReload", "false"))
                && Files.isRegularFile(current.directory.resolve(PreviewReloadRequest.AVAILABLE));
    }

    private void requestCompositionReload() throws IOException {
        Path directory = current.directory.resolve("reload-" + revision);
        List<String> classpath = new ArrayList<>(outputs.snapshot(directory, true));
        classpath.addAll(current.applicationClasspath);
        new PreviewReloadRequest(revision, classpath, current.entries)
                .write(current.directory.resolve(PreviewReloadRequest.REQUEST));
        reloadRequest = revision;
        reloadStarted = System.nanoTime();
        status("reloading", "Updating composition in the current window");
    }

    private void pollReload() throws IOException {
        if (reloadRequest == 0) return;
        if (current == null) { reloadRequest = 0; return; }
        Path response = current.directory.resolve(PreviewReloadRequest.RESPONSE);
        if (Files.isRegularFile(response)) {
            Properties result = PreviewReloadRequest.readValues(response);
            if (Long.toString(reloadRequest).equals(result.getProperty("revision"))) {
                reloadRequest = 0;
                if ("applied".equals(result.getProperty("state"))) {
                    refreshed();
                    status("running", "Composition updated in " + refreshMillis + " ms (Java " + compileMillis + " ms)");
                } else if ("restart".equals(result.getProperty("state"))) {
                    refreshMode = "java-restart";
                    startCandidate(null);
                } else status("runtime-failed", result.getProperty("message", "Composition reload failed; previous preview retained"));
                return;
            }
        }
        if ((System.nanoTime() - reloadStarted) / 1_000_000 > launch.milliseconds("startupTimeoutMillis", 30_000)) {
            reloadRequest = 0;
            refreshMode = "java-restart";
            startCandidate(null);
        }
    }

    private void refreshed() {
        if (refreshStarted == 0) return;
        refresh++;
        refreshMillis = (System.nanoTime() - refreshStarted) / 1_000_000;
        refreshStarted = 0;
    }

    private void startCandidate(Worker snapshot) throws IOException {
        Path directory = launch.session.resolve("generation-" + ProcessHandle.current().pid() + "-" + (++generation));
        PreviewSnapshot.deleteOwned(launch.session, directory);
        try {
            List<String> base = snapshot == null ? PreviewSnapshot.copy(launch, directory)
                    : PreviewSnapshot.copy(snapshot.classpath, snapshot.directory, directory);
            List<String> classpath = new ArrayList<>(outputs.snapshot(directory.resolve("java"), false));
            classpath.addAll(base);
            classpath = classpath.stream().distinct().toList();
            List<String> application = new ArrayList<>(outputs.applicationPaths(directory.resolve("java")));
            application.addAll(snapshot == null ? PreviewSnapshot.applicationClasspath(launch, directory)
                    : PreviewSnapshot.relocated(snapshot.applicationClasspath, snapshot.directory, directory));
            application = application.stream().distinct().toList();
            List<String> entries = List.of();
            String main = launch.main;
            String target = "";
            PreviewSessionFiles.EntrySelection selection = null;
            List<String> arguments = new ArrayList<>(workerJvmArguments);
            if (current != null) arguments.add("-D" + PreviewScrollHandoff.RESTORE_PROPERTY + "="
                    + current.directory.resolve(PreviewScrollHandoff.FILE));
            if (main.isEmpty()) {
                entries = snapshot == null ? PreviewEntries.discover(classpath, directory) : snapshot.entries;
                selection = chooseEntry(entries);
                if (selection == null) {
                    PreviewSnapshot.deleteOwned(launch.session, directory);
                    switchPending = false;
                    return;
                }
                main = selection.entryClass();
                Path catalog = directory.resolve("entries.txt");
                Files.write(catalog, entries);
                arguments.add("-D" + PreviewEntries.CATALOG_PROPERTY + "=" + catalog);
                arguments.add("-D" + PreviewEntries.CLASS_PROPERTY + "=" + main);
                PreviewSessionFiles.writeSelection(directory.resolve(PreviewSessionFiles.SELECTION_FILE_NAME), selection);
            } else {
                target = currentTarget();
                arguments.add("-D" + PreviewSessionFiles.RESTORED_TARGET + "=" + target);
            }
            Process process = new ProcessBuilder(PreviewSnapshot.workerCommand(launch, classpath, directory,
                    generation, arguments, main)).directory(launch.workingDirectory.toFile()).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("preview.log").toFile()).start();
            candidate = new Worker(process, directory, generation, System.nanoTime(), target, classpath, entries, main,
                    selection, application);
            switchPending = false;
            status("starting", "Starting generation " + generation + " (" + main + ")");
        } catch (IOException | IllegalArgumentException failure) {
            if (candidate != null) throw failure;
            PreviewSnapshot.deleteOwned(launch.session, directory);
            switchPending = false;
            status("runtime-failed", "Cannot prepare preview worker: " + failure);
        }
    }

    private PreviewSessionFiles.EntrySelection chooseEntry(List<String> entries) throws IOException {
        if (entries.isEmpty()) {
            status("target-unavailable", "No UiPreviewEntry classes found in compiled project outputs");
            return null;
        }
        if (current != null) return currentSelection(entries);
        String initial = "";
        var options = new ArrayList<>(launch.strings("jvmArguments"));
        options.addAll(workerJvmArguments);
        String prefix = "-Dlibfdx.uiPreview.target=";
        for (String option : options) {
            if (option.startsWith(prefix)) initial = option.substring(prefix.length());
        }
        if (initial.isEmpty()) initial = entries.getFirst();
        if (entries.contains(initial)) return new PreviewSessionFiles.EntrySelection(initial, "");
        status("target-unavailable", "No UiPreviewEntry named " + initial);
        return null;
    }

    private void readEntryRequest() throws IOException {
        if (current == null || current.entries.isEmpty()) return;
        Path file = current.directory.resolve(PreviewEntries.REQUEST_FILE);
        if (!Files.isRegularFile(file)) return;
        String request = Files.readString(file);
        if (request.equals(lastEntryRequest)) return;
        lastEntryRequest = request;
        int split = request.indexOf('\n');
        if (split < 0) return;
        String entry = request.substring(split + 1);
        if (entry.isEmpty() || current.entries.contains(entry)) {
            requestedEntry = entry;
            switchPending = !entry.isEmpty();
        }
    }

    private String currentTarget() throws IOException {
        return current == null ? "" : PreviewSessionFiles.read(current.directory);
    }

    private PreviewSessionFiles.EntrySelection currentSelection(List<String> entries) throws IOException {
        if (entries.contains(requestedEntry)) return new PreviewSessionFiles.EntrySelection(requestedEntry, "");
        var selected = PreviewSessionFiles.readSelection(
                current.directory.resolve(PreviewSessionFiles.SELECTION_FILE_NAME), current.main);
        if (entries.contains(selected.entryClass())) return selected;
        return new PreviewSessionFiles.EntrySelection(entries.getFirst(), "");
    }

    private boolean entryChangedDuringStartup() throws IOException {
        return current != null && !candidate.entries.isEmpty()
                && !candidate.restoredSelection.equals(currentSelection(candidate.entries));
    }

    private void pollCandidate() throws IOException, InterruptedException {
        if (candidate == null) return;
        if (Files.exists(candidate.directory.resolve("ready"))) {
            if (pending) { stop(candidate); delete(candidate); candidate = null; return; }
            if (entryChangedDuringStartup()) {
                Worker superseded = candidate;
                candidate = null;
                try { startCandidate(superseded); }
                finally { stop(superseded); delete(superseded); }
                return;
            }
            if (candidate.entries.isEmpty() && !candidate.requestedTarget.equals(currentTarget())) {
                stop(candidate);
                delete(candidate);
                candidate = null;
                startCandidate(null);
                return;
            }
            Worker previous = current;
            current = candidate; candidate = null;
            lastEntryRequest = "";
            requestedEntry = "";
            switchPending = false;
            if (previous != null) {
                try {
                    if (warmup != null) warmup.get();
                    compiler.releaseClasspath();
                } catch (java.util.concurrent.ExecutionException failure) {
                    System.err.println("[ui-preview] Compiler warmup failed: " + failure.getCause());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                stop(previous); delete(previous);
            }
            refreshed();
            status("running", "Preview generation " + current.generation + " ready");
        } else if (!candidate.process.isAlive()) {
            int exit = candidate.process.exitValue();
            if (exit == 0 && candidate.entries.isEmpty()) { stopping = true; return; }
            long failedGeneration = candidate.generation;
            retainFailure(candidate); candidate = null;
            status("runtime-failed", "Generation " + failedGeneration + " exited " + exit
                    + "; previous preview retained; see " + failedDirectory.resolve("preview.log"));
        } else if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - candidate.started)
                > launch.milliseconds("startupTimeoutMillis", 30_000)) {
            stop(candidate);
            retainFailure(candidate); candidate = null;
            status("runtime-failed", "Preview startup timed out; previous preview retained; see " + failedDirectory.resolve("preview.log"));
        }
    }

    private void retainFailure(Worker worker) {
        if (failedDirectory != null && !failedDirectory.equals(worker.directory)) {
            try { PreviewSnapshot.deleteOwned(launch.session, failedDirectory); }
            catch (IOException error) { System.err.println("[ui-preview] Cannot remove failed snapshot: " + error); }
        }
        failedDirectory = worker.directory;
    }

    private void status(String state, String message) throws IOException {
        Properties status = new Properties();
        status.setProperty("state", state); status.setProperty("message", message);
        status.setProperty("updated", Instant.now().toString());
        status.setProperty("controllerPid", Long.toString(ProcessHandle.current().pid()));
        status.setProperty("generation", Long.toString(current != null ? current.generation : 0));
        status.setProperty("refresh", Long.toString(refresh));
        status.setProperty("refreshMode", refreshMode);
        status.setProperty("compileMillis", Long.toString(compileMillis));
        status.setProperty("refreshMillis", Long.toString(refreshMillis));
        status.setProperty("workerPid", Long.toString(current != null ? current.process.pid() : 0));
        status.setProperty("candidatePid", Long.toString(candidate != null ? candidate.process.pid() : 0));
        status.setProperty("buildPid", Long.toString(build != null ? build.pid() : 0));
        if (current != null) status.setProperty("workerDirectory", current.directory.toString());
        if (candidate != null) status.setProperty("candidateDirectory", candidate.directory.toString());
        if (failedDirectory != null) status.setProperty("failedDirectory", failedDirectory.toString());
        Path temporary = launch.session.resolve("status.tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { status.store(writer, null); }
        PreviewSessionFiles.replace(temporary, launch.session.resolve("status.properties"));
        System.out.println("[ui-preview] " + state + ": " + message);
        System.out.flush();
    }

    private void stop(Worker worker) {
        try {
            Files.writeString(worker.directory.resolve("stop.request"), "stop\n");
            if (worker.process.waitFor(5, TimeUnit.SECONDS)) return;
        } catch (IOException ignored) { }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        worker.process.destroy();
        try { if (!worker.process.waitFor(1, TimeUnit.SECONDS)) worker.process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { worker.process.destroyForcibly(); Thread.currentThread().interrupt(); }
    }

    private void delete(Worker worker) {
        try { PreviewSnapshot.deleteOwned(launch.session, worker.directory); }
        catch (IOException error) { System.err.println("[ui-preview] Cannot remove old snapshot: " + error); }
    }

    synchronized void cleanup() {
        if (cleaned) return;
        cleaned = true;
        try { if (sourceEvents != null) sourceEvents.close(); }
        catch (IOException error) { System.err.println("[ui-preview] Cannot close file notifications: " + error); }
        compilation.shutdownNow();
        try {
            if (compilation.awaitTermination(5, TimeUnit.SECONDS)) compiler.close();
        } catch (IOException error) { System.err.println("[ui-preview] Cannot close compiler: " + error); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (build != null) {
            build.destroy();
            try { if (!build.waitFor(2, TimeUnit.SECONDS)) build.destroyForcibly().waitFor(2, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { build.destroyForcibly(); Thread.currentThread().interrupt(); }
            build = null;
        }
        if (candidate != null) { stop(candidate); delete(candidate); candidate = null; }
        if (current != null) { stop(current); delete(current); current = null; }
        try { status("stopped", "Preview watcher stopped"); Files.deleteIfExists(launch.session.resolve("controller.ready")); }
        catch (IOException error) { System.err.println("[ui-preview] Cannot write final status: " + error); }
    }

}
