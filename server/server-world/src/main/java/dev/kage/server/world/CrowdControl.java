package dev.kage.server.world;

import dev.kage.server.world.component.StatusEffectType;

/**
 * Luật khống chế lên boss (chủ dự án chốt 2026-10-11, README dòng 37vi): boss chịu khống chế với nửa
 * thời lượng — không miễn hẳn (hệ Thuỷ/Lôi vẫn có ích) và không chịu đủ (không khoá chết được boss).
 *
 * <p>"Khống chế" ở đây là đúng hai thứ cướp quyền hành động hoặc di chuyển: choáng, và làm CHẬM (tốc
 * độ âm). Giảm DEF/RES là làm yếu chứ không phải khống chế, nên giữ nguyên thời lượng; buff tăng tốc
 * cũng vậy.
 */
final class CrowdControl {

    private CrowdControl() {}

    /**
     * Thời lượng thật của một hiệu ứng sắp áp lên mục tiêu.
     *
     * @param targetIsBoss mục tiêu có phải boss không
     * @param type loại hiệu ứng sắp áp
     * @param magnitude độ lớn (âm với làm chậm)
     * @param durationSeconds thời lượng script khai
     * @return thời lượng thật sẽ áp lên mục tiêu
     */
    static float durationOn(boolean targetIsBoss, StatusEffectType type, float magnitude, float durationSeconds) {
        if (!targetIsBoss || !isCrowdControl(type, magnitude)) {
            return durationSeconds;
        }
        return durationSeconds * BossConstants.CROWD_CONTROL_DURATION_MULTIPLIER;
    }

    private static boolean isCrowdControl(StatusEffectType type, float magnitude) {
        return type == StatusEffectType.STUN || (type == StatusEffectType.MOVE_SPEED_MOD && magnitude < 0f);
    }
}
