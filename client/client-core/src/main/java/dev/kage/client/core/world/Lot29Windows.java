package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 29 — khoá vật phẩm (ADR-159), sắp xếp túi (ADR-160). Gom thành MỘT thành phần của
 * {@link ExpansionWindows} để {@code create} ở đó không vượt trần {@code MethodLength}.
 */
record Lot29Windows(BagActionsCoordinator bagActions) {

    static Lot29Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot29Windows(new BagActionsCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(bagActions);
    }
}
