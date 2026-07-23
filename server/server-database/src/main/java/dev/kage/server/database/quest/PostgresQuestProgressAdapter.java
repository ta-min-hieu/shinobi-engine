package dev.kage.server.database.quest;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

/** Hiện thực {@link QuestProgressPort} trên PostgreSQL — upsert thật theo khoá tự nhiên (PK). */
public final class PostgresQuestProgressAdapter implements QuestProgressPort {

    private static final String FIND = """
            SELECT status, progress_json FROM character_quest_progress
             WHERE character_id = ? AND quest_template_id = ?
            """;

    private static final String UPSERT = """
            INSERT INTO character_quest_progress (character_id, quest_template_id, status, progress_json, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (character_id, quest_template_id)
            DO UPDATE SET status = EXCLUDED.status, progress_json = EXCLUDED.progress_json,
                          updated_at = EXCLUDED.updated_at
            """;

    private final DataSource dataSource;

    public PostgresQuestProgressAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<QuestProgress> find(long characterId, long questTemplateId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND)) {
            statement.setLong(1, characterId);
            statement.setLong(2, questTemplateId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                var status = QuestStatus.valueOf(rs.getString("status"));
                var progressJson = rs.getString("progress_json");
                return Optional.of(new QuestProgress(characterId, questTemplateId, status, progressJson));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi đọc character_quest_progress: characterId=" + characterId, e);
        }
    }

    @Override
    public void save(Transaction tx, QuestProgress progress) {
        var connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setLong(1, progress.characterId());
            statement.setLong(2, progress.questTemplateId());
            statement.setString(3, progress.status().name());
            statement.setString(4, progress.progressJson());
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi lưu character_quest_progress: characterId=" + progress.characterId(), e);
        }
    }
}
