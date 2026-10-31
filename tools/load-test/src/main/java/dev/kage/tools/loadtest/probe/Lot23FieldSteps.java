package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.GetSpiritFieldRequest;
import dev.kage.protocol.GetSpiritFieldResponse;
import dev.kage.protocol.HarvestPlotCommand;
import dev.kage.protocol.HarvestPlotResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PlantSeedCommand;
import dev.kage.protocol.PlantSeedResponse;
import dev.kage.protocol.UnlockPlotCommand;
import dev.kage.protocol.UnlockPlotResponse;
import dev.kage.protocol.WaterPlotCommand;
import dev.kage.protocol.WaterPlotResponse;

/**
 * Bước linh điền lô 23 (ADR-119): {@code field[:<tên bạn>]}, {@code plant:<ô>:<templateId hạt>}, {@code
 * harvest:<ô>}, {@code water:<tên bạn>:<ô>}, {@code unlockPlot}. Số ô đếm từ 0 như trên dây.
 */
final class Lot23FieldSteps {

    private final Lot23Scenario.Asker asker;

    Lot23FieldSteps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "field" ->
                asker.ask(
                        Opcode.OPCODE_GET_SPIRIT_FIELD_REQUEST_VALUE,
                        GetSpiritFieldRequest.newBuilder().setOwnerName(arg).build(),
                        Opcode.OPCODE_GET_SPIRIT_FIELD_RESPONSE_VALUE,
                        p -> describe(GetSpiritFieldResponse.parseFrom(p)));
            case "plant" -> plant(arg);
            case "harvest" ->
                asker.ask(
                        Opcode.OPCODE_HARVEST_PLOT_COMMAND_VALUE,
                        HarvestPlotCommand.newBuilder()
                                .setPlotIndex(Integer.parseInt(arg))
                                .build(),
                        Opcode.OPCODE_HARVEST_PLOT_RESPONSE_VALUE,
                        p -> HarvestPlotResponse.parseFrom(p).getOutcome().name());
            case "water" -> water(arg);
            case "unlockPlot" ->
                asker.ask(
                        Opcode.OPCODE_UNLOCK_PLOT_COMMAND_VALUE,
                        UnlockPlotCommand.getDefaultInstance(),
                        Opcode.OPCODE_UNLOCK_PLOT_RESPONSE_VALUE,
                        p -> UnlockPlotResponse.parseFrom(p).getOutcome().name());
            default -> "bước không hiểu";
        };
    }

    private String plant(String arg) throws IOException {
        String[] parts = arg.split(":", 2);
        return asker.ask(
                Opcode.OPCODE_PLANT_SEED_COMMAND_VALUE,
                PlantSeedCommand.newBuilder()
                        .setPlotIndex(Integer.parseInt(parts[0]))
                        .setSeedItemTemplateId(Long.parseLong(parts[1]))
                        .build(),
                Opcode.OPCODE_PLANT_SEED_RESPONSE_VALUE,
                p -> PlantSeedResponse.parseFrom(p).getOutcome().name());
    }

    private String water(String arg) throws IOException {
        int split = arg.lastIndexOf(':');
        return asker.ask(
                Opcode.OPCODE_WATER_PLOT_COMMAND_VALUE,
                WaterPlotCommand.newBuilder()
                        .setOwnerName(arg.substring(0, split))
                        .setPlotIndex(Integer.parseInt(arg.substring(split + 1)))
                        .build(),
                Opcode.OPCODE_WATER_PLOT_RESPONSE_VALUE,
                p -> WaterPlotResponse.parseFrom(p).getOutcome().name());
    }

    private static String describe(GetSpiritFieldResponse r) {
        StringBuilder line = new StringBuilder(r.getOutcome().name())
                .append(" chủ=")
                .append(r.getOwnerName())
                .append(" ô=")
                .append(r.getPlots())
                .append(" giáMở=")
                .append(r.getUnlockCost());
        r.getPlantedList().forEach(plot -> line.append(" [ô")
                .append(plot.getIndex())
                .append(" hạt")
                .append(plot.getSeedItemTemplateId())
                .append(" chínSau=")
                .append((plot.getRipeAtEpochMs() - r.getServerTimeMs()) / 1000L)
                .append("s tưới=")
                .append(plot.getWatered())
                .append(']'));
        r.getSeedsList().forEach(seed -> line.append(" hạtBán:tpl")
                .append(seed.getItemTemplateId())
                .append('/')
                .append(seed.getGrowMinutes())
                .append("ph"));
        return line.toString();
    }
}
