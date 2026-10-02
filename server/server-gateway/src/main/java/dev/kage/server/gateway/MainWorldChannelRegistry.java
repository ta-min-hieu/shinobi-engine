package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.micrometer.core.instrument.MeterRegistry;

import dev.kage.server.world.CharacterEntry;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.server.world.WorldFacade;

/**
 * Quản lý nhiều "khu vực" (channel) song song cho map chính — mỗi khu vực là 1 {@link WorldFacade}
 * riêng, cùng kỹ thuật {@code DungeonInstanceRegistry}/{@code ArenaInstanceRegistry} (World riêng
 * cùng process, ADR-026), nhưng dùng để GIỚI HẠN số người/khu vực thay vì cô lập theo party/trận.
 *
 * <p><b>Vì sao cần (Phase 9 lát cắt 6)</b> — spatial-grid sharding trong {@code AreaOfInterestTracker}
 * chỉ giảm chi phí khi entity TRẢI RỘNG nhiều ô; khi mọi người tụ CHUNG 1 khu vực nhỏ (đo thật
 * N=2000: tick vẫn vượt xa ngân sách dù đã sharding — xem BENCHMARK.md §5.2), thuật toán không giúp
 * được nữa vì bản chất vấn đề là MẬT ĐỘ, không phải cách tính. Giới hạn cứng số người/khu vực ở
 * TẦNG GAMEPLAY này mới thật sự đảm bảo AoI trong 1 {@link WorldFacade} không bao giờ vượt quá
 * {@link #DEFAULT_CAPACITY} (thường)/{@link #SPECIAL_CAPACITY} (khu vực đặc biệt) người, bất kể
 * tổng N toàn map lớn thế nào — bù đắp đúng khoảng trống mà thuật toán sharding tự nó không lấp được.
 *
 * <p>Khu vực #1 do {@code GatewayBootstrap} dựng SẴN (mang {@code TickMetricsPort} thật, tag
 * "main", ADR-015) và LUÔN tồn tại — map chính không bao giờ trống hẳn, registry chỉ nhận lại qua
 * constructor chứ không tự tạo. Khu vực tiếp theo mở TỰ ĐỘNG khi tổng số người ĐẠT
 * {@value #AUTO_OPEN_UTILIZATION_THRESHOLD} tổng sức chứa hiện tại (yêu cầu chủ dự án
 * 2026-07-28: "20 người/khu mặc định, đặc biệt 50; 80% thì tự mở khu mới") — kiểm tra ngay lúc
 * {@link #attachCharacter}, không cần thread nền/poll riêng. Khu vực rỗng (trừ #1) tự đóng khi
 * người cuối cùng rời đi — CHƯA được yêu cầu tường minh nhưng theo ĐÚNG tiền lệ dọn rác của
 * {@code DungeonInstance}/{@code DuelInstance} (Điều 9: không để khu vực thừa chạy vô ích).
 *
 * <p>⚠️ Chat Thế giới/Bang/Party/Riêng/Bạn bè/Mail KHÔNG bị ảnh hưởng bởi lớp này — toàn bộ định
 * tuyến qua {@link ConnectedCharacterRegistry} DÙNG CHUNG giữa MỌI khu vực (đã đúng kiến trúc từ
 * trước, xem {@code GatewayChannelHandler.routeChatMessage}: {@code CHAT_CHANNEL_WORLD} broadcast
 * toàn bộ registry, không phụ thuộc {@code WorldFacade} nào). CHỈ tầm nhìn AoI (di chuyển/combat) +
 * {@code CHAT_CHANNEL_AREA} (chat khu vực) là cục bộ theo khu vực — đúng ý nghĩa "khu vực" vốn có.
 *
 * <p>⚠️ Metrics (sửa lại — trước đây CHỈ khu vực #1 có {@code TickMetricsPort} thật, phát hiện lúc
 * load-test lại {@code WorldTickScheduler}, BENCHMARK.md §5.4, khi thấy p99 {@code /metrics} không
 * đại diện được ~50-125 khu vực đang chạy thật): MỌI khu vực (kể cả khu mở thêm ở {@link
 * #openChannel}) giờ mang {@link MicrometerTickMetricsPort} thật, CÙNG tag {@code "main"} — KHÔNG
 * dùng ID khu vực làm tag (OBSERVABILITY.md: tránh nổ cardinality, số khu vực co giãn không trần).
 * Micrometer gộp {@code Timer}/{@code Counter} có cùng tên+tag từ nhiều lần {@code register()} vào
 * ĐÚNG 1 đối tượng dùng chung — {@code kage.tick.duration}/{@code kage.tick.overrun.count}/
 * {@code kage.ecs.system.duration} nhờ vậy phản ánh ĐÚNG toàn bộ khu vực main map, không chỉ #1.
 * ⚠️ {@code kage.entities.count} là NGOẠI LỆ — nó là {@code Gauge} bọc 1 {@code AtomicInteger} CỦA
 * TỪNG instance; Micrometer KHÔNG gộp nhiều Gauge cùng tag (lần {@code register()} sau chỉ trả về
 * Gauge đã có, không đổi đối tượng nó đọc) — {@code kage.entities.count} vẫn CHỈ phản ánh khu vực
 * #1, y hệt trước khi sửa. Đây là giới hạn CÓ CHỦ ĐÍCH của lần sửa này (Điều 9 — không mở rộng
 * phạm vi ngoài p99/overrun đã yêu cầu); muốn số entity tổng đúng cần lát cắt riêng (vd. tự cộng dồn
 * qua {@link #channels} giống {@link #totalPlayers()}, không qua {@code TickMetricsPort}).
 *
 * <p>⚠️ Diễn giải "25 khu mặc định/map" (yêu cầu gốc): KHÔNG tạo sẵn 25 khu vực rỗng lúc khởi động
 * (tốn 25 tick thread cho world gần như trống — ngược precedent {@code DungeonInstance}/
 * {@code DuelInstance} luôn tạo LAZY, chỉ khi thật sự cần) — 25 chỉ dùng làm mốc tham chiếu trong
 * tài liệu capacity-planning (BENCHMARK.md), KHÔNG phải số khu vực khởi tạo cứng ở đây. Khu vực mở
 * lazy theo đúng công thức 80%, bắt đầu từ #1. Nếu chủ dự án thật sự muốn pre-warm 25 khu vực lúc
 * khởi động, đó là 1 dòng đổi ở {@link #attachCharacter}/constructor — chưa làm vì chưa được xác
 * nhận cần thiết.
 */
final class MainWorldChannelRegistry {

    static final int DEFAULT_CAPACITY = 20;
    static final int SPECIAL_CAPACITY = 50;
    private static final double AUTO_OPEN_UTILIZATION_THRESHOLD = 0.8;

    /** Tag CỐ ĐỊNH cho mọi khu vực map chính — xem javadoc lớp "Metrics" ở trên. */
    private static final String WORLD_TAG = "main";

    private final ConnectedCharacterRegistry registry;
    private final MeterRegistry meterRegistry;
    private final WorldContentCatalog content;
    private final List<Channel> channels = new ArrayList<>();
    private final Map<Long, Channel> channelByCharacterId = new HashMap<>();

    /**
     * Dừng khu vực phụ vừa rỗng NGOÀI khoá và ngoài EventLoop — xem {@link #closeIfEmptyOverflowChannel}.
     * Cùng khuôn {@code ArenaInstanceRegistry.settlementExecutor}/{@code DungeonInstanceRegistry.defeatExecutor}.
     */
    private final ExecutorService channelStopExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * {@code firstChannelFacade} PHẢI đã {@code start()} — {@code GatewayBootstrap} dựng nó cùng
     * {@code TickMetricsPort} thật. {@code meterRegistry} dùng để gắn {@code TickMetricsPort} thật
     * cho MỌI khu vực mở thêm sau ({@link #openChannel}) — xem javadoc lớp "Metrics".
     */
    MainWorldChannelRegistry(
            ConnectedCharacterRegistry registry,
            WorldFacade firstChannelFacade,
            MeterRegistry meterRegistry,
            WorldContentCatalog content) {
        this.registry = registry;
        this.meterRegistry = meterRegistry;
        this.content = content;
        channels.add(new Channel(firstChannelFacade, DEFAULT_CAPACITY));
    }

    /**
     * Nội dung tĩnh của world chính — dùng lại cho World của instance (ADR-048).
     *
     * <p>Hầm ngục cần catalog để tra mã boss, nhưng {@code buildInstanceRegistries} đã dùng hết 4
     * tham số (trần Checkstyle {@code ParameterNumber}). Lớp này vốn đã giữ sẵn catalog, nên mở
     * một getter rẻ hơn hẳn việc gói thêm một record chỉ để chuyền một giá trị đi qua.
     */
    WorldContentCatalog worldContent() {
        return content;
    }

    /**
     * Định tuyến vào khu vực còn chỗ (mở thêm nếu cần) — trả lại {@link WorldFacade} của khu vực đó
     * để caller gọi tiếp (vd. {@code applyEquipmentBonus}).
     *
     * <p>{@code synchronized}: gọi từ NHIỀU thread khác nhau — {@code authExecutor} (select
     * character, {@code DungeonInstanceRegistry}) VÀ {@code ArenaInstanceRegistry.settlementExecutor}
     * (settle duel xong, đưa cả 2 về map chính) — cùng lý do {@code ArenaInstanceRegistry} tự dùng
     * {@code ConcurrentHashMap} cho bookkeeping riêng của nó. {@link Channel#characterIds()} +
     * {@link #channels}/{@link #channelByCharacterId} đọc-sửa nhiều bước (đếm sức chứa, chọn khu
     * vực, ghi 2 map) KHÔNG an toàn nếu chỉ đổi sang collection concurrent đơn lẻ — cần khoá cả khối.
     *
     * <p><b>Dựng World mới NGOÀI khoá (2026-10-02).</b> {@link #worldFacadeFor} cũng cần khoá này, và
     * EventLoop gọi nó cho MỌI gói di chuyển/tấn công của MỌI người chơi. Dựng một {@link WorldFacade}
     * tốn ~55–110ms (constructor chạy thử từng script skill trong một GraalJS Context mới) — bản cũ
     * dựng nó ngay trong khoá, nên mỗi lần mở khu vực mới mọi EventLoop đứng chờ chừng ấy (SERVER.md §2:
     * EventLoop cấm chờ khoá). Nay khi cần khu vực mới thì nhả khoá, dựng World, rồi mới vào lại khoá
     * và gắn; World dựng thừa (luồng khác vừa mở trước) chưa từng {@code start()} nên bỏ đi là xong.
     */
    WorldFacade attachCharacter(CharacterEntry entry) {
        return tryAttach(entry, Optional.empty()).orElseGet(() -> tryAttach(entry, Optional.of(newChannelFacade()))
                .orElseThrow());
    }

    /**
     * Mở sẵn khu vực mới (dựng World NGOÀI mọi khoá) nếu lượt {@link #attachCharacter} kế tiếp sẽ cần
     * nó. Dành cho caller đang chuẩn bị gắn nhân vật trong lúc giữ khoá của CHÍNH nó (vd. {@code
     * DungeonInstanceRegistry} đưa người về world chính) — không gọi trước thì chi phí dựng World rơi
     * vào trong khoá của caller, mà khoá đó EventLoop cũng xin cho mỗi gói di chuyển.
     */
    void ensureRoomForOneMore() {
        if (needsNewChannel()) {
            adoptChannelIfStillNeeded(newChannelFacade());
        }
    }

    /**
     * Phần trong khoá của {@link #attachCharacter}. Rỗng ⇔ cần mở khu vực mới mà {@code spare} rỗng —
     * caller dựng World ngoài khoá rồi gọi lại. {@code spare} không dùng tới thì bị bỏ (chưa start).
     */
    private synchronized Optional<WorldFacade> tryAttach(CharacterEntry entry, Optional<WorldFacade> spare) {
        long characterId = entry.characterId();
        Channel current = channelByCharacterId.get(characterId);
        if (current != null) {
            // Một nhân vật chỉ ở MỘT khu vực (quyết định chủ dự án 2026-09-25: một phiên sống). Trước
            // đây khu vực đầu đầy thì lần gắn thứ hai rơi sang khu vực khác — hai thực thể, cày gấp
            // đôi, và detach sau chỉ gỡ được một. Gắn lại khi đã ở trong là không làm gì cả.
            return Optional.of(current.facade());
        }
        if (needsNewChannel()) {
            if (spare.isEmpty()) {
                return Optional.empty();
            }
            openChannel(spare.get(), DEFAULT_CAPACITY);
        }
        Channel channel = firstChannelWithRoom();
        channel.facade().attachCharacter(entry);
        channel.characterIds().add(characterId);
        channelByCharacterId.put(characterId, channel);
        return Optional.of(channel.facade());
    }

    private synchronized void adoptChannelIfStillNeeded(WorldFacade spare) {
        if (needsNewChannel()) {
            openChannel(spare, DEFAULT_CAPACITY);
        }
    }

    synchronized void detachCharacter(long characterId) {
        Channel channel = channelByCharacterId.remove(characterId);
        if (channel == null) {
            return;
        }
        channel.facade().detachCharacter(characterId);
        channel.characterIds().remove(characterId);
        closeIfEmptyOverflowChannel(channel);
    }

    /**
     * {@link #detachCharacter} rồi trả mốc "khu vực cũ đã xử lý xong lệnh detach" ({@link
     * WorldFacade#inboxBarrier()}). Sau mốc này khu vực cũ không còn phát gói nào cho nhân vật — người
     * dời World chờ nó trước khi gửi {@code WorldChangedEvent} (quyết định chủ dự án 2026-10-01, xem
     * {@code ArenaInstanceRegistry#moveCharacterIntoDuel}). Không ở khu vực nào ⇒ xong ngay.
     */
    synchronized CompletableFuture<Void> detachCharacterAndDrain(long characterId) {
        Channel channel = channelByCharacterId.get(characterId);
        if (channel == null) {
            return CompletableFuture.completedFuture(null);
        }
        detachCharacter(characterId);
        // Xếp SAU lệnh detach trên cùng hàng đợi. Khu vực phụ vừa bị đóng vì rỗng thì stop() đang
        // chạy (hoặc sắp chạy) trên luồng khác — mốc xong khi tick xử lý tới nó, hoặc khi stop() xong
        // hẳn (WorldFacade#inboxBarrier chờ cả một stop() đang dở), không bao giờ sớm hơn.
        return channel.facade().inboxBarrier();
    }

    /**
     * Khu vực nhân vật này ĐANG ở, rỗng nếu không online qua registry này (vd. đang ở dungeon/duel
     * — {@code GatewayChannelHandler.currentWorldFacade} tự ưu tiên trước).
     */
    synchronized Optional<WorldFacade> worldFacadeFor(long characterId) {
        Channel channel = channelByCharacterId.get(characterId);
        return channel == null ? Optional.empty() : Optional.of(channel.facade());
    }

    /** Tổng số nhân vật đang ở MỌI khu vực map chính — mỗi nhân vật đếm đúng một lần. */
    synchronized int playerCount() {
        return totalPlayers();
    }

    /** Graceful shutdown (SERVER.md §10) — dừng MỌI khu vực đang chạy, kể cả #1. */
    synchronized void stopAll() {
        channels.forEach(channel -> channel.facade().stop());
        channels.clear();
        channelByCharacterId.clear();
        channelStopExecutor.shutdown();
    }

    /**
     * Lượt gắn kế tiếp có phải mở khu vực mới không: tổng số người đã ĐẠT ngưỡng tự mở, hoặc mọi khu
     * vực đều đầy (hiếm — capacity đặc biệt nhỏ) — mở thêm thay vì nhồi vượt cam kết.
     */
    private synchronized boolean needsNewChannel() {
        int totalCapacity = totalCapacity();
        int totalPlayers = totalPlayers();
        return (double) totalPlayers / totalCapacity >= AUTO_OPEN_UTILIZATION_THRESHOLD || !hasChannelWithRoom();
    }

    private boolean hasChannelWithRoom() {
        for (Channel channel : channels) {
            if (channel.characterIds().size() < channel.capacity()) {
                return true;
            }
        }
        return false;
    }

    private int totalCapacity() {
        int sum = 0;
        for (Channel channel : channels) {
            sum += channel.capacity();
        }
        return sum;
    }

    private int totalPlayers() {
        int sum = 0;
        for (Channel channel : channels) {
            sum += channel.characterIds().size();
        }
        return sum;
    }

    /** Chỉ gọi sau {@link #needsNewChannel} = {@code false} (hoặc vừa mở khu mới) — luôn có chỗ. */
    private Channel firstChannelWithRoom() {
        for (Channel channel : channels) {
            if (channel.characterIds().size() < channel.capacity()) {
                return channel;
            }
        }
        throw new IllegalStateException("Không khu vực nào còn chỗ dù vừa kiểm — lỗi lập trình");
    }

    /**
     * Dựng World cho một khu vực mới, CHƯA start — gọi NGOÀI khoá (xem {@link #attachCharacter}).
     * Khu vực mở thêm là CÙNG map chính nên phải có CÙNG quái VÀ cùng NPC — dùng lại catalog đã nạp
     * một lần ở GatewayBootstrap, không đọc lại DB (ADR-037, ADR-040).
     */
    private WorldFacade newChannelFacade() {
        return new WorldFacade(registry, true, new MicrometerTickMetricsPort(meterRegistry, WORLD_TAG), content);
    }

    private void openChannel(WorldFacade facade, int capacity) {
        facade.start();
        channels.add(new Channel(facade, capacity));
    }

    /**
     * Đóng khu vực rỗng — KHÔNG BAO GIỜ đóng khu vực #1 (map chính luôn phải sẵn ít nhất 1 khu vực).
     *
     * <p>Gỡ khỏi bảng NGAY (trong khoá — không ai được gắn vào nó nữa) nhưng {@code stop()} chạy trên
     * {@link #channelStopExecutor} (2026-10-02): hàm này chạy trong khoá mà EventLoop xin cho MỌI gói
     * di chuyển, thường còn trên chính EventLoop (rớt mạng), mà {@code stop()} chờ lần tick dở dang.
     * Người cần "khu vực cũ đã im" ({@link #detachCharacterAndDrain}) chờ mốc chặn, mốc đó chờ cả
     * {@code stop()} đang dở — nên thứ tự "World cũ im rồi mới báo 308" không đổi.
     */
    private void closeIfEmptyOverflowChannel(Channel channel) {
        if (channels.indexOf(channel) == 0 || !channel.characterIds().isEmpty()) {
            return;
        }
        WorldStopper.stopInBackground(channelStopExecutor, channel.facade());
        channels.remove(channel);
    }

    /** 1 khu vực (channel) đang chạy — {@code characterIds} mutable (người vào/ra liên tục). */
    private record Channel(WorldFacade facade, int capacity, Set<Long> characterIds) {
        private Channel(WorldFacade facade, int capacity) {
            this(facade, capacity, new HashSet<>());
        }
    }
}
