package dev.kage.tools.assetpipeline;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Kiểm mồ côi (ART-PIPELINE.md §8.5 "không có asset 'mồ côi' chỉ tồn tại trên đĩa" + §8.6 "Việc kèm
 * theo còn hở"). Chạy SAU {@link ArtRegistryUpserter} (khi có kết nối DB), đối chiếu ngược từ file
 * trên đĩa về dòng {@code art_asset} — điều mà bước upsert (chỉ ghi, không đọc lại) không tự làm.
 *
 * <p><b>Chiều 1 — fail build (đúng câu chữ §8.5 "pipeline fail build nếu thiếu"):</b> file trên đĩa
 * không có dòng DB tương ứng. Soi CẢ HAI nhánh theo đúng §8.6:
 *
 * <ul>
 *   <li><b>SPRITE</b> — so với {@code assets/exported/atlases/}. Không tự phân tích lại từng file
 *       {@code .atlas} (text libGDX, §8.7) để suy ngược {@code asset_key}: một atlas gộp NHIỀU
 *       {@code asset_key} vào một file, và tên vùng bên trong còn thêm hậu tố action (VD
 *       {@code char_ninja-fire_idle}) nên không thể phục hồi {@code asset_key} gốc mà không lặp lại
 *       đúng quy ước đặt tên vùng của {@link AtlasStager}. Dùng thẳng danh sách {@code assets} đã
 *       quét (tham số của phương thức này) làm đại diện: đây CHÍNH XÁC là tập nguồn đã được đóng gói
 *       thành công vào atlas ở bước ngay trước đó trong cùng lần chạy ({@code packTier} đã fail build
 *       rồi nếu đóng gói lỗi) — 1-1 với nội dung {@code assets/exported/atlases/}.
 *   <li><b>MODEL</b> — so trực tiếp với {@code assets/exported/models/}: mỗi {@link ScannedModel} là
 *       một file {@code .glb} thật đã quét được ở đó, không cần suy gián tiếp như nhánh SPRITE.
 * </ul>
 *
 * <p><b>Chiều 2 — chỉ cảnh báo, KHÔNG fail build:</b> dòng DB không còn file trên đĩa tương ứng
 * (asset đã xoá khỏi đĩa nhưng dòng {@code art_asset} còn sót lại). §8.5/§8.6 không nêu tường minh
 * chiều này bằng chữ — chỉ nói "fail build nếu THIẾU" (chiều 1). Quyết định KHÔNG fail build ở đây:
 * một dòng DB cũ (rác) không chặn được người khác build/deploy asset MỚI, khác hẳn việc thiếu đăng ký
 * cho asset đã vẽ xong (khoá được ngay hôm nay). Ghi log để chủ dự án tự dọn tay hoặc quyết định sau.
 */
final class OrphanAssetChecker {

    private static final String SELECT_ASSET_KEYS_SQL = "SELECT asset_key FROM art_asset WHERE asset_kind = ?";

    private static final String SPRITE_DISK_ROOT = "assets/exported/atlases/";
    private static final String MODEL_DISK_ROOT = "assets/exported/models/";

    static {
        // Cùng phát hiện thật đã ghi ở ArtRegistryUpserter: pgjdbc gửi TimeZone.getDefault() của JVM
        // cho Postgres NGAY LÚC MỞ CONNECTION (không phải lúc query), nên máy dev múi giờ khác UTC bị
        // FATAL ngay cả với một SELECT đơn giản. Lặp lại static block này (thay vì chỉ dựa vào
        // ArtRegistryUpserter đã chạy trước trong AssetPipelineRunner.main()) để class này tự đứng
        // vững khi test gọi thẳng, không phụ thuộc thứ tự load class của lớp khác.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private OrphanAssetChecker() {}

    static void check(List<AssetFolder> assets, List<ScannedModel> models, ArtRegistryConfig config) {
        Set<String> diskSpriteKeys =
                assets.stream().map(AssetFolder::assetKey).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> diskModelKeys =
                models.stream().map(ScannedModel::assetKey).collect(Collectors.toCollection(LinkedHashSet::new));

        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password())) {
            Set<String> dbSpriteKeys = selectAssetKeys(connection, "SPRITE");
            Set<String> dbModelKeys = selectAssetKeys(connection, "MODEL");

            failIfOrphanedOnDisk("SPRITE", SPRITE_DISK_ROOT, diskSpriteKeys, dbSpriteKeys);
            failIfOrphanedOnDisk("MODEL", MODEL_DISK_ROOT, diskModelKeys, dbModelKeys);

            warnIfStaleInDatabase("SPRITE", diskSpriteKeys, dbSpriteKeys);
            warnIfStaleInDatabase("MODEL", diskModelKeys, dbModelKeys);
        } catch (SQLException e) {
            throw new AssetPipelineException("Lỗi kiểm mồ côi art_asset (đã chạy V19/V20 chưa?): " + e.getMessage());
        }
        System.out.println("[asset-pipeline] kiểm mồ côi: OK (" + diskSpriteKeys.size() + " sprite / "
                + diskModelKeys.size() + " model đối chiếu với art_asset)");
    }

    private static Set<String> selectAssetKeys(Connection connection, String assetKind) throws SQLException {
        Set<String> keys = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ASSET_KEYS_SQL)) {
            statement.setString(1, assetKind);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    keys.add(resultSet.getString(1));
                }
            }
        }
        return keys;
    }

    private static void failIfOrphanedOnDisk(
            String branchLabel, String diskRoot, Set<String> diskKeys, Set<String> dbKeys) {
        Set<String> orphaned = new LinkedHashSet<>(diskKeys);
        orphaned.removeAll(dbKeys);
        if (!orphaned.isEmpty()) {
            throw new AssetPipelineException("Asset mồ côi nhánh " + branchLabel + " (" + diskRoot
                    + ") — có file trên đĩa nhưng KHÔNG có dòng art_asset tương ứng (asset_kind='"
                    + branchLabel + "'): " + orphaned);
        }
    }

    // Chỉ log, KHÔNG ném AssetPipelineException — xem lý do ở javadoc class "Chiều 2".
    private static void warnIfStaleInDatabase(String branchLabel, Set<String> diskKeys, Set<String> dbKeys) {
        Set<String> stale = new LinkedHashSet<>(dbKeys);
        stale.removeAll(diskKeys);
        if (!stale.isEmpty()) {
            System.out.println("[asset-pipeline] CẢNH BÁO kiểm mồ côi nhánh " + branchLabel + ": " + stale.size()
                    + " dòng art_asset không còn file trên đĩa tương ứng trong lần quét này (có thể asset đã bị"
                    + " xoá khỏi đĩa nhưng dòng DB còn sót lại — không fail build, tự xem xét dọn tay): " + stale);
        }
    }
}
