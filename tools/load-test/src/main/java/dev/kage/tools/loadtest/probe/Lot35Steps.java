package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 35: góp vật phẩm cho bang (ADR-207) {@code donateItem:<inventory item id>/<số lượng>}; lịch sử bang chiến
 * (ADR-208) {@code warHistory}; gợi ý kết bạn (ADR-211) {@code suggestFriends}; chặn thư người lạ (ADR-212)
 * {@code strangerMail:on|off|?}. Nối sau {@link Lot34Steps}.
 */
final class Lot35Steps {

    private final Lot23Scenario.Asker asker;

    Lot35Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "donateItem" -> donateItem(arg);
            case "warHistory" -> warHistory();
            case "suggestFriends" -> suggestFriends();
            case "strangerMail" -> strangerMail(arg);
            default -> "bước không hiểu";
        };
    }

    private String donateItem(String arg) throws IOException {
        String[] parts = arg.split("/", 2);
        return asker.ask(
                Opcode.OPCODE_DONATE_ITEM_TO_GUILD_COMMAND_VALUE,
                dev.kage.protocol.DonateItemToGuildCommand.newBuilder()
                        .setInventoryItemId(Long.parseLong(parts[0]))
                        .setQuantity(Integer.parseInt(parts[1]))
                        .build(),
                Opcode.OPCODE_DONATE_ITEM_TO_GUILD_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.DonateItemToGuildResponse r =
                            dev.kage.protocol.DonateItemToGuildResponse.parseFrom(p);
                    return r.getOutcome().name() + " cốngHiến=" + r.getContribution() + " lênCấp="
                            + r.getGuildLeveledUp();
                });
    }

    private String warHistory() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_GUILD_WAR_HISTORY_REQUEST_VALUE,
                dev.kage.protocol.GetGuildWarHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_GUILD_WAR_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("trận=");
                    dev.kage.protocol.GetGuildWarHistoryResponse.parseFrom(p)
                            .getEntriesList()
                            .forEach(e -> out.append(' ')
                                    .append(e.getResult().name().replace("GUILD_WAR_RESULT_", ""))
                                    .append(e.getDeclaredByUs() ? ">" : "<")
                                    .append(e.getOpponentGuildName())
                                    .append(' ')
                                    .append(e.getOurKills())
                                    .append('-')
                                    .append(e.getTheirKills()));
                    return out.toString();
                });
    }

    private String suggestFriends() throws IOException {
        return asker.ask(
                Opcode.OPCODE_SUGGEST_FRIENDS_REQUEST_VALUE,
                dev.kage.protocol.SuggestFriendsRequest.getDefaultInstance(),
                Opcode.OPCODE_SUGGEST_FRIENDS_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("gợiÝ=");
                    dev.kage.protocol.SuggestFriendsResponse.parseFrom(p)
                            .getSuggestionsList()
                            .forEach(s -> out.append(' ')
                                    .append(s.getName())
                                    .append("/Lv")
                                    .append(s.getLevel()));
                    return out.toString();
                });
    }

    private String strangerMail(String arg) throws IOException {
        dev.kage.protocol.SetStrangerMailBlockCommand.Builder command =
                dev.kage.protocol.SetStrangerMailBlockCommand.newBuilder();
        if ("?".equals(arg)) {
            command.setQueryOnly(true);
        } else {
            command.setEnabled("on".equals(arg));
        }
        return asker.ask(
                Opcode.OPCODE_SET_STRANGER_MAIL_BLOCK_COMMAND_VALUE,
                command.build(),
                Opcode.OPCODE_SET_STRANGER_MAIL_BLOCK_RESPONSE_VALUE,
                p -> "chặnThưNgườiLạ="
                        + dev.kage.protocol.SetStrangerMailBlockResponse.parseFrom(p)
                                .getEnabled());
    }
}
