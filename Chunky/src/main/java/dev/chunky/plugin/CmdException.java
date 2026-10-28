package dev.chunky.plugin;

/** Fehler mit benutzerfreundlicher Nachricht. */
final class CmdException extends Exception {
    private static final long serialVersionUID = 1L;

    CmdException(String message) {
        super(message);
    }
}
