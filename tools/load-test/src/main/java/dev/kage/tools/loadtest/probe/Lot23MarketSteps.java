package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.BuyOrderView;
import dev.kage.protocol.CancelBuyOrderCommand;
import dev.kage.protocol.CancelBuyOrderResponse;
import dev.kage.protocol.CreateBuyOrderCommand;
import dev.kage.protocol.CreateBuyOrderResponse;
import dev.kage.protocol.ListBuyOrdersRequest;
import dev.kage.protocol.ListBuyOrdersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellToBuyOrderCommand;
import dev.kage.protocol.SellToBuyOrderResponse;

/**
 * Bước cầu mua lô 23 (ADR-116): {@code buyOrders}, {@code placeBuyOrder:<templateId>:<sl>:<đơn giá>},
 * {@code sellToBuyOrder:<orderId>:<inventoryItemId>:<sl>}, {@code cancelBuyOrder:<orderId>}.
 */
final class Lot23MarketSteps {

    private final Lot23Scenario.Asker asker;
    private final Lot23WorldSteps world;

    Lot23MarketSteps(Lot23Scenario.Asker asker, Lot23WorldSteps world) {
        this.asker = asker;
        this.world = world;
    }

    /** {@code "bước không hiểu"} nếu bước không thuộc phần này. */
    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "buyOrders" ->
                asker.ask(
                        Opcode.OPCODE_LIST_BUY_ORDERS_REQUEST_VALUE,
                        ListBuyOrdersRequest.getDefaultInstance(),
                        Opcode.OPCODE_LIST_BUY_ORDERS_RESPONSE_VALUE,
                        p -> describe(ListBuyOrdersResponse.parseFrom(p)));
            case "placeBuyOrder" -> {
                String[] a = arg.split(":", 3);
                yield asker.ask(
                        Opcode.OPCODE_CREATE_BUY_ORDER_COMMAND_VALUE,
                        CreateBuyOrderCommand.newBuilder()
                                .setItemTemplateId(Long.parseLong(a[0]))
                                .setQuantity(Integer.parseInt(a[1]))
                                .setUnitPrice(Long.parseLong(a[2]))
                                .build(),
                        Opcode.OPCODE_CREATE_BUY_ORDER_RESPONSE_VALUE,
                        p -> CreateBuyOrderResponse.parseFrom(p).getOutcome().name());
            }
            case "sellToBuyOrder" -> {
                String[] a = arg.split(":", 3);
                yield asker.ask(
                        Opcode.OPCODE_SELL_TO_BUY_ORDER_COMMAND_VALUE,
                        SellToBuyOrderCommand.newBuilder()
                                .setOrderId(Long.parseLong(a[0]))
                                .setInventoryItemId(Long.parseLong(a[1]))
                                .setQuantity(Integer.parseInt(a[2]))
                                .build(),
                        Opcode.OPCODE_SELL_TO_BUY_ORDER_RESPONSE_VALUE,
                        p -> {
                            SellToBuyOrderResponse r = SellToBuyOrderResponse.parseFrom(p);
                            return r.getOutcome() + " nhận=" + r.getProceeds();
                        });
            }
            case "cancelBuyOrder" ->
                asker.ask(
                        Opcode.OPCODE_CANCEL_BUY_ORDER_COMMAND_VALUE,
                        CancelBuyOrderCommand.newBuilder()
                                .setOrderId(Long.parseLong(arg))
                                .build(),
                        Opcode.OPCODE_CANCEL_BUY_ORDER_RESPONSE_VALUE,
                        p -> CancelBuyOrderResponse.parseFrom(p).getOutcome().name());
            default -> world.step(verb, arg);
        };
    }

    private static String describe(ListBuyOrdersResponse r) {
        StringBuilder line = new StringBuilder("lệnh:");
        r.getOrdersList().forEach(o -> line.append(' ').append(view(o)));
        line.append(" | của tôi:");
        r.getMineList().forEach(o -> line.append(' ').append(view(o)));
        return line.toString();
    }

    private static String view(BuyOrderView o) {
        return "#" + o.getOrderId() + "[tpl" + o.getItemTemplateId() + " " + o.getQuantityFilled() + "/"
                + o.getQuantityWanted() + "@" + o.getUnitPrice() + "]";
    }
}
