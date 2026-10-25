package dev.kage.server.world;

/**
 * Luật "nhân vật A được gây hại cho nhân vật B không" của một World (ADR-102 D2) — thay cho cờ boolean cả World: đấu
 * trường cho mọi cặp, world chính chỉ cho cặp thuộc hai bang đang chiến và cả hai đứng ngoài vùng an toàn.
 *
 * <p>Gọi trên tick thread, mỗi tick mỗi người đang nhắm một người chơi khác — hiện thực phải chỉ ĐỌC RAM, không chạm
 * DB, không cấp phát.
 */
@FunctionalInterface
public interface PlayerVersusPlayerRule {

    /** Không cặp nào — mặc định của world chính khi chưa có bang chiến nào được nối vào. */
    PlayerVersusPlayerRule NOBODY = (attackerCharacterId, targetCharacterId) -> false;

    /** {@code attackerCharacterId} được đánh/dùng chiêu ENEMY lên {@code targetCharacterId}. */
    boolean allows(long attackerCharacterId, long targetCharacterId);
}
