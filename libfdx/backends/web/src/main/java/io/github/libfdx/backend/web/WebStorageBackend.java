package io.github.libfdx.backend.web;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.storage.StorageBackend;
import io.github.libfdx.storage.StorageScope;
import org.teavm.jso.JSBody;

import java.util.Base64;

/**
 * Persists web storage stores in browser localStorage.
 *
 * @author xpenatan
 */
public final class WebStorageBackend implements StorageBackend {
    private static final ProviderId ID = ProviderId.of("web-storage");
    private static final String PREFIX = "libfdx.storage.";

    @Override
    public byte[] read(StorageScope scope, String path) {
        String value = readString(key(scope, path));
        return value != null && value.length() > 0 ? Base64.getDecoder().decode(value) : null;
    }

    @Override
    public void write(StorageScope scope, String path, byte[] bytes) {
        writeString(key(scope, path), Base64.getEncoder().encodeToString(bytes != null ? bytes : new byte[0]));
    }

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    private String key(StorageScope scope, String path) {
        String scopeName = scope == StorageScope.CACHE ? "cache" : "local";
        return PREFIX + scopeName + "." + path;
    }

    @JSBody(params = { "key" }, script =
            "try {\n" +
            "  return window.localStorage ? (window.localStorage.getItem(key) || '') : '';\n" +
            "} catch (e) {\n" +
            "  return '';\n" +
            "}")
    private static native String readString(String key);

    @JSBody(params = { "key", "value" }, script =
            "try {\n" +
            "  if (window.localStorage) window.localStorage.setItem(key, value || '');\n" +
            "} catch (e) {\n" +
            "}")
    private static native void writeString(String key, String value);
}
