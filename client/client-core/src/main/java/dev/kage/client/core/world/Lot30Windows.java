package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 30 — nhận hết thư (ADR-167), tháo Linh Ngọc (ADR-168), sổ vàng (ADR-169), bán rác
 * (ADR-170), soi trang bị (ADR-171), đổi tên bang (ADR-172), ghi chú bạn bè (ADR-173),
 * nhắc nghỉ ngơi (ADR-174). Gom thành MỘT thành phần của
 * {@link ExpansionWindows} để {@code create} ở đó không vượt trần {@code MethodLength}.
 */
record Lot30Windows(
        MailBulkCoordinator mailBulk,
        GemRemovalCoordinator gemRemoval,
        GoldHistoryCoordinator goldHistory,
        JunkSaleCoordinator junkSale,
        InspectCoordinator inspect,
        GuildRenameCoordinator guildRename,
        FriendNoteCoordinator friendNote,
        RestReminder restReminder) {

    static Lot30Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot30Windows(
                new MailBulkCoordinator(sender, feeds.chat()),
                new GemRemovalCoordinator(sender, feeds),
                GoldHistoryCoordinator.create(skin, sender, feeds),
                JunkSaleCoordinator.create(skin, sender, feeds),
                InspectCoordinator.create(skin, sender, feeds),
                new GuildRenameCoordinator(sender, feeds.chat()),
                new FriendNoteCoordinator(sender, feeds.chat()),
                new RestReminder(
                        hours -> KageI18n.formatOrKey(KageI18n.load(), "rest_reminder.notice", hours),
                        feeds.chat()::showSystemNotice));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(mailBulk);
        panels.register(gemRemoval);
        panels.register(goldHistory.panel(), goldHistory);
        panels.register(junkSale.panel(), junkSale);
        panels.register(inspect.panel(), inspect);
        panels.register(guildRename);
        panels.register(friendNote);
    }
}
