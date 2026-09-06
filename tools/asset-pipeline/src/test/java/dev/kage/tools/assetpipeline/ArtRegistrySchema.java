package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.TimeZone;

/**
 * Dựng schema {@code art_category}/{@code art_asset} trên một Postgres throwaway bằng CHÍNH hai file
 * migration thật ({@code V19__create_art_registry.sql}, {@code V20__extend_art_asset_for_3d.sql})
 * đọc thẳng từ đĩa — không project-depend {@code server:server-database} chỉ để chạy Flyway (hai
 * migration này không phụ thuộc bảng nào khác, xem build.gradle.kts của module).
 *
 * <p>Dùng chung cho hai test cần schema đó: {@link OrphanAssetCheckerIntegrationTest} (kiểm mồ côi)
 * và {@link AssetPipelineRegistryBranchIntegrationTest} (nhánh registry của cả pipeline).
 */
final class ArtRegistrySchema {

    private ArtRegistrySchema() {}

    /**
     * Áp V19 rồi V20 lên database mà {@code config} trỏ tới.
     *
     * @throws SQLException nếu không nối được hoặc migration lỗi — test phải đỏ chứ không chạy tiếp
     *     trên schema nửa vời
     */
    static void applyTo(ArtRegistryConfig config) throws SQLException {
        // Phát hiện thật đã ghi ở ArtRegistryUpserter/OrphanAssetChecker: pgjdbc gửi
        // TimeZone.getDefault() của JVM cho Postgres NGAY LÚC MỞ CONNECTION, nên máy dev múi giờ
        // "Asia/Saigon" bị FATAL kể cả với connection chỉ dùng để chạy migration. Ép UTC TRƯỚC khi
        // mở connection đầu tiên.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Path migrationDir = Path.of(System.getProperty("kage.repoRoot"))
                .resolve("server/server-database/src/main/resources/db/migration");
        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password())) {
            applyMigration(connection, migrationDir.resolve("V19__create_art_registry.sql"));
            applyMigration(connection, migrationDir.resolve("V20__extend_art_asset_for_3d.sql"));
        }
    }

    private static void applyMigration(Connection connection, Path migrationFile) throws SQLException {
        String sql;
        try {
            sql = Files.readString(migrationFile);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Không đọc được migration thật (test cần chạy từ repo có "
                            + "server/server-database, đường dẫn thử: " + migrationFile + ")",
                    e);
        }
        try (Statement statement = connection.createStatement()) {
            // pgjdbc gửi cả chuỗi nhiều lệnh ngăn bằng ";" qua simple query protocol khi dùng
            // Statement (không phải PreparedStatement) — đủ để chạy nguyên văn 1 file migration
            // không cần Flyway, và các INSERT phụ thuộc lẫn nhau bằng subquery (art_category cha/con)
            // vẫn thấy đúng thứ tự vì Postgres chạy tuần tự trong cùng một message.
            statement.execute(sql);
        }
    }
}
