package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.SpiritVeinFacade;
import dev.kage.guild.domain.SpiritVein;
import dev.kage.guild.domain.SpiritVeinPort;
import dev.kage.guild.domain.SpiritVeinRules;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * Tranh đoạt Linh Mạch (ADR-090) — một luồng nền chạy mỗi {@link SpiritVeinRules#POINT_PERIOD_SECONDS} giây. Khung
 * đang mở: đếm người đứng trong vùng theo bang (vị trí đã biết của world chính; ai đang ở hầm ngục/Arena không tính),
 * bang đông nhất được 1 điểm. Khung vừa đóng: chốt (feature-guild làm nguyên tử, chỉ một lần), báo toàn server. Mỗi
 * lượt gửi nốt hàng đợi thư thưởng — khoá chống trùng suy từ (Linh Mạch, khung, nhân vật) nên không bao giờ gửi đôi.
 */
final class SpiritVeinScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SpiritVeinScheduler.class);
    private static final String MAIL_SUBJECT = "Linh Mạch / Spirit vein";

    /**
     * Thứ luồng nền cần.
     *
     * @param inInstance nhân vật đang ở hầm ngục/Arena — vị trí đã biết của họ là của world chính cũ, không tính
     */
    record Deps(
            SpiritVeinFacade veins,
            GuildFacade guilds,
            MailFacade mail,
            ConnectedCharacterRegistry registry,
            LongPredicate inInstance) {}

    private final Deps deps;
    private final Clock clock;
    // Khung đã báo "mở" (theo mã Linh Mạch) — chỉ để không báo lặp mỗi nhịp; khởi động lại giữa khung thì báo lại 1
    // lần.
    private final Map<String, Instant> announcedOpen = new ConcurrentHashMap<>();
    // Khung đã chốt (theo mã Linh Mạch) — để không mở giao dịch chốt lại mỗi nhịp; DB vẫn là chốt chặn chạy-một-lần.
    private final Map<String, Instant> settledWindow = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> ticking;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "spirit-vein-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    SpiritVeinScheduler(Deps deps, Clock clock) {
        this.deps = deps;
        this.clock = clock;
    }

    void start() {
        long period = SpiritVeinRules.POINT_PERIOD_SECONDS;
        ticking = timer.scheduleWithFixedDelay(this::tick, period, period, TimeUnit.SECONDS);
    }

    /** Ngừng (tắt server có trật tự) — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        ScheduledFuture<?> current = ticking;
        if (current != null) {
            current.cancel(false);
        }
        timer.shutdownNow();
    }

    // PMD AvoidCatchingGenericException: luồng hẹn giờ NGỪNG vĩnh viễn nếu một lần chạy để lọt exception.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void tick() {
        try {
            Instant now = clock.instant();
            for (SpiritVein vein : deps.veins().veins()) {
                Optional<Instant> active = SpiritVeinRules.activeWindowStart(vein.schedule(), now);
                if (active.isPresent()) {
                    contest(vein, active.get(), now);
                }
                SpiritVeinRules.lastClosedWindowStart(vein.schedule(), now).ifPresent(start -> settle(vein, start));
            }
            payPending();
        } catch (RuntimeException e) {
            LOG.error("Tranh đoạt Linh Mạch lỗi — thử lại nhịp sau", e);
        }
    }

    private void contest(SpiritVein vein, Instant windowStart, Instant now) {
        if (!windowStart.equals(announcedOpen.put(vein.code(), windowStart))) {
            broadcast(("Linh Mạch đã mở tranh đoạt trong %d phút — bang nào đông người trong vùng X %.0f..%.0f nhất sẽ"
                            + " giữ được! / The spirit vein is contested for %d minutes — hold X %.0f..%.0f!")
                    .formatted(
                            vein.schedule().durationMinutes(),
                            vein.zone().minX(),
                            vein.zone().maxX(),
                            vein.schedule().durationMinutes(),
                            vein.zone().minX(),
                            vein.zone().maxX()));
        }
        Map<Long, Integer> occupants = new HashMap<>();
        for (Map.Entry<Long, PositionSnapshot> entry :
                deps.registry().snapshotPositions().entrySet()) {
            long characterId = entry.getKey();
            if (vein.zone().contains(entry.getValue().x()) && !deps.inInstance().test(characterId)) {
                deps.guilds()
                        .findMyGuild(characterId)
                        .ifPresent(guild -> occupants.merge(guild.guildId(), 1, Integer::sum));
            }
        }
        SpiritVeinRules.occupier(occupants)
                .ifPresent(guildId -> deps.veins().addPoint(vein.code(), windowStart, guildId, now));
    }

    private void settle(SpiritVein vein, Instant windowStart) {
        if (windowStart.equals(settledWindow.get(vein.code()))) {
            return;
        }
        Instant heldUntil = SpiritVeinRules.windowEnd(vein.schedule(), windowStart)
                .plus(Duration.ofDays(SpiritVeinRules.HOLD_DAYS));
        deps.veins().settle(vein.code(), windowStart, heldUntil).ifPresent(winner -> {
            broadcast(
                    "Bang %s chiếm được Linh Mạch với %d điểm — thưởng đã gửi qua thư! / Guild %s holds the spirit vein"
                                    .formatted(winner.guildName(), winner.points(), winner.guildName())
                            + " with %d points — rewards sent by mail!".formatted(winner.points()));
            LOG.info(
                    "Chốt Linh Mạch {} khung {}: bang {} ({} điểm)",
                    vein.code(),
                    windowStart,
                    winner.guildId(),
                    winner.points());
        });
        settledWindow.put(vein.code(), windowStart);
    }

    private void payPending() {
        List<SpiritVeinPort.Payout> pending = deps.veins().pendingPayouts();
        if (pending.isEmpty()) {
            return;
        }
        Map<String, SpiritVein> byCode = new HashMap<>();
        deps.veins().veins().forEach(vein -> byCode.put(vein.code(), vein));
        for (SpiritVeinPort.Payout payout : pending) {
            SpiritVein vein = byCode.get(payout.veinCode());
            if (vein == null || mailReward(payout, vein.reward())) {
                deps.veins().markPaid(payout);
            }
        }
    }

    /** {@code true} nếu thư đã nằm trong hộp (gửi lần này hoặc đã gửi trước đó). */
    private boolean mailReward(SpiritVeinPort.Payout payout, SpiritVein.Reward reward) {
        List<MailFacade.AttachmentRequest> items = reward.itemTemplateId() > 0 && reward.quantity() > 0
                ? List.of(new MailFacade.AttachmentRequest(reward.itemTemplateId(), reward.quantity()))
                : List.of();
        String body = "Bang %s đã chiếm Linh Mạch. / Your guild %s holds the spirit vein."
                .formatted(payout.guildName(), payout.guildName());
        String key = UUID.nameUUIDFromBytes(("spirit-vein-" + payout.veinCode() + "-"
                                + payout.windowStart().getEpochSecond() + "-" + payout.characterId())
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
        MailFacade.SendOutcome outcome = deps.mail()
                .sendMail(
                        new MailFacade.SendMailRequest(
                                null, payout.characterId(), MAIL_SUBJECT, body, reward.gold(), items),
                        key);
        boolean delivered = outcome.result() == MailFacade.SendResult.OK
                || outcome.result() == MailFacade.SendResult.DUPLICATE_REQUEST;
        if (!delivered) {
            LOG.warn(
                    "Thư thưởng Linh Mạch chưa gửi được, thử lại nhịp sau: characterId={} — {}",
                    payout.characterId(),
                    outcome.result());
        }
        return delivered;
    }

    private void broadcast(String body) {
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build();
        deps.registry().broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, event.toByteArray());
    }
}
