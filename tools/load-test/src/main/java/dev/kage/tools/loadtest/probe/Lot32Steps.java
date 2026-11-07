package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildViceLeaderCommand;
import dev.kage.protocol.SetGuildViceLeaderResponse;
import dev.kage.protocol.TransferGuildLeaderCommand;
import dev.kage.protocol.TransferGuildLeaderResponse;

/**
 * Bước lô 32: nhường chức bang chủ (ADR-183) {@code transferLeader:<tên>}; phó bang chủ (ADR-184) {@code vice:<tên>},
 * {@code unvice:<tên>}. Nối sau {@link Lot31Steps}.
 */
final class Lot32Steps {

    private final Lot23Scenario.Asker asker;

    Lot32Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "transferLeader" -> transferLeader(arg);
            case "vice" -> vice(arg, true);
            case "unvice" -> vice(arg, false);
            default -> "bước không hiểu";
        };
    }

    private String transferLeader(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_TRANSFER_GUILD_LEADER_COMMAND_VALUE,
                TransferGuildLeaderCommand.newBuilder().setTargetName(name).build(),
                Opcode.OPCODE_TRANSFER_GUILD_LEADER_RESPONSE_VALUE,
                p -> {
                    TransferGuildLeaderResponse r = TransferGuildLeaderResponse.parseFrom(p);
                    return r.getOutcome().name() + " người nhận=" + r.getTargetName();
                });
    }

    private String vice(String name, boolean appoint) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_GUILD_VICE_LEADER_COMMAND_VALUE,
                SetGuildViceLeaderCommand.newBuilder()
                        .setTargetName(name)
                        .setAppoint(appoint)
                        .build(),
                Opcode.OPCODE_SET_GUILD_VICE_LEADER_RESPONSE_VALUE,
                p -> SetGuildViceLeaderResponse.parseFrom(p).getOutcome().name());
    }
}
