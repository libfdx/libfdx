package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.classfile.ClassFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** A warm JDK compiler. All outputs stay isolated from Gradle's normal class directories and jars. */
final class PreviewJavaCompiler implements AutoCloseable {
    record Module(int index, boolean direct, boolean reloadable, Path output, Set<Path> sources,
                  List<String> classpath, List<String> options) { }

    enum State { COMPILED, FAILED, GRADLE }

    record Result(State state, Map<Module, Path> outputs, Map<String, List<String>> api, Map<String, Set<String>> sourceClasses,
                  String diagnostics, long milliseconds) { }

    private final PreviewLaunch launch;
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final Map<Path, Module> owners = new HashMap<>();
    private final Map<String, List<String>> api = new HashMap<>();
    private final Map<Module, List<String>> classpaths = new HashMap<>();
    private final PreviewCompilerInputs inputs;
    private final Map<String, Set<String>> sourceClasses = new HashMap<>();
    private final Set<Module> incompleteSources = new java.util.HashSet<>();
    private StandardJavaFileManager files;
    private boolean initialized;

    PreviewJavaCompiler(PreviewLaunch launch) {
        this.launch = launch;
        inputs = new PreviewCompilerInputs(launch);
        for (int i = 0; i < Integer.parseInt(launch.values.getProperty("java.count", "0")); i++) {
            String key = "java." + i;
            Set<Path> sources = launch.strings(key + ".sources").stream()
                    .map(p -> Path.of(p).toAbsolutePath().normalize()).collect(java.util.stream.Collectors.toSet());
            Module module = new Module(i, Boolean.parseBoolean(launch.values.getProperty(key + ".direct")),
                    Boolean.parseBoolean(launch.values.getProperty(key + ".reloadable")),
                    Path.of(launch.values.getProperty(key + ".output")), sources,
                    launch.strings(key + ".classpath"), launch.strings(key + ".options"));
            for (Path source : sources) owners.put(source, module);
        }
    }

    boolean supports(Set<Path> changed) {
        return compiler != null && Boolean.parseBoolean(launch.values.getProperty("fastReload", "false"))
                && !changed.isEmpty() && changed.stream().allMatch(path -> Files.isRegularFile(path)
                && owners.containsKey(path) && owners.get(path).direct() && !path.endsWith("module-info.java"));
    }

    void warm() throws IOException {
        if (initialized || compiler == null) return;
        if (!Boolean.parseBoolean(launch.values.getProperty("fastReload", "false"))
                || owners.values().stream().noneMatch(Module::direct)) {
            initialized = true;
            return;
        }
        files = compiler.getStandardFileManager(null, null, null);
        for (Module module : new java.util.HashSet<>(owners.values())) {
            if (module.direct()) classpaths.put(module, inputs.snapshot(module.classpath()));
            if (!module.direct() || !Files.isDirectory(module.output())) continue;
            try (var paths = Files.walk(module.output())) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                    var model = ClassFile.of().parse(file);
                    api.put(model.thisClass().asInternalName(), PreviewJavaApi.signature(model));
                    var source = model.findAttribute(java.lang.classfile.Attributes.sourceFile());
                    if (source.isEmpty()) incompleteSources.add(module);
                    else sourceClasses.computeIfAbsent(module.index() + ":" + source.get().sourceFile().stringValue(),
                            key -> new java.util.HashSet<>()).add(model.thisClass().asInternalName());
                }
            }
        }
        Path warmup = launch.session.resolve("compiler-warmup");
        Files.createDirectories(warmup);
        JavaFileObject source = new javax.tools.SimpleJavaFileObject(java.net.URI.create("string:///PreviewCompilerWarmup.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "final class PreviewCompilerWarmup { int value() { return 1; } }";
            }

        };
        compiler.getTask(new StringWriter(), files, null, List.of("-proc:none", "-d", warmup.toString()),
                null, List.of(source)).call();
        initialized = true;
    }

    Result compile(Set<Path> changed, List<String> classpath, Path directory) throws IOException {
        long started = System.nanoTime();
        warm();
        Map<Module, List<Path>> groups = new LinkedHashMap<>();
        changed.stream().sorted().forEach(path -> groups.computeIfAbsent(owners.get(path), key -> new ArrayList<>()).add(path));
        Map<Module, Path> outputs = new LinkedHashMap<>();
        Map<String, List<String>> nextApi = new HashMap<>();
        Map<String, Set<String>> nextSourceClasses = new HashMap<>();
        StringWriter log = new StringWriter();
        for (var group : groups.entrySet()) {
            Module module = group.getKey();
            if (incompleteSources.contains(module)) return result(State.GRADLE, outputs, nextApi, nextSourceClasses,
                    "Class source metadata is unavailable; using Gradle to track removed outputs", started);
            Path output = directory.resolve("module-" + module.index());
            Files.createDirectories(output);
            List<String> search = new ArrayList<>();
            for (Path path : outputs.values()) search.add(path.toString());
            search.addAll(classpath);
            search.addAll(classpaths.get(module));
            List<String> options = new ArrayList<>(module.options());
            options.addAll(List.of("-proc:none", "-implicit:none", "-sourcepath", "", "-classpath",
                    String.join(java.io.File.pathSeparator, search), "-d", output.toString()));
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            boolean success = compiler.getTask(log, files, diagnostics, options, null,
                    files.getJavaFileObjectsFromPaths(group.getValue())).call();
            for (var diagnostic : diagnostics.getDiagnostics()) log.append(diagnostic.toString()).append('\n');
            if (!success) return result(State.FAILED, outputs, nextApi, nextSourceClasses, log.toString(), started);
            outputs.put(module, output);
            try (var paths = Files.walk(output)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    var model = ClassFile.of().parse(path);
                    String name = model.thisClass().asInternalName();
                    List<String> signature = PreviewJavaApi.signature(model);
                    if (api.containsKey(name) && !api.get(name).equals(signature)) {
                        return result(State.GRADLE, outputs, nextApi, nextSourceClasses,
                                "Visible Java declarations/constants changed: " + name + "; recompiling dependents with Gradle", started);
                    }
                    nextApi.put(name, signature);
                    var source = model.findAttribute(java.lang.classfile.Attributes.sourceFile());
                    if (source.isPresent()) nextSourceClasses.computeIfAbsent(module.index() + ":"
                            + source.get().sourceFile().stringValue(), key -> new java.util.HashSet<>()).add(name);
                }
            }
            for (Path source : group.getValue()) {
                String key = module.index() + ":" + source.getFileName();
                if (!nextSourceClasses.getOrDefault(key, Set.of()).containsAll(sourceClasses.getOrDefault(key, Set.of()))) {
                    return result(State.GRADLE, outputs, nextApi, nextSourceClasses,
                            "Class outputs were removed or their source ownership is ambiguous; using Gradle", started);
                }
            }
        }
        return result(State.COMPILED, outputs, nextApi, nextSourceClasses, log.toString(), started);
    }

    void accept(Result result) {
        api.putAll(result.api());
        sourceClasses.putAll(result.sourceClasses());
    }

    void releaseClasspath() throws IOException {
        if (files == null) return;
        files.close();
        files = compiler.getStandardFileManager(null, null, null);
    }

    private Result result(State state, Map<Module, Path> outputs, Map<String, List<String>> signatures,
            Map<String, Set<String>> sources,
            String diagnostics, long started) {
        return new Result(state, outputs, signatures, sources, diagnostics, (System.nanoTime() - started) / 1_000_000);
    }

    @Override
    public void close() throws IOException {
        try { if (files != null) files.close(); }
        finally { inputs.close(); }
    }

}
