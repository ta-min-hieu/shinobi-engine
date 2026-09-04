package dev.kage.monster.domain;

/**
 * Dữ liệu TĨNH của một loài quái (ADR-037 D1, bảng {@code monster_template}). Đọc-thuần, nạp một
 * lần lúc khởi động — cùng cách {@code item_template} được đối xử (DATABASE.md §182).
 *
 * <p>Trước ADR-037 những con số này nằm rải ở BỐN chỗ hardcode: {@code CombatConstants}
 * (chỉ số + máu), {@code WorldFacade.MONSTER_SPAWN_X} (vị trí), {@code MonsterExperience} (EXP,
 * một bảng tra Java mà javadoc của chính nó gọi là tạm), và {@code AiConstants} (tham số AI).
 * Chỗ thứ tư đóng lại ở ADR-039 D1 — xem {@link MonsterAiParams}; {@code AiConstants} nay chỉ còn
 * hai hằng CƠ CHẾ (epsilon "đã tới nơi" + van chống kẹt) và bộ mặc định.
 *
 * <p>{@code hitPercent}/{@code dodgePercent}/{@code critPercent} là SỐ NGUYÊN 0..100 (chia 100 khi
 * dựng chỉ số chiến đấu) — cân bằng bằng phần trăm nguyên dễ đọc hơn {@code 0.9f}, và tránh so sánh
 * dấu phẩy động trong migration sau này.
 *
 * <p>{@code dropTableCode} là {@code null} khi loài đó không rơi gì. Nó là CHUỖI chứ không phải id
 * vì {@code GrantLootUseCase} tra theo code — ADR-037 D1 cố ý không đổi chữ ký đó (đổi sẽ kéo theo
 * cả đường {@code onMonsterKilled}); FK trong DB vẫn là thứ bảo đảm code đó có thật.
 */
public record MonsterTemplate(
        String code,
        String nameKey,
        String modelKey,
        MonsterStats stats,
        MonsterAiParams aiParams,
        long expReward,
        String dropTableCode) {

    public MonsterTemplate {
        requireText(code, "code");
        requireText(nameKey, "nameKey");
        requireText(modelKey, "modelKey");
        if (stats == null) {
            throw new IllegalArgumentException("stats không được null");
        }
        if (aiParams == null) {
            throw new IllegalArgumentException("aiParams không được null");
        }
        if (expReward < 0) {
            throw new IllegalArgumentException("expReward không được âm: " + expReward);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " không được rỗng");
        }
    }
}
