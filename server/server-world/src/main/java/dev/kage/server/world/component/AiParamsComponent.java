package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Tham số FSM của LOÀI con quái này (ADR-039 D5) — hung dữ tới đâu, bám dai tới đâu, đi nhanh chậm
 * ra sao. Gắn lên entity đúng MỘT LẦN lúc spawn.
 *
 * <p><b>Vì sao là Component chứ không phải tra catalog trong tick.</b> Cách hiển nhiên —
 * {@code AiSystem} cầm {@code MonsterCatalogSnapshot} rồi mỗi tick gọi
 * {@code catalog.find(monsterCode)} — vi phạm /CLAUDE.md §6: một lần tra {@code HashMap} theo chuỗi
 * <b>cộng một {@code Optional} mới</b> cho MỖI con quái, MỖI tick, 60 lần/giây. Ở dạng Component,
 * việc tra là một {@code ComponentMapper.get()}: O(1), không cấp phát — cùng cách
 * {@link CombatStatsComponent} đang được đối xử.
 *
 * <p><b>Ngoại lệ có chủ đích với ECS.md §3.1</b> ("component mutable + field public"): đây là
 * Component {@code record} ĐẦU TIÊN của dự án. Lý do: mọi Component khác giữ <i>trạng thái chạy</i>
 * (System ghi vào mỗi tick), còn cái này giữ <i>cấu hình tĩnh của loài</i> — không ai được ghi. Bất
 * biến chính là thứ khiến việc dùng CHUNG một instance cho nhiều entity cùng loài trở nên an toàn
 * hiển nhiên. Trạng thái chạy của FSM ở {@link AiStateComponent}, không trộn vào đây: hai vòng đời
 * khác nhau ⇒ hai component.
 *
 * @param aggroRadiusMeters bán kính phát hiện người chơi, đo từ QUÁI tới NGƯỜI CHƠI. <b>0 = không
 *     bao giờ gây hấn</b> ({@code training_dummy}, ADR-039 D4)
 * @param leashRadiusMeters đo từ QUÁI tới ĐIỂM SPAWN của nó — đơn vị đo KHÁC aggro. Vượt là bỏ
 *     cuộc, quay về
 * @param chaseSpeedMps tốc độ khi đuổi và khi quay về
 * @param patrolSpeedMps tốc độ khi đi tuần
 * @param patrolRadiusMeters bán kính chọn điểm tuần tra quanh spawn; 0 = đứng nguyên tại chỗ
 * @param idleDurationSeconds đứng yên bao lâu rồi mới đi tuần
 */
public record AiParamsComponent(
        float aggroRadiusMeters,
        float leashRadiusMeters,
        float chaseSpeedMps,
        float patrolSpeedMps,
        float patrolRadiusMeters,
        float idleDurationSeconds)
        implements Component {

    /** Loài này không bao giờ chủ động tấn công (ADR-039 D4). */
    public boolean isPassive() {
        return aggroRadiusMeters == 0f;
    }
}
