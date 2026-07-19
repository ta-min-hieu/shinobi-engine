package dev.kage.server.database;

/** Bọc {@link java.sql.SQLException} thành unchecked — adapter caller không cần khai báo throws. */
public final class DatabaseAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatabaseAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
