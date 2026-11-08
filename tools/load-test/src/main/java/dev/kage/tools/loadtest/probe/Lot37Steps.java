package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 37: thống kê cá nhân (ADR-223) {@code personalStats}; lịch sự kiện (ADR-224) {@code calendar}. Nối sau {@link
 * Lot36Steps}.
 */
final class Lot37Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot38Steps lot38;

    Lot37Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot38 = new Lot38Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "personalStats" -> personalStats();
            case "calendar" -> calendar();
            case "nextQuest" -> nextQuest();
            case "shareAch" -> shareAch(arg);
            default -> lot38.step(verb, arg);
        };
    }

    private String personalStats() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_PERSONAL_STATS_REQUEST_VALUE,
                dev.kage.protocol.GetPersonalStatsRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_PERSONAL_STATS_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetPersonalStatsResponse r =
                            dev.kage.protocol.GetPersonalStatsResponse.parseFrom(p);
                    return "quái=" + r.getMonstersKilled() + " chết=" + r.getDeaths() + " pvp=" + r.getPvpWins() + "/"
                            + r.getPvpLosses() + " nhiệmVụ=" + r.getQuestsClaimed() + " thànhTựu="
                            + r.getAchievementsUnlocked() + " giây=" + r.getPlaytimeSeconds();
                });
    }

    private String calendar() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_EVENT_CALENDAR_REQUEST_VALUE,
                dev.kage.protocol.GetEventCalendarRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_EVENT_CALENDAR_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetEventCalendarResponse r =
                            dev.kage.protocol.GetEventCalendarResponse.parseFrom(p);
                    StringBuilder out = new StringBuilder("lịch=");
                    r.getEntriesList().forEach(e -> out.append(' ')
                            .append(e.getKind().name().replace("CALENDAR_EVENT_KIND_", ""))
                            .append('+')
                            .append((e.getAtEpochSeconds() - r.getServerNowEpochSeconds()) / 60)
                            .append("ph")
                            .append(e.getActive() ? "(đang)" : ""));
                    return out.toString();
                });
    }

    private String nextQuest() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_NEXT_QUEST_HINT_REQUEST_VALUE,
                dev.kage.protocol.GetNextQuestHintRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_NEXT_QUEST_HINT_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetNextQuestHintResponse r =
                            dev.kage.protocol.GetNextQuestHintResponse.parseFrom(p);
                    return r.getKind().name() + " quest=" + r.getQuestCode() + " npc=" + r.getNpcCode();
                });
    }

    private String shareAch(String arg) throws IOException {
        dev.kage.protocol.SetAchievementShareCommand.Builder command =
                dev.kage.protocol.SetAchievementShareCommand.newBuilder();
        if ("?".equals(arg)) {
            command.setQueryOnly(true);
        } else {
            command.setEnabled("on".equals(arg));
        }
        return asker.ask(
                Opcode.OPCODE_SET_ACHIEVEMENT_SHARE_COMMAND_VALUE,
                command.build(),
                Opcode.OPCODE_SET_ACHIEVEMENT_SHARE_RESPONSE_VALUE,
                p -> "khoe="
                        + dev.kage.protocol.SetAchievementShareResponse.parseFrom(p)
                                .getEnabled());
    }
}
