package dev.kage.server.world;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Danh sách TĨNH mọi skill trong game (Phase 8 — thay {@code SkillConstants} hardcode 1 skill).
 * CHƯA DB-driven (giống {@code CombatConstants.MONSTER_STATS}) — mỗi hệ phái mới thêm skill chỉ
 * cần thêm 1 dòng {@link SkillDefinition} + 1 file script, không sửa {@link SkillSystem}.
 */
final class SkillRegistry {

    private static final int ULTIMATE_LEVEL = SkillConstants.ULTIMATE_REQUIRED_LEVEL;
    private static final int CONSUME_ALL_CHI = SkillDefinition.CONSUME_ALL_CHI;

    private static final List<SkillDefinition> ALL = List.of(
            // Hỏa — "sát thương liên tục, bùng nổ" (GDD §5 dòng 60), hoàn thiện nốt lát cắt sau Phase
            // 8 (xem ROADMAP.md Phase 8 lát cắt 3) — không CC/khiên/hồi phục như 4 hệ kia, dồn toàn
            // bộ 4 active vào damage thuần, khác biệt bằng cooldown/hệ số thay vì cơ chế.
            new SkillDefinition(
                    SkillConstants.FIREBALL_SKILL_CODE,
                    Element.HOA,
                    0,
                    SkillTargetKind.ENEMY,
                    SkillConstants.FIREBALL_SCRIPT_RESOURCE,
                    SkillConstants.FIREBALL_COOLDOWN_SECONDS,
                    SkillConstants.FIREBALL_CHI_COST,
                    SkillConstants.FIREBALL_RANGE_METERS,
                    1),
            new SkillDefinition(
                    "hoa_tia_lua",
                    Element.HOA,
                    1,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/hoa_tia_lua.js",
                    2f,
                    8,
                    5f,
                    1),
            new SkillDefinition(
                    "hoa_bung_no",
                    Element.HOA,
                    2,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/hoa_bung_no.js",
                    7f,
                    16,
                    5f,
                    1),
            new SkillDefinition(
                    "hoa_diem_nguc",
                    Element.HOA,
                    SkillDefinition.ULTIMATE_SLOT,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/hoa_diem_nguc.js",
                    90f,
                    CONSUME_ALL_CHI,
                    6f,
                    ULTIMATE_LEVEL),

            // Thủy — khống chế + hồi phục (GDD §4). Toàn bộ cooldown/chi/tầm là PLACEHOLDER, chưa
            // cân bằng qua playtest (cùng tinh thần CombatConstants/SkillConstants).
            new SkillDefinition(
                    "thuy_song_du",
                    Element.THUY,
                    0,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/thuy_song_du.js",
                    4f,
                    12,
                    5f,
                    1),
            new SkillDefinition(
                    "thuy_phong_toa",
                    Element.THUY,
                    1,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/thuy_phong_toa.js",
                    8f,
                    15,
                    5f,
                    1),
            new SkillDefinition(
                    "thuy_hoi_phuc",
                    Element.THUY,
                    2,
                    SkillTargetKind.ALLY,
                    "/scripts/skill/thuy_hoi_phuc.js",
                    10f,
                    18,
                    6f,
                    1),
            new SkillDefinition(
                    "thuy_thuy_trieu",
                    Element.THUY,
                    SkillDefinition.ULTIMATE_SLOT,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/thuy_thuy_trieu.js",
                    90f,
                    CONSUME_ALL_CHI,
                    6f,
                    ULTIMATE_LEVEL),

            // Phong — tốc độ + bạo kích (GDD §4).
            new SkillDefinition(
                    "phong_cuong_phong",
                    Element.PHONG,
                    0,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/phong_cuong_phong.js",
                    4f,
                    12,
                    5f,
                    1),
            new SkillDefinition(
                    "phong_gia_toc",
                    Element.PHONG,
                    1,
                    SkillTargetKind.SELF,
                    "/scripts/skill/phong_gia_toc.js",
                    10f,
                    15,
                    0f,
                    1),
            new SkillDefinition(
                    "phong_bao_kich",
                    Element.PHONG,
                    2,
                    SkillTargetKind.SELF,
                    "/scripts/skill/phong_bao_kich.js",
                    10f,
                    15,
                    0f,
                    1),
            new SkillDefinition(
                    "phong_loc_xoay",
                    Element.PHONG,
                    SkillDefinition.ULTIMATE_SLOT,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/phong_loc_xoay.js",
                    90f,
                    CONSUME_ALL_CHI,
                    6f,
                    ULTIMATE_LEVEL),

            // Thổ — phòng thủ + khiên (GDD §4).
            new SkillDefinition(
                    "tho_trong_kich",
                    Element.THO,
                    0,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/tho_trong_kich.js",
                    5f,
                    14,
                    5f,
                    1),
            new SkillDefinition(
                    "tho_khien_da",
                    Element.THO,
                    1,
                    SkillTargetKind.SELF,
                    "/scripts/skill/tho_khien_da.js",
                    12f,
                    18,
                    0f,
                    1),
            new SkillDefinition(
                    "tho_kien_dinh",
                    Element.THO,
                    2,
                    SkillTargetKind.SELF,
                    "/scripts/skill/tho_kien_dinh.js",
                    10f,
                    15,
                    0f,
                    1),
            new SkillDefinition(
                    "tho_dia_chan",
                    Element.THO,
                    SkillDefinition.ULTIMATE_SLOT,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/tho_dia_chan.js",
                    90f,
                    CONSUME_ALL_CHI,
                    6f,
                    ULTIMATE_LEVEL),

            // Lôi — đơn mục tiêu, xuyên giáp, trung lập khắc chế (GDD §4).
            new SkillDefinition(
                    "loi_tia_set",
                    Element.LOI,
                    0,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/loi_tia_set.js",
                    4f,
                    13,
                    5f,
                    1),
            new SkillDefinition(
                    "loi_truy_kich",
                    Element.LOI,
                    1,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/loi_truy_kich.js",
                    5f,
                    14,
                    5f,
                    1),
            new SkillDefinition(
                    "loi_dien_giat",
                    Element.LOI,
                    2,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/loi_dien_giat.js",
                    10f,
                    16,
                    5f,
                    1),
            new SkillDefinition(
                    "loi_thien_loi",
                    Element.LOI,
                    SkillDefinition.ULTIMATE_SLOT,
                    SkillTargetKind.ENEMY,
                    "/scripts/skill/loi_thien_loi.js",
                    90f,
                    CONSUME_ALL_CHI,
                    6f,
                    ULTIMATE_LEVEL));

    static final Map<String, SkillDefinition> BY_CODE =
            ALL.stream().collect(Collectors.toMap(SkillDefinition::code, Function.identity()));

    private SkillRegistry() {}

    static List<String> allScriptResources() {
        return ALL.stream().map(SkillDefinition::scriptResource).toList();
    }
}
