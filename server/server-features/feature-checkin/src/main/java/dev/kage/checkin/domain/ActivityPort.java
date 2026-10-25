package dev.kage.checkin.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lịch hoạt động + điểm năng động (ADR-106) — hiện thực ở server-database (V89). */
public interface ActivityPort {

    /** Một việc hằng ngày (tham số, Điều 27). */
    record Definition(String code, String nameKey, int pointsEach, int maxCount) {}

    /** Một rương mốc. {@code itemTemplateId <= 0} = chỉ vàng. */
    record Chest(int milestone, long gold, long itemTemplateId, int quantity) {}

    /** Mọi việc, theo thứ tự hiển thị. */
    List<Definition> definitions();

    /** Mọi rương, mốc tăng dần. */
    List<Chest> chests();

    /** Số lần đã làm mỗi việc trong ngày. */
    Map<String, Integer> progress(long characterId, LocalDate day);

    /** Cộng một lần cho việc {@code activityCode} (mã lạ: bỏ qua). */
    void record(long characterId, LocalDate day, String activityCode);

    /** Mốc đã nhận trong ngày. */
    Set<Integer> claimed(long characterId, LocalDate day);

    /**
     * Nhận rương — nguyên tử: điểm hiện tại (tính lại trong giao dịch) phải ≥ mốc, khoá chính chặn nhận đôi.
     *
     * @return rương vừa nhận; rỗng nếu chưa đủ điểm, đã nhận, hoặc mốc lạ
     */
    Optional<Chest> claim(long characterId, LocalDate day, int milestone, Instant at);
}
