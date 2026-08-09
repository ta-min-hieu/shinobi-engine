package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Luật nhảy đơn/đôi phía client (ADR-033 + chủ dự án 2026-08-09). Không đụng GL/mạng nên test được
 * thẳng ở tốc độ tick thật.
 */
class JumpInputControllerTest {

    /** 60Hz, khớp {@code KageEngine.TICK_RATE} — cùng nhịp mà {@code WorldScreen} sẽ gọi thật. */
    private static final float TICK = 1f / 60f;

    private final JumpInputController controller = new JumpInputController();
    private final PositionComponent position = new PositionComponent();

    /** Giữ nút {@code ticks} khung liên tiếp; trả về số cú nhảy đã bật trong quãng đó. */
    private int holdFor(int ticks) {
        int jumps = 0;
        for (int i = 0; i < ticks; i++) {
            if (controller.update(true, position, TICK)) {
                jumps++;
            }
        }
        return jumps;
    }

    @Test
    void shouldJump_whenPressedWhileGrounded() {
        assertThat(controller.update(true, position, TICK)).isTrue();
        assertThat(position.grounded).isFalse();
        assertThat(position.vz).isEqualTo(WorldConstants.JUMP_VELOCITY_MPS);
        assertThat(position.airJumpsUsed).isZero();
    }

    @Test
    void shouldNotJump_whenKeyNotPressed() {
        assertThat(controller.update(false, position, TICK)).isFalse();
        assertThat(position.grounded).isTrue();
    }

    /** Chính là thao tác chủ dự án mô tả: giữ yên nút một lúc → cú thứ hai, CAO HƠN cú đầu. */
    @Test
    void shouldAirJumpHigher_whenKeyHeldPastThreshold() {
        int ticksToThreshold = (int) Math.ceil(WorldConstants.AIR_JUMP_HOLD_SECONDS / TICK) + 1;

        int jumps = holdFor(ticksToThreshold);

        assertThat(jumps).isEqualTo(2); // cú đất + cú giữa không trung
        assertThat(position.airJumpsUsed).isEqualTo(WorldConstants.MAX_AIR_JUMPS);
        assertThat(position.vz).isEqualTo(WorldConstants.AIR_JUMP_VELOCITY_MPS);
        assertThat(WorldConstants.AIR_JUMP_VELOCITY_MPS).isGreaterThan(WorldConstants.JUMP_VELOCITY_MPS);
    }

    /** Giữ nút MÃI cũng chỉ được thêm đúng một cú — hạn mức, không phải bay. */
    @Test
    void shouldNotJumpAgain_whenKeyHeldFarPastThreshold() {
        int jumps = holdFor(600); // 10 giây giữ liên tục

        assertThat(jumps).isEqualTo(2);
    }

    /** Nhả rồi bấm lại giữa không trung — phản xạ platformer quen thuộc, cũng phải ra cú thứ hai. */
    @Test
    void shouldAirJump_whenReleasedAndPressedAgainMidAir() {
        assertThat(controller.update(true, position, TICK)).isTrue(); // cú đất
        controller.update(false, position, TICK); // nhả, vẫn đang lơ lửng

        assertThat(controller.update(true, position, TICK)).isTrue();
        assertThat(position.vz).isEqualTo(WorldConstants.AIR_JUMP_VELOCITY_MPS);
    }

    /** Giữ nút sau khi ĐÃ đáp đất không tự nhảy lại — phải nhả ra rồi bấm, tránh nảy liên tục. */
    @Test
    void shouldNotAutoJump_whenStillHoldingAfterLanding() {
        holdFor(30);
        // Mô phỏng VerticalMovementSystem đáp đất giữa lúc nút vẫn đang được giữ.
        position.grounded = true;
        position.vz = 0f;
        position.airJumpsUsed = 0;

        assertThat(holdFor(120)).isZero();
        assertThat(position.grounded).isTrue();
    }

    /** Hạn mức nạp lại sau khi chạm đất: lần nhảy TIẾP THEO lại được nhảy đôi. */
    @Test
    void shouldAllowAirJumpAgain_afterLandingAndReleasingKey() {
        holdFor(30);
        position.grounded = true;
        position.vz = 0f;
        position.airJumpsUsed = 0;
        controller.update(false, position, TICK); // nhả nút

        int ticksToThreshold = (int) Math.ceil(WorldConstants.AIR_JUMP_HOLD_SECONDS / TICK) + 1;
        assertThat(holdFor(ticksToThreshold)).isEqualTo(2);
    }
}
