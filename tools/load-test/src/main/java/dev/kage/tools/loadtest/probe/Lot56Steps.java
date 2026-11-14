package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 56: thư cả nhóm (ADR-375) {@code partyMail:<tiêu đề>}, đỉnh online (ADR-377) {@code peakOnline:}, thống kê
 * PvP (ADR-378) {@code pvpStats:}, lời mời kèm lời nhắn (ADR-376) {@code friendNote:<tên>/<lời nhắn>}.
 */
final class Lot56Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot57Steps lot57;

    Lot56Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot57 = new Lot57Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "partyMail" -> partyMail(arg);
            case "friendNote" -> friendNote(arg);
            case "peakOnline" ->
                asker.ask(
                        Opcode.OPCODE_GET_PEAK_ONLINE_REQUEST_VALUE,
                        dev.kage.protocol.GetPeakOnlineRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_PEAK_ONLINE_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.GetPeakOnlineResponse r =
                                    dev.kage.protocol.GetPeakOnlineResponse.parseFrom(p);
                            return "đỉnh=" + r.getPeak() + " hiện=" + r.getCurrent();
                        });
            case "pvpStats" ->
                asker.ask(
                        Opcode.OPCODE_GET_PERSONAL_STATS_REQUEST_VALUE,
                        dev.kage.protocol.GetPersonalStatsRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_PERSONAL_STATS_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.GetPersonalStatsResponse r =
                                    dev.kage.protocol.GetPersonalStatsResponse.parseFrom(p);
                            return "hạ=" + r.getPlayerKills() + " bịHạ=" + r.getDeathsByPlayer();
                        });
            default -> lot57.step(verb, arg);
        };
    }

    private String friendNote(String arg) throws IOException {
        String[] parts = arg.split("/", 2);
        return asker.ask(
                Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE,
                dev.kage.protocol.SendFriendRequestCommand.newBuilder()
                        .setTargetName(parts[0])
                        .setMessage(parts.length > 1 ? parts[1] : "")
                        .build(),
                Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE,
                p -> dev.kage.protocol.SendFriendRequestResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    private String partyMail(String subject) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SEND_PARTY_MAIL_COMMAND_VALUE,
                dev.kage.protocol.SendPartyMailCommand.newBuilder()
                        .setSubject(subject)
                        .setBody("thư nhóm từ probe")
                        .build(),
                Opcode.OPCODE_SEND_PARTY_MAIL_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.SendPartyMailResponse r = dev.kage.protocol.SendPartyMailResponse.parseFrom(p);
                    return r.getOutcome().name() + " x" + r.getRecipients();
                });
    }
}
