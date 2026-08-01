package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Entry point {@code tools:asset-pipeline} (ADR-029). Đọc {@code assets/raw/render-out/} → hạ mẫu
 * xuống tier 1x/0.5x → validate tên → đóng gói atlas 3 tier (low/med/high) → sinh
 * {@code asset-manifest.json} → upsert {@code art_asset} (V19, ART-PIPELINE §8.3) NẾU có cấu hình
 * DB (biến môi trường {@code DB_PASSWORD} — xem {@link ArtRegistryConfig}); không có thì bỏ qua
 * bước upsert, không fail — cho phép duyệt atlas cục bộ mà không cần Postgres đang chạy.
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

        Path atlasesRoot = exportedRoot.resolve("atlases");
        packTier(assets, GraphicsTier.LOW, atlasesRoot);
        packTier(assets, GraphicsTier.MEDIUM, atlasesRoot);
        reuseMediumForHigh(atlasesRoot);

        Path manifestPath = exportedRoot.resolve("asset-manifest.json");
        ManifestWriter.writeManifest(exportedRoot, manifestPath);

        ArtRegistryConfig dbConfig = ArtRegistryConfig.fromEnvironmentOrNull();
        if (dbConfig != null) {
            ArtRegistryUpserter.upsertAll(assets, dbConfig);
        } else {
            System.out.println("[asset-pipeline] bỏ qua upsert art_asset (thiếu biến môi trường DB_PASSWORD)");
        }

        System.out.println("[asset-pipeline] xong -> " + exportedRoot);
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
