package io.github.libfdx.core;

public final class SystemLogger implements Logger {
    @Override
    public void debug(String message) {
        System.out.println("[debug] " + message);
    }

    @Override
    public void info(String message) {
        System.out.println("[info] " + message);
    }

    @Override
    public void warn(String message) {
        System.out.println("[warn] " + message);
    }

    @Override
    public void error(String message) {
        System.err.println("[error] " + message);
    }

    @Override
    public void error(String message, Throwable error) {
        error(message);
        if (error != null) {
            error.printStackTrace(System.err);
        }
    }
}
