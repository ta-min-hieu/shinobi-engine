package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.ClaimAllMailCommand;
import dev.kage.protocol.ClaimAllMailResponse;
import dev.kage.protocol.Opcode;

/** Bước lô 30: nhận hết thư (ADR-167) {@code claimAllMail}. */
final class Lot30Steps {

    private final Lot23Scenario.Asker asker;

    Lot30Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "claimAllMail" -> claimAllMail();
            default -> "bước không hiểu";
        };
    }

    private String claimAllMail() throws IOException {
        return asker.ask(
                Opcode.OPCODE_CLAIM_ALL_MAIL_COMMAND_VALUE,
                ClaimAllMailCommand.newBuilder().setDeleteClaimed(true).build(),
                Opcode.OPCODE_CLAIM_ALL_MAIL_RESPONSE_VALUE,
                p -> {
                    ClaimAllMailResponse r = ClaimAllMailResponse.parseFrom(p);
                    return "lĩnh=" + r.getClaimed() + " vàng=" + r.getGold() + " món=" + r.getItems() + " xoá="
                            + r.getDeleted() + " đầy=" + r.getBagFull();
                });
    }
}
