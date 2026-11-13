package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 50: chuỗi đăng nhập (ADR-327) {@code loginStreak}; báo bị soi (ADR-330) {@code inspectNotice:?|on|off};
 * người vừa chơi cùng (ADR-329) {@code recentPlayers}.
 */
final class Lot50Steps {

    private final Lot23Scenario.Asker asker;

    Lot50Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "loginStreak" ->
                asker.ask(
                        Opcode.OPCODE_GET_LOGIN_STREAK_REQUEST_VALUE,
                        dev.kage.protocol.GetLoginStreakRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_LOGIN_STREAK_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.GetLoginStreakResponse r =
                                    dev.kage.protocol.GetLoginStreakResponse.parseFrom(p);
                            return "chuỗi=" + r.getCurrent() + " dàiNhất=" + r.getBest() + " mốc="
                                    + r.getNextRewardDay() + ":" + r.getNextRewardGold();
                        });
            case "inspectNotice" ->
                asker.ask(
                        Opcode.OPCODE_SET_INSPECT_NOTICE_COMMAND_VALUE,
                        dev.kage.protocol.SetInspectNoticeCommand.newBuilder()
                                .setQueryOnly("?".equals(arg))
                                .setEnabled("on".equals(arg))
                                .build(),
                        Opcode.OPCODE_SET_INSPECT_NOTICE_RESPONSE_VALUE,
                        p -> "báoBịSoi="
                                + dev.kage.protocol.SetInspectNoticeResponse.parseFrom(p)
                                        .getEnabled());
            case "recentPlayers" ->
                asker.ask(
                        Opcode.OPCODE_GET_RECENT_PLAYERS_REQUEST_VALUE,
                        dev.kage.protocol.GetRecentPlayersRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_RECENT_PLAYERS_RESPONSE_VALUE,
                        p -> {
                            StringBuilder out = new StringBuilder("vừaChơiCùng");
                            for (dev.kage.protocol.RecentPlayer player :
                                    dev.kage.protocol.GetRecentPlayersResponse.parseFrom(p)
                                            .getPlayersList()) {
                                out.append(' ').append(player.getName()).append(player.getOnline() ? "(on)" : "");
                            }
                            return out.toString();
                        });
            default -> "bước không hiểu";
        };
    }
}
