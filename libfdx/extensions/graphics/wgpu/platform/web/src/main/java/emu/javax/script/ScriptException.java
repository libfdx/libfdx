package emu.javax.script;

/**
 * Signals script failures.
 *
 * @author xpenatan
 */
public class ScriptException extends Exception {
    /**
     * Creates a script exception.
     *
     * @param message the message
     */
    public ScriptException(String message) {
        super(message);
    }
}
