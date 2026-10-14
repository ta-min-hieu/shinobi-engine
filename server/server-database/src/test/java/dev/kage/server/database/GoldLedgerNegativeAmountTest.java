package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Số vàng âm đảo chiều tiền: {@code deduct(-100)} là cộng 100 mà bỏ qua trần, {@code credit(-100)} là
 * trừ mà bỏ qua điều kiện đủ tiền (chủ dự án chặn 2026-10-14, docs/README.md dòng 25(iv)).
 *
 * <p>Không cần Postgres: lời từ chối phải tới TRƯỚC câu SQL nào, nên {@code Connection} {@code null}
 * là đủ — chạm tới nó tức là kiểm tra đã đứng sai chỗ (test sẽ ném NPE thay vì
 * {@link IllegalArgumentException}).
 */
class GoldLedgerNegativeAmountTest {

    private static final long CHARACTER_ID = 1L;

    @Test
    void shouldRejectNegativeDeduct_beforeTouchingTheDatabase() {
        assertThatThrownBy(() -> GoldLedger.deduct(null, CHARACTER_ID, -100L, "gold.reason.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeCredit_beforeTouchingTheDatabase() {
        assertThatThrownBy(() -> GoldLedger.credit(null, CHARACTER_ID, -100L, "gold.reason.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
