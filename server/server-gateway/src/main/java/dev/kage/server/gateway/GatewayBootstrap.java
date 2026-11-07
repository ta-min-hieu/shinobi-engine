package dev.kage.server.gateway;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.account.api.AccountFacade;
import dev.kage.character.api.CharacterFacade;
import dev.kage.chat.api.ChatFacade;
import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.engine.scripting.ScriptSandbox;
import dev.kage.engine.scripting.ScriptValidator;
import dev.kage.friend.api.FriendFacade;
import dev.kage.gm.domain.GameEventKind;
import dev.kage.guild.api.GuildFacade;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.party.api.PartyFacade;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.quest.api.QuestFacade;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.world.Element;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;
import dev.kage.shop.api.ShopFacade;
import dev.kage.trade.api.TradeFacade;

/**
 * Entrypoint {@code main()} mỏng — logic thật ở {@link GatewayServer} (tách ra để test end-to-end
 * bằng socket thật được, xem {@code GatewayServerIntegrationTest}).
 *
 * <p>Composition root — nơi DUY NHẤT server-gateway chạm tới {@link ServerDatabaseBootstrap}, và
 * chỉ để lấy {@code AccountFacade} đã lắp ráp sẵn (không bao giờ thấy DataSource/RedisClient trực
 * tiếp — xem javadoc {@link ServerDatabaseBootstrap}, Điều 23).
 */
public final class GatewayBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayBootstrap.class);

    private static final int PORT = 7777;

    // Metrics scrape endpoint (ADR-015, Phase 9 lát cắt 1) — cổng riêng biệt cổng game protocol,
    // chưa có config file (MVP, cùng tinh thần PORT ở trên — tinh chỉnh khi cần thật).
    private static final int METRICS_PORT = 9404;
    private static final String WORLD_TAG_MAIN = "main";

    // Chỉ dùng để PARSE (validateSyntax), không thực thi — giới hạn statement không có ý nghĩa ở
    // đây (khác ScriptSandbox dùng lúc gameplay thật), giữ số dương bất kỳ cho hợp constructor.
    private static final long SCRIPT_VALIDATION_STATEMENT_LIMIT = 1L;

    // SERVER.md §8 gợi ý "định kỳ 5 phút" cho dữ liệu tái tạo được (vị trí, HP) — chưa đo, đủ cho
    // lát cắt persistence này (tinh chỉnh 1 chỗ khi có số liệu thật, giống MOVE_SPEED_MPS/AOI_RADIUS).
    private static final Duration POSITION_SAVE_INTERVAL = Duration.ofMinutes(5);

    // ADR-099 — thưởng lần đầu hạ boss Bí Cảnh (PLACEHOLDER cân bằng).
    private static final long FIRST_CLEAR_GOLD = 5_000L;
    private static final String FIRST_CLEAR_ITEM_CODE = "demon_core";
    private static final int FIRST_CLEAR_ITEM_QUANTITY = 3;

    // ADR-122 — giờ phiên hỏi đáp tông môn (giờ VN), "HH:mm,HH:mm"; không đặt/hỏng ⇒ 20:00.
    private static final String QUIZ_TIMES_ENV = "KAGE_QUIZ_TIMES";
    private static final LocalTime DEFAULT_QUIZ_START = LocalTime.of(20, 0);

    // ADR-125 — lịch luận võ đại hội "DAY HH:mm" (giờ VN); không đặt/hỏng ⇒ "SUNDAY 20:00".
    private static final String TOURNAMENT_AT_ENV = "KAGE_TOURNAMENT_AT";

    private GatewayBootstrap() {}

    public static void main(String[] args) throws InterruptedException, SSLException {
        // buildMetrics() dựng TRƯỚC ServerDatabaseBootstrap (khác thứ tự cũ) vì
        // MicrometerDbMetricsPort (OBSERVABILITY.md §3.3 kage.db.query.duration/
        // kage.db.transaction.failed) cần meterRegistry lúc construct, và ServerDatabaseBootstrap
        // cấu hình JdbcOperations/JdbcTransactionRunner với port này NGAY trong constructor —
        // trước khi bất kỳ Postgres*Adapter nào chạy query đầu tiên.
        MetricsContext metricsContext = buildMetrics();
        ServerDatabaseBootstrap databaseBootstrap =
                new ServerDatabaseBootstrap(new MicrometerDbMetricsPort(metricsContext.meterRegistry()));
        Assembly gateway = assemble(
                databaseBootstrap,
                metricsContext,
                new GatewayTracing(),
                new Endpoints(PORT, METRICS_PORT, TlsConfig.fromEnvironment()));
        startOrShutDown(gateway.server(), gateway.shutdown());
        Runtime.getRuntime().addShutdownHook(new Thread(gateway.shutdown(), "gateway-shutdown"));
        gateway.server().awaitTermination();
    }

    /** Cổng game, cổng metrics và cấu hình TLS — thứ duy nhất test đổi so với production. */
    record Endpoints(int gamePort, int metricsPort, TlsConfig tlsConfig) {}

    /** Server đã lắp xong (CHƯA mở cổng) kèm trình tự tắt của chính nó — xem {@link #assemble}. */
    record Assembly(GatewayServer server, Runnable shutdown) {}

    /**
     * Lắp toàn bộ server từ DB đã kết nối — world chính đã tick, luồng lưu vị trí và máy chủ metrics đã
     * chạy, cổng game CHƯA mở ({@link #startOrShutDown} mở).
     *
     * <p>Tách khỏi {@code main()} (2026-10-10) để test tắt/khởi động lại
     * ({@code ServerShutdownMidPlayDatabaseTest}) chạy ĐÚNG phép lắp và ĐÚNG trình tự tắt của
     * production trên Postgres thật, thay vì một bản chép tay có thể lệch thứ tự.
     */
    static Assembly assemble(
            ServerDatabaseBootstrap databaseBootstrap,
            MetricsContext metricsContext,
            GatewayTracing tracing,
            Endpoints endpoints) {
        GatewayFacades facades = wireFacades(databaseBootstrap);

        validateContentScripts(databaseBootstrap);

        GatewayObservability observability = new GatewayObservability(metricsContext.networkMetrics(), tracing);

        // characterRegistry là WorldOutboundSink thật — PHẢI tồn tại trước WorldFacade vì
        // WorldFacade cần sink lúc construct (SERVER.md §2: kết quả tick đi qua outbound queue).
        ConnectedCharacterRegistry characterRegistry = newCharacterRegistry(facades, metricsContext);
        wireGoldChangeNotifications(databaseBootstrap, characterRegistry);
        bindSkillRanks(characterRegistry, databaseBootstrap, facades);
        WorldContext worldContext = startWorld(
                characterRegistry, databaseBootstrap, metricsContext.meterRegistry(), endpoints.metricsPort());
        MainWorldChannelRegistry mainWorldChannels = worldContext.mainWorldChannels();
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(facades.characterFacade(), characterRegistry, POSITION_SAVE_INTERVAL);
        positionPersister.start();
        cancelLeftoverTrades(facades);
        InstanceRegistries instanceRegistries =
                buildInstanceRegistries(characterRegistry, facades, mainWorldChannels, positionPersister);

        GatewayServer server = new GatewayServer(
                endpoints.gamePort(),
                facades,
                new GatewayServer.GatewayContext(instanceRegistries.worldGateway(), observability),
                endpoints.tlsConfig());
        server.shutdownNotice(shutdownNoticeFromEnvironment());
        // /health/ready còn đòi cổng game phục vụ được (dòng 62(i)) — server dựng SAU máy chủ metrics.
        worldContext.metricsHttpServer().addReadinessCheck(server::isServingGameTraffic);
        BackgroundJobs jobs = startBackgroundJobs(facades, characterRegistry, instanceRegistries.worldGateway());
        Runnable shutdown = shutdownSequence(
                new ShutdownContext(server, worldContext, instanceRegistries, characterRegistry, jobs),
                positionPersister,
                observability,
                databaseBootstrap);
        return new Assembly(server, shutdown);
    }

    /**
     * Đếm ngược bảo trì (dòng 76(b)): {@link GatewayServer#SHUTDOWN_NOTICE_ENV} giây, thiếu hoặc hỏng thì
     * {@link GatewayServer#DEFAULT_SHUTDOWN_NOTICE}. Dev đặt 0 để Ctrl+C tắt ngay.
     */
    static Duration shutdownNoticeFromEnvironment() {
        String raw = System.getenv(GatewayServer.SHUTDOWN_NOTICE_ENV);
        if (raw == null || raw.isBlank()) {
            return GatewayServer.DEFAULT_SHUTDOWN_NOTICE;
        }
        try {
            long seconds = Long.parseLong(raw.strip());
            if (seconds >= 0) {
                return Duration.ofSeconds(seconds);
            }
        } catch (NumberFormatException e) {
            LOG.warn("{}={} không phải số — dùng mặc định", GatewayServer.SHUTDOWN_NOTICE_ENV, raw, e);
            return GatewayServer.DEFAULT_SHUTDOWN_NOTICE;
        }
        LOG.warn("{}={} âm — dùng mặc định", GatewayServer.SHUTDOWN_NOTICE_ENV, raw);
        return GatewayServer.DEFAULT_SHUTDOWN_NOTICE;
    }

    /**
     * Mở cổng; thất bại thì chạy {@code shutdown} rồi mới ném tiếp (sửa 2026-10-03).
     *
     * <p>Lúc mở cổng, luồng lưu vị trí và máy chủ metrics — đều KHÔNG daemon — đã chạy. Bản cũ để
     * exception thoát khỏi {@code main()} mà không tắt chúng (shutdown hook còn chưa đăng ký): JVM
     * không bao giờ thoát, tiến trình treo mà không mở cổng game nào. Hook chỉ đăng ký SAU khi mở
     * cổng thành công, nên trình tự tắt không chạy hai lần.
     */
    static void startOrShutDown(GatewayServer server, Runnable shutdown) throws InterruptedException, SSLException {
        boolean started = false;
        try {
            server.start();
            started = true;
        } finally {
            if (!started) {
                shutdown.run();
            }
        }
    }

    /**
     * SCRIPTING.md §8 — fail fast lúc khởi động, KHÔNG đợi người chơi phát hiện lỗi cú pháp script.
     * Gom {@code script_ref} từ DB (quest/dialogue) + hằng số Java (skill, chưa DB-driven).
     *
     * <p>Tách khỏi {@code main()} để giữ dưới giới hạn Checkstyle MethodLength (cùng lý do
     * {@link #wireFacades}/{@link #startWorld}).
     */
    private static void validateContentScripts(ServerDatabaseBootstrap databaseBootstrap) {
        ArrayList<String> scriptRefs = new ArrayList<String>();
        scriptRefs.addAll(databaseBootstrap.listContentScriptRefs());
        scriptRefs.addAll(WorldFacade.listSkillScriptResources());
        new ScriptValidator(new ScriptSandbox(SCRIPT_VALIDATION_STATEMENT_LIMIT)).validateAll(scriptRefs);
    }

    /**
     * ADR-035 Decision-3 — ĐIỂM DUY NHẤT nối "vàng vừa đổi" ({@code GoldLedger} ở server-database,
     * chỗ duy nhất ghi được {@code character_stat.gold}) với "gửi packet"
     * ({@link ConnectedCharacterRegistry}).
     *
     * <p>KHÔNG use-case nào tự bắn {@code GoldChangedEvent}: rắc lời gọi ở từng chỗ (mua, bán,
     * cường hoá, nhận thư, giao dịch) là cách chắc chắn sẽ sót một đường, và sót thì client hiện số
     * cũ mà không có lỗi nào để lần ra.
     */
    static void wireGoldChangeNotifications(
            ServerDatabaseBootstrap databaseBootstrap, ConnectedCharacterRegistry characterRegistry) {
        databaseBootstrap.configureGoldChangeListener(change -> characterRegistry.sendGoldChanged(
                change.characterId(), change.gold(), change.delta(), change.reasonKey()));
    }

    /**
     * Trình tự tắt server — chạy bởi shutdown hook, hoặc bởi {@link #startOrShutDown} nếu mở cổng
     * thất bại. Tách khỏi {@code main()} để giữ dưới giới hạn Checkstyle MethodLength.
     */
    private static Runnable shutdownSequence(
            ShutdownContext ctx,
            CharacterPositionPersister positionPersister,
            GatewayObservability observability,
            ServerDatabaseBootstrap databaseBootstrap) {
        return () -> {
            // Thứ tự SERVER.md §10: ngừng nhận kết nối mới trước (server.stop() đóng channel),
            // rồi mới dừng world tick + đóng DB. positionPersister dừng SAU worldFacade — stop() chạy
            // nốt các lượt ghi logout đã xếp rồi MỘT vòng quét cuối cho ai còn trong registry (dòng
            // 76(e)); lưu vị trí thật của từng người lúc tắt là việc của lần dọn kết nối (76(a)), chạy
            // TRƯỚC cả bước dừng world vì server.stop() đóng mọi kết nối. dungeonInstanceRegistry/arenaInstanceRegistry
            // dừng CÙNG
            // lúc worldFacade (mọi World, chính lẫn dungeon/duel, dừng trước khi đóng DB).
            // TRƯỚC khi đóng kết nối: từ đây rớt mạng giữa trận Arena là huỷ trận, không phải thua.
            ctx.instanceRegistries().arena().beginShutdown();
            ctx.server().stop();
            // Lịch boss thế giới gửi thư/ghi sổ qua DB — dừng TRƯỚC khi đóng pool (ADR-081).
            ctx.worldContext().worldBoss().stop();
            ctx.worldContext().pvpSeason().stop();
            ctx.worldContext().mainWorldChannels().stopAll();
            ctx.instanceRegistries().dungeon().stopAll();
            ctx.instanceRegistries().arena().stopAll();
            ctx.characterRegistry().stop();
            positionPersister.stop();
            // Hết hạn phường thị (ADR-063 D4) + Linh Mạch (ADR-090) ghi DB + gửi thư — dừng TRƯỚC khi đóng pool.
            ctx.jobs().marketExpiry().stop();
            ctx.jobs().expansion().stop();
            ctx.jobs().spiritVeins().stop();
            ctx.jobs().waypoints().stop();
            ctx.jobs().guildWars().stop();
            ctx.characterRegistry().guildWars.stop();
            ctx.characterRegistry().killingIntent.stop();
            ctx.characterRegistry().activity.stop();
            ctx.characterRegistry().collections.stop();
            ctx.characterRegistry().guildAuctions.stop();
            ctx.characterRegistry().escorts.stop();
            ctx.worldContext().metricsHttpServer().stop();
            observability.tracing().shutdown();
            databaseBootstrap.shutdown();
        };
    }

    /**
     * Gộp thứ cần cho {@link #shutdownSequence} — giữ tham số method đó ≤ 4 (Checkstyle
     * ParameterNumber).
     */
    private record ShutdownContext(
            GatewayServer server,
            WorldContext worldContext,
            InstanceRegistries instanceRegistries,
            ConnectedCharacterRegistry characterRegistry,
            BackgroundJobs jobs) {}

    /** Các luồng nền dựng SAU cổng game — cần {@link WorldGateway} hoặc chỉ chạm DB + thư. */
    private record BackgroundJobs(
            MarketExpiryJanitor marketExpiry,
            SpiritVeinScheduler spiritVeins,
            WaypointScheduler waypoints,
            GuildWarScheduler guildWars,
            ExpansionJobs expansion) {}

    private static BackgroundJobs startBackgroundJobs(
            GatewayFacades facades, ConnectedCharacterRegistry characterRegistry, WorldGateway worldGateway) {
        MailFacade mail = facades.contentFacades().socialFacades().mailFacade();
        MarketExpiryJanitor marketExpiry =
                new MarketExpiryJanitor(facades.progression().marketFacade(), mail, characterRegistry);
        marketExpiry.start();
        SpiritVeinScheduler spiritVeins = new SpiritVeinScheduler(
                new SpiritVeinScheduler.Deps(
                        facades.progression().spiritVeins(),
                        facades.contentFacades().socialFacades().guildFacade(),
                        mail,
                        characterRegistry,
                        characterId -> worldGateway.dungeonInstanceRegistry().isInDungeon(characterId)
                                || worldGateway.arenaInstanceRegistry().isInDuel(characterId)),
                java.time.Clock.systemUTC());
        spiritVeins.start();
        // ADR-100 — mở điểm truyền tống khi đứng gần lần đầu.
        WaypointScheduler waypoints = new WaypointScheduler(
                facades.progression().adventure().waypoints(),
                characterRegistry,
                characterId -> worldGateway.dungeonInstanceRegistry().isInDungeon(characterId)
                        || worldGateway.arenaInstanceRegistry().isInDuel(characterId));
        waypoints.start();
        GuildWarScheduler guildWars = new GuildWarScheduler(
                new GuildWarScheduler.Deps(
                        facades.progression().adventure().guildWars(),
                        characterRegistry.guildWars.roster,
                        mail,
                        characterRegistry),
                java.time.Clock.systemUTC());
        guildWars.start();
        characterRegistry.guildAuctions.start(); // ADR-112 — dừng ở shutdownSequence
        ExpansionJobs expansion = expansionJobs(facades, characterRegistry, worldGateway, mail);
        return new BackgroundJobs(marketExpiry, spiritVeins, waypoints, guildWars, expansion);
    }

    /** Tác vụ nền lô 23 (ADR-115…126) — tách khỏi {@link #startBackgroundJobs} vì trần {@code MethodLength}. */
    private static ExpansionJobs expansionJobs(
            GatewayFacades facades,
            ConnectedCharacterRegistry characterRegistry,
            WorldGateway worldGateway,
            MailFacade mail) {
        ExpansionJobs expansion = new ExpansionJobs();
        expansion.add(
                ExpansionJobs.buyOrderRefunds(facades.progression().expansion().buyOrders(), mail, characterRegistry));
        expansion.add(MerchantCommands.job(
                facades.progression().expansion().merchant(),
                facades.progression().adventure().waypoints(),
                characterRegistry,
                java.time.Clock.systemUTC()));
        expansion.add(MerchantCommands.presenceJob( // ADR-131 — NPC thương nhân đứng cạnh trạm đang ghé
                facades.progression().expansion().merchant(),
                facades.progression().adventure().waypoints(),
                worldGateway.mainWorldChannels(),
                java.time.Clock.systemUTC()));
        characterRegistry.quiz.bind(new QuizHooks.Deps( // ADR-122 — hỏi đáp tông môn
                facades.progression().expansion().quiz(), mail, characterRegistry, java.time.Clock.systemUTC()));
        expansion.add(characterRegistry.quiz.job());
        characterRegistry.tournament.bind(new TournamentHooks.Deps( // ADR-125 — luận võ đại hội
                facades.progression().expansion().tournament(),
                worldGateway.arenaInstanceRegistry(),
                characterRegistry,
                new PlayerNotices(mail, characterRegistry),
                facades.characterFacade(),
                java.time.Clock.systemUTC()));
        expansion.add(characterRegistry.tournament.job());
        characterRegistry.tribulation.bind(new TribulationHooks.Deps( // ADR-126 — Thiên Kiếp
                facades.progression().expansion().tribulation(),
                facades.characterFacade(),
                worldGateway.dungeonInstanceRegistry(),
                characterRegistry,
                java.time.Clock.systemUTC()));
        expansion.add(characterRegistry.tribulation.job());
        characterRegistry.formation.bind(new FormationHooks.Deps( // ADR-128 — trận pháp nhóm
                facades.contentFacades().socialFacades().partyFacade(),
                facades.characterFacade(),
                WorldRouteLookup.of(worldGateway),
                worldGateway.mainWorldChannels()::worldFacadeFor,
                characterRegistry));
        expansion.add(characterRegistry.formation.job());
        lot26Jobs(facades, characterRegistry, worldGateway).forEach(expansion::add); // ADR-142, ADR-144
        expansion.add(DayNightJob.job( // ADR-123 — ngày đêm
                worldGateway.mainWorldChannels(), characterRegistry, java.time.Clock.systemUTC()));
        expansion.start();
        return expansion;
    }

    /**
     * Lô 26: ADR-142 — nối sát khí (dải PvP của world chính đọc một lần) + nhịp làm mới ảnh chụp Ma Đầu; ADR-144 — nhịp
     * phát thời tiết theo vùng.
     */
    private static List<ExpansionJobs.Job> lot26Jobs(
            GatewayFacades facades, ConnectedCharacterRegistry characterRegistry, WorldGateway worldGateway) {
        dev.kage.world.api.WorldGeometryFacade geometry =
                facades.progression().adventure().geometry();
        characterRegistry.killingIntent.bind(new KillingIntentHooks.Deps(
                facades.progression().expansion().killingIntent(),
                facades.characterFacade(),
                geometry.listPvpZones(dev.kage.world.domain.SafeZone.MAIN_WORLD_CODE),
                characterRegistry,
                WorldRouteLookup.of(worldGateway),
                java.time.Clock.systemUTC()));
        characterRegistry.enemies.bind(new EnemyHooks.Deps(
                facades.progression().expansion().lot33().enemies(), java.time.Clock.systemUTC())); // ADR-194
        characterRegistry.deathRecap.bind(facades.characterFacade()); // ADR-195
        return List.of(
                characterRegistry.killingIntent.job(),
                WeatherJob.job(
                        geometry.listWeatherRegions(dev.kage.world.domain.SafeZone.MAIN_WORLD_CODE),
                        characterRegistry,
                        java.time.Clock.systemUTC()),
                new WorldBossBoardJob( // ADR-157
                                worldGateway.mainWorldChannels().firstChannel(),
                                characterRegistry,
                                facades.characterFacade())
                        .job());
    }

    /**
     * Gộp {@link MainWorldChannelRegistry} + {@link MetricsHttpServer} — cả hai sinh ra CÙNG lúc
     * trong {@link #startWorld}.
     */
    private record WorldContext(
            MainWorldChannelRegistry mainWorldChannels,
            MetricsHttpServer metricsHttpServer,
            WorldBossScheduler worldBoss,
            PvpSeasonScheduler pvpSeason) {}

    /**
     * Gộp Dungeon/Arena instance registry + {@link WorldGateway} — dựng cùng lúc,
     * xem {@link #buildInstanceRegistries}.
     */
    record InstanceRegistries(
            DungeonInstanceRegistry dungeon, ArenaInstanceRegistry arena, WorldGateway worldGateway) {}

    /**
     * Dòng 40(iv), lô 18 (2026-10-23): phiên giao dịch sót lại từ lần server chết đột ngột (tắt êm thì mọi phiên đã
     * bị huỷ lúc đóng kết nối) bị huỷ TRƯỚC khi mở cổng game — chưa ai online nên không ai đang dùng nó.
     */
    private static void cancelLeftoverTrades(GatewayFacades facades) {
        int cancelled = facades.contentFacades().socialFacades().tradeFacade().cancelLeftoverSessions();
        if (cancelled > 0) {
            LOG.info("Huỷ {} phiên giao dịch sót lại từ lần chạy trước", cancelled);
        }
    }

    /**
     * Tách khỏi {@code main()} để giữ dưới giới hạn Checkstyle MethodLength — Dungeon/Arena
     * registry đều cần {@code mainWorldChannels} (đích re-attach sau khi thoát instance, Phase 8
     * lát cắt 1/4) nên dựng cùng chỗ với {@link WorldGateway} luôn cho gọn.
     */
    static InstanceRegistries buildInstanceRegistries(
            ConnectedCharacterRegistry characterRegistry,
            GatewayFacades facades,
            MainWorldChannelRegistry mainWorldChannels,
            CharacterPositionPersister positionPersister) {
        // Đấu trường dựng TRƯỚC: hầm ngục hỏi nó "ai đang đấu" trước khi kéo cả nhóm vào (2026-10-02).
        ArenaInstanceRegistry arenaInstanceRegistry = new ArenaInstanceRegistry(
                characterRegistry,
                facades.characterFacade(),
                facades.contentFacades().socialFacades().pvpFacade(),
                mainWorldChannels);
        DungeonInstanceRegistry dungeonInstanceRegistry = new DungeonInstanceRegistry(
                characterRegistry,
                new DungeonInstanceRegistry.DungeonFacades(
                        facades.characterFacade(),
                        facades.contentFacades().socialFacades().partyFacade(),
                        arenaInstanceRegistry::isInDuel,
                        dungeonRewards(facades, characterRegistry)),
                mainWorldChannels,
                mainWorldChannels.worldContent());
        // Và chiều ngược: người ở hầm ngục không được vào trận đấu (2026-10-03).
        arenaInstanceRegistry.refuseCharactersIn(dungeonInstanceRegistry::isInDungeon);
        WorldGateway worldGateway = new WorldGateway(
                mainWorldChannels,
                characterRegistry,
                positionPersister,
                dungeonInstanceRegistry,
                arenaInstanceRegistry);
        // Lên cấp giữa phiên phải tới luật trong world (cổng Tuyệt Kỹ) — nối ở đây vì đây là chỗ đầu
        // tiên mọi bảng định tuyến World cùng tồn tại.
        // ADR-093 — thưởng sư đồ đi theo mỗi cú lên cấp.
        characterRegistry.syncLevelUpsThrough(
                CharacterLevelSync.of(worldGateway, levelListener(facades, characterRegistry, worldGateway)));
        // Cùng lý do: "đồng đội cùng World" chỉ tra được khi mọi bảng định tuyến đã có.
        characterRegistry.shareKillRewardsThrough(
                KillRewardSharing.of(facades.contentFacades().socialFacades().partyFacade(), worldGateway));
        WorldRouteLookup routes = WorldRouteLookup.of(worldGateway);
        characterRegistry.pets.bind(
                facades.inventoryFacade(),
                facades.progression().gameEvents(),
                id -> routes.find(id)
                        .ifPresent(world -> world.applyEquipmentBonus(id, characterRegistry.equipmentBonusOf(id))),
                (id, opcode, payload) -> characterRegistry.sendToCharacterIfOnline(id, opcode, 0, payload));
        bindCollections(characterRegistry, facades, routes);
        characterRegistry.guildProgress.bind(
                facades.progression().guildProgression(),
                facades.contentFacades().socialFacades().mailFacade(),
                (id, opcode, payload) -> characterRegistry.sendToCharacterIfOnline(id, opcode, 0, payload));
        bindLot21Hooks(characterRegistry, facades, worldGateway, routes);
        characterRegistry.achievements.bind(
                facades.progression().achievementFacade(),
                (id, opcode, payload) -> characterRegistry.sendToCharacterIfOnline(id, opcode, 0, payload));
        return new InstanceRegistries(dungeonInstanceRegistry, arenaInstanceRegistry, worldGateway);
    }

    /**
     * Gộp {@link PrometheusMeterRegistry} + {@link GatewayNetworkMetrics} — dựng cùng lúc,
     * xem {@link #buildMetrics}.
     */
    record MetricsContext(PrometheusMeterRegistry meterRegistry, GatewayNetworkMetrics networkMetrics) {}

    /**
     * Tách khỏi {@code main()} để giữ dưới giới hạn Checkstyle MethodLength — {@code meterRegistry}
     * dựng TRƯỚC {@link ConnectedCharacterRegistry} vì cả nó lẫn {@link #startWorld} đều cần
     * {@link GatewayNetworkMetrics}/{@code meterRegistry} lúc construct (packet.sent đếm được từ
     * mọi đường gửi, kể cả broadcast của {@code ConnectedCharacterRegistry}, không chỉ response
     * trực tiếp của {@link GatewayChannelHandler}).
     */
    static MetricsContext buildMetrics() {
        PrometheusMeterRegistry meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        // OBSERVABILITY.md §3.4 "jvm.gc.pause (quan trọng) · jvm.memory.used · jvm.threads.live" —
        // binder có sẵn trong micrometer-core (KHÔNG cần dependency mới), chỉ chưa ai gọi bindTo()
        // trước lát cắt này. jvm_gc_pause_seconds chỉ xuất hiện SAU lần GC thật đầu tiên (Micrometer
        // tạo Timer động theo action/cause, xem Prometheus alert_rules.yml).
        new JvmGcMetrics().bindTo(meterRegistry);
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        return new MetricsContext(meterRegistry, new GatewayNetworkMetrics(meterRegistry));
    }

    /**
     * Tách khỏi {@code main()} để giữ dưới giới hạn Checkstyle MethodLength (cùng lý do
     * {@link #wireFacades}) — dựng endpoint scrape (ADR-015) TRƯỚC {@link WorldFacade} vì
     * {@link MicrometerTickMetricsPort} cần {@code meterRegistry} (dựng ở {@code main()}, xem đó)
     * lúc construct World. Khu vực (channel) #1 của {@link MainWorldChannelRegistry} là
     * {@link WorldFacade} DUY NHẤT mang {@code TickMetricsPort} thật (tag "main") — khu vực mở
     * thêm sau (Phase 9 lát cắt 6) dùng {@code NO_OP}, xem javadoc {@link MainWorldChannelRegistry}.
     */
    private static WorldContext startWorld(
            ConnectedCharacterRegistry characterRegistry,
            ServerDatabaseBootstrap databaseBootstrap,
            PrometheusMeterRegistry meterRegistry,
            int metricsPort) {
        MetricsHttpServer metricsHttpServer =
                new MetricsHttpServer(metricsPort, meterRegistry, databaseBootstrap::isReady);
        metricsHttpServer.start();
        // OBSERVABILITY.md §3.1 kage.players.online + §3.5 kage.gold.total — Gauge đọc thẳng
        // state hiện có lúc scrape (weak reference qua Gauge.builder, không polling thủ công,
        // không allocation mỗi tick). kage.gold.total chạm DB mỗi lần scrape — chấp nhận được
        // (query SUM 1 bảng, không phải hot path tick/EventLoop, server/CLAUDE.md chỉ cấm 2 chỗ đó).
        Gauge.builder("kage.players.online", characterRegistry, ConnectedCharacterRegistry::onlineCount)
                .description("Tổng số nhân vật đang kết nối")
                .register(meterRegistry);
        Gauge.builder("kage.gold.total", databaseBootstrap, ServerDatabaseBootstrap::totalGold)
                .description("Tổng gold toàn server — tăng đột biến là dấu hiệu duplicate (OBSERVABILITY.md §3.5)")
                .register(meterRegistry);
        Gauge.builder("kage.db.pool.active", databaseBootstrap, ServerDatabaseBootstrap::poolActiveConnections)
                .description("Số connection HikariCP đang được dùng (OBSERVABILITY.md §3.3)")
                .register(meterRegistry);
        Gauge.builder("kage.db.pool.pending", databaseBootstrap, ServerDatabaseBootstrap::poolPendingThreads)
                .description("Số thread đang chờ pool có connection trống — >0 kéo dài nghĩa là pool quá nhỏ")
                .register(meterRegistry);
        // ADR-037 (quái) + ADR-040 (NPC) + ADR-046 D3 (vùng an toàn) — nội dung tĩnh đọc từ DB.
        // Nạp MỘT LẦN ở đây rồi chia cho mọi khu vực của world chính: config tĩnh, và đọc lại mỗi
        // lần mở khu vực là mở một đường DB thứ hai cho cùng dữ liệu.
        WorldContentCatalog worldContent = WorldContentLoader.load(
                databaseBootstrap.wireMonsterFacade(),
                databaseBootstrap.wireNpcPlacementFacade(),
                databaseBootstrap.wireWorldGeometryFacade(),
                MonsterSpawnPoint.MAIN_WORLD_CODE);
        WorldFacade firstChannelFacade = new WorldFacade(
                characterRegistry, true, new MicrometerTickMetricsPort(meterRegistry, WORLD_TAG_MAIN), worldContent);
        // ADR-102 D2 — luật PvP theo cặp (bang chiến) cho world chính; khu vực mở thêm nối ở MainWorldChannelRegistry.
        firstChannelFacade.usePlayerVersusPlayerRule(
                characterRegistry.killingIntent.ruleWith(characterRegistry.guildWars.roster)); // + ADR-142
        firstChannelFacade.useEscortListener(characterRegistry.escorts::onChanged); // ADR-114
        firstChannelFacade.useRevivalListener(RevivalCommands.listener(characterRegistry)); // ADR-117
        firstChannelFacade.start();
        WorldBossScheduler worldBoss = startWorldBoss(firstChannelFacade, databaseBootstrap, characterRegistry);
        MainWorldChannelRegistry mainWorldChannels =
                new MainWorldChannelRegistry(characterRegistry, firstChannelFacade, meterRegistry, worldContent);
        return new WorldContext(
                mainWorldChannels, metricsHttpServer, worldBoss, startPvpSeason(databaseBootstrap, characterRegistry));
    }

    /**
     * Tách khỏi {@code main()} để giữ dưới giới hạn Checkstyle MethodLength — càng thêm facade, dòng càng dài.
     * Package-private (cùng {@link #buildInstanceRegistries}/{@link #wireGoldChangeNotifications}) để
     * {@code GatewayOpcodeFuzzDatabaseTest} lắp ĐÚNG composition root này thay vì chép tay.
     */
    static GatewayFacades wireFacades(ServerDatabaseBootstrap databaseBootstrap) {
        AccountFacade accountFacade = databaseBootstrap.wireAccountFacade();
        CharacterFacade characterFacade = databaseBootstrap.wireCharacterFacade();
        InventoryFacade inventoryFacade = databaseBootstrap.wireInventoryFacade();
        ShopFacade shopFacade = databaseBootstrap.wireShopFacade();
        DialogueFacade dialogueFacade = databaseBootstrap.wireDialogueFacade();
        QuestFacade questFacade = databaseBootstrap.wireQuestFacade();
        FriendFacade friendFacade = databaseBootstrap.wireFriendFacade();
        MailFacade mailFacade = databaseBootstrap.wireMailFacade();
        ChatFacade chatFacade = databaseBootstrap.wireChatFacade();
        GuildFacade guildFacade = databaseBootstrap.wireGuildFacade();
        PartyFacade partyFacade = databaseBootstrap.wirePartyFacade();
        TradeFacade tradeFacade = databaseBootstrap.wireTradeFacade();
        PvpFacade pvpFacade = databaseBootstrap.wirePvpFacade();

        SocialFacades socialFacades = new SocialFacades(
                friendFacade, mailFacade, chatFacade, guildFacade, partyFacade, tradeFacade, pvpFacade);
        ContentFacades contentFacades = new ContentFacades(shopFacade, dialogueFacade, questFacade, socialFacades);
        ProgressionFacades progression = progressionFacades(databaseBootstrap, mailFacade, inventoryFacade);
        return new GatewayFacades(
                accountFacade,
                characterFacade,
                inventoryFacade,
                contentFacades,
                databaseBootstrap.wireGmFacade(),
                progression);
    }

    /** Facade các tính năng gắn bó (lô 16+) — tách khỏi {@link #wireFacades} vì trần {@code MethodLength}. */
    private static ProgressionFacades progressionFacades(
            ServerDatabaseBootstrap databaseBootstrap, MailFacade mailFacade, InventoryFacade inventoryFacade) {
        return new ProgressionFacades(
                databaseBootstrap.wireCheckInFacade(new CheckInMailSender(mailFacade, inventoryFacade)),
                databaseBootstrap.wireAchievementFacade(),
                databaseBootstrap.wireMarketFacade(),
                databaseBootstrap.wireGameEventFacade(),
                databaseBootstrap.wireGuildProgressionFacade(),
                databaseBootstrap.wireCraftingFacade(),
                databaseBootstrap.wireGuildShopFacade(),
                databaseBootstrap.wireWorldBossFacade(),
                databaseBootstrap.wireFriendGiftFacade(),
                databaseBootstrap.wireGuildSkillFacade(),
                databaseBootstrap.wireRebirthFacade(),
                databaseBootstrap.wireSpiritVeinFacade(),
                new RelationshipFacades(
                        databaseBootstrap.wireBlockFacade(),
                        databaseBootstrap.wireMentorshipFacade(),
                        databaseBootstrap.wireDaoLuFacade(),
                        databaseBootstrap.wireSwornFacade()),
                new DailyFacades(
                        databaseBootstrap.wireSeclusionFacade(),
                        databaseBootstrap.wireOnlineRewardFacade(),
                        databaseBootstrap.wireDismantleFacade(),
                        databaseBootstrap.wireHerbNodeFacade()),
                new AdventureFacades(
                        databaseBootstrap.wireDungeonQuotaFacade(),
                        databaseBootstrap.wireWaypointFacade(),
                        databaseBootstrap.wireBountyFacade(),
                        databaseBootstrap.wireGuildWarFacade(),
                        databaseBootstrap.wireTowerFacade(),
                        databaseBootstrap.wireEscortRouteFacade(),
                        databaseBootstrap.wireWorldGeometryFacade()),
                new ServiceDeskFacades(
                        databaseBootstrap.wireServerNoticeFacade(),
                        databaseBootstrap.wireGiftCodeFacade(),
                        databaseBootstrap.wirePlayerReportFacade(),
                        databaseBootstrap.wireActivityFacade(),
                        databaseBootstrap.expansion().wireCompensationFacade()),
                new CollectionFacades(
                        databaseBootstrap.wireBestiaryFacade(),
                        databaseBootstrap.wireReputationFacade(),
                        databaseBootstrap.wireRefinementFacade(),
                        databaseBootstrap.wireGuildAuctionFacade()),
                expansionFacades(databaseBootstrap));
    }

    /** Facade lô 23 (ADR-115…126) — tách khỏi {@link #progressionFacades} vì trần {@code MethodLength}. */
    private static ExpansionFacades expansionFacades(ServerDatabaseBootstrap databaseBootstrap) {
        return new ExpansionFacades(
                databaseBootstrap.wireSecurityPinFacade(),
                databaseBootstrap.wireBuyOrderFacade(),
                databaseBootstrap.wireConsumableFacade(),
                databaseBootstrap.wireTravelingMerchantFacade(),
                databaseBootstrap.expansion().wireSpiritFieldFacade(),
                databaseBootstrap.expansion().wireEncounterFacade(),
                databaseBootstrap
                        .expansion()
                        .wireSectQuiz(dev.kage.chat.domain.QuizRules.parseStarts(
                                System.getenv(QUIZ_TIMES_ENV), List.of(DEFAULT_QUIZ_START))),
                databaseBootstrap.expansion().wireTamingFacade(),
                databaseBootstrap
                        .expansion()
                        .wireTournamentFacade(
                                dev.kage.pvp.domain.TournamentRules.Schedule.parse(System.getenv(TOURNAMENT_AT_ENV))),
                databaseBootstrap.expansion().wireTribulationFacade(),
                databaseBootstrap.expansion().wireArtifactFacade(),
                databaseBootstrap.expansion().wireCaveFacade(),
                databaseBootstrap.expansion().wireKillingIntentFacade(),
                databaseBootstrap.expansion().wireTreasureFacade(),
                databaseBootstrap.expansion().wireRenameFacade(),
                databaseBootstrap.expansion().wireFortuneFacade(),
                databaseBootstrap.expansion().wireLuckyWheelFacade(),
                databaseBootstrap.expansion().wireMilestoneFacade(),
                databaseBootstrap.expansion().wireWeeklyTaskFacade(),
                databaseBootstrap.expansion().wireHonorFacade(),
                new Lot29Facades(
                        databaseBootstrap.expansion().wireItemLockFacade(),
                        databaseBootstrap.expansion().wireBagSortFacade(),
                        databaseBootstrap.expansion().wireGemCombineFacade(),
                        databaseBootstrap.expansion().wireDungeonSweepFacade(),
                        databaseBootstrap.expansion().wireLuckyBagFacade(),
                        databaseBootstrap.expansion().wireExpBuffFacade(),
                        databaseBootstrap.expansion().wireCostumeFacade()),
                new Lot30Facades(
                        databaseBootstrap.expansion().wireGemRemovalFacade(),
                        databaseBootstrap.expansion().wireGoldHistoryFacade(),
                        databaseBootstrap.expansion().wireJunkSaleFacade(),
                        databaseBootstrap.expansion().wireInspectPrivacyFacade(),
                        databaseBootstrap.expansion().wireGuildRenameFacade(),
                        databaseBootstrap.expansion().wireFriendNoteFacade()),
                lot31Facades(databaseBootstrap),
                lot32Facades(databaseBootstrap),
                lot33Facades(databaseBootstrap));
    }

    /** Facade lô 31 (ADR-175…) — tách khỏi {@link #expansionFacades} vì trần {@code MethodLength}. */
    private static Lot31Facades lot31Facades(ServerDatabaseBootstrap databaseBootstrap) {
        return new Lot31Facades(
                databaseBootstrap.expansion().wireEquipmentPresetFacade(),
                databaseBootstrap.expansion().wireMarketHistoryFacade(),
                databaseBootstrap.expansion().wireGuildNoticeFacade(),
                databaseBootstrap.expansion().wireGuildLogFacade());
    }

    /** Facade lô 32 (ADR-183…) — tách khỏi {@link #expansionFacades} vì trần {@code MethodLength}. */
    private static Lot32Facades lot32Facades(ServerDatabaseBootstrap databaseBootstrap) {
        return new Lot32Facades(
                databaseBootstrap.expansion().wireGuildLeadershipFacade(),
                databaseBootstrap.expansion().wireGuildRecruitmentFacade(),
                databaseBootstrap.expansion().wireGuildCheckInFacade(),
                databaseBootstrap.expansion().wireOfflineWhisperFacade(),
                databaseBootstrap.expansion().wireTradeHistoryFacade());
    }

    /** Facade lô 33 (ADR-191…) — tách khỏi {@link #expansionFacades} vì trần {@code MethodLength}. */
    private static Lot33Facades lot33Facades(ServerDatabaseBootstrap databaseBootstrap) {
        return new Lot33Facades(
                databaseBootstrap.expansion().wireLastOnlineFacade(),
                databaseBootstrap.expansion().wirePetNameFacade(),
                databaseBootstrap.expansion().wireEnemyListFacade(),
                databaseBootstrap.expansion().wireMarketWatchFacade(),
                databaseBootstrap.expansion().wireAccountStorageFacade(),
                databaseBootstrap.expansion().wireGuildBankFacade());
    }

    /** Boss thế giới (ADR-081) — ở khu vực ĐẦU TIÊN của world chính, khu vực không bao giờ đóng. */
    private static WorldBossScheduler startWorldBoss(
            WorldFacade firstChannel, ServerDatabaseBootstrap databaseBootstrap, ConnectedCharacterRegistry registry) {
        WorldBossScheduler scheduler = new WorldBossScheduler(
                firstChannel,
                new WorldBossScheduler.Deps(
                        databaseBootstrap.wireWorldBossFacade(),
                        databaseBootstrap.wireMailFacade(),
                        databaseBootstrap.wireCharacterFacade(),
                        registry),
                java.time.Clock.systemUTC());
        scheduler.start();
        return scheduler;
    }

    /** Mùa Luận Kiếm (ADR-087) — chốt mùa hết hạn, gửi hàng đợi thưởng. */
    private static PvpSeasonScheduler startPvpSeason(
            ServerDatabaseBootstrap databaseBootstrap, ConnectedCharacterRegistry registry) {
        PvpSeasonScheduler scheduler = new PvpSeasonScheduler(
                databaseBootstrap.wirePvpSeasonFacade(),
                databaseBootstrap.wireMailFacade(),
                registry,
                java.time.Clock.systemUTC());
        scheduler.start();
        return scheduler;
    }

    /** Sư đồ (ADR-093) — bản dùng cho móc lên cấp; mỗi kết nối có bản riêng cho lệnh, cùng facade. */
    private static MentorshipCommands mentorshipCommands(
            GatewayFacades facades, ConnectedCharacterRegistry characterRegistry) {
        RelationshipFacades relationships = facades.progression().relationships();
        return new MentorshipCommands(
                relationships.mentorship(),
                facades.characterFacade(),
                new BlockCommands(relationships.blocks(), relationships.daoLu(), facades.characterFacade()),
                new PlayerNotices(facades.contentFacades().socialFacades().mailFacade(), characterRegistry));
    }

    /** ADR-108/109 — mở trang sưu tầm thì áp lại chỉ số ngay, cùng đường linh thú lên cấp. */
    private static void bindCollections(
            ConnectedCharacterRegistry characterRegistry, GatewayFacades facades, WorldRouteLookup routes) {
        characterRegistry.escorts.bind(new EscortHooks.Deps(
                facades.contentFacades().questFacade(),
                facades.progression().adventure().escortRoutes(),
                new PlayerNotices(facades.contentFacades().socialFacades().mailFacade(), characterRegistry),
                characterRegistry));
        characterRegistry.tower.bind(new TowerHooks.Deps(
                facades.progression().adventure().tower(),
                new PlayerNotices(facades.contentFacades().socialFacades().mailFacade(), characterRegistry),
                characterRegistry));
        characterRegistry.guildAuctions.bind(new GuildAuctionHooks.Deps(
                facades.progression().collections().guildAuctions(),
                facades.contentFacades().socialFacades().guildFacade(),
                facades.contentFacades().socialFacades().mailFacade(),
                java.time.Clock.systemUTC()));
        characterRegistry.collections.bind(
                facades.progression().collections(),
                CollectionHooks.refreshing(
                        id -> routes.find(id)
                                .ifPresent(
                                        world -> world.applyEquipmentBonus(id, characterRegistry.equipmentBonusOf(id))),
                        new PlayerNotices(
                                facades.contentFacades().socialFacades().mailFacade(), characterRegistry)));
    }

    /**
     * Lô 21: bụi linh thảo nạp một lần (ADR-098); bảng truy nã đi theo lượt hạ quái (ADR-101). Lô 23: kỳ ngộ (ADR-121)
     * cũng đi theo lượt hạ quái, chỉ ở world chính.
     */
    private static void bindLot21Hooks(
            ConnectedCharacterRegistry characterRegistry,
            GatewayFacades facades,
            WorldGateway worldGateway,
            WorldRouteLookup routes) {
        characterRegistry.encounters.bind(new EncounterHooks.Deps(
                facades.progression().expansion().encounters(),
                characterRegistry,
                characterId -> worldGateway.dungeonInstanceRegistry().isInDungeon(characterId)
                        || worldGateway.arenaInstanceRegistry().isInDuel(characterId),
                routes::find,
                // Vừa vào world chưa nhúc nhích thì registry chưa có vị trí — đọc chỗ đã lưu (như NpcProximity).
                id -> java.util.Optional.ofNullable(characterRegistry.lastKnownPosition(id))
                        .or(() ->
                                java.util.Optional.of(facades.characterFacade().loadPosition(id))),
                java.time.Clock.systemUTC()));
        characterRegistry.lootRolls.bind(new LootRollHooks.Deps(
                characterRegistry,
                facades.inventoryFacade(),
                facades.characterFacade(),
                id -> partyMembers(facades.contentFacades().socialFacades().partyFacade(), id),
                routes::find,
                new PlayerNotices(facades.contentFacades().socialFacades().mailFacade(), characterRegistry)));
        characterRegistry.treasure.bind(new TreasureHooks.Deps(
                facades.progression().expansion().treasure(),
                characterId -> worldGateway.dungeonInstanceRegistry().isInDungeon(characterId)
                        || worldGateway.arenaInstanceRegistry().isInDuel(characterId),
                routes::find,
                id -> java.util.Optional.ofNullable(characterRegistry.lastKnownPosition(id))
                        .or(() ->
                                java.util.Optional.of(facades.characterFacade().loadPosition(id))),
                java.time.Clock.systemUTC()));
        characterRegistry.herbNodes.load(facades.progression().daily().herbs().listHerbNodes());
        characterRegistry.bounties.bind(
                facades.progression().adventure().bounties(), facades.characterFacade(), characterRegistry);
        characterRegistry.guildWars.bind(facades.progression().adventure().guildWars(), java.time.Clock.systemUTC());
        characterRegistry.activity.bind(facades.progression().serviceDesk().activity());
        characterRegistry.activity.bindWeekly(facades.progression().expansion().weeklyTasks()); // ADR-154
        characterRegistry.honor.bind(facades.progression().expansion().honor()); // ADR-155
        characterRegistry.costumeChoice.bind(
                facades.progression().expansion().lot29().costume()::chosenTier); // ADR-166
    }

    /** ADR-099 — lượt Bí Cảnh + thư thưởng lần đầu hạ boss (5.000 linh thạch + 3 Yêu Đan). */
    private static DungeonRewards dungeonRewards(GatewayFacades facades, ConnectedCharacterRegistry registry) {
        PlayerNotices notices =
                new PlayerNotices(facades.contentFacades().socialFacades().mailFacade(), registry);
        return new DungeonRewards(
                facades.progression().adventure().dungeonQuota(), registry.guildAuctions::onBossKilled, characterId -> {
                    java.util.List<dev.kage.mail.api.MailFacade.AttachmentRequest> items =
                            facades.inventoryFacade().itemCatalog().entries().stream()
                                    .filter(entry -> FIRST_CLEAR_ITEM_CODE.equals(entry.code()))
                                    .map(entry -> new dev.kage.mail.api.MailFacade.AttachmentRequest(
                                            entry.id(), FIRST_CLEAR_ITEM_QUANTITY, 0))
                                    .toList();
                    notices.mail(
                            characterId,
                            new PlayerNotices.SystemMail(
                                    "Bí Cảnh / Dungeon",
                                    "Lần đầu hạ boss Bí Cảnh. / First dungeon boss clear.",
                                    FIRST_CLEAR_GOLD,
                                    items));
                });
    }

    /** Lên cấp: sư đồ (ADR-091), bậc trang phục theo cảnh giới (ADR-132), báo đột phá cảnh giới (ADR-136). */
    private static CharacterLevelSync.LevelListener levelListener(
            GatewayFacades facades, ConnectedCharacterRegistry characterRegistry, WorldGateway worldGateway) {
        CharacterLevelSync.LevelListener mentorship = mentorshipCommands(facades, characterRegistry)::onLevelReached;
        CharacterLevelSync.LevelListener costume = CostumeTierHooks.listener(
                facades.characterFacade(), characterRegistry, WorldRouteLookup.of(worldGateway));
        CharacterLevelSync.LevelListener breakthrough = // ADR-136
                BreakthroughHooks.listener(facades.characterFacade(), characterRegistry);
        CharacterLevelSync.LevelListener friends = // ADR-182
                Lot31Commands.friendBreakthrough(facades, characterRegistry).listener();
        return (characterId, level) -> {
            mentorship.onLevelReached(characterId, level);
            costume.onLevelReached(characterId, level);
            breakthrough.onLevelReached(characterId, level);
            friends.onLevelReached(characterId, level);
        };
    }

    /** Bậc chiêu + tâm pháp (ADR-071/077) — tâm pháp cần linh căn hiện tại đọc từ DB. */
    private static void bindSkillRanks(
            ConnectedCharacterRegistry characterRegistry,
            ServerDatabaseBootstrap databaseBootstrap,
            GatewayFacades facades) {
        characterRegistry.skillRanks.bind(
                databaseBootstrap.wireSkillRankFacade(), characterId -> facades.characterFacade()
                        .findById(characterId)
                        .map(character -> FactionMapping.toElement(character.faction()))
                        .orElse(Element.NONE));
        characterRegistry.skillRanks.bindGuildBonus(characterId -> characterRegistry.formation.plus( // ADR-128
                TribulationEffects.plus( // ADR-126
                        RefinementEffects.plus(
                                BestiaryEffects.plus(
                                        RebirthEffects.plus(
                                                GuildSkillEffects.bonusOf(
                                                        facades.progression().guildSkills(), characterId),
                                                facades.progression().rebirth().rebirthCountOf(characterId)),
                                        facades.progression()
                                                .collections()
                                                .bestiary()
                                                .bonusOf(characterId)),
                                facades.progression().collections().refinement().wornBonusOf(characterId)),
                        facades.progression().expansion().tribulation().passed(characterId)),
                characterId));
    }

    private static java.util.List<Long> partyMembers(PartyFacade partyFacade, long characterId) {
        return partyFacade.listMembers(characterId).stream()
                .map(PartyFacade.MemberView::characterId)
                .toList();
    }

    /**
     * Registry kết nối — kiêm WorldOutboundSink; EXP đánh quái nhân sự kiện (ADR-067), rồi kỹ năng bang (ADR-085), rồi
     * thưởng đạo lữ cùng nhóm (ADR-094), rồi quẻ bói hôm nay (ADR-149).
     */
    private static ConnectedCharacterRegistry newCharacterRegistry(
            GatewayFacades facades, MetricsContext metricsContext) {
        PartyFacade partyFacade = facades.contentFacades().socialFacades().partyFacade();
        return new ConnectedCharacterRegistry(
                facades.inventoryFacade(),
                facades.contentFacades().questFacade(),
                // ADR-067 — EXP đánh quái nhân theo sự kiện; ADR-085 — rồi theo Ngộ Tính của bang. Quest không đi đây.
                (characterId, amount) -> facades.characterFacade()
                        .grantExperience(
                                characterId,
                                fortuneBoosted(
                                        facades,
                                        characterId,
                                        SwornEffects.boostedExp(
                                                facades.progression()
                                                        .relationships()
                                                        .sworn(),
                                                characterId,
                                                DaoLuEffects.boostedExp(
                                                        facades.progression()
                                                                .relationships()
                                                                .daoLu(),
                                                        characterId,
                                                        GuildSkillEffects.boostedExp(
                                                                facades.progression()
                                                                        .guildSkills(),
                                                                characterId,
                                                                amount
                                                                        * facades.progression()
                                                                                .gameEvents()
                                                                                .multiplier(GameEventKind.EXP)),
                                                        id -> partyMembers(partyFacade, id)),
                                                id -> partyMembers(partyFacade, id)))),
                metricsContext.networkMetrics());
    }

    /** ADR-149 — quẻ bói hôm nay cộng/trừ phần trăm EXP đánh quái (sau mọi hệ số khác). */
    private static long fortuneBoosted(GatewayFacades facades, long characterId, long amount) {
        java.time.Instant now = java.time.Instant.now();
        long afterFortune = facades.progression().expansion().fortune().boostedExp(characterId, amount, now);
        // ADR-164 — Đan x2 kinh nghiệm nhân đôi SAU mọi hệ số khác (đọc từ RAM, không chạm DB mỗi lần hạ quái).
        return facades.progression().expansion().lot29().expBuff().boosted(characterId, afterFortune, now);
    }
}
