package io.github.libfdx.collections;

/**
 * Provides read-only access to a live {@link Array}.
 *
 * <p>The view does not copy its source. Changes made through the owning array
 * are visible through the view, while the view itself exposes no mutation
 * operations.</p>
 *
 * @param <T> the value type
 * @author xpenatan
 */
public interface ArrayView<T> extends Iterable<T> {
    /**
     * Returns the value at an index.
     *
     * @param index the index
     * @return the value
     */
    T get(int index);

    /**
     * Returns the first value.
     *
     * @return the first value
     */
    T first();

    /**
     * Returns the last value.
     *
     * @return the last value
     */
    T peek();

    /**
     * Returns whether a value exists using equals comparison.
     *
     * @param value the value
     * @return true if present
     */
    boolean contains(T value);

    /**
     * Returns whether a value exists.
     *
     * @param value the value
     * @param identity whether to compare by identity
     * @return true if present
     */
    boolean contains(T value, boolean identity);

    /**
     * Returns the first index of a value using equals comparison.
     *
     * @param value the value
     * @return the index, or -1
     */
    int indexOf(T value);

    /**
     * Returns the first index of a value.
     *
     * @param value the value
     * @param identity whether to compare by identity
     * @return the index, or -1
     */
    int indexOf(T value, boolean identity);

    /**
     * Returns the last index of a value using equals comparison.
     *
     * @param value the value
     * @return the index, or -1
     */
    int lastIndexOf(T value);

    /**
     * Returns the last index of a value.
     *
     * @param value the value
     * @param identity whether to compare by identity
     * @return the index, or -1
     */
    int lastIndexOf(T value, boolean identity);

    /**
     * Returns the number of values.
     *
     * @return the number of values
     */
    int size();

    /**
     * Returns whether this view is empty.
     *
     * @return true if empty
     */
    boolean isEmpty();

    /**
     * Returns whether this view has at least one value.
     *
     * @return true if not empty
     */
    boolean notEmpty();

    /**
     * Returns the values as a copy.
     *
     * @return the values
     */
    Object[] toArray();

    /**
     * Copies the values into an array of the requested runtime type.
     *
     * @param destination the destination array
     * @param <A> the array component type
     * @return the destination array when large enough, or a new array
     */
    <A> A[] toArray(A[] destination);
}
