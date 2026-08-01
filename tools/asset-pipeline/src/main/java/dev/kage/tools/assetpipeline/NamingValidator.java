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

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z0-9_.-]+$");

    private NamingValidator() {}

    static void validate(AssetFolder asset) {
        requireSafeName(asset.assetKey(), "asset_key");
        if (KNOWN_PREFIXES.stream().noneMatch(asset.assetKey()::startsWith)) {
            throw new AssetPipelineException("asset_key '" + asset.assetKey() + "' không khớp prefix danh mục nào ("
                    + KNOWN_PREFIXES + ") — ART-PIPELINE.md §8.2");
        }
        if (asset.isStaticIcon()) {
            validateStaticIcon(asset);
        } else {
            validateAnimated(asset);
        }
    }

    private static void validateStaticIcon(AssetFolder asset) {
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
