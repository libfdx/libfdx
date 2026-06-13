package io.github.libfdx.backend.cshared;

import org.teavm.vm.TeaVMOptimizationLevel;

/**
 * Lists the supported tea VM optimization values.
 *
 * @author xpenatan
 */
public enum TeaVMOptimization {
    NONE(TeaVMOptimizationLevel.SIMPLE),
    BALANCED(TeaVMOptimizationLevel.ADVANCED),
    AGGRESSIVE(TeaVMOptimizationLevel.FULL);

    private final TeaVMOptimizationLevel teaVMOptimizationLevel;

    TeaVMOptimization(TeaVMOptimizationLevel teaVMOptimizationLevel) {
        this.teaVMOptimizationLevel = teaVMOptimizationLevel;
    }

    TeaVMOptimizationLevel teaVMOptimizationLevel() {
        return teaVMOptimizationLevel;
    }
}
