package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 54: đọc hết thư (ADR-359) {@code readAllMail}; ai thích mình (ADR-360) {@code likers}; lịch sử bị soi
 * (ADR-361) {@code inspectedBy}; chỉ bạn bè được soi (ADR-362) {@code friendsOnly:?|on|off}.
 */
final class Lot54Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot55Steps lot55;

    Lot54Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot55 = new Lot55Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "readAllMail" ->
                asker.ask(
                        Opcode.OPCODE_MARK_ALL_MAIL_READ_COMMAND_VALUE,
                        dev.kage.protocol.MarkAllMailReadCommand.getDefaultInstance(),
                        Opcode.OPCODE_MARK_ALL_MAIL_READ_RESPONSE_VALUE,
                        p -> "đãĐọc="
                                + dev.kage.protocol.MarkAllMailReadResponse.parseFrom(p)
                                        .getMarked());
            case "likers" -> likers();
            case "inspectedBy" -> inspectedBy();
            case "friendsOnly" ->
                asker.ask(
                        Opcode.OPCODE_SET_INSPECT_FRIENDS_ONLY_COMMAND_VALUE,
                        dev.kage.protocol.SetInspectFriendsOnlyCommand.newBuilder()
                                .setQueryOnly("?".equals(arg))
                                .setEnabled("on".equals(arg))
                                .build(),
                        Opcode.OPCODE_SET_INSPECT_FRIENDS_ONLY_RESPONSE_VALUE,
                        p -> "chỉBạnBè="
                                + dev.kage.protocol.SetInspectFriendsOnlyResponse.parseFrom(p)
                                        .getEnabled());
            default -> lot55.step(verb, arg);
        };
    }

    private String likers() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_PROFILE_LIKERS_REQUEST_VALUE,
                dev.kage.protocol.GetProfileLikersRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_PROFILE_LIKERS_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetProfileLikersResponse r =
                            dev.kage.protocol.GetProfileLikersResponse.parseFrom(p);
                    StringBuilder out = new StringBuilder("tổng=").append(r.getTotal());
                    for (dev.kage.protocol.ProfileLiker liker : r.getLikersList()) {
                        out.append(' ').append(liker.getName());
                    }
                    return out.toString();
                });
    }

    private String inspectedBy() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_INSPECT_HISTORY_REQUEST_VALUE,
                dev.kage.protocol.GetInspectHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_INSPECT_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("bịSoi");
                    for (dev.kage.protocol.InspectVisit visit : dev.kage.protocol.GetInspectHistoryResponse.parseFrom(p)
                            .getVisitsList()) {
                        out.append(' ').append(visit.getViewerName());
                    }
                    return out.toString();
                });
    }
}
