package dev.kage.server.gateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.channel.ChannelHandlerContext;

import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.ExperienceProgress;
import dev.kage.character.domain.MonsterExperience;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.EffectKind;
import dev.kage.protocol.ExperienceChangedEvent;
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
    // onMonsterKilled bị gọi TRÊN world tick thread (WorldOutboundSink javadoc) — grantLoot chạm
    // DB (blocking), CẤM chạy trực tiếp ở đó (server/CLAUDE.md: Game Tick cấm blocking I/O). Đẩy
    // sang Virtual Thread riêng, cùng pattern authExecutor của GatewayChannelHandler.
    private final ExecutorService lootExecutor = Executors.newVirtualThreadPerTaskExecutor();

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
    }

    void unregister(long characterId) {
        contextsByCharacterId.remove(characterId);
        lastKnownPosition.remove(characterId);
        appearanceByCharacterId.remove(characterId);
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

    /** Vị trí đã biết của MỘT nhân vật, hoặc {@code null} nếu chưa từng di chuyển kể từ khi vào world. */
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
        lastKnownPosition.put(accepted.characterId(), new PositionSnapshot(accepted.x(), accepted.y()));
        sendMoveResponseWithoutTrackingPosition(accepted);
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
        lastKnownPosition.put(respawn.characterId(), new PositionSnapshot(respawn.x(), respawn.y()));
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
    public void onMonsterKilled(long killerCharacterId, String monsterCode) {
        lootExecutor.execute(() -> grantLootBlocking(killerCharacterId, monsterCode));
        // Cạnh grantLoot, KHÔNG qua EventBus (engine-ecs) — 1 nguồn duy nhất chưa tới ngưỡng đáng
        // kích hoạt pub-sub thật (feature-quest/CLAUDE.md). Virtual Thread RIÊNG cho quest, không
        // chặn nhau với lootExecutor nếu 1 trong 2 chạm DB chậm.
        lootExecutor.execute(() -> processQuestBlocking(killerCharacterId, monsterCode));
        // Cạnh loot/quest, cùng lý do tách Virtual Thread riêng: cộng EXP chạm DB. Thêm 2026-08-09
        // khi chủ dự án hỏi vạch kinh nghiệm — trước đó giết quái KHÔNG cho EXP gì cả.
        lootExecutor.execute(() -> grantExperienceBlocking(killerCharacterId, monsterCode));
    }

    /**
     * Cộng EXP cho người hạ gục rồi báo vạch mới. Quái lạ (chưa khai báo EXP) cho 0 — bỏ qua sớm,
     * không đụng DB chỉ để cộng số không.
     */
    private void grantExperienceBlocking(long killerCharacterId, String monsterCode) {
        long reward = MonsterExperience.rewardFor(monsterCode);
        if (reward <= 0L) {
            return;
        }
        sendExperience(killerCharacterId, experienceGrant.grantExperience(killerCharacterId, reward));
    }

    /**
     * Phát {@code ExperienceChangedEvent} cho chính người chơi đó. Mất kết nối giữa chừng thì EXP
     * ĐÃ vào DB thật rồi, chỉ mất thông báo — cùng cách xử lý của {@link #grantLootBlocking}.
     */
    void sendExperience(long characterId, GrantExperienceUseCase.Result result) {
        ChannelHandlerContext ctx = contextsByCharacterId.get(characterId);
        if (ctx == null) {
            return;
        }
        ExperienceProgress progress = result.progress();
        ExperienceChangedEvent event = ExperienceChangedEvent.newBuilder()
                .setLevel(progress.level())
                .setTotalExp(progress.totalExp())
                .setExpIntoLevel(progress.expIntoLevel())
                .setExpForNextLevel(progress.expForNextLevel())
                .setLeveledUp(result.leveledUp())
                .build();
        send(ctx, Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE, 0, event.toByteArray());
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
        Optional<QuestFacade.QuestStatusChange> statusChange =
                questFacade.onMonsterKilled(killerCharacterId, monsterCode);
        if (statusChange.isEmpty()) {
            return; // không có quest IN_PROGRESS liên quan, hoặc chưa hoàn thành — không báo gì.
        }
        ChannelHandlerContext ctx = contextsByCharacterId.get(killerCharacterId);
        if (ctx == null) {
            return; // đã ngắt kết nối — tiến độ đã lưu DB thật, chỉ mất thông báo.
        }
        QuestStatusChangedEvent event = QuestStatusChangedEvent.newBuilder()
                .setQuestCode(statusChange.get().questCode())
                .setStatus(toProtoStatus(statusChange.get().status()))
                .build();
        send(ctx, Opcode.OPCODE_QUEST_STATUS_CHANGED_EVENT_VALUE, 0, event.toByteArray());
    }

    private static final Map<dev.kage.quest.domain.QuestStatus, QuestStatus> QUEST_STATUS_MAPPING = Map.of(
            dev.kage.quest.domain.QuestStatus.IN_PROGRESS, QuestStatus.QUEST_STATUS_IN_PROGRESS,
            dev.kage.quest.domain.QuestStatus.COMPLETED, QuestStatus.QUEST_STATUS_COMPLETED,
            dev.kage.quest.domain.QuestStatus.CLAIMED, QuestStatus.QUEST_STATUS_CLAIMED);

    private static QuestStatus toProtoStatus(dev.kage.quest.domain.QuestStatus status) {
        return QUEST_STATUS_MAPPING.get(status);
    }

    /** Dừng {@link #lootExecutor} (SERVER.md §10 graceful shutdown) — gọi sau khi world tick dừng. */
    void stop() {
        lootExecutor.shutdown();
    }

    private void send(ChannelHandlerContext ctx, int opcode, int sequenceId, byte[] payload) {
        networkMetrics.recordPacketSent(opcode, payload.length);
        PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        ctx.writeAndFlush(new PacketCodec.Frame(header, payload));
    }
}
