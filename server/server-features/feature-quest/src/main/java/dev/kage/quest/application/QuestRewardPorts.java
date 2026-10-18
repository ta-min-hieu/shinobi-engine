package dev.kage.quest.application;

import dev.kage.quest.domain.AuditPort;
import dev.kage.quest.domain.ExpPort;
import dev.kage.quest.domain.IdempotencyPort;
import dev.kage.quest.domain.QuestGoldPort;
import dev.kage.quest.domain.QuestInventoryGrantPort;

/**
 * Gộp 4 Port chỉ {@link ClaimQuestRewardUseCase} cần (trao thưởng) — tránh constructor quá 4 tham
 * số (Checkstyle ParameterNumber) khi cộng thêm {@code QuestProgressPort}/{@code QuestTemplatePort}
 * đã dùng chung với {@link AcceptQuestUseCase}/{@link ProcessQuestEventUseCase}, cùng tinh thần
 * {@code ShopPorts}/{@code EnhancePorts}. {@code idempotencyPort} thêm 2026-08-02 (ADR-025 retrofit
 * — nhận thưởng trao EXP/item thật giống ClaimMailCommand nhưng chưa enforce lúc code lát cắt gốc).
 */
public record QuestRewardPorts(
        ExpPort expPort,
        QuestInventoryGrantPort inventoryGrantPort,
        AuditPort auditPort,
        IdempotencyPort idempotencyPort,
        QuestGoldPort goldPort) {

    /**
     * Composition KHÔNG trao vàng — hình dạng trước ADR-055, giữ cho các bộ lắp ở test. Script nào gọi
     * {@code ctx.give_gold} qua bộ lắp này sẽ ném ({@link QuestGoldPort#UNSUPPORTED}).
     */
    public QuestRewardPorts(
            ExpPort expPort,
            QuestInventoryGrantPort inventoryGrantPort,
            AuditPort auditPort,
            IdempotencyPort idempotencyPort) {
        this(expPort, inventoryGrantPort, auditPort, idempotencyPort, QuestGoldPort.UNSUPPORTED);
    }
}
