package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test {@link OrphanAssetChecker} bằng PostgreSQL THẬT qua Testcontainers (không mock JDBC —
 * TESTING.md §2), migrate bằng chính 2 file {@code V19__create_art_registry.sql} /
 * {@code V20__extend_art_asset_for_3d.sql} đọc thẳng từ đĩa (không project-depend
 * {@code server:server-database} chỉ để chạy Flyway — hai migration này không phụ thuộc bất kỳ bảng
 * nào khác nên chạy trực tiếp 2 file .sql là đủ, xem build.gradle.kts).
 *
 * <p>Không gọi {@link AssetPipelineRunner#main} — cấu hình DB của pipeline chỉ đọc từ biến môi
 * trường thật ({@link ArtRegistryConfig#fromEnvironmentOrNull()}), không override được từ trong JVM
 * test. Gọi thẳng {@link ArtRegistryUpserter} (để seed dòng DB) rồi {@link OrphanAssetChecker#check}
 * — đúng ranh giới của lớp đang test, cùng phong cách {@code ArtRegistryUpserter} package-private
 * dùng trực tiếp trong test đã có ở module này.
 */
@Testcontainers
class OrphanAssetCheckerIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static ArtRegistryConfig dbConfig;

    @BeforeAll
    static void migrate() throws SQLException {
        // Cùng phát hiện thật ghi ở ArtRegistryUpserter/OrphanAssetChecker: phải ép UTC TRƯỚC khi mở
        // connection đầu tiên, không chỉ trước khi upsert — máy dev múi giờ khác UTC khiến Postgres
        // FATAL ngay lúc connect, kể cả cho connection chỉ dùng để chạy migration.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        dbConfig = new ArtRegistryConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Path repoRoot = Path.of(System.getProperty("kage.repoRoot"));
        Path migrationDir = repoRoot.resolve("server/server-database/src/main/resources/db/migration");
        try (Connection connection =
                DriverManager.getConnection(dbConfig.jdbcUrl(), dbConfig.user(), dbConfig.password())) {
            applyMigration(connection, migrationDir.resolve("V19__create_art_registry.sql"));
            applyMigration(connection, migrationDir.resolve("V20__extend_art_asset_for_3d.sql"));
        }
    }

    // Container/schema dùng CHUNG cho cả class (dựng Postgres throwaway tốn giây, không phải mili
    // giây) nhưng mỗi test cần một art_asset TRỐNG để so khớp đĩa/DB chính xác — dọn bảng trước MỖI
    // test thay vì trông vào thứ tự chạy (JUnit5 mặc định KHÔNG đảm bảo theo thứ tự khai báo).
    @BeforeEach
    void resetRegistry() throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection(dbConfig.jdbcUrl(), dbConfig.user(), dbConfig.password());
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM art_asset");
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

    @Test
    void shouldPassSilently_whenDiskAndDatabaseAssetsMatchExactly(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        AssetFolder spriteAsset = writeIconAsset(renderRoot, "icon_match-sprite");
        Path modelsRoot = tempRoot.resolve("models");
        ScannedModel modelAsset = writeModelAsset(modelsRoot, "char_match-model", GraphicsTier.MEDIUM, 900);

        ArtRegistryUpserter.upsertSprites(List.of(spriteAsset), dbConfig);
        ArtRegistryUpserter.upsertModels(List.of(modelAsset), modelsRoot, renderRoot, dbConfig);

        String output =
                captureStdout(() -> OrphanAssetChecker.check(List.of(spriteAsset), List.of(modelAsset), dbConfig));

        assertThat(output).doesNotContain("CẢNH BÁO").contains("kiểm mồ côi: OK");
    }

    @Test
    void shouldFailBuild_whenDiskHasSpriteAssetNotRegisteredInDatabase(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        AssetFolder orphanSprite = writeIconAsset(renderRoot, "icon_orphan-sprite");
        // KHÔNG upsert orphanSprite — mô phỏng đúng lỗi mà §8.5/§8.6 mô tả: file có thật trên đĩa
        // nhưng chưa có dòng art_asset (asset_kind='SPRITE') nào trỏ tới.

        assertThatThrownBy(() -> OrphanAssetChecker.check(List.of(orphanSprite), List.of(), dbConfig))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("SPRITE")
                .hasMessageContaining("icon_orphan-sprite")
                .hasMessageContaining("assets/exported/atlases/");
    }

    @Test
    void shouldFailBuild_whenDiskHasModelAssetNotRegisteredInDatabase(@TempDir Path tempRoot) throws IOException {
        Path modelsRoot = tempRoot.resolve("models");
        ScannedModel orphanModel = writeModelAsset(modelsRoot, "char_orphan-model", GraphicsTier.LOW, 4_000);
        // KHÔNG upsert orphanModel — file .glb có thật trong assets/exported/models/ nhưng chưa có
        // dòng art_asset (asset_kind='MODEL') nào trỏ tới.

        assertThatThrownBy(() -> OrphanAssetChecker.check(List.of(), List.of(orphanModel), dbConfig))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("MODEL")
                .hasMessageContaining("char_orphan-model")
                .hasMessageContaining("assets/exported/models/");
    }

    @Test
    void shouldWarnButNotFailBuild_whenDatabaseHasRowWithoutMatchingDiskFile(@TempDir Path tempRoot)
            throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        AssetFolder staleSprite = writeIconAsset(renderRoot, "icon_stale-sprite");
        ArtRegistryUpserter.upsertSprites(List.of(staleSprite), dbConfig);

        // Lần quét đĩa NÀY không còn thấy icon_stale-sprite nữa (coi như đã xoá khỏi đĩa), nhưng dòng
        // DB của lần upsert trước vẫn còn — đây là chiều 2 (stale), chỉ cảnh báo không fail build.
        String output = captureStdout(() -> OrphanAssetChecker.check(List.of(), List.of(), dbConfig));

        assertThat(output).contains("CẢNH BÁO").contains("SPRITE").contains("icon_stale-sprite");
    }

    private static String captureStdout(Runnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    /** Icon tĩnh tối thiểu để {@code ArtRegistryUpserter.upsertOne} chạy được (đọc {@code icon.png}). */
    private static AssetFolder writeIconAsset(Path renderRoot, String assetKey) throws IOException {
        Path folder = renderRoot.resolve(assetKey);
        Files.createDirectories(folder);
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", folder.resolve("icon.png").toFile());
        return new AssetFolder(assetKey, folder, null);
    }

    /** Một tier model tối thiểu kèm sidecar {@code <asset_key>.model-meta.json} bắt buộc của §8.6. */
    private static ScannedModel writeModelAsset(Path modelsRoot, String assetKey, GraphicsTier tier, int triangleCount)
            throws IOException {
        Path tierDir = modelsRoot.resolve(tier.folderName);
        Files.createDirectories(tierDir);
        Path glbFile = tierDir.resolve(assetKey + ".glb");
        Files.write(glbFile, new byte[] {0x67, 0x6C, 0x54, 0x46});
        Files.writeString(
                modelsRoot.resolve(assetKey + ".model-meta.json"),
                "{ \"blend_file\": \"assets/raw/blender/" + assetKey + ".blend\" }");
        return new ScannedModel(assetKey, tier, glbFile, triangleCount);
    }
}
