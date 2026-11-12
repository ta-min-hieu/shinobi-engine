package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 41: thông tin quái (ADR-255) {@code monsterInfo:<mã>}; lịch bang (ADR-256) {@code eventNew:<phút tới>/<tiêu
 * đề>}, {@code events}, {@code eventCancel:<id>}; dự tính cường hoá (ADR-257) {@code enhancePreview:<itemId>}; xếp hạng
 * bạn bè (ADR-258) {@code friendRank}. Nối sau {@link Lot40Steps}.
 */
final class Lot41Steps {

    private static final long SECONDS_PER_MINUTE = 60L;

    private final Lot23Scenario.Asker asker;
    private final Lot44Steps lot44;

    Lot41Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot44 = new Lot44Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "monsterInfo" -> monsterInfo(arg);
            case "eventNew" -> eventNew(arg);
            case "events" -> events();
            case "eventCancel" -> eventCancel(arg);
            case "enhancePreview" -> enhancePreview(arg);
            case "friendRank" -> friendRank();
            default -> lot44.step(verb, arg);
        };
    }

    private String monsterInfo(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_MONSTER_INFO_REQUEST_VALUE,
                dev.kage.protocol.GetMonsterInfoRequest.newBuilder()
                        .setMonsterCode(arg)
                        .build(),
                Opcode.OPCODE_GET_MONSTER_INFO_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetMonsterInfoResponse r = dev.kage.protocol.GetMonsterInfoResponse.parseFrom(p);
                    StringBuilder out = new StringBuilder("có=")
                            .append(r.getFound())
                            .append(" cấp=")
                            .append(r.getLevel())
                            .append(" máu=")
                            .append(r.getMaxHp())
                            .append(" exp=")
                            .append(r.getExpReward())
                            .append(" rơi=");
                    r.getDropsList().forEach(d -> out.append(' ')
                            .append(d.getItemTemplateId())
                            .append('@')
                            .append(d.getChancePercent())
                            .append('%'));
                    return out.toString();
                });
    }

    private String eventNew(String arg) throws IOException {
        String[] parts = arg.split("/", 2);
        long at = System.currentTimeMillis() / 1000L + Long.parseLong(parts[0]) * SECONDS_PER_MINUTE;
        return asker.ask(
                Opcode.OPCODE_CREATE_GUILD_EVENT_COMMAND_VALUE,
                dev.kage.protocol.CreateGuildEventCommand.newBuilder()
                        .setTitle(parts.length > 1 ? parts[1] : "")
                        .setStartsAtEpochSeconds(at)
                        .build(),
                Opcode.OPCODE_CREATE_GUILD_EVENT_RESPONSE_VALUE,
                p -> dev.kage.protocol.CreateGuildEventResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    private String events() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_GUILD_EVENTS_REQUEST_VALUE,
                dev.kage.protocol.ListGuildEventsRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_GUILD_EVENTS_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("lịch=");
                    long now = System.currentTimeMillis() / 1000L;
                    dev.kage.protocol.ListGuildEventsResponse.parseFrom(p)
                            .getEventsList()
                            .forEach(e -> out.append(' ')
                                    .append(e.getEventId())
                                    .append(':')
                                    .append(e.getTitle())
                                    .append('+')
                                    .append((e.getStartsAtEpochSeconds() - now) / SECONDS_PER_MINUTE)
                                    .append("ph"));
                    return out.toString();
                });
    }

    private String eventCancel(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_CANCEL_GUILD_EVENT_COMMAND_VALUE,
                dev.kage.protocol.CancelGuildEventCommand.newBuilder()
                        .setEventId(Long.parseLong(arg))
                        .build(),
                Opcode.OPCODE_CANCEL_GUILD_EVENT_RESPONSE_VALUE,
                p -> dev.kage.protocol.CancelGuildEventResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    private String enhancePreview(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_ENHANCE_PREVIEW_REQUEST_VALUE,
                dev.kage.protocol.GetEnhancePreviewRequest.newBuilder()
                        .setItemId(Long.parseLong(arg))
                        .build(),
                Opcode.OPCODE_GET_ENHANCE_PREVIEW_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetEnhancePreviewResponse r =
                            dev.kage.protocol.GetEnhancePreviewResponse.parseFrom(p);
                    return r.getOutcome().name() + " cấp=" + r.getCurrentLevel() + " vàng=" + r.getGoldCost()
                            + " tỉLệ=" + r.getSuccessPercent() + "% tụt=" + r.getDropsOnFail() + " bảoVệ="
                            + r.getProtectionCharms();
                });
    }

    private String friendRank() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_FRIEND_RANKING_REQUEST_VALUE,
                dev.kage.protocol.GetFriendRankingRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_FRIEND_RANKING_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("bạnBè=");
                    dev.kage.protocol.GetFriendRankingResponse.parseFrom(p)
                            .getEntriesList()
                            .forEach(e -> out.append(' ')
                                    .append(e.getName())
                                    .append(':')
                                    .append(e.getCombatPower())
                                    .append(e.getIsSelf() ? "(mình)" : ""));
                    return out.toString();
                });
    }
}
