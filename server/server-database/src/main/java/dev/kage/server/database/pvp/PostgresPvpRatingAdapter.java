package dev.kage.server.database.pvp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.pvp.domain.EloRating;
import dev.kage.pvp.domain.PvpRating;
import dev.kage.pvp.domain.PvpRatingPort;
import dev.kage.pvp.domain.PvpRatingPort.EloComputer;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link PvpRatingPort} trên PostgreSQL — bảng {@code pvp_rating} (V18).
 *
 * <p>{@link #recordDuelResult} khoá CẢ 2 dòng bằng {@code SELECT ... FOR UPDATE} TRƯỚC khi đọc rồi
 * mới gọi {@code computer} tính rating mới — đây chính là chỗ đóng lỗ race đã ghi ở javadoc
 * {@link PvpRatingPort#recordDuelResult}. Khoá theo thứ tự {@code character_id} TĂNG DẦN, KHÔNG
 * theo thắng/thua: 2 trận chồng lấn (VD A thắng liên tiếp cả B lẫn C) khoá 2 cặp {A,B}/{A,C} khác
 * nhau, nhưng nếu không cố định thứ tự thì một cặp trận hiếm gặp khoá ngược chiều nhau (trận X khoá
 * A rồi B, trận Y khoá B rồi A) vẫn có thể deadlock — khoá theo ID tăng dần loại bỏ khả năng đó
 * hoàn toàn, không cần chứng minh riêng nó có xảy ra hay không.
 */
public final class PostgresPvpRatingAdapter implements PvpRatingPort {

    private static final String FIND_BY_CHARACTER_ID = """
            SELECT character_id, rating, wins, losses, updated_at FROM pvp_rating WHERE character_id = ?
            """;

    private static final String LOCK_RATING_FOR_UPDATE = """
            SELECT rating FROM pvp_rating WHERE character_id = ? FOR UPDATE
            """;

    // ON CONFLICT: rating GHI ĐÈ (giá trị mới đã tính đủ, không cộng dồn); wins/losses CỘNG DỒN
    // (delta 0/1 theo winner/loser — xem lời gọi ở recordDuelResult). Dòng CHƯA TỒN TẠI thì
    // LOCK_RATING_FOR_UPDATE ở trên trả 0 dòng (không khoá được cái chưa có) — INSERT ở đây tự nó
    // là điểm nguyên tử THẬT SỰ cho trận ĐẦU TIÊN của 1 nhân vật (2 giao dịch cùng INSERT 1
    // character_id thì Postgres tự khoá theo unique index, một bên chờ bên kia commit).
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
    public EloRating.Result recordDuelResult(long winnerCharacterId, long loserCharacterId, EloComputer computer) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            // Khoá theo ID TĂNG DẦN (xem javadoc lớp) — không theo winner/loser.
            long firstToLock = Math.min(winnerCharacterId, loserCharacterId);
            long secondToLock = Math.max(winnerCharacterId, loserCharacterId);
            int firstLockedRating = lockAndReadRating(connection, firstToLock);
            int secondLockedRating = lockAndReadRating(connection, secondToLock);
            int winnerRating = winnerCharacterId == firstToLock ? firstLockedRating : secondLockedRating;
            int loserRating = loserCharacterId == firstToLock ? firstLockedRating : secondLockedRating;

            EloRating.Result result = computer.compute(winnerRating, loserRating);
            Timestamp now = Timestamp.from(Instant.now());
            upsert(connection, winnerCharacterId, new RatingDelta(result.newWinnerRating(), 1, 0), now);
            upsert(connection, loserCharacterId, new RatingDelta(result.newLoserRating(), 0, 1), now);
            return result;
        });
    }

    /** Khoá 1 dòng (nếu có) rồi trả rating của nó, hoặc {@link EloRating#DEFAULT_RATING} nếu chưa
     * từng đấu — PHẢI gọi bên trong transaction đã mở của {@link #recordDuelResult}, tự nó không
     * khoá được gì nếu chạy đơn lẻ ngoài transaction (autocommit nhả khoá ngay khi câu lệnh xong). */
    private static int lockAndReadRating(Connection connection, long characterId) {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_RATING_FOR_UPDATE)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("rating") : EloRating.DEFAULT_RATING;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi khoá dòng pvp_rating để đọc-tính-ghi nguyên tử", e);
        }
    }

    /** Gộp tham số UPSERT — tránh vượt giới hạn 4 tham số/method (STATIC-ANALYSIS.md). */
    private record RatingDelta(int newRating, int winsDelta, int lossesDelta) {}

    private static void upsert(Connection connection, long characterId, RatingDelta delta, Timestamp updatedAt) {
        JdbcOperations.update(
                connection,
                UPSERT_RATING,
                characterId,
                delta.newRating(),
                delta.winsDelta(),
                delta.lossesDelta(),
                updatedAt);
    }

    private static PvpRating mapRating(ResultSet rs) throws SQLException {
        return new PvpRating(
                rs.getLong("character_id"),
                rs.getInt("rating"),
                rs.getInt("wins"),
                rs.getInt("losses"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
