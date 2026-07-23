package dev.kage.mail.application;

import dev.kage.mail.domain.AuditPort;
import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.MailRepositoryPort;

/**
 * Gộp 4 Port mà {@link ClaimMailUseCase} cần — tránh constructor vượt 4 tham số (Checkstyle
 * ParameterNumber), cùng tinh thần {@code ShopPorts}. {@link SendMailUseCase}/{@link
 * ListMailUseCase}/{@link DeleteMailUseCase} chỉ cần {@link MailRepositoryPort} riêng lẻ — không
 * cần gold/item/audit lúc gửi/xem/xoá.
 */
public record MailPorts(
        MailRepositoryPort repositoryPort, GoldPort goldPort, InventoryGatewayPort inventoryGatewayPort,
        AuditPort auditPort) {
}
