package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 31 — lưu bộ trang bị (ADR-175), dùng nhiều vật phẩm (ADR-176). Gom thành MỘT thành phần của {@link
 * ExpansionWindows} để {@code create} ở đó không vượt trần {@code MethodLength}.
 */
record Lot31Windows(EquipmentPresetCoordinator equipmentPresets, BulkUseCoordinator bulkUse) {

    // skin chưa dùng (lô 31 hiện chỉ có lệnh chat) — giữ chữ ký giống các lô trước để thêm cửa sổ không đổi chỗ gọi.
    static Lot31Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot31Windows(
                new EquipmentPresetCoordinator(sender, feeds), new BulkUseCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(equipmentPresets);
        panels.register(bulkUse);
    }
}
