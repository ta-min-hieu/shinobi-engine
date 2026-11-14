package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 62: lịch sử tên bang (ADR-423) {@code guildNames:[tên]}, mời bạn online vào nhóm (ADR-424) {@code
 * inviteFriends:}, câu chào (ADR-425) {@code greeting:<câu>|?}, báo cừu nhân (ADR-426) {@code enemyAlert:on|off|query}.
 */
final class Lot62Steps {

    private final Lot23Scenario.Asker asker;

    Lot62Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "guildNames" ->
                asker.ask(
                        Opcode.OPCODE_GET_GUILD_NAME_HISTORY_REQUEST_VALUE,
                        dev.kage.protocol.GetGuildNameHistoryRequest.newBuilder()
                                .setGuildName(arg)
                                .build(),
                        Opcode.OPCODE_GET_GUILD_NAME_HISTORY_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.GetGuildNameHistoryResponse r =
                                    dev.kage.protocol.GetGuildNameHistoryResponse.parseFrom(p);
                            return "thấy=" + r.getFound() + " hiện=" + r.getCurrentName() + " đổi="
                                    + r.getRenamesCount();
                        });
            case "inviteFriends" ->
                asker.ask(
                        Opcode.OPCODE_INVITE_ONLINE_FRIENDS_COMMAND_VALUE,
                        dev.kage.protocol.InviteOnlineFriendsCommand.getDefaultInstance(),
                        Opcode.OPCODE_INVITE_ONLINE_FRIENDS_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.InviteOnlineFriendsResponse r =
                                    dev.kage.protocol.InviteOnlineFriendsResponse.parseFrom(p);
                            return "khôngPhảiTrưởng=" + r.getNotLeader() + " mời=" + r.getInvited();
                        });
            case "greeting" -> greeting(arg);
            case "enemyAlert" ->
                asker.ask(
                        Opcode.OPCODE_SET_ENEMY_ALERT_COMMAND_VALUE,
                        dev.kage.protocol.SetEnemyAlertCommand.newBuilder()
                                .setEnabled("on".equals(arg))
                                .setQueryOnly("query".equals(arg))
                                .build(),
                        Opcode.OPCODE_SET_ENEMY_ALERT_RESPONSE_VALUE,
                        p -> "báo="
                                + dev.kage.protocol.SetEnemyAlertResponse.parseFrom(p)
                                        .getEnabled());
            default -> "bước không hiểu";
        };
    }

    private String greeting(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_LOGIN_GREETING_COMMAND_VALUE,
                dev.kage.protocol.SetLoginGreetingCommand.newBuilder()
                        .setQueryOnly("?".equals(arg))
                        .setMessage("?".equals(arg) ? "" : arg)
                        .build(),
                Opcode.OPCODE_SET_LOGIN_GREETING_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.SetLoginGreetingResponse r =
                            dev.kage.protocol.SetLoginGreetingResponse.parseFrom(p);
                    return "nhận=" + r.getAccepted() + " câu=" + r.getMessage();
                });
    }
}
