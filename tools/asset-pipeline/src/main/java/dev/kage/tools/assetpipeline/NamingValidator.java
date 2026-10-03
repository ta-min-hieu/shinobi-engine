package dev.kage.tools.assetpipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Kiểm tra quy ước đặt tên RESOURCE.md §4 ("cấm khoảng trắng, tiếng Việt có dấu, chữ hoa") và
 * ART-PIPELINE.md §8.2 (prefix {@code asset_key} phải khớp tầng 1 danh mục) — bằng máy, không dựa
 * trí nhớ (cùng tinh thần ADR-011). Ném {@link AssetPipelineException} = fail build, không phải
 * warning im lặng.
 */
final class NamingValidator {

    // char_/mob_/vfx_/icon_ đã có asset thật dùng (ASSET-LOG.md); env_/ui_ nằm trong danh mục
    // tầng 1 của ART-PIPELINE §8.2 nhưng chưa có asset nào — vẫn cho phép để không chặn tương lai.
    private static final Set<String> KNOWN_PREFIXES = Set.of("char_", "mob_", "vfx_", "icon_", "env_", "ui_");

    // ADR-031: chỉ nhân vật · quái · NPC mới render 3D; VFX/icon/UI/môi trường vẫn là sprite 2D.
    // File .glb mang prefix ngoài tập này = đặt nhầm thư mục hoặc hiểu nhầm ADR, không phải asset mới.
    private static final Set<String> MODEL_PREFIXES = Set.of("char_", "mob_");

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z0-9_.-]+$");

    /** Cùng dạng hậu tố mà TexturePacker coi là chỉ số frame (xem {@link #validateStaticIcon}). */
    private static final Pattern FRAME_INDEX_SUFFIX = Pattern.compile(".+_\\d+$");

    private NamingValidator() {}

    static void validate(AssetFolder asset) {
        validateAssetKey(asset.assetKey(), KNOWN_PREFIXES);
        if (asset.isStaticIcon()) {
            validateStaticIcon(asset);
        } else {
            validateAnimated(asset);
        }
    }

    /**
     * Nhánh 3D: tên file {@code .glb} bỏ đuôi phải là một {@code asset_key} hợp lệ y hệt nhánh 2D
     * (cùng cột {@code art_asset.asset_key}, ART-PIPELINE.md §8) — không có quy ước riêng cho model.
     */
    static void validateModel(ScannedModel model) {
        validateAssetKey(model.assetKey(), MODEL_PREFIXES);
    }

    private static void validateAssetKey(String assetKey, Set<String> allowedPrefixes) {
        requireSafeName(assetKey, "asset_key");
        if (allowedPrefixes.stream().noneMatch(assetKey::startsWith)) {
            throw new AssetPipelineException("asset_key '" + assetKey + "' không khớp prefix danh mục nào ("
                    + allowedPrefixes + ") — ART-PIPELINE.md §8.2");
        }
    }

    private static void validateStaticIcon(AssetFolder asset) {
        // Icon tĩnh được stage thành "<asset_key>.png" KHÔNG hậu tố frame — TexturePacker
        // (useIndexes, regex "(.+)_(\d+)$" trong ImageProcessor) sẽ hiểu "_<số>" cuối tên là chỉ số
        // frame: "icon_x_2" ra region "icon_x" index 2, client findRegion("icon_x_2") không thấy gì.
        if (FRAME_INDEX_SUFFIX.matcher(asset.assetKey()).find()) {
            throw new AssetPipelineException("Icon '" + asset.assetKey() + "' kết thúc bằng '_<số>' — TexturePacker"
                    + " sẽ đọc phần đó thành chỉ số frame và đặt sai tên region trong atlas; đổi tên"
                    + " (VD '-2' thay vì '_2')");
        }
        Path expected = asset.folder().resolve("icon.png");
        if (!Files.isRegularFile(expected)) {
            throw new AssetPipelineException(
                    "Icon '" + asset.assetKey() + "' thiếu file icon.png tại " + asset.folder());
        }
    }

    private static void validateAnimated(AssetFolder asset) {
        for (Map.Entry<String, Integer> action : asset.renderMeta().actions().entrySet()) {
            requireSafeName(action.getKey(), "action");
            for (int index = 0; index < action.getValue(); index++) {
                String fileName = "%s_%02d.png".formatted(action.getKey(), index);
                Path framePath = asset.folder().resolve(fileName);
                if (!Files.isRegularFile(framePath)) {
                    throw new AssetPipelineException("Asset '" + asset.assetKey() + "' thiếu frame " + fileName
                            + " (render-meta.json khai " + action.getValue() + " frame cho action '"
                            + action.getKey() + "') tại " + asset.folder());
                }
            }
        }
    }

    private static void requireSafeName(String name, String kind) {
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new AssetPipelineException(
                    "%s '%s' vi phạm quy ước đặt tên RESOURCE.md §4 (chỉ a-z 0-9 _ - .)".formatted(kind, name));
        }
    }
}
