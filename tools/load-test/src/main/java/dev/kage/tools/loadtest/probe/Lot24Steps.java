package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.Locale;

import dev.kage.protocol.CaveAction;
import dev.kage.protocol.CaveCommand;
import dev.kage.protocol.CaveDecorationView;
import dev.kage.protocol.CaveResponse;
import dev.kage.protocol.Opcode;

/** Bước lô 24: động phủ (ADR-129) {@code cave:<view|enter|collect|upgrade|decorate/<mã>|invite/<tên>|visit/<tên>>}. */
final class Lot24Steps {

    private final Lot23Scenario.Asker asker;

    Lot24Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "cave" -> cave(arg);
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
}
