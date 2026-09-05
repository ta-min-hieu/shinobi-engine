package dev.kage.server.database.quest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import javax.sql.DataSource;

import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link QuestTemplatePort} trên PostgreSQL — đọc thuần, {@code quest_template} là config tĩnh. */
public final class PostgresQuestTemplateAdapter implements QuestTemplatePort {

    private static final String FIND_BY_CODE = """
            SELECT id, script_ref, prerequisite_quest_id FROM quest_template WHERE code = ?
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
        return JdbcOperations.queryOne(dataSource, FIND_BY_CODE, PostgresQuestTemplateAdapter::toTemplate, questCode);
    }

    /**
     * ADR-044 — {@code prerequisite_quest_id} nullable trong DB. {@code getLong} trả 0 cho NULL nên
     * PHẢI hỏi {@code wasNull()} ngay sau đó: 0 vừa là "không có" vừa là một id hợp lệ về mặt kiểu,
     * và nhầm hai thứ đó lại là chặn nhầm mọi quest không có điều kiện mở khoá.
     */
    private static QuestTemplate toTemplate(ResultSet rs) throws SQLException {
        long prerequisite = rs.getLong("prerequisite_quest_id");
        // wasNull() nói về lần ĐỌC CỘT GẦN NHẤT, nên phải chụp lấy NGAY, trước mọi getXxx khác.
        // Bản đầu của hàm này gọi nó bên trong lời gọi constructor — sau getLong("id") và
        // getString("script_ref") — nên nó trả lời về `script_ref` (không bao giờ NULL) và MỌI quest
        // đều trông như có tiên quyết id=0 ⇒ chặn sạch cả quest mở màn. 8 test bắt được ngay.
        boolean noPrerequisite = rs.wasNull();
        return new QuestTemplate(
                rs.getLong("id"),
                rs.getString("script_ref"),
                noPrerequisite ? OptionalLong.empty() : OptionalLong.of(prerequisite));
    }

    @Override
    public List<String> listAllScriptRefs() {
        return JdbcOperations.query(dataSource, LIST_ALL_SCRIPT_REFS, rs -> rs.getString("script_ref"));
    }
}
