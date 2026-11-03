package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.GetLuckyBagsRequest;
import dev.kage.protocol.GetLuckyBagsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.OpenLuckyBagCommand;
import dev.kage.protocol.OpenLuckyBagResponse;
import dev.kage.protocol.SweepDungeonCommand;
import dev.kage.protocol.SweepDungeonResponse;

/**
 * Bước lô 29 (phần hoạt động): quét Bí Cảnh (ADR-162) {@code sweep[:<mã>]}, túi phúc (ADR-163) {@code luckyBags},
 * {@code openBag:<templateId>}. Tách khỏi {@link Lot29Steps} vì switch ở đó chạm trần CyclomaticComplexity.
 */
final class Lot29ActivitySteps {

    private final Lot23Scenario.Asker asker;

    Lot29ActivitySteps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "sweep" -> sweep(arg.isEmpty() ? "sample_dungeon" : arg);
            case "luckyBags" -> luckyBags();
            case "openBag" -> openBag(Long.parseLong(arg));
            default -> "bước không hiểu";
        };
    }

    private String sweep(String dungeonCode) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SWEEP_DUNGEON_COMMAND_VALUE,
                SweepDungeonCommand.newBuilder().setDungeonCode(dungeonCode).build(),
                Opcode.OPCODE_SWEEP_DUNGEON_RESPONSE_VALUE,
                p -> {
                    SweepDungeonResponse r = SweepDungeonResponse.parseFrom(p);
                    StringBuilder line = new StringBuilder(r.getOutcome().name())
                            .append(" exp=")
                            .append(r.getExpGained())
                            .append(" còn=")
                            .append(r.getEntriesLeft());
                    r.getLootList().forEach(l -> line.append(' ')
                            .append(l.getItemTemplateId())
                            .append('x')
                            .append(l.getQuantity()));
                    return line.toString();
                });
    }

    private String luckyBags() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_LUCKY_BAGS_REQUEST_VALUE,
                GetLuckyBagsRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_LUCKY_BAGS_RESPONSE_VALUE,
                p -> describe(GetLuckyBagsResponse.parseFrom(p)));
    }

    private String openBag(long bagTemplateId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_OPEN_LUCKY_BAG_COMMAND_VALUE,
                OpenLuckyBagCommand.newBuilder()
                        .setBagItemTemplateId(bagTemplateId)
                        .build(),
                Opcode.OPCODE_OPEN_LUCKY_BAG_RESPONSE_VALUE,
                p -> {
                    OpenLuckyBagResponse r = OpenLuckyBagResponse.parseFrom(p);
                    return r.getOutcome().name() + " vàng=" + r.getReward().getGold() + " đồ="
                            + r.getReward().getItemTemplateId() + "x"
                            + r.getReward().getQuantity() + " "
                            + describe(r.getBags());
                });
    }

    private static String describe(GetLuckyBagsResponse bags) {
        StringBuilder line = new StringBuilder();
        bags.getBagsList().forEach(b -> line.append(" túi=")
                .append(b.getBagItemTemplateId())
                .append(" có=")
                .append(b.getOwned())
                .append(" quà=")
                .append(b.getRewardsCount()));
        return line.toString().strip();
    }
}
