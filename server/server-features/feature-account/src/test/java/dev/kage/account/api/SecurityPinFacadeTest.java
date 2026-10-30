package dev.kage.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.account.domain.SecurityPinPort;
import dev.kage.account.domain.SecurityPinRules;

class SecurityPinFacadeTest {

    private static final long ACCOUNT = 7L;
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private final FakePins pins = new FakePins();
    private final SecurityPinFacade facade = new SecurityPinFacade(pins);

    @Test
    @DisplayName("PIN chỉ nhận 4–6 chữ số")
    void formatIsFourToSixDigits() {
        assertThat(SecurityPinRules.isValidFormat("1234")).isTrue();
        assertThat(SecurityPinRules.isValidFormat("123456")).isTrue();
        assertThat(SecurityPinRules.isValidFormat("123")).isFalse();
        assertThat(SecurityPinRules.isValidFormat("1234567")).isFalse();
        assertThat(SecurityPinRules.isValidFormat("12a4")).isFalse();
        assertThat(SecurityPinRules.isValidFormat("١٢٣٤")).isFalse(); // chữ số không phải ASCII
        assertThat(SecurityPinRules.isValidFormat(null)).isFalse();
    }

    @Test
    @DisplayName("Đặt PIN lần đầu không cần PIN cũ; chỉ lưu băm, không lưu PIN thô")
    void firstPinNeedsNoCurrentPin() {
        assertThat(facade.set(ACCOUNT, "", "2468", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.OK);

        assertThat(facade.status(ACCOUNT, NOW).hasPin()).isTrue();
        assertThat(pins.stored.get(ACCOUNT).hashHex()).hasSize(64).doesNotContain("2468");
        assertThat(facade.verify(ACCOUNT, "2468", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.OK);
    }

    @Test
    @DisplayName("Đổi PIN phải đưa đúng PIN cũ")
    void changingPinRequiresCurrentPin() {
        facade.set(ACCOUNT, "", "2468", NOW);

        assertThat(facade.set(ACCOUNT, "1111", "1357", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.WRONG_PIN);
        assertThat(facade.set(ACCOUNT, "2468", "1357", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.OK);
        assertThat(facade.verify(ACCOUNT, "1357", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.OK);
    }

    @Test
    @DisplayName("Sai 5 lần liên tiếp ⇒ khoá 30 phút, kể cả PIN đúng; hết khoá thì mở lại")
    void fiveWrongAttemptsLockForThirtyMinutes() {
        facade.set(ACCOUNT, "", "2468", NOW);
        for (int i = 1; i < SecurityPinRules.MAX_FAILED_ATTEMPTS; i++) {
            SecurityPinFacade.Result wrong = facade.verify(ACCOUNT, "0000", NOW);
            assertThat(wrong.outcome()).isEqualTo(SecurityPinFacade.Outcome.WRONG_PIN);
            assertThat(wrong.attemptsLeft()).isEqualTo(SecurityPinRules.MAX_FAILED_ATTEMPTS - i);
        }

        SecurityPinFacade.Result locked = facade.verify(ACCOUNT, "0000", NOW);
        assertThat(locked.outcome()).isEqualTo(SecurityPinFacade.Outcome.LOCKED);
        assertThat(locked.lockedUntil()).isEqualTo(NOW.plus(SecurityPinRules.LOCK_DURATION));
        assertThat(facade.verify(ACCOUNT, "2468", NOW.plusSeconds(60)).outcome())
                .isEqualTo(SecurityPinFacade.Outcome.LOCKED);

        Instant later = NOW.plus(SecurityPinRules.LOCK_DURATION).plusSeconds(1);
        assertThat(facade.verify(ACCOUNT, "2468", later).outcome()).isEqualTo(SecurityPinFacade.Outcome.OK);
        assertThat(pins.stored.get(ACCOUNT).failedAttempts()).isZero();
    }

    @Test
    @DisplayName("Hết khoá rồi sai lại thì đếm lại từ đầu, không khoá ngay")
    void failuresRestartAfterLockExpires() {
        facade.set(ACCOUNT, "", "2468", NOW);
        for (int i = 0; i < SecurityPinRules.MAX_FAILED_ATTEMPTS; i++) {
            facade.verify(ACCOUNT, "0000", NOW);
        }
        Instant later = NOW.plus(SecurityPinRules.LOCK_DURATION).plusSeconds(1);

        SecurityPinFacade.Result again = facade.verify(ACCOUNT, "0000", later);

        assertThat(again.outcome()).isEqualTo(SecurityPinFacade.Outcome.WRONG_PIN);
        assertThat(again.attemptsLeft()).isEqualTo(SecurityPinRules.MAX_FAILED_ATTEMPTS - 1);
    }

    @Test
    @DisplayName("Kiểm khi chưa có PIN ⇒ NO_PIN; PIN sai dạng ⇒ INVALID_FORMAT")
    void verifyWithoutPinAndBadFormat() {
        assertThat(facade.verify(ACCOUNT, "2468", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.NO_PIN);
        assertThat(facade.set(ACCOUNT, "", "12", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.INVALID_FORMAT);
    }

    @Test
    @DisplayName("GM xoá PIN ⇒ tài khoản về trạng thái chưa có PIN")
    void gmClearRemovesPin() {
        facade.set(ACCOUNT, "", "2468", NOW);

        assertThat(facade.clear(ACCOUNT)).isTrue();
        assertThat(facade.status(ACCOUNT, NOW).hasPin()).isFalse();
        assertThat(facade.clear(ACCOUNT)).isFalse();
    }

    private static final class FakePins implements SecurityPinPort {
        private final Map<Long, StoredPin> stored = new HashMap<>();

        @Override
        public Optional<StoredPin> find(long accountId) {
            return Optional.ofNullable(stored.get(accountId));
        }

        @Override
        public void save(long accountId, String hashHex, String saltHex, Instant at) {
            stored.put(accountId, new StoredPin(hashHex, saltHex, 0, Instant.EPOCH));
        }

        @Override
        public void recordAttempts(long accountId, int failedAttempts, Instant lockedUntil) {
            StoredPin old = stored.get(accountId);
            stored.put(accountId, new StoredPin(old.hashHex(), old.saltHex(), failedAttempts, lockedUntil));
        }

        @Override
        public boolean clear(long accountId) {
            return stored.remove(accountId) != null;
        }
    }
}
