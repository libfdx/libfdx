package io.github.libfdx.collections;

/**
 * Defines how an {@link ObjectMap} compares and hashes keys.
 *
 * @author xpenatan
 */
public enum KeyComparison {
    /** Uses {@link Object#equals(Object)} and {@link Object#hashCode()}. */
    EQUALITY,

    /** Uses reference equality and {@link System#identityHashCode(Object)}. */
    IDENTITY
}
