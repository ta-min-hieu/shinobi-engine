package dev.kage.tools.assetpipeline;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Suy ra danh mục tầng lá + hệ phái từ {@code asset_key}, theo đúng quy ước đã dùng khi vẽ asset
 * (ASSET-LOG.md) — KHÔNG phải bảng tra tuỳ ý, đây là quy ước duy nhất đang tồn tại trong repo:
 * {@link #NPC_ASSET_KEYS} liệt kê NPC, mọi {@code char_*} còn lại là nhân vật người chơi;
 * {@code mob_*} hiện chỉ có 1 quái thường; {@code vfx_*} hiện chỉ có VFX skill; {@code icon_*} hiện
 * chỉ có icon vật phẩm (ART-PIPELINE.md §8.2 cây danh mục).
 *
 * <p>Khi asset đầu tiên thuộc loại khác xuất hiện (VD quái boss, icon skill) — sửa lại logic này,
 * đừng đoán trước phân nhánh cho loại chưa từng có asset thật.
 *
 * <p><b>2026-09-09 — lời nhắc trên đã tới hạn một lần.</b> Suốt từ Phase 6 thế giới chỉ có ĐÚNG MỘT
 * NPC, nên nhánh NPC ở đây từng là một phép so sánh chuỗi với {@code "char_town-elder"}. NPC thứ hai
 * ({@code senior_sister}, seed V37) biến phép so sánh đó thành lỗi im lặng: {@code char_senior-sister}
 * sẽ rơi vào {@code character-player} và nằm nhầm danh mục vĩnh viễn, không có gì đỏ lên cả. Nay là
 * một TẬP, và mọi NPC mới phải thêm một dòng vào đó — vẫn thủ công, nhưng nó thủ công một cách nhìn
 * thấy được. Danh mục là thứ thuộc về art (ART-PIPELINE.md §8.2), không đọc được từ DB gameplay:
 * công cụ này chạy trên đĩa, không nối tới Postgres.
 */
final class AssetCategoryResolver {

    private static final String[] ELEMENT_CODES = {"hoa", "thuy", "phong", "loi", "tho"};

    /** Các {@code char_*} là NPC; phần còn lại là ngoại hình nhân vật người chơi (5 hệ × 3 bộ). */
    private static final Set<String> NPC_ASSET_KEYS = Set.of("char_town-elder", "char_senior-sister");

    private AssetCategoryResolver() {}

    static String leafCategoryCode(String assetKey) {
        if (assetKey.startsWith("char_")) {
            return NPC_ASSET_KEYS.contains(assetKey) ? "character-npc" : "character-player";
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
                return Optional.of(code.toUpperCase(Locale.ROOT));
            }
        }
        return Optional.empty();
    }
}
