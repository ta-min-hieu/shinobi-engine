package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Sổ hồi chiêu qua relog (README dòng 37iii) — phần số học, dùng đồng hồ giả. */
final class SkillCooldownLedgerTest {

    private static final long CHARACTER = 7L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final AtomicLong clock = new AtomicLong(1_000L);
    private final SkillCooldownLedger ledger = new SkillCooldownLedger(clock::get);

    @Test
    void shouldGiveBackWhatIsLeft_afterTimeSpentOffline() {
        ledger.remember(CHARACTER, new float[] {0f, 8f, 0f, 60f});
        clock.addAndGet(5 * NANOS_PER_SECOND);

        float[] restored = new float[4];
        ledger.restoreInto(CHARACTER, restored);

        assertThat(restored[0]).isZero();
        assertThat(restored[1]).isCloseTo(3f, within(0.001f));
        assertThat(restored[2]).isZero();
        assertThat(restored[3]).isCloseTo(55f, within(0.001f));
    }

    @Test
    void shouldGiveBackNothing_whenTheCooldownRanOutWhileOffline() {
        ledger.remember(CHARACTER, new float[] {3f, 0f, 0f, 0f});
        clock.addAndGet(4 * NANOS_PER_SECOND);

        float[] restored = new float[4];
        ledger.restoreInto(CHARACTER, restored);

        assertThat(restored).containsOnly(0f);
    }

    @Test
    void shouldLeaveSlotsUntouched_forACharacterItNeverSaw() {
        float[] restored = new float[4];

        ledger.restoreInto(CHARACTER, restored);

        assertThat(restored).containsOnly(0f);
    }

    /** Sổ chỉ giữ người đang thật sự phải chờ — không phình theo số lượt đăng xuất. */
    @Test
    void shouldNotKeepRows_forCharactersWithNothingPending() {
        ledger.remember(CHARACTER, new float[] {0f, 0f, 0f, 0f});
        assertThat(ledger.trackedCharacterCount()).isZero();

        ledger.remember(CHARACTER, new float[] {2f, 0f, 0f, 0f});
        assertThat(ledger.trackedCharacterCount()).isEqualTo(1);

        clock.addAndGet(3 * NANOS_PER_SECOND);
        ledger.remember(CHARACTER + 1, new float[] {0f, 0f, 0f, 0f}); // lượt ghi kế tiếp dọn dòng đã hết hạn
        assertThat(ledger.trackedCharacterCount()).isZero();
    }

    /** Mặc định của World chưa được trao sổ: không nhớ gì, y như trước khi có luật. */
    @Test
    void shouldRememberNothing_whenForgetful() {
        SkillCooldownLedger forgetful = SkillCooldownLedger.forgetful();
        forgetful.remember(CHARACTER, new float[] {9f, 9f, 9f, 9f});

        float[] restored = new float[4];
        forgetful.restoreInto(CHARACTER, restored);

        assertThat(restored).containsOnly(0f);
    }
}
