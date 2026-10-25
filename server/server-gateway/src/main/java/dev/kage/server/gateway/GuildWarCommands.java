package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildWarFacade;
import dev.kage.guild.domain.GuildWar;
import dev.kage.guild.domain.GuildWarOutcome;
import dev.kage.guild.domain.GuildWarPort;
import dev.kage.guild.domain.GuildWarRules;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.DeclareGuildWarOutcome;
import dev.kage.protocol.DeclareGuildWarResponse;
import dev.kage.protocol.GetGuildWarStatusResponse;
import dev.kage.protocol.GuildWarView;
import dev.kage.protocol.Opcode;

/** Hai lệnh bang chiến (ADR-102) — tách khỏi {@code GatewayChannelHandler}. Chạy trên Virtual Thread (chạm DB). */
final class GuildWarCommands {

    private static final Map<GuildWarOutcome, DeclareGuildWarOutcome> OUTCOMES = new EnumMap<>(GuildWarOutcome.class);

    static {
        OUTCOMES.put(GuildWarOutcome.OK, DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_OK);
        OUTCOMES.put(GuildWarOutcome.NOT_IN_GUILD, DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_NOT_IN_GUILD);
        OUTCOMES.put(GuildWarOutcome.NOT_LEADER, DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_NOT_LEADER);
        OUTCOMES.put(GuildWarOutcome.UNKNOWN_GUILD, DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_UNKNOWN_GUILD);
        OUTCOMES.put(GuildWarOutcome.SAME_GUILD, DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_SAME_GUILD);
        OUTCOMES.put(GuildWarOutcome.ALREADY_AT_WAR, DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_ALREADY_AT_WAR);
        OUTCOMES.put(GuildWarOutcome.COOLDOWN, DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_COOLDOWN);
        OUTCOMES.put(
                GuildWarOutcome.INSUFFICIENT_GOLD, DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_INSUFFICIENT_GOLD);
    }

    /** Các nguồn dữ liệu. */
    record Deps(GuildWarFacade wars, GuildFacade guilds, ConnectedCharacterRegistry registry) {}

    private final Deps deps;
    private final Clock clock;

    GuildWarCommands(Deps deps, Clock clock) {
        this.deps = deps;
        this.clock = clock;
    }

    static GuildWarCommands of(GatewayFacades facades, ConnectedCharacterRegistry registry) {
        return new GuildWarCommands(
                new Deps(
                        facades.progression().adventure().guildWars(),
                        facades.contentFacades().socialFacades().guildFacade(),
                        registry),
                Clock.systemUTC());
    }

    DeclareGuildWarResponse declare(long characterId, String targetGuildName) {
        GuildWarPort.Declared declared = deps.wars().declare(characterId, targetGuildName.strip(), clock.instant());
        declared.war().ifPresent(this::announceDeclared);
        return DeclareGuildWarResponse.newBuilder()
                .setOutcome(OUTCOMES.get(declared.outcome()))
                .setCostGold(GuildWarRules.DECLARE_COST)
                .build();
    }

    GetGuildWarStatusResponse status(long characterId) {
        Instant now = clock.instant();
        long myGuild = deps.guilds()
                .findMyGuild(characterId)
                .map(GuildFacade.MyGuildView::guildId)
                .orElse(0L);
        GetGuildWarStatusResponse.Builder response =
                GetGuildWarStatusResponse.newBuilder().setDeclareCostGold(GuildWarRules.DECLARE_COST);
        for (GuildWar war : deps.wars().unsettled()) {
            boolean active = war.isActive(now);
            Instant change = active ? war.endsAt() : war.startsAt();
            response.addWars(GuildWarView.newBuilder()
                    .setAttackerGuildName(war.attacker().guildName())
                    .setDefenderGuildName(war.defender().guildName())
                    .setAttackerKills(war.attacker().kills())
                    .setDefenderKills(war.defender().kills())
                    .setActive(active)
                    .setSecondsUntilChange(
                            Math.max(0L, Duration.between(now, change).toSeconds()))
                    .setMine(myGuild != 0L && war.involves(myGuild)));
        }
        return response.build();
    }

    private void announceDeclared(GuildWar war) {
        long minutes = GuildWarRules.PREPARATION.toMinutes();
        String body = "Bang %s tuyên chiến bang %s — %d phút nữa khai chiến! / Guild %s declared war on %s — it begins"
                        .formatted(
                                war.attacker().guildName(),
                                war.defender().guildName(),
                                minutes,
                                war.attacker().guildName(),
                                war.defender().guildName())
                + " in %d minutes!".formatted(minutes);
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build();
        deps.registry().broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, event.toByteArray());
    }
}
