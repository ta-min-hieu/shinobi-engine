package dev.kage.client.core.world;

import java.util.Map;

import dev.kage.protocol.Faction;

/**
 * Hằng số combat phía client — CHỈ để hiển thị/dự đoán (GAMEPLAY.md §3: "Server giữ hết: cooldown,
 * Khí, vị trí, HP. Client chỉ có một bản sao để hiển thị"). Server không gửi gói đồng bộ HP/Khí/hồi
 * chiêu nào cả — client bắt đầu ở đầy HP/Khí và tự trừ khi gửi {@code UseSkillCommand}, sửa lại
 * bằng số thật mỗi khi có {@code AttackResultEvent}/{@code SkillResultEvent}.
 *
 * <p>⚠️ PHẢI khớp {@code server-world.CombatConstants}/{@code SkillConstants}/{@code SkillRegistry}
 * — lệch chỉ gây HUD hiển thị sai (vd nút skill bật sớm/muộn hơn thật), không phải lỗ hổng, vì
 * server luôn tự kiểm tra lại và âm thầm bỏ qua lệnh không hợp lệ (SECURITY.md).
 */
final class CombatConstants {

    static final int CHARACTER_MAX_HP = 100;

    static final int CHARACTER_MAX_CHI = 100;

    /**
     * Nhịp đánh thường, giây — bản sao của {@code server-world.CombatConstants.ATTACK_INTERVAL_SECONDS}.
     * Chỉ dùng để giãn nhịp GỬI lệnh khi người chơi giữ nút Đánh ({@link AttackInputController}),
     * không phải cooldown thật: server tự giữ cooldown và bỏ qua lệnh thừa.
     */
    static final float ATTACK_INTERVAL_SECONDS = 1f;

    /**
     * Tầm đánh thường, mét — bản sao của {@code server-world.CombatConstants.ATTACK_RANGE_METERS}.
     * CHỈ dùng để nói cho người chơi biết "đang đứng xa quá" khi bấm đánh mà không thấy gì xảy ra;
     * lệnh vẫn được GỬI dù client nghĩ là ngoài tầm — server mới là bên quyết định trúng/trượt, và
     * vị trí hai bên có thể lệch tới {@code WorldConstants.RECONCILE_SNAP_THRESHOLD}.
     */
    static final float ATTACK_RANGE_METERS = 2f;

    static final String FIREBALL_SKILL_CODE = "fireball";

    static final float FIREBALL_COOLDOWN_SECONDS = 3f;

    static final int FIREBALL_CHI_COST = 10;

    static final float FIREBALL_RANGE_METERS = 5f;

    /**
     * 1 skill chủ động slot 0 của 1 hệ phái (đối chiếu {@code server-world.SkillRegistry} — mỗi hệ
     * có 4 active skill, HUD 1-nút hiện tại chỉ cần đúng skill slot 0, cùng slot Hỏa Cầu chiếm).
     */
    record FactionSkill(String skillCode, float cooldownSeconds, int chiCost, String nameKey) {}

    /**
     * Skill slot 0 của cả 5 hệ phái — nguồn cho nút skill duy nhất của HUD hiện tại, tra theo
     * {@code Faction} của người chơi. Giá trị đối chiếu {@code SkillRegistry} phía server; tên
     * tiếng Việt lấy đúng từ comment đầu file script GraalJS tương ứng (vd {@code thuy_song_du.js}
     * → "Sóng Dữ") để không tự bịa từ.
     */
    static final Map<Faction, FactionSkill> FACTION_SKILLS = Map.of(
            Faction.FACTION_HOA,
                    new FactionSkill(
                            FIREBALL_SKILL_CODE, FIREBALL_COOLDOWN_SECONDS, FIREBALL_CHI_COST, "hud.skill.fireball"),
            Faction.FACTION_THUY, new FactionSkill("thuy_song_du", 4f, 12, "hud.skill.thuy_song_du"),
            Faction.FACTION_PHONG, new FactionSkill("phong_cuong_phong", 4f, 12, "hud.skill.phong_cuong_phong"),
            Faction.FACTION_THO, new FactionSkill("tho_trong_kich", 5f, 14, "hud.skill.tho_trong_kich"),
            Faction.FACTION_LOI, new FactionSkill("loi_tia_set", 4f, 13, "hud.skill.loi_tia_set"));

    private CombatConstants() {}
}
