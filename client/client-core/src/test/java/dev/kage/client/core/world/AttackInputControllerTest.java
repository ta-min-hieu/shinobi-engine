package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.client.core.world.AttackInputController.Send;

/**
 * Nhịp gửi {@code AttackCommand} phía client, kể cả cửa sổ gửi lại khi server chưa xác nhận đòn của
 * chính mình (server bỏ im lặng lệnh ngoài tầm / còn cooldown — {@code CombatSystem.tryAttackNow}).
 * Không đụng GL/mạng nên test thẳng ở tốc độ tick thật.
 */
class AttackInputControllerTest {

    /** 60Hz, khớp {@code KageEngine.TICK_RATE} — cùng nhịp mà {@code WorldScreen} gọi thật. */
    private static final float TICK = 1f / 60f;

    private static final long SELF = 42L;
    private static final long OTHER_PLAYER = 7L;
    private static final long MONSTER = -100L;
    private static final long OTHER_MONSTER = -101L;

    /**
     * Số tick của một nhịp lặp giữ phím, tính bằng ĐÚNG phép trừ float mà controller dùng — 1 giây
     * trừ dần 1/60 để lại phần dư dương nên ra 61 tick, không phải 60 (cùng hiện tượng với cooldown
     * 61 tick của server). Đây là nhịp "như cũ" mà các test bên dưới đòi giữ nguyên.
     */
    private static final int INTERVAL_TICKS = ticksToDrain(CombatConstants.ATTACK_INTERVAL_SECONDS);

    /** Số tick giữa hai lần gửi lại — 100ms, cùng cách tính. */
    private static final int RETRY_TICKS_MAX = ticksToDrain(CombatConstants.ATTACK_CONFIRM_RETRY_SECONDS);

    private final AttackInputController controller = new AttackInputController(SELF);

    private static int ticksToDrain(float seconds) {
        float remaining = seconds;
        int ticks = 0;
        while (remaining > 0f) {
            remaining = Math.max(0f, remaining - TICK);
            ticks++;
        }
        return ticks;
    }

    /** Số lệnh mỗi loại đã gửi trong một quãng tick. */
    private record Counts(int intents, int retries) {}

    /** Đếm từng loại lệnh trong {@code ticks} tick liên tiếp với phím ở trạng thái {@code pressed}. */
    private Counts run(boolean pressed, int ticks, long targetId) {
        int intents = 0;
        int retries = 0;
        for (int i = 0; i < ticks; i++) {
            Send send = controller.update(pressed, TICK, targetId);
            if (send == Send.INTENT) {
                intents++;
            } else if (send == Send.RETRY) {
                retries++;
            }
        }
        return new Counts(intents, retries);
    }

    /** Chạy tới lần gửi lại đầu tiên; trả số tick đã chạy, hoặc -1 nếu không có trong {@code maxTicks}. */
    private int ticksUntilRetry(boolean pressed, int maxTicks, long targetId) {
        for (int i = 1; i <= maxTicks; i++) {
            if (controller.update(pressed, TICK, targetId) == Send.RETRY) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void shouldSendIntentImmediately_onPress() {
        assertThat(controller.update(true, TICK, MONSTER)).isEqualTo(Send.INTENT);
        assertThat(controller.isAwaitingConfirmation()).isTrue();
    }

    /** Ca gốc: bấm khi quái còn ở 2,1m — server bỏ lệnh, client phải thử lại dày tới khi thấy đòn. */
    @Test
    void shouldRetryEvery100ms_untilOwnAttackConfirmed() {
        controller.update(true, TICK, MONSTER);

        int firstRetry = ticksUntilRetry(true, RETRY_TICKS_MAX, MONSTER);
        assertThat(firstRetry).isBetween(1, RETRY_TICKS_MAX);
        int secondRetry = ticksUntilRetry(true, RETRY_TICKS_MAX, MONSTER);
        assertThat(secondRetry).isBetween(1, RETRY_TICKS_MAX);

        controller.onAttackResult(SELF);
        assertThat(controller.isAwaitingConfirmation()).isFalse();
    }

    /** Sau xác nhận, giữ phím trở lại đúng 1 lệnh/giây — không còn gói thử lại nào giữa hai nhịp. */
    @Test
    void shouldStopDenseRetries_andKeepNormalCadence_afterConfirmation() {
        controller.update(true, TICK, MONSTER);
        controller.onAttackResult(SELF);

        Counts beforeNextRepeat = run(true, INTERVAL_TICKS - 1, MONSTER);
        assertThat(beforeNextRepeat.retries()).isZero();
        assertThat(beforeNextRepeat.intents()).isZero();
        assertThat(controller.update(true, TICK, MONSTER)).isEqualTo(Send.INTENT);
    }

    /** Nhịp giữ phím (trong tầm, server xác nhận mỗi nhát ngay): đúng 1 lệnh mỗi giây, như trước. */
    @Test
    void shouldRepeatOncePerSecond_whileHeldAndEveryAttackConfirmed() {
        int intents = 0;
        int retries = 0;
        for (int i = 0; i < INTERVAL_TICKS * 5; i++) {
            Send send = controller.update(true, TICK, MONSTER);
            if (send == Send.INTENT) {
                intents++;
                controller.onAttackResult(SELF);
            } else if (send == Send.RETRY) {
                retries++;
            }
        }
        assertThat(intents).isEqualTo(5);
        assertThat(retries).isZero();
    }

    /** Nhịp xác nhận mới là mốc đo: nhát sau cách lúc THẤY xác nhận đúng một nhịp. */
    @Test
    void shouldMeasureNextRepeatFromConfirmation_notFromLastSend() {
        controller.update(true, TICK, MONSTER);
        int ticksBeforeConfirm = 20;
        run(true, ticksBeforeConfirm, MONSTER);
        controller.onAttackResult(SELF);

        Counts untilRepeat = run(true, INTERVAL_TICKS - 1, MONSTER);
        assertThat(untilRepeat.intents()).isZero();
        assertThat(untilRepeat.retries()).isZero();
        assertThat(controller.update(true, TICK, MONSTER)).isEqualTo(Send.INTENT);
    }

    /** Cooldown server 61 tick: nhịp 1s tới sớm 1 tick bị bỏ ⇒ cửa sổ thử lại phải che được. */
    @Test
    void shouldRetry_whenRepeatArrivesBeforeServerCooldownAndIsDropped() {
        controller.update(true, TICK, MONSTER);
        controller.onAttackResult(SELF);
        run(true, INTERVAL_TICKS - 1, MONSTER);
        assertThat(controller.update(true, TICK, MONSTER)).isEqualTo(Send.INTENT);

        // Server bỏ lệnh này (sớm 1 tick) — không có xác nhận ⇒ phải thử lại trong ~100ms.
        assertThat(ticksUntilRetry(true, RETRY_TICKS_MAX, MONSTER)).isPositive();
    }

    @Test
    void shouldIgnoreAttacksByOthers_asConfirmation() {
        controller.update(true, TICK, MONSTER);
        controller.onAttackResult(OTHER_PLAYER);
        controller.onAttackResult(MONSTER);

        assertThat(controller.isAwaitingConfirmation()).isTrue();
        assertThat(ticksUntilRetry(true, RETRY_TICKS_MAX, MONSTER)).isPositive();
    }

    @Test
    void shouldStopRetrying_whenKeyReleased() {
        controller.update(true, TICK, MONSTER);

        Counts afterRelease = run(false, INTERVAL_TICKS * 2, MONSTER);
        assertThat(afterRelease.retries()).isZero();
        assertThat(afterRelease.intents()).isZero();
        assertThat(controller.isAwaitingConfirmation()).isFalse();
    }

    /** Click nút không có "đang giữ" — cửa sổ của nó sống hết thời hạn rồi mới tắt. */
    @Test
    void shouldRetryAfterButtonClick_untilWindowExpires() {
        controller.onButtonClicked();
        assertThat(controller.update(false, TICK, MONSTER)).isEqualTo(Send.INTENT);

        Counts window = run(false, INTERVAL_TICKS * 3, MONSTER);
        int maxRetries = Math.round(
                CombatConstants.ATTACK_CONFIRM_WINDOW_SECONDS / CombatConstants.ATTACK_CONFIRM_RETRY_SECONDS);
        assertThat(window.retries()).isBetween(1, maxRetries);
        assertThat(window.intents()).isZero();
        assertThat(controller.isAwaitingConfirmation()).isFalse();
    }

    /** Giữ phím mãi mà quái không bao giờ tới: trần ~10 gói/giây, không bao giờ hơn. */
    @Test
    void shouldCapPacketRate_whileHeldAndNeverConfirmed() {
        Counts tenSeconds = run(true, INTERVAL_TICKS * 10, MONSTER);
        int total = tenSeconds.intents() + tenSeconds.retries();
        assertThat(tenSeconds.intents()).isEqualTo(10);
        assertThat(total).isLessThanOrEqualTo(10 * 10);
    }

    @Test
    void shouldStopRetrying_whenTargetChanges() {
        controller.update(true, TICK, MONSTER);

        Counts afterSwitch = run(true, INTERVAL_TICKS - 2, OTHER_MONSTER);
        assertThat(afterSwitch.retries()).isZero();
        assertThat(controller.isAwaitingConfirmation()).isFalse();
    }

    @Test
    void shouldStopRetrying_whenCancelled() {
        controller.update(true, TICK, MONSTER);
        controller.cancelRetry();

        Counts afterCancel = run(true, INTERVAL_TICKS - 2, MONSTER);
        assertThat(afterCancel.retries()).isZero();
    }

    /** Không có mục tiêu thì server không làm gì ⇒ không có gì để chờ, không gửi lại. */
    @Test
    void shouldNotOpenRetryWindow_withoutTarget() {
        assertThat(controller.update(true, TICK, 0L)).isEqualTo(Send.INTENT);
        assertThat(controller.isAwaitingConfirmation()).isFalse();
        assertThat(run(true, INTERVAL_TICKS - 2, 0L).retries()).isZero();
    }

    /** Xác nhận tới muộn, khi cửa sổ đã đóng, không được xô lệch nhịp lặp đang chạy. */
    @Test
    void shouldIgnoreConfirmation_whenNotAwaiting() {
        controller.update(true, TICK, MONSTER);
        controller.onAttackResult(SELF);
        run(true, INTERVAL_TICKS / 2, MONSTER);
        controller.onAttackResult(SELF);

        Counts rest = run(true, INTERVAL_TICKS - (INTERVAL_TICKS / 2) - 1, MONSTER);
        assertThat(rest.intents()).isZero();
        assertThat(controller.update(true, TICK, MONSTER)).isEqualTo(Send.INTENT);
    }
}
