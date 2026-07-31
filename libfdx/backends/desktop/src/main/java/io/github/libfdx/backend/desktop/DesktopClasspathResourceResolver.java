package io.github.libfdx.backend.desktop;

import io.github.libfdx.files.ClasspathResourceResolver;
import java.io.InputStream;

/** JVM classpath lookup kept inside the JVM desktop backend. */
final class DesktopClasspathResourceResolver
        implements ClasspathResourceResolver {
    @Override
    public InputStream open(String path) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        InputStream resource = open(context, path);
        ClassLoader defining = DesktopClasspathResourceResolver.class
                .getClassLoader();
        if(resource == null && defining != context) {
            resource = open(defining, path);
        }
        ClassLoader system = ClassLoader.getSystemClassLoader();
        if(resource == null && system != context && system != defining) {
            resource = open(system, path);
        }
        if(resource == null && system == null) {
            resource = ClassLoader.getSystemResourceAsStream(path);
        }
        return resource;
    }

    private static InputStream open(ClassLoader loader, String path) {
        return loader != null ? loader.getResourceAsStream(path) : null;
    }
}
