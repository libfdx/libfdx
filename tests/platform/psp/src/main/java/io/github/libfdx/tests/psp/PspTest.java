package io.github.libfdx.tests.psp;

/**
 * Defines the contract for psp test implementations.
 *
 * @author xpenatan
 */
interface PspTest {
    /**
     * Initializes this instance.
     */
    void create();

    /**
     * Renders the current content.
     */
    void render();
}
