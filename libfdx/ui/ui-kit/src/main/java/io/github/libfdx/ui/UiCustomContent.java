package io.github.libfdx.ui;

/**
 * Defines the contract for ui custom content implementations.
 *
 * @author xpenatan
 */
public interface UiCustomContent {
    /**
     * Runs the build step.
     *
     * @param context the context
     */
    void build(UiCustomContext context);
}
