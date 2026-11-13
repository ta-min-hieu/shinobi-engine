package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 49: ghi chú bang (ADR-319) {@code memberNote:?|<tên>=<ghi chú>}; mã giới thiệu (ADR-320) {@code
 * referral:?|<tên>}; lịch sử đột phá (ADR-322) {@code breakthroughs}.
 */
final class Lot49Steps {

    private final Lot23Scenario.Asker asker;

    Lot49Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "memberNote" -> memberNote(arg);
            case "referral" ->
                asker.ask(
                        Opcode.OPCODE_ENTER_REFERRAL_COMMAND_VALUE,
                        dev.kage.protocol.EnterReferralCommand.newBuilder()
                                .setReferrerName("?".equals(arg) ? "" : arg)
                                .build(),
                        Opcode.OPCODE_ENTER_REFERRAL_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.EnterReferralResponse r =
                                    dev.kage.protocol.EnterReferralResponse.parseFrom(p);
                            return r.getOutcome().name() + " mã=" + r.getCode();
                        });
            case "breakthroughs" ->
                asker.ask(
                        Opcode.OPCODE_GET_BREAKTHROUGH_HISTORY_REQUEST_VALUE,
                        dev.kage.protocol.GetBreakthroughHistoryRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_BREAKTHROUGH_HISTORY_RESPONSE_VALUE,
                        p -> "độtPhá="
                                + dev.kage.protocol.GetBreakthroughHistoryResponse.parseFrom(p)
                                        .getEntriesCount());
            default -> "bước không hiểu";
        };
    }

    private String memberNote(String arg) throws IOException {
        int eq = arg.indexOf('=');
        dev.kage.protocol.SetGuildMemberNoteCommand.Builder request =
                dev.kage.protocol.SetGuildMemberNoteCommand.newBuilder();
        if (eq < 0) {
            request.setQueryOnly(true);
        } else {
            request.setMemberName(arg.substring(0, eq)).setNote(arg.substring(eq + 1));
        }
        return asker.ask(
                Opcode.OPCODE_SET_GUILD_MEMBER_NOTE_COMMAND_VALUE,
                request.build(),
                Opcode.OPCODE_SET_GUILD_MEMBER_NOTE_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.SetGuildMemberNoteResponse r =
                            dev.kage.protocol.SetGuildMemberNoteResponse.parseFrom(p);
                    StringBuilder out = new StringBuilder(r.getOutcome().name());
                    for (dev.kage.protocol.GuildMemberNote note : r.getNotesList()) {
                        out.append(' ').append(note.getName()).append(':').append(note.getNote());
                    }
                    return out.toString();
                });
    }
}
