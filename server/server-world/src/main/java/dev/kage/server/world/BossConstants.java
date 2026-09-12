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

    private BossConstants() {}
}
