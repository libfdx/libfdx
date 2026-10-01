package io.github.libfdx.backend.cshared;

/** Compile-time data-flow anchor for monitor operands in the C compiler. */
public final class CMonitorSupport {
    private CMonitorSupport() {}

    public static void keep(Object monitor) {
        // Empty by design. Dependency analysis must see the operand before optimization.
    }
}
