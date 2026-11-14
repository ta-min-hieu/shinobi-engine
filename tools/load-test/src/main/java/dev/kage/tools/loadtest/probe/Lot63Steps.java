package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 63: chăm chỉ hôm nay (ADR-431) {@code topDiligent:}, online 7 ngày (ADR-432) {@code onlineWeek:}, xếp hạng
 * thân mật (ADR-434) {@code topIntimacy:}.
 */
final class Lot63Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot64Steps lot64;

    Lot63Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot64 = new Lot64Steps(asker);
    }

    String step(String verb) throws IOException {
        return switch (verb) {
            case "topDiligent" ->
                asker.ask(
                        Opcode.OPCODE_GET_DILIGENCE_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetDiligenceRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_DILIGENCE_RANKING_RESPONSE_VALUE,
                        p -> {
                            StringBuilder out = new StringBuilder("top=");
                            for (dev.kage.protocol.DiligenceRank e :
                                    dev.kage.protocol.GetDiligenceRankingResponse.parseFrom(p)
                                            .getEntriesList()) {
                                out.append(e.getName())
                                        .append(':')
                                        .append(e.getSeconds())
                                        .append(' ');
                            }
                            return out.toString().strip();
                        });
            case "onlineWeek" -> onlineWeek();
            case "topIntimacy" ->
                asker.ask(
                        Opcode.OPCODE_GET_INTIMACY_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetIntimacyRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_INTIMACY_RANKING_RESPONSE_VALUE,
                        p -> {
                            StringBuilder out = new StringBuilder("cặp=");
                            for (dev.kage.protocol.IntimacyPair e :
                                    dev.kage.protocol.GetIntimacyRankingResponse.parseFrom(p)
                                            .getPairsList()) {
                                out.append(e.getFirstName())
                                        .append('&')
                                        .append(e.getSecondName())
                                        .append(':')
                                        .append(e.getIntimacy())
                                        .append(' ');
                            }
                            return out.toString().strip();
                        });
            default -> lot64.step(verb);
        };
    }

    private String onlineWeek() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_ONLINE_WEEK_REQUEST_VALUE,
                dev.kage.protocol.GetOnlineWeekRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_ONLINE_WEEK_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("ngày=");
                    for (dev.kage.protocol.OnlineDay d :
                            dev.kage.protocol.GetOnlineWeekResponse.parseFrom(p).getDaysList()) {
                        out.append(java.time.LocalDate.ofEpochDay(d.getDayEpochDays()))
                                .append(':')
                                .append(d.getSeconds())
                                .append(' ');
                    }
                    return out.toString().strip();
                });
    }
}
