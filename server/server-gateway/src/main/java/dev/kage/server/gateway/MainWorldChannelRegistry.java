package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
     */
    synchronized WorldFacade attachCharacter(CharacterEntry entry) {
        long characterId = entry.characterId();
        maybeAutoOpenChannel();
        Channel channel = firstChannelWithRoom();
        channel.facade().attachCharacter(entry);
        channel.characterIds().add(characterId);
        channelByCharacterId.put(characterId, channel);
        return channel.facade();
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
     * Khu vực nhân vật này ĐANG ở, rỗng nếu không online qua registry này (vd. đang ở dungeon/duel
     * — {@code GatewayChannelHandler.currentWorldFacade} tự ưu tiên trước).
     */
    synchronized Optional<WorldFacade> worldFacadeFor(long characterId) {
        Channel channel = channelByCharacterId.get(characterId);
        return channel == null ? Optional.empty() : Optional.of(channel.facade());
    }

    /** Graceful shutdown (SERVER.md §10) — dừng MỌI khu vực đang chạy, kể cả #1. */
    synchronized void stopAll() {
        channels.forEach(channel -> channel.facade().stop());
        channels.clear();
        channelByCharacterId.clear();
    }

    private void maybeAutoOpenChannel() {
        int totalCapacity = totalCapacity();
        int totalPlayers = totalPlayers();
        if ((double) totalPlayers / totalCapacity >= AUTO_OPEN_UTILIZATION_THRESHOLD) {
            openChannel(DEFAULT_CAPACITY);
        }
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

    private Channel firstChannelWithRoom() {
        for (Channel channel : channels) {
            if (channel.characterIds().size() < channel.capacity()) {
                return channel;
            }
        }
        // Mọi khu vực đều đầy dù vừa auto-open (hiếm — race giữa nhiều lượt attach cùng lúc trên
        // authExecutor, hoặc capacity đặc biệt nhỏ) — mở thêm 1 khu dự phòng thay vì nhồi vượt cam kết.
        return openChannel(DEFAULT_CAPACITY);
    }

    private Channel openChannel(int capacity) {
        // Khu vực mở thêm là CÙNG map chính nên phải có CÙNG quái VÀ cùng NPC — dùng lại catalog
        // đã nạp một lần ở GatewayBootstrap, không đọc lại DB (ADR-037, ADR-040).
        WorldFacade facade =
                new WorldFacade(registry, true, new MicrometerTickMetricsPort(meterRegistry, WORLD_TAG), content);
        facade.start();
        Channel channel = new Channel(facade, capacity);
        channels.add(channel);
        return channel;
    }

    /** Đóng khu vực rỗng — KHÔNG BAO GIỜ đóng khu vực #1 (map chính luôn phải sẵn ít nhất 1 khu vực). */
    private void closeIfEmptyOverflowChannel(Channel channel) {
        if (channels.indexOf(channel) == 0 || !channel.characterIds().isEmpty()) {
            return;
        }
        channel.facade().stop();
        channels.remove(channel);
    }

    /** 1 khu vực (channel) đang chạy — {@code characterIds} mutable (người vào/ra liên tục). */
    private record Channel(WorldFacade facade, int capacity, Set<Long> characterIds) {
        private Channel(WorldFacade facade, int capacity) {
            this(facade, capacity, new HashSet<>());
        }
    }
}
