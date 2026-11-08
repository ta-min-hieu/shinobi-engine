package dev.kage.client.core.world;

/**
 * Luồng lô 36 (ADR-215…) — tách chồng (ADR-215), dọn hộp thư (ADR-216/217), hồ sơ cá nhân (ADR-218)… Là MỘT thành
 * phần của {@link Lot35Windows} (chuỗi lồng nhau vì {@code ExpansionWindows.create} đã chạm trần {@code MethodLength}).
 */
record Lot36Windows(StackSplitCoordinator stackSplit, MailCleanupCoordinator mailCleanup, BioCoordinator bio) {

    static Lot36Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot36Windows(
                new StackSplitCoordinator(sender, feeds.chat()),
                new MailCleanupCoordinator(sender, feeds.chat()),
                new BioCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(stackSplit);
        panels.register(mailCleanup);
        panels.register(bio);
    }
}
