package dev.kage.server.gateway;

import dev.kage.account.api.AccountFacade;
import dev.kage.character.api.CharacterFacade;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.shop.api.ShopFacade;

/**
 * Gộp 4 Facade nghiệp vụ (account/character/inventory/shop) — tránh {@link GatewayServer}/
 * {@link GatewayChannelHandler} vượt quá 4 tham số (CODING-STANDARDS §4, Checkstyle
 * {@code ParameterNumber}), cùng pattern {@code CharacterBaseStats}/{@code NewCharacter}.
 */
record GatewayFacades(
        AccountFacade accountFacade, CharacterFacade characterFacade, InventoryFacade inventoryFacade,
        ShopFacade shopFacade) {
}
