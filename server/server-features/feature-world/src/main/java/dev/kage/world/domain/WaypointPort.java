package dev.kage.world.domain;

import java.util.List;
import java.util.Set;

/** Điểm truyền tống (ADR-100) — hiện thực ở {@code server-database}. */
public interface WaypointPort {

    /** Mọi điểm, theo thứ tự hiển thị. */
    List<Waypoint> listWaypoints();

    /** Mã các điểm {@code characterId} đã mở (không kể điểm mở sẵn). */
    Set<String> unlockedBy(long characterId);

    /** Ghi mở; {@code true} nếu lần này mới mở. */
    boolean unlock(long characterId, String waypointCode);

    /**
     * Trừ một Truyền Tống Phù dưới khoá túi đồ chung; {@code false} nếu không có.
     *
     * @param talismanCode mã vật phẩm phù
     */
    boolean consumeTalisman(long characterId, String talismanCode);

    /** Trả lại một phù (dời hỏng sau khi đã trừ). */
    void refundTalisman(long characterId, String talismanCode);
}
