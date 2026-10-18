package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.mail.api.MailFacade;
import dev.kage.market.api.MarketFacade;
import dev.kage.market.domain.MarketListing;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;

/**
 * Hết hạn phường thị (ADR-063 D4), mỗi phút: (1) dòng quá hạn {@code ACTIVE → EXPIRED} — từ đó không ai mua
 * được; (2) mỗi dòng {@code EXPIRED} chưa trả: thư hệ thống kèm món cho người bán với khoá chống trùng tất
 * định theo id dòng, rồi đánh dấu đã trả. Chết giữa (1) và (2), hay giữa thư và dấu: lượt sau gửi lại ĐÚNG
 * khoá ⇒ không hai thư, không mất món.
 */
final class MarketExpiryJanitor {

    private static final Logger LOG = LoggerFactory.getLogger(MarketExpiryJanitor.class);
    private static final long PERIOD_SECONDS = 60L;
    private static final int BATCH = 100;
    private static final String SUBJECT = "Phường thị / Market";
    private static final String BODY = "Hết hạn treo bán — trả lại món. / Listing expired — item returned.";

    private final MarketFacade market;
    private final MailFacade mail;
    private final ConnectedCharacterRegistry registry;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("market-expiry").factory());

    MarketExpiryJanitor(MarketFacade market, MailFacade mail, ConnectedCharacterRegistry registry) {
        this.market = market;
        this.mail = mail;
        this.registry = registry;
    }

    @SuppressWarnings("FutureReturnValueIgnored") // lỗi từng lượt đã bắt và log trong runOnce
    void start() {
        scheduler.scheduleWithFixedDelay(this::runSafely, PERIOD_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /** Graceful shutdown (SERVER.md §10) — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        ShutdownDrain.await(scheduler, "Hết hạn phường thị");
    }

    // PMD AvoidCatchingGenericException: ranh giới tác vụ nền — một lượt hỏng (DB chập) không được giết lịch.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void runSafely() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            LOG.warn("Lượt hết hạn phường thị lỗi — lượt sau thử lại", e);
        }
    }

    /** Một lượt — package-private cho test. */
    void runOnce() {
        List<MarketListing> due = market.expireAndListUnreturned(BATCH);
        for (MarketListing listing : due) {
            MailFacade.SendOutcome outcome = mail.sendMail(
                    new MailFacade.SendMailRequest(
                            null,
                            listing.sellerId(),
                            SUBJECT,
                            BODY,
                            0L,
                            List.of(new MailFacade.AttachmentRequest(listing.itemTemplateId(), listing.quantity()))),
                    keyOf(listing.id()).toString());
            if (outcome.result() == MailFacade.SendResult.OK) {
                registry.sendToCharacterIfOnline(
                        listing.sellerId(),
                        Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                        0,
                        NewMailEvent.newBuilder()
                                .setMailId(outcome.mailId())
                                .build()
                                .toByteArray());
            }
            if (outcome.result() == MailFacade.SendResult.OK
                    || outcome.result() == MailFacade.SendResult.DUPLICATE_REQUEST) {
                market.markReturned(listing.id());
            } else {
                LOG.warn(
                        "Không trả được món phường thị hết hạn: listingId={}, result={}",
                        listing.id(),
                        outcome.result());
            }
        }
    }

    static UUID keyOf(long listingId) {
        return UUID.nameUUIDFromBytes(("market-expire:" + listingId).getBytes(StandardCharsets.UTF_8));
    }
}
