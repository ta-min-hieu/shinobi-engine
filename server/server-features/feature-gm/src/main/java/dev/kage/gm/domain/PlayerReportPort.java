package dev.kage.gm.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Báo cáo người chơi (ADR-105) — hiện thực ở server-database (V89). */
public interface PlayerReportPort {

    /**
     * Một báo cáo mới.
     *
     * @param dayStart đầu ngày (giờ Việt Nam) chứa {@code at} — để đếm trần mỗi ngày
     */
    record Filing(
            long reporterId, long targetId, PlayerReport.Reason reason, String note, Instant at, Instant dayStart) {}

    /** Không lưu gì (test). */
    PlayerReportPort NONE = new PlayerReportPort() {
        @Override
        public ReportOutcome file(Filing filing) {
            return ReportOutcome.OK;
        }

        @Override
        public List<PlayerReport> open(int limit) {
            return List.of();
        }

        @Override
        public Optional<PlayerReport> find(long reportId) {
            return Optional.empty();
        }

        @Override
        public boolean resolve(long reportId, long handledBy, Instant at) {
            return false;
        }
    };

    /** Ghi báo cáo — trần ngày + trùng người kiểm trong cùng giao dịch, trích chat ngay lúc ghi. */
    ReportOutcome file(Filing filing);

    /** Các báo cáo chưa xử lý, mới nhất trước. */
    List<PlayerReport> open(int limit);

    /** Một báo cáo theo id. */
    Optional<PlayerReport> find(long reportId);

    /**
     * Đánh dấu đã xử lý.
     *
     * @return {@code false} nếu không có hoặc đã xử lý rồi
     */
    boolean resolve(long reportId, long handledBy, Instant at);
}
