package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.pvp.api.PvpSeasonFacade;
import dev.kage.pvp.domain.PvpSeasonPolicy;
import dev.kage.pvp.domain.PvpSeasonPort;

/**
 * Mùa Luận Kiếm (ADR-087) — một luồng nền hỏi mỗi {@link #CHECK_PERIOD_SECONDS} giây: mùa đang mở đã hết hạn thì chốt
 * (feature-pvp làm nguyên tử, ghi luôn hàng đợi thưởng), báo toàn server; mỗi lượt cũng gửi phần hàng đợi còn lại. Khoá
 * chống trùng của mỗi thư suy từ (mùa, nhân vật) — server chết giữa vòng gửi thì lượt sau gửi tiếp, không gửi đôi.
 */
final class PvpSeasonScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PvpSeasonScheduler.class);
    private static final long CHECK_PERIOD_SECONDS = 60L;
    private static final String MAIL_SUBJECT = "Mùa Luận Kiếm / Arena season";

    private final PvpSeasonFacade seasons;
    private final MailFacade mail;
    private final ConnectedCharacterRegistry registry;
    private final Clock clock;
    private volatile ScheduledFuture<?> ticking;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pvp-season-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    PvpSeasonScheduler(PvpSeasonFacade seasons, MailFacade mail, ConnectedCharacterRegistry registry, Clock clock) {
        this.seasons = seasons;
        this.mail = mail;
        this.registry = registry;
        this.clock = clock;
    }

    void start() {
        ticking =
                timer.scheduleWithFixedDelay(this::check, CHECK_PERIOD_SECONDS, CHECK_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /** Ngừng hỏi (tắt server có trật tự) — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        ScheduledFuture<?> current = ticking;
        if (current != null) {
            current.cancel(false);
        }
        timer.shutdownNow();
    }

    // PMD AvoidCatchingGenericException: luồng hẹn giờ NGỪNG vĩnh viễn nếu một lần chạy để lọt exception.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void check() {
        try {
            Optional<PvpSeasonPort.Season> season = seasons.currentSeason();
            if (season.isPresent()
                    && !clock.instant().isBefore(season.get().endsAt())
                    && seasons.closeSeason(season.get().number(), clock.instant())) {
                announce(season.get().number());
            }
            payPending();
        } catch (RuntimeException e) {
            LOG.error("Chốt mùa Luận Kiếm lỗi — thử lại lượt sau", e);
        }
    }

    private void payPending() {
        List<PvpSeasonPort.Reward> tiers = seasons.rewards();
        for (PvpSeasonPort.Payout payout : seasons.pendingPayouts()) {
            Optional<PvpSeasonPort.Reward> tier = PvpSeasonPolicy.rewardFor(payout.rank(), tiers);
            if (tier.isEmpty() || mailReward(payout, tier.get())) {
                seasons.markPaid(payout);
            }
        }
    }

    private void announce(int number) {
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(("Mùa Luận Kiếm %d đã khép lại — thưởng đã gửi qua thư, mùa %d bắt đầu!"
                                + " / Arena season %d is over — rewards sent by mail, season %d begins!")
                        .formatted(number, number + 1, number, number + 1))
                .build();
        registry.broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, event.toByteArray());
        LOG.info("Chốt mùa Luận Kiếm {}", number);
    }

    /** {@code true} nếu thư đã nằm trong hộp (gửi lần này hoặc đã gửi trước đó). */
    private boolean mailReward(PvpSeasonPort.Payout payout, PvpSeasonPort.Reward tier) {
        List<MailFacade.AttachmentRequest> items = tier.itemTemplateId() > 0 && tier.quantity() > 0
                ? List.of(new MailFacade.AttachmentRequest(tier.itemTemplateId(), tier.quantity()))
                : List.of();
        String body = "Mùa %d: hạng %d, %d điểm. / Season %d: rank %d, %d rating."
                .formatted(
                        payout.season(),
                        payout.rank(),
                        payout.rating(),
                        payout.season(),
                        payout.rank(),
                        payout.rating());
        String key = UUID.nameUUIDFromBytes(
                        ("pvp-season-" + payout.season() + "-" + payout.characterId()).getBytes(StandardCharsets.UTF_8))
                .toString();
        MailFacade.SendOutcome outcome = mail.sendMail(
                new MailFacade.SendMailRequest(null, payout.characterId(), MAIL_SUBJECT, body, tier.gold(), items),
                key);
        boolean delivered = outcome.result() == MailFacade.SendResult.OK
                || outcome.result() == MailFacade.SendResult.DUPLICATE_REQUEST;
        if (!delivered) {
            LOG.warn(
                    "Thư thưởng mùa chưa gửi được, thử lại lượt sau: characterId={} — {}",
                    payout.characterId(),
                    outcome.result());
        }
        return delivered;
    }
}
