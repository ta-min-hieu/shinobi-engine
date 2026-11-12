package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 44: tra cứu vật phẩm (ADR-279) {@code lookupItem:<templateId>}; lịch sử đấu (ADR-280) {@code duels}; đếm
 * online (ADR-281) {@code onlineCount}; tự nhận lời mời nhóm (ADR-286) {@code autoParty:on|off|?}, {@code leaveParty}.
 * Nối sau {@link Lot41Steps}.
 */
final class Lot44Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot45Steps lot45;

    Lot44Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot45 = new Lot45Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "lookupItem" -> lookupItem(arg);
            case "duels" -> duels();
            case "onlineCount" -> online();
            case "autoParty" -> autoParty(arg);
            case "leaveParty" ->
                asker.ask(
                        Opcode.OPCODE_LEAVE_PARTY_COMMAND_VALUE,
                        dev.kage.protocol.LeavePartyCommand.getDefaultInstance(),
                        Opcode.OPCODE_LEAVE_PARTY_RESPONSE_VALUE,
                        p -> dev.kage.protocol.LeavePartyResponse.parseFrom(p)
                                .getOutcome()
                                .name());
            default -> lot45.step(verb);
        };
    }

    private String lookupItem(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_LOOKUP_ITEM_REQUEST_VALUE,
                dev.kage.protocol.LookupItemRequest.newBuilder()
                        .setItemTemplateId(Long.parseLong(arg))
                        .build(),
                Opcode.OPCODE_LOOKUP_ITEM_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.LookupItemResponse r = dev.kage.protocol.LookupItemResponse.parseFrom(p);
                    StringBuilder out = new StringBuilder("mua=")
                            .append(r.getBuyPrice())
                            .append(" thu=")
                            .append(r.getSellPrice())
                            .append(" rơi=");
                    r.getDropsList().forEach(d -> out.append(' ')
                            .append(d.getMonsterCode())
                            .append('@')
                            .append(d.getChancePercent())
                            .append('%'));
                    return out.toString();
                });
    }

    private String duels() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_DUEL_HISTORY_REQUEST_VALUE,
                dev.kage.protocol.GetDuelHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_DUEL_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("đấu=");
                    dev.kage.protocol.GetDuelHistoryResponse.parseFrom(p)
                            .getEntriesList()
                            .forEach(e -> out.append(' ')
                                    .append(e.getWon() ? "THẮNG:" : "thua:")
                                    .append(e.getOpponentName())
                                    .append('/')
                                    .append(e.getRatingAfter()));
                    return out.toString();
                });
    }

    private String online() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_ONLINE_COUNT_REQUEST_VALUE,
                dev.kage.protocol.GetOnlineCountRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_ONLINE_COUNT_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetOnlineCountResponse r = dev.kage.protocol.GetOnlineCountResponse.parseFrom(p);
                    StringBuilder out =
                            new StringBuilder("tổng=").append(r.getTotal()).append(" linhCăn=");
                    r.getByFactionList().forEach(f -> out.append(f.getFaction())
                            .append(':')
                            .append(f.getCount())
                            .append(' '));
                    return out.append("cảnhGiới=").append(r.getByRealmList()).toString();
                });
    }

    private String autoParty(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_PARTY_AUTO_ACCEPT_COMMAND_VALUE,
                dev.kage.protocol.SetPartyAutoAcceptCommand.newBuilder()
                        .setQueryOnly("?".equals(arg))
                        .setEnabled("on".equals(arg))
                        .build(),
                Opcode.OPCODE_SET_PARTY_AUTO_ACCEPT_RESPONSE_VALUE,
                p -> "tựNhận="
                        + dev.kage.protocol.SetPartyAutoAcceptResponse.parseFrom(p)
                                .getEnabled());
    }
}
