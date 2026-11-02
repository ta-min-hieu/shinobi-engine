package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 27 — Tàng Bảo Đồ (ADR-147), Dịch Danh Phù (ADR-148), quẻ bói (ADR-149), xúc xắc chia đồ (ADR-150).
 * Gom thành MỘT thành phần của {@link ExpansionWindows} để {@code create} ở đó không vượt trần {@code MethodLength}.
 */
record Lot27Windows(
        TreasureCoordinator treasure,
        RenameCoordinator rename,
        FortuneCoordinator fortune,
        LootRollCoordinator lootRoll) {

    static Lot27Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot27Windows(
                TreasureCoordinator.create(skin, sender, feeds),
                new RenameCoordinator(sender, feeds.chat()),
                new FortuneCoordinator(sender, feeds),
                LootRollCoordinator.create(skin, sender, feeds));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(treasure.panel(), treasure);
        panels.register(rename);
        panels.register(fortune);
        panels.register(lootRoll.panel(), lootRoll);
    }
}
