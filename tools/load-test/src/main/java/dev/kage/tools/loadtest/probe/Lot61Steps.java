package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/** Bước lô 61: bạn bè lâu vắng (ADR-416) {@code absentFriends:}, tân thủ hôm nay (ADR-417) {@code newPlayers:}. */
final class Lot61Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot62Steps lot62;

    Lot61Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot62 = new Lot62Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "absentFriends" ->
                asker.ask(
                        Opcode.OPCODE_GET_ABSENT_FRIENDS_REQUEST_VALUE,
                        dev.kage.protocol.GetAbsentFriendsRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_ABSENT_FRIENDS_RESPONSE_VALUE,
                        p -> {
                            StringBuilder text = new StringBuilder();
                            for (dev.kage.protocol.AbsentFriend friend :
                                    dev.kage.protocol.GetAbsentFriendsResponse.parseFrom(p)
                                            .getEntriesList()) {
                                text.append(friend.getName())
                                        .append('=')
                                        .append(friend.getDays())
                                        .append(' ');
                            }
                            return text.toString().strip();
                        });
            case "newPlayers" ->
                asker.ask(
                        Opcode.OPCODE_GET_NEW_PLAYERS_REQUEST_VALUE,
                        dev.kage.protocol.GetNewPlayersRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_NEW_PLAYERS_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.GetNewPlayersResponse r =
                                    dev.kage.protocol.GetNewPlayersResponse.parseFrom(p);
                            return "số=" + r.getCount() + " mới=" + r.getNewestList();
                        });
            default -> lot62.step(verb, arg);
        };
    }
}
