package dev.kage.server.database.dialogue;

import dev.kage.dialogue.domain.NpcTemplatePort;
import dev.kage.server.database.JdbcOperations;
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
        return JdbcOperations.queryOne(
                dataSource, FIND_DIALOGUE_SCRIPT_REF_BY_CODE, rs -> rs.getString("dialogue_script_ref"), npcCode);
    }

    @Override
    public List<String> listAllDialogueScriptRefs() {
        return JdbcOperations.query(
                dataSource, LIST_ALL_DIALOGUE_SCRIPT_REFS, rs -> rs.getString("dialogue_script_ref"));
    }
}
