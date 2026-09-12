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

    /**
     * Compare-and-set (xem {@code QuestProgressPort.saveIfUnchanged}). Ở mức READ COMMITTED mặc
     * định, câu này chờ mọi lần ghi đang mở trên cùng dòng rồi ĐÁNH GIÁ LẠI {@code WHERE} trên bản
     * đã commit — nên lần ghi thứ hai thấy {@code progress_json} đã khác và nhận 0 dòng, thay vì ghi đè.
     */
    private static final String UPDATE_IF_UNCHANGED = """
            UPDATE character_quest_progress
               SET status = ?, progress_json = ?, updated_at = ?
             WHERE character_id = ? AND quest_template_id = ?
               AND status = 'IN_PROGRESS' AND progress_json = ?
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

    /**
     * Quest đang làm + script + tiến độ, lọc IN_PROGRESS ngay trong SQL (xem javadoc
     * {@code QuestProgressPort.listInProgress}).
     */
    private static final String LIST_IN_PROGRESS = """
            SELECT p.quest_template_id, t.code AS quest_code, t.script_ref, p.progress_json
              FROM character_quest_progress p
              JOIN quest_template t ON t.id = p.quest_template_id
             WHERE p.character_id = ? AND p.status = 'IN_PROGRESS'
             ORDER BY p.quest_template_id
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
    public List<QuestProgressPort.ActiveQuest> listInProgress(long characterId) {
        return JdbcOperations.query(
                dataSource,
                LIST_IN_PROGRESS,
                rs -> new QuestProgressPort.ActiveQuest(
                        rs.getLong("quest_template_id"),
                        rs.getString("quest_code"),
                        rs.getString("script_ref"),
                        rs.getString("progress_json")),
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

    @Override
    public boolean saveIfUnchanged(Transaction tx, QuestProgress progress, String expectedProgressJson) {
        Connection connection = ((JdbcTransaction) tx).connection();
        int updated = JdbcOperations.update(
                connection,
                UPDATE_IF_UNCHANGED,
                progress.status().name(),
                progress.progressJson(),
                Timestamp.from(Instant.now()),
                progress.characterId(),
                progress.questTemplateId(),
                expectedProgressJson);
        return updated == 1;
    }
}
