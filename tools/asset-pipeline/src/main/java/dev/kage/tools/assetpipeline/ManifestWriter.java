package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Sinh {@code asset-manifest.json} (RESOURCE.md §9: đường dẫn + sha256 + size) từ mọi file thật đã
 * xuất trong {@code assets/exported/atlases/} và {@code assets/exported/models/}. Đường dẫn trong
 * manifest tương đối tới {@code assets/exported/} (ART-PIPELINE.md §8.4 — không nhúng hostname, đổi
 * origin không sửa DB).
 *
 * <p>Hai nhánh nằm ở hai mảng tách bạch: {@code entries} là atlas 2D, {@code models} là glTF 3D
 * (ADR-031). Cùng 3 field {@code path}/{@code hash}/{@code size} để patcher xử lý y hệt nhau, nhưng
 * mảng {@code models} mang thêm {@code assetKey}/{@code tier}/{@code triangles} — client chọn tier
 * theo cấu hình đồ họa (ART-PIPELINE.md §5) nên phải biết file nào thuộc tier nào <b>trước khi</b>
 * tải, không thể suy từ đường dẫn bằng cách hard-code quy ước thư mục ở phía client.
 */
final class ManifestWriter {

    private ManifestWriter() {}

    static void writeManifest(Path exportedRoot, Path manifestPath, List<ScannedModel> models) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"assetVersion\": 1,\n");
        json.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
        appendAtlasEntries(json, exportedRoot);
        appendModelEntries(json, exportedRoot, models);
        json.append("}\n");

        try {
            // manifestPath luôn là exportedRoot.resolve("asset-manifest.json") — có parent thật.
            Files.createDirectories(Objects.requireNonNull(manifestPath.getParent()));
            Files.writeString(manifestPath, json.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được manifest: " + manifestPath, e);
        }
    }

    private static void appendAtlasEntries(StringBuilder json, Path exportedRoot) {
        List<Path> files = listFilesSorted(exportedRoot.resolve("atlases"));
        json.append("  \"entries\": [\n");
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            json.append("    { ");
            appendCommonFields(json, exportedRoot, file);
            json.append(" }");
            json.append(i < files.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ],\n");
    }

    private static void appendModelEntries(StringBuilder json, Path exportedRoot, List<ScannedModel> models) {
        json.append("  \"models\": [\n");
        for (int i = 0; i < models.size(); i++) {
            ScannedModel model = models.get(i);
            json.append("    { ");
            appendCommonFields(json, exportedRoot, model.file());
            json.append(", \"assetKey\": \"").append(model.assetKey()).append("\", ");
            json.append("\"tier\": \"").append(model.tier().folderName).append("\", ");
            json.append("\"triangles\": ").append(model.triangleCount());
            json.append(" }");
            json.append(i < models.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n");
    }

    private static void appendCommonFields(StringBuilder json, Path exportedRoot, Path file) {
        String relativePath = exportedRoot.relativize(file).toString().replace('\\', '/');
        json.append("\"path\": \"").append(relativePath).append("\", ");
        json.append("\"hash\": \"sha256:").append(sha256Of(file)).append("\", ");
        json.append("\"size\": ").append(sizeOf(file));
    }

    private static List<Path> listFilesSorted(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được thư mục xuất bản: " + root, e);
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được kích thước file: " + file, e);
        }
    }

    private static String sha256Of(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 luôn có sẵn trong JDK chuẩn", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file để hash: " + file, e);
        }
    }
}
