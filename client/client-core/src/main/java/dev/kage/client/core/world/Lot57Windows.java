package dev.kage.client.core.world;

/**
 * Luồng lô 57 (ADR-383…) — chế độ ẩn / thích tuần / cấp trong bang (ADR-383/384/385), rút gọn số sát thương (ADR-387),
 * hẹn giờ thoát (ADR-388), báo tin riêng khi ẩn chat + ping cao (ADR-389/390). Dọn thư cũ (ADR-386) chạy ở server. MỘT
 * thành phần của {@link Lot56Windows}.
 */
record Lot57Windows(
        InvisibleCoordinator invisible,
        CompactDamageCoordinator compactDamage,
        AutoExitCoordinator autoExit,
        ChatAlertsCoordinator alerts,
        Lot58Windows lot58) {

    static Lot57Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot57Windows(
                new InvisibleCoordinator(sender, feeds.chat()),
                new CompactDamageCoordinator(feeds.chat()),
                new AutoExitCoordinator(feeds.chat()),
                new ChatAlertsCoordinator(feeds.chat()),
                Lot58Windows.create(sender, feeds)); // lô 58 lồng ở đây
    }

    void registerInto(PanelHub panels) {
        panels.register(invisible);
        panels.register(compactDamage);
        panels.register(autoExit);
        panels.register(alerts);
        lot58.registerInto(panels);
    }
}
