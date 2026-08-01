package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Quét {@code assets/raw/render-out/} thành danh sách {@link AssetFolder}. Bỏ qua thư mục con bắt
 * đầu bằng {@code _} (ảnh so sánh scratch dùng lúc làm art, không commit — ART-PIPELINE.md §6.1)
 * và mọi thứ không phải thư mục (VD {@code _compare_*.png} nằm ngay cấp render-out).
 */
final class AssetScanner {

    private AssetScanner() {}

    static List<AssetFolder> scan(Path renderRoot) {
        List<AssetFolder> found = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(renderRoot)) {
            for (Path entry : entries) {
                // getFileName() chỉ null cho root path — entry luôn là con của renderRoot đã mở
                // được DirectoryStream, không bao giờ là root, nhưng SpotBugs không suy luận được
                // điều đó qua Files.newDirectoryStream() nên assert tường minh ở đây.
                String name = Objects.requireNonNull(entry.getFileName()).toString();
                if (!Files.isDirectory(entry) || name.startsWith("_")) {
                    continue;
                }
                found.add(readFolder(name, entry));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được render-out: " + renderRoot, e);
        }
        return found;
    }

    private static AssetFolder readFolder(String assetKey, Path folder) throws IOException {
        Path metaPath = folder.resolve("render-meta.json");
        RenderMeta meta = Files.isRegularFile(metaPath) ? RenderMeta.readFrom(metaPath) : null;
        return new AssetFolder(assetKey, folder, meta);
    }
}
