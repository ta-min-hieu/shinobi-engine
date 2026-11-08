package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.achievement.domain.AchievementKind;
import dev.kage.character.domain.LifetimeStatsPort;
import dev.kage.protocol.CalendarEntry;
import dev.kage.protocol.CalendarEventKind;
import dev.kage.protocol.GetEventCalendarResponse;
import dev.kage.protocol.GetPersonalStatsResponse;

/** Lớp lệnh lô 37 (ADR-223…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
final class Lot37Commands {

    /**
     * Mốc vào world của phiên đang chơi — DÙNG CHUNG cả server (lớp lệnh dựng theo từng kết nối), khoá theo registry
     * như {@code GuildInviteCommands}.
     */
    private static final Map<ConnectedCharacterRegistry, Map<Long, Instant>> SESSIONS_BY_REGISTRY =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Nguồn dữ liệu; {@code proximity} cho danh sách NPC đặt tĩnh (NPC giao nhiệm vụ, ADR-228). */
    record Deps(GatewayFacades gateway, ConnectedCharacterRegistry registry, NpcProximity proximity, Clock clock) {}

    /** ADR-227 — gói quà tân thủ: số Tiểu Hồi Xuân Đan và linh thạch. PLACEHOLDER cân bằng (Điều 27). */
    static final int STARTER_PILLS = 5;

    static final long STARTER_GOLD = 200L;

    private static final String STARTER_PILL_CODE = "minor_healing_pill";

    private final Deps deps;
    private final Map<Long, Instant> sessions;
    private final Lot38Commands lot38;

    Lot37Commands(Deps deps) {
        this.deps = deps;
        this.sessions = SESSIONS_BY_REGISTRY.computeIfAbsent(deps.registry(), r -> new ConcurrentHashMap<>());
        this.lot38 = Lot38Commands.of(
                deps.gateway(), deps.registry()); // lô 38 lồng ở đây (trần MethodLength của ExpansionCommands.of)
    }

    /** Lớp lệnh lô 38 (ADR-231…). */
    Lot38Commands lot38() {
        return lot38;
    }

    static Lot37Commands of(GatewayFacades gateway, WorldGateway worldGateway, NpcProximity proximity) {
        return new Lot37Commands(new Deps(gateway, worldGateway.registry(), proximity, Clock.systemUTC()));
    }

    /**
     * ADR-227 — vừa tạo nhân vật: gửi MỘT thư quà tân thủ (cờ trong DB bảo đảm đúng một lần; khoá chống trùng suy từ
     * id). Việc phụ trên luồng blocking của lệnh tạo nhân vật — lỗi dừng ở đây, không làm hỏng việc tạo.
     */
    // PMD AvoidCatchingGenericException: việc phụ — không được làm hỏng lệnh tạo nhân vật.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void grantStarterGift(long characterId) {
        try {
            if (!facades().starterGift().markSent(characterId)) {
                return;
            }
            java.util.List<dev.kage.mail.api.MailFacade.AttachmentRequest> items =
                    deps.gateway().inventoryFacade().itemCatalog().entries().stream()
                            .filter(entry -> STARTER_PILL_CODE.equals(entry.code()))
                            .map(entry -> new dev.kage.mail.api.MailFacade.AttachmentRequest(entry.id(), STARTER_PILLS))
                            .toList();
            deps.gateway()
                    .contentFacades()
                    .socialFacades()
                    .mailFacade()
                    .sendMail(
                            new dev.kage.mail.api.MailFacade.SendMailRequest(
                                    null,
                                    characterId,
                                    "Quà nhập môn / Welcome gift",
                                    "Chúc đạo hữu tu hành thuận lợi! / May your cultivation go smoothly!",
                                    STARTER_GOLD,
                                    items),
                            java.util
                                    .UUID
                                    .nameUUIDFromBytes(("starter-gift:" + characterId)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                    .toString());
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(Lot37Commands.class)
                    .warn("Gửi quà tân thủ lỗi: characterId={}", characterId, e);
        }
    }

    /** ADR-228 — Virtual Thread; nên làm nhiệm vụ nào tiếp và gặp NPC nào. */
    dev.kage.protocol.GetNextQuestHintResponse nextQuestHint(long characterId) {
        java.util.Map<String, dev.kage.quest.domain.QuestStatus> log = new java.util.LinkedHashMap<>();
        deps.gateway()
                .contentFacades()
                .questFacade()
                .listQuests(characterId)
                .forEach(entry -> log.put(entry.questCode(), entry.status()));
        return NextQuestHint.of(
                log,
                facades().questCatalog().all(),
                QuestGivers.index(deps.proximity().staticNpcCodes()));
    }

    /** ADR-230 — Virtual Thread; bật / tắt (hoặc chỉ hỏi) khoe thành tựu vào bang. */
    dev.kage.protocol.SetAchievementShareResponse setAchievementShare(
            long characterId, dev.kage.protocol.SetAchievementShareCommand command) {
        if (!command.getQueryOnly()) {
            facades().achievementShare().setEnabled(characterId, command.getEnabled());
        }
        return dev.kage.protocol.SetAchievementShareResponse.newBuilder()
                .setEnabled(facades().achievementShare().enabled(characterId))
                .build();
    }

    /** ADR-223 — vừa vào world: ghi mốc bắt đầu phiên. */
    void onEnteredWorld(long characterId) {
        sessions.put(characterId, deps.clock().instant());
        lot38.lot39().onEnteredWorld(characterId); // ADR-240 — nạp kỷ lục
    }

    /** ADR-223 — rời world: cộng giây của phiên vào tổng giờ chơi (chạm DB — luồng blocking của handler). */
    void onLeftWorld(long characterId) {
        Instant started = sessions.remove(characterId);
        if (started != null) {
            lifetime()
                    .addPlaytime(
                            characterId,
                            Duration.between(started, deps.clock().instant()).toSeconds());
        }
    }

    /** ADR-223 — Virtual Thread; bảng thống kê cá nhân, giờ chơi gồm cả phiên đang chơi. */
    GetPersonalStatsResponse personalStats(long characterId) {
        LifetimeStatsPort.Stats stats = lifetime().of(characterId);
        Instant started = sessions.get(characterId);
        long current = started == null
                ? 0L
                : Duration.between(started, deps.clock().instant()).toSeconds();
        Map<AchievementKind, Long> counters =
                deps.registry().achievements.facade().counters(characterId);
        dev.kage.pvp.application.GetPvpRatingUseCase.RatingView pvp =
                deps.gateway().contentFacades().socialFacades().pvpFacade().findRating(characterId);
        return GetPersonalStatsResponse.newBuilder()
                .setMonstersKilled(counters.getOrDefault(AchievementKind.KILL_MONSTERS, 0L))
                .setQuestsClaimed(counters.getOrDefault(AchievementKind.CLAIM_QUESTS, 0L))
                .setDeaths(stats.deaths())
                .setPvpWins(pvp.wins())
                .setPvpLosses(pvp.losses())
                .setAchievementsUnlocked(deps.registry().achievements.facade().unlockedCount(characterId))
                .setPlaytimeSeconds(stats.playtimeSeconds() + Math.max(0L, current))
                .build();
    }

    /** ADR-224 — Virtual Thread; các mốc sự kiện sắp tới, sớm nhất trước. */
    GetEventCalendarResponse calendar() {
        Instant now = deps.clock().instant();
        GetEventCalendarResponse.Builder response =
                GetEventCalendarResponse.newBuilder().setServerNowEpochSeconds(now.getEpochSecond());
        WorldBossTimes boss = deps.registry().worldBossTimes;
        boss.nextSpawnAt()
                .ifPresent(at -> response.addEntries(entry(CalendarEventKind.CALENDAR_EVENT_KIND_WORLD_BOSS, at)
                        .setActive(boss.alive())));
        response.addEntries(entry(CalendarEventKind.CALENDAR_EVENT_KIND_DAILY_RESET, ResetSchedule.nextDaily(now)));
        response.addEntries(entry(CalendarEventKind.CALENDAR_EVENT_KIND_WEEKLY_RESET, ResetSchedule.nextWeekly(now)));
        Instant tournament = deps.gateway()
                .progression()
                .expansion()
                .tournament()
                .current(now)
                .startAt();
        response.addEntries(entry(CalendarEventKind.CALENDAR_EVENT_KIND_TOURNAMENT, tournament)
                .setActive(!now.isBefore(tournament)));
        java.util.List<CalendarEntry> sorted = new java.util.ArrayList<>(response.getEntriesList());
        sorted.sort(java.util.Comparator.comparingLong(CalendarEntry::getAtEpochSeconds));
        return response.clearEntries().addAllEntries(sorted).build();
    }

    private static CalendarEntry.Builder entry(CalendarEventKind kind, Instant at) {
        return CalendarEntry.newBuilder().setKind(kind).setAtEpochSeconds(at.getEpochSecond());
    }

    private dev.kage.character.api.LifetimeStatsFacade lifetime() {
        return facades().lifetime();
    }

    private Lot37Facades facades() {
        return deps.gateway().progression().expansion().lot34().lot35().lot36().lot37();
    }
}
