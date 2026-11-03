package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 29 — khoá vật phẩm (ADR-159), sắp xếp túi (ADR-160), ghép Linh Ngọc
 * (ADR-161). Gom thành MỘT thành phần của
 * {@link ExpansionWindows} để {@code create} ở đó không vượt trần {@code MethodLength}.
 */
record Lot29Windows(BagActionsCoordinator bagActions, GemCombineCoordinator gemCombine) {

    static Lot29Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot29Windows(
                new BagActionsCoordinator(sender, feeds.chat()), GemCombineCoordinator.create(skin, sender, feeds));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(bagActions);
        panels.register(gemCombine.panel(), gemCombine);
    }
}
