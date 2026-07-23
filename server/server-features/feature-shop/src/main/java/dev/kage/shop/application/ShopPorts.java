package dev.kage.shop.application;

import dev.kage.shop.domain.AuditPort;
import dev.kage.shop.domain.GoldPort;
import dev.kage.shop.domain.InventoryGatewayPort;
import dev.kage.shop.domain.ShopCatalogPort;

/**
 * Gom 4 Port mà {@link BuyItemUseCase}/{@link SellItemUseCase} đều cần — tránh constructor quá 4
 * tham số (Checkstyle ParameterNumber), cùng tinh thần {@code EquipmentUseCases} ở feature-inventory.
 */
public record ShopPorts(
        ShopCatalogPort catalogPort,
        GoldPort goldPort,
        InventoryGatewayPort inventoryGatewayPort,
        AuditPort auditPort) {
}
