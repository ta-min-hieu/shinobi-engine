package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.LifetimeRankingKind;
import dev.kage.protocol.Opcode;

/**
 * Bước lô 58: xếp hạng trọn đời (ADR-391/392) {@code lifetimeRank:kills|playtime}, thâm niên bang (ADR-393) {@code
 * seniority:}.
 */
final class Lot58Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot59Steps lot59;

    Lot58Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot59 = new Lot59Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "lifetimeRank" ->
                asker.ask(
                        Opcode.OPCODE_GET_LIFETIME_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetLifetimeRankingRequest.newBuilder()
                                .setKind(
                                        "playtime".equals(arg)
                                                ? LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYTIME
                                                : LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYER_KILLS)
                                .build(),
                        Opcode.OPCODE_GET_LIFETIME_RANKING_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.GetLifetimeRankingResponse r =
                                    dev.kage.protocol.GetLifetimeRankingResponse.parseFrom(p);
                            StringBuilder text = new StringBuilder(r.getKind().name());
                            for (dev.kage.protocol.LifetimeRank rank : r.getEntriesList()) {
                                text.append(' ')
                                        .append(rank.getName())
                                        .append('=')
                                        .append(rank.getValue());
                            }
                            return text.toString();
                        });
            case "seniority" ->
                asker.ask(
                        Opcode.OPCODE_GET_GUILD_SENIORITY_REQUEST_VALUE,
                        dev.kage.protocol.GetGuildSeniorityRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_GUILD_SENIORITY_RESPONSE_VALUE,
                        p -> {
                            StringBuilder text = new StringBuilder();
                            for (dev.kage.protocol.GuildSeniorityEntry entry :
                                    dev.kage.protocol.GetGuildSeniorityResponse.parseFrom(p)
                                            .getEntriesList()) {
                                text.append(entry.getName()).append(' ');
                            }
                            return text.toString().strip();
                        });
            default -> lot59.step(verb, arg);
        };
    }
}
