package dev.kage.checkin.domain;

import java.util.List;
import java.util.Optional;

/** Lưu trữ điểm danh (ADR-060) — hiện thực ở server-database. */
public interface CheckInPort {

    /** Trạng thái hiện tại; rỗng nếu chưa từng điểm danh. */
    Optional<CheckInState> find(long characterId);

    /**
     * Compare-and-set: ghi {@code next} CHỈ KHI trạng thái đang là {@code expected} ({@link
     * CheckInState#NEVER} = chưa có dòng nào).
     *
     * @return {@code false} nếu một lượt khác đã đổi trước — caller coi như hôm nay đã nhận
     */
    boolean advance(long characterId, CheckInState expected, CheckInState next);

    /** Bảng quà, đúng {@link CheckInCycle#DAYS} dòng, xếp theo ngày. */
    List<CheckInReward> rewards();
}
