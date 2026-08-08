package dev.kage.client.core.world;

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

    static final String FIREBALL_SKILL_CODE = "fireball";

    static final float FIREBALL_COOLDOWN_SECONDS = 3f;

    static final int FIREBALL_CHI_COST = 10;

    static final float FIREBALL_RANGE_METERS = 5f;

    private CombatConstants() {}
}
