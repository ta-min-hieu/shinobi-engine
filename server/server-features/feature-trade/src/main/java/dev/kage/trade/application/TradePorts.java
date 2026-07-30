package dev.kage.trade.application;

import dev.kage.trade.domain.IdempotencyPort;
import dev.kage.trade.domain.TradeAuditPort;
import dev.kage.trade.domain.TradeGoldPort;
import dev.kage.trade.domain.TradeInventoryPort;
import dev.kage.trade.domain.TradeSessionPort;

/**
 * Gộp 5 Port mà {@link ConfirmTradeUseCase} cần — tránh constructor vượt 4 tham số (Checkstyle
 * ParameterNumber), cùng tinh thần {@code ShopPorts}/{@code MailPorts}. Các UseCase khác
 * (Request/Accept/Cancel/SetOffer/GetStatus) chỉ cần {@code TradeSessionPort} (+{@code
 * TradeInventoryPort} riêng cho GetTradeStatusUseCase) — không cần cả 5 Port.
 */
public record TradePorts(
        TradeSessionPort sessionPort,
        TradeInventoryPort inventoryPort,
        TradeGoldPort goldPort,
        TradeAuditPort auditPort,
        IdempotencyPort idempotencyPort) {}
