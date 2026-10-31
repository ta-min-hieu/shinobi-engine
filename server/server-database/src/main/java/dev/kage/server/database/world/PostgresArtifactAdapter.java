package dev.kage.server.database.world;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.world.domain.ArtifactPort;

/** Hiện thực {@link ArtifactPort} (ADR-127, V107). */
public final class PostgresArtifactAdapter implements ArtifactPort {

    private static final String SKILLS = """
            SELECT item_template_id, effect, magnitude_percent, duration_seconds, cooldown_seconds FROM artifact_skill
            """;

    private final DataSource dataSource;

    public PostgresArtifactAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<Long, ArtifactSkill> skills() {
        return JdbcOperations.query(
                        dataSource,
                        SKILLS,
                        rs -> new ArtifactSkill(
                                rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)))
                .stream()
                .collect(Collectors.toUnmodifiableMap(ArtifactSkill::itemTemplateId, Function.identity()));
    }
}
