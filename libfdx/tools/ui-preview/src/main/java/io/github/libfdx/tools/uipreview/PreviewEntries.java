package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Reads preview entry declarations from project bytecode without loading classes. */
final class PreviewEntries {
    static final String CATALOG_PROPERTY = "libfdx.uiPreview.entryCatalog";
    static final String CLASS_PROPERTY = "libfdx.uiPreview.entryClass";
    static final String REQUEST_FILE = "entry-request";
    private static final String ENTRY = "io/github/libfdx/tools/uipreview/UiPreviewEntry";
    private static final String MAIN_TYPE = "([Ljava/lang/String;)V";

    private record Type(String parent, List<String> interfaces, boolean concreteEntry, boolean declaresMain) { }

    static List<String> discover(List<String> classpath, Path snapshot) throws IOException {
        ClassFile parser = ClassFile.of();
        Map<String, Type> types = new HashMap<>();
        for (String entry : classpath) {
            Path path = Path.of(entry);
            if (!path.startsWith(snapshot)) continue;
            if (Files.isDirectory(path)) {
                try (var files = Files.walk(path)) {
                    for (Path file : files.filter(file -> file.toString().endsWith(".class")).toList()) {
                        add(types, parser.parse(file));
                    }
                }
            } else {
                try (var jar = new JarFile(path.toFile(), false, JarFile.OPEN_READ, Runtime.version());
                     var files = jar.versionedStream()) {
                    for (var file : files.filter(file -> file.getName().endsWith(".class")).toList()) {
                        try (var input = jar.getInputStream(file)) {
                            add(types, parser.parse(input.readAllBytes()));
                        }
                    }
                }
            }
        }
        return types.entrySet().stream()
                .filter(entry -> entry.getValue().concreteEntry())
                .map(Map.Entry::getKey)
                .filter(name -> inheritsEntry(types, name) && !overridesMain(types, name))
                .map(name -> name.replace('/', '.'))
                .sorted()
                .toList();
    }

    private static void add(Map<String, Type> types, ClassModel model) {
        int flags = model.flags().flagsMask();
        boolean concrete = (flags & (ClassFile.ACC_ABSTRACT | ClassFile.ACC_INTERFACE | ClassFile.ACC_SYNTHETIC)) == 0;
        boolean noArg = false;
        boolean main = false;
        for (var method : model.methods()) {
            if ((method.flags().flagsMask() & ClassFile.ACC_PRIVATE) != 0) continue;
            if (method.methodName().equalsString("<init>") && method.methodType().equalsString("()V")) noArg = true;
            if (method.methodName().equalsString("main") && method.methodType().equalsString(MAIN_TYPE)) main = true;
        }
        types.putIfAbsent(model.thisClass().asInternalName(), new Type(
                model.superclass().map(parent -> parent.asInternalName()).orElse(""),
                model.interfaces().stream().map(type -> type.asInternalName()).toList(), concrete && noArg, main));
    }

    private static boolean inheritsEntry(Map<String, Type> types, String name) {
        if (ENTRY.equals(name)) return true;
        Type type = types.get(name);
        return type != null && (inheritsEntry(types, type.parent())
                || type.interfaces().stream().anyMatch(parent -> inheritsEntry(types, parent)));
    }

    private static boolean overridesMain(Map<String, Type> types, String name) {
        if (ENTRY.equals(name)) return false;
        Type type = types.get(name);
        return type != null && (type.declaresMain() || overridesMain(types, type.parent())
                || type.interfaces().stream().anyMatch(parent -> overridesMain(types, parent)));
    }

}
