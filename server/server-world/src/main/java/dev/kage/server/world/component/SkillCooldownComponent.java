package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Thời gian còn lại (giây) trước lần dùng skill tiếp theo — 1 SLOT/skill (index 0-2 = active, 3 =
 * Tuyệt Kỹ, xem {@code SkillDefinition.slotIndex}). Mảng {@code float} CỐ ĐỊNH (không
 * {@code Map<String,Float>}) để tránh boxing trong game loop (/CLAUDE.md §6) — cấp phát 1 LẦN lúc
 * attach, {@code StatusEffectSystem}-style: mỗi entity chỉ dùng 4 slot của ĐÚNG hệ phái mình, 3 hệ
 * còn lại không bao giờ đọc tới (không lãng phí gì đáng kể). Server giữ tuyệt đối, client không bao
 * giờ gửi cooldown (SECURITY.md §8 — giống {@link AttackCooldownComponent}).
 */
public final class SkillCooldownComponent implements Component {
    public static final int SLOT_COUNT = 4;
    public final float[] remainingSeconds = new float[SLOT_COUNT];
}
