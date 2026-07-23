package dev.kage.quest.application;

import dev.kage.quest.domain.AuditPort;
import dev.kage.quest.domain.ExpPort;
import dev.kage.quest.domain.QuestInventoryGrantPort;

/**
 * Gộp 3 Port chỉ {@link ClaimQuestRewardUseCase} cần (trao thưởng) — tránh constructor quá 4 tham
 * số (Checkstyle ParameterNumber) khi cộng thêm {@code QuestProgressPort}/{@code QuestTemplatePort}
 * đã dùng chung với {@link AcceptQuestUseCase}/{@link ProcessMonsterKilledUseCase}, cùng tinh thần
 * {@code ShopPorts}/{@code EnhancePorts}.
 */
public record QuestRewardPorts(ExpPort expPort, QuestInventoryGrantPort inventoryGrantPort, AuditPort auditPort) {
}
