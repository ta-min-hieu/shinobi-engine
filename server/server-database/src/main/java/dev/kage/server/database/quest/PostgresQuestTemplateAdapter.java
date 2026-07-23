package dev.kage.server.database.quest;

import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/** Hiện thực {@link QuestTemplatePort} trên PostgreSQL — đọc thuần, {@code quest_template} là config tĩnh. */
public final class PostgresQuestTemplateAdapter implements QuestTemplatePort {

    private static final String FIND_BY_CODE = """
            SELECT id, script_ref FROM quest_template WHERE code = ?
            """;

    private final DataSource dataSource;

    public PostgresQuestTemplateAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<QuestTemplate> findByCode(String questCode) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_CODE)) {
            statement.setString(1, questCode);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new QuestTemplate(rs.getLong("id"), rs.getString("script_ref")));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc quest_template: questCode=" + questCode, e);
        }
    }
}
