package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 48: thư gửi mọi bạn bè (ADR-312) {@code friendsMail}; lời chào bang (ADR-313) {@code welcome:?|xoa|<chữ>};
 * đánh dấu đã đọc (ADR-314) {@code markRead:<mailId>}.
 */
final class Lot48Steps {

    private final Lot23Scenario.Asker asker;

    Lot48Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "friendsMail" ->
                asker.ask(
                        Opcode.OPCODE_SEND_FRIENDS_MAIL_COMMAND_VALUE,
                        dev.kage.protocol.SendFriendsMailCommand.newBuilder()
                                .setSubject("probe thư bạn bè")
                                .setBody("lô 48")
                                .build(),
                        Opcode.OPCODE_SEND_FRIENDS_MAIL_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.SendFriendsMailResponse r =
                                    dev.kage.protocol.SendFriendsMailResponse.parseFrom(p);
                            return r.getOutcome().name() + " gửi=" + r.getRecipients();
                        });
            case "welcome" ->
                asker.ask(
                        Opcode.OPCODE_SET_GUILD_WELCOME_COMMAND_VALUE,
                        dev.kage.protocol.SetGuildWelcomeCommand.newBuilder()
                                .setQueryOnly("?".equals(arg))
                                .setMessage("xoa".equals(arg) ? "" : arg)
                                .build(),
                        Opcode.OPCODE_SET_GUILD_WELCOME_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.SetGuildWelcomeResponse r =
                                    dev.kage.protocol.SetGuildWelcomeResponse.parseFrom(p);
                            return r.getOutcome().name() + " lờiChào='" + r.getMessage() + "'";
                        });
            case "markRead" ->
                asker.ask(
                        Opcode.OPCODE_MARK_MAIL_READ_COMMAND_VALUE,
                        dev.kage.protocol.MarkMailReadCommand.newBuilder()
                                .setMailId(Long.parseLong(arg))
                                .build(),
                        Opcode.OPCODE_MARK_MAIL_READ_RESPONSE_VALUE,
                        p -> "chưaĐọc="
                                + dev.kage.protocol.MarkMailReadResponse.parseFrom(p)
                                        .getUnread());
            default -> "bước không hiểu";
        };
    }
}
