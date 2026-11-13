package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 46: boss đã hạ (ADR-296) {@code bossKills}; thư hẹn giờ (ADR-297) {@code scheduleMail:<phút>:<tên>}; thích
 * hồ sơ (ADR-298) {@code likeProfile:<tên>}. Nối sau {@link Lot45Steps}.
 */
final class Lot46Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot47Steps lot47;

    Lot46Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot47 = new Lot47Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "bossKills" ->
                asker.ask(
                        Opcode.OPCODE_GET_BOSS_KILLS_REQUEST_VALUE,
                        dev.kage.protocol.GetBossKillsRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_BOSS_KILLS_RESPONSE_VALUE,
                        p -> "boss="
                                + dev.kage.protocol.GetBossKillsResponse.parseFrom(p)
                                        .getEntriesCount());
            case "scheduleMail" -> {
                String[] parts = arg.split(":", -1);
                yield asker.ask(
                        Opcode.OPCODE_SCHEDULE_MAIL_COMMAND_VALUE,
                        dev.kage.protocol.ScheduleMailCommand.newBuilder()
                                .setDelayMinutes(Integer.parseInt(parts[0]))
                                .setRecipientName(parts[1])
                                .setSubject("probe hẹn giờ")
                                .setBody("lô 46")
                                .build(),
                        Opcode.OPCODE_SCHEDULE_MAIL_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.ScheduleMailResponse r =
                                    dev.kage.protocol.ScheduleMailResponse.parseFrom(p);
                            return r.getOutcome().name() + " chờ=" + r.getPending();
                        });
            }
            case "likeProfile" ->
                asker.ask(
                        Opcode.OPCODE_LIKE_PROFILE_COMMAND_VALUE,
                        dev.kage.protocol.LikeProfileCommand.newBuilder()
                                .setTargetName(arg)
                                .build(),
                        Opcode.OPCODE_LIKE_PROFILE_RESPONSE_VALUE,
                        p -> {
                            dev.kage.protocol.LikeProfileResponse r =
                                    dev.kage.protocol.LikeProfileResponse.parseFrom(p);
                            return r.getOutcome().name() + " thích=" + r.getLikes();
                        });
            default -> lot47.step(verb, arg);
        };
    }
}
