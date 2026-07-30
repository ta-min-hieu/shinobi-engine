package dev.kage.server.database.quest;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link QuestTemplatePort} trên PostgreSQL — đọc thuần, {@code quest_template} là config tĩnh. */
public final class PostgresQuestTemplateAdapter implements QuestTemplatePort {

    private static final String FIND_BY_CODE = """
            SELECT id, script_ref FROM quest_template WHERE code = ?
            """;

    private static final String LIST_ALL_SCRIPT_REFS = """
            SELECT DISTINCT script_ref FROM quest_template
            """;

    private final DataSource dataSource;

    public PostgresQuestTemplateAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<QuestTemplate> findByCode(String questCode) {
        return JdbcOperations.queryOne(
                dataSource,
                FIND_BY_CODE,
                rs -> new QuestTemplate(rs.getLong("id"), rs.getString("script_ref")),
                questCode);
    }

    @Override
    public List<String> listAllScriptRefs() {
        return JdbcOperations.query(dataSource, LIST_ALL_SCRIPT_REFS, rs -> rs.getString("script_ref"));
    }
}
