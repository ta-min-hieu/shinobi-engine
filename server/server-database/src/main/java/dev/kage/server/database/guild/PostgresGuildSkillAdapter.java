package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildSkill;
import dev.kage.guild.domain.GuildSkillOutcome;
import dev.kage.guild.domain.GuildSkillPort;
import dev.kage.guild.domain.GuildSkillRules;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link GuildSkillPort} (ADR-085, V81). Nâng bậc khoá dòng {@code guild} trước khi đếm điểm. */
public final class PostgresGuildSkillAdapter implements GuildSkillPort {

    private static final String MEMBERSHIP = """
            SELECT g.id, g.level, gm.role FROM guild_member gm JOIN guild g ON g.id = gm.guild_id
             WHERE gm.character_id = ?
            """;

    private static final String LOCK_GUILD = "SELECT level FROM guild WHERE id = ? FOR UPDATE";

    private static final String RANKS = "SELECT skill_code, rank FROM guild_skill WHERE guild_id = ?";

    private static final String UPSERT_RANK = """
            INSERT INTO guild_skill (guild_id, skill_code, rank) VALUES (?, ?, 1)
            ON CONFLICT (guild_id, skill_code) DO UPDATE SET rank = guild_skill.rank + 1
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private static final String LEADER_ROLE = "LEADER";

    private record Membership(long guildId, int guildLevel, boolean leader) {}

    private final DataSource dataSource;

    public PostgresGuildSkillAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<GuildSkills> skillsOf(long characterId) {
        return JdbcTransactionRunner.call(dataSource, connection -> membership(connection, characterId)
                .map(member ->
                        new GuildSkills(member.guildLevel(), member.leader(), ranks(connection, member.guildId()))));
    }

    @Override
    public GuildSkillOutcome upgrade(long leaderId, GuildSkill skill) {
        return JdbcTransactionRunner.call(dataSource, connection -> upgradeIn(connection, leaderId, skill));
    }

    private static GuildSkillOutcome upgradeIn(Connection connection, long leaderId, GuildSkill skill) {
        Optional<Membership> member = membership(connection, leaderId);
        if (member.isEmpty()) {
            return GuildSkillOutcome.NOT_IN_GUILD;
        }
        long guildId = member.get().guildId();
        int level = JdbcOperations.queryOne(connection, LOCK_GUILD, rs -> rs.getInt(1), guildId)
                .orElse(member.get().guildLevel());
        Map<GuildSkill, Integer> ranks = ranks(connection, guildId);
        Optional<GuildSkillOutcome> refusal =
                GuildSkillRules.refusal(member.get().leader(), level, ranks, skill);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        JdbcOperations.update(connection, UPSERT_RANK, guildId, skill.name());
        JdbcOperations.update(
                connection,
                AUDIT,
                leaderId,
                "GUILD_SKILL_UPGRADE",
                "{\"guildId\":" + guildId + ",\"skill\":\"" + skill.name() + "\",\"rank\":"
                        + (ranks.getOrDefault(skill, 0) + 1) + "}",
                Timestamp.from(Instant.now()));
        return GuildSkillOutcome.OK;
    }

    private static Optional<Membership> membership(Connection connection, long characterId) {
        return JdbcOperations.queryOne(
                connection,
                MEMBERSHIP,
                rs -> new Membership(rs.getLong(1), rs.getInt(2), LEADER_ROLE.equals(rs.getString(3))),
                characterId);
    }

    private static Map<GuildSkill, Integer> ranks(Connection connection, long guildId) {
        Map<GuildSkill, Integer> ranks = new EnumMap<>(GuildSkill.class);
        JdbcOperations.query(connection, RANKS, rs -> Map.entry(rs.getString(1), rs.getInt(2)), guildId)
                .forEach(row -> parse(row.getKey()).ifPresent(skill -> ranks.put(skill, row.getValue())));
        return ranks;
    }

    /** Mã lạ trong DB (kỹ năng đã bỏ) thì bỏ qua — không làm hỏng cả bang. */
    private static Optional<GuildSkill> parse(String code) {
        for (GuildSkill skill : GuildSkill.values()) {
            if (skill.name().equals(code)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }
}
