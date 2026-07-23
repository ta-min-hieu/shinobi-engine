package dev.kage.inventory.application;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemTemplatePort;

/**
 * Gộp 4 Port mà {@link EnhanceItemUseCase} cần — tránh constructor quá 4 tham số (Checkstyle
 * ParameterNumber) sau khi thêm {@link ItemTemplatePort} cho "Bảo Vệ" (§7.1), cùng tinh thần
 * {@code EquipmentUseCases}/{@code ShopPorts}.
 */
public record EnhancePorts(
        InventoryPort inventoryPort, GoldPort goldPort, AuditPort auditPort, ItemTemplatePort itemTemplatePort) {
}
