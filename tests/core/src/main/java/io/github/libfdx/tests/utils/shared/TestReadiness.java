package io.github.libfdx.tests.utils.shared;

/** Lets automatic runs wait for a scene's resources and scripted checks before observing it. */
public interface TestReadiness {
    boolean readyForAutomaticCompletion();
}
