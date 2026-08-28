package dev.kage.client.core.world;

import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.Faction;

/**
 * Hai thứ trên màn hình phụ thuộc HỆ PHÁI của chính người chơi, gom về một chỗ (2026-08-27) — model
 * glTF ({@link CharacterModelKey}, ADR-031) và nút chiêu trên HUD
 * ({@link CombatConstants#FACTION_SKILLS}).
 *
 * <p>Tách khỏi {@code WorldScreen} vì lớp đó đang ở ĐÚNG trần {@code CyclomaticComplexity} của PMD,
 * và vì đây là một mối quan tâm trọn vẹn: "nhân vật của mình trông thế nào, bấm được chiêu gì".
 * {@link FactionCoordinator} nhận thẳng lớp này thay vì một {@code Consumer<Faction>} vô danh —
 * hệ quả của một gói tin nên nằm cạnh chỗ đọc gói tin đó.
 *
 * <p><b>⚠️ Chỉ đổi được trên MÁY NÀY.</b> Giao thức không có gói nào báo hệ phái mới cho người khác:
 * {@code CharacterSpawnedEvent} có mang {@code faction} nhưng chỉ phát lúc một nhân vật vào tầm
 * nhìn, nên người chơi đang đứng cạnh vẫn thấy model hệ CŨ cho tới khi mình ra khỏi tầm nhìn của họ
 * rồi quay lại. Đóng lỗ đó cần một event đẩy phía server, tức cần sửa {@code .proto} — ngoài quyền
 * của client (/CLAUDE.md §4). Xem thêm {@link FactionController}.
 *
 * @param models nơi giữ {@code Scene} glTF của mọi thực thể.
 * @param hudController chỗ giữ skill slot 0 đang gắn với nút chiêu.
 * @param modelId id {@code models} dùng cho nhân vật của CHÍNH người chơi
 *     ({@code WorldScreen.LOCAL_CHARACTER_ID} — sentinel nội bộ, không phải id thật từ DB).
 */
record LocalCharacterAppearance(WorldModelRenderer models, CombatHudController hudController, long modelId) {

    /**
     * Áp hệ phái mà server vừa XÁC NHẬN (opcode 207, khớp {@code sequenceId} — xem
     * {@link FactionCoordinator}). Không có đường nào khác gọi được: client không tự phong hệ phái
     * cho mình (Điều 15).
     *
     * <p>Model phải GỠ hẳn rồi nạp lại — {@code WorldModelRenderer} gắn đúng một {@code Scene} cố
     * định cho mỗi id, không có đường thay tại chỗ.
     *
     * <p>Bậc trang phục giữ nguyên {@code UNSPECIFIED} như lúc vào world: client vẫn chưa suy được
     * bậc thật từ đâu (xem {@code CharacterAppearance} phía server), và dùng đúng giá trị server
     * đang phát cho người khác là cách để mình và người khác nhìn thấy mình giống nhau.
     */
    void applyFaction(Faction faction) {
        // Nhãn nút chiêu KHÔNG cần đặt lại tay: WorldScreen#syncCombatHud đẩy skillNameKey() xuống
        // HUD mỗi khung, nên nó tự đổi ngay khung kế tiếp.
        hudController.onFactionChanged(faction);
        models.remove(modelId);
        models.addCharacter(modelId, CharacterModelKey.of(faction, CostumeTier.COSTUME_TIER_UNSPECIFIED));
    }
}
