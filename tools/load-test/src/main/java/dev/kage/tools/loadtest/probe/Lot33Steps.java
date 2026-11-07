package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.EnemyView;
import dev.kage.protocol.ListEnemiesRequest;
import dev.kage.protocol.ListEnemiesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RemoveEnemyCommand;
import dev.kage.protocol.RemoveEnemyResponse;
import dev.kage.protocol.RenamePetCommand;
import dev.kage.protocol.RenamePetResponse;
import dev.kage.protocol.TransferPartyLeaderCommand;
import dev.kage.protocol.TransferPartyLeaderResponse;

/**
 * Bước lô 33: nhường trưởng nhóm (ADR-191) {@code partyLeader:<tên>}; đặt tên linh thú (ADR-193) {@code petName:<tên>}
 * ({@code petName:} trống = xoá); cừu nhân (ADR-194) {@code enemies}, {@code unenemy:<tên>}. Nối sau {@link
 * Lot32Steps}.
 */
final class Lot33Steps {

    private final Lot23Scenario.Asker asker;

    Lot33Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "partyLeader" -> partyLeader(arg);
            case "petName" -> petName(arg);
            case "enemies" -> enemies();
            case "unenemy" -> unenemy(arg);
            default -> "bước không hiểu";
        };
    }

    private String partyLeader(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_TRANSFER_PARTY_LEADER_COMMAND_VALUE,
                TransferPartyLeaderCommand.newBuilder().setTargetName(name).build(),
                Opcode.OPCODE_TRANSFER_PARTY_LEADER_RESPONSE_VALUE,
                p -> {
                    TransferPartyLeaderResponse r = TransferPartyLeaderResponse.parseFrom(p);
                    return r.getOutcome().name() + " người nhận=" + r.getTargetName();
                });
    }

    private String petName(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_RENAME_PET_COMMAND_VALUE,
                RenamePetCommand.newBuilder().setName(name).build(),
                Opcode.OPCODE_RENAME_PET_RESPONSE_VALUE,
                p -> {
                    RenamePetResponse r = RenamePetResponse.parseFrom(p);
                    return r.getOutcome().name() + " tên=" + r.getName();
                });
    }

    private String enemies() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_ENEMIES_REQUEST_VALUE,
                ListEnemiesRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_ENEMIES_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (EnemyView enemy : ListEnemiesResponse.parseFrom(p).getEnemiesList()) {
                        out.append(enemy.getName())
                                .append(" x")
                                .append(enemy.getKillCount())
                                .append(" online=")
                                .append(enemy.getOnline())
                                .append("; ");
                    }
                    return out.toString();
                });
    }

    private String unenemy(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_REMOVE_ENEMY_COMMAND_VALUE,
                RemoveEnemyCommand.newBuilder().setName(name).build(),
                Opcode.OPCODE_REMOVE_ENEMY_RESPONSE_VALUE,
                p -> "removed=" + RemoveEnemyResponse.parseFrom(p).getRemoved());
    }
}
