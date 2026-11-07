package dev.kage.server.database.achievement;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.achievement.domain.TitleBonusPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link TitleBonusPort} (ADR-210, V162) — bốn cột chỉ số trên bảng {@code achievement}. */
public final class PostgresTitleBonusAdapter implements TitleBonusPort {

    private static final String BONUSES = """
            SELECT code, bonus_atk, bonus_def, bonus_res, bonus_hp FROM achievement
             WHERE bonus_atk > 0 OR bonus_def > 0 OR bonus_res > 0 OR bonus_hp > 0
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresTitleBonusAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<String, TitleBonus> bonusesByCode() {
        Map<String, TitleBonus> bonuses = new HashMap<>();
        for (Map.Entry<String, TitleBonus> row : JdbcOperations.query(
                dataSource,
                BONUSES,
                rs -> Map.entry(
                        rs.getString("code"),
                        new TitleBonus(
                                rs.getInt("bonus_atk"),
                                rs.getInt("bonus_def"),
                                rs.getInt("bonus_res"),
                                rs.getInt("bonus_hp"))))) {
            bonuses.put(row.getKey(), row.getValue());
        }
        return bonuses;
    }
}
