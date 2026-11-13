package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/** Bước lô 52: tra tên cũ (ADR-343) {@code oldNames:<tên>}; ký hiệu bang (ADR-344) {@code guildTag:?|-|<ký hiệu>}. */
final class Lot52Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot53Steps lot53;

    Lot52Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot53 = new Lot53Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "oldNames" ->
                asker.ask(
                        Opcode.OPCODE_GET_NAME_HISTORY_REQUEST_VALUE,
                        dev.kage.protocol.GetNameHistoryRequest.newBuilder()
                                .setName(arg)
                                .build(),
                        Opcode.OPCODE_GET_NAME_HISTORY_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.GetNameHistoryResponse r =
                                    dev.kage.protocol.GetNameHistoryResponse.parseFrom(p);
                            return "thấy=" + r.getFound() + " hiệnTại=" + r.getCurrentName() + " tênCũ="
                                    + r.getOldNamesCount();
                        });
            case "guildTag" -> guildTag(arg);
            default -> lot53.step(verb, arg);
        };
    }

    private String guildTag(String arg) throws IOException {
        dev.kage.protocol.SetGuildTagCommand.Builder request = dev.kage.protocol.SetGuildTagCommand.newBuilder();
        if ("?".equals(arg)) {
            request.setQueryOnly(true);
        } else {
            request.setTag("-".equals(arg) ? "" : arg);
        }
        return asker.ask(
                Opcode.OPCODE_SET_GUILD_TAG_COMMAND_VALUE,
                request.build(),
                Opcode.OPCODE_SET_GUILD_TAG_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.SetGuildTagResponse r = dev.kage.protocol.SetGuildTagResponse.parseFrom(p);
                    return r.getOutcome().name() + " '" + r.getTag() + "'";
                });
    }
}
