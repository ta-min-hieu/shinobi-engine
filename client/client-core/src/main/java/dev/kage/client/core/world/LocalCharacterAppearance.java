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
 * <p><b>Lớp này lo ĐÚNG máy này.</b> Người chơi khác được server báo bằng đường riêng —
 * {@code CharacterAppearanceChangedEvent} (opcode 307, ADR-045) tới thẳng
 * {@code RemoteCharacterPresenter.onAppearanceChanged}. Chính vì đường đó tồn tại mà server KHÔNG
 * gửi event cho người vừa đổi: model của họ đổi ở đây, ngay khi opcode 207 xác nhận. Xem thêm
 * {@link FactionController}.
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
     * <p>Model thay TẠI CHỖ ({@code WorldModelRenderer.replaceCharacterModel}, ADR-045) để giữ
     * hướng mặt; chỉ khi chưa có model nào để thay mới gắn mới.
     *
     * <p>Bậc trang phục giữ nguyên {@code UNSPECIFIED} như lúc vào world: client vẫn chưa suy được
     * bậc thật từ đâu (xem {@code CharacterAppearance} phía server), và dùng đúng giá trị server
     * đang phát cho người khác là cách để mình và người khác nhìn thấy mình giống nhau.
     */
    void applyFaction(Faction faction) {
        // Nhãn nút chiêu KHÔNG cần đặt lại tay: WorldScreen#syncCombatHud đẩy skillNameKey() xuống
        // HUD mỗi khung, nên nó tự đổi ngay khung kế tiếp.
        hudController.onFactionChanged(faction);
        String modelKey = CharacterModelKey.of(faction, CostumeTier.COSTUME_TIER_UNSPECIFIED);
        if (!models.replaceCharacterModel(modelId, modelKey)) {
            models.addCharacter(modelId, modelKey);
        }
    }
}
