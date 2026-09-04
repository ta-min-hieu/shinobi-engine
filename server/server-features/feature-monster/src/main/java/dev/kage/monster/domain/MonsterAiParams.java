package dev.kage.monster.domain;

/**
 * Tham số AI của một loài quái (ADR-039 D1, sáu cột {@code *_meters}/{@code *_mps}/{@code *_seconds}
 * trên {@code monster_template}) — thứ quyết định con quái đó có <b>tính cách</b> gì: hung dữ tới
 * đâu, bám dai tới đâu, đi nhanh chậm ra sao.
 *
 * <p>Tách khỏi {@link MonsterStats} vì đây là nhóm có nghĩa riêng: {@code MonsterStats} là mọi thứ
 * cần để dựng chỉ số chiến đấu, còn nhóm này là mọi thứ {@code AiSystem} cần để chạy FSM (AI.md §3)
 * — hai System khác nhau đọc, hai lý do khác nhau để thay đổi.
 *
 * <p><b>Chỉ SÁU trong tám hằng của {@code AiConstants} có mặt ở đây, và đó là chủ đích</b>
 * (ADR-039 D1). Hai hằng ở lại code vì chúng là <i>cơ chế</i> chứ không phải <i>nội dung</i>:
 * {@code ARRIVAL_THRESHOLD_METERS} là epsilon hình học có giá trị đúng phụ thuộc {@code FIXED_DT}
 * và tốc độ (đặt 0 ⇒ quái không bao giờ "tới nơi", rung quanh điểm spawn vĩnh viễn), còn
 * {@code MAX_PATROL_DURATION_SECONDS} là van an toàn chống kẹt — van mà người dùng chỉnh được thì
 * không còn là van.
 *
 * <p>{@code aggroRadiusMeters == 0} có nghĩa riêng: <b>không bao giờ gây hấn</b>. Đó là cách
 * {@code training_dummy} thôi đuổi theo người chơi (ADR-039 D4) mà không cần thêm cờ
 * {@code is_passive} nào.
 *
 * @param aggroRadiusMeters bán kính phát hiện người chơi, đo từ QUÁI tới NGƯỜI CHƠI; 0 = thụ động
 * @param leashRadiusMeters đo từ QUÁI tới ĐIỂM SPAWN của nó (khác đơn vị đo với aggro) — vượt là
 *     bỏ cuộc, quay về. Phải {@code >=} aggro, nếu không FSM dao động Chase↔Return vô hạn
 * @param chaseSpeedMps tốc độ khi đuổi và khi quay về
 * @param patrolSpeedMps tốc độ khi đi tuần
 * @param patrolRadiusMeters bán kính chọn điểm tuần tra quanh spawn; 0 = đứng nguyên tại chỗ
 * @param idleDurationSeconds đứng yên bao lâu rồi mới đi tuần
 */
public record MonsterAiParams(
        float aggroRadiusMeters,
        float leashRadiusMeters,
        float chaseSpeedMps,
        float patrolSpeedMps,
        float patrolRadiusMeters,
        float idleDurationSeconds) {

    public MonsterAiParams {
        requireNonNegative(aggroRadiusMeters, "aggroRadiusMeters");
        requireNonNegative(patrolRadiusMeters, "patrolRadiusMeters");
        requirePositive(leashRadiusMeters, "leashRadiusMeters");
        requirePositive(chaseSpeedMps, "chaseSpeedMps");
        requirePositive(patrolSpeedMps, "patrolSpeedMps");
        requirePositive(idleDurationSeconds, "idleDurationSeconds");
        if (leashRadiusMeters < aggroRadiusMeters) {
            throw new IllegalArgumentException(
                    "leashRadiusMeters phải >= aggroRadiusMeters, nếu không quái dao động Chase/Return vô hạn: "
                            + leashRadiusMeters + " < " + aggroRadiusMeters);
        }
        if (patrolRadiusMeters > leashRadiusMeters) {
            throw new IllegalArgumentException(
                    "patrolRadiusMeters phải <= leashRadiusMeters: " + patrolRadiusMeters + " > " + leashRadiusMeters);
        }
    }

    /** {@code aggroRadiusMeters == 0} — loài này không bao giờ chủ động tấn công (ADR-039 D4). */
    public boolean isPassive() {
        return aggroRadiusMeters == 0f;
    }

    // NaN kiểm riêng: mọi so sánh với NaN đều false, nên `value <= 0` KHÔNG bắt được nó — một cột
    // REAL hỏng sẽ lọt vào tick thread rồi biến vị trí quái thành NaN vĩnh viễn.
    private static void requirePositive(float value, String field) {
        if (Float.isNaN(value) || value <= 0f) {
            throw new IllegalArgumentException(field + " phải > 0: " + value);
        }
    }

    private static void requireNonNegative(float value, String field) {
        if (Float.isNaN(value) || value < 0f) {
            throw new IllegalArgumentException(field + " phải >= 0: " + value);
        }
    }
}
