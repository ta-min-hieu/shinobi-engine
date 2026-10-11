package dev.kage.server.database;

/**
 * Một lượt cộng vàng sẽ đẩy số dư vượt {@link GoldLedger#MAX_GOLD} (quyết định chủ dự án 2026-10-11).
 *
 * <p>Ném từ {@link GoldLedger#credit} giữa transaction ⇒ transaction đó rollback TRỌN: món hàng không
 * bị bán, thư không bị lĩnh, giao dịch không thi hành. Gateway đổi nó thành
 * {@code error.gold_cap_reached} thay cho {@code error.server_internal} mà tràn {@code BIGINT} trước
 * đó gây ra.
 *
 * <p>Không mang tên hay số dư của ai: bên vượt trần có thể là NGƯỜI KIA trong một giao dịch.
 */
public final class GoldCapExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GoldCapExceededException(long characterId) {
        super("Cộng vàng sẽ vượt trần " + GoldLedger.MAX_GOLD + ": characterId=" + characterId);
    }
}
