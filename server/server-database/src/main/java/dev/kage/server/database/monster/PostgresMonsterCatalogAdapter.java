package dev.kage.server.database.monster;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.monster.domain.MonsterAiParams;
import dev.kage.monster.domain.MonsterBossBehaviour;
import dev.kage.monster.domain.MonsterCatalogPort;
import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.monster.domain.MonsterStats;
import dev.kage.monster.domain.MonsterTemplate;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link MonsterCatalogPort} trên PostgreSQL (ADR-037) — đọc thuần,
 * {@code monster_template}/{@code monster_spawn} là config tĩnh nạp một lần lúc khởi động.
 */
public final class PostgresMonsterCatalogAdapter implements MonsterCatalogPort {

    // LEFT JOIN vì drop_table_id được phép NULL (loài không rơi gì). INNER JOIN sẽ âm thầm nuốt mất
    // cả loài đó khỏi catalog — quái biến khỏi thế giới vì không có bảng rơi là một lỗi rất khó soi.
    private static final String LIST_TEMPLATES = """
            SELECT mt.code, mt.name_key, mt.model_key, mt.level, mt.max_hp, mt.atk, mt.skill_dmg,
                   mt.def, mt.res, mt.hit_percent, mt.dodge_percent, mt.crit_percent, mt.element,
                   mt.exp_reward, mt.aggro_radius_meters, mt.leash_radius_meters, mt.chase_speed_mps,
                   mt.patrol_speed_mps, mt.patrol_radius_meters, mt.idle_duration_seconds,
                   mt.retaliates,
                   mt.is_boss, mt.ai_script_ref, mt.ai_decision_interval_seconds,
                   mt.ai_skill_cooldown_seconds,
                   dt.code AS drop_table_code
              FROM monster_template mt
              LEFT JOIN drop_table dt ON dt.id = mt.drop_table_id
             ORDER BY mt.id
            """;

    /**
     * Mọi {@code ai_script_ref} đang khai trong bảng (ADR-048) — {@code ScriptValidator} duyệt hết
     * lúc khởi động, cùng cách nó duyệt script quest và hội thoại (SCRIPTING.md §8).
     *
     * <p>Vì sao cần: trước ADR-048, {@code BossAiSystem} nạp script lúc dựng system nên một đường
     * dẫn sai làm server chết ngay. Nay script nạp LÚC BOSS SPAWN, tức lỗi sẽ nổ giữa trận đấu —
     * fail fast phải chuyển sang đây, nếu không ADR này đánh đổi một lỗi ồn lấy một lỗi im.
     */
    private static final String LIST_BOSS_SCRIPT_REFS = """
            SELECT ai_script_ref FROM monster_template
             WHERE ai_script_ref IS NOT NULL
             ORDER BY id
            """;

    private static final String LIST_SPAWN_POINTS = """
            SELECT mt.code AS monster_code, ms.world_code, ms.pos_x, ms.pos_y, ms.respawn_seconds
              FROM monster_spawn ms
              JOIN monster_template mt ON mt.id = ms.monster_template_id
             WHERE ms.world_code = ?
             ORDER BY ms.id
            """;

    private final DataSource dataSource;

    public PostgresMonsterCatalogAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<MonsterTemplate> listTemplates() {
        return JdbcOperations.query(dataSource, LIST_TEMPLATES, PostgresMonsterCatalogAdapter::mapTemplate);
    }

    /** Xem {@link #LIST_BOSS_SCRIPT_REFS}. Không thuộc {@code MonsterCatalogPort}: đây là việc của
     * composition root lúc khởi động, không phải của gameplay. */
    public List<String> listAllBossScriptRefs() {
        return JdbcOperations.query(dataSource, LIST_BOSS_SCRIPT_REFS, rs -> rs.getString("ai_script_ref"));
    }

    @Override
    public List<MonsterSpawnPoint> listSpawnPoints(String worldCode) {
        return JdbcOperations.query(
                dataSource, LIST_SPAWN_POINTS, PostgresMonsterCatalogAdapter::mapSpawnPoint, worldCode);
    }

    /**
     * Hành vi boss, hoặc {@code null} với quái FSM thường (ADR-048 D2).
     *
     * <p>KHÔNG đoán lại ràng buộc của DB ở đây: {@code monster_template_boss_needs_script} (V41) đã
     * chặn cả hai chiều sai — {@code is_boss} mà thiếu script, và script mà không phải boss — nên
     * một hàng đọc lên chỉ có hai hình dạng hợp lệ.
     */
    private static MonsterBossBehaviour mapBossBehaviour(ResultSet rs) throws SQLException {
        if (!rs.getBoolean("is_boss")) {
            return null;
        }
        return new MonsterBossBehaviour(
                rs.getString("ai_script_ref"),
                rs.getFloat("ai_decision_interval_seconds"),
                rs.getFloat("ai_skill_cooldown_seconds"));
    }

    private static MonsterTemplate mapTemplate(ResultSet rs) throws SQLException {
        MonsterStats stats = new MonsterStats(
                rs.getInt("level"),
                rs.getInt("max_hp"),
                rs.getInt("atk"),
                rs.getInt("skill_dmg"),
                rs.getInt("def"),
                rs.getInt("res"),
                rs.getInt("hit_percent"),
                rs.getInt("dodge_percent"),
                rs.getInt("crit_percent"),
                rs.getString("element"));
        return new MonsterTemplate(
                rs.getString("code"),
                rs.getString("name_key"),
                rs.getString("model_key"),
                stats,
                mapAiParams(rs),
                rs.getLong("exp_reward"),
                rs.getString("drop_table_code"),
                mapBossBehaviour(rs));
    }

    /**
     * Sáu cột AI của V25 (ADR-039 D1) cộng {@code retaliates} của V43 (ADR-049) — hai hằng CƠ CHẾ
     * còn lại cố ý không có trong bảng.
     */
    private static MonsterAiParams mapAiParams(ResultSet rs) throws SQLException {
        return new MonsterAiParams(
                rs.getFloat("aggro_radius_meters"),
                rs.getFloat("leash_radius_meters"),
                rs.getFloat("chase_speed_mps"),
                rs.getFloat("patrol_speed_mps"),
                rs.getFloat("patrol_radius_meters"),
                rs.getFloat("idle_duration_seconds"),
                rs.getBoolean("retaliates"));
    }

    private static MonsterSpawnPoint mapSpawnPoint(ResultSet rs) throws SQLException {
        return new MonsterSpawnPoint(
                rs.getString("monster_code"),
                rs.getString("world_code"),
                rs.getFloat("pos_x"),
                rs.getFloat("pos_y"),
                rs.getFloat("respawn_seconds"));
    }
}
