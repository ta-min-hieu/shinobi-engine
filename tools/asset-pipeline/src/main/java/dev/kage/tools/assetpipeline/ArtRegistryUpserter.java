package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Upsert {@code art_asset} (ART-PIPELINE.md §8.3: "tools/asset-pipeline (bước cuối) upsert
 * art_asset từ ASSET-LOG + manifest — pipeline là nguồn sự thật kỹ thuật"). Chạy SAU khi pack thành
 * công (atlas_name luôn là {@code "main"} — chưa có khái niệm zone thật trong repo, xem
 * {@link AtlasPacker}), nên status luôn {@code EXPORTED} khi upsert: asset đã thật sự nằm trong
 * atlas vừa xuất, không phải mới vẽ xong chờ duyệt.
 *
 * <p>V19 migration (art_category + art_asset) là điều kiện tiên quyết — chưa chạy migration thì
 * bước này lỗi rõ ràng (fail build), không âm thầm bỏ qua.
 */
final class ArtRegistryUpserter {

    private static final String UPSERT_SQL = """
            INSERT INTO art_asset
                (asset_key, category_id, element, status, blend_path, render_dir, atlas_name,
                 actions, master_px, license, checksum, updated_at)
            VALUES
                (?, (SELECT id FROM art_category WHERE code = ?), ?, 'EXPORTED', ?, ?, 'main',
                 ?::jsonb, ?, 'ORIGINAL', ?, now())
            ON CONFLICT (asset_key) DO UPDATE SET
                category_id = EXCLUDED.category_id,
                element = EXCLUDED.element,
                status = EXCLUDED.status,
                blend_path = EXCLUDED.blend_path,
                render_dir = EXCLUDED.render_dir,
                atlas_name = EXCLUDED.atlas_name,
                actions = EXCLUDED.actions,
                master_px = EXCLUDED.master_px,
                checksum = EXCLUDED.checksum,
                updated_at = now()
            """;

    static {
        // Cùng phát hiện thật đã ghi ở PostgresDataSourceFactory (server-database): pgjdbc lấy
        // TimeZone.getDefault() của JVM gửi cho Postgres lúc connect — không có connection property
        // nào override được (đã verify ở đó, không thử lại URL "options=-c TimeZone=..." ở đây).
        // Máy dev múi giờ "Asia/Saigon" (alias tzdata cũ) khiến postgres:17 FATAL ngay lúc mở
        // connection. Ép JVM chạy UTC nội bộ, đúng gốc, không phụ thuộc múi giờ máy host.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private ArtRegistryUpserter() {}

    static void upsertAll(List<AssetFolder> assets, ArtRegistryConfig config) {
        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password())) {
            for (AssetFolder asset : assets) {
                upsertOne(connection, asset);
            }
        } catch (SQLException e) {
            throw new AssetPipelineException(
                    "Lỗi upsert art_asset (đã chạy V19__create_art_registry.sql chưa?): " + e.getMessage());
        }
        System.out.println("[asset-pipeline] upsert art_asset: " + assets.size() + " dòng");
    }

    private static void upsertOne(Connection connection, AssetFolder asset) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, asset.assetKey());
            statement.setString(2, AssetCategoryResolver.leafCategoryCode(asset.assetKey()));
            Optional<String> element = AssetCategoryResolver.element(asset.assetKey());
            if (element.isPresent()) {
                statement.setString(3, element.get());
            } else {
                statement.setNull(3, Types.VARCHAR);
            }
            statement.setString(4, BlendPathResolver.resolve(asset.assetKey()));
            statement.setString(5, asset.folder().toString().replace('\\', '/'));
            if (asset.isStaticIcon()) {
                statement.setNull(6, Types.OTHER);
                statement.setInt(7, readIconMasterPx(asset));
            } else {
                statement.setString(6, actionsToJson(asset.renderMeta().actions()));
                statement.setInt(7, asset.renderMeta().masterPx());
            }
            statement.setString(8, checksumOfFolder(asset.folder()));
            statement.executeUpdate();
        }
    }

    private static int readIconMasterPx(AssetFolder asset) {
        try {
            return javax.imageio.ImageIO.read(asset.folder().resolve("icon.png").toFile())
                    .getWidth();
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được kích thước icon: " + asset.assetKey(), e);
        }
    }

    private static String actionsToJson(Map<String, Integer> actions) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : actions.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        return json.append('}').toString();
    }

    // Checksum đại diện cho toàn bộ nội dung hình ảnh của asset (mọi frame PNG, thứ tự cố định) —
    // dùng để trang quản trị (tương lai) biết asset có đổi từ lần upsert trước hay chưa. Không phải
    // 1 file "master" duy nhất vì asset nhiều frame không có khái niệm đó.
    private static String checksumOfFolder(Path folder) {
        List<Path> pngFiles = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder, "*.png")) {
            entries.forEach(pngFiles::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được thư mục asset: " + folder, e);
        }
        pngFiles.sort(Comparator.comparing(Path::toString));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path png : pngFiles) {
                digest.update(Files.readAllBytes(png));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 luôn có sẵn trong JDK chuẩn", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file để hash: " + folder, e);
        }
    }
}
