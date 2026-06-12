package io.github.libfdx.core;

/**
 * Represents a provider id.
 *
 * @author xpenatan
 */
public final class ProviderId {
    private final String value;

    private ProviderId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new FdxException("ProviderId value cannot be empty");
        }
        this.value = value;
    }

    /**
     * Creates a provider ID from the supplied values.
     *
     * @param value the value
     * @return a new provider ID
     */
    public static ProviderId of(String value) {
        return new ProviderId(value);
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public String value() {
        return value;
    }

    /**
     * Compares this instance with another object for equality.
     *
     * @param other the other
     * @return true if equals succeeds or is active; false otherwise
     */
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

    /**
     * Returns the hash code for this instance.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Returns a readable string representation of this instance.
     *
     * @return the to string
     */
    @Override
    public String toString() {
        return value;
    }
}
