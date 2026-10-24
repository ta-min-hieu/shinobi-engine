package dev.kage.server.database.friend;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.friend.domain.MentorshipOutcome;
import dev.kage.friend.domain.MentorshipPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link MentorshipPort} (ADR-093, V85). Trần đồ đệ giữ bằng khoá dòng {@code character} của SƯ PHỤ: hai lời
 * nhận song song xếp hàng sau khoá đó rồi mới đếm, nên không vượt trần.
 */
public final class PostgresMentorshipAdapter implements MentorshipPort {

    private static final String MENTOR_OF = "SELECT mentor_id FROM mentorship WHERE disciple_id = ?";

    private static final String DISCIPLES_OF =
            "SELECT disciple_id FROM mentorship WHERE mentor_id = ? ORDER BY created_at, disciple_id";

    private static final String LOCK_MENTOR = "SELECT id FROM character WHERE id = ? FOR UPDATE";

    private static final String COUNT_DISCIPLES = "SELECT COUNT(*) FROM mentorship WHERE mentor_id = ?";

    private static final String INSERT = """
            INSERT INTO mentorship (disciple_id, mentor_id) VALUES (?, ?) ON CONFLICT (disciple_id) DO NOTHING
            """;

    private static final String DELETE_PAIR = """
            DELETE FROM mentorship WHERE (disciple_id = ? AND mentor_id = ?) OR (disciple_id = ? AND mentor_id = ?)
            """;

    private static final String DELETE_DISCIPLE = "DELETE FROM mentorship WHERE disciple_id = ?";

    private static final String CLAIM = """
            INSERT INTO mentorship_milestone (disciple_id, milestone, mentor_id, rewarded) VALUES (?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private final DataSource dataSource;

    public PostgresMentorshipAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Long> mentorOf(long discipleId) {
        return JdbcOperations.queryOne(dataSource, MENTOR_OF, rs -> rs.getLong(1), discipleId);
    }

    @Override
    public List<Long> disciplesOf(long mentorId) {
        return JdbcOperations.query(dataSource, DISCIPLES_OF, rs -> rs.getLong(1), mentorId);
    }

    @Override
    public MentorshipOutcome bind(long mentorId, long discipleId, int maxDisciples) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> bindIn(connection, mentorId, discipleId, maxDisciples));
    }

    private static MentorshipOutcome bindIn(Connection connection, long mentorId, long discipleId, int maxDisciples) {
        JdbcOperations.lock(connection, LOCK_MENTOR, mentorId);
        long count = JdbcOperations.queryOne(connection, COUNT_DISCIPLES, rs -> rs.getLong(1), mentorId)
                .orElse(0L);
        if (count >= maxDisciples) {
            return MentorshipOutcome.MENTOR_FULL;
        }
        return JdbcOperations.update(connection, INSERT, discipleId, mentorId) == 0
                ? MentorshipOutcome.ALREADY_HAS_MENTOR
                : MentorshipOutcome.OK;
    }

    @Override
    public boolean unbind(long firstId, long secondId) {
        return JdbcOperations.update(dataSource, DELETE_PAIR, firstId, secondId, secondId, firstId) > 0;
    }

    @Override
    public boolean claimMilestone(long discipleId, long mentorId, int milestone) {
        return JdbcOperations.update(dataSource, CLAIM, discipleId, milestone, mentorId, true) > 0;
    }

    @Override
    public void markMilestonesPassed(long discipleId, long mentorId, List<Integer> milestones) {
        if (milestones.isEmpty()) {
            return;
        }
        JdbcTransactionRunner.run(dataSource, connection -> {
            List<Object[]> rows = new ArrayList<>(milestones.size());
            for (int milestone : milestones) {
                rows.add(new Object[] {discipleId, milestone, mentorId, false});
            }
            JdbcOperations.batchUpdate(connection, CLAIM, rows);
        });
    }

    @Override
    public void graduate(long discipleId) {
        JdbcOperations.update(dataSource, DELETE_DISCIPLE, discipleId);
    }
}
