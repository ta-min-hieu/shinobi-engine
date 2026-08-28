package dev.kage.mail.application;

import dev.kage.mail.domain.AuditPort;
import dev.kage.mail.domain.GoldPort;
import dev.kage.mail.domain.IdempotencyPort;
import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.MailRepositoryPort;

/**
 * Gộp 5 Port mà {@link ClaimMailUseCase} và {@link SendMailUseCase} cần — tránh constructor vượt 4
 * tham số (Checkstyle ParameterNumber), cùng tinh thần {@code ShopPorts}. {@link ListMailUseCase}/
 * {@link DeleteMailUseCase} chỉ cần {@link MailRepositoryPort} riêng lẻ — đọc/xoá không đụng
 * gold/item. {@code idempotencyPort} thêm 2026-07-24 (ADR-025 retrofit, phát hiện lúc làm Trade —
 * ClaimMail được ADR nêu tên nhưng chưa enforce).
 *
 * <p>{@link SendMailUseCase} chuyển sang dùng bản gộp này 2026-08-28: gửi thư giờ TRỪ vàng và
 * vật phẩm của người gửi (ký quỹ), nên cần đúng {@code goldPort}/{@code inventoryGatewayPort}/
 * {@code auditPort} như lúc nhận — trước đó nó chỉ có {@code repositoryPort}, và chính chỗ thiếu
 * đó là lỗ đúc vàng. Nó KHÔNG dùng {@code idempotencyPort}: gửi lại thư là tạo thêm một lá thư
 * thật (đã bị trừ tiền thật), không phải một hiệu ứng lặp cần chặn.
 */
public record MailPorts(
        MailRepositoryPort repositoryPort,
        GoldPort goldPort,
        InventoryGatewayPort inventoryGatewayPort,
        AuditPort auditPort,
        IdempotencyPort idempotencyPort) {}
