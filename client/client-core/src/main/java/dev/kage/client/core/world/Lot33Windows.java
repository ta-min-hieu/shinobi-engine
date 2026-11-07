package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 33 — nhường trưởng nhóm (ADR-191), đặt tên linh thú (ADR-193)… Là MỘT thành phần của {@link Lot32Windows}
 * (chuỗi lồng nhau vì {@code ExpansionWindows.create} đã chạm trần {@code MethodLength}).
 */
record Lot33Windows(PartyLeaderCoordinator partyLeader, PetNameCoordinator petName) {

    @SuppressWarnings("unused") // skin/feeds dành cho các cửa sổ lô 33 tiếp theo
    static Lot33Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot33Windows(
                new PartyLeaderCoordinator(sender, feeds.chat()), new PetNameCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(partyLeader);
        panels.register(petName);
    }
}
