package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Entry point {@code tools:asset-pipeline} (ADR-029). Hai nhánh chạy trong cùng một lần gọi:
 *
 * <ul>
 *   <li><b>2D (sprite)</b> — đọc {@code assets/raw/render-out/} → hạ mẫu xuống tier 1x/0.5x →
 *       validate tên → đóng gói atlas 3 tier (low/med/high).
 *   <li><b>3D (model glTF, ADR-031)</b> — đọc {@code assets/exported/models/{low,med,high}/} →
 *       validate tên → đếm tam giác thật trong từng {@code .glb} và gác ngân sách §4.4. Không
 *       decimate/xuất lại: bước đó thuộc Blender (ART-PIPELINE.md §6.4 bước 9).
 * </ul>
 *
 * <p>Rồi sinh {@code asset-manifest.json} cho cả hai nhánh → upsert {@code art_asset} (V19,
 * ART-PIPELINE §8.3) NẾU có cấu hình DB (biến môi trường {@code DB_PASSWORD} — xem
 * {@link ArtRegistryConfig}); không có thì bỏ qua bước upsert, không fail — cho phép duyệt atlas
 * cục bộ mà không cần Postgres đang chạy.
 *
 * <p>⚠️ Upsert hiện <b>chỉ chạy cho nhánh 2D</b>. Nhánh 3D cần cột {@code asset_kind}/
 * {@code model_name}/{@code tri_counts} mà {@code V19} chưa có; đó là DB schema change nên phải chờ
 * chủ dự án duyệt {@code V20} (ART-PIPELINE.md §8.6, /CLAUDE.md §4) — tool chỉ in cảnh báo rõ ràng
 * chứ không tự thêm cột, cũng không âm thầm nhét model vào cột của sprite.
 */
public final class AssetPipelineRunner {

    private AssetPipelineRunner() {}

    public static void main(String[] args) {
        Path renderRoot = argOrDefault(args, "--render-root", Path.of("assets/raw/render-out"));
        Path exportedRoot = argOrDefault(args, "--exported-root", Path.of("assets/exported"));

        List<AssetFolder> assets = AssetScanner.scan(renderRoot);
        System.out.println("[asset-pipeline] tìm thấy " + assets.size() + " asset trong " + renderRoot);
        assets.forEach(NamingValidator::validate);
        System.out.println("[asset-pipeline] validate tên: OK (" + assets.size() + "/" + assets.size() + ")");

        List<ScannedModel> models = checkModels(exportedRoot.resolve("models"));

        Path atlasesRoot = exportedRoot.resolve("atlases");
        packTier(assets, GraphicsTier.LOW, atlasesRoot);
        packTier(assets, GraphicsTier.MEDIUM, atlasesRoot);
        reuseMediumForHigh(atlasesRoot);

        Path manifestPath = exportedRoot.resolve("asset-manifest.json");
        ManifestWriter.writeManifest(exportedRoot, manifestPath, models);

        ArtRegistryConfig dbConfig = ArtRegistryConfig.fromEnvironmentOrNull();
        if (dbConfig != null) {
            ArtRegistryUpserter.upsertAll(assets, dbConfig);
            warnModelsNotUpserted(models);
        } else {
            System.out.println("[asset-pipeline] bỏ qua upsert art_asset (thiếu biến môi trường DB_PASSWORD)");
        }

        System.out.println("[asset-pipeline] xong -> " + exportedRoot);
    }

    // Nhánh 3D (ADR-031): quét + validate tên + gác ngân sách tam giác §4.4. Chạy TRƯỚC bước pack
    // 2D để hỏng thì hỏng sớm, đỡ tốn vài chục giây đóng gói atlas rồi mới báo lỗi.
    private static List<ScannedModel> checkModels(Path modelsRoot) {
        List<ScannedModel> models = ModelScanner.scan(modelsRoot);
        System.out.println("[asset-pipeline] tìm thấy " + models.size() + " model .glb trong " + modelsRoot);
        for (ScannedModel model : models) {
            NamingValidator.validateModel(model);
            // Locale.ROOT tường minh: máy dev locale vi-VN thì "%,d" ra dấu chấm, log so sánh giữa
            // hai máy sẽ khác nhau — cùng loại bẫy môi trường với UTF-8 console ở build.gradle.kts.
            System.out.printf(
                    Locale.ROOT,
                    "[asset-pipeline] model %-16s tier %-4s %,8d tam giác (trần %,6d)%n",
                    model.assetKey(),
                    model.tier().folderName,
                    model.triangleCount(),
                    model.tier().maxTriangles);
        }
        TriangleBudgetValidator.validateAll(models);
        if (!models.isEmpty()) {
            System.out.println("[asset-pipeline] ngân sách tam giác: OK (" + models.size() + "/" + models.size() + ")");
        }
        return models;
    }

    private static void warnModelsNotUpserted(List<ScannedModel> models) {
        if (models.isEmpty()) {
            return;
        }
        System.out.println("[asset-pipeline] CHƯA upsert " + models.size()
                + " model vào art_asset: bảng thiếu cột asset_kind/model_name/tri_counts."
                + " Đây là DB schema change — chờ chủ dự án duyệt V20 (ART-PIPELINE.md §8.6).");
    }

    private static void packTier(List<AssetFolder> assets, GraphicsTier tier, Path atlasesRoot) {
        Path stagingDir;
        try {
            stagingDir = Files.createTempDirectory("kage-asset-pipeline-" + tier.folderName + "-");
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được thư mục staging cho tier " + tier, e);
        }
        try {
            AtlasStager.stage(assets, tier, stagingDir);
            AtlasPacker.pack(stagingDir, atlasesRoot.resolve(tier.folderName));
            System.out.println("[asset-pipeline] tier " + tier.folderName + ": đóng gói xong");
        } catch (IOException e) {
            throw new UncheckedIOException("Lỗi hạ mẫu/đóng gói tier " + tier, e);
        } finally {
            deleteRecursively(stagingDir);
        }
    }

    // HIGH dùng chung ảnh @1x với MEDIUM (xem GraphicsTier) — sao chép thay vì render/pack lại lần 2.
    private static void reuseMediumForHigh(Path atlasesRoot) {
        Path mediumDir = atlasesRoot.resolve(GraphicsTier.MEDIUM.folderName);
        Path highDir = atlasesRoot.resolve(GraphicsTier.HIGH.folderName);
        try (Stream<Path> files = Files.walk(mediumDir)) {
            Files.createDirectories(highDir);
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = highDir.resolve(mediumDir.relativize(source));
                // destination luôn nằm trong highDir (đã tạo ở trên) hoặc thư mục con của nó.
                Files.createDirectories(Objects.requireNonNull(destination.getParent()));
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không sao chép được atlas medium -> high", e);
        }
        System.out.println("[asset-pipeline] tier high: sao chép từ medium (cùng độ phân giải @1x)");
    }

    private static void deleteRecursively(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            files.sorted(Comparator.reverseOrder()).forEach(AssetPipelineRunner::deleteQuietly);
        } catch (IOException e) {
            throw new UncheckedIOException("Không dọn được thư mục staging: " + root, e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Không xoá được: " + path, e);
        }
    }

    private static Path argOrDefault(String[] args, String flag, Path fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        return fallback;
    }
}
