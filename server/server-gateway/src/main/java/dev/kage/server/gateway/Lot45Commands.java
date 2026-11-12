package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.kage.mail.api.MailFacade;
import dev.kage.mail.domain.SentMailPort;
import dev.kage.protocol.GetShopPurchaseHistoryResponse;
import dev.kage.protocol.ListSentMailResponse;
import dev.kage.protocol.SentMailEntry;
import dev.kage.protocol.ShopPurchaseEntry;
import dev.kage.shop.domain.PurchaseHistoryPort;

/** Lớp lệnh lô 45 (ADR-287…) — MỘT thành phần của {@link Lot44Commands}. Gọi trên Virtual Thread. */
record Lot45Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-289 — mỗi mốc gắn bó dài bấy nhiêu ngày. */
    static final int LOYALTY_DAYS = 30;

    /** ADR-289 — linh thạch mỗi mốc nhân với số mốc (mốc càng xa quà càng lớn). */
    static final long LOYALTY_GOLD_PER_MILESTONE = 1_000L;

    /** ADR-289 — số mốc tối đa dùng để tính quà (mốc 12 trở đi quà bằng nhau). */
    static final int LOYALTY_MAX_MULTIPLIER = 12;

    private static final String LOYALTY_SUBJECT = "Quà gắn bó / Loyalty gift";

    /** Lớp lệnh lô 46 (ADR-295…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot46Commands lot46() {
        return new Lot46Commands(gateway, registry);
    }

    /** ADR-288 — 20 lần mua cửa hàng NPC gần nhất. */
    GetShopPurchaseHistoryResponse purchaseHistory(long characterId) {
        GetShopPurchaseHistoryResponse.Builder response = GetShopPurchaseHistoryResponse.newBuilder();
        for (PurchaseHistoryPort.Purchase purchase : facades().purchaseHistory().recent(characterId)) {
            response.addEntries(ShopPurchaseEntry.newBuilder()
                    .setItemTemplateId(purchase.itemTemplateId())
                    .setQuantity(purchase.quantity())
                    .setGoldCost(purchase.goldCost())
                    .setAtEpochSeconds(purchase.at().getEpochSecond()));
        }
        return response.build();
    }

    /** ADR-290 — 20 thư gần nhất người này đã gửi (thư người nhận đã xoá thì không còn). */
    ListSentMailResponse sentMail(long characterId) {
        ListSentMailResponse.Builder response = ListSentMailResponse.newBuilder();
        for (SentMailPort.Sent sent : facades().sentMail().recent(characterId)) {
            response.addEntries(SentMailEntry.newBuilder()
                    .setRecipientName(sent.recipientName())
                    .setSubject(sent.subject())
                    .setGoldAmount(sent.goldAmount())
                    .setAttachments(sent.attachments())
                    .setClaimed(sent.claimed())
                    .setAtEpochSeconds(sent.at().getEpochSecond()));
        }
        return response.build();
    }

    /**
     * ADR-289 — vào world: nhân vật đã qua mốc gắn bó thứ {@code n} (mỗi {@value #LOYALTY_DAYS} ngày từ lúc tạo) ⇒ gửi
     * thư hệ thống kèm linh thạch. Khoá chống trùng theo (nhân vật, mốc) nên vào world bao nhiêu lần cũng chỉ một thư
     * mỗi mốc; vắng mặt qua trọn một mốc thì chỉ nhận mốc mới nhất.
     */
    void onEnteredWorld(long characterId) {
        gateway.characterFacade().findById(characterId).ifPresent(character -> {
            long milestone = milestone(character.createdAt(), Instant.now());
            if (milestone <= 0) {
                return;
            }
            MailFacade mail = gateway.contentFacades().socialFacades().mailFacade();
            mail.sendMail(
                    new MailFacade.SendMailRequest(
                            null,
                            characterId,
                            LOYALTY_SUBJECT,
                            loyaltyBody(milestone),
                            loyaltyGold(milestone),
                            List.of()),
                    loyaltyKey(characterId, milestone));
        });
    }

    /** Số mốc gắn bó đã qua (0 nếu chưa đủ một mốc). Tĩnh để test. */
    static long milestone(Instant createdAt, Instant now) {
        if (!now.isAfter(createdAt)) {
            return 0L;
        }
        return Duration.between(createdAt, now).toDays() / LOYALTY_DAYS;
    }

    /** Linh thạch của mốc {@code n}. Tĩnh để test. */
    static long loyaltyGold(long milestone) {
        return LOYALTY_GOLD_PER_MILESTONE * Math.min(milestone, LOYALTY_MAX_MULTIPLIER);
    }

    /** Khoá chống trùng (UUID tên) của một mốc. Tĩnh để test. */
    static String loyaltyKey(long characterId, long milestone) {
        return UUID.nameUUIDFromBytes(("loyalty:" + characterId + ":" + milestone).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    /** Nội dung thư song ngữ. Tĩnh để test. */
    static String loyaltyBody(long milestone) {
        long days = milestone * LOYALTY_DAYS;
        return "Cảm tạ đạo hữu đã đồng hành " + days + " ngày. / Thank you for " + days + " days with us.";
    }

    private Lot45Facades facades() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .lot44()
                .lot45();
    }
}
