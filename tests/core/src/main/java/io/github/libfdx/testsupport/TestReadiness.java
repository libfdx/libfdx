package io.github.libfdx.testsupport;

/** Lets automatic runs wait for a scene's resources and scripted checks before observing it. */
public interface TestReadiness {
    boolean readyForAutomaticCompletion();
}
