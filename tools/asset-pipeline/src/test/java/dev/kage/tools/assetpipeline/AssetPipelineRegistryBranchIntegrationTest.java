package dev.kage.tools.assetpipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
 * Nhánh CÓ registry của {@link AssetPipelineRunner#run} — chạy TRỌN pipeline (pack atlas + quét
 * model + manifest + upsert {@code art_asset} + kiểm mồ côi) trên Postgres throwaway.
 *
 * <p><b>Vì sao tồn tại</b> (ROADMAP mục 61): nhánh này trước đây chưa từng có test nào chạy qua.
 * Nó chỉ được thi hành khi máy chạy tình cờ có biến {@code DB_PASSWORD}, và lúc đó
 * {@code AssetPipelineIntegrationTest} rơi vào nó với fixture thiếu sidecar
 * {@code <asset_key>.model-meta.json} ⇒ {@code ./gradlew build} ĐỎ chỉ vì shell. Sửa xong, nhánh
 * được chọn bằng THAM SỐ nên test khai rõ mình muốn nhánh nào — và nhánh registry cuối cùng cũng có
 * chỗ để được kiểm thật, thay vì được chạy do may rủi.
 *
 * <p>Đối xứng với {@link AssetPipelineIntegrationTest} ({@code Optional.empty()} — nhánh KHÔNG có
 * Postgres). Schema dựng bằng chính V19/V20 thật, xem {@link ArtRegistrySchema}.
 */
@Testcontainers
class AssetPipelineRegistryBranchIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_TYPE_JSON = 0x4E4F534A;
    private static final int GLB_VERSION = 2;

    /** 12 byte header GLB + 8 byte header chunk JSON. */
    private static final int GLB_HEADER_BYTES = 20;

    /** 900 chỉ số = 300 tam giác — thoải mái dưới trần của mọi tier (ART-PIPELINE.md §4.4). */
    private static final int INDEX_COUNT_WELL_UNDER_BUDGET = 900;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static ArtRegistryConfig dbConfig;

    @BeforeAll
    static void migrate() throws SQLException {
        dbConfig = new ArtRegistryConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ArtRegistrySchema.applyTo(dbConfig);
    }

    // Mỗi test cần art_asset TRỐNG: kiểm mồ côi đối chiếu ĐĨA với DB, dòng sót của test trước chỉ
    // ra cảnh báo "stale" chứ không fail — nhưng khi đó không còn phân biệt được dòng nào do lần
    // chạy NÀY ghi ra.
    @BeforeEach
    void resetRegistry() throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection(dbConfig.jdbcUrl(), dbConfig.user(), dbConfig.password());
                java.sql.Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM art_asset");
        }
    }

    @Test
    void shouldUpsertBothBranchesIntoArtAsset_whenRegistryConfigIsGiven(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeStaticIcon(renderRoot, "icon_registry-item");
        Path exportedRoot = tempRoot.resolve("exported");
        writeModel(exportedRoot, GraphicsTier.MEDIUM, "char_registry-hero", INDEX_COUNT_WELL_UNDER_BUDGET);

        AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.of(dbConfig));

        Map<String, String> kindByAssetKey = selectAssetKinds();
        assertThat(kindByAssetKey)
                .containsEntry("icon_registry-item", "SPRITE")
                .containsEntry("char_registry-hero", "MODEL")
                .hasSize(2);
        assertThat(selectTriCounts("char_registry-hero")).isEqualTo("{\"med\": 300}");
    }

    /**
     * {@code render_dir} của SPRITE phải tương đối gốc repo (ART-PIPELINE.md §8.4) bất kể
     * {@code --render-root} được truyền thế nào. Bản cũ ghi thẳng {@code asset.folder()} — chạy thật
     * bắt buộc dùng đường dẫn TUYỆT ĐỐI (cwd của Gradle {@code run} là thư mục subproject), nên DB
     * nhận {@code E:/study/.../render-out/<key>}: ổ đĩa + thư mục của một máy cụ thể, đúng thứ §8.4
     * cấm, trong khi nhánh MODEL cùng lớp đã ghi hằng số tương đối.
     */
    @Test
    void shouldStoreRepoRelativeSpriteRenderDir_whenRenderRootIsAbsolute(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out").toAbsolutePath();
        writeStaticIcon(renderRoot, "icon_registry-item");
        Path exportedRoot = tempRoot.resolve("exported");

        AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.of(dbConfig));

        assertThat(selectRenderDir("icon_registry-item")).isEqualTo("assets/raw/render-out/icon_registry-item");
    }

    private static String selectRenderDir(String assetKey) {
        try (Connection connection =
                        DriverManager.getConnection(dbConfig.jdbcUrl(), dbConfig.user(), dbConfig.password());
                PreparedStatement statement =
                        connection.prepareStatement("SELECT render_dir FROM art_asset WHERE asset_key = ?")) {
            statement.setString(1, assetKey);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next())
                        .as("phải có đúng 1 dòng art_asset cho " + assetKey)
                        .isTrue();
                return rows.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc lại được render_dir của " + assetKey, e);
        }
    }

    /**
     * Chạy hai lần trên CÙNG dữ liệu phải cho cùng kết quả — {@code ON CONFLICT (asset_key) DO
     * UPDATE} là điều kiện để dây chuyền art chạy được nhiều lần trong ngày (ART-PIPELINE.md §8.3).
     */
    @Test
    void shouldStayIdempotent_whenPipelineRunsTwiceOnSameAssets(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeStaticIcon(renderRoot, "icon_registry-item");
        Path exportedRoot = tempRoot.resolve("exported");
        writeModel(exportedRoot, GraphicsTier.MEDIUM, "char_registry-hero", INDEX_COUNT_WELL_UNDER_BUDGET);

        AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.of(dbConfig));
        AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.of(dbConfig));

        assertThat(selectAssetKinds()).hasSize(2);
        assertThat(selectTriCounts("char_registry-hero")).isEqualTo("{\"med\": 300}");
    }

    /**
     * Sidecar là BẮT BUỘC theo §8.6 và chính pipeline phải nói rõ khi thiếu — đây đúng là thông báo
     * mà {@code ./gradlew build} phun ra khi shell có {@code DB_PASSWORD} (ROADMAP mục 61). Giữ nó
     * làm test có chủ đích thay vì để nó nổ ngẫu nhiên theo môi trường của máy chạy.
     */
    @Test
    void shouldFailBuildWithClearMessage_whenModelHasNoSidecar(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeStaticIcon(renderRoot, "icon_registry-item");
        Path exportedRoot = tempRoot.resolve("exported");
        writeModel(exportedRoot, GraphicsTier.MEDIUM, "char_registry-hero", INDEX_COUNT_WELL_UNDER_BUDGET);
        Files.delete(exportedRoot.resolve("models").resolve("char_registry-hero.model-meta.json"));

        assertThatThrownBy(() -> AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.of(dbConfig)))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("char_registry-hero")
                .hasMessageContaining("thiếu sidecar");
    }

    /**
     * MỘT model hỏng không được chặn những model còn lại — phát hiện thật 2026-09-13: trên máy dev,
     * {@code char_hoa-b1} có sidecar trỏ ra ngoài {@code assets/raw/} VÀ đứng ĐẦU bảng chữ cái, nên bản
     * fail-fast cũ bỏ luôn 25 model hợp lệ phía sau — đo được **23/26 model mồ côi** trong {@code art_asset}.
     *
     * <p>Ô này gác CẢ HAI nửa, vì chỉ một nửa thì vẫn hỏng theo hướng ngược lại: model lành VẪN vào
     * {@code art_asset}, VÀ pipeline VẪN fail build (§8.5) với thông báo nêu đích danh model hỏng. Tên
     * {@code aaa}/{@code zzz} là có chủ đích: model hỏng phải được duyệt TRƯỚC model lành, nếu không ô test
     * sẽ xanh giả ngay cả với bản fail-fast cũ.
     */
    @Test
    void shouldStillUpsertHealthyModels_whenAnEarlierModelHasBrokenSidecar(@TempDir Path tempRoot) throws IOException {
        Path renderRoot = tempRoot.resolve("render-out");
        writeStaticIcon(renderRoot, "icon_registry-item");
        Path exportedRoot = tempRoot.resolve("exported");
        writeModel(exportedRoot, GraphicsTier.MEDIUM, "char_aaa-broken", INDEX_COUNT_WELL_UNDER_BUDGET);
        writeModel(exportedRoot, GraphicsTier.MEDIUM, "char_zzz-healthy", INDEX_COUNT_WELL_UNDER_BUDGET);
        Files.delete(exportedRoot.resolve("models").resolve("char_aaa-broken.model-meta.json"));

        assertThatThrownBy(() -> AssetPipelineRunner.run(renderRoot, exportedRoot, Optional.of(dbConfig)))
                .isInstanceOf(AssetPipelineException.class)
                .hasMessageContaining("char_aaa-broken");

        assertThat(selectAssetKinds()).containsEntry("char_zzz-healthy", "MODEL");
    }

    private static Map<String, String> selectAssetKinds() {
        Map<String, String> kinds = new LinkedHashMap<>();
        try (Connection connection =
                        DriverManager.getConnection(dbConfig.jdbcUrl(), dbConfig.user(), dbConfig.password());
                java.sql.Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT asset_key, asset_kind FROM art_asset")) {
            while (rows.next()) {
                kinds.put(rows.getString(1), rows.getString(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc lại được art_asset sau khi chạy pipeline", e);
        }
        return kinds;
    }

    /** {@code tri_counts} là {@code jsonb} — Postgres tự chuẩn hoá khoảng trắng khi đọc ra text. */
    private static String selectTriCounts(String assetKey) {
        try (Connection connection =
                        DriverManager.getConnection(dbConfig.jdbcUrl(), dbConfig.user(), dbConfig.password());
                PreparedStatement statement =
                        connection.prepareStatement("SELECT tri_counts::text FROM art_asset WHERE asset_key = ?")) {
            statement.setString(1, assetKey);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next())
                        .as("phải có đúng 1 dòng art_asset cho " + assetKey)
                        .isTrue();
                return rows.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc lại được tri_counts của " + assetKey, e);
        }
    }

    /** Y hệt fixture của {@link AssetPipelineIntegrationTest}: file {@code .glb} tối thiểu + sidecar. */
    private static void writeModel(Path exportedRoot, GraphicsTier tier, String assetKey, int indexCount)
            throws IOException {
        Path modelsRoot = exportedRoot.resolve("models");
        Path tierDir = modelsRoot.resolve(tier.folderName);
        Files.createDirectories(tierDir);
        Files.writeString(
                modelsRoot.resolve(assetKey + ".model-meta.json"),
                "{ \"blend_file\": \"assets/raw/blender/" + assetKey + ".blend\" }");
        byte[] json = ("{ \"accessors\": [ { \"count\": " + indexCount + " } ], \"meshes\": "
                        + "[ { \"primitives\": [ { \"attributes\": { \"POSITION\": 0 }, \"indices\": 0 } ] } ] }")
                .getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(GLB_HEADER_BYTES + json.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(GLB_MAGIC);
        buffer.putInt(GLB_VERSION);
        buffer.putInt(buffer.capacity());
        buffer.putInt(json.length);
        buffer.putInt(CHUNK_TYPE_JSON);
        buffer.put(json);
        Files.write(tierDir.resolve(assetKey + ".glb"), buffer.array());
    }

    private static void writeStaticIcon(Path renderRoot, String assetKey) throws IOException {
        Path folder = renderRoot.resolve(assetKey);
        Files.createDirectories(folder);
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", folder.resolve("icon.png").toFile());
    }
}
