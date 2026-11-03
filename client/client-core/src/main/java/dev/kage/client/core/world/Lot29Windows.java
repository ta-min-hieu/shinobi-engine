package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 29 — khoá vật phẩm (ADR-159), sắp xếp túi (ADR-160), ghép Linh Ngọc
 * (ADR-161), quét nhanh Bí Cảnh (ADR-162). Gom thành MỘT thành phần của
 * {@link ExpansionWindows} để {@code create} ở đó không vượt trần {@code MethodLength}.
 */
record Lot29Windows(
        BagActionsCoordinator bagActions, GemCombineCoordinator gemCombine, DungeonSweepCoordinator dungeonSweep) {

    static Lot29Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot29Windows(
                new BagActionsCoordinator(sender, feeds.chat()),
                GemCombineCoordinator.create(skin, sender, feeds),
                new DungeonSweepCoordinator(sender, feeds));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(bagActions);
        panels.register(gemCombine.panel(), gemCombine);
        panels.register(dungeonSweep);
    }
}
