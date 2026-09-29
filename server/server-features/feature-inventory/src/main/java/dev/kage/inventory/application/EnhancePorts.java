package dev.kage.inventory.application;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.IdempotencyPort;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.TradeOfferPort;

/**
 * Gộp 5 Port mà {@link EnhanceItemUseCase} cần — tránh constructor quá 4 tham số (Checkstyle
 * ParameterNumber) sau khi thêm {@link ItemTemplatePort} cho "Bảo Vệ" (§7.1), cùng tinh thần
 * {@code EquipmentUseCases}/{@code ShopPorts}. {@code idempotencyPort} thêm 2026-08-02 (ADR-025
 * retrofit — cường hoá trừ gold thật giống BuyItemCommand nhưng chưa enforce lúc code lát cắt gốc,
 * cùng đợt đã làm cho ShopPorts/MailPorts/TradePorts). {@code tradeOfferPort} thêm 2026-09-29 (món
 * đang chào trong giao dịch không được cường hoá — quyết định chủ dự án).
 */
public record EnhancePorts(
        InventoryPort inventoryPort,
        GoldPort goldPort,
        AuditPort auditPort,
        ItemTemplatePort itemTemplatePort,
        IdempotencyPort idempotencyPort,
        TradeOfferPort tradeOfferPort) {}
