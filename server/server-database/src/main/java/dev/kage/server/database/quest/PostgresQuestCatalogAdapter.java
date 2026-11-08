package dev.kage.server.database.quest;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.quest.domain.QuestCatalogPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link QuestCatalogPort} (ADR-228) — đọc {@code quest_template} kèm mã nhiệm vụ tiên quyết. */
public final class PostgresQuestCatalogAdapter implements QuestCatalogPort {

    private static final String ALL = """
            SELECT q.code, p.code AS prerequisite, q.repeat_policy
              FROM quest_template q LEFT JOIN quest_template p ON p.id = q.prerequisite_quest_id
             ORDER BY q.id
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresQuestCatalogAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Entry> all() {
        return JdbcOperations.query(
                dataSource,
                ALL,
                rs -> new Entry(
                        rs.getString("code"),
                        Optional.ofNullable(rs.getString("prerequisite")),
                        "DAILY".equals(rs.getString("repeat_policy"))));
    }
}
