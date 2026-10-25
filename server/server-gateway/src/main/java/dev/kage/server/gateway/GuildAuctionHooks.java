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

import dev.kage.guild.api.GuildAuctionFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.domain.GuildAuctionPort;
import dev.kage.guild.domain.GuildAuctionRules;
import dev.kage.mail.api.MailFacade;

/**
 * Đấu giá bang (ADR-112) — hai việc nền: mở lô khi boss Bí Cảnh chết (D1) và một luồng hẹn giờ mỗi
 * {@link GuildAuctionRules#SETTLE_PERIOD_SECONDS} giây chốt lô hết giờ rồi gửi hàng đợi thư (D3). Khoá chống trùng của
 * thư suy từ id dòng hàng đợi nên gửi lại không bao giờ thành gửi đôi. Nối sau khi DB đã dựng, cùng khuôn
 * {@link CollectionHooks}.
 */
final class GuildAuctionHooks {

    private static final Logger LOG = LoggerFactory.getLogger(GuildAuctionHooks.class);
    private static final String SUBJECT = "Đấu giá bang / Guild auction";

    /** Thứ việc nền cần. */
    record Deps(GuildAuctionFacade auctions, GuildFacade guilds, MailFacade mail, Clock clock) {}

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "guild-auction");
        thread.setDaemon(true);
        return thread;
    });
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();
    private volatile ScheduledFuture<?> ticking;

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /**
     * Bắt đầu nhịp chốt — gọi cùng các luồng nền khác ({@code GatewayBootstrap#startBackgroundJobs}), KHÔNG ở
     * {@link #bind}: composition của test dựng facade mà không đi đường tắt server chuẩn, và một nhịp mồ côi sẽ chạy
     * tiếp sau khi pool DB của test đã đóng.
     */
    void start() {
        ticking = timer.scheduleWithFixedDelay(
                this::tick,
                GuildAuctionRules.SETTLE_PERIOD_SECONDS,
                GuildAuctionRules.SETTLE_PERIOD_SECONDS,
                TimeUnit.SECONDS);
    }

    /** Facade đang nối (lệnh của client). */
    GuildAuctionFacade auctions() {
        return deps.map(Deps::auctions).orElse(GuildAuctionFacade.DISABLED);
    }

    /**
     * Boss Bí Cảnh vừa chết — đã ở Virtual Thread của đường thưởng. Người ra đòn cuối có bang thì mở một lô cho bang
     * đó; người tham gia là những người trong {@code members} cùng bang.
     */
    // PMD AvoidCatchingGenericException: việc phụ — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onBossKilled(long killerId, List<Long> members) {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return;
        }
        Deps d = current.get();
        try {
            d.guilds().findMyGuild(killerId).ifPresent(guild -> {
                List<Long> sameGuild = members.stream()
                        .filter(id -> d.guilds()
                                .findMyGuild(id)
                                .map(g -> g.guildId() == guild.guildId())
                                .orElse(false))
                        .toList();
                d.auctions()
                        .createLot(new GuildAuctionPort.NewLot(
                                guild.guildId(), killerId, sameGuild, d.clock().instant()))
                        .ifPresent(lot -> LOG.info("Mở lô đấu giá bang {} cho bang {}", lot.id(), guild.guildId()));
            });
        } catch (RuntimeException e) {
            LOG.warn("Mở lô đấu giá bang lỗi — MẤT: killerId={}", killerId, e);
        }
    }

    // PMD AvoidCatchingGenericException: luồng hẹn giờ NGỪNG vĩnh viễn nếu một lần chạy để lọt exception.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void tick() {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return;
        }
        Deps d = current.get();
        try {
            int settled = d.auctions().settleDue(d.clock().instant());
            if (settled > 0) {
                LOG.info("Chốt {} lô đấu giá bang", settled);
            }
            for (GuildAuctionPort.Payout payout : d.auctions().pendingPayouts()) {
                if (deliver(d.mail(), payout)) {
                    d.auctions().markPaid(payout.id());
                }
            }
        } catch (RuntimeException e) {
            LOG.error("Đấu giá bang lỗi — thử lại nhịp sau", e);
        }
    }

    /** {@code true} nếu thư đã nằm trong hộp (gửi lần này hoặc trước đó). */
    private static boolean deliver(MailFacade mail, GuildAuctionPort.Payout payout) {
        String key = UUID.nameUUIDFromBytes(("guild-auction-payout-" + payout.id()).getBytes(StandardCharsets.UTF_8))
                .toString();
        List<MailFacade.AttachmentRequest> items = payout.itemTemplateId() == 0L
                ? List.of()
                : List.of(new MailFacade.AttachmentRequest(payout.itemTemplateId(), payout.quantity()));
        MailFacade.SendOutcome outcome = mail.sendMail(
                new MailFacade.SendMailRequest(
                        null, payout.characterId(), SUBJECT, body(payout.kind()), payout.gold(), items),
                key);
        boolean delivered = outcome.result() == MailFacade.SendResult.OK
                || outcome.result() == MailFacade.SendResult.DUPLICATE_REQUEST;
        if (!delivered) {
            LOG.warn("Thư đấu giá bang chưa gửi được, thử lại nhịp sau: payout={} — {}", payout.id(), outcome.result());
        }
        return delivered;
    }

    private static String body(GuildAuctionPort.PayoutKind kind) {
        return switch (kind) {
            case REFUND -> "Bạn đã bị trả giá vượt — hoàn lại linh thạch. / You were outbid — gold refunded.";
            case ITEM -> "Vật phẩm từ đấu giá bang. / Your guild auction item.";
            case SHARE -> "Phần chia tiền đấu giá bang. / Your share of the guild auction.";
        };
    }

    /** Tắt server có trật tự — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        ScheduledFuture<?> current = ticking;
        if (current != null) {
            current.cancel(false);
        }
        // shutdown (không Now) + chờ: nhịp đang chạy dở phải chốt xong TRƯỚC khi pool DB đóng — không ngắt giữa
        // giao dịch (fuzz ngắt kết nối từng bắt được "HikariDataSource has been closed" ở đây).
        timer.shutdown();
        ShutdownDrain.await(timer, "Chốt đấu giá bang");
    }
}
