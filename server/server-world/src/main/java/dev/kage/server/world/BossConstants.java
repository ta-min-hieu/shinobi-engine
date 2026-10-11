package dev.kage.server.world;

/**
 * Trần câu lệnh của sandbox script boss (ADR-016, SCRIPTING.md §4).
 *
 * <p>⚠️ Lớp này từng giữ TOÀN BỘ định nghĩa của con boss duy nhất trong dự án: mã, máu, EXP, đường
 * dẫn script, nhịp hỏi script, hồi chiêu. ADR-048 kéo hết sang {@code monster_template} — mã/máu/
 * EXP/model là cột dữ liệu, đường dẫn script là {@code ai_script_ref}, hai con số nhịp là
 * {@code ai_decision_interval_seconds}/{@code ai_skill_cooldown_seconds}.
 *
 * <p>Thứ còn lại ở đây là thứ DUY NHẤT không thuộc về con boss nào: một tham số CƠ CHẾ chung cho
 * mọi script. Nó ở lại Java đúng theo Điều 27 — dữ liệu tả NỘI DUNG, không tả giới hạn sandbox.
 */
final class BossConstants {

    /**
     * Đủ dư cho một script vài dòng if/return object literal — đây là chặn vòng lặp vô hạn thật
     * ({@link ScriptSandbox} statementLimit), không phải giới hạn "vừa đủ" cần tinh chỉnh.
     */
    static final long SCRIPT_MAX_STATEMENTS = 10_000L;

    /**
     * Boss nhớ "người khác vừa đánh mình" trong bao lâu (README dòng 37i, xem {@link BossRetargeting}).
     * 10 giây: dài hơn mọi hồi chiêu đang có (dài nhất 8 giây) nên một người đang đánh đều tay không
     * bao giờ rơi khỏi trí nhớ, mà người đã bỏ đi thì không kéo boss theo mãi. Con số do người triển
     * khai chọn — chủ dự án chỉ chốt hành vi.
     */
    static final float OTHER_ATTACKER_MEMORY_SECONDS = 10f;

    /**
     * Boss chịu khống chế (làm chậm, choáng) với NỬA thời lượng — chủ dự án chốt 2026-10-11 (README
     * dòng 37vi): hệ Thuỷ/Lôi vẫn có ích khi đánh boss, nhưng không khoá chết được nó.
     */
    static final float CROWD_CONTROL_DURATION_MULTIPLIER = 0.5f;

    private BossConstants() {}
}
