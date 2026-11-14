package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/** Bước lô 55: kênh lãnh đạo bang (ADR-367) {@code officerChat:<tin>}. */
final class Lot55Steps {

    private final Lot23Scenario.Asker asker;

    Lot55Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        if (!"officerChat".equals(verb)) {
            return "bước không hiểu";
        }
        return asker.ask(
                Opcode.OPCODE_OFFICER_CHAT_COMMAND_VALUE,
                dev.kage.protocol.OfficerChatCommand.newBuilder().setBody(arg).build(),
                Opcode.OPCODE_OFFICER_CHAT_RESPONSE_VALUE,
                p -> dev.kage.protocol.OfficerChatResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }
}
