package dev.kage.tools.assetpipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Quét {@code assets/exported/models/{low,med,high}/} thành danh sách {@link ScannedModel} — bản
 * đối xứng của {@link AssetScanner} cho nhánh 3D (ADR-031).
 *
 * <p>Quét thư mục <b>đã xuất</b> chứ không phải thư mục raw, khác {@link AssetScanner}: bước xuất
 * glTF chạy trong Blender ({@code export_character_lods.py}, ART-PIPELINE.md §6.4 bước 9), không
 * chạy trong tool Java này — pipeline nhận model đã decimate sẵn rồi gác cổng, không tự decimate.
 *
 * <p>Thư mục thiếu = 0 model, không phải lỗi: {@code assets/exported/} nằm trong {@code .gitignore}
 * (ART-PIPELINE.md §7 "sinh lại được"), nên bản clone mới chưa có tier nào cả.
 */
final class ModelScanner {

    private static final String GLB_SUFFIX = ".glb";

    private ModelScanner() {}

    static List<ScannedModel> scan(Path modelsRoot) {
        List<ScannedModel> found = new ArrayList<>();
        for (GraphicsTier tier : GraphicsTier.values()) {
            collectTier(modelsRoot.resolve(tier.folderName), tier, found);
        }
        found.sort(Comparator.comparing(ScannedModel::assetKey).thenComparing(model -> model.tier().folderName));
        return found;
    }

    private static void collectTier(Path tierDir, GraphicsTier tier, List<ScannedModel> found) {
        if (!Files.isDirectory(tierDir)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(tierDir, "*" + GLB_SUFFIX)) {
            for (Path entry : entries) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                // getFileName() chỉ null với root path — entry luôn là con của tierDir (cùng lý do
                // đã ghi ở AssetScanner, SpotBugs không suy luận được qua DirectoryStream).
                String fileName = Objects.requireNonNull(entry.getFileName()).toString();
                String assetKey = fileName.substring(0, fileName.length() - GLB_SUFFIX.length());
                found.add(new ScannedModel(assetKey, tier, entry, GlbTriangleCounter.countTriangles(entry)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được thư mục model: " + tierDir, e);
        }
    }
}
