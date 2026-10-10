package dev.kage.server.gateway;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.ExperienceProgress;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterAppearanceChangedEvent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.EffectKind;
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.GoldChangedEvent;
import dev.kage.protocol.HealthChangedEvent;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.NpcDespawnedEvent;
import dev.kage.protocol.NpcSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestStatus;
import dev.kage.protocol.QuestStatusChangedEvent;
import dev.kage.protocol.ServerShuttingDownEvent;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.quest.api.QuestFacade;
import dev.kage.server.world.EquipmentBonusSnapshot;
import dev.kage.server.world.WorldOutboundSink;

/**
 * Sổ đăng ký {@code characterId -> ChannelHandlerContext} + implementation THẬT của
 * {@link WorldOutboundSink} (SERVER.md §2: "Response/Event vào outbound queue"). MODULE DUY
 * NHẤT được import Netty (Điều 23) nên đây là chỗ duy nhất server-world's kết quả tick được biến
 * thành packet + ghi ra dây — server-world tự nó không biết {@code ChannelHandlerContext}.
 *
 * <p>Mọi phương thức {@code onXxx} bị gọi TRÊN world tick thread ({@link WorldOutboundSink}
 * javadoc) — {@code ctx.writeAndFlush} an toàn gọi từ thread khác EventLoop (Netty tự chuyển
 * task nội bộ), nên KHÔNG có blocking nào ở đây.
 */
final class ConnectedCharacterRegistry implements WorldOutboundSink {

    private final InventoryFacade inventoryFacade;
    private final QuestFacade questFacade;
    private final ExperienceGrantPort experienceGrant;
    private final GatewayNetworkMetrics networkMetrics;
    private final Map<Long, ChannelHandlerContext> contextsByCharacterId = new ConcurrentHashMap<>();
    // Ngoại hình của nhân vật ĐANG kết nối, chụp một lần lúc chọn nhân vật. Ở đây chứ không ở
    // server-world vì world chỉ biết vị trí và chỉ số — bắt nó mang thêm hệ phái/bậc trang phục
    // là đẩy chuyện hiển thị xuống tầng gameplay, và WorldOutboundSink sẽ phải đổi chữ ký chỉ để
    // chở dữ liệu mà tầng đó không dùng.
    private final Map<Long, CharacterAppearance> appearanceByCharacterId = new ConcurrentHashMap<>();
    // Vị trí MỚI NHẤT của CHÍNH nhân vật (không phải nhân vật khác) — nguồn cho
    // CharacterPositionPersister lưu định kỳ + lúc logout (SERVER.md §8), cập nhật mỗi lần
    // onMoveAccepted (server-world xác nhận lệnh di chuyển của connection này).
    private final Map<Long, PositionSnapshot> lastKnownPosition = new ConcurrentHashMap<>();
    // Vạch EXP MỚI NHẤT đã gửi cho nhân vật đang kết nối — xem sendExperience. Xoá lúc register (mỗi
    // lần vào world là một phiên hiển thị mới) và lúc unregister.
    private final Map<Long, ExperienceProgress> lastSentExperience = new ConcurrentHashMap<>();
    // onMonsterKilled bị gọi TRÊN world tick thread (WorldOutboundSink javadoc) — grantLoot chạm
    // DB (blocking), CẤM chạy trực tiếp ở đó (server/CLAUDE.md: Game Tick cấm blocking I/O). Đẩy
    // sang Virtual Thread riêng, mỗi việc tự cô lập lỗi của nó — xem KillRewardDispatcher. Giữ tên
    // cũ `lootExecutor` (nhiều module nhắc tới nó trong comment) dù nay chạy cả quest lẫn EXP.
    private final KillRewardDispatcher lootExecutor = new KillRewardDispatcher();
    // Ai có quyền giữ/dọn phiên của mỗi nhân vật (quyết định chủ dự án 2026-09-25) — ở đây vì
    // registry này vốn là thứ DUY NHẤT mọi kết nối cùng thấy, không phải thêm tham số cho handler.
    private final CharacterSessionRegistry sessions = new CharacterSessionRegistry();
    // Trần chat theo tài khoản — ở đây cùng lý do với sessions: phải sống LÂU HƠN một kết nối.
    private final ChatRateLimits chatRateLimits = new ChatRateLimits();

    ConnectedCharacterRegistry(
            InventoryFacade inventoryFacade,
            QuestFacade questFacade,
            ExperienceGrantPort experienceGrant,
            GatewayNetworkMetrics networkMetrics) {
        this.inventoryFacade = inventoryFacade;
        this.questFacade = questFacade;
        this.experienceGrant = experienceGrant;
        this.networkMetrics = networkMetrics;
    }

    void register(long characterId, ChannelHandlerContext ctx) {
        register(characterId, ctx, CharacterAppearance.unknown());
    }

    /**
     * Đăng ký kèm ngoại hình để {@link #onCharacterSpawned} nói được cho nhân vật khác biết phải vẽ
     * model nào (ADR-031). Overload không có {@code appearance} ở trên dành cho test chỉ quan tâm
     * đường đi của packet.
     */
    void register(long characterId, ChannelHandlerContext ctx, CharacterAppearance appearance) {
        contextsByCharacterId.put(characterId, ctx);
        appearanceByCharacterId.put(characterId, appearance);
        lastSentExperience.remove(characterId);
    }

    /**
     * Ngoại hình vừa đổi giữa phiên chơi (đổi hệ phái — {@code SelectFactionRequest}, opcode 206).
     *
     * <p><b>Vì sao bắt buộc phải có.</b> {@link #appearanceByCharacterId} chỉ được ghi MỘT LẦN lúc
     * {@link #register}, nghĩa là lúc nhân vật vào world. Đổi hệ phái xong mà không cập nhật ở đây
     * thì mọi {@code CharacterSpawnedEvent} phát về sau — kể cả cho người chơi VỪA đi tới lần đầu
     * — vẫn mang hệ phái CŨ, cho tới khi chính nhân vật đó thoát ra và đăng nhập lại.
     *
     * <p>Chỉ vá CACHE. Người đang NHÌN THẤY nhân vật này lúc đổi được báo bằng đường khác —
     * {@link #onCharacterAppearanceChanged} (ADR-045), phát từ world tick sau khi hỏi AoI ai đang
     * quan sát. Hai nửa phải có cả hai: thiếu nửa này thì người tới sau thấy model cũ, thiếu nửa
     * kia thì người đang đứng cạnh thấy model cũ.
     *
     * <p>Bỏ qua nếu nhân vật không còn kết nối: không có gì để giữ, và lần đăng nhập sau
     * {@link #register} tự đọc lại hệ phái thật từ DB.
     */
    void updateAppearance(long characterId, CharacterAppearance appearance) {
        appearanceByCharacterId.computeIfPresent(characterId, (id, previous) -> appearance);
    }

    void unregister(long characterId) {
        // Thứ tự bắt buộc: xoá khỏi contextsByCharacterId TRƯỚC lastKnownPosition — xem
        // trackPositionIfConnected, đó là thứ khiến một onMoveAccepted muộn không đẻ lại dòng vị trí.
        contextsByCharacterId.remove(characterId);
        lastKnownPosition.remove(characterId);
        appearanceByCharacterId.remove(characterId);
        lastSentExperience.remove(characterId);
    }

    /**
     * Như {@link #unregister(long)} nhưng CHỈ khi nhân vật vẫn gắn với đúng {@code ctx} — lần dọn của
     * một kết nối cũ không bao giờ được xoá nhân vật mà kết nối MỚI đã đăng ký (bug trước quyết định
     * một-phiên 2026-09-25: kết nối nào đóng trước thì dọn luôn nhân vật của kết nối kia).
     *
     * @return {@code true} nếu đã dọn; {@code false} nếu nhân vật đã thuộc kết nối khác (hoặc đã dọn)
     */
    boolean unregister(long characterId, ChannelHandlerContext ctx) {
        if (!contextsByCharacterId.remove(characterId, ctx)) {
            return false;
        }
        lastKnownPosition.remove(characterId);
        appearanceByCharacterId.remove(characterId);
        lastSentExperience.remove(characterId);
        return true;
    }

    /** Sổ chủ phiên của mọi nhân vật — xem {@link CharacterSessionRegistry}. */
    CharacterSessionRegistry sessions() {
        return sessions;
    }

    /** Trần tần suất chat của mọi tài khoản — xem {@link ChatRateLimits}. */
    ChatRateLimits chatRateLimits() {
        return chatRateLimits;
    }

    /**
     * Đang kết nối không — dùng để hiển thị trạng thái online (feature-friend) mà KHÔNG cần thêm
     * registry Redis riêng ở single-instance hiện tại (feature-friend/CLAUDE.md).
     */
    boolean isOnline(long characterId) {
        return contextsByCharacterId.containsKey(characterId);
    }

    /** Tổng số nhân vật đang kết nối (OBSERVABILITY.md §3.1 {@code kage.players.online}). */
    int onlineCount() {
        return contextsByCharacterId.size();
    }

    /**
     * Đẩy 1 packet cho ĐÚNG 1 nhân vật khác nếu đang online — no-op an toàn nếu offline (đã ngắt
     * kết nối). Dùng cho push per-player không gắn AoI/broadcast (bạn online, thư mới...).
     */
    void sendToCharacterIfOnline(long characterId, int opcode, int sequenceId, byte[] payload) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(characterId);
        if (ctx != null) {
            send(ctx, opcode, sequenceId, payload);
        }
    }

    /** Bản chụp vị trí đã biết của MỌI nhân vật đang kết nối — dùng cho lưu định kỳ (quét hết). */
    Map<Long, PositionSnapshot> snapshotPositions() {
        return Map.copyOf(lastKnownPosition);
    }

    /**
     * Vị trí đã biết của MỘT nhân vật, hoặc {@code null} nếu world chính chưa báo lần nào kể từ khi
     * vào world (chưa gửi lệnh di chuyển và chưa tới nhịp {@link #onCharacterPositionSampled} đầu tiên).
     */
    PositionSnapshot lastKnownPosition(long characterId) {
        return lastKnownPosition.get(characterId);
    }

    /** SERVER.md §10 bước 2 — báo MỌI connection đã chọn nhân vật TRƯỚC khi đóng kết nối. */
    void broadcastShuttingDown() {
        ServerShuttingDownEvent event = ServerShuttingDownEvent.newBuilder().build();
        byte[] payload = event.toByteArray();
        contextsByCharacterId
                .values()
                .forEach(ctx -> send(ctx, Opcode.OPCODE_SERVER_SHUTTING_DOWN_EVENT_VALUE, 0, payload));
    }

    /** Đẩy 1 packet cho MỌI connection đang kết nối — dùng cho kênh chat Thế giới (GAMEPLAY.md §10). */
    void broadcastToAll(int opcode, byte[] payload) {
        contextsByCharacterId.values().forEach(ctx -> send(ctx, opcode, 0, payload));
    }

    @Override
    public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot accepted) {
        // z KHÔNG vào PositionSnapshot/DB (ADR-033) — nhân vật luôn hồi sinh/đăng nhập lại ở mặt
        // đất z=0 (WorldFacade.respawnCharacter), nên trục dọc là trạng thái CHỈ SỐNG TRONG TICK,
        // không cần bền vững. Chỉ x/y (mặt đất) mới cần cho CharacterPositionPersister.
        trackPositionIfConnected(accepted.characterId(), accepted.x(), accepted.y());
        sendMoveResponseWithoutTrackingPosition(accepted);
    }

    /**
     * Vị trí thật theo nhịp của world chính (sửa 2026-10-10) — thiếu nó thì {@code lastKnownPosition}
     * chỉ đổi khi client gửi lệnh, tức là chỗ người chơi ĐỔI HƯỚNG lần cuối chứ không phải chỗ nhân
     * vật đang đứng. Một {@link PositionSnapshot} mỗi nhân vật mỗi GIÂY (nhịp của world), không phải
     * mỗi tick.
     */
    @Override
    public void onCharacterPositionSampled(long characterId, float x, float y) {
        trackPositionIfConnected(characterId, x, y);
    }

    /**
     * Ghi vị trí mới nhất CHỈ KHI nhân vật còn đăng ký — nguyên tử với {@link #unregister}.
     *
     * <p><b>Vì sao không {@code put} thẳng.</b> {@code onMoveAccepted}/{@code onCharacterRespawned}
     * chạy trên world tick thread, còn {@link #unregister} chạy trên EventLoop và CHỈ SAU ĐÓ mới
     * xếp {@code detachCharacter} vào inbox của world. Mọi lệnh di chuyển đã nằm trong inbox trước
     * lệnh detach vẫn được world xử lý và gọi {@code onMoveAccepted} SAU {@code unregister} —
     * {@code put} thẳng sẽ đẻ lại một dòng trong {@code lastKnownPosition} cho nhân vật đã thoát:
     * rò rỉ vĩnh viễn, và {@code CharacterPositionPersister} ghi nó xuống DB ở MỌI lượt định kỳ.
     * Kiểm {@code containsKey} rồi mới {@code put} cũng không đủ (unregister có thể chen giữa hai
     * bước). {@code computeIfPresent} giữ khoá bin của khoá này trong {@code contextsByCharacterId}
     * suốt lúc ghi, còn {@link #unregister} xoá khỏi {@code contextsByCharacterId} TRƯỚC rồi mới
     * xoá {@code lastKnownPosition} — nên hoặc lần ghi chạy trọn trước và bị unregister dọn, hoặc
     * nó thấy nhân vật đã vắng mặt và không ghi gì.
     */
    private void trackPositionIfConnected(long characterId, float x, float y) {
        contextsByCharacterId.computeIfPresent(characterId, (id, ctx) -> {
            lastKnownPosition.put(id, new PositionSnapshot(x, y));
            return ctx;
        });
    }

    /**
     * Gửi {@code MoveResponse} KHÔNG ghi {@code lastKnownPosition} — dùng bởi
     * {@code DungeonOutboundSink} (Phase 8 lát cắt 1, ADR-026): toạ độ trong dungeon là không gian
     * riêng của instance đó, ghi vào đây sẽ làm {@code CharacterPositionPersister} lưu NHẦM toạ độ
     * dungeon thành vị trí thật của nhân vật. Nhờ world chính không còn gọi {@link #onMoveAccepted}
     * cho nhân vật đã {@code detachCharacter} (đang ở dungeon), {@code lastKnownPosition} tự "đóng
     * băng" đúng ở toạ độ thật cuối cùng — không cần cờ "đang ở dungeon" nào thêm.
     */
    void sendMoveResponseWithoutTrackingPosition(WorldOutboundSink.MoveAcceptedSnapshot accepted) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(accepted.characterId());
        if (ctx == null) {
            return; // đã ngắt kết nối giữa lúc lệnh còn trong hàng đợi world — bỏ qua an toàn.
        }
        MoveResponse response = MoveResponse.newBuilder()
                .setX(accepted.x())
                .setY(accepted.y())
                .setZ(accepted.z())
                .setGrounded(accepted.grounded())
                .build();
        send(ctx, Opcode.OPCODE_MOVE_RESPONSE_VALUE, accepted.sequenceId(), response.toByteArray());
    }

    @Override
    public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        // Nhân vật spawn có thể vừa ngắt kết nối giữa lúc AoI còn đang xử lý tick — khi đó không
        // còn ngoại hình để tra, và unknown() để client tự chọn model thay thế thay vì bỏ vẽ.
        CharacterAppearance appearance =
                appearanceByCharacterId.getOrDefault(spawnedCharacterId, CharacterAppearance.unknown());
        CharacterSpawnedEvent event = CharacterSpawnedEvent.newBuilder()
                .setCharacterId(spawnedCharacterId)
                .setX(x)
                .setY(y)
                .setFaction(appearance.faction())
                .setCostumeTier(appearance.costumeTier())
                .build();
        send(ctx, Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        CharacterDespawnedEvent event = CharacterDespawnedEvent.newBuilder()
                .setCharacterId(despawnedCharacterId)
                .build();
        send(ctx, Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        CharacterMovedEvent event = CharacterMovedEvent.newBuilder()
                .setCharacterId(movedCharacterId)
                .setX(x)
                .setY(y)
                .build();
        send(ctx, Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        MonsterSpawnedEvent event = MonsterSpawnedEvent.newBuilder()
                .setMonsterId(monster.monsterId())
                .setMonsterCode(monster.monsterCode())
                .setX(monster.x())
                .setY(monster.y())
                .setCurrentHp(monster.currentHp())
                .setMaxHp(monster.maxHp())
                .setModelKey(monster.modelKey())
                .build();
        send(ctx, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onMonsterDespawned(long targetCharacterId, long monsterId) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        MonsterDespawnedEvent event =
                MonsterDespawnedEvent.newBuilder().setMonsterId(monsterId).build();
        send(ctx, Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        MonsterMovedEvent event = MonsterMovedEvent.newBuilder()
                .setMonsterId(monsterId)
                .setX(x)
                .setY(y)
                .build();
        send(ctx, Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onNpcSpawned(long targetCharacterId, WorldOutboundSink.NpcSnapshot npc) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        NpcSpawnedEvent event = NpcSpawnedEvent.newBuilder()
                .setNpcId(npc.npcId())
                .setNpcCode(npc.npcCode())
                .setX(npc.x())
                .setY(npc.y())
                .build();
        send(ctx, Opcode.OPCODE_NPC_SPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onNpcDespawned(long targetCharacterId, long npcId) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        NpcDespawnedEvent event = NpcDespawnedEvent.newBuilder().setNpcId(npcId).build();
        send(ctx, Opcode.OPCODE_NPC_DESPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        AttackResultEvent event = AttackResultEvent.newBuilder()
                .setAttackerEntityId(outcome.attackerEntityId())
                .setTargetEntityId(outcome.targetEntityId())
                .setDamage(outcome.damage())
                .setIsCrit(outcome.crit())
                .setIsMiss(outcome.miss())
                .setTargetCurrentHp(outcome.targetCurrentHp())
                .setTargetDied(outcome.targetDied())
                .build();
        send(ctx, Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        SkillResultEvent event = SkillResultEvent.newBuilder()
                .setSkillCode(outcome.skillCode())
                .setAttackerEntityId(outcome.attackerEntityId())
                .setTargetEntityId(outcome.targetEntityId())
                .setDamage(outcome.damage())
                .setIsCrit(outcome.crit())
                .setIsMiss(outcome.miss())
                .setTargetCurrentHp(outcome.targetCurrentHp())
                .setTargetDied(outcome.targetDied())
                .setEffectKind(toProtoEffectKind(outcome.effectKind()))
                .build();
        send(ctx, Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        HealthChangedEvent event = HealthChangedEvent.newBuilder()
                .setEntityId(health.entityId())
                .setCurrentHp(health.currentHp())
                .setMaxHp(health.maxHp())
                .build();
        send(ctx, Opcode.OPCODE_HEALTH_CHANGED_EVENT_VALUE, 0, event.toByteArray());
    }

    private static EffectKind toProtoEffectKind(dev.kage.server.world.SkillEffectKind effectKind) {
        return switch (effectKind) {
            case DAMAGE -> EffectKind.EFFECT_KIND_DAMAGE;
            case HEAL -> EffectKind.EFFECT_KIND_HEAL;
            case SHIELD -> EffectKind.EFFECT_KIND_SHIELD;
            case BUFF -> EffectKind.EFFECT_KIND_BUFF;
            case DEBUFF -> EffectKind.EFFECT_KIND_DEBUFF;
        };
    }

    @Override
    public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        if (ctx == null) {
            return;
        }
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_AREA)
                .setSenderId(senderCharacterId)
                .setSenderName(senderName)
                .setBody(body)
                .build();
        send(ctx, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event.toByteArray());
    }

    /**
     * ADR-045 — ngoại hình của {@code changedCharacterId} vừa đổi; đây là chỗ DUY NHẤT biết ngoại
     * hình đó là gì ({@link #appearanceByCharacterId}), nên server-world chỉ gửi id sang.
     *
     * <p>Không dùng {@link CharacterAppearance#unknown()} thay thế như {@link #onCharacterSpawned}:
     * ở đó "không tra được" nghĩa là nhân vật vừa ngắt kết nối và client vẫn cần MỘT model để vẽ;
     * ở đây không tra được nghĩa là không có gì để đổi, và gửi {@code unknown} sẽ làm người quan
     * sát thay model ĐANG ĐÚNG bằng model thay thế.
     */
    @Override
    public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(targetCharacterId);
        CharacterAppearance appearance = appearanceByCharacterId.get(changedCharacterId);
        if (ctx == null || appearance == null) {
            return;
        }
        CharacterAppearanceChangedEvent event = CharacterAppearanceChangedEvent.newBuilder()
                .setCharacterId(changedCharacterId)
                .setFaction(appearance.faction())
                .setCostumeTier(appearance.costumeTier())
                .build();
        send(ctx, Opcode.OPCODE_CHARACTER_APPEARANCE_CHANGED_EVENT_VALUE, 0, event.toByteArray());
    }

    /**
     * World CHÍNH không tự chốt gì ở đây — {@code WorldFacade} là nơi quyết định hồi sinh (gọi
     * {@link #onCharacterRespawned} ngay sau đó), registry chỉ là đường ống gửi packet. Chốt kết
     * quả trận đấu (Arena) route qua {@code ArenaOutboundSink} override method này, KHÔNG đi qua
     * nhánh no-op này.
     */
    @Override
    public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
        // Không làm gì — WorldFacade tự gọi onCharacterRespawned, xem javadoc phía trên.
    }

    @Override
    public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(respawn.characterId());
        if (ctx == null) {
            return;
        }
        trackPositionIfConnected(respawn.characterId(), respawn.x(), respawn.y());
        CharacterRespawnedEvent event = CharacterRespawnedEvent.newBuilder()
                .setX(respawn.x())
                .setY(respawn.y())
                .setCurrentHp(respawn.currentHp())
                .setMaxHp(respawn.maxHp())
                .setCurrentChi(respawn.currentChi())
                .setMaxChi(respawn.maxChi())
                .build();
        send(ctx, Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE, 0, event.toByteArray());
    }

    @Override
    public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {
        lootExecutor.dispatch(
                KillRewardDispatcher.Step.LOOT,
                killerCharacterId,
                monsterCode,
                () -> grantLootBlocking(killerCharacterId, monsterCode));
        // Cạnh grantLoot, KHÔNG qua EventBus (engine-ecs) — 1 nguồn duy nhất chưa tới ngưỡng đáng
        // kích hoạt pub-sub thật (feature-quest/CLAUDE.md). Virtual Thread RIÊNG cho quest, không
        // chặn nhau với lượt rơi đồ nếu 1 trong 2 chạm DB chậm.
        lootExecutor.dispatch(
                KillRewardDispatcher.Step.QUEST_PROGRESS,
                killerCharacterId,
                monsterCode,
                () -> processQuestBlocking(killerCharacterId, monsterCode));
        // Cạnh loot/quest, cùng lý do tách Virtual Thread riêng: cộng EXP chạm DB. Thêm 2026-08-09
        // khi chủ dự án hỏi vạch kinh nghiệm — trước đó giết quái KHÔNG cho EXP gì cả.
        lootExecutor.dispatch(
                KillRewardDispatcher.Step.EXPERIENCE,
                killerCharacterId,
                monsterCode,
                () -> grantExperienceBlocking(killerCharacterId, expReward));
    }

    /**
     * Cộng EXP cho người hạ gục rồi báo vạch mới. Quái chưa khai EXP cho 0 — bỏ qua sớm, không đụng
     * DB chỉ để cộng số không (giữ nguyên hành vi cũ của {@code MonsterExperience}).
     *
     * <p>ADR-037 D4: con số này đến TỪ {@code monster_template.exp_reward} và được world chở theo
     * lượt báo "quái chết", thay cho bảng tra Java {@code MonsterExperience} nay đã bị xoá.
     */
    private void grantExperienceBlocking(long killerCharacterId, long reward) {
        if (reward <= 0L) {
            return;
        }
        sendExperience(killerCharacterId, experienceGrant.grantExperience(killerCharacterId, reward));
    }

    /**
     * Phát {@code ExperienceChangedEvent} cho chính người chơi đó. Mất kết nối giữa chừng thì EXP
     * ĐÃ vào DB thật rồi, chỉ mất thông báo — cùng cách xử lý của {@link #grantLootBlocking}.
     *
     * <p><b>Vạch EXP không bao giờ lùi</b> (sửa 2026-10-10). Mỗi lượt cộng EXP chạy trên một Virtual
     * Thread riêng ({@code lootExecutor}, và {@code authExecutor} cho thưởng quest): DB cộng đúng thứ
     * tự nhưng các luồng GỬI theo thứ tự bất kỳ. Gói về sau cùng mà mang tổng EXP CŨ hơn thì HUD
     * đứng sai ở số cũ tới lần nhận EXP kế tiếp. Tổng EXP chỉ tăng (EXP quái {@code >= 0}, thưởng
     * quest dương), nên gói có tổng nhỏ hơn gói đã gửi chắc chắn là gói đến muộn: bỏ nó — trừ khi
     * nó mang {@code leveledUp}, khi đó vẫn báo lên cấp nhưng kèm con số MỚI NHẤT. So-rồi-gửi nằm
     * trong {@code compute} để hai luồng không chen được vào giữa; {@code writeAndFlush} từ ngoài
     * EventLoop chỉ xếp hàng, không chặn.
     */
    void sendExperience(long characterId, GrantExperienceUseCase.Result result) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(characterId);
        if (ctx == null) {
            return;
        }
        lastSentExperience.compute(characterId, (id, lastSent) -> {
            boolean overtaken = lastSent != null && result.progress().totalExp() < lastSent.totalExp();
            if (overtaken && !result.leveledUp()) {
                return lastSent;
            }
            ExperienceProgress progress = overtaken ? lastSent : result.progress();
            ExperienceChangedEvent event = ExperienceChangedEvent.newBuilder()
                    .setLevel(progress.level())
                    .setTotalExp(progress.totalExp())
                    .setExpIntoLevel(progress.expIntoLevel())
                    .setExpForNextLevel(progress.expForNextLevel())
                    .setLeveledUp(result.leveledUp())
                    .build();
            send(ctx, Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE, 0, event.toByteArray());
            return progress;
        });
    }

    /**
     * Phát {@code GoldChangedEvent} cho chính người chơi đó (ADR-035 Decision-3). Bị gọi từ HAI
     * chỗ, và chỉ hai: {@code GatewayBootstrap} nối vào {@code GoldChangeListener} của
     * server-database (mọi lần vàng ĐỔI, sau khi commit), và {@code GatewayChannelHandler} lúc
     * nhân vật vừa vào world (đồng bộ số dư đầu tiên, {@code delta=0}).
     *
     * <p>Offline thì bỏ qua an toàn: vàng ĐÃ vào DB thật, chỉ mất thông báo — và vì {@code gold} là
     * số dư TUYỆT ĐỐI nên lần đăng nhập kế tự sửa lại, không tích luỹ sai lệch. Cùng cách xử lý của
     * {@link #sendExperience}.
     */
    void sendGoldChanged(long characterId, long gold, long delta, String reasonKey) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(characterId);
        if (ctx == null) {
            return;
        }
        GoldChangedEvent event = GoldChangedEvent.newBuilder()
                .setGold(gold)
                .setDelta(delta)
                .setReasonKey(reasonKey)
                .build();
        send(ctx, Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE, 0, event.toByteArray());
    }

    /**
     * Chỉ số trang bị hiện tại của một nhân vật (2026-09-18) — lớp này đã cầm sẵn
     * {@code InventoryFacade}, nên hai registry đấu trường/hầm ngục mượn được mà không phải
     * đổi constructor của chúng.
     *
     * <p>⚠️ Vì sao cần: trước đây hai đường đó attach nhân vật trở lại map chính bằng
     * {@code baseStats} và KHÔNG bao giờ cộng lại bonus — đấu xong hoặc rời hầm ngục là máu tối đa
     * 206 → 100, def 65 → 20, kéo dài tới khi người chơi mặc/gỡ một món đồ hoặc đăng nhập lại.
     *
     * <p>Chạm DB: chỉ gọi trên Virtual Thread (settlementExecutor / authExecutor), không bao giờ
     * trên EventLoop.
     */
    EquipmentBonusSnapshot equipmentBonusOf(long characterId) {
        return CharacterLoadout.snapshotOf(inventoryFacade.computeEquipmentBonus(characterId));
    }

    private void grantLootBlocking(long killerCharacterId, String monsterCode) {
        List<ItemStack> granted = inventoryFacade.grantLoot(killerCharacterId, monsterCode);
        ChannelHandlerContext ctx = contextsByCharacterId.get(killerCharacterId);
        if (ctx == null) {
            return; // đã ngắt kết nối giữa lúc roll loot — item đã lưu DB thật, chỉ mất thông báo.
        }
        for (ItemStack stack : granted) {
            // ID thật (GrantLootUseCase đã trả về từ Inventory đã lưu) — client cần để mặc đồ (EquipItemCommand).
            ItemGrantedEvent event = ItemGrantedEvent.newBuilder()
                    .setItemTemplateId(stack.itemTemplateId())
                    .setQuantity(stack.quantity())
                    .setItemId(stack.id())
                    .build();
            send(ctx, Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE, 0, event.toByteArray());
        }
    }

    private void processQuestBlocking(long killerCharacterId, String monsterCode) {
        // DANH SÁCH từ 2026-09-05: một con quái có thể là mục tiêu cuối của nhiều quest cùng lúc
        // kể từ khi có quest thứ hai theo dõi giết quái — phát đủ, đừng chỉ lấy cái đầu.
        List<QuestFacade.QuestStatusChange> completed = questFacade.onMonsterKilled(killerCharacterId, monsterCode);
        if (completed.isEmpty()) {
            return; // không có quest IN_PROGRESS liên quan, hoặc chưa hoàn thành — không báo gì.
        }
        ChannelHandlerContext ctx = contextsByCharacterId.get(killerCharacterId);
        if (ctx == null) {
            return; // đã ngắt kết nối — tiến độ đã lưu DB thật, chỉ mất thông báo.
        }
        for (QuestFacade.QuestStatusChange statusChange : completed) {
            QuestStatusChangedEvent event = QuestStatusChangedEvent.newBuilder()
                    .setQuestCode(statusChange.questCode())
                    .setStatus(toProtoStatus(statusChange.status()))
                    .build();
            send(ctx, Opcode.OPCODE_QUEST_STATUS_CHANGED_EVENT_VALUE, 0, event.toByteArray());
        }
    }

    private static final Map<dev.kage.quest.domain.QuestStatus, QuestStatus> QUEST_STATUS_MAPPING = Map.of(
            dev.kage.quest.domain.QuestStatus.IN_PROGRESS, QuestStatus.QUEST_STATUS_IN_PROGRESS,
            dev.kage.quest.domain.QuestStatus.COMPLETED, QuestStatus.QUEST_STATUS_COMPLETED,
            dev.kage.quest.domain.QuestStatus.CLAIMED, QuestStatus.QUEST_STATUS_CLAIMED);

    private static QuestStatus toProtoStatus(dev.kage.quest.domain.QuestStatus status) {
        return QUEST_STATUS_MAPPING.get(status);
    }

    /**
     * Dừng trao thưởng hạ quái (SERVER.md §10 graceful shutdown) — gọi sau khi world tick dừng, TRƯỚC
     * khi đóng DB; chờ lượt đang dở xong, xem {@link KillRewardDispatcher#stop()}.
     */
    void stop() {
        lootExecutor.stop();
    }

    // FutureReturnValueIgnored: ChannelFuture của writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
    private void send(ChannelHandlerContext ctx, int opcode, int sequenceId, byte[] payload) {
        networkMetrics.recordPacketSent(opcode, payload.length);
        PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        ctx.writeAndFlush(new PacketCodec.Frame(header, payload));
    }
}
