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
            implements WorldCommand {
    }

    /** Xoá entity ECS của {@code characterId} — gọi khi mất kết nối. */
    record DetachCharacterCommand(long characterId) implements WorldCommand {
    }

    /** Ý định di chuyển — {@code dirX/dirY} là hướng CHƯA chuẩn hoá, world tự clamp + nhân tốc độ. */
    record MoveIntentCommand(long characterId, int sequenceId, float dirX, float dirY) implements WorldCommand {
    }

    /**
     * Chọn mục tiêu chiến đấu (GDD §6 "Auto-target đánh thường") — server tự động đánh khi hết
     * cooldown, KHÔNG phải lệnh "đánh 1 nhát" (xem {@code CombatSystem}).
     */
    record SetTargetCommand(long characterId, long targetEntityId) implements WorldCommand {
    }

    /**
     * Dùng skill nhắm mục tiêu (GAMEPLAY.md §3-4) — {@code skillCode} do client gửi, server tự
     * kiểm tra có tồn tại/cooldown/Khí/tầm (xem {@code SkillSystem}), KHÔNG tin client.
     */
    record UseSkillCommand(long characterId, String skillCode, long targetEntityId) implements WorldCommand {
    }

    /**
     * Tính lại chỉ số dẫn xuất theo trang bị (GAMEPLAY.md §2/§7) — {@code bonusAtk} là TỔNG cộng
     * dồn hiện tại từ mọi item đang mặc, gateway (feature-inventory) tự tính lại từ đầu mỗi lần.
     */
    record ApplyEquipmentBonusCommand(long characterId, int bonusAtk) implements WorldCommand {
    }
}
