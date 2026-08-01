package dev.kage.tools.assetpipeline;

import java.util.Optional;

/**
 * Suy ra danh mục tầng lá + hệ phái từ {@code asset_key}, theo đúng quy ước đã dùng khi vẽ asset
 * (ASSET-LOG.md) — KHÔNG phải bảng tra tuỳ ý, đây là quy ước duy nhất đang tồn tại trong repo:
 * {@code char_town-elder} là NPC duy nhất, mọi {@code char_*} còn lại là nhân vật người chơi;
 * {@code mob_*} hiện chỉ có 1 quái thường; {@code vfx_*} hiện chỉ có VFX skill; {@code icon_*} hiện
 * chỉ có icon vật phẩm (ART-PIPELINE.md §8.2 cây danh mục).
 *
 * <p>Khi asset đầu tiên thuộc loại khác xuất hiện (VD quái boss, icon skill) — sửa lại logic này,
 * đừng đoán trước phân nhánh cho loại chưa từng có asset thật.
 */
final class AssetCategoryResolver {

    private static final String[] ELEMENT_CODES = {"hoa", "thuy", "phong", "loi", "tho"};

    private AssetCategoryResolver() {}

    static String leafCategoryCode(String assetKey) {
        if (assetKey.startsWith("char_")) {
            return "char_town-elder".equals(assetKey) ? "character-npc" : "character-player";
        }
        if (assetKey.startsWith("mob_")) {
            return "monster-normal";
        }
        if (assetKey.startsWith("vfx_")) {
            return "vfx-skill";
        }
        if (assetKey.startsWith("icon_")) {
            return "icon-item";
        }
        throw new AssetPipelineException("Không suy ra được danh mục cho asset_key '" + assetKey
                + "' — thêm nhánh mới vào AssetCategoryResolver khi có loại asset mới");
    }

    static Optional<String> element(String assetKey) {
        String afterPrefix = assetKey.substring(assetKey.indexOf('_') + 1);
        for (String code : ELEMENT_CODES) {
            if (afterPrefix.equals(code) || afterPrefix.startsWith(code + "-") || afterPrefix.startsWith(code + "_")) {
                return Optional.of(code.toUpperCase(java.util.Locale.ROOT));
            }
        }
        return Optional.empty();
    }
}
