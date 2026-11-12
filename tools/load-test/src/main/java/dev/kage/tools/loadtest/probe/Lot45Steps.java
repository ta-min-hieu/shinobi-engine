package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 45: lịch sử mua cửa hàng NPC (ADR-288) {@code purchases}; thư đã gửi (ADR-290) {@code sentMail}. Nối sau
 * {@link Lot44Steps}.
 */
final class Lot45Steps {

    private final Lot23Scenario.Asker asker;

    Lot45Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb) throws IOException {
        return switch (verb) {
            case "purchases" ->
                asker.ask(
                        Opcode.OPCODE_GET_SHOP_PURCHASE_HISTORY_REQUEST_VALUE,
                        dev.kage.protocol.GetShopPurchaseHistoryRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_SHOP_PURCHASE_HISTORY_RESPONSE_VALUE,
                        p -> {
                            StringBuilder out = new StringBuilder("mua=");
                            for (dev.kage.protocol.ShopPurchaseEntry e :
                                    dev.kage.protocol.GetShopPurchaseHistoryResponse.parseFrom(p)
                                            .getEntriesList()) {
                                out.append(' ')
                                        .append(e.getItemTemplateId())
                                        .append('x')
                                        .append(e.getQuantity())
                                        .append('=')
                                        .append(e.getGoldCost());
                            }
                            return out.toString();
                        });
            case "sentMail" ->
                asker.ask(
                        Opcode.OPCODE_LIST_SENT_MAIL_REQUEST_VALUE,
                        dev.kage.protocol.ListSentMailRequest.getDefaultInstance(),
                        Opcode.OPCODE_LIST_SENT_MAIL_RESPONSE_VALUE,
                        p -> {
                            StringBuilder out = new StringBuilder("thư=");
                            for (dev.kage.protocol.SentMailEntry e : dev.kage.protocol.ListSentMailResponse.parseFrom(p)
                                    .getEntriesList()) {
                                out.append(' ')
                                        .append(e.getRecipientName())
                                        .append(':')
                                        .append(e.getSubject())
                                        .append(e.getClaimed() ? "(đã lĩnh)" : "(chưa lĩnh)");
                            }
                            return out.toString();
                        });
            default -> "bước không hiểu";
        };
    }
}
