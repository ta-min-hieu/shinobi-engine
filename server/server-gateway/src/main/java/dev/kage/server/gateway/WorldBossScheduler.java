package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.mail.api.MailFacade;
import dev.kage.monster.api.WorldBossFacade;
import dev.kage.monster.domain.WorldBossRanking;
import dev.kage.monster.domain.WorldBossReward;
import dev.kage.monster.domain.WorldBossSchedule;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.server.world.WorldBossListener;
import dev.kage.server.world.WorldFacade;

/**
 * Boss thế giới định kỳ (ADR-081) — một luồng nền hỏi lịch mỗi {@link #CHECK_PERIOD}: tới giờ mà con trước đã chết
 * thì spawn ở khu vực ĐẦU TIÊN của world chính (khu vực không bao giờ đóng) và báo toàn server. Boss chết ⇒
 * server-world gọi {@link #onWorldBossDefeated} trên tick thread; việc thật (cộng sổ, gửi thư thưởng theo hạng,
 * báo) chuyển sang luồng nền ngay.
 *
 * <p>Lần xuất hiện đầu sau khởi động: hết một chu kỳ, trừ khi {@code KAGE_WORLD_BOSS_FIRST_SPAWN_SECONDS} đặt sớm
 * hơn (kiểm sống/bảo trì). Trạng thái "đang sống" chỉ nằm trong RAM — khởi động lại là boss cũ mất cùng world.
 */
final class WorldBossScheduler implements WorldBossListener {

    private static final Logger LOG = LoggerFactory.getLogger(WorldBossScheduler.class);
    private static final Duration CHECK_PERIOD = Duration.ofSeconds(15);
    private static final String FIRST_SPAWN_ENV = "KAGE_WORLD_BOSS_FIRST_SPAWN_SECONDS";
    private static final String MAIL_SUBJECT = "Boss thế giới / World boss";

    /** Các facade + nơi phát tin cần cho một trận boss — gom để constructor không vượt trần tham số. */
    record Deps(
            WorldBossFacade bosses, MailFacade mail, CharacterFacade characters, ConnectedCharacterRegistry registry) {}

    private final WorldFacade world;
    private final Deps deps;
    private final Clock clock;
    private final Map<String, Instant> nextSpawnAt = new ConcurrentHashMap<>();
    // Lịch là tham số — đọc MỘT lần lúc start(): vòng hỏi 15 s không được chạm DB (lúc tắt server pool đã đóng).
    private volatile List<WorldBossSchedule> schedules = List.of();
    private final Set<String> alive = ConcurrentHashMap.newKeySet();
    // Future của vòng hỏi lịch — {@link #stop} huỷ nó (luồng daemon cũng chết cùng tiến trình).
    private volatile ScheduledFuture<?> ticking;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "world-boss-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    WorldBossScheduler(WorldFacade world, Deps deps, Clock clock) {
        this.world = world;
        this.deps = deps;
        this.clock = clock;
    }

    /** Nạp lịch, nghe boss chết, bắt đầu hỏi định kỳ. Gọi một lần lúc khởi động. */
    void start() {
        Optional<Duration> firstOverride = firstSpawnOverride();
        Instant now = clock.instant();
        schedules = List.copyOf(deps.bosses().schedules());
        for (WorldBossSchedule schedule : schedules) {
            Duration wait = firstOverride.orElse(Duration.ofMinutes(schedule.intervalMinutes()));
            nextSpawnAt.put(schedule.monsterCode(), now.plus(wait));
        }
        world.setWorldBossListener(this);
        ticking = timer.scheduleWithFixedDelay(this::checkSchedules, 0, CHECK_PERIOD.toSeconds(), TimeUnit.SECONDS);
        LOG.info("Boss thế giới: {} lịch", nextSpawnAt.size());
    }

    /** Ngừng hỏi lịch (test, hoặc tắt server có trật tự). Boss đang sống vẫn sống tới khi world dừng. */
    void stop() {
        ScheduledFuture<?> current = ticking;
        if (current != null) {
            current.cancel(false);
        }
        timer.shutdownNow();
    }

    private static Optional<Duration> firstSpawnOverride() {
        String raw = System.getenv(FIRST_SPAWN_ENV);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0L, Long.parseLong(raw.strip()))));
        } catch (NumberFormatException e) {
            LOG.warn("{}='{}' không phải số giây — dùng chu kỳ của lịch", FIRST_SPAWN_ENV, raw, e);
            return Optional.empty();
        }
    }

    // PMD AvoidCatchingGenericException: luồng hẹn giờ NGỪNG vĩnh viễn nếu một lần chạy để lọt exception.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void checkSchedules() {
        try {
            Instant now = clock.instant();
            for (WorldBossSchedule schedule : schedules) {
                Instant due = nextSpawnAt.get(schedule.monsterCode());
                if (due != null && !now.isBefore(due) && alive.add(schedule.monsterCode())) {
                    world.spawnWorldBoss(schedule.monsterCode(), schedule.spawnX(), schedule.spawnY());
                    announce(
                            "Yêu Vương đã xuất hiện ở phía tây tông môn (x=%d)! / The Demon King has appeared west of"
                                    + " the sect (x=%d)!",
                            Math.round(schedule.spawnX()));
                    LOG.info("Boss thế giới xuất hiện: {}", schedule.monsterCode());
                }
            }
        } catch (RuntimeException e) {
            LOG.error("Lịch boss thế giới lỗi — thử lại lượt sau", e);
        }
    }

    /** Tick thread — chỉ đẩy việc sang luồng nền. */
    @Override
    public void onWorldBossDefeated(String monsterCode, Map<Long, Long> damageByCharacter) {
        timer.execute(() -> settle(monsterCode, damageByCharacter));
    }

    // PMD AvoidCatchingGenericException: cùng lý do checkSchedules — một trận lỗi không được làm chết lịch.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void settle(String monsterCode, Map<Long, Long> damageByCharacter) {
        alive.remove(monsterCode);
        try {
            int interval = schedules.stream()
                    .filter(schedule -> schedule.monsterCode().equals(monsterCode))
                    .mapToInt(WorldBossSchedule::intervalMinutes)
                    .findFirst()
                    .orElse(1);
            nextSpawnAt.put(monsterCode, clock.instant().plus(Duration.ofMinutes(interval)));
            deps.bosses().addDamage(damageByCharacter);
            List<WorldBossRanking.Entry> ranked = WorldBossRanking.rank(damageByCharacter);
            List<WorldBossReward> tiers = deps.bosses().rewards(monsterCode);
            for (WorldBossRanking.Entry entry : ranked) {
                WorldBossRanking.rewardFor(entry.rank(), tiers).ifPresent(tier -> mailReward(entry, tier));
            }
            String top = ranked.isEmpty() ? "?" : nameOf(ranked.getFirst().characterId());
            announce(
                    "Yêu Vương đã bị hạ! %s gây nhiều sát thương nhất. / The Demon King has fallen! Top damage: %s.",
                    top);
            LOG.info("Boss thế giới bị hạ: {} — {} người tham gia", monsterCode, ranked.size());
        } catch (RuntimeException e) {
            LOG.error("Chốt boss thế giới lỗi: {}", monsterCode, e);
        }
    }

    private void mailReward(WorldBossRanking.Entry entry, WorldBossReward tier) {
        List<MailFacade.AttachmentRequest> items = tier.hasItem()
                ? List.of(new MailFacade.AttachmentRequest(tier.itemTemplateId(), tier.quantity()))
                : List.of();
        String body = "Hạng %d, %d sát thương. / Rank %d, %d damage."
                .formatted(entry.rank(), entry.damage(), entry.rank(), entry.damage());
        MailFacade.SendOutcome outcome = deps.mail()
                .sendMail(
                        new MailFacade.SendMailRequest(
                                null, entry.characterId(), MAIL_SUBJECT, body, tier.gold(), items),
                        UUID.randomUUID().toString());
        if (outcome.result() != MailFacade.SendResult.OK) {
            LOG.warn(
                    "Thư thưởng boss thế giới không gửi được: characterId={} — {}",
                    entry.characterId(),
                    outcome.result());
        }
    }

    private String nameOf(long characterId) {
        return deps.characters().findById(characterId).map(Character::name).orElse("#" + characterId);
    }

    /** Dòng HỆ THỐNG toàn server, song ngữ trên một dòng (cùng khuôn thông báo linh thú lên cấp). */
    private void announce(String template, Object value) {
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(template.formatted(value, value))
                .build();
        deps.registry().broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, event.toByteArray());
    }
}
