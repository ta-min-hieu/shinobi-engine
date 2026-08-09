package dev.kage.client.core.world;

import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Biến trạng thái giữ/nhả nút nhảy thành cú nhảy DỰ ĐOÁN CỤC BỘ trên {@link PositionComponent} của
 * chính người chơi (ADR-033 + nhảy đôi kiểu Ninja School Online, chủ dự án yêu cầu 2026-08-09).
 *
 * <p>Tách khỏi {@code WorldScreen} vì đây là logic thời gian có trạng thái riêng (mép nhấn + đồng
 * hồ giữ nút) chứ không phải việc vẽ hay việc mạng — và tách ra thì test được thẳng, không cần
 * context đồ hoạ ({@code JumpInputControllerTest}).
 *
 * <p><b>Chỉ DỰ ĐOÁN</b> (CLIENT.md §6): mọi cú nhảy ở đây đều phải gửi kèm một {@code JumpCommand}
 * và server mới là nơi quyết định thật ({@code WorldFacade#handleJumpIntent} kiểm lại y hệt các
 * điều kiện này). Sai lệch được sửa qua {@code MoveResponse}.
 */
final class JumpInputController {

    private boolean keyWasPressed;

    /** Đã giữ nút liên tục bao lâu, giây — về 0 mỗi lần nhả. Nền tảng của cú nhảy thứ hai "giữ nút". */
    private float holdSeconds;

    /**
     * Gọi mỗi tick logic.
     *
     * @param jumpPressed nút nhảy đang được giữ hay không (trạng thái, không phải sự kiện).
     * @param position vị trí của CHÍNH người chơi — bị SỬA tại chỗ khi cú nhảy được chấp nhận.
     * @return {@code true} nếu vừa bật một cú nhảy (đất hoặc giữa không trung) — caller phải gửi
     *     {@code JumpCommand} ngay.
     */
    boolean update(boolean jumpPressed, PositionComponent position, float deltaTime) {
        boolean risingEdge = jumpPressed && !keyWasPressed;
        if (risingEdge) {
            holdSeconds = 0f;
        }
        boolean jumped = jumpPressed && tryJump(position, risingEdge);
        // Cộng SAU khi xét: khung đầu tiên của một lần nhấn phải tính là đã giữ 0s chứ không phải
        // một tick, nếu không ngưỡng giữ bị lệch đúng 1 tick ngay từ đầu.
        holdSeconds = jumpPressed ? holdSeconds + deltaTime : 0f;
        keyWasPressed = jumpPressed;
        return jumped;
    }

    /**
     * Ba đường vào một cú nhảy, tất cả gửi cùng một {@code JumpCommand} — server tự suy loại từ
     * trạng thái thật của entity, client không nói loại (Server Authority):
     *
     * <ol>
     *   <li>Vừa nhấn khi đang đứng đất → cú nhảy thường.
     *   <li><b>GIỮ NGUYÊN nút</b> quá {@link WorldConstants#AIR_JUMP_HOLD_SECONDS} khi đang lơ lửng
     *       → cú thứ hai, cao hơn. Đây đúng là thao tác chủ dự án mô tả ("giữ yên nút nhảy một lúc
     *       sẽ nhảy lần 2 và nhảy được cao hơn").
     *   <li>Nhả rồi bấm lại giữa không trung → cũng ra cú thứ hai, không bắt chờ hết ngưỡng giữ. Bỏ
     *       đường này thì người quen platformer bấm nhịp hai sẽ tưởng game bị đơ.
     * </ol>
     *
     * @param risingEdge {@code true} nếu đây là khung VỪA nhấn; {@code false} = đang giữ tiếp.
     */
    private boolean tryJump(PositionComponent position, boolean risingEdge) {
        if (position.grounded) {
            if (!risingEdge) {
                return false; // giữ nút khi đã đáp đất KHÔNG tự nhảy lại — phải nhả ra rồi bấm.
            }
            position.grounded = false;
            position.vz = WorldConstants.JUMP_VELOCITY_MPS;
            // Phòng thủ: VerticalMovementSystem đã xoá hạn mức lúc đáp đất, đặt lại ở đây để cú
            // nhảy đầu tiên sau khi vào world (chưa đáp đất lần nào) cũng bắt đầu từ 0.
            position.airJumpsUsed = 0;
            return true;
        }
        if (position.airJumpsUsed >= WorldConstants.MAX_AIR_JUMPS
                || !(risingEdge || holdSeconds >= WorldConstants.AIR_JUMP_HOLD_SECONDS)) {
            return false;
        }
        position.airJumpsUsed++;
        position.vz = WorldConstants.AIR_JUMP_VELOCITY_MPS; // GHI ĐÈ, không cộng dồn — đúng như server.
        return true;
    }
}
