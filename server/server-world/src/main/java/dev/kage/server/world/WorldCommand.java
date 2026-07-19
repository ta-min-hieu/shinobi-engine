package dev.kage.server.world;

/**
 * Command nội bộ trong hàng đợi world (SERVER.md §2-3) — KHÔNG phải {@code Command} của
 * {@code shared-protocol}/NETWORK.md §3 (đó là packet trên dây; đây là việc cần làm ở tick kế
 * tiếp, gateway enqueue rồi trả về ngay, không chờ). Sealed vì tập việc world phải làm đã đóng;
 * lồng record trong interface (thay vì file riêng) để tránh nhiều top-level class/file.
 */
sealed interface WorldCommand {

    /** Gán entity ECS mới cho {@code characterId} — gọi sau khi SelectCharacter thành công. */
    record AttachCharacterCommand(long characterId) implements WorldCommand {
    }

    /** Xoá entity ECS của {@code characterId} — gọi khi mất kết nối. */
    record DetachCharacterCommand(long characterId) implements WorldCommand {
    }

    /** Ý định di chuyển — {@code dirX/dirY} là hướng CHƯA chuẩn hoá, world tự clamp + nhân tốc độ. */
    record MoveIntentCommand(long characterId, int sequenceId, float dirX, float dirY) implements WorldCommand {
    }
}
