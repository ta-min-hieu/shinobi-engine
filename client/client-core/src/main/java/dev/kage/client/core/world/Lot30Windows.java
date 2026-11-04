package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 30 — nhận hết thư (ADR-167). Gom thành MỘT thành phần của {@link ExpansionWindows} để {@code create} ở đó
 * không vượt trần {@code MethodLength}.
 */
record Lot30Windows(MailBulkCoordinator mailBulk) {

    static Lot30Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot30Windows(new MailBulkCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(mailBulk);
    }
}
