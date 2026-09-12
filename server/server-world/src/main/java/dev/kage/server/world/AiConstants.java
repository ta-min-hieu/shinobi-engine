package dev.kage.server.world;

import dev.kage.server.world.component.AiParamsComponent;

/**
 * Những gì CÒN LẠI của tham số FSM quái sau ADR-039: hai hằng <b>cơ chế</b> + bộ <b>mặc định</b>.
 *
 * <p>Sáu tham số mô tả <i>tính cách một loài</i> (aggro, leash, tốc độ đuổi/tuần tra, bán kính tuần
 * tra, thời gian đứng yên) đã chuyển thành cột {@code monster_template} và tới đây qua
 * {@link AiParamsComponent} — Điều 27, AI.md §10. Hai hằng dưới đây <b>cố ý ở lại code</b>
 * (ADR-039 D1):
 *
 * <ul>
 *   <li>{@link #ARRIVAL_THRESHOLD_METERS} là <b>epsilon hình học</b>, không mô tả con quái mà mô tả
 *       cách một vòng lặp rời rạc quyết định "đã tới nơi". Giá trị đúng là hàm của
 *       {@code WorldConstants.FIXED_DT} và tốc độ (60Hz × 3 m/s = 5cm/tick ⇒ 0.3m ≈ 6 tick dung
 *       sai). Đưa ra DB là mời người thiết kế nội dung phá bất biến của FSM bằng một con số trông
 *       rất hợp lý: đặt 0 thì {@code tickReturn}/{@code tickPatrol} KHÔNG BAO GIỜ thoả điều kiện
 *       tới nơi, quái rung quanh điểm spawn vĩnh viễn, và không có gì báo lỗi.
 *   <li>{@link #MAX_PATROL_DURATION_SECONDS} là <b>van an toàn</b> chống kẹt. Van an toàn mà người
 *       dùng chỉnh được thì không còn là van an toàn (đặt 0 ⇒ mọi lượt tuần tra chết yểu; đặt 3600
 *       ⇒ một con quái kẹt sẽ kẹt nguyên một giờ).
 * </ul>
 *
 * <p>{@link #DEFAULTS} là bộ giá trị mà toàn bộ dự án dùng chung TRƯỚC ADR-039, giữ nguyên từng con
 * số. Nó có hai người dùng, cả hai đều tường minh: mọi {@code DEFAULT} của migration V25 (nên chạy
 * migration xong hành vi y hệt), và <b>boss</b> — {@code sample_boss} không có hàng trong
 * {@code monster_template} vì ADR-037 D7 giữ boss hardcode, nên {@code WorldFacade} gắn thẳng bộ
 * mặc định này cho nó thay vì có một đường lui ngầm trong {@code AiSystem}.
 */
final class AiConstants {

    /** "Đã tới nơi chưa" — epsilon, KHÔNG phải tham số theo loài. Xem javadoc lớp. */
    static final float ARRIVAL_THRESHOLD_METERS = 0.3f;

    /** Van chống kẹt nếu vì lý do gì không tới được điểm tuần tra. KHÔNG phải tham số theo loài. */
    static final float MAX_PATROL_DURATION_SECONDS = 5f;

    /**
     * Bộ tham số mặc định — bằng ĐÚNG các hằng số dùng chung trước ADR-039, và bằng đúng các
     * {@code DEFAULT} của V25. Đổi ở đây mà không đổi migration là làm hai nguồn chân lý lệch nhau.
     */
    static final AiParamsComponent DEFAULTS = new AiParamsComponent(6f, 10f, 3f, 1.5f, 3f, 3f, false);

    private AiConstants() {}
}
