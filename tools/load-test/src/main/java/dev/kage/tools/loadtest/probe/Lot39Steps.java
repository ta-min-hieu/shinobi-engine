package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 39: buff bang (ADR-239) {@code guildBuff:on|?}; kỷ lục cá nhân (ADR-240) {@code records}; xem trước thưởng
 * (ADR-242) {@code questPreview:<mã>}. Nối sau {@link Lot38Steps}.
 */
final class Lot39Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot40Steps lot40;

    Lot39Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot40 = new Lot40Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "guildBuff" -> guildBuff(arg);
            case "records" -> records();
            case "questPreview" -> questPreview(arg);
            default -> lot40.step(verb, arg);
        };
    }

    private String guildBuff(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_ACTIVATE_GUILD_BUFF_COMMAND_VALUE,
                dev.kage.protocol.ActivateGuildBuffCommand.newBuilder()
                        .setQueryOnly("?".equals(arg))
                        .build(),
                Opcode.OPCODE_ACTIVATE_GUILD_BUFF_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.ActivateGuildBuffResponse r =
                            dev.kage.protocol.ActivateGuildBuffResponse.parseFrom(p);
                    return r.getOutcome().name() + " giây=" + r.getSecondsLeft() + " kinhNghiệmBang="
                            + r.getGuildExpLeft();
                });
    }

    private String records() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_PERSONAL_RECORDS_REQUEST_VALUE,
                dev.kage.protocol.GetPersonalRecordsRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_PERSONAL_RECORDS_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetPersonalRecordsResponse r =
                            dev.kage.protocol.GetPersonalRecordsResponse.parseFrom(p);
                    return "đònMạnh=" + r.getHighestHit() + " chuỗiDài=" + r.getLongestKillStreak() + " chuỗiNay="
                            + r.getCurrentKillStreak();
                });
    }

    private String questPreview(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_QUEST_REWARD_PREVIEW_REQUEST_VALUE,
                dev.kage.protocol.GetQuestRewardPreviewRequest.newBuilder()
                        .setQuestCode(arg)
                        .build(),
                Opcode.OPCODE_GET_QUEST_REWARD_PREVIEW_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetQuestRewardPreviewResponse r =
                            dev.kage.protocol.GetQuestRewardPreviewResponse.parseFrom(p);
                    StringBuilder out = new StringBuilder(r.getOutcome().name())
                            .append(" exp=")
                            .append(r.getExp())
                            .append(" vàng=")
                            .append(r.getGold());
                    r.getItemsList().forEach(i -> out.append(' ')
                            .append(i.getItemTemplateCode())
                            .append('x')
                            .append(i.getQuantity()));
                    return out.toString();
                });
    }
}
