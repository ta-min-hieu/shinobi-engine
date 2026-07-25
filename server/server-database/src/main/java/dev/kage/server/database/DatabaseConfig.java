package dev.kage.server.database;

/**
 * Cấu hình kết nối PostgreSQL — đọc từ biến môi trường, không hardcode secret (Điều 42,
 * SECURITY.md §13). Tên biến khớp {@code deploy/docker/docker-compose.yml}.
 */
public record DatabaseConfig(String host, int port, String database, String user, String password) {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_DATABASE = "kage";
    private static final String DEFAULT_USER = "kage";

    public static DatabaseConfig fromEnvironment() {
        String portValue = System.getenv("DB_PORT");
        String passwordValue = System.getenv("DB_PASSWORD");
        if (passwordValue == null || passwordValue.isBlank()) {
            throw new IllegalStateException("Thiếu biến môi trường bắt buộc: DB_PASSWORD");
        }
        return new DatabaseConfig(
                System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : DEFAULT_HOST,
                portValue != null ? Integer.parseInt(portValue) : DEFAULT_PORT,
                System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : DEFAULT_DATABASE,
                System.getenv("DB_USER") != null ? System.getenv("DB_USER") : DEFAULT_USER,
                passwordValue);
    }

    String jdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(host, port, database);
    }
}
