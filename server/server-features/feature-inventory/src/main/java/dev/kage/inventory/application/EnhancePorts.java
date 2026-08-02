package dev.kage.inventory.application;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.IdempotencyPort;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemTemplatePort;

/**
 * Gộp 5 Port mà {@link EnhanceItemUseCase} cần — tránh constructor quá 4 tham số (Checkstyle
 * ParameterNumber) sau khi thêm {@link ItemTemplatePort} cho "Bảo Vệ" (§7.1), cùng tinh thần
 * {@code EquipmentUseCases}/{@code ShopPorts}. {@code idempotencyPort} thêm 2026-08-02 (ADR-025
 * retrofit — cường hoá trừ gold thật giống BuyItemCommand nhưng chưa enforce lúc code lát cắt gốc,
 * cùng đợt đã làm cho ShopPorts/MailPorts/TradePorts).
 */
public record EnhancePorts(
        InventoryPort inventoryPort,
        GoldPort goldPort,
        AuditPort auditPort,
        ItemTemplatePort itemTemplatePort,
        IdempotencyPort idempotencyPort) {}
