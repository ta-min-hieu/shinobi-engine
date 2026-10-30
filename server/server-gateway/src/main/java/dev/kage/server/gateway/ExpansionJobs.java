package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.mail.api.MailFacade;
import dev.kage.market.api.BuyOrderFacade;
import dev.kage.market.domain.BuyOrder;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;

/**
 * Tác vụ nền của lô 23 (ADR-115…126) trên MỘT bộ hẹn giờ Virtual Thread — mỗi việc một nhịp riêng, lỗi một lượt chỉ
 * log rồi lượt sau thử lại. {@link #start()} gọi ở {@code startBackgroundJobs} (KHÔNG ở constructor: test tự dựng không
 * để nhịp mồ côi), {@link #stop()} trước khi đóng pool DB.
 */
final class ExpansionJobs {

    private static final Logger LOG = LoggerFactory.getLogger(ExpansionJobs.class);
    private static final long BUY_ORDER_PERIOD_SECONDS = 60L;
    private static final int BATCH = 100;
    private static final String REFUND_SUBJECT = "Phường thị / Market";
    private static final String REFUND_BODY = "Cầu mua hết hạn — hoàn ký quỹ. / Buy order expired — escrow refunded.";

    /** Một việc định kỳ. */
    record Job(String name, long periodSeconds, Runnable work) {}

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("expansion-jobs").factory());
    private final List<Job> jobs = new ArrayList<>();

    /** Đăng ký trước {@link #start()}. */
    void add(Job job) {
        jobs.add(job);
    }

    @SuppressWarnings("FutureReturnValueIgnored") // lỗi từng lượt đã bắt và log trong runSafely
    void start() {
        for (Job job : jobs) {
            scheduler.scheduleWithFixedDelay(
                    () -> runSafely(job), job.periodSeconds(), job.periodSeconds(), TimeUnit.SECONDS);
        }
    }

    void stop() {
        ShutdownDrain.await(scheduler, "Tác vụ nền lô 23");
    }

    // PMD AvoidCatchingGenericException: ranh giới tác vụ nền — một lượt hỏng (DB chập) không được giết lịch.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void runSafely(Job job) {
        try {
            job.work().run();
        } catch (RuntimeException e) {
            LOG.warn("Lượt '{}' lỗi — lượt sau thử lại", job.name(), e);
        }
    }

    /**
     * ADR-116 — cầu mua hết hạn: đóng lệnh quá hạn, gửi thư hoàn phần ký quỹ còn lại với khoá chống trùng tất định theo
     * id lệnh, rồi đánh dấu đã hoàn. Chết giữa chừng ⇒ lượt sau gửi lại ĐÚNG khoá — không hai thư, không mất tiền.
     */
    static Job buyOrderRefunds(BuyOrderFacade orders, MailFacade mail, ConnectedCharacterRegistry registry) {
        return new Job("hoàn ký quỹ cầu mua", BUY_ORDER_PERIOD_SECONDS, () -> {
            for (BuyOrder order : orders.expireAndListUnrefunded(BATCH)) {
                refund(order, orders, mail, registry);
            }
        });
    }

    private static void refund(
            BuyOrder order, BuyOrderFacade orders, MailFacade mail, ConnectedCharacterRegistry registry) {
        if (order.escrowRemaining() == 0L) {
            orders.markRefunded(order.id());
            return;
        }
        MailFacade.SendOutcome outcome = mail.sendMail(
                new MailFacade.SendMailRequest(
                        null, order.buyerId(), REFUND_SUBJECT, REFUND_BODY, order.escrowRemaining(), List.of()),
                refundKeyOf(order.id()).toString());
        if (outcome.result() == MailFacade.SendResult.OK) {
            registry.sendToCharacterIfOnline(
                    order.buyerId(),
                    Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                    0,
                    NewMailEvent.newBuilder()
                            .setMailId(outcome.mailId())
                            .build()
                            .toByteArray());
        }
        if (outcome.result() == MailFacade.SendResult.OK
                || outcome.result() == MailFacade.SendResult.DUPLICATE_REQUEST) {
            orders.markRefunded(order.id());
        } else {
            LOG.warn("Không hoàn được ký quỹ cầu mua: orderId={}, result={}", order.id(), outcome.result());
        }
    }

    static UUID refundKeyOf(long orderId) {
        return UUID.nameUUIDFromBytes(("buy-order-expire:" + orderId).getBytes(StandardCharsets.UTF_8));
    }
}
