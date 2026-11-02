package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.Locale;

import dev.kage.protocol.CaveAction;
import dev.kage.protocol.CaveCommand;
import dev.kage.protocol.CaveDecorationView;
import dev.kage.protocol.CaveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenameCharacterCommand;
import dev.kage.protocol.RenameCharacterResponse;
import dev.kage.protocol.TreasureAction;
import dev.kage.protocol.TreasureHuntCommand;
import dev.kage.protocol.TreasureHuntResponse;

/**
 * Bước lô 24: động phủ (ADR-129) {@code cave:<view|enter|collect|upgrade|decorate/<mã>|invite/<tên>|visit/<tên>>}. Lô
 * 27: Tàng Bảo Đồ (ADR-147) {@code treasure:<status|read|dig>}, Dịch Danh Phù (ADR-148) {@code rename:<tên mới>}.
 */
final class Lot24Steps {

    private final Lot23Scenario.Asker asker;

    Lot24Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "cave" -> cave(arg);
            case "treasure" -> treasure(arg);
            case "rename" ->
                asker.ask(
                        Opcode.OPCODE_RENAME_CHARACTER_COMMAND_VALUE,
                        RenameCharacterCommand.newBuilder().setNewName(arg).build(),
                        Opcode.OPCODE_RENAME_CHARACTER_RESPONSE_VALUE,
                        p -> {
                            RenameCharacterResponse r = RenameCharacterResponse.parseFrom(p);
                            return r.getOutcome().name() + " name=" + r.getName() + " next="
                                    + r.getNextAllowedAtEpochMs();
                        });
            default -> "bước không hiểu";
        };
    }

    private String cave(String arg) throws IOException {
        String[] parts = arg.split("/", 2);
        String detail = parts.length > 1 ? parts[1] : "";
        CaveAction action = CaveAction.valueOf("CAVE_ACTION_" + parts[0].toUpperCase(Locale.ROOT));
        return asker.ask(
                Opcode.OPCODE_CAVE_COMMAND_VALUE,
                CaveCommand.newBuilder()
                        .setAction(action)
                        .setDecorationCode(detail)
                        .setTargetName(detail)
                        .build(),
                Opcode.OPCODE_CAVE_RESPONSE_VALUE,
                p -> {
                    CaveResponse r = CaveResponse.parseFrom(p);
                    StringBuilder placed = new StringBuilder();
                    r.getDecorationsList().stream()
                            .filter(CaveDecorationView::getPlaced)
                            .forEach(dec -> placed.append(dec.getCode()).append(' '));
                    return r.getOutcome().name() + " lv=" + r.getVeinLevel() + " perHour=" + r.getPerHour()
                            + " stored=" + r.getStored() + "/" + r.getCapacity() + " upgrade=" + r.getUpgradeCost()
                            + " collected=" + r.getCollected() + " placed=["
                            + placed.toString().strip() + "]";
                });
    }

    private String treasure(String arg) throws IOException {
        TreasureAction action = TreasureAction.valueOf("TREASURE_ACTION_" + arg.toUpperCase(Locale.ROOT));
        return asker.ask(
                Opcode.OPCODE_TREASURE_HUNT_COMMAND_VALUE,
                TreasureHuntCommand.newBuilder().setAction(action).build(),
                Opcode.OPCODE_TREASURE_HUNT_RESPONSE_VALUE,
                p -> {
                    TreasureHuntResponse r = TreasureHuntResponse.parseFrom(p);
                    return r.getOutcome().name() + " hunting=" + r.getHunting() + " spot=" + r.getSpotNameKey()
                            + " x=" + r.getSpotX() + " distance=" + r.getDistance() + " result=" + r.getResultKey()
                            + " vàng=" + r.getGold() + " tpl" + r.getItemTemplateId() + "x" + r.getQuantity()
                            + " quái=" + r.getGuardianSpawned();
                });
    }
}
