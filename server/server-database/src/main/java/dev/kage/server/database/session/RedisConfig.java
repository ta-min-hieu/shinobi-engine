package dev.kage.server.database.session;

/**
 * Cấu hình kết nối Redis — đọc từ biến môi trường (Điều 42). Tên biến khớp
 * {@code deploy/docker/docker-compose.yml}.
 */
public record RedisConfig(String host, int port, String password) {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 6379;

    public static RedisConfig fromEnvironment() {
        var portValue = System.getenv("CACHE_PORT");
        var passwordValue = System.getenv("CACHE_PASSWORD");
        if (passwordValue == null || passwordValue.isBlank()) {
            throw new IllegalStateException("Thiếu biến môi trường bắt buộc: CACHE_PASSWORD");
        }
        return new RedisConfig(
                System.getenv("CACHE_HOST") != null ? System.getenv("CACHE_HOST") : DEFAULT_HOST,
                portValue != null ? Integer.parseInt(portValue) : DEFAULT_PORT,
                passwordValue);
    }
}
