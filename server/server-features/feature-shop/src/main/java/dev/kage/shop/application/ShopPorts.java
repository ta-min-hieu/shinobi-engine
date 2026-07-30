package dev.kage.shop.application;

import dev.kage.shop.domain.AuditPort;
import dev.kage.shop.domain.GoldPort;
import dev.kage.shop.domain.IdempotencyPort;
import dev.kage.shop.domain.InventoryGatewayPort;
import dev.kage.shop.domain.ShopCatalogPort;

/**
 * Gom 5 Port mà {@link BuyItemUseCase}/{@link SellItemUseCase} đều cần — tránh constructor quá 4
 * tham số (Checkstyle ParameterNumber), cùng tinh thần {@code EquipmentUseCases} ở feature-inventory.
 * {@code idempotencyPort} thêm 2026-07-24 (ADR-025 retrofit, phát hiện lúc làm Trade — Buy/Sell
 * được ADR nêu tên nhưng chưa thật sự enforce).
 */
public record ShopPorts(
        ShopCatalogPort catalogPort,
        GoldPort goldPort,
        InventoryGatewayPort inventoryGatewayPort,
        AuditPort auditPort,
        IdempotencyPort idempotencyPort) {}
