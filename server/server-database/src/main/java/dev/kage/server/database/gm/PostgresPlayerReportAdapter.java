package dev.kage.server.database.gm;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.gm.domain.PlayerReport;
import dev.kage.gm.domain.PlayerReportPort;
import dev.kage.gm.domain.ReportOutcome;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link PlayerReportPort} (ADR-105, V89). Khoá dòng {@code character} của người báo cáo để hai báo cáo cùng
 * lúc không cùng lọt qua trần ngày. Trích chat bằng một câu {@code INSERT … SELECT} từ {@code audit_log}
 * ({@code CHAT_MESSAGE}) — bằng chứng chụp ngay lúc báo cáo, janitor dọn audit sau đó cũng không mất.
 */
public final class PostgresPlayerReportAdapter implements PlayerReportPort {

    private static final String LOCK_REPORTER = "SELECT id FROM character WHERE id = ? FOR NO KEY UPDATE";

    private static final String COUNT_TODAY = """
            SELECT COUNT(*), COUNT(*) FILTER (WHERE target_id = ?) FROM player_report
             WHERE reporter_id = ? AND created_at >= ?
            """;

    private static final String INSERT = """
            INSERT INTO player_report (reporter_id, target_id, reason, note, chat_excerpt, created_at)
            SELECT ?, ?, ?, ?, COALESCE(json_agg(recent.detail_json ORDER BY recent.id)::text, '[]'), ?
              FROM (SELECT id, detail_json::json AS detail_json FROM audit_log
                     WHERE character_id = ? AND action = 'CHAT_MESSAGE'
                     ORDER BY id DESC LIMIT ?) AS recent
            """;

    private static final String COLUMNS =
            "SELECT id, reporter_id, target_id, reason, note, chat_excerpt, created_at FROM player_report";

    private static final String OPEN = COLUMNS + " WHERE handled_at IS NULL ORDER BY created_at DESC, id DESC LIMIT ?";

    private static final String FIND = COLUMNS + " WHERE id = ?";

    private static final String RESOLVE =
            "UPDATE player_report SET handled_at = ?, handled_by = ? WHERE id = ? AND handled_at IS NULL";

    private final DataSource dataSource;

    public PostgresPlayerReportAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Hai con số đếm trong ngày. */
    private record Today(long total, long sameTarget) {}

    @Override
    public ReportOutcome file(Filing filing) {
        return JdbcTransactionRunner.call(dataSource, connection -> fileIn(connection, filing));
    }

    private static ReportOutcome fileIn(Connection connection, Filing filing) {
        JdbcOperations.lock(connection, LOCK_REPORTER, filing.reporterId());
        Today today = JdbcOperations.queryOne(
                        connection,
                        COUNT_TODAY,
                        rs -> new Today(rs.getLong(1), rs.getLong(2)),
                        filing.targetId(),
                        filing.reporterId(),
                        Timestamp.from(filing.dayStart()))
                .orElse(new Today(0, 0));
        if (today.sameTarget() > 0) {
            return ReportOutcome.ALREADY_REPORTED;
        }
        if (today.total() >= PlayerReport.MAX_PER_DAY) {
            return ReportOutcome.DAILY_LIMIT;
        }
        JdbcOperations.update(
                connection,
                INSERT,
                filing.reporterId(),
                filing.targetId(),
                filing.reason().name(),
                filing.note(),
                Timestamp.from(filing.at()),
                filing.targetId(),
                PlayerReport.EXCERPT_MESSAGES);
        return ReportOutcome.OK;
    }

    @Override
    public List<PlayerReport> open(int limit) {
        return JdbcOperations.query(dataSource, OPEN, PostgresPlayerReportAdapter::map, limit);
    }

    @Override
    public Optional<PlayerReport> find(long reportId) {
        return JdbcOperations.queryOne(dataSource, FIND, PostgresPlayerReportAdapter::map, reportId);
    }

    @Override
    public boolean resolve(long reportId, long handledBy, Instant at) {
        return JdbcOperations.update(dataSource, RESOLVE, Timestamp.from(at), handledBy, reportId) > 0;
    }

    private static PlayerReport map(ResultSet rs) throws SQLException {
        return new PlayerReport(
                rs.getLong(1),
                rs.getLong(2),
                rs.getLong(3),
                PlayerReport.Reason.valueOf(rs.getString(4)),
                rs.getString(5),
                rs.getString(6),
                rs.getTimestamp(7).toInstant());
    }
}
