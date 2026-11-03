package dev.kage.gm.domain;

import java.time.Instant;
import java.util.List;

/**
 * Thư bồi thường hàng loạt (ADR-165) — sổ các đợt bồi thường. Việc gửi thư nằm ở gateway (feature-mail); port này chỉ
 * giữ đợt, danh sách người nhận và lúc xong.
 */
public interface CompensationPort {

    /** Một đợt: phần quà (vàng và/hoặc một chồng đồ — {@code itemTemplateId == 0} là không có đồ) + cấp tối thiểu. */
    record Campaign(String code, long gold, long itemTemplateId, int quantity, int minLevel) {}

    /** Mở đợt. */
    enum Start {
        /** Đợt mới. */
        NEW,
        /** Đợt cùng mã, cùng phần quà, chưa xong — chạy tiếp (khoá chống trùng của thư chặn gửi lại). */
        RESUME,
        /** Đợt cùng mã đã xong — không chạy lại. */
        DONE,
        /** Mã đã dùng cho phần quà KHÁC. */
        CONFLICT
    }

    /** Ghi đợt mới, hoặc nhận ra đợt cũ cùng mã. */
    Start begin(Campaign campaign, long createdBy, Instant at);

    /** Id mọi nhân vật cấp ≥ {@code minLevel}, tăng dần. */
    List<Long> recipients(int minLevel);

    /** Chốt đợt: số thư đã gửi trong lần chạy này cộng dồn, lúc xong. */
    void finish(String code, int sent, Instant at);
}
