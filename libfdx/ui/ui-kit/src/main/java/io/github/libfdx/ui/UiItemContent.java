package io.github.libfdx.ui;

public interface UiItemContent<T> {
    void build(UiScope ui, T item);
}
