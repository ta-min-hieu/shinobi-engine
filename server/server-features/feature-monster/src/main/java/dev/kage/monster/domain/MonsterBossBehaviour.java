package dev.kage.monster.domain;

/**
 * Hành vi kịch bản của một loài BOSS (ADR-048) — ba thứ mà quái FSM thường không có.
 *
 * <p>Tách khỏi {@link MonsterTemplate} vì nó là thứ <b>vắng mặt</b> ở hầu hết các loài:
 * {@code MonsterTemplate.bossBehaviour == null} nghĩa là "chạy FSM thường", và đó là trường hợp
 * thường gặp. Nhét ba field nullable thẳng vào record kia sẽ bắt mọi chỗ dựng template phải truyền
 * ba số vô nghĩa.
 *
 * <p><b>{@code scriptRef} là ĐƯỜNG DẪN, không phải logic</b> — đúng khuôn
 * {@code npc_template.dialogue_script_ref} và {@code quest_template.script_ref} đã dùng từ Phase 6.
 * Hành vi thật nằm trong file GraalJS (Điều 27); Java chỉ biết nạp nó.
 *
 * <p>Vì sao hai con số nhịp là DỮ LIỆU chứ không phải hằng số Java (ADR-048 D4): boss thứ hai của
 * cốt truyện là một kẻ <i>cải trang</i>, nhịp ra đòn của hắn phải khác con máy móc của Phase 8. Khi
 * chúng còn là {@code BossConstants.DECISION_INTERVAL_SECONDS}/{@code HEAVY_STRIKE_COOLDOWN_SECONDS}
 * thì mọi boss buộc phải đánh cùng một nhịp.
 *
 * @param scriptRef đường dẫn classpath tới script AI, ví dụ {@code /scripts/boss/sample_boss.js}
 * @param decisionIntervalSeconds nhịp HỎI script — throttle, không phải mỗi tick (AI.md §9)
 * @param skillCooldownSeconds hồi chiêu đặc biệt
 */
public record MonsterBossBehaviour(String scriptRef, float decisionIntervalSeconds, float skillCooldownSeconds) {

    public MonsterBossBehaviour {
        if (scriptRef == null || scriptRef.isBlank()) {
            throw new IllegalArgumentException("scriptRef không được rỗng — boss phải có script (ADR-048 D2)");
        }
        requirePositive(decisionIntervalSeconds, "decisionIntervalSeconds");
        requirePositive(skillCooldownSeconds, "skillCooldownSeconds");
    }

    private static void requirePositive(float value, String field) {
        // Bằng 0 nghĩa là hỏi script MỖI tick 60Hz (nhịp quyết định) hoặc chiêu không bao giờ hồi
        // (cooldown) — cả hai đều là số vô nghĩa lọt qua từ một hàng DB sai, không phải cấu hình hợp lệ.
        if (!(value > 0f)) {
            throw new IllegalArgumentException(field + " phải dương: " + value);
        }
    }
}
