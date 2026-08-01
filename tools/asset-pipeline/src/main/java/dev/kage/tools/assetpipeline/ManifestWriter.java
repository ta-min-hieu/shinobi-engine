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
 * xuất trong {@code assets/exported/atlases/}. Đường dẫn trong manifest tương đối tới
 * {@code assets/exported/} (ART-PIPELINE.md §8.4 — không nhúng hostname, đổi origin không sửa DB).
 */
final class ManifestWriter {

    private ManifestWriter() {}

    static void writeManifest(Path exportedRoot, Path manifestPath) {
        List<Path> files = listFilesSorted(exportedRoot.resolve("atlases"));
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"assetVersion\": 1,\n");
        json.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"entries\": [\n");
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String relativePath = exportedRoot.relativize(file).toString().replace('\\', '/');
            json.append("    { \"path\": \"").append(relativePath).append("\", ");
            json.append("\"hash\": \"sha256:").append(sha256Of(file)).append("\", ");
            json.append("\"size\": ").append(sizeOf(file)).append(" }");
            json.append(i < files.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        try {
            // manifestPath luôn là exportedRoot.resolve("asset-manifest.json") — có parent thật.
            Files.createDirectories(Objects.requireNonNull(manifestPath.getParent()));
            Files.writeString(manifestPath, json.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được manifest: " + manifestPath, e);
        }
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
