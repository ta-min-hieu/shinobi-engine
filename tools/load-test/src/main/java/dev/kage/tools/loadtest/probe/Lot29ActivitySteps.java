package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.GetLuckyBagsRequest;
import dev.kage.protocol.GetLuckyBagsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.OpenLuckyBagCommand;
import dev.kage.protocol.OpenLuckyBagResponse;
import dev.kage.protocol.SetCostumeCommand;
import dev.kage.protocol.SetCostumeResponse;
import dev.kage.protocol.SweepDungeonCommand;
import dev.kage.protocol.SweepDungeonResponse;

/**
 * Bước lô 29 (phần hoạt động): quét Bí Cảnh (ADR-162) {@code sweep[:<mã>]}, túi phúc (ADR-163) {@code luckyBags},
 * {@code openBag:<templateId>}, huyễn hình trang phục (ADR-166) {@code costume:<0-3>}. Tách khỏi {@link Lot29Steps}
 * vì switch ở đó chạm trần CyclomaticComplexity.
 */
final class Lot29ActivitySteps {

    private final Lot23Scenario.Asker asker;
    private final Lot30Steps lot30;

    Lot29ActivitySteps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot30 = new Lot30Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "sweep" -> sweep(arg.isEmpty() ? "sample_dungeon" : arg);
            case "luckyBags" -> luckyBags();
            case "openBag" -> openBag(Long.parseLong(arg));
            case "costume" -> costume(Integer.parseInt(arg));
            default -> lot30.step(verb, arg);
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

    private String costume(int tier) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_COSTUME_COMMAND_VALUE,
                SetCostumeCommand.newBuilder()
                        .setTier(CostumeTier.forNumber(tier))
                        .build(),
                Opcode.OPCODE_SET_COSTUME_RESPONSE_VALUE,
                p -> {
                    SetCostumeResponse r = SetCostumeResponse.parseFrom(p);
                    return r.getOutcome().name() + " hiện=" + r.getShownTier().name() + " phí=" + r.getFee();
                });
    }
}
