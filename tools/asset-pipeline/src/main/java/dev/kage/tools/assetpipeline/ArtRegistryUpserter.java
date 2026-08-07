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
import java.util.LinkedHashMap;
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
 * <p>Hai nhánh, hai câu SQL, cùng một bảng (ART-PIPELINE.md §8.6 — thêm cột chứ không tách bảng
 * {@code art_model}): {@link #upsertSprites} ghi {@code asset_kind = 'SPRITE'} kèm
 * {@code atlas_name}/{@code master_px}; {@link #upsertModels} ghi {@code asset_kind = 'MODEL'} kèm
 * {@code model_name}/{@code tri_counts}. Ràng buộc {@code art_asset_kind_branch} (V20) bác ngay
 * nếu một dòng mang cột của cả hai nhánh.
 *
 * <p>V19 (art_category + art_asset) và V20 (3 cột nhánh 3D) là điều kiện tiên quyết — chưa chạy
 * migration thì bước này lỗi rõ ràng (fail build), không âm thầm bỏ qua.
 */
final class ArtRegistryUpserter {

    /**
     * Thư mục bằng chứng nghiệm thu, tương đối gốc repo (ART-PIPELINE.md §7). Ghi hằng số này thay
     * vì {@code renderRoot} người dùng truyền vào: §8.4 bắt mọi cột đường dẫn của {@code art_asset}
     * giữ đường dẫn tương đối, mà {@code --render-root} thường được gọi bằng đường dẫn tuyệt đối.
     */
    private static final String RENDER_OUT_ROOT = "assets/raw/render-out/";

    private static final String UPSERT_SQL = """
            INSERT INTO art_asset
                (asset_key, category_id, element, status, asset_kind, blend_path, render_dir,
                 atlas_name, actions, master_px, license, checksum, updated_at)
            VALUES
                (?, (SELECT id FROM art_category WHERE code = ?), ?, 'EXPORTED', 'SPRITE', ?, ?,
                 'main', ?::jsonb, ?, 'ORIGINAL', ?, now())
            ON CONFLICT (asset_key) DO UPDATE SET
                category_id = EXCLUDED.category_id,
                element = EXCLUDED.element,
                status = EXCLUDED.status,
                asset_kind = EXCLUDED.asset_kind,
                model_name = NULL,
                tri_counts = NULL,
                blend_path = EXCLUDED.blend_path,
                render_dir = EXCLUDED.render_dir,
                atlas_name = EXCLUDED.atlas_name,
                actions = EXCLUDED.actions,
                master_px = EXCLUDED.master_px,
                checksum = EXCLUDED.checksum,
                updated_at = now()
            """;

    // Nhánh 3D. KHÔNG ghi atlas_name/master_px (model không nằm trong atlas nào) và KHÔNG ghi
    // actions: §8.8 chốt trang quản trị đọc danh sách animation thẳng từ sidecar model-meta.json,
    // đúng luật "không nhân bản dữ liệu đã đọc được bằng máy vào DB" mà §8.7 đặt ra cho toạ độ
    // region. tri_counts thì ngược lại — nó là dữ liệu DUY NHẤT cho phép so 3 tier (§8.8) nên phải
    // vào DB. Ba cột nhánh 2D bị ép NULL khi DO UPDATE: một asset có thể chuyển từ SPRITE sang
    // MODEL (đúng chuyện đã xảy ra với nhân vật theo ADR-031), giữ lại giá trị cũ là vi phạm ngay
    // ràng buộc art_asset_kind_branch.
    private static final String UPSERT_MODEL_SQL = """
            INSERT INTO art_asset
                (asset_key, category_id, element, status, asset_kind, model_name, blend_path,
                 render_dir, tri_counts, license, checksum, updated_at)
            VALUES
                (?, (SELECT id FROM art_category WHERE code = ?), ?, 'EXPORTED', 'MODEL', ?, ?,
                 ?, ?::jsonb, 'ORIGINAL', ?, now())
            ON CONFLICT (asset_key) DO UPDATE SET
                category_id = EXCLUDED.category_id,
                element = EXCLUDED.element,
                status = EXCLUDED.status,
                asset_kind = EXCLUDED.asset_kind,
                model_name = EXCLUDED.model_name,
                blend_path = EXCLUDED.blend_path,
                render_dir = EXCLUDED.render_dir,
                tri_counts = EXCLUDED.tri_counts,
                atlas_name = NULL,
                actions = NULL,
                master_px = NULL,
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

    static void upsertSprites(List<AssetFolder> assets, ArtRegistryConfig config) {
        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password())) {
            for (AssetFolder asset : assets) {
                upsertOne(connection, asset);
            }
        } catch (SQLException e) {
            throw new AssetPipelineException(
                    "Lỗi upsert art_asset (đã chạy V19__create_art_registry.sql chưa?): " + e.getMessage());
        }
        System.out.println("[asset-pipeline] upsert art_asset nhánh 2D: " + assets.size() + " dòng");
    }

    /**
     * Nhánh 3D (ADR-031). Một {@code asset_key} = MỘT dòng {@code art_asset} dù có 3 file
     * {@code .glb}: ba tier khác nhau ở con số tam giác, và con số đó nằm gọn trong một cột
     * {@code tri_counts} — đúng cách {@code atlas_name} gói 3 atlas tier vào một cột ở nhánh 2D
     * (ART-PIPELINE.md §8.5/§8.6). {@code asset_key} là UNIQUE nên không có cách nào khác.
     *
     * @param modelsRoot thư mục chứa sidecar {@code <asset_key>.model-meta.json}, tức
     *     {@code assets/exported/models/} (cha của 3 thư mục tier)
     * @param renderRoot thư mục master render, chỉ dùng để KIỂM TRA bằng chứng nghiệm thu có thật
     *     hay không — đường dẫn ghi vào DB luôn là hằng số tương đối {@link #RENDER_OUT_ROOT}
     */
    static void upsertModels(List<ScannedModel> models, Path modelsRoot, Path renderRoot, ArtRegistryConfig config) {
        if (models.isEmpty()) {
            return;
        }
        Map<String, List<ScannedModel>> tiersByAssetKey = new LinkedHashMap<>();
        for (ScannedModel model : models) {
            tiersByAssetKey
                    .computeIfAbsent(model.assetKey(), key -> new ArrayList<>())
                    .add(model);
        }
        try (Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password())) {
            for (List<ScannedModel> tiers : tiersByAssetKey.values()) {
                upsertOneModel(connection, tiers, modelsRoot, renderRoot);
            }
        } catch (SQLException e) {
            throw new AssetPipelineException("Lỗi upsert art_asset nhánh 3D"
                    + " (đã chạy V20__extend_art_asset_for_3d.sql chưa?): " + e.getMessage());
        }
        System.out.println("[asset-pipeline] upsert art_asset nhánh 3D: " + tiersByAssetKey.size() + " dòng model ("
                + models.size() + " file .glb)");
    }

    // assetKey suy từ chính tiers thay vì nhận thêm 1 tham số: mọi phần tử trong danh sách này đã
    // được gom theo asset_key ở upsertModels nên chúng luôn mang cùng một giá trị.
    private static void upsertOneModel(
            Connection connection, List<ScannedModel> tiers, Path modelsRoot, Path renderRoot) throws SQLException {
        String assetKey = tiers.getFirst().assetKey();
        ModelSidecar sidecar = ModelSidecar.readFrom(modelsRoot, assetKey);
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_MODEL_SQL)) {
            statement.setString(1, assetKey);
            statement.setString(2, AssetCategoryResolver.leafCategoryCode(assetKey));
            Optional<String> element = AssetCategoryResolver.element(assetKey);
            if (element.isPresent()) {
                statement.setString(3, element.get());
            } else {
                statement.setNull(3, Types.VARCHAR);
            }
            // model_name = asset_key: đường dẫn phát hành từng tier SUY RA theo quy ước
            // models/{low|med|high}/{model_name}.glb (§8.5), không lưu cứng 3 đường dẫn.
            statement.setString(4, assetKey);
            statement.setString(5, sidecar.blendPath());
            Optional<String> renderDir = acceptanceRenderDir(renderRoot, assetKey);
            if (renderDir.isPresent()) {
                statement.setString(6, renderDir.get());
            } else {
                statement.setNull(6, Types.VARCHAR);
            }
            statement.setString(7, triCountsToJson(tiers));
            statement.setString(8, checksumOfModels(tiers));
            statement.executeUpdate();
        }
    }

    // render_dir với MODEL đổi VAI TRÒ (§8.6): không còn là nguồn để pack atlas mà là BẰNG CHỨNG
    // NGHIỆM THU — contact sheet đã render lúc chấm Q1-Q12 (ART-QUALITY-STANDARD §7). Chỉ ghi khi
    // thư mục đó có thật: một số model chưa có contact sheet riêng, ghi bừa đường dẫn không tồn tại
    // thì trang quản trị mở ra chỉ thấy 404 mà vẫn tưởng có bằng chứng.
    private static Optional<String> acceptanceRenderDir(Path renderRoot, String assetKey) {
        if (!Files.isDirectory(renderRoot.resolve(assetKey))) {
            return Optional.empty();
        }
        return Optional.of(RENDER_OUT_ROOT + assetKey);
    }

    // {"low":5107,"med":10107,"high":20107} — duyệt theo thứ tự GraphicsTier chứ không theo thứ tự
    // quét được, để hai lần chạy trên cùng dữ liệu cho ra CÙNG một chuỗi JSON (idempotent thật, không
    // phải "cùng nội dung nhưng khác byte").
    private static String triCountsToJson(List<ScannedModel> tiers) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (GraphicsTier tier : GraphicsTier.values()) {
            for (ScannedModel model : tiers) {
                if (model.tier() != tier) {
                    continue;
                }
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append('"').append(tier.folderName).append("\":").append(model.triangleCount());
            }
        }
        return json.append('}').toString();
    }

    // Một checksum cho CẢ BỘ 3 tier, không phải 3 checksum: art_asset có đúng 1 dòng cho 1 model
    // (xem upsertModels), nên cột checksum phải trả lời được "bộ model này có đổi từ lần upsert
    // trước không". Cùng cách checksumOfFolder gộp mọi frame PNG của một asset sprite.
    private static String checksumOfModels(List<ScannedModel> tiers) {
        List<ScannedModel> ordered = new ArrayList<>(tiers);
        ordered.sort(Comparator.comparing(ScannedModel::tier));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ScannedModel model : ordered) {
                digest.update(Files.readAllBytes(model.file()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 luôn có sẵn trong JDK chuẩn", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file .glb để hash", e);
        }
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
