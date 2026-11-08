package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.Arrays;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 40: bình chọn bang (ADR-248) {@code pollNew:<câu hỏi>/<a>/<b>…}, {@code pollVote:<số 0-based>}, {@code
 * poll}; lịch sử nhặt đồ (ADR-253) {@code lootLog}. Nối sau {@link Lot39Steps}.
 */
final class Lot40Steps {

    private final Lot23Scenario.Asker asker;

    Lot40Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "pollNew" -> pollNew(arg);
            case "pollVote" -> pollVote(arg);
            case "poll" -> poll();
            case "lootLog" -> lootLog();
            default -> "bước không hiểu";
        };
    }

    private String pollNew(String arg) throws IOException {
        String[] parts = arg.split("/");
        return asker.ask(
                Opcode.OPCODE_CREATE_GUILD_POLL_COMMAND_VALUE,
                dev.kage.protocol.CreateGuildPollCommand.newBuilder()
                        .setQuestion(parts[0])
                        .addAllOptions(Arrays.asList(parts).subList(1, parts.length))
                        .build(),
                Opcode.OPCODE_CREATE_GUILD_POLL_RESPONSE_VALUE,
                p -> dev.kage.protocol.CreateGuildPollResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    private String pollVote(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_VOTE_GUILD_POLL_COMMAND_VALUE,
                dev.kage.protocol.VoteGuildPollCommand.newBuilder()
                        .setOptionIndex(Integer.parseInt(arg))
                        .build(),
                Opcode.OPCODE_VOTE_GUILD_POLL_RESPONSE_VALUE,
                p -> dev.kage.protocol.VoteGuildPollResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    private String poll() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_GUILD_POLL_REQUEST_VALUE,
                dev.kage.protocol.GetGuildPollRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_GUILD_POLL_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetGuildPollResponse r = dev.kage.protocol.GetGuildPollResponse.parseFrom(p);
                    return "có=" + r.getHasPoll() + " hỏi=" + r.getQuestion() + " chọn=" + r.getOptionsList()
                            + " phiếu=" + r.getVotesList() + " củaTôi=" + r.getMyVote() + " giây=" + r.getSecondsLeft();
                });
    }

    private String lootLog() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_LOOT_HISTORY_REQUEST_VALUE,
                dev.kage.protocol.GetLootHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_LOOT_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("nhặt=");
                    dev.kage.protocol.GetLootHistoryResponse.parseFrom(p)
                            .getEntriesList()
                            .forEach(e -> out.append(' ')
                                    .append(e.getItemTemplateId())
                                    .append('x')
                                    .append(e.getQuantity())
                                    .append('@')
                                    .append(e.getSource()));
                    return out.toString();
                });
    }
}
