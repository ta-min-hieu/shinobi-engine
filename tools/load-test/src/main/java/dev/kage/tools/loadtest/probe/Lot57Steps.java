package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 57: chế độ ẩn (ADR-383) {@code invisible:on|off|query}, thích tuần (ADR-384) {@code likeWeek:}, xếp hạng cấp
 * trong bang (ADR-385) {@code guildLevels:}.
 */
final class Lot57Steps {

    private final Lot23Scenario.Asker asker;

    Lot57Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "invisible" ->
                asker.ask(
                        Opcode.OPCODE_SET_INVISIBLE_COMMAND_VALUE,
                        dev.kage.protocol.SetInvisibleCommand.newBuilder()
                                .setEnabled("on".equals(arg))
                                .setQueryOnly("query".equals(arg))
                                .build(),
                        Opcode.OPCODE_SET_INVISIBLE_RESPONSE_VALUE,
                        p -> "ẩn="
                                + dev.kage.protocol.SetInvisibleResponse.parseFrom(p)
                                        .getEnabled());
            case "likeWeek" ->
                asker.ask(
                        Opcode.OPCODE_GET_LIKE_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetLikeRankingRequest.newBuilder()
                                .setWeekly(true)
                                .build(),
                        Opcode.OPCODE_GET_LIKE_RANKING_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.GetLikeRankingResponse r =
                                    dev.kage.protocol.GetLikeRankingResponse.parseFrom(p);
                            return "tuần=" + r.getWeekly() + " dòng=" + r.getEntriesCount();
                        });
            case "guildLevels" ->
                asker.ask(
                        Opcode.OPCODE_GET_GUILD_LEVEL_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetGuildLevelRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_GUILD_LEVEL_RANKING_RESPONSE_VALUE,
                        p -> {
                            StringBuilder text = new StringBuilder();
                            for (dev.kage.protocol.GuildLevelRank rank :
                                    dev.kage.protocol.GetGuildLevelRankingResponse.parseFrom(p)
                                            .getEntriesList()) {
                                text.append(rank.getName())
                                        .append('=')
                                        .append(rank.getLevel())
                                        .append(' ');
                            }
                            return text.toString().strip();
                        });
            default -> "bước không hiểu";
        };
    }
}
