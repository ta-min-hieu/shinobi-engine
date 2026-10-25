package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.guild.api.GuildWarFacade;
import dev.kage.guild.domain.GuildWar;
import dev.kage.guild.domain.GuildWarPort;
import dev.kage.guild.domain.GuildWarRules;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * Bang chiến (ADR-102) — một luồng nền chạy mỗi {@link GuildWarRules#ROSTER_PERIOD_SECONDS} giây: dựng lại
 * {@link GuildWarRoster} từ các trận đang trong giờ, báo toàn server khi một trận khai chiến, chốt trận hết giờ
 * (feature-guild làm nguyên tử, chỉ một lần) rồi gửi nốt hàng đợi thư thưởng — khoá chống trùng suy từ (trận, nhân vật)
 * nên không bao giờ gửi đôi.
 */
final class GuildWarScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(GuildWarScheduler.class);
    private static final String MAIL_SUBJECT = "Bang chiến / Guild war";

    /** Thứ luồng nền cần. */
    record Deps(GuildWarFacade wars, GuildWarRoster roster, MailFacade mail, ConnectedCharacterRegistry registry) {}

    private final Deps deps;
    private final Clock clock;
    // Trận đã báo "khai chiến" — chỉ để không báo lặp mỗi nhịp; khởi động lại giữa trận thì báo lại 1 lần.
    private final Set<Long> announcedActive = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> ticking;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "guild-war-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    GuildWarScheduler(Deps deps, Clock clock) {
        this.deps = deps;
        this.clock = clock;
    }

    void start() {
        // Nhịp đầu chạy NGAY: khởi động lại giữa trận thì luật PvP phải có lại sớm nhất có thể.
        ticking = timer.scheduleWithFixedDelay(this::tick, 0L, GuildWarRules.ROSTER_PERIOD_SECONDS, TimeUnit.SECONDS);
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
    void tick() {
        try {
            Instant now = clock.instant();
            List<GuildWar> active = new ArrayList<>();
            for (GuildWar war : deps.wars().unsettled()) {
                if (!now.isBefore(war.endsAt())) {
                    settle(war, now);
                } else if (war.isActive(now)) {
                    active.add(war);
                    announceIfNew(war);
                }
            }
            deps.roster().replace(active.isEmpty() ? List.of() : deps.wars().combatants(now), active);
            payPending();
        } catch (RuntimeException e) {
            LOG.error("Bang chiến lỗi — thử lại nhịp sau", e);
        }
    }

    private void announceIfNew(GuildWar war) {
        if (announcedActive.add(war.id())) {
            broadcast(("Bang chiến %s vs %s đã khai chiến — %d phút, hạ thành viên đối địch ngoài vùng an toàn để ghi"
                            + " điểm! / Guild war %s vs %s has begun!")
                    .formatted(
                            war.attacker().guildName(),
                            war.defender().guildName(),
                            GuildWarRules.DURATION.toMinutes(),
                            war.attacker().guildName(),
                            war.defender().guildName()));
        }
    }

    private void settle(GuildWar war, Instant now) {
        Optional<GuildWar> settled = deps.wars().settle(war.id(), now);
        announcedActive.remove(war.id());
        if (settled.isEmpty()) {
            return;
        }
        GuildWar result = settled.get();
        String score =
                "%d : %d".formatted(result.attacker().kills(), result.defender().kills());
        Optional<GuildWar.Side> winner = GuildWarRules.winner(result);
        if (winner.isPresent()) {
            broadcast("Bang chiến %s vs %s kết thúc %s — bang %s thắng! / Guild %s wins the war (%s)."
                    .formatted(
                            result.attacker().guildName(),
                            result.defender().guildName(),
                            score,
                            winner.get().guildName(),
                            winner.get().guildName(),
                            score));
        } else {
            broadcast("Bang chiến %s vs %s kết thúc hoà %s. / The guild war ended in a draw (%s)."
                    .formatted(result.attacker().guildName(), result.defender().guildName(), score, score));
        }
        LOG.info(
                "Chốt bang chiến {}: {} {}",
                result.id(),
                score,
                winner.map(GuildWar.Side::guildId).orElse(0L));
    }

    private void payPending() {
        for (GuildWarPort.Payout payout : deps.wars().pendingPayouts()) {
            if (mailReward(payout)) {
                deps.wars().markPaid(payout);
            }
        }
    }

    /** {@code true} nếu thư đã nằm trong hộp (gửi lần này hoặc đã gửi trước đó). */
    private boolean mailReward(GuildWarPort.Payout payout) {
        String body = "Bang %s đã thắng bang chiến. / Your guild %s won the guild war."
                .formatted(payout.guildName(), payout.guildName());
        String key = UUID.nameUUIDFromBytes(
                        ("guild-war-" + payout.warId() + "-" + payout.characterId()).getBytes(StandardCharsets.UTF_8))
                .toString();
        MailFacade.SendOutcome outcome = deps.mail()
                .sendMail(
                        new MailFacade.SendMailRequest(
                                null,
                                payout.characterId(),
                                MAIL_SUBJECT,
                                body,
                                GuildWarRules.WINNER_REWARD_GOLD,
                                List.of()),
                        key);
        boolean delivered = outcome.result() == MailFacade.SendResult.OK
                || outcome.result() == MailFacade.SendResult.DUPLICATE_REQUEST;
        if (!delivered) {
            LOG.warn(
                    "Thư thưởng bang chiến chưa gửi được, thử lại nhịp sau: characterId={} — {}",
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
