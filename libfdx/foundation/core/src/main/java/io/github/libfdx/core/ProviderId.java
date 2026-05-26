package io.github.libfdx.core;

public final class ProviderId {
    private final String value;

    private ProviderId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new FdxException("ProviderId value cannot be empty");
        }
        this.value = value;
    }

    public static ProviderId of(String value) {
        return new ProviderId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProviderId)) {
            return false;
        }
        ProviderId that = (ProviderId) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
