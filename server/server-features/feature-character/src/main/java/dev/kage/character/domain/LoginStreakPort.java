package dev.kage.character.domain;

import java.time.LocalDate;
import java.util.Optional;

/** Chuỗi đăng nhập liên tiếp (ADR-327) — bảng {@code login_streak}. */
public interface LoginStreakPort {

    /** Một dòng: chuỗi hiện tại, chuỗi dài nhất, ngày vào world gần nhất. */
    record Row(int streak, int best, LocalDate lastDay) {}

    /**
     * Ghi ngày vào world {@code today}; chỉ khi {@code today} sau ngày đã ghi (lần đầu trong ngày) thì trả dòng mới —
     * chuỗi +1 nếu hôm qua có vào, ngược lại về 1. Đã ghi hôm nay rồi ⇒ rỗng.
     */
    Optional<Row> advance(long characterId, LocalDate today);

    /** Dòng hiện có, rỗng nếu chưa từng ghi. */
    Optional<Row> find(long characterId);
}
