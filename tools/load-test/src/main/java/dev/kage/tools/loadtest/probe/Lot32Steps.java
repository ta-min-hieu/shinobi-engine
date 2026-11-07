package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.GuildCheckInCommand;
import dev.kage.protocol.GuildCheckInResponse;
import dev.kage.protocol.GuildRecruitmentView;
import dev.kage.protocol.ListGuildRecruitmentRequest;
import dev.kage.protocol.ListGuildRecruitmentResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PostGuildRecruitmentCommand;
import dev.kage.protocol.PostGuildRecruitmentResponse;
import dev.kage.protocol.SendGuildJoinRequestCommand;
import dev.kage.protocol.SendGuildJoinRequestResponse;
import dev.kage.protocol.SetGuildViceLeaderCommand;
import dev.kage.protocol.SetGuildViceLeaderResponse;
import dev.kage.protocol.TransferGuildLeaderCommand;
import dev.kage.protocol.TransferGuildLeaderResponse;

/**
 * Bước lô 32: nhường chức bang chủ (ADR-183) {@code transferLeader:<tên>}; phó bang chủ (ADR-184) {@code vice:<tên>},
 * {@code unvice:<tên>}; chiêu mộ (ADR-185) {@code recruit:<cấp>=<lời>} ({@code recruit:off} gỡ), {@code recruitBoard},
 * {@code applyGuild:<tên bang>}; điểm danh bang (ADR-186) {@code guildCheckIn}. Nối sau {@link Lot31Steps}.
 */
final class Lot32Steps {

    private final Lot23Scenario.Asker asker;

    Lot32Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "transferLeader" -> transferLeader(arg);
            case "vice" -> vice(arg, true);
            case "unvice" -> vice(arg, false);
            case "recruit" -> recruit(arg);
            case "recruitBoard" -> recruitBoard();
            case "applyGuild" -> applyGuild(arg);
            case "guildCheckIn" -> guildCheckIn();
            default -> "bước không hiểu";
        };
    }

    private String transferLeader(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_TRANSFER_GUILD_LEADER_COMMAND_VALUE,
                TransferGuildLeaderCommand.newBuilder().setTargetName(name).build(),
                Opcode.OPCODE_TRANSFER_GUILD_LEADER_RESPONSE_VALUE,
                p -> {
                    TransferGuildLeaderResponse r = TransferGuildLeaderResponse.parseFrom(p);
                    return r.getOutcome().name() + " người nhận=" + r.getTargetName();
                });
    }

    private String vice(String name, boolean appoint) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_GUILD_VICE_LEADER_COMMAND_VALUE,
                SetGuildViceLeaderCommand.newBuilder()
                        .setTargetName(name)
                        .setAppoint(appoint)
                        .build(),
                Opcode.OPCODE_SET_GUILD_VICE_LEADER_RESPONSE_VALUE,
                p -> SetGuildViceLeaderResponse.parseFrom(p).getOutcome().name());
    }

    private String recruit(String arg) throws IOException {
        PostGuildRecruitmentCommand.Builder command = PostGuildRecruitmentCommand.newBuilder();
        if (!"off".equals(arg)) {
            String[] parts = arg.split("=", 2);
            command.setOpen(true).setMinLevel(Integer.parseInt(parts[0])).setMessage(parts[1]);
        }
        return asker.ask(
                Opcode.OPCODE_POST_GUILD_RECRUITMENT_COMMAND_VALUE,
                command.build(),
                Opcode.OPCODE_POST_GUILD_RECRUITMENT_RESPONSE_VALUE,
                p -> PostGuildRecruitmentResponse.parseFrom(p).getOutcome().name());
    }

    private String recruitBoard() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_GUILD_RECRUITMENT_REQUEST_VALUE,
                ListGuildRecruitmentRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_GUILD_RECRUITMENT_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (GuildRecruitmentView v :
                            ListGuildRecruitmentResponse.parseFrom(p).getEntriesList()) {
                        out.append(v.getGuildName())
                                .append(" Lv")
                                .append(v.getGuildLevel())
                                .append(' ')
                                .append(v.getMembers())
                                .append('/')
                                .append(v.getMaxMembers())
                                .append(" từCấp")
                                .append(v.getMinLevel())
                                .append(": ")
                                .append(v.getMessage())
                                .append(" | ");
                    }
                    return out.toString();
                });
    }

    private String applyGuild(String guildName) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_COMMAND_VALUE,
                SendGuildJoinRequestCommand.newBuilder().setGuildName(guildName).build(),
                Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_RESPONSE_VALUE,
                p -> SendGuildJoinRequestResponse.parseFrom(p).getOutcome().name());
    }

    private String guildCheckIn() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GUILD_CHECK_IN_COMMAND_VALUE,
                GuildCheckInCommand.getDefaultInstance(),
                Opcode.OPCODE_GUILD_CHECK_IN_RESPONSE_VALUE,
                p -> {
                    GuildCheckInResponse r = GuildCheckInResponse.parseFrom(p);
                    return r.getOutcome().name() + " cốngHiến+" + r.getContribution() + " expBang+" + r.getGuildExp()
                            + " hômNay=" + r.getTodayCount();
                });
    }
}
