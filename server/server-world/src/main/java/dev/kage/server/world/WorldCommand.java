package dev.kage.server.world;

/**
 * Command nội bộ trong hàng đợi world (SERVER.md §2-3) — KHÔNG phải {@code Command} của
 * {@code shared-protocol}/NETWORK.md §3 (đó là packet trên dây; đây là việc cần làm ở tick kế
 * tiếp, gateway enqueue rồi trả về ngay, không chờ). Sealed vì tập việc world phải làm đã đóng;
 * lồng record trong interface (thay vì file riêng) để tránh nhiều top-level class/file.
 */
sealed interface WorldCommand {

    /**
     * Gán entity ECS mới cho {@code characterId} — gọi sau khi SelectCharacter thành công.
     * {@code spawnX/spawnY} là vị trí đã LƯU lần cuối (feature-character.PositionSnapshot,
     * SERVER.md §8) — KHÔNG luôn là (0,0), chỉ nhân vật chưa từng lưu vị trí mới ở đó.
     * {@code baseStats} là STR/AGI/INT/VIT thật từ {@code character_stat} DB (gateway đọc qua
     * {@code CharacterFacade.loadStats}) — world derive thành ATK/DEF/... (GDD §5).
     */
    record AttachCharacterCommand(long characterId, float spawnX, float spawnY, CharacterBaseStats baseStats)
            implements WorldCommand {}

    /** Xoá entity ECS của {@code characterId} — gọi khi mất kết nối. */
    record DetachCharacterCommand(long characterId) implements WorldCommand {}

    /** Ý định di chuyển — {@code dirX/dirY} là hướng CHƯA chuẩn hoá, world tự clamp + nhân tốc độ. */
    record MoveIntentCommand(long characterId, int sequenceId, float dirX, float dirY) implements WorldCommand {}

    /**
     * Ý định nhảy (ADR-033) — không mang theo lực/độ cao, world tự áp {@code JUMP_VELOCITY_MPS}
     * NẾU đang grounded (bỏ qua an toàn nếu không, xem {@code WorldFacade.handleJumpIntent}).
     */
    record JumpIntentCommand(long characterId, int sequenceId) implements WorldCommand {}

    /**
     * Chọn mục tiêu chiến đấu — chỉ ghi nhận NHẮM AI, KHÔNG tự ra đòn (đổi 2026-08-09, xem
     * {@code CombatSystem}). Muốn đánh phải gửi tiếp {@link AttackIntentCommand}.
     */
    record SetTargetCommand(long characterId, long targetEntityId) implements WorldCommand {}

    /**
     * Ý định ra MỘT đòn đánh thường lên mục tiêu đang nhắm (2026-08-09) — không mang mục tiêu theo
     * gói (world tự đọc {@code TargetComponent}), cùng lý do {@link JumpIntentCommand} rỗng.
     */
    record AttackIntentCommand(long characterId) implements WorldCommand {}

    /**
     * Dùng skill nhắm mục tiêu (GAMEPLAY.md §3-4) — {@code skillCode} do client gửi, server tự
     * kiểm tra có tồn tại/cooldown/Khí/tầm (xem {@code SkillSystem}), KHÔNG tin client.
     */
    record UseSkillCommand(long characterId, String skillCode, long targetEntityId) implements WorldCommand {}

    /**
     * Tính lại chỉ số dẫn xuất theo trang bị (GAMEPLAY.md §2/§7) — cả 4 chỉ số là TỔNG cộng dồn
     * hiện tại từ mọi item đang mặc (ĐÃ tính hệ số cường hoá từng món, ADR-036 D2/D3), gateway
     * (feature-inventory) tự tính lại từ đầu mỗi lần. {@code bonusHp} cộng vào MÁU TỐI ĐA.
     */
    record ApplyEquipmentBonusCommand(long characterId, EquipmentBonusSnapshot bonus) implements WorldCommand {}

    /**
     * Chat kênh Khu vực (GAMEPLAY.md §10, Phase 6 lát cắt 6) — CHỈ kênh này cần world tick (đọc
     * {@code AreaOfInterestTracker}, không thread-safe ngoài tick thread); World/Riêng tư gateway
     * tự định tuyến thẳng qua {@code ConnectedCharacterRegistry}, không đi qua hàng đợi này.
     */
    record AreaChatMessageCommand(long characterId, String senderName, String body) implements WorldCommand {}

    /**
     * Spawn 1 boss hardcode ({@code BossConstants.BOSS_CODE}, Phase 8 lát cắt 2) — gọi từ
     * {@code DungeonInstanceRegistry} ngay sau khi tạo dungeon World, KHÔNG phải lệnh từ client
     * (world-command nội bộ, giống mọi command khác ở đây).
     */
    record SpawnBossCommand() implements WorldCommand {}

    /**
     * Ngoại hình của {@code characterId} vừa đổi (đổi hệ phái — ADR-045). Command này CHỈ mang id:
     * server-world không biết "hệ phái"/"bậc trang phục" là gì và không được biết (ngoại hình sống
     * ở {@code ConnectedCharacterRegistry}, xem javadoc {@code appearanceByCharacterId}). Việc duy
     * nhất của world là trả lời "ai đang nhìn thấy nhân vật này" — thứ chỉ đọc được trên tick
     * thread, cùng lý do {@link AreaChatMessageCommand} tồn tại.
     */
    record AppearanceChangedCommand(long characterId) implements WorldCommand {}
}
