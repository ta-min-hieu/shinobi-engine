package dev.kage.tools.assetpipeline;

import java.util.Optional;

/**
 * Cấu hình kết nối Postgres cho bước upsert {@code art_asset} (ART-PIPELINE.md §8.3) — đọc từ biến
 * môi trường, không hardcode secret (Điều 42, cùng quy ước {@code DatabaseConfig} ở server-database).
 *
 * <p>Khác {@code DatabaseConfig}: bước upsert là **tuỳ chọn** (chạy pipeline để duyệt atlas cục bộ
 * không nhất thiết cần Postgres đang chạy) — thiếu {@code DB_PASSWORD} nghĩa là "bỏ qua bước này",
 * không phải lỗi.
 *
 * <p><b>Chỉ {@link AssetPipelineRunner#main} được đọc môi trường.</b> Bản thân bước chạy pipeline
 * ({@link AssetPipelineRunner#run}) nhận cấu hình này như một THAM SỐ — xem javadoc ở đó cho lỗi
 * thật mà ranh giới này sinh ra để chặn.
 */
record ArtRegistryConfig(String host, int port, String database, String user, String password) {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_DATABASE = "kage";
    private static final String DEFAULT_USER = "kage";

    /**
     * Đọc cấu hình từ biến môi trường của tiến trình.
     *
     * @return rỗng khi {@code DB_PASSWORD} không có — nghĩa là "bỏ qua upsert", không phải lỗi
     */
    static Optional<ArtRegistryConfig> fromEnvironment() {
        String passwordValue = System.getenv("DB_PASSWORD");
        if (passwordValue == null || passwordValue.isBlank()) {
            return Optional.empty();
        }
        String portValue = System.getenv("DB_PORT");
        String hostValue = System.getenv("DB_HOST");
        String databaseValue = System.getenv("DB_NAME");
        String userValue = System.getenv("DB_USER");
        return Optional.of(new ArtRegistryConfig(
                hostValue != null ? hostValue : DEFAULT_HOST,
                portValue != null ? Integer.parseInt(portValue) : DEFAULT_PORT,
                databaseValue != null ? databaseValue : DEFAULT_DATABASE,
                userValue != null ? userValue : DEFAULT_USER,
                passwordValue));
    }

    String jdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(host, port, database);
    }
}
