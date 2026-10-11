package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** {@link ItemUseCooldown} với đồng hồ điều khiển được — không ngủ, không phụ thuộc tải máy. */
final class ItemUseCooldownTest {

    private static final long ONE_SECOND_NANOS = 1_000_000_000L;

    private final AtomicLong clock = new AtomicLong(5_000L);
    private final ItemUseCooldown cooldown = new ItemUseCooldown(clock::get);

    @Test
    void shouldAllowTheFirstUse_andRefuseTheNextUntilTheCooldownEnds() {
        assertThat(cooldown.tryBegin()).isNotEqualTo(ItemUseCooldown.ON_COOLDOWN);

        assertThat(cooldown.tryBegin()).as("ngay sau đó").isEqualTo(ItemUseCooldown.ON_COOLDOWN);
        clock.addAndGet(ItemUseCooldown.DURATION.toNanos() - 1);
        assertThat(cooldown.tryBegin()).as("thiếu 1 ns").isEqualTo(ItemUseCooldown.ON_COOLDOWN);

        clock.addAndGet(1);
        assertThat(cooldown.tryBegin()).as("vừa đủ hồi chiêu").isNotEqualTo(ItemUseCooldown.ON_COOLDOWN);
    }

    /** Lệnh hỏng (không có món, món không dùng được) không được phạt người chơi trọn hồi chiêu. */
    @Test
    void shouldGiveTheTurnBack_whenTheUseFailed() {
        long ticket = cooldown.tryBegin();

        cooldown.cancel(ticket);

        assertThat(cooldown.tryBegin()).isNotEqualTo(ItemUseCooldown.ON_COOLDOWN);
    }

    /**
     * Vé cũ không được huỷ lượt của lệnh KHÁC đã chiếm sau đó — nếu không, một lệnh hỏng về muộn sẽ mở
     * lại hồi chiêu mà một lệnh thành công vừa đặt.
     */
    @Test
    void shouldNotCancelALaterUse_withAnOlderTicket() {
        long first = cooldown.tryBegin();
        cooldown.cancel(first);
        clock.addAndGet(ONE_SECOND_NANOS);
        long second = cooldown.tryBegin();
        assertThat(second).isNotEqualTo(ItemUseCooldown.ON_COOLDOWN);

        cooldown.cancel(first);

        assertThat(cooldown.tryBegin()).isEqualTo(ItemUseCooldown.ON_COOLDOWN);
    }

    /** {@code nanoTime} được phép âm và được phép tràn — phép so phải đúng ở cả hai. */
    @Test
    void shouldWorkAcrossANegativeAndOverflowingClock() {
        AtomicLong wrapping = new AtomicLong(Long.MAX_VALUE - ONE_SECOND_NANOS);
        ItemUseCooldown nearOverflow = new ItemUseCooldown(wrapping::get);

        assertThat(nearOverflow.tryBegin()).isNotEqualTo(ItemUseCooldown.ON_COOLDOWN);
        wrapping.addAndGet(ONE_SECOND_NANOS * 2); // đồng hồ đã tràn sang số âm
        assertThat(nearOverflow.tryBegin()).isEqualTo(ItemUseCooldown.ON_COOLDOWN);
        wrapping.addAndGet(ItemUseCooldown.DURATION.toNanos());
        assertThat(nearOverflow.tryBegin()).isNotEqualTo(ItemUseCooldown.ON_COOLDOWN);
    }
}
