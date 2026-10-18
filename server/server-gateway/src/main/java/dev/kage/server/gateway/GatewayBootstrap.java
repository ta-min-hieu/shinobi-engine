package dev.kage.server.gateway;

import java.time.Duration;
import java.util.ArrayList;

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
import dev.kage.guild.api.GuildFacade;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.party.api.PartyFacade;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.quest.api.QuestFacade;
import dev.kage.server.database.ServerDatabaseBootstrap;
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
        ConnectedCharacterRegistry characterRegistry = new ConnectedCharacterRegistry(
                facades.inventoryFacade(),
                facades.contentFacades().questFacade(),
                facades.characterFacade()::grantExperience,
                metricsContext.networkMetrics());
        wireGoldChangeNotifications(databaseBootstrap, characterRegistry);
        WorldContext worldContext = startWorld(
                characterRegistry, databaseBootstrap, metricsContext.meterRegistry(), endpoints.metricsPort());
        MainWorldChannelRegistry mainWorldChannels = worldContext.mainWorldChannels();
        CharacterPositionPersister positionPersister =
                new CharacterPositionPersister(facades.characterFacade(), characterRegistry, POSITION_SAVE_INTERVAL);
        positionPersister.start();
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
        Runnable shutdown = shutdownSequence(
                new ShutdownContext(server, worldContext, instanceRegistries, characterRegistry),
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
            ctx.worldContext().mainWorldChannels().stopAll();
            ctx.instanceRegistries().dungeon().stopAll();
            ctx.instanceRegistries().arena().stopAll();
            ctx.characterRegistry().stop();
            positionPersister.stop();
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
            ConnectedCharacterRegistry characterRegistry) {}

    /**
     * Gộp {@link MainWorldChannelRegistry} + {@link MetricsHttpServer} — cả hai sinh ra CÙNG lúc
     * trong {@link #startWorld}.
     */
    private record WorldContext(MainWorldChannelRegistry mainWorldChannels, MetricsHttpServer metricsHttpServer) {}

    /**
     * Gộp Dungeon/Arena instance registry + {@link WorldGateway} — dựng cùng lúc,
     * xem {@link #buildInstanceRegistries}.
     */
    record InstanceRegistries(
            DungeonInstanceRegistry dungeon, ArenaInstanceRegistry arena, WorldGateway worldGateway) {}

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
                        arenaInstanceRegistry::isInDuel),
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
        characterRegistry.syncLevelUpsThrough(CharacterLevelSync.of(worldGateway));
        // Cùng lý do: "đồng đội cùng World" chỉ tra được khi mọi bảng định tuyến đã có.
        characterRegistry.shareKillRewardsThrough(
                KillRewardSharing.of(facades.contentFacades().socialFacades().partyFacade(), worldGateway));
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
        firstChannelFacade.start();
        MainWorldChannelRegistry mainWorldChannels =
                new MainWorldChannelRegistry(characterRegistry, firstChannelFacade, meterRegistry, worldContent);
        return new WorldContext(mainWorldChannels, metricsHttpServer);
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
        return new GatewayFacades(
                accountFacade, characterFacade, inventoryFacade, contentFacades, databaseBootstrap.wireGmFacade());
    }
}
