package dev.kage.mail.application;

import dev.kage.mail.domain.AuditPort;
import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.IdempotencyPort;
import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.MailRepositoryPort;

/**
 * Gộp 5 Port mà {@link ClaimMailUseCase} cần — tránh constructor vượt 4 tham số (Checkstyle
 * ParameterNumber), cùng tinh thần {@code ShopPorts}. {@link SendMailUseCase}/{@link
 * ListMailUseCase}/{@link DeleteMailUseCase} chỉ cần {@link MailRepositoryPort} riêng lẻ — không
 * cần gold/item/audit/idempotency lúc gửi/xem/xoá. {@code idempotencyPort} thêm 2026-07-24
 * (ADR-025 retrofit, phát hiện lúc làm Trade — ClaimMail được ADR nêu tên nhưng chưa enforce).
 */
public record MailPorts(
        MailRepositoryPort repositoryPort, GoldPort goldPort, InventoryGatewayPort inventoryGatewayPort,
        AuditPort auditPort, IdempotencyPort idempotencyPort) {
}
