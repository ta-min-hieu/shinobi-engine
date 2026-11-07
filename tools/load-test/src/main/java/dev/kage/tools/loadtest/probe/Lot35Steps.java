package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 35: góp vật phẩm cho bang (ADR-207) {@code donateItem:<inventory item id>/<số lượng>}; lịch sử bang chiến
 * (ADR-208) {@code warHistory}. Nối sau {@link Lot34Steps}.
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
}
