package dev.kage.engine.scripting;

/** Script lỗi cú pháp, vượt giới hạn tài nguyên, hoặc cố thoát sandbox (SCRIPTING.md §4). */
public final class ScriptExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
