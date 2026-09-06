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
import java.util.Optional;
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
 * <p>Rồi sinh {@code asset-manifest.json} cho cả hai nhánh → upsert {@code art_asset} cho CẢ HAI
 * nhánh (V19 + V20, ART-PIPELINE §8.3/§8.6) → {@link OrphanAssetChecker} đối chiếu ngược đĩa/DB, fail
 * build nếu có file mồ côi (§8.5/§8.6) — TẤT CẢ NẾU có cấu hình DB (biến môi trường {@code
 * DB_PASSWORD} — xem {@link ArtRegistryConfig}); không có thì bỏ qua cả ba bước, không fail — cho
 * phép duyệt atlas cục bộ mà không cần Postgres đang chạy.
 */
public final class AssetPipelineRunner {

    private AssetPipelineRunner() {}

    /** Đọc {@code DB_PASSWORD} từ môi trường rồi giao cho {@link #run} — xem javadoc ở đó. */
    public static void main(String[] args) {
        run(
                argOrDefault(args, "--render-root", Path.of("assets/raw/render-out")),
                argOrDefault(args, "--exported-root", Path.of("assets/exported")),
                ArtRegistryConfig.fromEnvironment());
    }

    /**
     * Chạy trọn pipeline với nhánh registry do NGƯỜI GỌI chọn — {@code main} lấy từ môi trường, test
     * truyền thẳng.
     *
     * <p><b>Vì sao cấu hình DB là THAM SỐ chứ không đọc thẳng môi trường ở đây</b> (lỗi thật, ghi
     * ROADMAP mục 61): bản cũ gọi {@code ArtRegistryConfig.fromEnvironmentOrNull()} ngay giữa thân
     * hàm, nên {@code AssetPipelineIntegrationTest} rẽ nhánh theo biến môi trường của MÁY chạy —
     * {@code ./gradlew build} xanh trên máy sạch và ĐỎ trên đúng cái shell đã export
     * {@code DB_PASSWORD} để chạy server dev (test rơi vào nhánh upsert rồi đòi sidecar
     * {@code <asset_key>.model-meta.json}). Một tham số tường minh làm kết quả test chỉ phụ thuộc
     * thứ test tự khai, và mở luôn đường cho test nhánh registry chạy trên Postgres throwaway
     * ({@code AssetPipelineRegistryBranchIntegrationTest}) — điều mà bản cũ không làm được, xem
     * javadoc {@code OrphanAssetCheckerIntegrationTest}.
     *
     * @param registry rỗng = bỏ qua upsert + kiểm mồ côi (duyệt atlas cục bộ không cần Postgres)
     */
    static void run(Path renderRoot, Path exportedRoot, Optional<ArtRegistryConfig> registry) {
        List<AssetFolder> assets = AssetScanner.scan(renderRoot);
        System.out.println("[asset-pipeline] tìm thấy " + assets.size() + " asset trong " + renderRoot);
        assets.forEach(NamingValidator::validate);
        System.out.println("[asset-pipeline] validate tên: OK (" + assets.size() + "/" + assets.size() + ")");

        Path modelsRoot = exportedRoot.resolve("models");
        List<ScannedModel> models = checkModels(modelsRoot);

        Path atlasesRoot = exportedRoot.resolve("atlases");
        packTier(assets, GraphicsTier.LOW, atlasesRoot);
        packTier(assets, GraphicsTier.MEDIUM, atlasesRoot);
        reuseMediumForHigh(atlasesRoot);

        Path manifestPath = exportedRoot.resolve("asset-manifest.json");
        ManifestWriter.writeManifest(exportedRoot, manifestPath, models);

        if (registry.isPresent()) {
            ArtRegistryConfig dbConfig = registry.get();
            ArtRegistryUpserter.upsertSprites(assets, dbConfig);
            ArtRegistryUpserter.upsertModels(models, modelsRoot, renderRoot, dbConfig);
            OrphanAssetChecker.check(assets, models, dbConfig);
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
