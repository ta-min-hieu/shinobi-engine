package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.kage.checkin.domain.CheckInCycle;
import dev.kage.checkin.domain.CheckInReward;
import dev.kage.checkin.domain.CheckInRewardSender;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.mail.api.MailFacade;

/**
 * Quà điểm danh đi THƯ HỆ THỐNG (ADR-060 D3): túi đầy không mất quà, lĩnh qua {@code MAIL_CLAIM} có
 * audit sẵn. Khoá chống trùng ADR-058 làm phần "không gửi hai lần".
 */
final class CheckInMailSender implements CheckInRewardSender {

    private static final String SUBJECT = "Điểm danh / Check-in";

    private final MailFacade mail;
    private final InventoryFacade inventory;

    CheckInMailSender(MailFacade mail, InventoryFacade inventory) {
        this.mail = mail;
        this.inventory = inventory;
    }

    @Override
    public Delivery send(long characterId, CheckInReward reward, UUID idempotencyKey) {
        List<MailFacade.AttachmentRequest> items = List.of();
        if (reward.hasItem()) {
            Optional<ItemCatalogEntry> item = inventory.itemCatalog().entries().stream()
                    .filter(entry -> entry.code().equals(reward.itemCode()))
                    .findFirst();
            if (item.isEmpty()) {
                return Delivery.FAILED;
            }
            items = List.of(new MailFacade.AttachmentRequest(item.get().id(), reward.quantity()));
        }
        String body = "Ngày %d/%d. / Day %d of %d."
                .formatted(reward.day(), CheckInCycle.DAYS, reward.day(), CheckInCycle.DAYS);
        MailFacade.SendOutcome outcome = mail.sendMail(
                new MailFacade.SendMailRequest(null, characterId, SUBJECT, body, reward.gold(), items),
                idempotencyKey.toString());
        return switch (outcome.result()) {
            case OK -> new Delivery(true, outcome.mailId());
            case DUPLICATE_REQUEST -> new Delivery(true, 0L);
            default -> Delivery.FAILED;
        };
    }
}
