package dev.kage.client.core.world;

/**
 * Luồng lô 53 (ADR-351…) — xếp hạng chuỗi / giới thiệu và tìm người (ADR-351/352/353), lưu trữ thư (ADR-354), đếm
 * ngược reset (ADR-356). % máu mục tiêu (ADR-355) trong {@code HudTargetFrame}, bảng phím F1 (ADR-357) trong {@code
 * CombatHud}, giảm hiệu ứng người khác (ADR-358) trong {@code CombatFeedbackPresenter}. MỘT thành phần của {@link
 * Lot52Windows}.
 */
record Lot53Windows(
        PlayerLookupCoordinator lookup, MailArchiveCoordinator mailArchive, ResetCountdownCoordinator resetCountdown) {

    static Lot53Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot53Windows(
                new PlayerLookupCoordinator(sender, feeds.chat()),
                new MailArchiveCoordinator(sender, feeds.chat()),
                new ResetCountdownCoordinator(feeds.chat(), System::currentTimeMillis));
    }

    /** ADR-357 — chữ bảng phím tắt (giữ F1): danh sách phím đang gán, dịch sẵn. */
    String keyHelpText() {
        return dev.kage.client.core.ui.theme.KageI18n.formatOrKey(
                dev.kage.client.core.ui.theme.KageI18n.load(),
                "keyhelp.text",
                KeyBindingsCoordinator.list(dev.kage.client.core.input.KeyBindings.shared()));
    }

    void registerInto(PanelHub panels) {
        panels.register(lookup);
        panels.register(mailArchive);
        panels.register(resetCountdown);
    }
}
