package dev.kage.server.world;

/**
 * Port đầu ra của server-world — thực thi bởi {@code server-gateway} (Port/Adapter, giống
 * {@code CharacterPort}/{@code AccountPort} ở feature-*). server-world KHÔNG biết Netty
 * (server/CLAUDE.md), nên mọi kết quả tick (SERVER.md §2: "Response/Event vào outbound queue")
 * đi qua interface này thay vì gọi thẳng {@code ChannelHandlerContext}.
 *
 * <p>Mọi phương thức được gọi TRÊN world tick thread (SERVER.md §3) — implementation phía
 * gateway chỉ được tra cứu channel theo {@code characterId} rồi {@code ctx.writeAndFlush(...)}
 * (an toàn gọi từ thread khác EventLoop, Netty tự chuyển task), KHÔNG được block.
 */
public interface WorldOutboundSink {

    /**
     * Server đã xử lý xong 1 {@code MoveCommand} HOẶC {@code JumpCommand} (ADR-033) — trả
     * {@code MoveResponse} về ĐÚNG connection đã gửi lệnh, khớp {@code sequenceId} để client
     * reconciliation (CLIENT.md §6).
     */
    void onMoveAccepted(MoveAcceptedSnapshot accepted);

    /** Nhân vật {@code spawnedCharacterId} VỪA vào AoI của {@code targetCharacterId}. */
    void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y);

    /** Nhân vật {@code despawnedCharacterId} VỪA rời AoI của {@code targetCharacterId}. */
    void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId);

    /** Nhân vật {@code movedCharacterId} (đã ở trong AoI từ trước) vừa đổi vị trí. */
    void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y);

    /** Quái {@code monsterId} VỪA vào AoI của {@code targetCharacterId} (spawn lần đầu hoặc hồi sinh). */
    void onMonsterSpawned(long targetCharacterId, MonsterSnapshot monster);

    /** Quái {@code monsterId} VỪA rời AoI của {@code targetCharacterId} (chết hoặc ra xa). */
    void onMonsterDespawned(long targetCharacterId, long monsterId);

    /** Quái {@code monsterId} (đã ở trong AoI từ trước) vừa đổi vị trí (AI.md §3 — Patrol/Chase/Return). */
    void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y);

    /**
     * @param expReward EXP thưởng ({@code monster_template.exp_reward}, ADR-037 D4) — server-world
     *     chỉ CHỞ con số này ra, không dùng tới nó, đúng như nó chở {@code monsterCode}
     *
     * Quái {@code monsterCode} VỪA bị {@code killerCharacterId} hạ gục — server-world không biết
     * DB/inventory (server/CLAUDE.md), nên chỉ báo SỰ KIỆN; việc roll rơi đồ + cộng vào túi thật
     * xảy ra ở gateway (nơi có {@code InventoryFacade}, GAMEPLAY.md §5).
     */
    void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward);

    /** NPC {@code npc.npcId()} VỪA vào AoI của {@code targetCharacterId} (Phase 6 §9 — NPC tĩnh). */
    void onNpcSpawned(long targetCharacterId, NpcSnapshot npc);

    /** NPC {@code npcId} VỪA rời AoI của {@code targetCharacterId} (đi xa — NPC không "chết"). */
    void onNpcDespawned(long targetCharacterId, long npcId);

    /**
     * Kết quả 1 đòn đánh thường THẬT (đã né/bạo kích/giảm giáp — GDD §6) — gửi cho MỌI nhân vật
     * đang thấy mục tiêu trong AoI, không chỉ người tấn công.
     */
    void onAttackResult(long targetCharacterId, AttackOutcome outcome);

    /**
     * Kết quả 1 lần dùng skill THẬT (đã né/bạo kích/kháng phép — GAMEPLAY.md §3-4) — gửi cho MỌI
     * nhân vật đang thấy mục tiêu trong AoI, không chỉ người dùng skill.
     */
    void onSkillResult(long targetCharacterId, SkillOutcome outcome);

    /**
     * Tin nhắn chat kênh Khu vực (GAMEPLAY.md §10) — gửi cho {@code targetCharacterId} (1 quan sát
     * viên AoI của {@code senderCharacterId}, gồm cả chính người gửi). Nội dung tin (đã validate ở
     * {@code feature-chat}) đi kèm nguyên văn — server-world không biết {@code ChatFacade}.
     */
    void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body);

    /**
     * Ngoại hình của {@code changedCharacterId} vừa đổi (ADR-045) — gửi cho {@code targetCharacterId}
     * (1 quan sát viên AoI của nhân vật đó, GỒM cả chính họ). Không có tham số ngoại hình: chỉ
     * implementation ở tầng gateway mới biết hệ phái/bậc trang phục hiện tại, đúng như
     * {@link #onAreaChatMessage} không nhận {@code ChatFacade}.
     */
    void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId);

    /**
     * {@code defeatedCharacterId} (nhân vật, luôn DƯƠNG) vừa bị {@code killerEntityId} hạ gục.
     * World CHÍNH dùng sự kiện này để hồi sinh nhân vật (xem {@link #onCharacterRespawned}) —
     * {@code ConnectedCharacterRegistry} không còn no-op nữa. Arena instance (Phase 8 lát cắt 4,
     * {@code ArenaOutboundSink}) override để chốt kết quả trận đấu thay vì hồi sinh tại chỗ.
     * server-world không biết khái niệm "Arena" — chỉ báo SỰ KIỆN, giống {@link #onMonsterKilled}
     * không biết inventory.
     */
    void onCharacterDefeated(long defeatedCharacterId, long killerEntityId);

    /**
     * {@code respawn.characterId()} VỪA hồi sinh với HP/Chi đầy — chỉ world CHÍNH gọi (Arena/
     * Dungeon có luồng kết thúc trận riêng, không hồi sinh tại chỗ). Không có tham số "ai gửi cho
     * ai" như các {@code onXxx} khác vì luôn gửi cho ĐÚNG connection của {@code characterId} đó —
     * cùng quy ước "ngầm định chính mình" như {@link #onMoveAccepted}.
     */
    void onCharacterRespawned(RespawnSnapshot respawn);

    /**
     * Ảnh chụp vị trí sau khi xử lý {@code MoveCommand}/{@code JumpCommand} (ADR-033) — gộp tham số
     * (STATIC-ANALYSIS.md, checkstyle {@code ParameterNumberCheck} max=4). {@code z}/{@code
     * grounded} thêm ở ADR-033.
     */
    record MoveAcceptedSnapshot(long characterId, int sequenceId, float x, float y, float z, boolean grounded) {}

    /** Ảnh chụp trạng thái quái lúc phát {@link #onMonsterSpawned} — gộp tham số (STATIC-ANALYSIS.md). */
    record MonsterSnapshot(
            long monsterId, String monsterCode, String modelKey, float x, float y, int currentHp, int maxHp) {}

    /** Ảnh chụp trạng thái nhân vật lúc phát {@link #onCharacterRespawned} — gộp tham số (STATIC-ANALYSIS.md). */
    record RespawnSnapshot(long characterId, float x, float y, int currentHp, int maxHp, int currentChi, int maxChi) {}

    /** Ảnh chụp NPC lúc phát {@link #onNpcSpawned} — không có HP (NPC không combat, khác quái). */
    record NpcSnapshot(long npcId, String npcCode, float x, float y) {}

    /** Kết quả 1 đòn đánh — gộp tham số cho {@link #onAttackResult} (STATIC-ANALYSIS.md). */
    record AttackOutcome(
            long attackerEntityId,
            long targetEntityId,
            int damage,
            boolean crit,
            boolean miss,
            int targetCurrentHp,
            boolean targetDied) {}

    /**
     * Kết quả 1 lần dùng skill — gộp tham số cho {@link #onSkillResult} (STATIC-ANALYSIS.md).
     * {@code damage} là "độ lớn" dùng chung mọi {@code effectKind} (lượng heal/khiên khi không phải
     * {@link SkillEffectKind#DAMAGE} — xem {@code combat.proto SkillResultEvent}).
     */
    record SkillOutcome(
            String skillCode,
            long attackerEntityId,
            long targetEntityId,
            int damage,
            boolean crit,
            boolean miss,
            int targetCurrentHp,
            boolean targetDied,
            SkillEffectKind effectKind) {}
}
