package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 31 — lưu bộ trang bị (ADR-175). Gom thành MỘT thành phần của {@link ExpansionWindows} để {@code create} ở
 * đó không vượt trần {@code MethodLength}.
 */
record Lot31Windows(EquipmentPresetCoordinator equipmentPresets) {

    // skin chưa dùng (lô 31 hiện chỉ có lệnh chat) — giữ chữ ký giống các lô trước để thêm cửa sổ không đổi chỗ gọi.
    static Lot31Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot31Windows(new EquipmentPresetCoordinator(sender, feeds));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(equipmentPresets);
    }
}
