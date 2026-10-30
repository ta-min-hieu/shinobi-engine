package dev.kage.server.gateway;

import java.util.Locale;
import java.util.Optional;

import dev.kage.market.api.BuyOrderFacade;
import dev.kage.market.application.BuyOrderUseCase;
import dev.kage.market.domain.BuyOrder;
import dev.kage.protocol.BuyOrderFilledEvent;
import dev.kage.protocol.BuyOrderView;
import dev.kage.protocol.CancelBuyOrderResponse;
import dev.kage.protocol.CreateBuyOrderCommand;
import dev.kage.protocol.CreateBuyOrderResponse;
import dev.kage.protocol.ListBuyOrdersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellToBuyOrderCommand;
import dev.kage.protocol.SellToBuyOrderResponse;

/**
 * Bốn lệnh cầu mua (ADR-116) — chạy trên Virtual Thread (chạm DB). {@code characterId} từ session. Bán vào lệnh
 * xong thì báo người đặt nếu đang online (1023).
 */
final class BuyOrderCommands {

    private final BuyOrderFacade orders;
    private final ConnectedCharacterRegistry registry;

    BuyOrderCommands(BuyOrderFacade orders, ConnectedCharacterRegistry registry) {
        this.orders = orders;
        this.registry = registry;
    }

    ListBuyOrdersResponse list(long characterId, long itemTemplateId) {
        ListBuyOrdersResponse.Builder response = ListBuyOrdersResponse.newBuilder();
        Optional<Long> filter = itemTemplateId > 0 ? Optional.of(itemTemplateId) : Optional.empty();
        for (BuyOrder order : orders.browse(filter)) {
            if (order.buyerId() != characterId) {
                response.addOrders(view(order));
            }
        }
        for (BuyOrder order : orders.mine(characterId)) {
            response.addMine(view(order));
        }
        return response.build();
    }

    CreateBuyOrderResponse create(long characterId, CreateBuyOrderCommand command) {
        return CreateBuyOrderResponse.newBuilder()
                .setOutcome(wire(orders.place(
                        characterId, command.getItemTemplateId(), command.getQuantity(), command.getUnitPrice())))
                .build();
    }

    SellToBuyOrderResponse sell(long characterId, SellToBuyOrderCommand command) {
        BuyOrderUseCase.SellResult result =
                orders.sell(characterId, command.getOrderId(), command.getInventoryItemId(), command.getQuantity());
        result.fill()
                .ifPresent(fill -> registry.sendToCharacterIfOnline(
                        fill.buyerId(),
                        Opcode.OPCODE_BUY_ORDER_FILLED_EVENT_VALUE,
                        0,
                        BuyOrderFilledEvent.newBuilder()
                                .setOrderId(fill.orderId())
                                .setItemTemplateId(fill.itemTemplateId())
                                .setQuantity(fill.quantity())
                                .build()
                                .toByteArray()));
        return SellToBuyOrderResponse.newBuilder()
                .setOutcome(wire(result.outcome()))
                .setProceeds(result.fill().map(BuyOrderUseCase.Fill::proceeds).orElse(0L))
                .build();
    }

    CancelBuyOrderResponse cancel(long characterId, long orderId) {
        return CancelBuyOrderResponse.newBuilder()
                .setOutcome(wire(orders.cancel(characterId, orderId)))
                .build();
    }

    private static BuyOrderView view(BuyOrder order) {
        return BuyOrderView.newBuilder()
                .setOrderId(order.id())
                .setItemTemplateId(order.itemTemplateId())
                .setQuantityWanted(order.quantityWanted())
                .setQuantityFilled(order.quantityFilled())
                .setUnitPrice(order.unitPrice())
                .setExpiresAtEpochMs(order.expiresAt().toEpochMilli())
                .build();
    }

    /** Tên hằng hai enum khớp nhau (có tiền tố) — test {@code BuyOrderCommandsTest} ghim điều đó. */
    static dev.kage.protocol.BuyOrderOutcome wire(dev.kage.market.domain.BuyOrderOutcome outcome) {
        return dev.kage.protocol.BuyOrderOutcome.valueOf(
                "BUY_ORDER_OUTCOME_" + outcome.name().toUpperCase(Locale.ROOT));
    }
}
