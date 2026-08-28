package dev.kage.server.database.quest;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

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

    // JOIN quest_template để trả quest_code (khoá client dùng) thay vì quest_template_id —
    // ClaimQuestRewardCommand nhận code. KHÔNG select progress_json: chuỗi đục do script tự quản,
    // cố ý không đi qua dây (ADR-035 Decision-4).
    private static final String LIST_LOG = """
            SELECT t.code AS quest_code, p.status
              FROM character_quest_progress p
              JOIN quest_template t ON t.id = p.quest_template_id
             WHERE p.character_id = ?
             ORDER BY t.code
            """;

    private final DataSource dataSource;

    public PostgresQuestProgressAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<QuestProgress> find(long characterId, long questTemplateId) {
        return JdbcOperations.queryOne(
                dataSource,
                FIND,
                rs -> new QuestProgress(
                        characterId,
                        questTemplateId,
                        QuestStatus.valueOf(rs.getString("status")),
                        rs.getString("progress_json")),
                characterId,
                questTemplateId);
    }

    @Override
    public List<QuestLogEntry> listLog(long characterId) {
        return JdbcOperations.query(
                dataSource,
                LIST_LOG,
                rs -> new QuestLogEntry(rs.getString("quest_code"), QuestStatus.valueOf(rs.getString("status"))),
                characterId);
    }

    @Override
    public void save(Transaction tx, QuestProgress progress) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.update(
                connection,
                UPSERT,
                progress.characterId(),
                progress.questTemplateId(),
                progress.status().name(),
                progress.progressJson(),
                Timestamp.from(Instant.now()));
    }
}
