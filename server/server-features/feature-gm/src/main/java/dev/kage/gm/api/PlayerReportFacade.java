package dev.kage.gm.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import dev.kage.gm.domain.PlayerReport;
import dev.kage.gm.domain.PlayerReportPort;
import dev.kage.gm.domain.ReportOutcome;

/** API báo cáo người chơi (ADR-105). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class PlayerReportFacade {

    /** Mốc "ngày" của trần báo cáo — cùng giờ Việt Nam với nhiệm vụ ngày. */
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Không lưu gì (test). */
    public static final PlayerReportFacade DISABLED = new PlayerReportFacade(PlayerReportPort.NONE, Clock.systemUTC());

    private final PlayerReportPort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public PlayerReportFacade(PlayerReportPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    /** Người chơi báo cáo người khác — ghi chú bị cắt về {@link PlayerReport#MAX_NOTE_LENGTH}. */
    public ReportOutcome file(long reporterId, long targetId, PlayerReport.Reason reason, String note) {
        if (reporterId == targetId) {
            return ReportOutcome.SELF;
        }
        Instant now = clock.instant();
        String trimmed = note.strip();
        if (trimmed.length() > PlayerReport.MAX_NOTE_LENGTH) {
            trimmed = trimmed.substring(0, PlayerReport.MAX_NOTE_LENGTH);
        }
        Instant dayStart =
                LocalDate.ofInstant(now, VIETNAM).atStartOfDay(VIETNAM).toInstant();
        return port.file(new PlayerReportPort.Filing(reporterId, targetId, reason, trimmed, now, dayStart));
    }

    /** Báo cáo chưa xử lý, mới nhất trước. */
    public List<PlayerReport> open(int limit) {
        return port.open(limit);
    }

    /** Một báo cáo. */
    public Optional<PlayerReport> find(long reportId) {
        return port.find(reportId);
    }

    /** GM đánh dấu đã xử lý. */
    public boolean resolve(long reportId, long handledBy) {
        return port.resolve(reportId, handledBy, clock.instant());
    }
}
