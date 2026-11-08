package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 38: huỷ vật phẩm (ADR-231) {@code destroy:<itemId>}; phân giải hàng loạt (ADR-232) {@code
 * dismantleAll:<cấp>}; lịch sử cường hoá (ADR-233) {@code enhanceLog}; giá gợi ý (ADR-234) {@code
 * priceHint:<templateId>}. Nối sau {@link Lot37Steps}.
 */
final class Lot38Steps {

    private final Lot23Scenario.Asker asker;

    Lot38Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "destroy" -> destroy(arg);
            case "dismantleAll" -> dismantleAll(arg);
            case "enhanceLog" -> enhanceLog();
            case "priceHint" -> priceHint(arg);
            default -> "bước không hiểu";
        };
    }

    private String destroy(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_DESTROY_ITEM_COMMAND_VALUE,
                dev.kage.protocol.DestroyItemCommand.newBuilder()
                        .setItemId(Long.parseLong(arg))
                        .build(),
                Opcode.OPCODE_DESTROY_ITEM_RESPONSE_VALUE,
                p -> dev.kage.protocol.DestroyItemResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    private String dismantleAll(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_BULK_DISMANTLE_COMMAND_VALUE,
                dev.kage.protocol.BulkDismantleCommand.newBuilder()
                        .setMaxRequiredLevel(Integer.parseInt(arg))
                        .build(),
                Opcode.OPCODE_BULK_DISMANTLE_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.BulkDismantleResponse r = dev.kage.protocol.BulkDismantleResponse.parseFrom(p);
                    return "phânGiải=" + r.getDismantled() + " vàng=" + r.getGold() + " thảo=" + r.getHerbs();
                });
    }

    private String enhanceLog() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_ENHANCE_HISTORY_REQUEST_VALUE,
                dev.kage.protocol.GetEnhanceHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_ENHANCE_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("cườngHoá=");
                    dev.kage.protocol.GetEnhanceHistoryResponse.parseFrom(p)
                            .getEntriesList()
                            .forEach(e -> out.append(' ')
                                    .append(e.getItemTemplateId())
                                    .append(':')
                                    .append(e.getFromLevel())
                                    .append('>')
                                    .append(e.getToLevel())
                                    .append('/')
                                    .append(e.getResult()));
                    return out.toString();
                });
    }

    private String priceHint(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_MARKET_PRICE_HINT_REQUEST_VALUE,
                dev.kage.protocol.GetMarketPriceHintRequest.newBuilder()
                        .setItemTemplateId(Long.parseLong(arg))
                        .build(),
                Opcode.OPCODE_GET_MARKET_PRICE_HINT_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetMarketPriceHintResponse r =
                            dev.kage.protocol.GetMarketPriceHintResponse.parseFrom(p);
                    return "rẻNhất=" + r.getLowestActive() + " tbBán=" + r.getAverageSold() + " lượt="
                            + r.getSoldListings();
                });
    }
}
