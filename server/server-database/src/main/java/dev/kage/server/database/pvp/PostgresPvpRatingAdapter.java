package dev.kage.server.database.pvp;

import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link PvpRatingPort} trên PostgreSQL — bảng {@code pvp_rating} (V18).
 * {@link #recordMatchResult} ghi CẢ 2 dòng (thắng+thua) nguyên tử — "commit khi xong / rollback khi
 * lỗi" thuần tuý, không trả giá trị, nên dùng {@link JdbcTransactionRunner#run} có sẵn thay vì tự
 * lặp lại khung transaction thủ công.
 */
public final class PostgresPvpRatingAdapter implements PvpRatingPort {

    private static final String FIND_BY_CHARACTER_ID = """
            SELECT character_id, rating, wins, losses, updated_at FROM pvp_rating WHERE character_id = ?
            """;

    // ON CONFLICT: rating GHI ĐÈ (giá trị mới đã tính đủ, không cộng dồn); wins/losses CỘNG DỒN
    // (delta 0/1 theo winner/loser — xem lời gọi ở recordMatchResult).
    private static final String UPSERT_RATING = """
            INSERT INTO pvp_rating (character_id, rating, wins, losses, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (character_id) DO UPDATE
            SET rating = EXCLUDED.rating,
                wins = pvp_rating.wins + EXCLUDED.wins,
                losses = pvp_rating.losses + EXCLUDED.losses,
                updated_at = EXCLUDED.updated_at
            """;

    private final DataSource dataSource;

    public PostgresPvpRatingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PvpRating> findByCharacterId(long characterId) {
        return JdbcOperations.queryOne(
                dataSource, FIND_BY_CHARACTER_ID, PostgresPvpRatingAdapter::mapRating, characterId);
    }

    @Override
    public void recordMatchResult(
            long winnerCharacterId, int winnerNewRating, long loserCharacterId, int loserNewRating) {
        JdbcTransactionRunner.run(dataSource, connection -> {
            upsert(connection, winnerCharacterId, new RatingDelta(winnerNewRating, 1, 0));
            upsert(connection, loserCharacterId, new RatingDelta(loserNewRating, 0, 1));
        });
    }

    /** Gộp tham số UPSERT — tránh vượt giới hạn 4 tham số/method (STATIC-ANALYSIS.md). */
    private record RatingDelta(int newRating, int winsDelta, int lossesDelta) {
    }

    private static void upsert(Connection connection, long characterId, RatingDelta delta) {
        JdbcOperations.update(
                connection,
                UPSERT_RATING,
                characterId,
                delta.newRating(),
                delta.winsDelta(),
                delta.lossesDelta(),
                Timestamp.from(Instant.now()));
    }

    private static PvpRating mapRating(ResultSet rs) throws SQLException {
        return new PvpRating(
                rs.getLong("character_id"), rs.getInt("rating"), rs.getInt("wins"), rs.getInt("losses"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
