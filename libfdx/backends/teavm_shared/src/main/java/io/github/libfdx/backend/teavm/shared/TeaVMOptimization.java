package io.github.libfdx.backend.teavm.shared;

import org.teavm.vm.TeaVMOptimizationLevel;

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
