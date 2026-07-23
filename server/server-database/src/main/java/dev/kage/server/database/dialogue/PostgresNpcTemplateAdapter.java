package dev.kage.server.database.dialogue;

import dev.kage.dialogue.domain.NpcTemplatePort;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Hiện thực {@link NpcTemplatePort} trên PostgreSQL — đọc thuần, {@code npc_template} là config tĩnh. */
public final class PostgresNpcTemplateAdapter implements NpcTemplatePort {

    private static final String FIND_DIALOGUE_SCRIPT_REF_BY_CODE = """
            SELECT dialogue_script_ref FROM npc_template WHERE code = ?
            """;

    private static final String LIST_ALL_DIALOGUE_SCRIPT_REFS = """
            SELECT DISTINCT dialogue_script_ref FROM npc_template WHERE dialogue_script_ref IS NOT NULL
            """;

    private final DataSource dataSource;

    public PostgresNpcTemplateAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<String> findDialogueScriptRefByCode(String npcCode) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_DIALOGUE_SCRIPT_REF_BY_CODE)) {
            statement.setString(1, npcCode);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("dialogue_script_ref")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc npc_template: npcCode=" + npcCode, e);
        }
    }

    @Override
    public List<String> listAllDialogueScriptRefs() {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LIST_ALL_DIALOGUE_SCRIPT_REFS);
                ResultSet rs = statement.executeQuery()) {
            var result = new ArrayList<String>();
            while (rs.next()) {
                result.add(rs.getString("dialogue_script_ref"));
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê dialogue_script_ref npc_template", e);
        }
    }
}
